use anyhow::{anyhow, Result};
use parking_lot::Mutex;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Instant;
use tokio::sync::{broadcast, mpsc};
use tokio::time::{sleep, Duration};
use uuid::Uuid;

use crate::protocol::frame::{Frame, FrameType};
use crate::protocol::message::{ChunkAckMessage, ChunkReqMessage, ControlMessageWrapper};
use crate::scheduler::metrics::{ChunkVisualState, SchedulerTelemetry};
use crate::scheduler::pool::ChunkPool;
use crate::storage::chunker::TransferManifest;
use crate::storage::integrity::IntegrityVerifier;
use crate::storage::manifest_db::ManifestDb;
use crate::storage::sparse_writer::SparseWriter;
use crate::transport::r#trait::{AsyncTransport, TransportMetrics};

#[derive(Debug, Clone)]
pub enum TransferEvent {
    Started(Uuid),
    Progress(SchedulerTelemetry),
    ChunkCompleted { chunk_id: u32, transport_id: String },
    ChunkCorrupt { chunk_id: u32, transport_id: String },
    TransportAdded(String),
    TransportLost(String),
    Completed(Uuid),
    Failed { transfer_id: Uuid, reason: String },
    Cancelled(Uuid),
}

pub struct TransferHandle {
    pub transfer_id: Uuid,
    pub cancel_flag: Arc<AtomicBool>,
    pub telemetry_tx: broadcast::Sender<SchedulerTelemetry>,
}

pub struct MultipathScheduler {
    manifest: TransferManifest,
    pool: ChunkPool,
    writer: Option<Arc<SparseWriter>>,
    db: Option<Arc<ManifestDb>>,
    cancel_flag: Arc<AtomicBool>,
    telemetry_tx: broadcast::Sender<SchedulerTelemetry>,
    chunk_visual_map: Arc<Mutex<HashMap<u32, ChunkVisualState>>>,
    start_time: Instant,
}

impl MultipathScheduler {
    pub fn new(
        manifest: TransferManifest,
        already_completed: &[u32],
        writer: Option<Arc<SparseWriter>>,
        db: Option<Arc<ManifestDb>>,
    ) -> (Self, TransferHandle) {
        let (telemetry_tx, _) = broadcast::channel(256);
        let cancel_flag = Arc::new(AtomicBool::new(false));
        let pool = ChunkPool::new(manifest.chunks.clone(), already_completed);

        let mut visual_map = HashMap::new();
        for chunk in &manifest.chunks {
            let state = if already_completed.contains(&chunk.chunk_id) {
                "COMPLETED".to_string()
            } else {
                "PENDING".to_string()
            };
            visual_map.insert(
                chunk.chunk_id,
                ChunkVisualState {
                    chunk_id: chunk.chunk_id,
                    state,
                    transport_id: None,
                },
            );
        }

        let handle = TransferHandle {
            transfer_id: manifest.transfer_id,
            cancel_flag: cancel_flag.clone(),
            telemetry_tx: telemetry_tx.clone(),
        };

        let scheduler = Self {
            manifest,
            pool,
            writer,
            db,
            cancel_flag,
            telemetry_tx,
            chunk_visual_map: Arc::new(Mutex::new(visual_map)),
            start_time: Instant::now(),
        };

        (scheduler, handle)
    }

    /// Execute multipath receiver transfer across a set of active AsyncTransport instances
    pub async fn run_receiver(
        self,
        mut transports: Vec<Box<dyn AsyncTransport>>,
    ) -> Result<()> {
        let transfer_id = self.manifest.transfer_id;
        let total_bytes = self.manifest.total_bytes;
        let _total_chunks = self.manifest.total_chunks;
        let title = self.manifest.title.clone();

        let (event_tx, mut event_rx) = mpsc::channel::<TransferEvent>(512);

        // Shared map of transport metrics
        let transport_metrics_map: Arc<Mutex<HashMap<String, TransportMetrics>>> = Arc::new(Mutex::new(HashMap::new()));
        for t in &transports {
            transport_metrics_map.lock().insert(t.id().to_string(), t.metrics());
        }

        // Spawn transport worker loops
        let mut worker_join_handles = Vec::new();

        for mut transport in transports.drain(..) {
            let pool = self.pool.clone();
            let writer = self.writer.clone();
            let db = self.db.clone();
            let cancel = self.cancel_flag.clone();
            let event_tx_clone = event_tx.clone();
            let visual_map = self.chunk_visual_map.clone();
            let transport_metrics_map = transport_metrics_map.clone();

            let handle = tokio::spawn(async move {
                let transport_id = transport.id().to_string();
                tracing::info!("Starting transport worker loop for {}", transport_id);

                while !cancel.load(Ordering::SeqCst) && !pool.is_all_completed() {
                    let metrics = transport.metrics();
                    if !metrics.is_enabled {
                        sleep(Duration::from_millis(100)).await;
                        continue;
                    }

                    // Attempt to take next unassigned chunk, or steal a stalled chunk
                    let chunk_opt = pool
                        .take_next_unassigned(&transport_id, metrics.current_mbps, metrics.rtt_ms)
                        .or_else(|| pool.steal_stalled_chunk(&transport_id));

                    let chunk = match chunk_opt {
                        Some(c) => c,
                        None => {
                            // No work available currently, yield briefly
                            sleep(Duration::from_millis(25)).await;
                            continue;
                        }
                    };

                    let chunk_id = chunk.chunk_id;

                    // Update visual state to IN_FLIGHT
                    {
                        let mut vmap = visual_map.lock();
                        if let Some(v) = vmap.get_mut(&chunk_id) {
                            v.state = "IN_FLIGHT".to_string();
                            v.transport_id = Some(transport_id.clone());
                        }
                    }

                    // Send ChunkReq frame
                    let req_msg = ChunkReqMessage {
                        transfer_id,
                        file_index: chunk.file_index,
                        chunk_id,
                        offset: chunk.offset,
                        length: chunk.length,
                    };

                    let req_payload = match ControlMessageWrapper::to_bytes(&req_msg) {
                        Ok(p) => p,
                        Err(e) => {
                            tracing::error!("Failed to serialize ChunkReq: {}", e);
                            pool.return_to_pool(chunk_id);
                            continue;
                        }
                    };

                    let req_frame = Frame::new(FrameType::ChunkReq, transfer_id, chunk_id, 0, req_payload);
                    if let Err(e) = transport.send_frame(req_frame).await {
                        tracing::warn!("Transport {} failed sending ChunkReq {}: {}", transport_id, chunk_id, e);
                        pool.return_to_pool(chunk_id);
                        break; // Exit worker loop on socket failure
                    }

                    // Await ChunkData response frame
                    let mut chunk_received = false;
                    let wait_start = Instant::now();

                    while wait_start.elapsed() < Duration::from_secs(15) {
                        match transport.recv_frame().await {
                            Ok(Some(resp_frame)) => {
                                if resp_frame.header.frame_type == FrameType::ChunkData
                                    && resp_frame.header.chunk_id == chunk_id
                                {
                                    // Verify chunk integrity
                                    let is_valid = IntegrityVerifier::verify_chunk(
                                        &resp_frame.payload,
                                        &chunk.sha256,
                                        &chunk.blake3,
                                    )
                                    .unwrap_or(false);

                                    if is_valid {
                                        // Write chunk data to disk
                                        if let Some(ref w) = writer {
                                            if let Err(e) = w.write_chunk(chunk.file_index, chunk.offset, &resp_frame.payload) {
                                                tracing::error!("Failed writing chunk {} to disk: {}", chunk_id, e);
                                            }
                                        }

                                        // Mark completed in DB
                                        if let Some(ref d) = db {
                                            let _ = d.mark_chunk_completed(transfer_id, chunk_id, &transport_id);
                                        }

                                        let completed_fresh = pool.mark_completed(chunk_id, &transport_id);

                                        // Update visual state to COMPLETED
                                        {
                                            let mut vmap = visual_map.lock();
                                            if let Some(v) = vmap.get_mut(&chunk_id) {
                                                v.state = "COMPLETED".to_string();
                                                v.transport_id = Some(transport_id.clone());
                                            }
                                        }

                                        if completed_fresh {
                                            let _ = event_tx_clone
                                                .send(TransferEvent::ChunkCompleted {
                                                    chunk_id,
                                                    transport_id: transport_id.clone(),
                                                })
                                                .await;
                                        }

                                        // Send ChunkAck
                                        let ack = ChunkAckMessage {
                                            transfer_id,
                                            chunk_id,
                                            verified: true,
                                        };
                                        if let Ok(ack_bytes) = ControlMessageWrapper::to_bytes(&ack) {
                                            let _ = transport
                                                .send_frame(Frame::new(
                                                    FrameType::ChunkAck,
                                                    transfer_id,
                                                    chunk_id,
                                                    0,
                                                    ack_bytes,
                                                ))
                                                .await;
                                        }

                                        chunk_received = true;
                                        break;
                                    } else {
                                        tracing::warn!("Chunk {} integrity check FAILED on transport {}", chunk_id, transport_id);
                                        pool.return_to_pool(chunk_id);
                                        {
                                            let mut vmap = visual_map.lock();
                                            if let Some(v) = vmap.get_mut(&chunk_id) {
                                                v.state = "CORRUPTED".to_string();
                                            }
                                        }
                                        let _ = event_tx_clone
                                            .send(TransferEvent::ChunkCorrupt {
                                                chunk_id,
                                                transport_id: transport_id.clone(),
                                            })
                                            .await;
                                        break;
                                    }
                                }
                            }
                            Ok(None) => {
                                tracing::warn!("Transport {} reached EOF / disconnected", transport_id);
                                break;
                            }
                            Err(e) => {
                                tracing::warn!("Transport {} recv error: {}", transport_id, e);
                                break;
                            }
                        }
                    }

                    if !chunk_received {
                        // Return chunk back to pool for another transport to pick up
                        pool.return_to_pool(chunk_id);
                    }

                    // Update live metrics snapshot
                    transport_metrics_map.lock().insert(transport_id.clone(), transport.metrics());
                }

                // If transport crashed/disconnected, return any remaining in-flight chunks
                let returned = pool.return_all_from_transport(&transport_id);
                if !returned.is_empty() {
                    tracing::info!("Returned {} in-flight chunks from lost transport {}", returned.len(), transport_id);
                }
                let _ = event_tx_clone.send(TransferEvent::TransportLost(transport_id.clone())).await;
            });

            worker_join_handles.push(handle);
        }

        // Telemetry Broadcast Loop
        let cancel_telemetry = self.cancel_flag.clone();
        let pool_telemetry = self.pool.clone();
        let telemetry_tx = self.telemetry_tx.clone();
        let visual_map = self.chunk_visual_map.clone();
        let start_time = self.start_time;
        let telem_metrics_map = transport_metrics_map.clone();

        tokio::spawn(async move {
            while !cancel_telemetry.load(Ordering::SeqCst) && !pool_telemetry.is_all_completed() {
                let (_, _, _, completed_bytes, total_bytes) = pool_telemetry.stats();
                let metrics_vec: Vec<TransportMetrics> = telem_metrics_map.lock().values().cloned().collect();
                let visual_vec: Vec<ChunkVisualState> = visual_map.lock().values().cloned().collect();
                let elapsed = start_time.elapsed().as_secs_f64();

                let status = if pool_telemetry.is_all_completed() {
                    "COMPLETED".to_string()
                } else {
                    "ACTIVE".to_string()
                };

                let telemetry = SchedulerTelemetry::new(
                    transfer_id,
                    title.clone(),
                    status,
                    metrics_vec,
                    total_bytes,
                    completed_bytes,
                    elapsed,
                    visual_vec,
                );

                let _ = telemetry_tx.send(telemetry);
                sleep(Duration::from_millis(100)).await; // 10Hz live UI updates
            }
        });

        // Await completion or cancellation
        loop {
            if self.cancel_flag.load(Ordering::SeqCst) {
                return Err(anyhow!("Transfer was cancelled by user"));
            }

            if self.pool.is_all_completed() {
                tracing::info!("Multipath transfer {} completed successfully!", transfer_id);

                // Finalize all files
                if let Some(ref w) = self.writer {
                    for f in &self.manifest.files {
                        let _ = w.finalize_file(f.file_index);
                    }
                }

                // Send final telemetry update
                let metrics_vec: Vec<TransportMetrics> = transport_metrics_map.lock().values().cloned().collect();
                let visual_vec: Vec<ChunkVisualState> = self.chunk_visual_map.lock().values().cloned().collect();
                let elapsed = self.start_time.elapsed().as_secs_f64();

                let final_telemetry = SchedulerTelemetry::new(
                    transfer_id,
                    self.manifest.title.clone(),
                    "COMPLETED".to_string(),
                    metrics_vec,
                    total_bytes,
                    total_bytes,
                    elapsed,
                    visual_vec,
                );
                let _ = self.telemetry_tx.send(final_telemetry);
                break;
            }

            // Drain any events
            while let Ok(_event) = event_rx.try_recv() {}

            sleep(Duration::from_millis(50)).await;
        }

        Ok(())
    }
}

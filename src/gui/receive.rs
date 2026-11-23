use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{Duration, Instant};

use axum::extract::State;
use axum::http::{HeaderMap, HeaderValue, StatusCode};
use axum::Json;
use bytes::Bytes;
use parking_lot::Mutex;
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use tokio::sync::{broadcast, watch};
use uuid::Uuid;

use crate::protocol::TransportKind;
use crate::scheduler::metrics::SchedulerTelemetry;
use crate::server::api::{receive_transfer_chunk, AppState};
use crate::transport::r#trait::TransportMetrics;

type ChunkResponse = Result<Json<Value>, (StatusCode, Json<Value>)>;
type FileReceipt = Arc<tokio::sync::Mutex<Option<(Instant, u64)>>>;

#[derive(Default)]
pub(super) struct ChunkReceiver {
    receipts: Mutex<HashMap<Uuid, FileReceipt>>,
}

impl ChunkReceiver {
    pub async fn receive(
        &self,
        state: AppState,
        mut headers: HeaderMap,
        body: Bytes,
    ) -> ChunkResponse {
        let header = |key| {
            headers
                .get(key)
                .and_then(|value| value.to_str().ok())
                .unwrap_or("")
                .trim()
        };
        let batch_id = header("x-transfer-id");
        let file = header("x-file-name");
        let has_batch = !batch_id.is_empty();

        // Android uses one transfer ID across a batch and restarts chunk numbering for each file.
        let mut hash = Sha256::new();
        hash.update(batch_id);
        hash.update([0]);
        hash.update(file);
        let mut id_bytes = [0; 16];
        id_bytes.copy_from_slice(&hash.finalize()[..16]);
        let file_id = Uuid::from_bytes(id_bytes);

        let name = std::path::Path::new(file)
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("received_file.bin")
            .to_owned();
        let total_bytes: u64 = header("x-file-size").parse().unwrap_or(0);
        let chunk_id: u64 = header("x-chunk-id").parse().unwrap_or(0);
        let usb = header("x-transport-kind").eq_ignore_ascii_case("usb")
            || header("x-transport").to_ascii_lowercase().contains("usb");
        headers.insert(
            "x-transfer-id",
            HeaderValue::from_str(&file_id.to_string()).unwrap(),
        );

        let receipt = {
            let mut receipts = self.receipts.lock();
            receipts.retain(|_, receipt| {
                Arc::strong_count(receipt) > 1
                    || receipt.try_lock().is_ok_and(|entry| {
                        entry.is_some_and(|(finished, _)| {
                            finished.elapsed() < Duration::from_secs(300)
                        })
                    })
            });
            receipts.entry(file_id).or_default().clone()
        };
        // Keep a retry of the final chunk from starting a fresh receiver session.
        let mut receipt = receipt.lock().await;
        if let Some((_, total_chunks)) = *receipt {
            return Ok(Json(json!({
                "success": true, "chunk_id": chunk_id,
                "completed_chunks": total_chunks, "total_chunks": total_chunks,
            })));
        }

        let received = body.len() as u64;
        let result = receive_transfer_chunk(State(state.clone()), headers, body).await;
        match &result {
            Ok(Json(response)) => {
                let done = response["completed_chunks"].as_u64().unwrap_or(0);
                let total = response["total_chunks"].as_u64().unwrap_or(1);
                if done >= total && has_batch {
                    *receipt = Some((Instant::now(), total));
                } else if done == 1 && total > 1 {
                    // The shared receiver throttles telemetry, including on its first chunk.
                    let (label, kind) = if usb {
                        ("USB", TransportKind::Usb)
                    } else {
                        ("Wi-Fi", TransportKind::WifiDirect)
                    };
                    let _ = state.telemetry_tx.send(SchedulerTelemetry::new(
                        file_id,
                        name,
                        "ACTIVE".into(),
                        vec![TransportMetrics::new(label.into(), kind)],
                        total_bytes,
                        received,
                        0.0,
                        vec![],
                    ));
                }
            }
            Err((status, _)) if status.is_server_error() => {
                let _ = state.telemetry_tx.send(SchedulerTelemetry::new(
                    file_id,
                    name,
                    "FAILED".into(),
                    vec![],
                    total_bytes,
                    0,
                    0.0,
                    vec![],
                ));
            }
            _ => {}
        }
        result
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum ReceiveStatus {
    Receiving,
    Saving,
    Completed,
    Failed,
}

impl ReceiveStatus {
    pub fn is_active(self) -> bool {
        matches!(self, Self::Receiving | Self::Saving)
    }
}

#[derive(Clone, Debug)]
pub(super) struct ReceivedFile {
    pub id: Uuid,
    pub name: String,
    pub status: ReceiveStatus,
    pub received_bytes: u64,
    pub total_bytes: Option<u64>,
    pub bytes_per_second: f64,
    pub usb: bool,
    pub wifi: bool,
}

impl ReceivedFile {
    pub fn progress(&self) -> Option<f32> {
        if self.status == ReceiveStatus::Completed {
            return Some(1.0);
        }
        self.total_bytes
            .filter(|&total| total > 0)
            .map(|total| (self.received_bytes as f32 / total as f32).clamp(0., 1.))
    }
}

#[derive(Clone, Default)]
pub(super) struct ReceiveActivity {
    pub files: Vec<ReceivedFile>,
}

impl ReceiveActivity {
    fn update(&mut self, telemetry: &SchedulerTelemetry) -> bool {
        let status = match telemetry.status.as_str() {
            "ACTIVE" => ReceiveStatus::Receiving,
            "VERIFYING" => ReceiveStatus::Saving,
            "COMPLETED" => ReceiveStatus::Completed,
            "FAILED" => ReceiveStatus::Failed,
            _ => return false,
        };
        let id = telemetry.transfer_id;
        let aggregate = &telemetry.aggregate;
        let received_bytes = aggregate.total_bytes_transferred;
        // Streaming multipart has no advertised file size. Its live telemetry uses the bytes
        // received so far as the expected size, whereas chunk uploads report the true size.
        let total_bytes = (status == ReceiveStatus::Completed
            || aggregate.total_bytes_expected > received_bytes)
            .then_some(aggregate.total_bytes_expected);
        let mut file = ReceivedFile {
            id,
            name: telemetry.title.clone(),
            status,
            received_bytes,
            total_bytes,
            bytes_per_second: if aggregate.elapsed_seconds > 0.0 {
                received_bytes as f64 / aggregate.elapsed_seconds
            } else {
                0.0
            },
            usb: telemetry
                .transports
                .iter()
                .any(|t| t.transport_kind == TransportKind::Usb),
            wifi: telemetry
                .transports
                .iter()
                .any(|t| t.transport_kind != TransportKind::Usb),
        };
        if let Some(previous) = self.files.iter_mut().find(|item| item.id == id) {
            if previous.status == ReceiveStatus::Completed
                || (status == ReceiveStatus::Receiving
                    && previous.status == ReceiveStatus::Receiving
                    && file.received_bytes <= previous.received_bytes)
            {
                return false;
            }
            if status == ReceiveStatus::Failed {
                file.received_bytes = previous.received_bytes;
                file.bytes_per_second = 0.0;
            }
            file.usb |= previous.usb;
            file.wifi |= previous.wifi;
            file.total_bytes = file.total_bytes.or(previous.total_bytes);
            *previous = file;
        } else {
            self.files.insert(0, file);
        }
        let mut completed = 0;
        self.files.retain(|file| {
            if file.status.is_active() {
                return true;
            }
            completed += 1;
            completed <= 8
        });
        true
    }
}

pub(super) async fn monitor(
    mut incoming: broadcast::Receiver<SchedulerTelemetry>,
    activity: watch::Sender<ReceiveActivity>,
    telemetry: broadcast::Sender<SchedulerTelemetry>,
) {
    loop {
        match incoming.recv().await {
            Ok(event) => {
                activity.send_if_modified(|state| state.update(&event));
                let _ = telemetry.send(event);
            }
            Err(broadcast::error::RecvError::Lagged(_)) => continue,
            Err(broadcast::error::RecvError::Closed) => break,
        }
    }
}

pub(super) fn download_directory() -> PathBuf {
    std::env::var_os("USERPROFILE")
        .map(|profile| {
            PathBuf::from(profile)
                .join("Downloads")
                .join("ShareDash_Received")
        })
        .unwrap_or_else(|| PathBuf::from("downloads"))
}

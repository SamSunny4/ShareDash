use anyhow::{anyhow, Result};
use async_trait::async_trait;
use bytes::Bytes;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::mpsc::{self, Receiver, Sender};
use tokio::time::sleep;

use crate::protocol::frame::Frame;
use crate::protocol::message::TransportKind;
use crate::transport::r#trait::{AsyncTransport, BandwidthTracker, TransportMetrics, TransportState};

/// In-memory simulated transport with artificial speed throttling, latency injection, and simulated disconnection
pub struct MockSimTransport {
    id: String,
    kind: TransportKind,
    tx: Sender<Frame>,
    rx: Receiver<Frame>,
    metrics: TransportMetrics,
    tracker: BandwidthTracker,
    target_mbps: f64,
    latency_ms: u64,
    corrupt_next_chunk: Arc<AtomicBool>,
    disconnected: Arc<AtomicBool>,
}

impl MockSimTransport {
    pub fn pair(
        id_a: &str,
        id_b: &str,
        kind: TransportKind,
        target_mbps: f64,
        latency_ms: u64,
    ) -> (Self, Self) {
        let (tx_a, rx_b) = mpsc::channel(100);
        let (tx_b, rx_a) = mpsc::channel(100);

        let corrupt_a = Arc::new(AtomicBool::new(false));
        let corrupt_b = Arc::new(AtomicBool::new(false));
        let disc_a = Arc::new(AtomicBool::new(false));
        let disc_b = Arc::new(AtomicBool::new(false));

        let mut t_a = Self {
            id: id_a.to_string(),
            kind: kind.clone(),
            tx: tx_a,
            rx: rx_a,
            metrics: TransportMetrics::new(id_a.to_string(), kind.clone()),
            tracker: BandwidthTracker::new(),
            target_mbps,
            latency_ms,
            corrupt_next_chunk: corrupt_a,
            disconnected: disc_a,
        };
        t_a.metrics.state = TransportState::Connected;
        t_a.metrics.current_mbps = target_mbps;

        let mut t_b = Self {
            id: id_b.to_string(),
            kind: kind.clone(),
            tx: tx_b,
            rx: rx_b,
            metrics: TransportMetrics::new(id_b.to_string(), kind),
            tracker: BandwidthTracker::new(),
            target_mbps,
            latency_ms,
            corrupt_next_chunk: corrupt_b,
            disconnected: disc_b,
        };
        t_b.metrics.state = TransportState::Connected;
        t_b.metrics.current_mbps = target_mbps;

        (t_a, t_b)
    }

    pub fn inject_disconnection(&self) {
        self.disconnected.store(true, Ordering::SeqCst);
    }

    pub fn get_disconnection_trigger(&self) -> Arc<AtomicBool> {
        self.disconnected.clone()
    }

    pub fn inject_chunk_corruption(&self) {
        self.corrupt_next_chunk.store(true, Ordering::SeqCst);
    }

    pub fn set_simulated_speed(&mut self, mbps: f64) {
        self.target_mbps = mbps;
        self.metrics.current_mbps = mbps;
    }
}

#[async_trait]
impl AsyncTransport for MockSimTransport {
    fn id(&self) -> &str {
        &self.id
    }

    fn kind(&self) -> TransportKind {
        self.kind.clone()
    }

    async fn send_frame(&mut self, mut frame: Frame) -> Result<()> {
        if self.disconnected.load(Ordering::SeqCst) {
            self.metrics.state = TransportState::Disconnected;
            return Err(anyhow!("Simulated transport {} is disconnected (cable pull)", self.id));
        }

        if !self.metrics.is_enabled {
            return Err(anyhow!("Transport {} is disabled by user", self.id));
        }

        let payload_len = frame.payload.len() as u64;

        // Apply simulated bandwidth throttle delay
        if self.target_mbps > 0.0 && payload_len > 0 {
            let bytes_per_sec = (self.target_mbps * 1_000_000.0) / 8.0;
            let transmit_secs = (payload_len as f64) / bytes_per_sec;
            let delay_ms = (transmit_secs * 1000.0) as u64 + self.latency_ms;
            if delay_ms > 0 {
                sleep(Duration::from_millis(delay_ms)).await;
            }
        }

        // Apply chunk corruption if triggered
        if self.corrupt_next_chunk.swap(false, Ordering::SeqCst) && !frame.payload.is_empty() {
            let mut corrupted = frame.payload.to_vec();
            corrupted[0] ^= 0xFF; // Flip bits
            frame.payload = Bytes::from(corrupted);
        }

        self.tx
            .send(frame)
            .await
            .map_err(|e| anyhow!("Failed to send simulated frame: {}", e))?;

        self.metrics.total_bytes_sent += payload_len;
        let (ewma, peak) = self.tracker.record_bytes(self.metrics.total_bytes_sent + self.metrics.total_bytes_recv);
        self.metrics.current_mbps = self.target_mbps.max(ewma);
        self.metrics.peak_mbps = peak.max(self.target_mbps);
        Ok(())
    }

    async fn recv_frame(&mut self) -> Result<Option<Frame>> {
        if self.disconnected.load(Ordering::SeqCst) {
            self.metrics.state = TransportState::Disconnected;
            return Ok(None);
        }

        match self.rx.recv().await {
            Some(frame) => {
                let payload_len = frame.payload.len() as u64;
                self.metrics.total_bytes_recv += payload_len;
                let (ewma, peak) = self.tracker.record_bytes(self.metrics.total_bytes_sent + self.metrics.total_bytes_recv);
                self.metrics.current_mbps = self.target_mbps.max(ewma);
                self.metrics.peak_mbps = peak.max(self.target_mbps);
                Ok(Some(frame))
            }
            None => {
                self.metrics.state = TransportState::Disconnected;
                Ok(None)
            }
        }
    }

    async fn benchmark(&mut self, _probe_size_bytes: usize) -> Result<f64> {
        self.metrics.rtt_ms = self.latency_ms as f64 * 2.0;
        self.metrics.current_mbps = self.target_mbps;
        self.metrics.state = TransportState::Active;
        Ok(self.target_mbps)
    }

    fn metrics(&self) -> TransportMetrics {
        self.metrics.clone()
    }

    fn is_connected(&self) -> bool {
        !self.disconnected.load(Ordering::SeqCst) && self.metrics.state != TransportState::Disconnected
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.metrics.is_enabled = enabled;
    }

    async fn close(&mut self) -> Result<()> {
        self.disconnected.store(true, Ordering::SeqCst);
        self.metrics.state = TransportState::Closed;
        Ok(())
    }
}

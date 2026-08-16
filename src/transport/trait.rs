use anyhow::Result;
use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::time::Instant;

use crate::protocol::frame::Frame;
use crate::protocol::message::TransportKind;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TransportState {
    Disconnected,
    Connecting,
    Connected,
    Benchmarking,
    Active,
    Degraded,
    Closed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransportMetrics {
    pub transport_id: String,
    pub transport_kind: TransportKind,
    pub state: TransportState,
    pub current_mbps: f64,
    pub peak_mbps: f64,
    pub average_mbps: f64,
    pub rtt_ms: f64,
    pub total_bytes_sent: u64,
    pub total_bytes_recv: u64,
    pub in_flight_chunks: usize,
    pub completed_chunks: u64,
    pub failed_chunks: u64,
    pub is_enabled: bool,
}

impl TransportMetrics {
    pub fn new(transport_id: String, transport_kind: TransportKind) -> Self {
        Self {
            transport_id,
            transport_kind,
            state: TransportState::Disconnected,
            current_mbps: 0.0,
            peak_mbps: 0.0,
            average_mbps: 0.0,
            rtt_ms: 0.0,
            total_bytes_sent: 0,
            total_bytes_recv: 0,
            in_flight_chunks: 0,
            completed_chunks: 0,
            failed_chunks: 0,
            is_enabled: true,
        }
    }
}

pub struct BandwidthTracker {
    last_check_time: Instant,
    last_bytes_count: u64,
    current_rate_bps: f64,
    ewma_mbps: f64,
    peak_mbps: f64,
}

impl Default for BandwidthTracker {
    fn default() -> Self {
        Self::new()
    }
}

impl BandwidthTracker {
    pub fn new() -> Self {
        Self {
            last_check_time: Instant::now(),
            last_bytes_count: 0,
            current_rate_bps: 0.0,
            ewma_mbps: 0.0,
            peak_mbps: 0.0,
        }
    }

    pub fn record_bytes(&mut self, total_bytes: u64) -> (f64, f64) {
        let now = Instant::now();
        let elapsed = now.duration_since(self.last_check_time).as_secs_f64();

        if elapsed >= 0.25 {
            // Update every 250ms
            let bytes_delta = total_bytes.saturating_sub(self.last_bytes_count);
            let instant_bps = (bytes_delta as f64) / elapsed;
            let instant_mbps = (instant_bps * 8.0) / 1_000_000.0;

            // Exponentially weighted moving average (alpha = 0.35)
            if self.ewma_mbps == 0.0 {
                self.ewma_mbps = instant_mbps;
            } else {
                self.ewma_mbps = 0.35 * instant_mbps + 0.65 * self.ewma_mbps;
            }

            if instant_mbps > self.peak_mbps {
                self.peak_mbps = instant_mbps;
            }

            self.current_rate_bps = instant_bps;
            self.last_bytes_count = total_bytes;
            self.last_check_time = now;
        }

        (self.ewma_mbps, self.peak_mbps)
    }
}

#[async_trait]
pub trait AsyncTransport: Send + Sync {
    fn id(&self) -> &str;
    fn kind(&self) -> TransportKind;
    async fn send_frame(&mut self, frame: Frame) -> Result<()>;
    async fn recv_frame(&mut self) -> Result<Option<Frame>>;
    async fn benchmark(&mut self, probe_size_bytes: usize) -> Result<f64>;
    fn metrics(&self) -> TransportMetrics;
    fn is_connected(&self) -> bool;
    fn set_enabled(&mut self, enabled: bool);
    async fn close(&mut self) -> Result<()>;
}

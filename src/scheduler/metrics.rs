use chrono::Utc;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::transport::r#trait::TransportMetrics;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AggregateMetrics {
    pub aggregate_mbps: f64,
    pub peak_aggregate_mbps: f64,
    pub total_bytes_transferred: u64,
    pub total_bytes_expected: u64,
    pub progress_pct: f64,
    pub eta_seconds: u64,
    pub elapsed_seconds: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChunkVisualState {
    pub chunk_id: u32,
    pub state: String, // "PENDING", "IN_FLIGHT", "COMPLETED", "CORRUPTED"
    pub transport_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SchedulerTelemetry {
    pub transfer_id: Uuid,
    pub title: String,
    pub status: String,
    pub aggregate: AggregateMetrics,
    pub transports: Vec<TransportMetrics>,
    pub chunk_states: Vec<ChunkVisualState>,
    pub timestamp_epoch_ms: i64,
}

impl SchedulerTelemetry {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        transfer_id: Uuid,
        title: String,
        status: String,
        transports: Vec<TransportMetrics>,
        total_bytes: u64,
        completed_bytes: u64,
        elapsed_seconds: f64,
        chunk_states: Vec<ChunkVisualState>,
    ) -> Self {
        let mut agg_mbps = 0.0;
        let mut peak_agg = 0.0;

        for t in &transports {
            if t.is_enabled {
                agg_mbps += t.current_mbps;
                peak_agg += t.peak_mbps;
            }
        }

        let progress_pct = if total_bytes > 0 {
            (completed_bytes as f64 / total_bytes as f64) * 100.0
        } else {
            0.0
        };

        let eta_seconds = if agg_mbps > 0.05 && completed_bytes < total_bytes {
            let bytes_left = total_bytes.saturating_sub(completed_bytes);
            let bytes_per_sec = (agg_mbps * 1_000_000.0) / 8.0;
            ((bytes_left as f64) / bytes_per_sec).ceil() as u64
        } else {
            0
        };

        Self {
            transfer_id,
            title,
            status,
            aggregate: AggregateMetrics {
                aggregate_mbps: agg_mbps,
                peak_aggregate_mbps: peak_agg,
                total_bytes_transferred: completed_bytes,
                total_bytes_expected: total_bytes,
                progress_pct,
                eta_seconds,
                elapsed_seconds,
            },
            transports,
            chunk_states,
            timestamp_epoch_ms: Utc::now().timestamp_millis(),
        }
    }
}

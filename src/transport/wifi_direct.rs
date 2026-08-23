use anyhow::{anyhow, Context, Result};
use async_trait::async_trait;
use bytes::Bytes;
use futures::{SinkExt, StreamExt};
use std::net::SocketAddr;
use std::time::Instant;
use tokio::net::TcpStream;
use tokio_util::codec::Framed;

use crate::protocol::frame::{Frame, FrameCodec, FrameType, FLAG_BENCHMARK};
use crate::protocol::message::TransportKind;
use crate::transport::r#trait::{AsyncTransport, BandwidthTracker, TransportMetrics, TransportState};

pub struct WifiDirectTransport {
    id: String,
    framed: Option<Framed<TcpStream, FrameCodec>>,
    metrics: TransportMetrics,
    tracker: BandwidthTracker,
    addr: Option<SocketAddr>,
}

impl WifiDirectTransport {
    pub fn new(id: impl Into<String>) -> Self {
        let id_str = id.into();
        Self {
            metrics: TransportMetrics::new(id_str.clone(), TransportKind::WifiDirect),
            id: id_str,
            framed: None,
            tracker: BandwidthTracker::new(),
            addr: None,
        }
    }

    pub async fn connect(p2p_group_owner_addr: SocketAddr) -> Result<Self> {
        let stream = TcpStream::connect(p2p_group_owner_addr)
            .await
            .with_context(|| format!("Failed to connect to Wi-Fi Direct P2P Group Owner: {}", p2p_group_owner_addr))?;

        let _ = stream.set_nodelay(true);

        let mut transport = Self::new(format!("WIFI_DIRECT_{}", p2p_group_owner_addr));
        transport.addr = Some(p2p_group_owner_addr);
        transport.framed = Some(Framed::new(stream, FrameCodec::new()));
        transport.metrics.state = TransportState::Connected;
        Ok(transport)
    }

    pub fn from_stream(stream: TcpStream, id: String) -> Result<Self> {
        let addr = stream.peer_addr().ok();
        let _ = stream.set_nodelay(true);

        let mut transport = Self::new(id);
        transport.addr = addr;
        transport.framed = Some(Framed::new(stream, FrameCodec::new()));
        transport.metrics.state = TransportState::Connected;
        Ok(transport)
    }
}

#[async_trait]
impl AsyncTransport for WifiDirectTransport {
    fn id(&self) -> &str {
        &self.id
    }

    fn kind(&self) -> TransportKind {
        TransportKind::WifiDirect
    }

    async fn send_frame(&mut self, frame: Frame) -> Result<()> {
        if !self.metrics.is_enabled {
            return Err(anyhow!("Transport {} is disabled by user", self.id));
        }

        let payload_len = frame.payload.len() as u64;
        let framed = self
            .framed
            .as_mut()
            .ok_or_else(|| anyhow!("Wi-Fi Direct Transport is not connected"))?;

        framed
            .send(frame)
            .await
            .map_err(|e| anyhow!("Failed to send frame over Wi-Fi Direct: {}", e))?;

        self.metrics.total_bytes_sent += payload_len;
        let (ewma, peak) = self.tracker.record_bytes(self.metrics.total_bytes_sent + self.metrics.total_bytes_recv);
        self.metrics.current_mbps = ewma;
        self.metrics.peak_mbps = peak;
        Ok(())
    }

    async fn recv_frame(&mut self) -> Result<Option<Frame>> {
        let framed = self
            .framed
            .as_mut()
            .ok_or_else(|| anyhow!("Wi-Fi Direct Transport is not connected"))?;

        match framed.next().await {
            Some(Ok(frame)) => {
                let payload_len = frame.payload.len() as u64;
                self.metrics.total_bytes_recv += payload_len;
                let (ewma, peak) = self.tracker.record_bytes(self.metrics.total_bytes_sent + self.metrics.total_bytes_recv);
                self.metrics.current_mbps = ewma;
                self.metrics.peak_mbps = peak;
                Ok(Some(frame))
            }
            Some(Err(e)) => {
                self.metrics.state = TransportState::Degraded;
                self.metrics.failed_chunks += 1;
                Err(anyhow!("Wi-Fi Direct frame decode error: {}", e))
            }
            None => {
                self.metrics.state = TransportState::Disconnected;
                Ok(None)
            }
        }
    }

    async fn benchmark(&mut self, probe_size_bytes: usize) -> Result<f64> {
        self.metrics.state = TransportState::Benchmarking;
        let start = Instant::now();

        let probe_data = vec![0xCC; probe_size_bytes];
        let probe_frame = Frame::new(
            FrameType::BenchmarkProbe,
            uuid::Uuid::nil(),
            0,
            FLAG_BENCHMARK,
            Bytes::from(probe_data),
        );

        self.send_frame(probe_frame).await?;

        let recv_result = tokio::time::timeout(std::time::Duration::from_secs(10), async {
            while let Some(frame) = self.recv_frame().await? {
                if frame.header.frame_type == FrameType::BenchmarkResp {
                    return Ok(Some(frame));
                } else {
                    tracing::warn!("Discarded non-benchmark frame during benchmark");
                }
            }
            Ok::<_, anyhow::Error>(None)
        }).await;

        match recv_result {
            Ok(Ok(Some(_))) => {
                let duration = start.elapsed().as_secs_f64();
                self.metrics.rtt_ms = duration * 1000.0;
                let mbps = ((probe_size_bytes as f64) * 8.0) / (duration * 1_000_000.0);
                self.metrics.current_mbps = mbps;
            }
            Ok(Ok(None)) | Ok(Err(_)) => {}
            Err(_) => {
                tracing::warn!("Benchmark timed out");
            }
        }

        self.metrics.state = TransportState::Active;
        Ok(self.metrics.current_mbps)
    }

    fn metrics(&self) -> TransportMetrics {
        self.metrics.clone()
    }

    fn is_connected(&self) -> bool {
        self.framed.is_some() && self.metrics.state != TransportState::Disconnected
    }

    fn set_enabled(&mut self, enabled: bool) {
        self.metrics.is_enabled = enabled;
    }

    async fn close(&mut self) -> Result<()> {
        self.framed = None;
        self.metrics.state = TransportState::Closed;
        Ok(())
    }
}

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WifiCapabilities {
    pub wifi_generation: String,
    pub channel: u32,
    pub band_ghz: f64,
    pub signal_quality: u32,
    pub receive_rate_mbps: f64,
    pub transmit_rate_mbps: f64,
}

pub async fn detect_wifi_capabilities() -> Option<WifiCapabilities> {
    #[cfg(target_os = "windows")]
    {
        let output = tokio::process::Command::new("netsh")
            .args(["wlan", "show", "interfaces"])
            .output()
            .await
            .ok()?;

        let stdout = String::from_utf8_lossy(&output.stdout);
        if !stdout.contains("State") || !stdout.contains("connected") {
            return None;
        }

        let mut radio_type = "802.11ac (Wi-Fi 5)".to_string();
        let mut channel: u32 = 36;
        let mut signal_pct: u32 = 80;
        let mut rx_rate: f64 = 650.0;
        let mut tx_rate: f64 = 650.0;

        for line in stdout.lines() {
            let line_trim = line.trim();
            if line_trim.starts_with("Radio type") {
                if let Some(val) = line_trim.split(':').nth(1) {
                    let v = val.trim();
                    if v.contains("802.11be") {
                        radio_type = "802.11be (Wi-Fi 7)".to_string();
                    } else if v.contains("802.11ax") {
                        radio_type = "802.11ax (Wi-Fi 6 / 6E)".to_string();
                    } else if v.contains("802.11ac") {
                        radio_type = "802.11ac (Wi-Fi 5)".to_string();
                    } else if v.contains("802.11n") {
                        radio_type = "802.11n (Wi-Fi 4)".to_string();
                    } else {
                        radio_type = v.to_string();
                    }
                }
            } else if line_trim.starts_with("Channel") {
                if let Some(val) = line_trim.split(':').nth(1) {
                    channel = val.trim().parse().unwrap_or(36);
                }
            } else if line_trim.starts_with("Signal") {
                if let Some(val) = line_trim.split(':').nth(1) {
                    let s = val.trim().trim_end_matches('%');
                    signal_pct = s.parse().unwrap_or(80);
                }
            } else if line_trim.starts_with("Receive rate") {
                if let Some(val) = line_trim.split(':').nth(1) {
                    rx_rate = val.trim().parse().unwrap_or(650.0);
                }
            } else if line_trim.starts_with("Transmit rate") {
                if let Some(val) = line_trim.split(':').nth(1) {
                    tx_rate = val.trim().parse().unwrap_or(650.0);
                }
            }
        }

        let band_ghz = if channel <= 14 {
            2.4
        } else if channel <= 177 {
            5.0
        } else {
            6.0
        };

        Some(WifiCapabilities {
            wifi_generation: radio_type,
            channel,
            band_ghz,
            signal_quality: signal_pct,
            receive_rate_mbps: rx_rate,
            transmit_rate_mbps: tx_rate,
        })
    }
    #[cfg(not(target_os = "windows"))]
    {
        None
    }
}

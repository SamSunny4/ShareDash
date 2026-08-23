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

pub struct LanTransport {
    id: String,
    framed: Option<Framed<TcpStream, FrameCodec>>,
    metrics: TransportMetrics,
    tracker: BandwidthTracker,
    addr: Option<SocketAddr>,
}

impl LanTransport {
    pub fn new(id: impl Into<String>) -> Self {
        let id_str = id.into();
        Self {
            metrics: TransportMetrics::new(id_str.clone(), TransportKind::Lan),
            id: id_str,
            framed: None,
            tracker: BandwidthTracker::new(),
            addr: None,
        }
    }

    pub async fn connect(addr: SocketAddr) -> Result<Self> {
        let stream = TcpStream::connect(addr)
            .await
            .with_context(|| format!("Failed to connect to LAN endpoint: {}", addr))?;

        let _ = stream.set_nodelay(true);

        let mut transport = Self::new(format!("LAN_{}", addr));
        transport.addr = Some(addr);
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
impl AsyncTransport for LanTransport {
    fn id(&self) -> &str {
        &self.id
    }

    fn kind(&self) -> TransportKind {
        TransportKind::Lan
    }

    async fn send_frame(&mut self, frame: Frame) -> Result<()> {
        if !self.metrics.is_enabled {
            return Err(anyhow!("Transport {} is disabled by user", self.id));
        }

        let payload_len = frame.payload.len() as u64;
        let framed = self
            .framed
            .as_mut()
            .ok_or_else(|| anyhow!("LAN Transport is not connected"))?;

        framed
            .send(frame)
            .await
            .map_err(|e| anyhow!("Failed to send frame over LAN: {}", e))?;

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
            .ok_or_else(|| anyhow!("LAN Transport is not connected"))?;

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
                Err(anyhow!("LAN frame decode error: {}", e))
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

        let probe_data = vec![0xAA; probe_size_bytes];
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

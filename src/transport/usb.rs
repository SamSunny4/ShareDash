use anyhow::{anyhow, Context, Result};
use async_trait::async_trait;
use bytes::Bytes;
use futures::{SinkExt, StreamExt};
use std::net::SocketAddr;
use tokio::process::Command;
use std::time::Instant;
use tokio::net::TcpStream;
use tokio_util::codec::Framed;

use crate::protocol::frame::{Frame, FrameCodec, FrameType, FLAG_BENCHMARK};
use crate::protocol::message::TransportKind;
use crate::transport::r#trait::{AsyncTransport, BandwidthTracker, TransportMetrics, TransportState};

pub struct UsbTransport {
    id: String,
    framed: Option<Framed<TcpStream, FrameCodec>>,
    metrics: TransportMetrics,
    tracker: BandwidthTracker,
    forwarded_port: u16,
    is_adb_bridged: bool,
}

impl UsbTransport {
    pub fn new(id: impl Into<String>, forwarded_port: u16) -> Self {
        let id_str = id.into();
        Self {
            metrics: TransportMetrics::new(id_str.clone(), TransportKind::Usb),
            id: id_str,
            framed: None,
            tracker: BandwidthTracker::new(),
            forwarded_port,
            is_adb_bridged: false,
        }
    }

    pub fn forwarded_port(&self) -> u16 {
        self.forwarded_port
    }

    /// Automatically sets up ADB port forwarding if ADB is detected on host
    pub async fn setup_adb_forward(host_port: u16, device_port: u16) -> Result<bool> {
        let output = Command::new("adb")
            .args(["forward", &format!("tcp:{}", host_port), &format!("tcp:{}", device_port)])
            .output()
            .await;

        match output {
            Ok(out) if out.status.success() => Ok(true),
            _ => Ok(false),
        }
    }

    /// Connects to the local USB bridge socket endpoint
    pub async fn connect_usb(host_port: u16) -> Result<Self> {
        // Try ADB forward setup first
        let adb_bridged = Self::setup_adb_forward(host_port, host_port).await.unwrap_or(false);

        let addr: SocketAddr = format!("127.0.0.1:{}", host_port).parse()?;
        let stream = TcpStream::connect(addr)
            .await
            .with_context(|| format!("Failed to connect to USB bridge on {}", addr))?;

        let _ = stream.set_nodelay(true);

        let mut transport = Self::new("USB_CABLE", host_port);
        transport.is_adb_bridged = adb_bridged;
        transport.framed = Some(Framed::new(stream, FrameCodec::new()));
        transport.metrics.state = TransportState::Connected;
        Ok(transport)
    }

    pub fn from_stream(stream: TcpStream, id: String) -> Result<Self> {
        let _ = stream.set_nodelay(true);
        let mut transport = Self::new(id, 0);
        transport.framed = Some(Framed::new(stream, FrameCodec::new()));
        transport.metrics.state = TransportState::Connected;
        Ok(transport)
    }
}

#[async_trait]
impl AsyncTransport for UsbTransport {
    fn id(&self) -> &str {
        &self.id
    }

    fn kind(&self) -> TransportKind {
        TransportKind::Usb
    }

    async fn send_frame(&mut self, frame: Frame) -> Result<()> {
        if !self.metrics.is_enabled {
            return Err(anyhow!("Transport {} is disabled by user", self.id));
        }

        let payload_len = frame.payload.len() as u64;
        let framed = self
            .framed
            .as_mut()
            .ok_or_else(|| anyhow!("USB Transport is not connected"))?;

        framed
            .send(frame)
            .await
            .map_err(|e| anyhow!("Failed to send frame over USB: {}", e))?;

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
            .ok_or_else(|| anyhow!("USB Transport is not connected"))?;

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
                Err(anyhow!("USB frame decode error: {}", e))
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

        let probe_data = vec![0xBB; probe_size_bytes];
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

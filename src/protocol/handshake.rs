use anyhow::Result;
use bytes::Bytes;
use serde::{Deserialize, Serialize};
use std::time::Duration;

use crate::protocol::frame::{Frame, FrameType, FLAG_NONE};
use crate::protocol::message::{CapabilitiesMessage, HelloMessage, HelloRespMessage, TransportKind};
use crate::transport::r#trait::AsyncTransport;

#[derive(Debug, Clone, PartialEq)]
pub enum HandshakeState {
    Idle,
    SynSent,
    SynReceived,
    Established,
    Failed(String),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SessionInfo {
    pub peer_device_id: String,
    pub peer_device_name: String,
    pub peer_os: String,
    pub peer_capabilities: CapabilitiesMessage,
    pub local_capabilities: CapabilitiesMessage,
}

pub struct TransportHandshake {
    pub state: HandshakeState,
    pub local_device_id: String,
    pub local_device_name: String,
    pub local_os: String,
    timeout: Duration,
}

impl TransportHandshake {
    pub fn new(device_id: String, device_name: String, os: String) -> Self {
        Self {
            state: HandshakeState::Idle,
            local_device_id: device_id,
            local_device_name: device_name,
            local_os: os,
            timeout: Duration::from_secs(5),
        }
    }

    pub fn with_timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }

    pub async fn initiate(&mut self, transport: &mut dyn AsyncTransport) -> Result<SessionInfo> {
        // 1. Send Hello
        self.state = HandshakeState::SynSent;
        let hello = HelloMessage {
            device_id: self.local_device_id.clone(),
            friendly_name: self.local_device_name.clone(),
            os_name: self.local_os.clone(),
            app_version: "1.0.0".to_string(),
            protocol_version: 1,
            listen_endpoints: vec![],
        };
        let hello_bytes = serde_json::to_vec(&hello)?;
        let frame = Frame::new(FrameType::Hello, uuid::Uuid::nil(), 0, FLAG_NONE, Bytes::from(hello_bytes));
        transport.send_frame(frame).await?;

        // 2. Receive HelloResp
        let resp_frame = tokio::time::timeout(self.timeout, transport.recv_frame()).await
            .map_err(|_| anyhow::anyhow!("Handshake timeout waiting for SYN-ACK"))??
            .ok_or_else(|| anyhow::anyhow!("Connection closed during handshake"))?;
        
        let hello_resp: HelloRespMessage = serde_json::from_slice(&resp_frame.payload)?;
        self.state = HandshakeState::SynReceived;

        // 3. Send Capabilities
        let caps = build_local_capabilities();
        let caps_bytes = serde_json::to_vec(&caps)?;
        let caps_frame = Frame::new(FrameType::Capabilities, uuid::Uuid::nil(), 0, FLAG_NONE, Bytes::from(caps_bytes));
        transport.send_frame(caps_frame).await?;

        // 4. Receive peer Capabilities
        let peer_caps_frame = tokio::time::timeout(self.timeout, transport.recv_frame()).await
            .map_err(|_| anyhow::anyhow!("Handshake timeout waiting for Capabilities"))??
            .ok_or_else(|| anyhow::anyhow!("Connection closed during handshake"))?;
        
        let peer_caps: CapabilitiesMessage = serde_json::from_slice(&peer_caps_frame.payload)?;
        self.state = HandshakeState::Established;

        Ok(SessionInfo {
            peer_device_id: hello_resp.peer_device_id,
            peer_device_name: hello_resp.peer_name,
            peer_os: "".to_string(),
            peer_capabilities: peer_caps,
            local_capabilities: caps,
        })
    }

    pub async fn respond(&mut self, transport: &mut dyn AsyncTransport) -> Result<SessionInfo> {
        // 1. Receive Hello
        let hello_frame = tokio::time::timeout(self.timeout, transport.recv_frame()).await
            .map_err(|_| anyhow::anyhow!("Handshake timeout waiting for SYN"))??
            .ok_or_else(|| anyhow::anyhow!("Connection closed during handshake"))?;
        let hello: HelloMessage = serde_json::from_slice(&hello_frame.payload)?;
        self.state = HandshakeState::SynReceived;

        // 2. Send HelloResp
        let resp = HelloRespMessage {
            peer_device_id: self.local_device_id.clone(),
            peer_name: self.local_device_name.clone(),
            session_id: uuid::Uuid::new_v4(),
            accepted: true,
        };
        let resp_bytes = serde_json::to_vec(&resp)?;
        let frame = Frame::new(FrameType::HelloResp, uuid::Uuid::nil(), 0, FLAG_NONE, Bytes::from(resp_bytes));
        transport.send_frame(frame).await?;

        // 3. Receive Capabilities
        let peer_caps_frame = tokio::time::timeout(self.timeout, transport.recv_frame()).await
            .map_err(|_| anyhow::anyhow!("Handshake timeout waiting for Capabilities"))??
            .ok_or_else(|| anyhow::anyhow!("Connection closed during handshake"))?;
        let peer_caps: CapabilitiesMessage = serde_json::from_slice(&peer_caps_frame.payload)?;

        // 4. Send Capabilities
        let caps = build_local_capabilities();
        let caps_bytes = serde_json::to_vec(&caps)?;
        let caps_frame = Frame::new(FrameType::Capabilities, uuid::Uuid::nil(), 0, FLAG_NONE, Bytes::from(caps_bytes));
        transport.send_frame(caps_frame).await?;

        self.state = HandshakeState::Established;

        Ok(SessionInfo {
            peer_device_id: hello.device_id,
            peer_device_name: hello.friendly_name,
            peer_os: hello.os_name,
            peer_capabilities: peer_caps,
            local_capabilities: caps,
        })
    }
}

fn build_local_capabilities() -> CapabilitiesMessage {
    CapabilitiesMessage {
        supported_transports: vec![TransportKind::Lan, TransportKind::WifiDirect, TransportKind::Usb],
        max_concurrent_streams: 4,
        protocol_version: 1,
        wifi_generation: None,
        usb_generation: None,
        link_speed_mbps: None,
        frequency_bands: vec![],
        max_chunk_size: 1024 * 1024,
        available_storage_bytes: 1024 * 1024 * 1024,
        is_charging: true,
        battery_pct: None,
    }
}

use bytes::Bytes;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

fn default_protocol_version() -> u32 { 1 }
fn default_max_streams() -> u32 { 4 }
fn default_chunk_size() -> u32 { 1024 * 1024 }

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum TransportKind {
    Usb,
    Lan,
    WifiDirect,
    InternetQuic,
    Bluetooth,
    MockSim,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelloMessage {
    pub device_id: String,
    pub friendly_name: String,
    pub os_name: String,
    pub app_version: String,
    #[serde(default = "default_protocol_version")]
    pub protocol_version: u32,
    #[serde(default)]
    pub listen_endpoints: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HelloRespMessage {
    pub peer_device_id: String,
    pub peer_name: String,
    pub session_id: Uuid,
    pub accepted: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CapabilitiesMessage {
    #[serde(default, alias = "transports")]
    pub supported_transports: Vec<TransportKind>,
    #[serde(default = "default_max_streams")]
    pub max_concurrent_streams: u32,
    #[serde(default = "default_protocol_version")]
    pub protocol_version: u32,
    pub wifi_generation: Option<String>,
    pub usb_generation: Option<String>,
    pub link_speed_mbps: Option<f64>,
    #[serde(default)]
    pub frequency_bands: Vec<String>,
    #[serde(default = "default_chunk_size")]
    pub max_chunk_size: u32,
    #[serde(default)]
    pub available_storage_bytes: u64,
    #[serde(default)]
    pub is_charging: bool,
    pub battery_pct: Option<u8>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairRequestMessage {
    pub session_id: Uuid,
    pub pin_code: String,
    pub client_public_key: Vec<u8>,
    pub device_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PairResponseMessage {
    pub session_id: Uuid,
    pub approved: bool,
    pub server_public_key: Vec<u8>,
    pub auth_token: String,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileMetadata {
    pub file_index: u32,
    pub relative_path: String,
    pub size_bytes: u64,
    pub modified_timestamp: i64,
    pub is_executable: bool,
    pub chunk_start_index: u32,
    pub chunk_count: u32,
    pub sha256_hash: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferOfferMessage {
    pub transfer_id: Uuid,
    pub sender_name: String,
    pub total_files: u32,
    pub total_bytes: u64,
    pub chunk_size: u32,
    pub total_chunks: u32,
    pub root_hash: String,
    pub preview_files: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransferAcceptMessage {
    pub transfer_id: Uuid,
    pub destination_path: Option<String>,
    pub resume_from_chunks: Vec<u32>, // already completed chunk indices on receiver
    pub accepted: bool,
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManifestMessage {
    pub transfer_id: Uuid,
    pub files: Vec<FileMetadata>,
    pub chunk_hashes: Vec<String>, // BLAKE3 or SHA-256 hex string for each chunk_id
    pub chunk_size: u32,
    pub total_chunks: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChunkReqMessage {
    pub transfer_id: Uuid,
    pub file_index: u32,
    pub chunk_id: u32,
    pub offset: u64,
    pub length: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChunkAckMessage {
    pub transfer_id: Uuid,
    pub chunk_id: u32,
    pub verified: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChunkRejectMessage {
    pub transfer_id: Uuid,
    pub chunk_id: u32,
    pub reason: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BenchmarkProbeMessage {
    pub probe_id: u64,
    pub sender_timestamp_ms: u64,
    pub probe_size: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BenchmarkRespMessage {
    pub probe_id: u64,
    pub sender_timestamp_ms: u64,
    pub receiver_timestamp_ms: u64,
    pub bytes_received: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransportUpdateMessage {
    pub action: String, // "ADD", "REMOVE", "DEGRADED", "RECOVERED"
    pub transport_kind: TransportKind,
    pub endpoint: String,
    pub measured_mbps: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ControlMessageWrapper {
    pub payload_json: String,
}

impl ControlMessageWrapper {
    pub fn to_bytes<T: Serialize>(val: &T) -> anyhow::Result<Bytes> {
        let json_str = serde_json::to_string(val)?;
        Ok(Bytes::from(json_str))
    }

    pub fn from_bytes<'a, T: Deserialize<'a>>(bytes: &'a [u8]) -> anyhow::Result<T> {
        let val = serde_json::from_slice(bytes)?;
        Ok(val)
    }
}

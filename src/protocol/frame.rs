use bytes::{Buf, BufMut, Bytes, BytesMut};
use crc32fast::Hasher as Crc32Hasher;
use serde::{Deserialize, Serialize};
use std::io;
use tokio_util::codec::{Decoder, Encoder};
use uuid::Uuid;

pub const MAGIC_BYTES: [u8; 2] = [0x53, 0x44]; // 'S', 'D' (ShareDash)
pub const PROTOCOL_VERSION: u8 = 1;
pub const FRAME_HEADER_LEN: usize = 34;
pub const MAX_PAYLOAD_SIZE: usize = 64 * 1024 * 1024; // 64 MB max chunk/frame

pub const FLAG_NONE: u16 = 0x0000;
pub const FLAG_ENCRYPTED: u16 = 0x0001;
pub const FLAG_COMPRESSED: u16 = 0x0002;
pub const FLAG_BENCHMARK: u16 = 0x0004;
pub const FLAG_PRIORITY: u16 = 0x0008;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[repr(u8)]
pub enum FrameType {
    Hello = 0x01,
    HelloResp = 0x02,
    Capabilities = 0x03,
    PairReq = 0x04,
    PairResp = 0x05,
    TransferOffer = 0x06,
    TransferAccept = 0x07,
    TransferReject = 0x08,
    Manifest = 0x09,
    ChunkReq = 0x0A,
    ChunkData = 0x0B,
    ChunkAck = 0x0C,
    ChunkReject = 0x0D,
    BenchmarkProbe = 0x0E,
    BenchmarkResp = 0x0F,
    TransportAdd = 0x10,
    TransportRemove = 0x11,
    TransferPause = 0x12,
    TransferResume = 0x13,
    TransferComplete = 0x14,
    TransferCancel = 0x15,
    Ping = 0x16,
    Pong = 0x17,
    Error = 0xFF,
}

impl FrameType {
    pub fn from_u8(val: u8) -> Option<Self> {
        match val {
            0x01 => Some(Self::Hello),
            0x02 => Some(Self::HelloResp),
            0x03 => Some(Self::Capabilities),
            0x04 => Some(Self::PairReq),
            0x05 => Some(Self::PairResp),
            0x06 => Some(Self::TransferOffer),
            0x07 => Some(Self::TransferAccept),
            0x08 => Some(Self::TransferReject),
            0x09 => Some(Self::Manifest),
            0x0A => Some(Self::ChunkReq),
            0x0B => Some(Self::ChunkData),
            0x0C => Some(Self::ChunkAck),
            0x0D => Some(Self::ChunkReject),
            0x0E => Some(Self::BenchmarkProbe),
            0x0F => Some(Self::BenchmarkResp),
            0x10 => Some(Self::TransportAdd),
            0x11 => Some(Self::TransportRemove),
            0x12 => Some(Self::TransferPause),
            0x13 => Some(Self::TransferResume),
            0x14 => Some(Self::TransferComplete),
            0x15 => Some(Self::TransferCancel),
            0x16 => Some(Self::Ping),
            0x17 => Some(Self::Pong),
            0xFF => Some(Self::Error),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FrameHeader {
    pub magic: [u8; 2],
    pub version: u8,
    pub frame_type: FrameType,
    pub flags: u16,
    pub transfer_id: Uuid,
    pub chunk_id: u32,
    pub payload_len: u32,
    pub crc32: u32,
}

impl FrameHeader {
    pub fn new(frame_type: FrameType, flags: u16, transfer_id: Uuid, chunk_id: u32, payload_len: u32) -> Self {
        let mut h = Self {
            magic: MAGIC_BYTES,
            version: PROTOCOL_VERSION,
            frame_type,
            flags,
            transfer_id,
            chunk_id,
            payload_len,
            crc32: 0,
        };
        h.crc32 = h.compute_crc32();
        h
    }

    pub fn compute_crc32(&self) -> u32 {
        let mut hasher = Crc32Hasher::new();
        hasher.update(&self.magic);
        hasher.update(&[self.version]);
        hasher.update(&[self.frame_type as u8]);
        hasher.update(&self.flags.to_be_bytes());
        hasher.update(self.transfer_id.as_bytes());
        hasher.update(&self.chunk_id.to_be_bytes());
        hasher.update(&self.payload_len.to_be_bytes());
        hasher.finalize()
    }

    pub fn encode(&self, dst: &mut BytesMut) {
        dst.put_slice(&self.magic);
        dst.put_u8(self.version);
        dst.put_u8(self.frame_type as u8);
        dst.put_u16(self.flags);
        dst.put_slice(self.transfer_id.as_bytes());
        dst.put_u32(self.chunk_id);
        dst.put_u32(self.payload_len);
        dst.put_u32(self.crc32);
    }

    pub fn decode(src: &mut BytesMut) -> Result<Option<Self>, io::Error> {
        if src.len() < FRAME_HEADER_LEN {
            return Ok(None);
        }

        if src[0] != MAGIC_BYTES[0] || src[1] != MAGIC_BYTES[1] {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("Invalid magic bytes: {:02X}{:02X}", src[0], src[1]),
            ));
        }

        let version = src[2];
        if version != PROTOCOL_VERSION {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("Unsupported protocol version: {}", version),
            ));
        }

        let frame_type = match FrameType::from_u8(src[3]) {
            Some(ft) => ft,
            None => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidData,
                    format!("Unknown frame type: 0x{:02X}", src[3]),
                ))
            }
        };

        let flags = u16::from_be_bytes([src[4], src[5]]);
        let mut uuid_bytes = [0u8; 16];
        uuid_bytes.copy_from_slice(&src[6..22]);
        let transfer_id = Uuid::from_bytes(uuid_bytes);
        let chunk_id = u32::from_be_bytes([src[22], src[23], src[24], src[25]]);
        let payload_len = u32::from_be_bytes([src[26], src[27], src[28], src[29]]);
        let expected_crc = u32::from_be_bytes([src[30], src[31], src[32], src[33]]);

        if payload_len as usize > MAX_PAYLOAD_SIZE {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("Payload length exceeds maximum: {} bytes", payload_len),
            ));
        }

        let header = Self {
            magic: [src[0], src[1]],
            version,
            frame_type,
            flags,
            transfer_id,
            chunk_id,
            payload_len,
            crc32: expected_crc,
        };

        let calculated_crc = header.compute_crc32();
        if calculated_crc != expected_crc {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                format!("CRC32 mismatch: expected {:08X}, calculated {:08X}", expected_crc, calculated_crc),
            ));
        }

        src.advance(FRAME_HEADER_LEN);
        Ok(Some(header))
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Frame {
    pub header: FrameHeader,
    pub payload: Bytes,
}

impl Frame {
    pub fn new(frame_type: FrameType, transfer_id: Uuid, chunk_id: u32, flags: u16, payload: Bytes) -> Self {
        let header = FrameHeader::new(frame_type, flags, transfer_id, chunk_id, payload.len() as u32);
        Self { header, payload }
    }

    pub fn control(frame_type: FrameType, payload: Bytes) -> Self {
        Self::new(frame_type, Uuid::nil(), 0, FLAG_NONE, payload)
    }

    pub fn chunk(transfer_id: Uuid, chunk_id: u32, payload: Bytes) -> Self {
        Self::new(FrameType::ChunkData, transfer_id, chunk_id, FLAG_NONE, payload)
    }

    pub fn is_control(&self) -> bool {
        !matches!(self.header.frame_type, FrameType::ChunkData | FrameType::BenchmarkProbe)
    }
}

#[derive(Default)]
pub struct FrameCodec {
    header: Option<FrameHeader>,
}

impl FrameCodec {
    pub fn new() -> Self {
        Self { header: None }
    }
}

impl Decoder for FrameCodec {
    type Item = Frame;
    type Error = io::Error;

    fn decode(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        if self.header.is_none() {
            if let Some(header) = FrameHeader::decode(src)? {
                self.header = Some(header);
            } else {
                return Ok(None);
            }
        }

        if let Some(ref header) = self.header {
            let required_len = header.payload_len as usize;
            if src.len() < required_len {
                src.reserve(required_len - src.len());
                return Ok(None);
            }

            let payload = src.split_to(required_len).freeze();
            let header = self.header.take().unwrap();
            Ok(Some(Frame { header, payload }))
        } else {
            Ok(None)
        }
    }
}

impl Encoder<Frame> for FrameCodec {
    type Error = io::Error;

    fn encode(&mut self, item: Frame, dst: &mut BytesMut) -> Result<(), Self::Error> {
        dst.reserve(FRAME_HEADER_LEN + item.payload.len());
        item.header.encode(dst);
        dst.put_slice(&item.payload);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_frame_encode_decode() {
        let mut codec = FrameCodec::new();
        let mut buf = BytesMut::new();

        let transfer_id = Uuid::new_v4();
        let payload_bytes = b"Hello ShareDash Multipath Transfer Engine!";
        let original_frame = Frame::new(
            FrameType::Hello,
            transfer_id,
            42,
            FLAG_ENCRYPTED,
            Bytes::from_static(payload_bytes),
        );

        codec.encode(original_frame.clone(), &mut buf).unwrap();
        assert_eq!(buf.len(), FRAME_HEADER_LEN + payload_bytes.len());

        let decoded = codec.decode(&mut buf).unwrap().expect("should decode frame");
        assert_eq!(decoded.header.frame_type, FrameType::Hello);
        assert_eq!(decoded.header.transfer_id, transfer_id);
        assert_eq!(decoded.header.chunk_id, 42);
        assert_eq!(decoded.header.flags, FLAG_ENCRYPTED);
        assert_eq!(decoded.payload.as_ref(), payload_bytes);
        assert!(buf.is_empty());
    }
}

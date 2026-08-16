use bytes::{Bytes, BytesMut};
use sharedash::protocol::{
    crypto::SessionCrypto,
    frame::{Frame, FrameCodec, FrameType, FLAG_ENCRYPTED},
    message::{ControlMessageWrapper, TransferOfferMessage},
};
use tokio_util::codec::{Decoder, Encoder};
use uuid::Uuid;

#[test]
fn test_binary_frame_integrity_and_crc32() {
    let mut codec = FrameCodec::new();
    let mut buffer = BytesMut::new();

    let transfer_id = Uuid::new_v4();
    let payload = Bytes::from_static(b"ShareDash High-Performance Multipath Payload Test");

    let original_frame = Frame::new(
        FrameType::ChunkData,
        transfer_id,
        1001,
        FLAG_ENCRYPTED,
        payload.clone(),
    );

    codec.encode(original_frame.clone(), &mut buffer).unwrap();
    let decoded = codec.decode(&mut buffer).unwrap().expect("Frame decoded");

    assert_eq!(decoded.header.frame_type, FrameType::ChunkData);
    assert_eq!(decoded.header.transfer_id, transfer_id);
    assert_eq!(decoded.header.chunk_id, 1001);
    assert_eq!(decoded.header.flags, FLAG_ENCRYPTED);
    assert_eq!(decoded.payload, payload);
}

#[test]
fn test_crypto_session_encryption_and_hashing() {
    let key = SessionCrypto::generate_random_key();
    let crypto = SessionCrypto::from_key(key);

    let plaintext = b"Confidential chunk data streaming over untrusted public network";
    let ciphertext = crypto.encrypt_payload(plaintext).unwrap();
    assert_ne!(ciphertext.as_ref(), plaintext);

    let decrypted = crypto.decrypt_payload(&ciphertext).unwrap();
    assert_eq!(decrypted.as_ref(), plaintext);

    let sha256 = SessionCrypto::compute_sha256(plaintext);
    let blake3 = SessionCrypto::compute_blake3(plaintext);
    assert_eq!(sha256.len(), 64);
    assert_eq!(blake3.len(), 64);
}

#[test]
fn test_control_messages_serialization() {
    let offer = TransferOfferMessage {
        transfer_id: Uuid::new_v4(),
        sender_name: "MacBook Pro M3".to_string(),
        total_files: 5,
        total_bytes: 1024 * 1024 * 1024,
        chunk_size: 4 * 1024 * 1024,
        total_chunks: 256,
        root_hash: "abcd0123456789".to_string(),
        preview_files: vec!["movie.mp4".to_string()],
    };

    let serialized = ControlMessageWrapper::to_bytes(&offer).unwrap();
    let deserialized: TransferOfferMessage = ControlMessageWrapper::from_bytes(&serialized).unwrap();

    assert_eq!(deserialized.sender_name, "MacBook Pro M3");
    assert_eq!(deserialized.total_files, 5);
    assert_eq!(deserialized.total_chunks, 256);
}

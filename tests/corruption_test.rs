use bytes::Bytes;
use sharedash::protocol::crypto::SessionCrypto;
use sharedash::protocol::frame::{Frame, FrameType};
use sharedash::protocol::message::{FileMetadata, TransportKind};
use sharedash::scheduler::dynamic_scheduler::MultipathScheduler;
use sharedash::storage::chunker::{ChunkInfo, TransferManifest};
use sharedash::storage::manifest_db::ManifestDb;
use sharedash::storage::sparse_writer::SparseWriter;
use sharedash::transport::mock_sim::MockSimTransport;
use sharedash::transport::r#trait::AsyncTransport;
use std::collections::HashMap;
use std::sync::Arc;
use tempfile::tempdir;
use uuid::Uuid;

#[tokio::test]
async fn test_cryptographic_corruption_rejection_and_recovery() {
    let temp_dir = tempdir().unwrap();
    let transfer_id = Uuid::new_v4();

    // 8 MB test payload split into 8 chunks (1 MB each)
    let total_bytes: u64 = 8 * 1024 * 1024;
    let chunk_size: u32 = 1024 * 1024;
    let total_chunks = 8;

    let mut generated_payload = Vec::new();
    let mut chunks = Vec::new();

    for i in 0..total_chunks {
        let chunk_data = vec![((i * 31) % 253) as u8; chunk_size as usize];
        let sha256 = SessionCrypto::compute_sha256(&chunk_data);
        let blake3 = SessionCrypto::compute_blake3(&chunk_data);

        chunks.push(ChunkInfo {
            chunk_id: i as u32,
            file_index: 0,
            offset: i as u64 * chunk_size as u64,
            length: chunk_size,
            sha256,
            blake3,
        });

        generated_payload.extend_from_slice(&chunk_data);
    }

    let file_meta = FileMetadata {
        file_index: 0,
        relative_path: "integrity_test.bin".to_string(),
        size_bytes: total_bytes,
        modified_timestamp: 1700000000,
        is_executable: false,
        chunk_start_index: 0,
        chunk_count: total_chunks as u32,
        sha256_hash: SessionCrypto::compute_sha256(&generated_payload),
    };

    let manifest = TransferManifest {
        transfer_id,
        title: "integrity_test.bin".to_string(),
        total_bytes,
        total_files: 1,
        chunk_size,
        total_chunks: total_chunks as u32,
        root_hash: "integrity_merkle_root".to_string(),
        files: vec![file_meta.clone()],
        chunks: chunks.clone(),
    };

    let db = Arc::new(ManifestDb::open_in_memory().unwrap());
    db.save_transfer(&manifest, false, temp_dir.path().to_str().unwrap()).unwrap();

    let writer = Arc::new(SparseWriter::new(temp_dir.path(), vec![file_meta]).unwrap());

    // Pair 1: Wi-Fi with deliberate chunk corruption injected on sender
    let (mut sender_corrupt, receiver_wifi) = MockSimTransport::pair(
        "Corrupted Wi-Fi",
        "Corrupted Wi-Fi (Rx)",
        TransportKind::WifiDirect,
        200.0,
        2,
    );

    // Pair 2: Clean USB transport
    let (mut sender_clean, receiver_clean) = MockSimTransport::pair(
        "Clean USB",
        "Clean USB (Rx)",
        TransportKind::Usb,
        400.0,
        1,
    );

    // Inject corruption on the first chunk sent by Wi-Fi
    sender_corrupt.inject_chunk_corruption();

    let chunks_map: Arc<HashMap<u32, Vec<u8>>> = Arc::new(
        (0..total_chunks)
            .map(|i| (i as u32, vec![((i * 31) % 253) as u8; chunk_size as usize]))
            .collect(),
    );

    let map1 = chunks_map.clone();
    tokio::spawn(async move {
        mock_responder(&mut sender_corrupt, map1).await;
    });

    let map2 = chunks_map.clone();
    tokio::spawn(async move {
        mock_responder(&mut sender_clean, map2).await;
    });

    let (scheduler, _handle) = MultipathScheduler::new(
        manifest,
        &[],
        Some(writer),
        Some(db.clone()),
    );

    let transports: Vec<Box<dyn AsyncTransport>> = vec![
        Box::new(receiver_wifi),
        Box::new(receiver_clean),
    ];

    scheduler.run_receiver(transports).await.unwrap();

    // Verify all chunks completed successfully
    let uncompleted = db.get_uncompleted_chunks(transfer_id).unwrap();
    assert!(uncompleted.is_empty(), "All chunks should complete cleanly");

    // Verify final file is uncorrupted bit-by-bit
    let destination_file = temp_dir.path().join("integrity_test.bin");
    let written_bytes = std::fs::read(&destination_file).unwrap();
    assert_eq!(written_bytes, generated_payload);
}

async fn mock_responder(transport: &mut MockSimTransport, chunks: Arc<HashMap<u32, Vec<u8>>>) {
    while let Ok(Some(frame)) = transport.recv_frame().await {
        if frame.header.frame_type == FrameType::ChunkReq {
            let chunk_id = frame.header.chunk_id;
            if let Some(payload) = chunks.get(&chunk_id) {
                let resp_frame = Frame::new(
                    FrameType::ChunkData,
                    frame.header.transfer_id,
                    chunk_id,
                    0,
                    Bytes::from(payload.clone()),
                );
                let _ = transport.send_frame(resp_frame).await;
            }
        }
    }
}

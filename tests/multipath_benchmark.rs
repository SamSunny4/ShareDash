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
async fn test_multipath_concurrent_transports() {
    let temp_dir = tempdir().unwrap();
    let transfer_id = Uuid::new_v4();

    // 20 MB test payload split into 20 chunks (1 MB each)
    let total_bytes: u64 = 20 * 1024 * 1024;
    let chunk_size: u32 = 1024 * 1024;
    let total_chunks = 20;

    let mut generated_payload = Vec::new();
    let mut chunks = Vec::new();

    for i in 0..total_chunks {
        let chunk_data = vec![((i * 13) % 251) as u8; chunk_size as usize];
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
        relative_path: "multipath_test_video.mkv".to_string(),
        size_bytes: total_bytes,
        modified_timestamp: 1700000000,
        is_executable: false,
        chunk_start_index: 0,
        chunk_count: total_chunks as u32,
        sha256_hash: SessionCrypto::compute_sha256(&generated_payload),
    };

    let manifest = TransferManifest {
        transfer_id,
        title: "multipath_test_video.mkv".to_string(),
        total_bytes,
        total_files: 1,
        chunk_size,
        total_chunks: total_chunks as u32,
        root_hash: "root_merkle_test".to_string(),
        files: vec![file_meta.clone()],
        chunks: chunks.clone(),
    };

    let db = Arc::new(ManifestDb::open_in_memory().unwrap());
    db.save_transfer(&manifest, false, temp_dir.path().to_str().unwrap()).unwrap();

    let writer = Arc::new(SparseWriter::new(temp_dir.path(), vec![file_meta]).unwrap());

    // Setup 3 concurrent transports:
    // 1. Fast USB 3.2 (@ 800 MB/s simulated)
    // 2. Wi-Fi Direct (@ 300 MB/s simulated)
    // 3. LAN Wi-Fi (@ 150 MB/s simulated)
    let (mut sender_usb, receiver_usb) = MockSimTransport::pair(
        "USB 3.2",
        "USB 3.2 (Rx)",
        TransportKind::Usb,
        800.0,
        1,
    );

    let (mut sender_wifi, receiver_wifi) = MockSimTransport::pair(
        "Wi-Fi Direct",
        "Wi-Fi Direct (Rx)",
        TransportKind::WifiDirect,
        300.0,
        2,
    );

    let (mut sender_lan, receiver_lan) = MockSimTransport::pair(
        "LAN 5GHz",
        "LAN 5GHz (Rx)",
        TransportKind::Lan,
        150.0,
        5,
    );

    // Spawn mock responders
    let chunks_map: Arc<HashMap<u32, Vec<u8>>> = Arc::new(
        (0..total_chunks)
            .map(|i| (i as u32, vec![((i * 13) % 251) as u8; chunk_size as usize]))
            .collect(),
    );

    let map1 = chunks_map.clone();
    tokio::spawn(async move {
        mock_responder(&mut sender_usb, map1).await;
    });

    let map2 = chunks_map.clone();
    tokio::spawn(async move {
        mock_responder(&mut sender_wifi, map2).await;
    });

    let map3 = chunks_map.clone();
    tokio::spawn(async move {
        mock_responder(&mut sender_lan, map3).await;
    });

    let (scheduler, _handle) = MultipathScheduler::new(
        manifest,
        &[],
        Some(writer),
        Some(db.clone()),
    );

    let transports: Vec<Box<dyn AsyncTransport>> = vec![
        Box::new(receiver_usb),
        Box::new(receiver_wifi),
        Box::new(receiver_lan),
    ];

    scheduler.run_receiver(transports).await.unwrap();

    // Verify all chunks are marked completed in DB
    let uncompleted = db.get_uncompleted_chunks(transfer_id).unwrap();
    assert!(uncompleted.is_empty(), "All chunks should be completed");

    // Verify bit-for-bit file equality
    let destination_file = temp_dir.path().join("multipath_test_video.mkv");
    assert!(destination_file.exists());

    let written_bytes = std::fs::read(&destination_file).unwrap();
    assert_eq!(written_bytes.len(), total_bytes as usize);
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

#[tokio::test]
async fn test_pc_to_pc_hotspot_script_generation() {
    let script = sharedash::hotspot::generate_standalone_hotspot_script("ShareDash-PC-Test", "secretpass123");
    assert!(script.contains("ShareDash-PC-Test"));
    assert!(script.contains("secretpass123"));
    assert!(script.contains("NetworkOperatorTetheringManager"));

    let script_path = sharedash::hotspot::write_hotspot_script_to_disk("ShareDash-PC-Test", "secretpass123");
    assert!(script_path.is_ok());
    let path = script_path.unwrap();
    assert!(path.exists());
    let _ = std::fs::remove_file(path);
}

#[tokio::test]
async fn test_scan_direct_usb_pc_peers() {
    let peers = sharedash::hotspot::scan_direct_usb_pc_peers().await;
    println!("Direct PC peer scan result: {} peers found", peers.len());
    for p in peers {
        println!("  • Peer: {} ({}:{}) - Direct: {}", p.device_name, p.ip, p.port, p.is_usb_direct);
    }
}

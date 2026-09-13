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

#[test]
fn test_select_optimal_pc_to_pc_host_and_band() {
    use sharedash::discovery::WifiCapsInfo;
    use sharedash::hotspot::select_optimal_pc_to_pc_host;

    let wifi6e_pc = WifiCapsInfo {
        wifi_standard: "Wi-Fi 6E (802.11ax)".to_string(),
        max_frequency_ghz: 6.0,
        max_channel_width_mhz: 160,
        max_phy_rate_mbps: 2402,
        supported_bands: vec!["2.4 GHz".to_string(), "5 GHz".to_string(), "6 GHz".to_string()],
    };

    let wifi6_pc = WifiCapsInfo {
        wifi_standard: "Wi-Fi 6 (802.11ax)".to_string(),
        max_frequency_ghz: 5.0,
        max_channel_width_mhz: 160,
        max_phy_rate_mbps: 1200,
        supported_bands: vec!["2.4 GHz".to_string(), "5 GHz".to_string()],
    };

    let wifi5_pc = WifiCapsInfo {
        wifi_standard: "Wi-Fi 5 (802.11ac)".to_string(),
        max_frequency_ghz: 5.0,
        max_channel_width_mhz: 80,
        max_phy_rate_mbps: 866,
        supported_bands: vec!["2.4 GHz".to_string(), "5 GHz".to_string()],
    };

    // Case 1: Wi-Fi 6E (2402 Mbps) vs Wi-Fi 6 (1200 Mbps)
    // Common band is 5 GHz; Wi-Fi 6E PC has higher PHY rate so it should host
    let (i_am_host, band, reason) = select_optimal_pc_to_pc_host(&wifi6e_pc, &wifi6_pc, "Laptop-A", "Laptop-B");
    assert!(i_am_host);
    assert_eq!(band, "5 GHz");
    assert!(reason.contains("Laptop-A"));

    // Case 2: Wi-Fi 6 (1200 Mbps) vs Wi-Fi 6E (2402 Mbps) from Laptop-B perspective
    let (i_am_host2, band2, _) = select_optimal_pc_to_pc_host(&wifi6_pc, &wifi6e_pc, "Laptop-B", "Laptop-A");
    assert!(!i_am_host2);
    assert_eq!(band2, "5 GHz");

    // Case 3: Both PCs have Wi-Fi 6E (6 GHz supported by both!)
    let (i_am_host3, band3, _) = select_optimal_pc_to_pc_host(&wifi6e_pc, &wifi6e_pc, "Laptop-A", "Laptop-B");
    assert_eq!(band3, "6 GHz");
    assert!(i_am_host3); // tie-breaker Laptop-A <= Laptop-B

    // Case 4: Wi-Fi 6 vs Wi-Fi 5
    let (i_am_host4, band4, _) = select_optimal_pc_to_pc_host(&wifi6_pc, &wifi5_pc, "Laptop-A", "Laptop-B");
    assert!(i_am_host4);
    assert_eq!(band4, "5 GHz");
}


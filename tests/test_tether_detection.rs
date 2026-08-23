#[tokio::test]
async fn test_detect_usb_tethering() {
    let result = sharedash::hotspot::detect_usb_tethering_peer_detailed().await;
    println!("USB Tethering Detection Result: {:?}", result);
    if let Some((ip, name)) = result {
        println!("Successfully detected phone over USB Tethering: IP={}, Name={}", ip, name);
    } else {
        println!("No USB tethering device detected (ensure USB tethering is enabled)");
    }
}

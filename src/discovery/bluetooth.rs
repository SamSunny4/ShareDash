use anyhow::Result;
use btleplug::api::{Central, Manager as _, Peripheral, ScanFilter, CentralEvent, WriteType};
use btleplug::platform::{Adapter, Manager};
use chrono::Utc;
use futures::StreamExt;
use parking_lot::Mutex;
use std::collections::HashMap;
use std::net::{Ipv4Addr, SocketAddr};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;
use tokio::time::sleep;
use uuid::Uuid as BleUuid;

use super::{DiscoveredPeer, WifiCapsInfo};

/// ShareDash BLE Service UUID — matches the Android app's service UUID
/// Android uses 0x5344 short UUID which expands to: 00005344-0000-1000-8000-00805F9B34FB
pub const SHAREDASH_BLE_SERVICE_UUID: &str = "00005344-0000-1000-8000-00805f9b34fb";
pub const WIFI_CAPS_CHAR_UUID: &str = "00005345-0000-1000-8000-00805f9b34fb";
pub const COMMAND_CHAR_UUID: &str = "00005346-0000-1000-8000-00805f9b34fb";
pub const RESPONSE_CHAR_UUID: &str = "00005347-0000-1000-8000-00805f9b34fb";

/// How a peer was discovered
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum DiscoveryMethod {
    Ble,
    Udp,
    HttpProbe,
}

use serde::{Deserialize, Serialize};

/// BLE Discovery manager for Windows
#[derive(Clone)]
pub struct BleDiscovery {
    peers: Arc<Mutex<HashMap<String, DiscoveredPeer>>>,
    adapter: Arc<Mutex<Option<Adapter>>>,
    stop_flag: Arc<AtomicBool>,
    is_available: Arc<AtomicBool>,
}

impl BleDiscovery {
    pub fn new() -> Self {
        Self {
            peers: Arc::new(Mutex::new(HashMap::new())),
            adapter: Arc::new(Mutex::new(None)),
            stop_flag: Arc::new(AtomicBool::new(false)),
            is_available: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Check if BLE hardware is available on this system
    pub async fn check_available() -> bool {
        match Manager::new().await {
            Ok(manager) => {
                match manager.adapters().await {
                    Ok(adapters) => !adapters.is_empty(),
                    Err(_) => false,
                }
            }
            Err(_) => false,
        }
    }

    /// Get active adapter reference
    pub fn get_adapter(&self) -> Option<Adapter> {
        self.adapter.lock().clone()
    }

    /// Start BLE scanning in the background
    pub async fn start(&self) -> Result<()> {
        let manager = match Manager::new().await {
            Ok(m) => m,
            Err(e) => {
                tracing::warn!("BLE not available on this system: {}. Continuing without Bluetooth.", e);
                return Ok(());
            }
        };

        let adapters = match manager.adapters().await {
            Ok(a) if !a.is_empty() => a,
            Ok(_) => {
                tracing::warn!("No BLE adapters found. Continuing without Bluetooth.");
                return Ok(());
            }
            Err(e) => {
                tracing::warn!("Failed to enumerate BLE adapters: {}. Continuing without Bluetooth.", e);
                return Ok(());
            }
        };

        let adapter = adapters.into_iter().next().unwrap();
        *self.adapter.lock() = Some(adapter.clone());
        self.is_available.store(true, Ordering::SeqCst);
        tracing::info!("BLE adapter found. Starting Bluetooth Low Energy scanning...");

        // Start scanning with no filter (we'll filter by service UUID manually)
        let scan_filter = ScanFilter::default();
        if let Err(e) = adapter.start_scan(scan_filter).await {
            tracing::warn!("Failed to start BLE scan: {}. Continuing without Bluetooth.", e);
            return Ok(());
        }

        let peers_map = self.peers.clone();
        let stop_flag = self.stop_flag.clone();

        // Spawn the event-driven scanner
        let scan_adapter = adapter.clone();
        tokio::spawn(async move {
            Self::run_scan_loop(scan_adapter, peers_map, stop_flag).await;
        });

        // Spawn stale peer eviction loop (30 second TTL)
        let peers_evict = self.peers.clone();
        let stop_evict = self.stop_flag.clone();
        tokio::spawn(async move {
            while !stop_evict.load(Ordering::SeqCst) {
                sleep(Duration::from_secs(10)).await;
                let now = Utc::now().timestamp_millis();
                peers_evict.lock().retain(|_, p| now - p.last_seen_epoch_ms < 30_000);
            }
        });

        Ok(())
    }

    async fn run_scan_loop(
        adapter: Adapter,
        peers_map: Arc<Mutex<HashMap<String, DiscoveredPeer>>>,
        stop_flag: Arc<AtomicBool>,
    ) {
        // Try to get an event stream for continuous scanning
        let mut events = match adapter.events().await {
            Ok(e) => e,
            Err(e) => {
                tracing::warn!("BLE event stream unavailable: {}. Falling back to polling.", e);
                // Fallback: poll peripherals periodically
                Self::run_poll_loop(adapter, peers_map, stop_flag).await;
                return;
            }
        };

        while !stop_flag.load(Ordering::SeqCst) {
            tokio::select! {
                Some(event) = events.next() => {
                    match event {
                        CentralEvent::DeviceDiscovered(id) |
                        CentralEvent::DeviceUpdated(id) => {
                            if let Ok(peripherals) = adapter.peripherals().await {
                                for peripheral in peripherals {
                                    if peripheral.id() == id {
                                        Self::process_peripheral(&peripheral, &peers_map).await;
                                    }
                                }
                            }
                        }
                        _ => {}
                    }
                }
                _ = sleep(Duration::from_secs(30)) => {
                    // Periodic re-scan to keep discovery active
                    let _ = adapter.start_scan(ScanFilter::default()).await;
                }
            }
        }

        let _ = adapter.stop_scan().await;
    }

    async fn run_poll_loop(
        adapter: Adapter,
        peers_map: Arc<Mutex<HashMap<String, DiscoveredPeer>>>,
        stop_flag: Arc<AtomicBool>,
    ) {
        while !stop_flag.load(Ordering::SeqCst) {
            if let Ok(peripherals) = adapter.peripherals().await {
                for peripheral in &peripherals {
                    Self::process_peripheral(peripheral, &peers_map).await;
                }
            }
            sleep(Duration::from_secs(3)).await;
        }
        let _ = adapter.stop_scan().await;
    }

    async fn process_peripheral(
        peripheral: &impl Peripheral,
        peers_map: &Arc<Mutex<HashMap<String, DiscoveredPeer>>>,
    ) {
        let properties = match peripheral.properties().await {
            Ok(Some(props)) => props,
            _ => return,
        };

        // Check if this device advertises the ShareDash service UUID
        let sharedash_uuid = match BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID) {
            Ok(u) => u,
            Err(_) => return,
        };

        let has_sharedash_service = properties.services.iter().any(|s| *s == sharedash_uuid)
            || properties.service_data.contains_key(&sharedash_uuid);

        if !has_sharedash_service {
            return;
        }

        let device_name = properties.local_name.unwrap_or_else(|| "Android Device".to_string());

        // Try to extract IP address, port, and Wi-Fi capabilities from service data
        let mut wifi_caps: Option<WifiCapsInfo> = None;
        let (ip_addr, port) = if let Some(service_data) = properties.service_data.get(&sharedash_uuid) {
            if service_data.len() >= 12 {
                let ip = Ipv4Addr::new(service_data[0], service_data[1], service_data[2], service_data[3]);
                let port = ((service_data[4] as u16) << 8) | (service_data[5] as u16);
                let std_num = service_data[6];
                let max_freq = (service_data[7] as f64) / 10.0;
                let max_bw = service_data[8] as u32;
                let max_phy = ((service_data[9] as u32) << 8) | (service_data[10] as u32);
                let bands_mask = service_data[11];
                let mut bands = vec!["2.4 GHz".to_string()];
                if bands_mask & 0x02 != 0 { bands.push("5 GHz".to_string()); }
                if bands_mask & 0x04 != 0 { bands.push("6 GHz".to_string()); }
                let standard_str = match std_num {
                    7 => "Wi-Fi 7 (802.11be)",
                    6 if max_freq >= 6.0 => "Wi-Fi 6E (802.11ax)",
                    6 => "Wi-Fi 6 (802.11ax)",
                    _ => "Wi-Fi 5 (802.11ac)",
                };
                wifi_caps = Some(WifiCapsInfo {
                    wifi_standard: standard_str.to_string(),
                    max_frequency_ghz: if max_freq > 0.0 { max_freq } else { 5.0 },
                    max_channel_width_mhz: if max_bw > 0 { max_bw } else { 160 },
                    max_phy_rate_mbps: if max_phy > 0 { max_phy } else { 1200 },
                    supported_bands: bands,
                });
                (Some(ip), port)
            } else if service_data.len() >= 6 {
                let ip = Ipv4Addr::new(service_data[0], service_data[1], service_data[2], service_data[3]);
                let port = ((service_data[4] as u16) << 8) | (service_data[5] as u16);
                (Some(ip), port)
            } else if service_data.len() >= 4 {
                let ip = Ipv4Addr::new(service_data[0], service_data[1], service_data[2], service_data[3]);
                (Some(ip), 54321)
            } else {
                (None, 54321)
            }
        } else {
            (None, 54321)
        };

        // If IP from BLE is 0.0.0.0 or not set, check if PC is connected to Phone Hotspot (192.168.43.x / 192.168.49.x)
        let resolved_ip = match ip_addr {
            Some(ip) if !ip.is_unspecified() && ip != Ipv4Addr::new(127, 0, 0, 1) => Some(ip),
            _ => {
                if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
                    let mut gw = None;
                    for (_, iface_ip) in interfaces {
                        let ip_str = iface_ip.to_string();
                        if ip_str.starts_with("192.168.43.") && ip_str != "192.168.43.1" {
                            gw = Some(Ipv4Addr::new(192, 168, 43, 1));
                            break;
                        } else if ip_str.starts_with("192.168.49.") && ip_str != "192.168.49.1" {
                            gw = Some(Ipv4Addr::new(192, 168, 49, 1));
                            break;
                        }
                    }
                    gw
                } else {
                    None
                }
            }
        };

        // Construct device ID from BLE address
        let ble_address = format!("{:?}", peripheral.id());
        let device_id = format!("ble-{}", ble_address.replace([':', '-', ' ', '{', '}'], "").chars().take(12).collect::<String>());

        if let Some(ip) = resolved_ip {
            if let Ok(remote_addr) = format!("{}:{}", ip, port).parse::<SocketAddr>() {
                let peer = DiscoveredPeer {
                    device_id: device_id.clone(),
                    friendly_name: device_name,
                    os_name: "Android".to_string(),
                    remote_addr,
                    server_port: port,
                    app_version: super::CURRENT_APP_VERSION.to_string(),
                    is_compatible: true,
                    supported_transports: vec!["Bluetooth LE".to_string(), "Wi-Fi Direct".to_string(), "LAN".to_string()],
                    wifi_caps,
                    last_seen_epoch_ms: Utc::now().timestamp_millis(),
                };
                peers_map.lock().insert(device_id, peer);
            }
        } else {
            let peer = DiscoveredPeer {
                device_id: device_id.clone(),
                friendly_name: device_name,
                os_name: "Android".to_string(),
                remote_addr: "0.0.0.0:0".parse().unwrap(),
                server_port: port,
                app_version: super::CURRENT_APP_VERSION.to_string(),
                is_compatible: true,
                supported_transports: vec!["Bluetooth LE".to_string(), "Wi-Fi Direct".to_string()],
                wifi_caps,
                last_seen_epoch_ms: Utc::now().timestamp_millis(),
            };
            peers_map.lock().insert(device_id, peer);
        }
    }

    /// Read Wi-Fi capabilities from GATT or cached peer
    pub async fn read_wifi_capabilities(&self) -> Option<WifiCapsInfo> {
        // 1. Check if any peer has decoded wifi_caps from advertisement
        for peer in self.get_ble_peers() {
            if let Some(ref caps) = peer.wifi_caps {
                return Some(caps.clone());
            }
        }

        // 2. Try GATT read via the active adapter
        let adapter = self.get_adapter()?;
        let sharedash_uuid = BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID).ok()?;
        let wifi_caps_uuid = BleUuid::parse_str(WIFI_CAPS_CHAR_UUID).ok()?;

        if let Ok(peripherals) = adapter.peripherals().await {
            for peripheral in peripherals {
                if let Ok(Some(props)) = peripheral.properties().await {
                    let has_sd = props.services.iter().any(|s| *s == sharedash_uuid)
                        || props.service_data.contains_key(&sharedash_uuid)
                        || props.local_name.as_ref().map(|n| n.contains("ShareDash") || n.contains("Pixel") || n.contains("Galaxy") || n.contains("Android") || n.contains("Sam") || n.contains("Phone")).unwrap_or(false);

                    if has_sd {
                        for _ in 0..2 {
                            let is_conn = peripheral.is_connected().await.unwrap_or(false);
                            if is_conn || peripheral.connect().await.is_ok() {
                                tokio::time::sleep(Duration::from_millis(100)).await;
                                if peripheral.discover_services().await.is_ok() {
                                    for ch in peripheral.characteristics() {
                                        if ch.uuid == wifi_caps_uuid {
                                            if let Ok(val) = peripheral.read(&ch).await {
                                                let _ = peripheral.disconnect().await;
                                                let json_str = String::from_utf8_lossy(&val);
                                                if let Ok(caps) = serde_json::from_str::<WifiCapsInfo>(&json_str) {
                                                    return Some(caps);
                                                }
                                            }
                                        }
                                    }
                                }
                                let _ = peripheral.disconnect().await;
                            }
                            tokio::time::sleep(Duration::from_millis(150)).await;
                        }
                    }
                }
            }
        }

        None
    }

    /// Send GATT command to peripheral with robust discovery & retry
    pub async fn send_gatt_command(&self, cmd_json: &str) -> bool {
        let adapter = match self.get_adapter() {
            Some(a) => a,
            None => return false,
        };
        let sharedash_uuid = match BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID) {
            Ok(u) => u,
            Err(_) => return false,
        };
        let command_uuid = match BleUuid::parse_str(COMMAND_CHAR_UUID) {
            Ok(u) => u,
            Err(_) => return false,
        };

        if let Ok(peripherals) = adapter.peripherals().await {
            for peripheral in peripherals {
                if let Ok(Some(props)) = peripheral.properties().await {
                    let has_sd = props.services.iter().any(|s| *s == sharedash_uuid)
                        || props.service_data.contains_key(&sharedash_uuid)
                        || props.local_name.as_ref().map(|n| n.contains("ShareDash") || n.contains("Pixel") || n.contains("Galaxy") || n.contains("Android") || n.contains("Phone") || n.contains("Xiaomi") || n.contains("OnePlus") || n.contains("Sam")).unwrap_or(false);
                    if has_sd {
                        for attempt in 0..3 {
                            let is_conn = peripheral.is_connected().await.unwrap_or(false);
                            if is_conn || peripheral.connect().await.is_ok() {
                                tokio::time::sleep(Duration::from_millis(100)).await;
                                if peripheral.discover_services().await.is_ok() {
                                    for ch in peripheral.characteristics() {
                                        if ch.uuid == command_uuid {
                                            let res = peripheral.write(&ch, cmd_json.as_bytes(), WriteType::WithResponse).await;
                                            if res.is_ok() {
                                                tokio::time::sleep(Duration::from_millis(50)).await;
                                                let _ = peripheral.disconnect().await;
                                                return true;
                                            }
                                            let res2 = peripheral.write(&ch, cmd_json.as_bytes(), WriteType::WithoutResponse).await;
                                            if res2.is_ok() {
                                                tokio::time::sleep(Duration::from_millis(50)).await;
                                                let _ = peripheral.disconnect().await;
                                                return true;
                                            }
                                        }
                                    }
                                }
                                let _ = peripheral.disconnect().await;
                            }
                            if attempt < 2 {
                                tokio::time::sleep(Duration::from_millis(150)).await;
                            }
                        }
                    }
                }
            }
        }
        false
    }

    /// Send GATT command and read the response from the RESPONSE characteristic.
    pub async fn send_gatt_command_and_read_response(&self, cmd_json: &str) -> Option<String> {
        let adapter = self.get_adapter()?;
        let sharedash_uuid = BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID).ok()?;
        let command_uuid = BleUuid::parse_str(COMMAND_CHAR_UUID).ok()?;
        let response_uuid = BleUuid::parse_str(RESPONSE_CHAR_UUID).ok()?;

        if let Ok(peripherals) = adapter.peripherals().await {
            for peripheral in peripherals {
                if let Ok(Some(props)) = peripheral.properties().await {
                    let has_sd = props.services.iter().any(|s| *s == sharedash_uuid)
                        || props.service_data.contains_key(&sharedash_uuid)
                        || props.local_name.as_ref().map(|n| n.contains("ShareDash") || n.contains("Pixel") || n.contains("Galaxy") || n.contains("Android") || n.contains("Phone") || n.contains("Sam")).unwrap_or(false);
                    if has_sd {
                        for _ in 0..3 {
                            let is_conn = peripheral.is_connected().await.unwrap_or(false);
                            if is_conn || peripheral.connect().await.is_ok() {
                                tokio::time::sleep(Duration::from_millis(100)).await;
                                if peripheral.discover_services().await.is_ok() {
                                    let mut cmd_char = None;
                                    let mut resp_char = None;
                                    for ch in peripheral.characteristics() {
                                        if ch.uuid == command_uuid {
                                            cmd_char = Some(ch);
                                        } else if ch.uuid == response_uuid {
                                            resp_char = Some(ch);
                                        }
                                    }

                                    if let (Some(ref cmd_ch), Some(ref resp_ch)) = (&cmd_char, &resp_char) {
                                        // Write command
                                        let write_ok = peripheral.write(cmd_ch, cmd_json.as_bytes(), WriteType::WithResponse).await.is_ok()
                                            || peripheral.write(cmd_ch, cmd_json.as_bytes(), WriteType::WithoutResponse).await.is_ok();
                                        if write_ok {
                                            // Wait for phone to process and write response
                                            tokio::time::sleep(Duration::from_millis(300)).await;

                                            // Poll response characteristic
                                            for _ in 0..6 {
                                                if let Ok(val) = peripheral.read(resp_ch).await {
                                                    let response_str = String::from_utf8_lossy(&val).to_string();
                                                    if !response_str.is_empty() && (response_str.contains("status") || response_str.contains("pong")) {
                                                        let _ = peripheral.disconnect().await;
                                                        return Some(response_str);
                                                    }
                                                }
                                                tokio::time::sleep(Duration::from_millis(300)).await;
                                            }
                                        }
                                    }
                                }
                                let _ = peripheral.disconnect().await;
                            }
                            tokio::time::sleep(Duration::from_millis(200)).await;
                        }
                    }
                }
            }
        }
        None
    }

    /// Request the phone to start its 5GHz Hotspot and return (ssid, password, gateway)
    pub async fn request_phone_start_hotspot(&self) -> Option<(String, String, String)> {
        let adapter = self.get_adapter()?;
        let sharedash_uuid = BleUuid::parse_str(SHAREDASH_BLE_SERVICE_UUID).ok()?;
        let command_uuid = BleUuid::parse_str(COMMAND_CHAR_UUID).ok()?;
        let response_uuid = BleUuid::parse_str(RESPONSE_CHAR_UUID).ok()?;
        let cmd_json = serde_json::json!({"cmd": "start_hotspot"}).to_string();

        if let Ok(peripherals) = adapter.peripherals().await {
            for peripheral in peripherals {
                if let Ok(Some(props)) = peripheral.properties().await {
                    let has_sd = props.services.iter().any(|s| *s == sharedash_uuid)
                        || props.service_data.contains_key(&sharedash_uuid)
                        || props.local_name.as_ref().map(|n| n.contains("ShareDash") || n.contains("Pixel") || n.contains("Galaxy") || n.contains("Android") || n.contains("Phone") || n.contains("Sam")).unwrap_or(false);
                    if has_sd {
                        for _ in 0..3 {
                            let is_conn = peripheral.is_connected().await.unwrap_or(false);
                            if is_conn || peripheral.connect().await.is_ok() {
                                tokio::time::sleep(Duration::from_millis(100)).await;
                                if peripheral.discover_services().await.is_ok() {
                                    let mut cmd_char = None;
                                    let mut resp_char = None;
                                    for ch in peripheral.characteristics() {
                                        if ch.uuid == command_uuid {
                                            cmd_char = Some(ch);
                                        } else if ch.uuid == response_uuid {
                                            resp_char = Some(ch);
                                        }
                                    }

                                    if let (Some(ref cmd_ch), Some(ref resp_ch)) = (&cmd_char, &resp_char) {
                                        let write_ok = peripheral.write(cmd_ch, cmd_json.as_bytes(), WriteType::WithResponse).await.is_ok()
                                            || peripheral.write(cmd_ch, cmd_json.as_bytes(), WriteType::WithoutResponse).await.is_ok();
                                        if write_ok {
                                            // Wait for phone to disconnect client Wi-Fi and bring up 5GHz SoftAP
                                            for _ in 0..25 {
                                                tokio::time::sleep(Duration::from_millis(500)).await;
                                                if let Ok(val) = peripheral.read(resp_ch).await {
                                                    let response_str = String::from_utf8_lossy(&val).to_string();
                                                    if response_str.contains("hotspot_started") {
                                                        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&response_str) {
                                                            let ssid = json.get("ssid").and_then(|v| v.as_str()).unwrap_or("ShareDash-5G").to_string();
                                                            let pass = json.get("password").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                                            let gw = json.get("gateway").and_then(|v| v.as_str()).unwrap_or("192.168.43.1").to_string();
                                                            let _ = peripheral.disconnect().await;
                                                            return Some((ssid, pass, gw));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                let _ = peripheral.disconnect().await;
                            }
                            tokio::time::sleep(Duration::from_millis(300)).await;
                        }
                    }
                }
            }
        }
        None
    }

    /// Send Wi-Fi connect credentials command to phone over BLE GATT
    pub async fn send_wifi_connect_cmd(&self, ssid: &str, password: &str) -> bool {
        let cmd = serde_json::json!({
            "cmd": "wifi_connect",
            "ssid": ssid,
            "password": password
        }).to_string();
        self.send_gatt_command(&cmd).await
    }

    /// Ping the phone over Bluetooth and return the response
    pub async fn ping_phone(&self) -> Option<String> {
        let cmd = serde_json::json!({"cmd": "ping"}).to_string();
        self.send_gatt_command_and_read_response(&cmd).await
    }

    /// Get BLE-discovered peers
    pub fn get_ble_peers(&self) -> Vec<DiscoveredPeer> {
        let now = Utc::now().timestamp_millis();
        let lock = self.peers.lock();
        lock.values()
            .filter(|p| now - p.last_seen_epoch_ms < 30_000)
            .cloned()
            .collect()
    }

    /// Whether BLE hardware was successfully initialized
    pub fn is_available(&self) -> bool {
        self.is_available.load(Ordering::SeqCst)
    }

    pub fn stop(&self) {
        self.stop_flag.store(true, Ordering::SeqCst);
    }
}


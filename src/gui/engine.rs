//! ShareDash GUI Backend Engine
//!
//! Spawns the Axum HTTP & WebSocket server, handles background discovery (BLE, UDP mDNS, ADB),
//! and provides all transfer & wizard capabilities for the GUI without touching CLI code.

use std::collections::HashMap;
use std::net::SocketAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use axum::{
    routing::{get, post},
    Router,
};
use parking_lot::Mutex;
use tower_http::cors::{Any, CorsLayer};

use crate::discovery::{BleDiscovery, PairingManager, PeerDiscovery, WifiCapsInfo};
use crate::hotspot;
use crate::scheduler::metrics::SchedulerTelemetry;
use crate::server::api::{
    cancel_all_active_transfers, cancel_transfer, confirm_pair_session, connect_to_remote_peer,
    create_pair_session, detect_transports, get_connection_bridges, get_device_info,
    get_discovered_peers, get_pairing_status, get_pending_pair_request, get_wifi_caps_handler,
    handle_incoming_pair_request, handle_start_hotspot, handle_wifi_connect, list_transfers,
    open_received_folder, open_windows_hotspot_settings, prepare_transfer, receive_transfer_chunk,
    respond_to_pair_request, start_benchmark_transfer, upload_and_transfer_files, verify_pair_pin,
    AppState,
};
use crate::server::ws_telemetry::ws_telemetry_handler;
use crate::storage::manifest_db::ManifestDb;

/// Live channel telemetry state for GUI
#[derive(Clone, Debug, Default)]
pub struct ChannelStats {
    pub name: String,
    pub icon: String,
    pub bytes_sent: u64,
    pub speed_mb_s: f64,
    pub speed_gbps: f64,
    pub chunks_sent: usize,
}

/// Overall transfer progress snapshot
#[derive(Clone, Debug, Default)]
pub struct TransferProgressSnapshot {
    pub file_name: String,
    pub total_bytes: u64,
    pub bytes_transferred: u64,
    pub progress_pct: f64,
    pub speed_mb_s: f64,
    pub speed_gbps: f64,
    pub eta_seconds: f64,
    pub channels: Vec<ChannelStats>,
    pub status: String,
}

/// Discovered BLE/network device for the GUI list
#[derive(Clone, Debug)]
pub struct DiscoveredDevice {
    pub name: String,
    pub address: String,
    pub rssi: i16,
    pub peripheral_id: String,
    pub transport: String,
}

pub struct GuiEngine {
    pub state: AppState,
    pub tokio_handle: tokio::runtime::Handle,
}

impl GuiEngine {
    /// Initialize the background engine (HTTP server, discovery, telemetry)
    pub async fn start(port: u16, name: String, db_path: PathBuf) -> anyhow::Result<Self> {
        let (telemetry_tx, _) = tokio::sync::broadcast::channel::<SchedulerTelemetry>(512);

        // Persistent Device ID
        let device_id_file = PathBuf::from("./sharedash_data/device_id.txt");
        let device_id = if let Ok(existing_id) = std::fs::read_to_string(&device_id_file) {
            let trimmed = existing_id.trim().to_string();
            if !trimmed.is_empty() {
                trimmed
            } else {
                let new_id = uuid::Uuid::new_v4().to_string();
                let _ = std::fs::create_dir_all("./sharedash_data");
                let _ = std::fs::write(&device_id_file, &new_id);
                new_id
            }
        } else {
            let new_id = uuid::Uuid::new_v4().to_string();
            let _ = std::fs::create_dir_all("./sharedash_data");
            let _ = std::fs::write(&device_id_file, &new_id);
            new_id
        };

        let manifest_db = Arc::new(ManifestDb::open(&db_path)?);
        let pairing_mgr = Arc::new(PairingManager::new(device_id.clone(), name.clone()));
        let discovery = Arc::new(PeerDiscovery::new(
            device_id.clone(),
            name.clone(),
            std::env::consts::OS.to_string(),
            port,
        ));

        // Start UDP peer discovery
        if let Err(e) = discovery.start().await {
            tracing::warn!("Could not start UDP discovery: {}", e);
        }

        // Start BLE discovery
        let ble_discovery = Arc::new(BleDiscovery::new());
        if let Err(e) = ble_discovery.start().await {
            tracing::warn!("Could not start BLE discovery: {}", e);
        }

        // Maintain ADB reverse port 54325 for connected USB Android devices
        let adb_server_port = port;
        tokio::spawn(async move {
            loop {
                if let Ok(output) = tokio::process::Command::new("adb").arg("devices").output().await {
                    let text = String::from_utf8_lossy(&output.stdout);
                    let has_device = text.lines().skip(1).any(|l| l.contains("\tdevice"));
                    if has_device {
                        let _ = tokio::process::Command::new("adb")
                            .args(["reverse", "tcp:54325", &format!("tcp:{}", adb_server_port)])
                            .output()
                            .await;
                        let _ = tokio::process::Command::new("adb")
                            .args(["forward", "tcp:54325", "tcp:54321"])
                            .output()
                            .await;
                    }
                }
                tokio::time::sleep(Duration::from_secs(3)).await;
            }
        });

        let state = AppState {
            device_id,
            device_name: name,
            server_port: port,
            pairing_mgr,
            discovery,
            ble_discovery,
            manifest_db,
            telemetry_tx: telemetry_tx.clone(),
            active_transfers: Arc::new(Mutex::new(HashMap::new())),
            pending_pair: Arc::new(Mutex::new(None)),
            active_paired_peer: Arc::new(Mutex::new(None)),
            outgoing_pair: Arc::new(Mutex::new(None)),
            active_sender_cancel_flag: Arc::new(std::sync::atomic::AtomicBool::new(false)),
        };

        let cors = CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(Any)
            .allow_headers(Any);

        let ws_telemetry_tx = telemetry_tx.clone();
        let app = Router::new()
            .route("/api/v1/info", get(get_device_info))
            .route("/api/v1/peers", get(get_discovered_peers))
            .route("/api/v1/bridges", get(get_connection_bridges))
            .route("/api/v1/transports/detect", get(detect_transports))
            .route("/api/v1/pair/create", post(create_pair_session))
            .route("/api/v1/pair/verify", post(verify_pair_pin))
            .route("/api/v1/pair/request", post(handle_incoming_pair_request))
            .route("/api/v1/pair/incoming", get(get_pending_pair_request))
            .route("/api/v1/pair/respond", post(respond_to_pair_request))
            .route("/api/v1/pair/confirm", post(confirm_pair_session))
            .route("/api/v1/pair/connect", post(connect_to_remote_peer))
            .route("/api/v1/pair/status", get(get_pairing_status))
            .route("/api/v1/storage/open_folder", post(open_received_folder))
            .route("/api/v1/wifi_caps", get(get_wifi_caps_handler))
            .route("/api/v1/wifi_connect", post(handle_wifi_connect))
            .route("/api/v1/hotspot/start", post(handle_start_hotspot))
            .route("/api/v1/hotspot/open-settings", post(open_windows_hotspot_settings))
            .route("/api/v1/transfers", get(list_transfers))
            .route("/api/v1/transfers/chunk", post(receive_transfer_chunk))
            .route("/api/v1/transfers/send", post(upload_and_transfer_files))
            .route("/api/v1/transfers/prepare", post(prepare_transfer))
            .route("/api/v1/transfers/:id/cancel", post(cancel_transfer))
            .route("/api/v1/transfers/cancel", post(cancel_all_active_transfers))
            .route("/api/v1/benchmark/start", post(start_benchmark_transfer))
            .route(
                "/ws/telemetry",
                get(move |ws| ws_telemetry_handler(ws, ws_telemetry_tx.subscribe())),
            )
            .layer(cors)
            .layer(axum::extract::DefaultBodyLimit::disable())
            .with_state(state.clone());

        let addr = SocketAddr::from(([0, 0, 0, 0], port));
        let listener = tokio::net::TcpListener::bind(addr).await?;

        tokio::spawn(async move {
            let _ = axum::serve(listener, app).await;
        });

        // Clean up any stale temporary Wi-Fi profiles
        hotspot::cleanup_cached_wlan_profiles().await;

        Ok(Self {
            state,
            tokio_handle: tokio::runtime::Handle::current(),
        })
    }

    // ═══════════════════════════════════════════════════════════
    //  USB & HARDWARE DETECTION
    // ═══════════════════════════════════════════════════════════

    pub async fn check_usb_status(&self) -> (bool, Option<String>, Option<String>) {
        let mut usb_connected = false;
        let mut usb_name = None;
        let mut rndis_ip = None;

        // 1. Check ADB
        let (adb_ok, adb_name) = self.check_adb().await;
        if adb_ok {
            usb_connected = true;
            usb_name = adb_name;
        }

        // 2. Check RNDIS Tethering
        if let Some((ip, name)) = hotspot::detect_usb_tethering_peer_detailed().await {
            rndis_ip = Some(ip);
            if usb_name.is_none() {
                usb_name = Some(name);
            }
            usb_connected = true;
        }

        // 3. Sweep direct USB network peers
        if !usb_connected {
            let direct_peers = hotspot::scan_direct_usb_pc_peers().await;
            if let Some(peer) = direct_peers.into_iter().find(|p| {
                p.is_usb_direct
                    || p.ip.starts_with("169.254.")
                    || p.ip.starts_with("192.168.42.")
                    || p.ip.starts_with("192.168.43.")
            }) {
                rndis_ip = Some(peer.ip);
                if usb_name.is_none() {
                    usb_name = Some(peer.device_name);
                }
                usb_connected = true;
            }
        }

        (usb_connected, usb_name, rndis_ip)
    }

    pub async fn check_adb(&self) -> (bool, Option<String>) {
        if let Some(adb_path) = self.find_adb() {
            if let Ok(output) = tokio::process::Command::new(&adb_path)
                .arg("devices")
                .output()
                .await
            {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines().skip(1) {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() && trimmed.ends_with("device") {
                        let serial = trimmed.split_whitespace().next().unwrap_or("Android");
                        let model = tokio::process::Command::new(&adb_path)
                            .args(["shell", "getprop", "ro.product.model"])
                            .output()
                            .await
                            .ok()
                            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
                            .filter(|s| !s.is_empty());

                        let display_name = model
                            .map(|m| format!("{} ({})", m, serial))
                            .unwrap_or_else(|| format!("Android Device ({})", serial));

                        return (true, Some(display_name));
                    }
                }
            }
        }
        (false, None)
    }

    pub fn find_adb(&self) -> Option<String> {
        let candidates = [
            format!(
                "{}\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
                std::env::var("USERPROFILE").unwrap_or_default()
            ),
            "adb.exe".to_string(),
            "adb".to_string(),
        ];

        for candidate in &candidates {
            if let Ok(output) = std::process::Command::new(candidate).arg("version").output() {
                if output.status.success() {
                    return Some(candidate.clone());
                }
            }
        }
        None
    }

    pub async fn setup_adb_forward(&self) -> bool {
        if let Some(adb_path) = self.find_adb() {
            let fwd = tokio::process::Command::new(&adb_path)
                .args(["forward", "tcp:54325", "tcp:54321"])
                .output()
                .await;
            let _ = tokio::process::Command::new(&adb_path)
                .args(["reverse", "tcp:54321", "tcp:54321"])
                .output()
                .await;
            let _ = tokio::process::Command::new(&adb_path)
                .args(["reverse", "tcp:54325", "tcp:54321"])
                .output()
                .await;
            let _ = tokio::process::Command::new(&adb_path)
                .args(["shell", "am", "start", "-n", "com.sharedash.app/.MainActivity"])
                .output()
                .await;
            fwd.is_ok()
        } else {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HTTP HELPER METHODS
    // ═══════════════════════════════════════════════════════════

    pub async fn http_probe(&self, ip: &str, port: u16) -> bool {
        let url = format!("http://{}:{}/api/v1/info", ip, port);
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(3))
            .build()
            .unwrap_or_default();
        matches!(client.get(&url).send().await, Ok(resp) if resp.status().is_success())
    }

    pub async fn query_phone_wifi_caps_http(&self, ip: &str, port: u16) -> Option<WifiCapsInfo> {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(3))
            .build()
            .ok()?;
        let url = format!("http://{}:{}/api/v1/wifi_caps", ip, port);
        let resp = client.get(&url).send().await.ok()?;
        if resp.status().is_success() {
            resp.json::<WifiCapsInfo>().await.ok()
        } else {
            None
        }
    }

    pub async fn send_wifi_connect_over_usb(
        &self,
        ip: &str,
        port: u16,
        ssid: &str,
        password: &str,
    ) -> bool {
        if let Ok(client) = reqwest::Client::builder().timeout(Duration::from_secs(4)).build() {
            let url = format!("http://{}:{}/api/v1/wifi_connect", ip, port);
            let body = serde_json::json!({
                "ssid": ssid,
                "password": password
            });
            for _ in 0..3 {
                if let Ok(resp) = client.post(&url).json(&body).send().await {
                    if resp.status().is_success() {
                        return true;
                    }
                }
                tokio::time::sleep(Duration::from_millis(600)).await;
            }
        }

        // ADB broadcast intent fallback
        if let Some(adb_path) = self.find_adb() {
            let res = tokio::process::Command::new(&adb_path)
                .args([
                    "shell", "am", "broadcast",
                    "-a", "com.sharedash.app.WIFI_CONNECT",
                    "--es", "ssid", ssid,
                    "--es", "password", password,
                ])
                .output()
                .await;
            if res.is_ok() {
                return true;
            }
        }

        false
    }

    pub async fn send_start_hotspot_over_usb(&self, ip: &str, port: u16) -> Option<(String, String, String)> {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .build()
            .ok()?;
        let url = format!("http://{}:{}/api/v1/hotspot/start", ip, port);
        for _ in 0..3 {
            if let Ok(resp) = client.post(&url).send().await {
                if resp.status().is_success() {
                    if let Ok(json) = resp.json::<serde_json::Value>().await {
                        let ssid = json.get("ssid")?.as_str()?.to_string();
                        let password = json.get("password")?.as_str()?.to_string();
                        let gw = json.get("gateway").and_then(|g| g.as_str()).unwrap_or("192.168.43.1").to_string();
                        return Some((ssid, password, gw));
                    }
                }
            }
            tokio::time::sleep(Duration::from_millis(600)).await;
        }
        None
    }

    pub async fn send_start_hotspot_with_band(&self, ip: &str, port: u16, band: &str) -> Option<(String, String, String)> {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .build()
            .ok()?;
        let url = format!("http://{}:{}/api/v1/hotspot/start", ip, port);
        let body = serde_json::json!({ "band": band });
        for _ in 0..3 {
            if let Ok(resp) = client.post(&url).json(&body).send().await {
                if resp.status().is_success() {
                    if let Ok(json) = resp.json::<serde_json::Value>().await {
                        let ssid = json.get("ssid")?.as_str()?.to_string();
                        let password = json.get("password")?.as_str()?.to_string();
                        let gw = json.get("gateway").and_then(|g| g.as_str()).unwrap_or("192.168.137.1").to_string();
                        return Some((ssid, password, gw));
                    }
                }
            }
            tokio::time::sleep(Duration::from_millis(600)).await;
        }
        None
    }

    pub async fn pair_handshake_target(&self, ip: &str, port: u16) -> bool {
        let client = match reqwest::Client::builder().timeout(Duration::from_secs(5)).build() {
            Ok(c) => c,
            Err(_) => return false,
        };

        let initiator_ip = if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
            let target_prefix: String = ip.split('.').take(3).collect::<Vec<_>>().join(".");
            interfaces
                .into_iter()
                .find(|(_, if_ip)| {
                    let s = if_ip.to_string();
                    s.starts_with(&target_prefix) && s != ip
                })
                .map(|(_, if_ip)| if_ip.to_string())
                .unwrap_or_else(|| "127.0.0.1".to_string())
        } else {
            "127.0.0.1".to_string()
        };

        let pin = format!("{:06}", rand::random::<u32>() % 1_000_000);
        let body = serde_json::json!({
            "initiator_device_id": self.state.device_id,
            "initiator_name": self.state.device_name,
            "initiator_ip": initiator_ip,
            "pin_code": pin,
            "app_version": env!("CARGO_PKG_VERSION"),
            "status": "PENDING"
        });

        let url = format!("http://{}:{}/api/v1/pair/request", ip, port);
        match client.post(&url).json(&body).send().await {
            Ok(resp) => resp.status().is_success(),
            Err(_) => false,
        }
    }

    pub async fn verify_and_pair_wifi_with_retries(&self, target_ip: &str, port: u16, max_retries: usize) -> bool {
        let url_info = format!("http://{}:{}/api/v1/info", target_ip, port);
        let local_wifi_ip: Option<std::net::IpAddr> = if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
            let target_prefix: String = target_ip.split('.').take(3).collect::<Vec<_>>().join(".");
            interfaces
                .into_iter()
                .find(|(_, ip)| {
                    let s = ip.to_string();
                    s.starts_with(&target_prefix) && s != target_ip
                })
                .map(|(_, ip)| ip)
        } else {
            None
        };

        let bound_client = local_wifi_ip.and_then(|local_ip| {
            reqwest::Client::builder()
                .local_address(local_ip)
                .timeout(Duration::from_millis(2000))
                .build()
                .ok()
        });

        let standard_client = reqwest::Client::builder()
            .timeout(Duration::from_millis(2000))
            .build()
            .unwrap_or_default();

        let mut link_alive = false;
        for _ in 0..max_retries {
            if let Some(ref bc) = bound_client {
                if let Ok(resp) = bc.get(&url_info).send().await {
                    if resp.status().is_success() {
                        link_alive = true;
                        break;
                    }
                }
            }

            if let Ok(resp) = standard_client.get(&url_info).send().await {
                if resp.status().is_success() {
                    link_alive = true;
                    break;
                }
            }

            tokio::time::sleep(Duration::from_millis(800)).await;
        }

        if !link_alive {
            return false;
        }

        for _ in 0..3 {
            if self.pair_handshake_target(target_ip, port).await {
                return true;
            }
            tokio::time::sleep(Duration::from_millis(600)).await;
        }

        false
    }

    // ═══════════════════════════════════════════════════════════
    //  BLE SCAN & DISCOVERY
    // ═══════════════════════════════════════════════════════════

    pub async fn scan_devices(&self) -> Vec<DiscoveredDevice> {
        let mut list = Vec::new();

        // 1. From BLE Discovery
        for p in self.state.ble_discovery.get_ble_peers() {
            list.push(DiscoveredDevice {
                name: p.friendly_name,
                address: p.device_id.clone(),
                rssi: -50,
                peripheral_id: p.device_id,
                transport: "Bluetooth LE".to_string(),
            });
        }

        // 2. From UDP mDNS
        for p in self.state.discovery.get_active_peers() {
            if !list.iter().any(|d| d.name == p.friendly_name) {
                list.push(DiscoveredDevice {
                    name: p.friendly_name,
                    address: p.remote_addr.ip().to_string(),
                    rssi: -40,
                    peripheral_id: p.device_id,
                    transport: "Wi-Fi (mDNS)".to_string(),
                });
            }
        }

        list
    }

    pub async fn query_wifi_caps_ble(&self, peripheral_id: &str) -> Option<WifiCapsInfo> {
        for peer in self.state.ble_discovery.get_ble_peers() {
            if (peer.device_id == peripheral_id || peer.friendly_name == peripheral_id) && peer.wifi_caps.is_some() {
                return peer.wifi_caps;
            }
        }
        for peer in self.state.ble_discovery.get_ble_peers() {
            if let Some(ref caps) = peer.wifi_caps {
                return Some(caps.clone());
            }
        }
        self.state.ble_discovery.read_wifi_capabilities().await
    }

    pub async fn send_wifi_connect_ble(&self, ssid: &str, password: &str) -> bool {
        let cmd = serde_json::json!({
            "cmd": "wifi_connect",
            "ssid": ssid,
            "password": password
        });
        self.state.ble_discovery.send_gatt_command(&cmd.to_string()).await
    }

    // ═══════════════════════════════════════════════════════════
    //  MULTIPATH FILE TRANSFER EXECUTION
    // ═══════════════════════════════════════════════════════════

    pub async fn execute_file_transfer<F>(
        &self,
        file_path: PathBuf,
        targets: Vec<(String, u16, String)>,
        on_progress: F,
    ) -> anyhow::Result<crate::gui::widgets::TransferResultData>
    where
        F: Fn(TransferProgressSnapshot) + Send + Sync + 'static,
    {
        let is_dir = file_path.is_dir();
        let (target_file_path, file_name, file_size, is_temp_zip) = if is_dir {
            let (zip_path, size) = package_directory_to_zip(&file_path)?;
            let name = zip_path
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("folder.zip")
                .to_string();
            (zip_path, name, size, true)
        } else {
            let name = file_path
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("file")
                .to_string();
            let size = std::fs::metadata(&file_path).map(|m| m.len()).unwrap_or(0);
            (file_path.clone(), name, size, false)
        };

        let file_size_mb = file_size as f64 / (1024.0 * 1024.0);
        let start_time = Instant::now();

        let channel_states: Vec<(Arc<Mutex<ChannelStats>>, String, u16, String)> = targets
            .iter()
            .map(|(ip, port, name)| {
                let icon = if name.contains("USB") { "⚡" } else { "📶" };
                let state = Arc::new(Mutex::new(ChannelStats {
                    name: name.clone(),
                    icon: icon.to_string(),
                    bytes_sent: 0,
                    speed_mb_s: 0.0,
                    speed_gbps: 0.0,
                    chunks_sent: 0,
                }));
                (state, ip.clone(), *port, name.clone())
            })
            .collect();

        let stop_ui = Arc::new(std::sync::atomic::AtomicBool::new(false));
        let stop_ui_clone = stop_ui.clone();
        let channel_states_ui: Vec<Arc<Mutex<ChannelStats>>> =
            channel_states.iter().map(|c| c.0.clone()).collect();
        let total_size = file_size;
        let file_name_clone = file_name.clone();

        let progress_cb = Arc::new(on_progress);
        let progress_cb_clone = progress_cb.clone();

        let transfer_start = Instant::now();
        let ui_handle = self.tokio_handle.spawn(async move {
            while !stop_ui_clone.load(std::sync::atomic::Ordering::SeqCst) {
                tokio::time::sleep(Duration::from_millis(100)).await;
                let elapsed = transfer_start.elapsed().as_secs_f64().max(0.01);

                let mut current_channels: Vec<ChannelStats> = Vec::with_capacity(channel_states_ui.len());
                for c in channel_states_ui.iter() {
                    let mut guard = c.lock();
                    let bytes_mb = guard.bytes_sent as f64 / (1024.0 * 1024.0);
                    guard.speed_mb_s = bytes_mb / elapsed;
                    guard.speed_gbps = (guard.speed_mb_s * 8.0) / 1000.0;
                    current_channels.push(guard.clone());
                }

                let total_sent: u64 = current_channels.iter().map(|c| c.bytes_sent).sum();
                let pct = if total_size > 0 {
                    (total_sent as f64 / total_size as f64).min(1.0)
                } else {
                    0.0
                };
                let total_speed: f64 = current_channels.iter().map(|c| c.speed_mb_s).sum();
                let remaining_bytes = total_size.saturating_sub(total_sent);
                let eta = if total_speed > 0.0 {
                    (remaining_bytes as f64 / (1024.0 * 1024.0)) / total_speed
                } else {
                    0.0
                };

                progress_cb_clone(TransferProgressSnapshot {
                    file_name: file_name_clone.clone(),
                    total_bytes: total_size,
                    bytes_transferred: total_sent,
                    progress_pct: pct,
                    speed_mb_s: total_speed,
                    speed_gbps: (total_speed * 8.0) / 1000.0,
                    eta_seconds: eta,
                    channels: current_channels,
                    status: "ACTIVE".to_string(),
                });
            }
        });

        let std_file = std::fs::File::open(&target_file_path)?;
        let mmap = Arc::new(unsafe { memmap2::Mmap::map(&std_file)? });

        let transfer_id = uuid::Uuid::new_v4().to_string();
        let dispatcher = Arc::new(Mutex::new(GuiWorkDispatcher::new(file_size)));
        let stop_flag = self.state.active_sender_cancel_flag.clone();
        stop_flag.store(false, std::sync::atomic::Ordering::SeqCst);
        let mut handles = Vec::new();

        for (state, ip, port, name) in &channel_states {
            let worker_count = if name.contains("USB") { 6 } else { 4 };
            for _ in 0..worker_count {
                let h = self.tokio_handle.spawn(run_gui_chunk_worker(
                    ip.clone(),
                    *port,
                    name.clone(),
                    mmap.clone(),
                    file_name.clone(),
                    file_size,
                    transfer_id.clone(),
                    dispatcher.clone(),
                    state.clone(),
                    stop_flag.clone(),
                ));
                handles.push(h);
            }
        }

        let mut all_ok = true;
        for h in handles {
            if h.await.is_err() {
                all_ok = false;
            }
        }

        let was_cancelled = stop_flag.load(std::sync::atomic::Ordering::SeqCst) || dispatcher.lock().is_cancelled();
        stop_ui.store(true, std::sync::atomic::Ordering::SeqCst);
        let _ = ui_handle.await;

        if is_temp_zip {
            let _ = std::fs::remove_file(&target_file_path);
        }

        if was_cancelled {
            anyhow::bail!("Transfer was cancelled");
        }

        if dispatcher.lock().has_fatal_error() {
            all_ok = false;
        }

        let elapsed = start_time.elapsed().as_secs_f64().max(0.01);
        let speed_mb_s = file_size_mb / elapsed;
        let speed_gbps = (speed_mb_s * 8.0) / 1000.0;

        let final_channels: Vec<ChannelStats> =
            channel_states.iter().map(|c| c.0.lock().clone()).collect();

        let usb_ch = final_channels.iter().find(|c| c.name.contains("USB"));
        let wifi_ch = final_channels.iter().find(|c| !c.name.contains("USB"));

        let total_chunks = dispatcher.lock().chunks.len();
        let chunk_size_bytes = if total_chunks > 0 {
            (file_size as usize / total_chunks).max(1)
        } else {
            file_size as usize
        };

        let usb_speed_mb_s = usb_ch.map(|c| (c.bytes_sent as f64 / (1024.0 * 1024.0)) / elapsed);
        let wifi_speed_mb_s = wifi_ch.map(|c| (c.bytes_sent as f64 / (1024.0 * 1024.0)) / elapsed);

        // Final completion notification
        progress_cb(TransferProgressSnapshot {
            file_name: file_name.clone(),
            total_bytes: total_size,
            bytes_transferred: total_size,
            progress_pct: 1.0,
            speed_mb_s,
            speed_gbps,
            eta_seconds: 0.0,
            channels: final_channels.clone(),
            status: "COMPLETED".to_string(),
        });

        Ok(crate::gui::widgets::TransferResultData {
            file_name,
            size_mb: file_size_mb,
            time_secs: elapsed,
            avg_speed_mb_s: speed_mb_s,
            avg_speed_gbps: speed_gbps,
            usb_speed_mb_s,
            usb_pct: usb_ch.map(|c| (c.bytes_sent as f64 / file_size.max(1) as f64) * 100.0),
            wifi_speed_mb_s,
            wifi_pct: wifi_ch.map(|c| (c.bytes_sent as f64 / file_size.max(1) as f64) * 100.0),
            chunk_size_bytes,
            total_chunks,
            integrity_ok: all_ok,
        })
    }
}

// ═══════════════════════════════════════════════════════════
//  CHUNK WORKER & DISPATCHER (replicated for GUI)
// ═══════════════════════════════════════════════════════════

#[derive(Clone, Debug)]
struct GuiChunkInfo {
    chunk_id: u32,
    offset: u64,
    length: u64,
}

struct GuiWorkDispatcher {
    unassigned: std::collections::VecDeque<u32>,
    in_flight: std::collections::HashMap<u32, (Instant, String)>,
    completed: std::collections::HashSet<u32>,
    retries: std::collections::HashMap<u32, u32>,
    chunks: Vec<GuiChunkInfo>,
    fatal_error: bool,
    cancelled: bool,
}

impl GuiWorkDispatcher {
    fn new(total_bytes: u64) -> Self {
        let mut chunks = Vec::new();
        let mut unassigned = std::collections::VecDeque::new();

        let base_chunk_size: u64 = if total_bytes < 20 * 1024 * 1024 {
            2 * 1024 * 1024
        } else if total_bytes < 100 * 1024 * 1024 {
            4 * 1024 * 1024
        } else if total_bytes < 500 * 1024 * 1024 {
            8 * 1024 * 1024
        } else if total_bytes < 2 * 1024 * 1024 * 1024 {
            16 * 1024 * 1024
        } else {
            32 * 1024 * 1024
        };

        let mut current_offset: u64 = 0;
        let mut chunk_id: u32 = 0;

        if total_bytes == 0 {
            chunks.push(GuiChunkInfo {
                chunk_id: 0,
                offset: 0,
                length: 0,
            });
            unassigned.push_back(0);
        } else {
            while current_offset < total_bytes {
                let remaining = total_bytes - current_offset;
                let chunk_size = if remaining <= 16 * 1024 * 1024 && base_chunk_size > 4 * 1024 * 1024 {
                    4 * 1024 * 1024
                } else if remaining <= 48 * 1024 * 1024 && base_chunk_size > 8 * 1024 * 1024 {
                    8 * 1024 * 1024
                } else if remaining <= 96 * 1024 * 1024 && base_chunk_size > 16 * 1024 * 1024 {
                    16 * 1024 * 1024
                } else {
                    base_chunk_size
                };

                let length = remaining.min(chunk_size);
                chunks.push(GuiChunkInfo {
                    chunk_id,
                    offset: current_offset,
                    length,
                });
                unassigned.push_back(chunk_id);
                current_offset += length;
                chunk_id += 1;
            }
        }

        Self {
            unassigned,
            in_flight: std::collections::HashMap::new(),
            completed: std::collections::HashSet::new(),
            retries: std::collections::HashMap::new(),
            chunks,
            fatal_error: false,
            cancelled: false,
        }
    }

    fn pop_chunk(&mut self, transport_name: &str) -> Option<GuiChunkInfo> {
        if self.cancelled || self.fatal_error {
            return None;
        }

        if let Some(cid) = self.unassigned.pop_front() {
            self.in_flight
                .insert(cid, (Instant::now(), transport_name.to_string()));
            return self.chunks.get(cid as usize).cloned();
        }

        let now = Instant::now();
        let mut candidate_cid = None;
        for (cid, (dispatched_at, owner)) in &self.in_flight {
            if owner != transport_name && now.duration_since(*dispatched_at).as_secs_f64() > 2.5 {
                candidate_cid = Some(*cid);
                break;
            }
        }

        if let Some(cid) = candidate_cid {
            self.in_flight
                .insert(cid, (Instant::now(), transport_name.to_string()));
            return self.chunks.get(cid as usize).cloned();
        }

        None
    }

    fn mark_completed(&mut self, chunk_id: u32) -> bool {
        self.in_flight.remove(&chunk_id);
        self.completed.insert(chunk_id)
    }

    fn mark_cancelled(&mut self) {
        self.cancelled = true;
        self.unassigned.clear();
        self.in_flight.clear();
    }

    fn is_cancelled(&self) -> bool {
        self.cancelled
    }

    fn return_for_retry(&mut self, chunk_id: u32) {
        self.in_flight.remove(&chunk_id);
        if self.cancelled || self.fatal_error {
            return;
        }
        if !self.completed.contains(&chunk_id) {
            let count = self.retries.entry(chunk_id).or_insert(0);
            *count += 1;
            if *count <= 5 {
                self.unassigned.push_back(chunk_id);
            } else {
                tracing::error!("Chunk #{} exceeded maximum retries (5)!", chunk_id);
                self.fatal_error = true;
            }
        }
    }

    fn is_done(&self) -> bool {
        self.cancelled || self.fatal_error || self.completed.len() >= self.chunks.len()
    }

    fn has_fatal_error(&self) -> bool {
        self.fatal_error
    }
}

async fn run_gui_chunk_worker(
    ip: String,
    port: u16,
    transport_name: String,
    mmap: Arc<memmap2::Mmap>,
    file_name: String,
    file_size: u64,
    transfer_id: String,
    dispatcher: Arc<Mutex<GuiWorkDispatcher>>,
    progress: Arc<Mutex<ChannelStats>>,
    stop_flag: Arc<std::sync::atomic::AtomicBool>,
) {
    let client = reqwest::Client::builder()
        .tcp_nodelay(true)
        .http1_only()
        .pool_idle_timeout(Some(Duration::from_secs(60)))
        .pool_max_idle_per_host(32)
        .timeout(Duration::from_secs(60))
        .build()
        .unwrap_or_default();

    let url_chunk = format!("http://{}:{}/api/v1/transfers/chunk", ip, port);
    let total_chunks = dispatcher.lock().chunks.len() as u32;

    while !stop_flag.load(std::sync::atomic::Ordering::SeqCst) {
        if dispatcher.lock().is_done() {
            break;
        }

        let chunk = {
            let mut guard = dispatcher.lock();
            guard.pop_chunk(&transport_name)
        };

        let chunk = match chunk {
            Some(c) => c,
            None => {
                tokio::time::sleep(Duration::from_millis(10)).await;
                continue;
            }
        };

        if stop_flag.load(std::sync::atomic::Ordering::SeqCst) || dispatcher.lock().is_cancelled() {
            break;
        }

        let start_idx = chunk.offset as usize;
        let end_idx = (chunk.offset + chunk.length) as usize;
        if end_idx > mmap.len() {
            dispatcher.lock().return_for_retry(chunk.chunk_id);
            continue;
        }

        let slice = &mmap[start_idx..end_idx];

        let chunk_crc = {
            let mut hasher = crc32fast::Hasher::new();
            hasher.update(slice);
            format!("{:08x}", hasher.finalize())
        };

        let chunk_body = bytes::Bytes::copy_from_slice(slice);

        let req = client
            .post(&url_chunk)
            .header("x-transfer-id", &transfer_id)
            .header("x-file-name", &file_name)
            .header("x-file-size", file_size.to_string())
            .header("x-chunk-id", chunk.chunk_id.to_string())
            .header("x-chunk-offset", chunk.offset.to_string())
            .header("x-chunk-length", chunk.length.to_string())
            .header("x-chunk-crc32", &chunk_crc)
            .header("x-total-chunks", total_chunks.to_string())
            .header("Content-Type", "application/octet-stream")
            .header("Connection", "keep-alive")
            .body(chunk_body);

        match req.send().await {
            Ok(resp) if resp.status().is_success() => {
                let is_new = dispatcher.lock().mark_completed(chunk.chunk_id);
                if is_new {
                    let mut p = progress.lock();
                    p.bytes_sent += chunk.length;
                    p.chunks_sent += 1;
                }
            }
            Ok(resp)
                if resp.status() == reqwest::StatusCode::GONE
                    || resp.status() == reqwest::StatusCode::CONFLICT =>
            {
                tracing::info!("Remote peer cancelled transfer. Aborting.");
                stop_flag.store(true, std::sync::atomic::Ordering::SeqCst);
                dispatcher.lock().mark_cancelled();
                break;
            }
            Ok(_) => {
                dispatcher.lock().return_for_retry(chunk.chunk_id);
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
            Err(_) => {
                dispatcher.lock().return_for_retry(chunk.chunk_id);
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
        }
    }
}

pub fn get_directory_size(path: &Path) -> u64 {
    let mut total = 0;
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                total += get_directory_size(&p);
            } else if let Ok(meta) = p.metadata() {
                total += meta.len();
            }
        }
    }
    total
}

fn package_directory_to_zip(dir_path: &Path) -> anyhow::Result<(PathBuf, u64)> {
    use std::io::{Read, Write};
    let dir_name = dir_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("folder");
    let temp_dir = std::env::temp_dir().join("sharedash_archives");
    let _ = std::fs::create_dir_all(&temp_dir);
    let zip_path = temp_dir.join(format!("{}.zip", dir_name));

    let file = std::fs::File::create(&zip_path)?;
    let mut zip = zip::ZipWriter::new(file);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Stored)
        .unix_permissions(0o755);

    fn walk_dir_entries(root: &Path, current: &Path, list: &mut Vec<(PathBuf, String)>) -> std::io::Result<()> {
        for entry in std::fs::read_dir(current)? {
            let entry = entry?;
            let path = entry.path();
            if path.is_dir() {
                walk_dir_entries(root, &path, list)?;
            } else if path.is_file() {
                let rel = path.strip_prefix(root).unwrap_or(&path).to_string_lossy().replace('\\', "/");
                list.push((path, rel));
            }
        }
        Ok(())
    }

    let mut files = Vec::new();
    walk_dir_entries(dir_path, dir_path, &mut files)?;

    let mut buffer = vec![0u8; 1024 * 1024];
    for (src_path, rel_name) in files {
        zip.start_file(rel_name, options)?;
        let mut f = std::fs::File::open(&src_path)?;
        loop {
            let n = f.read(&mut buffer)?;
            if n == 0 {
                break;
            }
            zip.write_all(&buffer[..n])?;
        }
    }
    zip.finish()?;
    let size = std::fs::metadata(&zip_path)?.len();
    Ok((zip_path, size))
}

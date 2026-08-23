use anyhow::Result;
use chrono::Utc;
use parking_lot::Mutex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::net::UdpSocket;
use tokio::sync::Semaphore;
use tokio::time::sleep;

pub const DISCOVERY_BROADCAST_PORT: u16 = 54320;
pub const CURRENT_APP_VERSION: &str = "0.1.0";
pub const MIN_SUPPORTED_APP_VERSION: &str = "0.1.0";

pub fn is_version_compatible(peer_version: &str) -> bool {
    if peer_version.is_empty() {
        return true; // backwards compatibility
    }
    let peer_parts: Vec<u32> = peer_version.split('.').filter_map(|s| s.parse::<u32>().ok()).collect();
    let min_parts: Vec<u32> = MIN_SUPPORTED_APP_VERSION.split('.').filter_map(|s| s.parse::<u32>().ok()).collect();
    if peer_parts.len() >= 2 && min_parts.len() >= 2 {
        if peer_parts[0] != min_parts[0] {
            return false;
        }
        return peer_parts[1] >= min_parts[1];
    }
    true
}

fn default_app_version() -> String {
    CURRENT_APP_VERSION.to_string()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PeerBeacon {
    pub device_id: String,
    pub friendly_name: String,
    #[serde(default)]
    pub os_name: String,
    #[serde(default = "default_server_port")]
    pub server_port: u16,
    #[serde(default = "default_app_version")]
    pub app_version: String,
    #[serde(default)]
    pub supported_transports: Vec<String>,
    #[serde(default)]
    pub timestamp_ms: i64,
}

fn default_server_port() -> u16 {
    54321
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WifiCapsInfo {
    pub wifi_standard: String,
    pub max_frequency_ghz: f64,
    pub max_channel_width_mhz: u32,
    pub max_phy_rate_mbps: u32,
    pub supported_bands: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiscoveredPeer {
    pub device_id: String,
    pub friendly_name: String,
    pub os_name: String,
    pub remote_addr: SocketAddr,
    pub server_port: u16,
    #[serde(default = "default_app_version")]
    pub app_version: String,
    #[serde(default = "default_true")]
    pub is_compatible: bool,
    pub supported_transports: Vec<String>,
    #[serde(default)]
    pub wifi_caps: Option<WifiCapsInfo>,
    pub last_seen_epoch_ms: i64,
}

fn default_true() -> bool {
    true
}

#[derive(Clone)]
pub struct PeerDiscovery {
    peers: Arc<Mutex<HashMap<String, DiscoveredPeer>>>,
    device_id: String,
    friendly_name: String,
    os_name: String,
    server_port: u16,
    stop_flag: Arc<AtomicBool>,
}

impl PeerDiscovery {
    pub fn new(
        device_id: String,
        friendly_name: String,
        os_name: String,
        server_port: u16,
    ) -> Self {
        Self {
            peers: Arc::new(Mutex::new(HashMap::new())),
            device_id,
            friendly_name,
            os_name,
            server_port,
            stop_flag: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Start UDP beacon broadcaster, listener, and parallel HTTP subnet probe
    pub async fn start(&self) -> Result<()> {
        let bind_addr = format!("0.0.0.0:{}", DISCOVERY_BROADCAST_PORT);
        let socket = match UdpSocket::bind(&bind_addr).await {
            Ok(s) => s,
            Err(e) => {
                tracing::warn!("Could not bind UDP discovery socket on {}: {}. Trying random port...", bind_addr, e);
                UdpSocket::bind("0.0.0.0:0").await?
            }
        };

        let _ = socket.set_broadcast(true);
        let socket = Arc::new(socket);

        // 1. Broadcaster Loop
        let beacon = PeerBeacon {
            device_id: self.device_id.clone(),
            friendly_name: self.friendly_name.clone(),
            os_name: self.os_name.clone(),
            server_port: self.server_port,
            app_version: CURRENT_APP_VERSION.to_string(),
            supported_transports: vec!["LAN".to_string(), "Wi-Fi Direct".to_string()],
            timestamp_ms: Utc::now().timestamp_millis(),
        };

        let socket_broadcast = socket.clone();
        let stop_broadcast = self.stop_flag.clone();

        tokio::spawn(async move {
            while !stop_broadcast.load(Ordering::SeqCst) {
                let mut current_beacon = beacon.clone();
                current_beacon.timestamp_ms = Utc::now().timestamp_millis();
                if let Ok(bytes) = serde_json::to_vec(&current_beacon) {
                    let mut targets: Vec<SocketAddr> = vec![
                        format!("255.255.255.255:{}", DISCOVERY_BROADCAST_PORT).parse().unwrap(),
                    ];

                    if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
                        for (_, ip) in interfaces {
                            if let std::net::IpAddr::V4(v4) = ip {
                                if !v4.is_loopback() {
                                    let octets = v4.octets();
                                    if let Ok(addr) = format!("{}.{}.{}.255:{}", octets[0], octets[1], octets[2], DISCOVERY_BROADCAST_PORT).parse() {
                                        if !targets.contains(&addr) {
                                            targets.push(addr);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if let Ok(addr) = format!("192.168.42.255:{}", DISCOVERY_BROADCAST_PORT).parse() {
                        if !targets.contains(&addr) { targets.push(addr); }
                    }
                    if let Ok(addr) = format!("172.20.10.255:{}", DISCOVERY_BROADCAST_PORT).parse() {
                        if !targets.contains(&addr) { targets.push(addr); }
                    }

                    for target in &targets {
                        let _ = socket_broadcast.send_to(&bytes, target).await;
                    }
                    tracing::debug!("📡 Beacon broadcast sent to {} targets", targets.len());
                }
                sleep(Duration::from_secs(2)).await;
            }
        });

        // 2. UDP Listener Loop
        let socket_listen = socket.clone();
        let stop_listen = self.stop_flag.clone();
        let peers_map = self.peers.clone();
        let local_device_id = self.device_id.clone();

        tokio::spawn(async move {
            let mut buf = vec![0u8; 4096];
            while !stop_listen.load(Ordering::SeqCst) {
                match socket_listen.recv_from(&mut buf).await {
                    Ok((len, peer_addr)) => {
                        if let Ok(beacon) = serde_json::from_slice::<PeerBeacon>(&buf[..len]) {
                            if beacon.device_id != local_device_id {
                                let remote_socket = SocketAddr::new(peer_addr.ip(), beacon.server_port);
                                let app_ver = if beacon.app_version.is_empty() { CURRENT_APP_VERSION.to_string() } else { beacon.app_version };
                                let compatible = is_version_compatible(&app_ver);

                                tracing::info!(
                                    "📱 UDP beacon received: \"{}\" ({}) at {} | port={} | v={} | compatible={}",
                                    beacon.friendly_name, beacon.os_name, peer_addr.ip(), beacon.server_port, app_ver, compatible
                                );

                                let peer = DiscoveredPeer {
                                    device_id: beacon.device_id.clone(),
                                    friendly_name: if beacon.friendly_name.is_empty() { "Android Device".to_string() } else { beacon.friendly_name },
                                    os_name: if beacon.os_name.is_empty() { "Android".to_string() } else { beacon.os_name },
                                    remote_addr: remote_socket,
                                    server_port: beacon.server_port,
                                    app_version: app_ver,
                                    is_compatible: compatible,
                                    supported_transports: if beacon.supported_transports.is_empty() { vec!["Wi-Fi".to_string()] } else { beacon.supported_transports },
                                    wifi_caps: None,
                                    last_seen_epoch_ms: Utc::now().timestamp_millis(),
                                };
                                peers_map.lock().insert(beacon.device_id, peer);
                            }
                        }
                    }
                    Err(_e) => {
                        sleep(Duration::from_millis(500)).await;
                    }
                }
            }
        });

        // 3. HTTP Subnet Prober Loop (Guarantees bypass of router isolation & firewalls)
        let peers_map_http = self.peers.clone();
        let local_id_http = self.device_id.clone();
        let stop_http = self.stop_flag.clone();
        let semaphore = Arc::new(Semaphore::new(32));
        let negative_cache: Arc<Mutex<HashMap<String, Instant>>> = Arc::new(Mutex::new(HashMap::new()));

        tokio::spawn(async move {
            let client = reqwest::Client::builder()
                .timeout(Duration::from_millis(800))
                .pool_max_idle_per_host(10)
                .build()
                .unwrap_or_default();

            while !stop_http.load(Ordering::SeqCst) {
                let mut prefixes: Vec<(String, u8)> = Vec::new();
                if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
                    for (_, ip) in interfaces {
                        if let std::net::IpAddr::V4(v4) = ip {
                            if !v4.is_loopback() {
                                let octets = v4.octets();
                                prefixes.push((format!("{}.{}.{}", octets[0], octets[1], octets[2]), octets[3]));
                            }
                        }
                    }
                }

                for (prefix, local_host) in prefixes {
                    // Probe nearby IPs in parallel batches
                    let mut tasks = Vec::new();
                    
                    // Priority hosts (common gateways)
                    let priority_hosts = [1, 129, 63, 254, 100];
                    for &h in &priority_hosts {
                        if h != local_host {
                            let ip = format!("{}.{}", prefix, h);
                            let client_ref = client.clone();
                            let peers_ref = peers_map_http.clone();
                            let local_id = local_id_http.clone();
                            let permit = semaphore.clone().acquire_owned().await.unwrap();

                            tasks.push(tokio::spawn(async move {
                                let _permit = permit;
                                let url = format!("http://{}:54321/api/v1/info", ip);
                                if let Ok(resp) = client_ref.get(&url).send().await {
                                    if resp.status().is_success() {
                                        if let Ok(info) = resp.json::<serde_json::Value>().await {
                                            let dev_id = info.get("device_id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                            let name = info.get("device_name").and_then(|v| v.as_str()).unwrap_or("Android Device").to_string();
                                            let os = info.get("os_name").and_then(|v| v.as_str()).unwrap_or("Android").to_string();
                                            let port = info.get("server_port").and_then(|v| v.as_u64()).unwrap_or(54321) as u16;
                                            let app_ver = info.get("app_version").and_then(|v| v.as_str()).unwrap_or("0.1.0").to_string();
                                            let compatible = is_version_compatible(&app_ver);

                                            if !dev_id.is_empty() && dev_id != local_id {
                                                tracing::info!(
                                                    "🌐 HTTP probe found device: \"{}\" ({}) at {}:{} | v={} | compatible={}",
                                                    name, os, ip, port, app_ver, compatible
                                                );
                                                if let Ok(sock) = format!("{}:{}", ip, port).parse::<SocketAddr>() {
                                                    let peer = DiscoveredPeer {
                                                        device_id: dev_id.clone(),
                                                        friendly_name: name,
                                                        os_name: os,
                                                        remote_addr: sock,
                                                        server_port: port,
                                                        app_version: app_ver,
                                                        is_compatible: compatible,
                                                        supported_transports: vec!["🔌 USB Tethering / Wi-Fi".to_string(), "Wi-Fi Direct".to_string(), "LAN".to_string()],
                                                        wifi_caps: None,
                                                        last_seen_epoch_ms: Utc::now().timestamp_millis(),
                                                    };
                                                    peers_ref.lock().insert(dev_id, peer);
                                                }
                                            }
                                        }
                                    }
                                }
                            }));
                        }
                    }

                    for host in 1..=254 {
                        if host == local_host || priority_hosts.contains(&host) { continue; }
                        let ip = format!("{}.{}", prefix, host);
                        
                        {
                            let cache = negative_cache.lock();
                            if let Some(t) = cache.get(&ip) {
                                if t.elapsed().as_secs() < 60 {
                                    continue;
                                }
                            }
                        }

                        let client_ref = client.clone();
                        let peers_ref = peers_map_http.clone();
                        let local_id = local_id_http.clone();
                        let permit = semaphore.clone().acquire_owned().await.unwrap();
                        let neg_cache = negative_cache.clone();

                        tasks.push(tokio::spawn(async move {
                            let _permit = permit;
                            let url = format!("http://{}:54321/api/v1/info", ip);
                            let mut success = false;
                            
                            if let Ok(resp) = client_ref.get(&url).send().await {
                                if resp.status().is_success() {
                                    success = true;
                                    if let Ok(info) = resp.json::<serde_json::Value>().await {
                                        let dev_id = info.get("device_id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                                        let name = info.get("device_name").and_then(|v| v.as_str()).unwrap_or("Android Device").to_string();
                                        let os = info.get("os_name").and_then(|v| v.as_str()).unwrap_or("Android").to_string();
                                        let port = info.get("server_port").and_then(|v| v.as_u64()).unwrap_or(54321) as u16;
                                        let app_ver = info.get("app_version").and_then(|v| v.as_str()).unwrap_or("0.1.0").to_string();
                                        let compatible = is_version_compatible(&app_ver);

                                        if !dev_id.is_empty() && dev_id != local_id {
                                            tracing::info!(
                                                "🌐 HTTP probe found device: \"{}\" ({}) at {}:{} | v={} | compatible={}",
                                                name, os, ip, port, app_ver, compatible
                                            );
                                            if let Ok(sock) = format!("{}:{}", ip, port).parse::<SocketAddr>() {
                                                let peer = DiscoveredPeer {
                                                    device_id: dev_id.clone(),
                                                    friendly_name: name,
                                                    os_name: os,
                                                    remote_addr: sock,
                                                    server_port: port,
                                                    app_version: app_ver,
                                                    is_compatible: compatible,
                                                    supported_transports: vec!["🔌 USB Tethering / Wi-Fi".to_string(), "Wi-Fi Direct".to_string(), "LAN".to_string()],
                                                    wifi_caps: None,
                                                    last_seen_epoch_ms: Utc::now().timestamp_millis(),
                                                };
                                                peers_ref.lock().insert(dev_id, peer);
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if !success {
                                neg_cache.lock().insert(ip, Instant::now());
                            }
                        }));
                    }

                    for task in tasks {
                        let _ = task.await;
                    }
                }
                sleep(Duration::from_secs(4)).await;
            }
        });

        Ok(())
    }

    /// Return list of all active peers seen within the last 30 seconds
    pub fn get_active_peers(&self) -> Vec<DiscoveredPeer> {
        let mut lock = self.peers.lock();
        let now = Utc::now().timestamp_millis();
        let before_count = lock.len();

        // Prune peers older than 30 seconds (was 15s — too aggressive for HTTP probe cycle)
        lock.retain(|id, p| {
            let age_ms = now - p.last_seen_epoch_ms;
            let keep = age_ms < 30_000;
            if !keep {
                tracing::debug!("⏰ Pruning stale peer: \"{}\" ({}) — last seen {}ms ago", p.friendly_name, id, age_ms);
            }
            keep
        });

        let peers: Vec<DiscoveredPeer> = lock.values().cloned().collect();
        if before_count > 0 || !peers.is_empty() {
            tracing::debug!("📋 get_active_peers: {} active (pruned {} stale)", peers.len(), before_count - lock.len());
        }
        peers
    }

    pub fn stop(&self) {
        self.stop_flag.store(true, Ordering::SeqCst);
    }
}

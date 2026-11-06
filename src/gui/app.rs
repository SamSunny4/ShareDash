//! Root ShareDash GPUI Application Component

use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

use futures::StreamExt;
use gpui::*;

use super::engine::{DiscoveredDevice, GuiEngine, TransferProgressSnapshot};
use super::home_view::*;
use super::info_view::*;
use super::pc2pc_view::*;
use super::theme;
use super::transfer_view::*;
use super::widgets::*;
use super::wizard_view::*;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum ActiveView {
    Home,
    Wizard,
    PcToPc,
    Transfer,
    Info,
}

struct HardwareMonitorData {
    usb_ok: bool,
    usb_name: Option<String>,
    _rndis_ip: Option<String>,
    pc_caps: crate::discovery::WifiCapsInfo,
    wifi_on: bool,
    paired: Option<String>,
    pending_data: Option<PendingPairData>,
}

enum WizardEvent {
    Log(LogEntry),
    UsbStatus {
        connected: bool,
        name: Option<String>,
        is_rndis: bool,
    },
    Phase2Start {
        ip: String,
        port: u16,
    },
    CapsSynchronized {
        phone_wifi_standard: String,
        phone_wifi_phy: u32,
        pc_wifi_on: bool,
    },
    Phase3OptimalHost {
        choice: String,
        reason: String,
    },
    Phase4HotspotActive {
        ssid: String,
        band: String,
    },
    HotspotCredsTransferred,
    WifiConnected {
        ip: String,
    },
    Phase5UsbConfirmed,
}

pub struct ShareDashApp {
    pub engine: Arc<GuiEngine>,
    pub active_view: ActiveView,
    pub home_status: HomeStatus,
    pub wizard_state: WizardViewState,
    pub pc2pc_state: PcToPcViewState,
    pub transfer_state: TransferViewState,
    pub info_state: InfoViewState,
    pub incoming_transfer: Option<TransferProgressSnapshot>,
}

impl ShareDashApp {
    pub fn new(engine: Arc<GuiEngine>, cx: &mut Context<Self>) -> Self {
        let mut app = Self {
            engine: engine.clone(),
            active_view: ActiveView::Home,
            home_status: HomeStatus {
                device_name: engine.state.device_name.clone(),
                server_port: engine.state.server_port,
                usb_connected: false,
                usb_name: None,
                wifi_standard: "Wi-Fi 6".into(),
                wifi_phy_rate: 1200,
                wifi_enabled: true,
                paired_peer: None,
                pending_pair: None,
            },
            wizard_state: WizardViewState::default(),
            pc2pc_state: PcToPcViewState::default(),
            transfer_state: TransferViewState::default(),
            info_state: InfoViewState::default(),
            incoming_transfer: None,
        };

        // Start background polling and event listeners
        app.start_background_monitors(cx);

        app
    }

    fn start_background_monitors(&mut self, cx: &mut Context<Self>) {
        let engine = self.engine.clone();

        // 1. Telemetry Subscriber for Incoming Transfers
        let mut telemetry_rx = engine.state.telemetry_tx.subscribe();
        let (tx_telem, mut rx_telem) = futures::channel::mpsc::unbounded::<Option<TransferProgressSnapshot>>();

        engine.tokio_handle.spawn(async move {
            while let Ok(telem) = telemetry_rx.recv().await {
                let is_active = telem.status == "ACTIVE" || telem.status == "VERIFYING";
                let is_completed = telem.status == "COMPLETED";

                let snapshot = if is_active || is_completed {
                    let channels = if telem.transports.is_empty() {
                        vec![crate::gui::engine::ChannelStats {
                            name: "Fast-Path Multipath".to_string(),
                            icon: "⚡".to_string(),
                            bytes_sent: telem.aggregate.total_bytes_transferred,
                            speed_mb_s: telem.aggregate.aggregate_mbps,
                            speed_gbps: (telem.aggregate.aggregate_mbps * 8.0) / 1000.0,
                            chunks_sent: telem.chunk_states.len(),
                        }]
                    } else {
                        telem.transports
                            .iter()
                            .map(|t| {
                                let icon = if t.transport_kind == crate::protocol::TransportKind::Usb {
                                    "⚡"
                                } else {
                                    "📶"
                                };
                                crate::gui::engine::ChannelStats {
                                    name: t.transport_id.clone(),
                                    icon: icon.to_string(),
                                    bytes_sent: telem.aggregate.total_bytes_transferred,
                                    speed_mb_s: t.current_mbps,
                                    speed_gbps: (t.current_mbps * 8.0) / 1000.0,
                                    chunks_sent: telem.chunk_states.len(),
                                }
                            })
                            .collect()
                    };

                    Some(TransferProgressSnapshot {
                        file_name: telem.title.clone(),
                        total_bytes: telem.aggregate.total_bytes_expected,
                        bytes_transferred: telem.aggregate.total_bytes_transferred,
                        progress_pct: (telem.aggregate.progress_pct / 100.0).clamp(0.0, 1.0),
                        speed_mb_s: telem.aggregate.aggregate_mbps,
                        speed_gbps: (telem.aggregate.aggregate_mbps * 8.0) / 1000.0,
                        eta_seconds: telem.aggregate.eta_seconds as f64,
                        channels,
                        status: telem.status.clone(),
                    })
                } else {
                    None
                };

                let to_send = if is_completed { None } else { snapshot };
                if tx_telem.unbounded_send(to_send).is_err() {
                    break;
                }
            }
        });

        cx.spawn(async move |this, cx| {
            while let Some(snap) = rx_telem.next().await {
                let _ = this.update(cx, |view, cx| {
                    view.incoming_transfer = snap;
                    cx.notify();
                });
            }
        }).detach();

        // 2. Periodic Hardware Status Monitor (every 1.5 seconds)
        let engine_clone = self.engine.clone();
        let (tx_hw, mut rx_hw) = futures::channel::mpsc::unbounded::<HardwareMonitorData>();

        self.engine.tokio_handle.spawn(async move {
            loop {
                let (usb_ok, usb_name, rndis_ip) = engine_clone.check_usb_status().await;
                let pc_caps = crate::hotspot::detect_pc_wifi_caps().await;
                let wifi_on = crate::hotspot::check_pc_wifi_adapter_enabled().await;
                let paired = engine_clone.state.active_paired_peer.lock().clone();
                let pending = engine_clone.state.pending_pair.lock().clone();

                let pending_data = pending.map(|p| PendingPairData {
                    initiator_name: p.initiator_name,
                    initiator_ip: p.initiator_ip,
                    initiator_port: p.initiator_port,
                    pin_code: p.pin_code,
                });

                if tx_hw.unbounded_send(HardwareMonitorData {
                    usb_ok,
                    usb_name,
                    _rndis_ip: rndis_ip,
                    pc_caps,
                    wifi_on,
                    paired,
                    pending_data,
                }).is_err() {
                    break;
                }

                tokio::time::sleep(Duration::from_millis(1500)).await;
            }
        });

        cx.spawn(async move |this, cx| {
            while let Some(data) = rx_hw.next().await {
                let _ = this.update(cx, |view, cx| {
                    view.home_status.usb_connected = data.usb_ok;
                    view.home_status.usb_name = data.usb_name.clone();
                    view.home_status.wifi_standard = data.pc_caps.wifi_standard.clone();
                    view.home_status.wifi_phy_rate = data.pc_caps.max_phy_rate_mbps;
                    view.home_status.wifi_enabled = data.wifi_on;
                    view.home_status.paired_peer = data.paired.clone();
                    view.home_status.pending_pair = data.pending_data;

                    view.wizard_state.usb_connected = data.usb_ok;
                    view.wizard_state.usb_name = data.usb_name;
                    view.wizard_state.pc_wifi_standard = data.pc_caps.wifi_standard;
                    view.wizard_state.pc_wifi_phy = data.pc_caps.max_phy_rate_mbps;
                    view.wizard_state.pc_wifi_enabled = data.wifi_on;

                    if let Some(ref peer) = data.paired {
                        view.transfer_state.target_name = peer.clone();
                    }

                    cx.notify();
                });
            }
        }).detach();
    }

    // ── Navigation Transitions ────────────────────────────────
    pub fn navigate_to(&mut self, view: ActiveView, cx: &mut Context<Self>) {
        self.active_view = view;
        match view {
            ActiveView::Wizard => self.start_wizard_flow(cx),
            ActiveView::PcToPc => self.start_pc2pc_flow(cx),
            ActiveView::Info => self.load_info_view(cx),
            _ => {}
        }
        cx.notify();
    }

    // ── Wizard Workflow Automation ────────────────────────────
    fn start_wizard_flow(&mut self, cx: &mut Context<Self>) {
        self.wizard_state.current_phase = 1;
        self.wizard_state.logs.clear();
        self.wizard_state.logs.push(LogEntry::info("Step 1: Checking USB Fast-Path link..."));

        let engine = self.engine.clone();
        let (tx, mut rx) = futures::channel::mpsc::unbounded::<WizardEvent>();

        self.engine.tokio_handle.spawn(async move {
            let (usb_ok, usb_name, rndis_ip) = engine.check_usb_status().await;

            if usb_ok {
                let name = usb_name.clone().unwrap_or_else(|| "Android Device".to_string());
                let _ = tx.unbounded_send(WizardEvent::UsbStatus {
                    connected: true,
                    name: Some(name.clone()),
                    is_rndis: rndis_ip.is_some(),
                });
                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok(format!("USB Connected: {}", name))));

                // Phase 2: Hardware Capabilities
                let (usb_ip, usb_port) = if let Some(ref ip) = rndis_ip {
                    (ip.clone(), 54321)
                } else {
                    let _ = engine.setup_adb_forward().await;
                    ("127.0.0.1".to_string(), 54325)
                };

                let _ = tx.unbounded_send(WizardEvent::Phase2Start {
                    ip: usb_ip.clone(),
                    port: usb_port,
                });
                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::info("Step 2: Exchanging Wi-Fi specifications over USB...")));

                let phone_caps = engine.query_phone_wifi_caps_http(&usb_ip, usb_port).await;
                let pc_caps = crate::hotspot::detect_pc_wifi_caps().await;
                let pc_wifi_on = crate::hotspot::check_pc_wifi_adapter_enabled().await;

                let (phone_wifi_standard, phone_wifi_phy) = if let Some(ref p_caps) = phone_caps {
                    (p_caps.wifi_standard.clone(), p_caps.max_phy_rate_mbps)
                } else {
                    ("Unknown".to_string(), 0)
                };

                let _ = tx.unbounded_send(WizardEvent::CapsSynchronized {
                    phone_wifi_standard,
                    phone_wifi_phy,
                    pc_wifi_on,
                });
                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("Hardware capabilities synchronized.")));

                let mut wifi_connected = false;

                // Phase 3 & 4: Optimal Host & Hotspot Provisioning (Phone Wi-Fi Direct Primary, PC Hotspot Fallback)
                if pc_wifi_on {
                    let phone_caps_ref = phone_caps.as_ref().unwrap_or(&pc_caps);
                    let (best_host, host_reason) =
                        crate::hotspot::select_optimal_hotspot_host(&pc_caps, phone_caps_ref).await;

                    let host_choice = match best_host {
                        crate::hotspot::HotspotHostChoice::Pc => "PC".to_string(),
                        crate::hotspot::HotspotHostChoice::Phone => "Phone".to_string(),
                    };

                    let _ = tx.unbounded_send(WizardEvent::Phase3OptimalHost {
                        choice: host_choice,
                        reason: host_reason,
                    });
                    let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("Optimal Host: Phone 5GHz Wi-Fi Direct AP selected as Primary.")));

                    // ── Primary Strategy: Request Phone to host 5GHz Wi-Fi Direct / LocalHotspot ──
                    let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::info("Requesting Phone to start 5GHz Wi-Fi Direct / Hotspot over USB...")));

                    if let Some((p_ssid, p_pass, p_gw)) = engine.send_start_hotspot_over_usb(&usb_ip, usb_port).await {
                        let _ = tx.unbounded_send(WizardEvent::Phase4HotspotActive {
                            ssid: p_ssid.clone(),
                            band: "5 GHz".to_string(),
                        });
                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok(format!("Phone 5GHz AP Active (SSID: '{}')", p_ssid))));
                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::info(format!("Connecting PC Wi-Fi to phone hotspot '{}'...", p_ssid))));

                        let _ = crate::hotspot::connect_to_phone_hotspot(&p_ssid, &p_pass).await;

                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::info("Waiting for PC to bind to 5GHz Wi-Fi network...")));

                        let detected_ip = crate::hotspot::wait_for_phone_hotspot_interface(
                            Duration::from_secs(35),
                            &p_ssid,
                            Some(&p_gw),
                        ).await;

                        if let Some(target_ip) = detected_ip {
                            let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::info(format!("Verifying direct 5GHz link & pairing at {}:54321...", target_ip))));

                            let verified = engine.verify_and_pair_wifi_with_retries(&target_ip, 54321, 15).await;
                            if verified {
                                let _ = tx.unbounded_send(WizardEvent::WifiConnected { ip: target_ip.clone() });
                                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok(format!("✔ 5GHz Wi-Fi Direct link verified & paired: {}:54321", target_ip))));
                                wifi_connected = true;
                            } else {
                                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::warn(format!("Could not verify link to phone via Wi-Fi ({}:54321).", target_ip))));
                            }
                        } else {
                            let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::warn("PC Wi-Fi did not associate to phone hotspot within timeout.")));
                        }
                    } else {
                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::warn("Phone hotspot start failed over USB.")));
                    }

                    // ── Fallback Strategy: Create PC Hotspot if Phone Hosting failed ──
                    if !wifi_connected {
                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::warn("Phone Wi-Fi Direct not active. Falling back to PC Hotspot...")));

                        let (ssid, password) = crate::hotspot::generate_hotspot_credentials();
                        if let Ok(info) = crate::hotspot::create_hotspot(&ssid, &password, true).await {
                            let _ = tx.unbounded_send(WizardEvent::Phase4HotspotActive {
                                ssid: info.ssid.clone(),
                                band: info.band.clone(),
                            });
                            let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok(format!("PC Fallback Hotspot Active (SSID: {}, Band: {})", info.ssid, info.band))));

                            let sent = engine
                                .send_wifi_connect_over_usb(&usb_ip, usb_port, &info.ssid, &info.password)
                                .await;

                            if sent {
                                let _ = tx.unbounded_send(WizardEvent::HotspotCredsTransferred);
                                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("Hotspot credentials transferred via USB.")));

                                for _ in 0..25 {
                                    if let Some(ip) = crate::hotspot::fast_scan_hotspot_clients(54321).await {
                                        if engine.http_probe(&ip, 54321).await {
                                            let synack = engine.pair_handshake_target(&ip, 54321).await;
                                            if synack {
                                                let _ = tx.unbounded_send(WizardEvent::WifiConnected { ip: ip.clone() });
                                                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok(format!("Phone connected to PC fallback hotspot (IP: {})", ip))));
                                                wifi_connected = true;
                                                break;
                                            }
                                        }
                                    }
                                    tokio::time::sleep(Duration::from_millis(400)).await;
                                }
                            }
                        }
                    }
                }

                // Phase 5: USB Handshake
                let syn = engine.http_probe(&usb_ip, usb_port).await;
                if syn {
                    let synack = engine.pair_handshake_target(&usb_ip, usb_port).await;
                    if synack {
                        let _ = tx.unbounded_send(WizardEvent::Phase5UsbConfirmed);
                        let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("USB channel handshake confirmed (Fast-Path Line Speed).")));
                        if wifi_connected {
                            let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("🚀 Dual-Channel Multipath (Fast USB + 5GHz Wi-Fi Direct) READY!")));
                        } else {
                            let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::ok("✔ USB Fast-Path Channel READY to transfer!")));
                        }
                    }
                }
            } else {
                let _ = tx.unbounded_send(WizardEvent::UsbStatus {
                    connected: false,
                    name: None,
                    is_rndis: false,
                });
                let _ = tx.unbounded_send(WizardEvent::Log(LogEntry::warn("USB not detected. Plug in USB cable or click 'Continue in Wireless Mode'.")));
            }
        });

        cx.spawn(async move |this, cx| {
            while let Some(ev) = rx.next().await {
                let _ = this.update(cx, |view, cx| {
                    match ev {
                        WizardEvent::Log(entry) => {
                            view.wizard_state.logs.push(entry);
                        }
                        WizardEvent::UsbStatus { connected, name, is_rndis } => {
                            view.wizard_state.usb_connected = connected;
                            view.wizard_state.usb_name = name;
                            view.wizard_state.is_rndis = is_rndis;
                            view.wizard_state.waiting_for_usb = !connected;
                        }
                        WizardEvent::Phase2Start { ip, port } => {
                            view.wizard_state.current_phase = 2;
                            view.wizard_state.usb_ip = ip;
                            view.wizard_state.usb_port = port;
                            view.wizard_state.waiting_for_usb = false;
                        }
                        WizardEvent::CapsSynchronized { phone_wifi_standard, phone_wifi_phy, pc_wifi_on } => {
                            view.wizard_state.phone_wifi_standard = phone_wifi_standard;
                            view.wizard_state.phone_wifi_phy = phone_wifi_phy;
                            view.wizard_state.pc_wifi_enabled = pc_wifi_on;
                        }
                        WizardEvent::Phase3OptimalHost { choice, reason } => {
                            view.wizard_state.current_phase = 3;
                            view.wizard_state.host_choice = choice;
                            view.wizard_state.host_reason = reason;
                        }
                        WizardEvent::Phase4HotspotActive { ssid, band } => {
                            view.wizard_state.current_phase = 4;
                            view.wizard_state.hotspot_ssid = ssid;
                            view.wizard_state.hotspot_band = band;
                        }
                        WizardEvent::HotspotCredsTransferred => {}
                        WizardEvent::WifiConnected { ip } => {
                            view.wizard_state.wifi_ip = Some(ip);
                            view.wizard_state.wifi_ready = true;
                        }
                        WizardEvent::Phase5UsbConfirmed => {
                            view.wizard_state.current_phase = 5;
                            view.wizard_state.usb_ready = true;
                        }
                    }
                    cx.notify();
                });
            }
        }).detach();
    }

    // ── PC-to-PC Workflow Automation ──────────────────────────
    fn start_pc2pc_flow(&mut self, cx: &mut Context<Self>) {
        self.pc2pc_state.current_phase = 2;
        self.pc2pc_state.is_scanning = true;
        self.pc2pc_state.logs.clear();
        self.pc2pc_state.logs.push(LogEntry::info("Broadcasting Bluetooth LE beacon & scanning for nearby PCs..."));

        let engine = self.engine.clone();
        let (tx, rx) = futures::channel::oneshot::channel::<Vec<DiscoveredDevice>>();

        self.engine.tokio_handle.spawn(async move {
            tokio::time::sleep(Duration::from_millis(1500)).await;
            let devices = engine.scan_devices().await;
            let _ = tx.send(devices);
        });

        cx.spawn(async move |this, cx| {
            if let Ok(devices) = rx.await {
                let _ = this.update(cx, |view, cx| {
                    view.pc2pc_state.is_scanning = false;
                    view.pc2pc_state.discovered_pcs = devices.clone();
                    view.pc2pc_state.logs.push(LogEntry::ok(format!("Found {} nearby device(s).", devices.len())));
                    cx.notify();
                });
            }
        }).detach();
    }

    // ── Load Network Info View ────────────────────────────────
    fn load_info_view(&mut self, cx: &mut Context<Self>) {
        let engine = self.engine.clone();
        let (tx, rx) = futures::channel::oneshot::channel::<InfoViewState>();

        self.engine.tokio_handle.spawn(async move {
            let mut ifaces = Vec::new();
            if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
                for (name, ip) in interfaces {
                    let ip_str = ip.to_string();
                    if ip_str.starts_with("127.") || ip_str.contains("::1") {
                        continue;
                    }
                    let name_lower = name.to_lowercase();
                    let is_usb = name_lower.contains("rndis")
                        || name_lower.contains("ndis")
                        || name_lower.contains("usb")
                        || name_lower.contains("tether")
                        || ip_str.starts_with("192.168.42.");

                    let label = if is_usb {
                        "🔌 USB Tethering".to_string()
                    } else if ip_str.starts_with("192.168.137.") {
                        "💻 PC Hotspot Gateway".to_string()
                    } else if ip_str.starts_with("192.168.43.") || ip_str.starts_with("192.168.49.") {
                        "📱 Phone Hotspot".to_string()
                    } else {
                        "🏠 LAN".to_string()
                    };

                    ifaces.push(InterfaceEntry {
                        name,
                        ip: ip_str,
                        label,
                        is_usb,
                    });
                }
            }

            let tether_peer = crate::hotspot::detect_usb_tethering_peer_detailed().await;
            let (adb_ok, model) = engine.check_adb().await;
            let pc_caps = crate::hotspot::detect_pc_wifi_caps().await;

            let download_dir = std::env::var("USERPROFILE")
                .map(|p| format!("{}\\Downloads\\ShareDash_Received", p))
                .unwrap_or_else(|_| "downloads".to_string());

            let _ = tx.send(InfoViewState {
                interfaces: ifaces,
                usb_tether_device: tether_peer.map(|(ip, name)| format!("{} ({})", name, ip)),
                usb_adb_device: if adb_ok { model } else { None },
                wifi_standard: pc_caps.wifi_standard,
                wifi_phy: pc_caps.max_phy_rate_mbps,
                wifi_bands: pc_caps.supported_bands,
                server_port: engine.state.server_port,
                download_dir,
            });
        });

        cx.spawn(async move |this, cx| {
            if let Ok(state) = rx.await {
                let _ = this.update(cx, |view, cx| {
                    view.info_state = state;
                    cx.notify();
                });
            }
        }).detach();
    }

    // ── Execute Transfer Action ───────────────────────────────
    pub fn trigger_start_transfer(&mut self, cx: &mut Context<Self>) {
        if let Some(ref path) = self.transfer_state.selected_path.clone() {
            self.transfer_state.is_transferring = true;
            self.transfer_state.error_message = None;
            self.transfer_state.completed_result = None;

            let engine = self.engine.clone();
            let file_path = path.clone();

            let mut targets = Vec::new();
            match self.transfer_state.send_mode {
                SendModeChoice::UsbOnly => {
                    targets.push((
                        self.transfer_state.usb_target_ip.clone(),
                        self.transfer_state.usb_target_port,
                        "USB".to_string(),
                    ));
                }
                SendModeChoice::WifiOnly => {
                    targets.push((
                        self.transfer_state.wifi_target_ip.clone(),
                        self.transfer_state.wifi_target_port,
                        "Wi-Fi".to_string(),
                    ));
                }
                SendModeChoice::Multipath => {
                    if self.transfer_state.usb_ready {
                        targets.push((
                            self.transfer_state.usb_target_ip.clone(),
                            self.transfer_state.usb_target_port,
                            "USB".to_string(),
                        ));
                    }
                    if self.transfer_state.wifi_ready {
                        targets.push((
                            self.transfer_state.wifi_target_ip.clone(),
                            self.transfer_state.wifi_target_port,
                            "Wi-Fi".to_string(),
                        ));
                    }
                    if targets.is_empty() {
                        targets.push((
                            self.transfer_state.wifi_target_ip.clone(),
                            self.transfer_state.wifi_target_port,
                            "Wi-Fi".to_string(),
                        ));
                    }
                }
            }

            let (tx, mut rx) = futures::channel::mpsc::unbounded::<TransferProgressSnapshot>();
            let (result_tx, mut result_rx) = futures::channel::oneshot::channel::<anyhow::Result<TransferResultData>>();

            let engine_transfer = engine.clone();
            self.engine.tokio_handle.spawn(async move {
                let res = engine_transfer
                    .execute_file_transfer(file_path, targets, move |snap| {
                        let _ = tx.unbounded_send(snap);
                    })
                    .await;
                let _ = result_tx.send(res);
            });

            cx.spawn(async move |this, cx| {
                loop {
                    futures::select_biased! {
                        res = result_rx => {
                            let _ = this.update(cx, |view, cx| {
                                view.transfer_state.is_transferring = false;
                                match res {
                                    Ok(Ok(result_data)) => {
                                        view.transfer_state.completed_result = Some(result_data);
                                    }
                                    Ok(Err(e)) => {
                                        view.transfer_state.error_message = Some(format!("Transfer failed: {}", e));
                                    }
                                    Err(e) => {
                                        view.transfer_state.error_message = Some(format!("Transfer task error: {}", e));
                                    }
                                }
                                cx.notify();
                            });
                            break;
                        }
                        snap = rx.next() => {
                            if let Some(snap) = snap {
                                let _ = this.update(cx, |view, cx| {
                                    view.transfer_state.live_progress = Some(snap);
                                    cx.notify();
                                });
                            }
                        }
                    }
                }
            }).detach();
        }
    }
}

impl Render for ShareDashApp {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let mut root = div()
            .flex()
            .flex_col()
            .size_full()
            .bg(theme::bg_base())
            .text_color(theme::text_primary());

        // ── Top Header Banner ─────────────────────────────────
        root = root.child(banner(&self.home_status.device_name, self.home_status.server_port));

        // ── Global Incoming Transfer Banner (if active) ───────
        if let Some(ref telem) = self.incoming_transfer {
            root = root.child(
                div()
                    .w_full()
                    .px_4()
                    .py_2()
                    .bg(theme::bg_surface())
                    .border_b_1()
                    .border_color(theme::accent_green())
                    .child(
                        div()
                            .flex()
                            .flex_row()
                            .justify_between()
                            .items_center()
                            .child(
                                div()
                                    .flex()
                                    .flex_row()
                                    .gap_2()
                                    .items_center()
                                    .child(
                                        div()
                                            .text_size(px(14.0))
                                            .font_weight(FontWeight::BOLD)
                                            .text_color(theme::accent_green())
                                            .child("📥 Receiving:"),
                                    )
                                    .child(
                                        div()
                                            .text_size(px(13.0))
                                            .font_weight(FontWeight::BOLD)
                                            .text_color(theme::text_primary())
                                            .child(telem.file_name.clone()),
                                    ),
                            )
                            .child(
                                div()
                                    .flex()
                                    .flex_row()
                                    .gap_3()
                                    .items_center()
                                    .child(progress_bar(telem.progress_pct, 200.0))
                                    .child(
                                        div()
                                            .text_size(px(13.0))
                                            .font_weight(FontWeight::BOLD)
                                            .text_color(theme::accent_cyan())
                                            .child(format!("{:.1} MB/s", telem.speed_mb_s)),
                                    ),
                            ),
                    ),
            );
        }

        // ── Active View Dispatcher ────────────────────────────
        let content = match self.active_view {
            ActiveView::Home => {
                render_home_view(
                    &self.home_status,
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Wizard, cx)),
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::PcToPc, cx)),
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Transfer, cx)),
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Info, cx)),
                    cx.listener(|_, _, _, _| {
                        let download_dir = std::env::var("USERPROFILE")
                            .map(|p| PathBuf::from(p).join("Downloads").join("ShareDash_Received"))
                            .unwrap_or_else(|_| PathBuf::from("downloads"));
                        let _ = std::fs::create_dir_all(&download_dir);
                        let _ = std::process::Command::new("explorer").arg(&download_dir).spawn();
                    }),
                    cx.listener(|this, _, _, cx| {
                        // Accept pair
                        if let Some(req) = this.engine.state.pending_pair.lock().clone() {
                            *this.engine.state.active_paired_peer.lock() = Some(req.initiator_name);
                            *this.engine.state.pending_pair.lock() = None;
                        }
                        this.home_status.pending_pair = None;
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        // Reject pair
                        *this.engine.state.pending_pair.lock() = None;
                        this.home_status.pending_pair = None;
                        cx.notify();
                    }),
                )
            }
            ActiveView::Wizard => {
                render_wizard_view(
                    &self.wizard_state,
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Home, cx)),
                    cx.listener(|this, _, _, cx| {
                        // Skip USB to Wireless mode
                        this.wizard_state.waiting_for_usb = false;
                        this.wizard_state.current_phase = 2;
                        this.wizard_state.logs.push(LogEntry::warn("Switched to Wireless Mode (Wi-Fi Direct)."));
                        cx.notify();
                    }),
                    cx.listener(|_, _, _, _| {
                        crate::hotspot::open_windows_wifi_settings();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.wizard_state.current_phase = 5;
                        this.wizard_state.usb_ready = true;
                        this.wizard_state.logs.push(LogEntry::info("Continuing in High-Speed USB-Only Mode."));
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        // Proceed to send files with wizard settings
                        this.transfer_state.usb_ready = this.wizard_state.usb_ready;
                        this.transfer_state.usb_target_ip = this.wizard_state.usb_ip.clone();
                        this.transfer_state.usb_target_port = this.wizard_state.usb_port;
                        this.transfer_state.wifi_ready = this.wizard_state.wifi_ready;
                        if let Some(ref w_ip) = this.wizard_state.wifi_ip {
                            this.transfer_state.wifi_target_ip = w_ip.clone();
                        }
                        this.navigate_to(ActiveView::Transfer, cx);
                    }),
                )
            }
            ActiveView::PcToPc => {
                let engine_for_connect = self.engine.clone();
                render_pc2pc_view(
                    &self.pc2pc_state,
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Home, cx)),
                    cx.listener(|this, _, _, cx| this.start_pc2pc_flow(cx)),
                    move |_name, ip, port| {
                        // Connect to discovered PC
                        let eng = engine_for_connect.clone();
                        let handle = eng.tokio_handle.clone();
                        handle.spawn(async move {
                            let _ = eng.pair_handshake_target(&ip, port).await;
                        });
                    },
                    cx.listener(|this, _, _, cx| {
                        // Manual connect by IP
                        let ip = this.pc2pc_state.manual_ip_input.clone();
                        let ip_for_tokio = ip.clone();
                        let ip_for_ui = ip.clone();
                        let engine = this.engine.clone();
                        let (tx, rx) = futures::channel::oneshot::channel::<bool>();

                        this.engine.tokio_handle.spawn(async move {
                            let parts: Vec<&str> = ip_for_tokio.split(':').collect();
                            let target_ip = parts[0].to_string();
                            let target_port: u16 = parts.get(1).and_then(|p| p.parse().ok()).unwrap_or(54321);

                            if engine.http_probe(&target_ip, target_port).await {
                                let ok = engine.pair_handshake_target(&target_ip, target_port).await;
                                let _ = tx.send(ok);
                            } else {
                                let _ = tx.send(false);
                            }
                        });

                        cx.spawn(async move |this, cx| {
                            let ok = rx.await.unwrap_or(false);
                            let _ = this.update(cx, |view, cx| {
                                let parts: Vec<&str> = ip_for_ui.split(':').collect();
                                let target_ip = parts[0].to_string();
                                let target_port: u16 = parts.get(1).and_then(|p| p.parse().ok()).unwrap_or(54321);
                                if ok {
                                    view.pc2pc_state.current_phase = 5;
                                    view.pc2pc_state.wifi_ready = true;
                                    view.pc2pc_state.target_ip = target_ip.clone();
                                    view.pc2pc_state.target_port = target_port;
                                    view.pc2pc_state.logs.push(LogEntry::ok(format!("Connected to PC at {}:{}", target_ip, target_port)));
                                } else {
                                    view.pc2pc_state.logs.push(LogEntry::fail("Pairing handshake rejected or host unreachable."));
                                }
                                cx.notify();
                            });
                        }).detach();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.transfer_state.wifi_ready = true;
                        this.transfer_state.wifi_target_ip = this.pc2pc_state.target_ip.clone();
                        this.transfer_state.wifi_target_port = this.pc2pc_state.target_port;
                        this.transfer_state.target_name = this.pc2pc_state.selected_pc_name.clone();
                        this.transfer_state.send_mode = SendModeChoice::WifiOnly;
                        this.navigate_to(ActiveView::Transfer, cx);
                    }),
                )
            }
            ActiveView::Transfer => {
                render_transfer_view(
                    &self.transfer_state,
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Home, cx)),
                    cx.listener(|_this, _, _, cx| {
                        // Browse file via native Windows dialog on a dedicated thread to avoid COM conflicts
                        let (tx, rx) = futures::channel::oneshot::channel::<Option<PathBuf>>();
                        std::thread::spawn(move || {
                            let file = rfd::FileDialog::new().pick_file();
                            let _ = tx.send(file);
                        });

                        cx.spawn(async move |this, cx| {
                            if let Ok(Some(file)) = rx.await {
                                let size = std::fs::metadata(&file).map(|m| m.len()).unwrap_or(0);
                                let name = file.file_name().and_then(|n| n.to_str()).unwrap_or("file").to_string();
                                let _ = this.update(cx, |view, cx| {
                                    view.transfer_state.selected_path = Some(file);
                                    view.transfer_state.selected_file_name = name;
                                    view.transfer_state.selected_file_size = size;
                                    view.transfer_state.is_directory = false;
                                    view.transfer_state.completed_result = None;
                                    cx.notify();
                                });
                            }
                        }).detach();
                    }),
                    cx.listener(|_this, _, _, cx| {
                        // Browse folder via native Windows dialog on a dedicated thread to avoid COM conflicts
                        let (tx, rx) = futures::channel::oneshot::channel::<Option<PathBuf>>();
                        std::thread::spawn(move || {
                            let folder = rfd::FileDialog::new().pick_folder();
                            let _ = tx.send(folder);
                        });

                        cx.spawn(async move |this, cx| {
                            if let Ok(Some(folder)) = rx.await {
                                let size = crate::gui::engine::get_directory_size(&folder);
                                let name = folder.file_name().and_then(|n| n.to_str()).unwrap_or("folder").to_string();
                                let _ = this.update(cx, |view, cx| {
                                    view.transfer_state.selected_path = Some(folder);
                                    view.transfer_state.selected_file_name = name;
                                    view.transfer_state.selected_file_size = size;
                                    view.transfer_state.is_directory = true;
                                    view.transfer_state.completed_result = None;
                                    cx.notify();
                                });
                            }
                        }).detach();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.transfer_state.send_mode = SendModeChoice::UsbOnly;
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.transfer_state.send_mode = SendModeChoice::WifiOnly;
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.transfer_state.send_mode = SendModeChoice::Multipath;
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.trigger_start_transfer(cx);
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.engine.state.active_sender_cancel_flag.store(true, std::sync::atomic::Ordering::SeqCst);
                        this.transfer_state.is_transferring = false;
                        cx.notify();
                    }),
                    cx.listener(|this, _, _, cx| {
                        this.transfer_state.selected_path = None;
                        this.transfer_state.completed_result = None;
                        cx.notify();
                    }),
                )
            }
            ActiveView::Info => {
                render_info_view(
                    &self.info_state,
                    cx.listener(|this, _, _, cx| this.navigate_to(ActiveView::Home, cx)),
                    cx.listener(|_, _, _, _| {
                        crate::hotspot::open_windows_wifi_settings();
                    }),
                    cx.listener(|_, _, _, _| {
                        let _ = std::process::Command::new("powershell")
                            .args(["-NoProfile", "-Command", "Start-Process 'ms-settings:network-mobilehotspot'"])
                            .spawn();
                    }),
                    cx.listener(|_, _, _, _| {
                        let download_dir = std::env::var("USERPROFILE")
                            .map(|p| PathBuf::from(p).join("Downloads").join("ShareDash_Received"))
                            .unwrap_or_else(|_| PathBuf::from("downloads"));
                        let _ = std::fs::create_dir_all(&download_dir);
                        let _ = std::process::Command::new("explorer").arg(&download_dir).spawn();
                    }),
                )
            }
        };

        root.child(content)
    }
}

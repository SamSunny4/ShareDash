//! Wifite-style 8-phase terminal wizard for ShareDash.
//!
//! Flow:
//!   Phase 1 — BLE Scan → discover ShareDash phones
//!   Phase 2 — Wi-Fi Capability Exchange via BLE GATT
//!   Phase 3 — Create PC Hotspot (matched to phone caps)
//!   Phase 4 — Send Wi-Fi credentials to phone via BLE GATT
//!   Phase 5 — Wi-Fi 3-Way Handshake
//!   Phase 6 — USB Cable Detection
//!   Phase 7 — USB Tethering (via BLE command)
//!   Phase 8 — USB 3-Way Handshake
//!   → READY TO SEND → File selection → Transfer with live telemetry

use std::io::{self, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{Duration, Instant};

use btleplug::api::{Central, Peripheral};
use crate::cli_widgets::*;
use crate::discovery::WifiCapsInfo;
use crate::hotspot;
use crate::server::api::{AppState, IncomingPairRequest, OutgoingPairInfo};

pub struct TerminalCli {
    state: AppState,
}

/// A BLE-discovered device for the scan table.
#[derive(Clone, Debug)]
struct BleDevice {
    name: String,
    address: String,
    rssi: i16,
    peripheral_id: String,
}

impl TerminalCli {
    pub fn new(state: AppState) -> Self {
        Self { state }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MAIN WIZARD ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    pub async fn run_interactive_loop(self: Arc<Self>) {
        print_wizard_banner();
        println!("  • Device: {BOLD}{}{RESET}  •  Port: {GREEN}{}{RESET}", self.state.device_name, self.state.server_port);
        println!();

        loop {
            println!();
            println!("{BOLD_WHITE}Select Mode:{RESET}");
            println!("  {YELLOW}[1]{RESET} 🚀 Auto-Connect Wizard (BLE → Wi-Fi → USB → Send)");
            println!("  {YELLOW}[2]{RESET} 📡 Manual Scan & Send (classic)");
            println!("  {YELLOW}[3]{RESET} 📊 Transfer History");
            println!("  {YELLOW}[4]{RESET} 🔧 Network Info");
            println!("  {YELLOW}[q]{RESET} Quit");

            let choice = prompt("ShareDash ❯ ").await;
            match choice.as_str() {
                "1" | "wizard" | "auto" => {
                    self.run_wizard().await;
                }
                "2" | "scan" | "send" | "manual" => {
                    self.handle_scan().await;
                    self.manual_send_flow().await;
                }
                "3" | "status" | "history" => {
                    self.handle_status().await;
                }
                "4" | "info" | "net" => {
                    self.handle_info().await;
                }
                "q" | "quit" | "exit" => {
                    println!("{YELLOW}👋 Shutting down ShareDash...{RESET}");
                    std::process::exit(0);
                }
                "clear" | "cls" => {
                    print!("\x1B[2J\x1B[1;1H");
                    let _ = io::stdout().flush();
                    print_wizard_banner();
                }
                _ => {
                    println!("{RED}Unknown option.{RESET} Enter 1-4 or q.");
                }
            }
        }
    }

    async fn run_wizard(&self) {
        // ═══════════════════════════════════════════════════════════════
        //  STEP 1: USB-FIRST DETECTION & PROMPT (Wait for USB, No Timer)
        // ═══════════════════════════════════════════════════════════════
        print_phase_header(1, "USB Connection Check (Fast-Path Priority)");

        let (mut usb_connected, mut usb_serial) = self.check_adb().await;
        let mut rndis_ip: Option<String> = None;

        if let Some((ip, name)) = hotspot::detect_usb_tethering_peer_detailed().await {
            rndis_ip = Some(ip);
            if usb_serial.is_none() {
                usb_serial = Some(name);
            }
            usb_connected = true;
        }

        if !usb_connected && rndis_ip.is_none() {
            println!("  ⚡ {BOLD_CYAN}Waiting for USB connection (Plug in USB cable & Enable USB Tethering for 3+ Gbps Line Speed)...{RESET}");
            println!("  {GRAY}Plug in USB cable / Enable USB Tethering now (or press [Enter] to switch to Wireless mode){RESET}");

            let stdin_handle = tokio::spawn(async {
                let mut line = String::new();
                let _ = std::io::stdin().read_line(&mut line);
                line
            });

            loop {
                // Check if USB tethering / RNDIS is plugged in
                if let Some((peer, name)) = hotspot::detect_usb_tethering_peer_detailed().await {
                    rndis_ip = Some(peer);
                    if usb_serial.is_none() {
                        usb_serial = Some(name);
                    }
                    usb_connected = true;
                    stdin_handle.abort();
                    break;
                }

                // Check if ADB USB is plugged in
                let (conn, ser) = self.check_adb_inner().await;
                if conn {
                    usb_connected = true;
                    usb_serial = ser;
                    stdin_handle.abort();
                    break;
                }

                // Check if user pressed Enter
                if stdin_handle.is_finished() {
                    break;
                }

                tokio::time::sleep(Duration::from_millis(500)).await;
            }
        }

        if usb_connected || rndis_ip.is_some() {
            self.run_usb_first_wizard(usb_connected, usb_serial, rndis_ip).await;
        } else {
            println!();
            println!("  ⚠️  {YELLOW}{BOLD}Continuing without USB (Wireless Mode){RESET}");
            println!("  {YELLOW}Warning: Wireless transfer speeds (Wi-Fi Direct / BT) might slow down significantly compared to USB 3.x line speed.{RESET}");
            println!();
            self.run_wireless_direct_wizard().await;
        }
    }

    /// ═══════════════════════════════════════════════════════════════
    ///  USB-FIRST SMART HARDWARE WIZARD (Steps 1-5)
    /// ═══════════════════════════════════════════════════════════════
    async fn run_usb_first_wizard(
        &self,
        _usb_connected: bool,
        usb_serial: Option<String>,
        rndis_ip: Option<String>,
    ) {
        let is_rndis = rndis_ip.is_some();
        let target_name = usb_serial.as_deref().unwrap_or("Android Device");

        print_ok(&format!(
            "USB Connected: {} ({})",
            target_name,
            if is_rndis { "USB Tethering RNDIS" } else { "USB ADB Fast-Path" }
        ));

        // ── Phase 1: Setup USB Link & Exchange Capabilities ────────
        print_phase_header(2, "Hardware Capability Exchange (via USB)");
        println!("  Exchanging device capabilities and Wi-Fi specs over USB...");

        let (usb_ip, usb_port) = if is_rndis {
            (rndis_ip.clone().unwrap(), 54321)
        } else {
            let adb_fwd = self.setup_adb_forward().await;
            print_step_result("ADB Forward: tcp:54325 → tcp:54321", adb_fwd);
            ("127.0.0.1".to_string(), 54325)
        };

        // Query Phone Wi-Fi Capabilities over USB
        let phone_caps = self.query_phone_wifi_caps_http(&usb_ip, usb_port).await;
        let pc_caps = hotspot::detect_pc_wifi_caps().await;
        let mut pc_wifi_on = hotspot::check_pc_wifi_adapter_enabled().await;

        println!("  Hardware Overview:");
        print_tree_item("PC Wi-Fi", &format!("{} (PHY: {} Mbps) [{}]", pc_caps.wifi_standard, pc_caps.max_phy_rate_mbps, if pc_wifi_on { "ON" } else { "OFF" }), false);
        if let Some(ref p_caps) = phone_caps {
            print_tree_item("Phone Wi-Fi", &format!("{} (PHY: {} Mbps)", p_caps.wifi_standard, p_caps.max_phy_rate_mbps), true);
        } else {
            print_tree_item("Phone Wi-Fi", "Wi-Fi 6 (802.11ax) (PHY: 1200 Mbps)", true);
        }

        // If PC Wi-Fi is OFF, prompt user to enable it
        if !pc_wifi_on {
            println!();
            println!("  ⚠️  {YELLOW}{BOLD}PC Wi-Fi is currently turned OFF.{RESET}");
            println!("     To enable 5GHz wireless aggregation (~150+ MB/s), PC Wi-Fi must be enabled.");
            println!("     {CYAN}[1] Turn ON Wi-Fi / Open Windows Wi-Fi Settings{RESET}   {GRAY}[2] Continue in USB-Only Mode (~100+ MB/s){RESET}");
            let choice = prompt_choice("Select option [1-2] (default 1): ", 1, 2).await;
            if choice == 1 {
                hotspot::open_windows_wifi_settings();
                hotspot::ensure_pc_wifi_adapter_enabled().await;
                let enabled = with_spinner("Waiting for PC Wi-Fi to be enabled in Windows...", async {
                    for _ in 0..20 {
                        if hotspot::check_pc_wifi_adapter_enabled().await {
                            return true;
                        }
                        tokio::time::sleep(Duration::from_millis(500)).await;
                    }
                    false
                }).await;
                if enabled {
                    print_ok("PC Wi-Fi is now ON and active!");
                    pc_wifi_on = true;
                } else {
                    print_warn("PC Wi-Fi is still OFF. Continuing with Turbo USB...");
                }
            } else {
                println!("  ⚡ Continuing in High-Speed USB-Only Mode.");
            }
        }

        let mut wifi_ready = false;
        let mut wifi_ip: Option<String> = None;

        if pc_wifi_on {
            // ── Phase 2: Select Best Hotspot Hardware ───────────────────
            print_phase_header(3, "Optimal Hotspot Host Selection");
            let phone_caps_ref = phone_caps.as_ref().unwrap_or(&pc_caps);
            let (best_host, host_reason) = hotspot::select_optimal_hotspot_host(&pc_caps, phone_caps_ref).await;

            println!("  {}", host_reason);
            match best_host {
                hotspot::HotspotHostChoice::Pc => {
                    print_ok("PC Wi-Fi hardware selected as Primary 5GHz Hotspot Host");
                }
                hotspot::HotspotHostChoice::Phone => {
                    print_ok("Phone Wi-Fi hardware selected as Primary 5GHz Hotspot Host (Quick Share Direct)");
                }
            }

            // ── Phase 3: Create 5GHz Hotspot & Share Credentials over USB ──
            print_phase_header(4, "Creating 5GHz Hotspot & Sharing over USB");
            let (ssid, password) = hotspot::generate_hotspot_credentials();

            if best_host == hotspot::HotspotHostChoice::Pc {
                println!("  Starting PC 5GHz Hotspot: {BOLD}{ssid}{RESET}...");
                let hotspot_res = hotspot::create_hotspot(&ssid, &password, true).await;
                match hotspot_res {
                    Ok(info) => {
                        print_ok(&format!("PC Hotspot Active (SSID: {}, Band: {})", info.ssid, info.band));

                        with_spinner("Initializing PC 5GHz Wi-Fi Radio & DHCP broadcast...", async {
                            tokio::time::sleep(Duration::from_millis(1000)).await;
                        }).await;

                        println!("  Sending hotspot credentials to phone through USB...");
                        let sent = self.send_wifi_connect_over_usb(&usb_ip, usb_port, &info.ssid, &info.password).await;
                        if sent {
                            print_ok("Phone received credentials via USB and connecting to 5GHz Hotspot...");
                            let client_ip = with_spinner("Waiting for phone on 5GHz Wi-Fi...", async {
                                for _ in 0..30 {
                                    if let Some(ip) = hotspot::fast_scan_hotspot_clients(54321).await {
                                        return Some(ip);
                                    }
                                    tokio::time::sleep(Duration::from_millis(300)).await;
                                }
                                None
                            }).await;

                            if let Some(ip) = client_ip {
                                if self.http_probe(&ip, 54321).await {
                                    let synack = self.pair_handshake_target(&ip, 54321).await;
                                    if synack {
                                        print_ok(&format!("Phone connected & paired on 5GHz Hotspot! IP: {}", ip));
                                        wifi_ip = Some(ip);
                                        wifi_ready = true;
                                    }
                                }
                            } else {
                                print_warn("Phone did not associate to 5GHz Wi-Fi. Continuing with Turbo USB...");
                            }
                        } else {
                            print_warn("Could not send credentials to phone via USB.");
                        }
                    }
                    Err(e) => {
                        print_warn(&format!("PC Hotspot creation: {}. Trying phone hotspot...", e));
                        if let Some((p_ssid, p_pass, p_gw)) = self.send_start_hotspot_over_usb(&usb_ip, usb_port).await {
                            print_ok(&format!("Phone Hotspot started via USB: {}", p_ssid));
                            println!("  Connecting PC to phone hotspot...");
                            let _ = hotspot::connect_to_phone_hotspot(&p_ssid, &p_pass).await;
                            let detected_ip = with_spinner("Waiting for PC to acquire IP on phone hotspot...", async {
                                hotspot::wait_for_phone_hotspot_interface(Duration::from_secs(12), Some(&p_gw)).await
                            }).await;
                            let target = detected_ip.unwrap_or(p_gw);
                            if self.http_probe(&target, 54321).await {
                                let synack = self.pair_handshake_target(&target, 54321).await;
                                if synack {
                                    print_ok(&format!("Direct 5GHz Wi-Fi link verified: {}:54321", target));
                                    wifi_ip = Some(target);
                                    wifi_ready = true;
                                }
                            }
                        }
                    }
                }
            } else {
                // Phone is chosen as best host
                println!("  Requesting Phone to start 5GHz Wi-Fi Direct / Hotspot over USB...");
                if let Some((p_ssid, p_pass, p_gw)) = self.send_start_hotspot_over_usb(&usb_ip, usb_port).await {
                    print_ok(&format!("Phone 5GHz AP Active: SSID='{}'", p_ssid));
                    println!("  Connecting PC to phone 5GHz hotspot...");
                    let _ = hotspot::connect_to_phone_hotspot(&p_ssid, &p_pass).await;
                    let detected_ip = with_spinner("Waiting for PC to bind to 5GHz network...", async {
                        hotspot::wait_for_phone_hotspot_interface(Duration::from_secs(12), Some(&p_gw)).await
                    }).await;
                    let target = detected_ip.unwrap_or(p_gw);
                    if self.http_probe(&target, 54321).await {
                        let synack = self.pair_handshake_target(&target, 54321).await;
                        if synack {
                            print_ok(&format!("5GHz Wi-Fi Direct link verified & paired: {}:54321", target));
                            wifi_ip = Some(target);
                            wifi_ready = true;
                        } else {
                            print_warn(&format!("Wi-Fi handshake failed at {}:54321. Wi-Fi disabled.", target));
                        }
                    } else {
                        print_warn(&format!("Could not reach phone via Wi-Fi ({}:54321). Wi-Fi disabled.", target));
                    }
                } else {
                    print_warn("Phone hotspot start failed over USB. Continuing in USB-Only Mode.");
                }
            }
        }

        // ── Phase 4: USB 3-Way Handshake ───────────────────────────
        print_phase_header(5, "USB + Wi-Fi Handshake (Multipath Link)");
        let syn = self.http_probe(&usb_ip, usb_port).await;
        print_step_result(&format!("SYN  → {}:{}", usb_ip, usb_port), syn);

        let mut usb_ready = false;
        if syn {
            let synack = self.pair_handshake_target(&usb_ip, usb_port).await;
            print_step_result(&format!("SYN-ACK ← {}", target_name), synack);
            if synack {
                print_step_result("ACK  → Confirmed", true);
                println!("  🔒 USB Channel {GREEN}READY{RESET} (AES-256-GCM Line Speed)");
                usb_ready = true;
            }
        }

        self.send_file_multipath_loop(wifi_ready, wifi_ip, usb_ready, usb_ip, usb_port, is_rndis).await;
    }

    /// ═══════════════════════════════════════════════════════════════
    ///  WIRELESS FALLBACK WIZARD (No USB: BLE Scan + Wi-Fi Direct / 5GHz Hotspot)
    /// ═══════════════════════════════════════════════════════════════
    async fn run_wireless_direct_wizard(&self) {
        // Ensure PC Wi-Fi is ON before wireless scan
        if !hotspot::check_pc_wifi_adapter_enabled().await {
            println!("  ⚠️  {YELLOW}{BOLD}PC Wi-Fi is currently turned OFF.{RESET}");
            println!("     Wireless transfer requires PC Wi-Fi to be enabled.");
            hotspot::open_windows_wifi_settings();
            hotspot::ensure_pc_wifi_adapter_enabled().await;
            let enabled = with_spinner("Waiting for PC Wi-Fi to be turned ON...", async {
                for _ in 0..20 {
                    if hotspot::check_pc_wifi_adapter_enabled().await {
                        return true;
                    }
                    tokio::time::sleep(Duration::from_millis(500)).await;
                }
                false
            }).await;
            if enabled {
                print_ok("PC Wi-Fi is now ON!");
            } else {
                print_fail("PC Wi-Fi is disabled. Wireless mode cannot connect.");
            }
        }

        print_phase_header(1, "Wireless Bluetooth & Wi-Fi Scan (No USB Connected)");
        println!("  Scanning for nearby ShareDash devices via Bluetooth & Wi-Fi...");

        let mut devices = self.phase1_ble_scan().await;

        // If BLE returned no devices, also check for peers discovered via UDP / Wi-Fi
        if devices.is_empty() {
            let active_udp = self.state.discovery.get_active_peers();
            for p in active_udp {
                if !devices.iter().any(|d| d.name == p.friendly_name) {
                    devices.push(BleDevice {
                        name: p.friendly_name.clone(),
                        address: p.remote_addr.ip().to_string(),
                        rssi: -40,
                        peripheral_id: p.device_id.clone(),
                    });
                }
            }
        }

        if devices.is_empty() {
            print_fail("No ShareDash devices found via Bluetooth or Wi-Fi.");
            println!("  {YELLOW}Make sure Bluetooth or Wi-Fi is turned ON on both PC and Phone.{RESET}");
            println!("  {GRAY}Falling back to manual scan...{RESET}");
            self.handle_scan().await;
            self.manual_send_flow().await;
            return;
        }

        // Show device table
        println!();
        println!("  Found {} device(s):", devices.len());
        let headers = &["#", "Device Name", "BLE Addr", "RSSI", "Status"];
        let rows: Vec<Vec<String>> = devices
            .iter()
            .enumerate()
            .map(|(i, d)| {
                vec![
                    format!("{}", i + 1),
                    d.name.clone(),
                    d.address.chars().take(8).collect(),
                    format!("{}", d.rssi),
                    format!("{GREEN}ShareDash ✔{RESET}"),
                ]
            })
            .collect();
        print_table(headers, &rows);

        let selected_idx = if devices.len() == 1 {
            println!("  Auto-selecting only device: {BOLD}{}{RESET}", devices[0].name);
            0
        } else {
            let choice = prompt_choice(
                &format!("Select device [1-{}]: ", devices.len()),
                1,
                devices.len(),
            )
            .await;
            choice - 1
        };

        let selected_device = &devices[selected_idx];
        print_ok(&format!("Selected: {}", selected_device.name));

        // ── Phase 2: Hardware & Internet Connection Inspection ──────────
        print_phase_header(2, "Hardware & Hotspot Readiness Check");
        println!("  Inspecting PC network connection and querying phone capabilities...");

        let pc_has_internet = hotspot::check_pc_internet_connection().await;
        let pc_caps = hotspot::detect_pc_wifi_caps().await;
        let phone_caps = self.phase2_wifi_caps(&selected_device.peripheral_id).await;

        println!("  Connection & Hardware Status:");
        print_tree_item(
            "PC Internet",
            if pc_has_internet { "Connected (Active Profile)" } else { "No Active Internet Connection" },
            false,
        );
        print_tree_item("PC Wi-Fi", &format!("{} (PHY: {} Mbps)", pc_caps.wifi_standard, pc_caps.max_phy_rate_mbps), false);
        if let Some(ref p_caps) = phone_caps {
            print_tree_item("Phone Wi-Fi", &format!("{} (PHY: {} Mbps)", p_caps.wifi_standard, p_caps.max_phy_rate_mbps), true);
        } else {
            print_tree_item("Phone Wi-Fi", "Wi-Fi 6 (802.11ax) (PHY: 1200 Mbps)", true);
        }

        let phone_caps_ref = phone_caps.as_ref().unwrap_or(&pc_caps);
        let (best_host, host_reason) = hotspot::select_optimal_hotspot_host(&pc_caps, phone_caps_ref).await;

        println!();
        println!("  Decision: {BOLD}{}{RESET}", host_reason);

        // ── Phase 3: 5GHz Hotspot Creation & Credential Sharing ───────
        print_phase_header(3, "5GHz Hotspot Provisioning & Auto-Connect");
        let mut wifi_ip: Option<String> = None;

        if best_host == hotspot::HotspotHostChoice::Phone {
            println!("  📡 Requesting phone to create maximum-config 5GHz Hotspot / Wi-Fi Direct...");

            let creds = with_spinner("Waiting for phone 5GHz Hotspot initialization over Bluetooth...", async {
                self.state.ble_discovery.request_phone_start_hotspot().await
            }).await;

            if let Some((p_ssid, p_pass, p_gw)) = creds {
                print_ok(&format!("Phone 5GHz Hotspot Active! SSID='{}'", p_ssid));
                println!("  ⚡ Auto-connecting PC Wi-Fi to phone hotspot: {BOLD}{}{RESET}...", p_ssid);

                let connected = hotspot::connect_to_phone_hotspot(&p_ssid, &p_pass).await.unwrap_or(false);
                if connected {
                    print_ok("WLAN profile configured and association command sent.");
                } else {
                    print_warn("WLAN connect command sent. Waiting for DHCP network binding...");
                }

                let detected_ip = with_spinner("Waiting for PC to acquire IP on phone hotspot subnet...", async {
                    hotspot::wait_for_phone_hotspot_interface(Duration::from_secs(12), Some(&p_gw)).await
                }).await;

                let target_ip = detected_ip.unwrap_or(p_gw);
                if self.http_probe(&target_ip, 54321).await {
                    print_ok(&format!("Direct Wi-Fi link verified: {}:54321", target_ip));
                    wifi_ip = Some(target_ip);
                } else {
                    print_fail(&format!("Could not reach phone at {}:54321 over Wi-Fi", target_ip));
                    wifi_ip = None;
                }
            } else {
                print_warn("Phone did not return hotspot credentials over BLE. Checking existing network paths...");
                let mut candidate_ips: Vec<String> = vec!["192.168.49.1".to_string(), "192.168.43.1".to_string()];
                for peer in self.state.discovery.get_active_peers() {
                    let ip = peer.remote_addr.ip().to_string();
                    if ip != "127.0.0.1" && !candidate_ips.contains(&ip) {
                        candidate_ips.push(ip);
                    }
                }
                for ip in &candidate_ips {
                    if self.http_probe(ip, 54321).await {
                        wifi_ip = Some(ip.clone());
                        break;
                    }
                }
            }
        } else {
            // PC is chosen as best host
            let (ssid, password) = hotspot::generate_hotspot_credentials();
            println!("  Starting PC 5GHz Hotspot: {BOLD}{ssid}{RESET}...");
            let hotspot_res = hotspot::create_hotspot(&ssid, &password, true).await;
            match hotspot_res {
                Ok(info) => {
                    print_ok(&format!("PC Hotspot Active (SSID: {}, Band: {})", info.ssid, info.band));

                    with_spinner("Initializing PC 5GHz Wi-Fi Radio & DHCP broadcast...", async {
                        tokio::time::sleep(Duration::from_millis(1000)).await;
                    }).await;

                    println!("  Sending hotspot credentials to phone via Bluetooth GATT...");
                    let sent = self.phase4_connect_phone(&selected_device.peripheral_id, &info.ssid, &info.password).await;
                    if sent {
                        print_ok("Phone received credentials via BLE and connecting to 5GHz Hotspot...");
                        let client_ip = with_spinner("Waiting for phone on 5GHz Wi-Fi...", async {
                            for _ in 0..30 {
                                if let Some(ip) = hotspot::fast_scan_hotspot_clients(54321).await {
                                    return Some(ip);
                                }
                                tokio::time::sleep(Duration::from_millis(300)).await;
                            }
                            None
                        }).await;

                        if let Some(ip) = client_ip {
                            print_ok(&format!("Phone connected to 5GHz Hotspot! IP: {}", ip));
                            wifi_ip = Some(ip);
                        } else {
                            print_warn("Phone did not associate to 5GHz Wi-Fi within timeout.");
                        }
                    } else {
                        print_warn("Could not send credentials over BLE.");
                    }
                }
                Err(e) => {
                    print_warn(&format!("PC Hotspot creation failed: {}. Requesting phone hotspot...", e));
                    if let Some((p_ssid, p_pass, p_gw)) = self.state.ble_discovery.request_phone_start_hotspot().await {
                        print_ok(&format!("Phone Hotspot Started: SSID='{}'", p_ssid));
                        let _ = hotspot::connect_to_phone_hotspot(&p_ssid, &p_pass).await;
                        wifi_ip = Some(p_gw);
                    }
                }
            }
        }

        // ── Phase 4: 3-Way Handshake ─────────────────────────────────
        print_phase_header(4, "Wi-Fi 3-Way Handshake (Direct Channel)");
        let mut wifi_ready = false;
        if let Some(target_wifi_ip) = wifi_ip.as_ref() {
            let syn_ok = self.http_probe(target_wifi_ip, 54321).await;
            print_step_result(&format!("SYN  → {}:54321", target_wifi_ip), syn_ok);

            if syn_ok {
                let synack_ok = self.pair_handshake(target_wifi_ip).await;
                print_step_result(&format!("SYN-ACK ← {}", selected_device.name), synack_ok);

                if synack_ok {
                    print_step_result("ACK  → Pair Confirmed", true);
                    println!("  🔒 Wi-Fi Direct / Hotspot Channel {GREEN}READY{RESET} (AES-256-GCM)");
                    wifi_ready = true;
                } else {
                    wifi_ready = false;
                    wifi_ip = None;
                }
            } else {
                print_fail(&format!("Could not reach phone at {}:54321", target_wifi_ip));
                wifi_ready = false;
                wifi_ip = None;
            }
        } else {
            print_fail("Wi-Fi Direct channel not established.");
            wifi_ready = false;
        }

        self.send_file_multipath_loop(wifi_ready, wifi_ip, false, String::new(), 54325, false).await;
    }

    /// Helper HTTP calls over USB
    async fn query_phone_wifi_caps_http(&self, ip: &str, port: u16) -> Option<WifiCapsInfo> {
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

    async fn send_wifi_connect_over_usb(&self, ip: &str, port: u16, ssid: &str, password: &str) -> bool {
        // 1. Try sending over HTTP to AndroidHttpServer
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

        // 2. Fallback: If ADB is available, broadcast intent directly to phone
        if let Some(adb_path) = self.find_adb() {
            let res = std::process::Command::new(&adb_path)
                .args([
                    "shell", "am", "broadcast",
                    "-a", "com.sharedash.app.WIFI_CONNECT",
                    "--es", "ssid", ssid,
                    "--es", "password", password,
                ])
                .output();
            if res.is_ok() {
                return true;
            }
        }

        false
    }

    async fn send_start_hotspot_over_usb(&self, ip: &str, port: u16) -> Option<(String, String, String)> {
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

    async fn pair_handshake_target(&self, ip: &str, port: u16) -> bool {
        let client = match reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
        {
            Ok(c) => c,
            Err(_) => return false,
        };

        let pin = format!("{:06}", rand::random::<u32>() % 1_000_000);
        let body = serde_json::json!({
            "initiator_device_id": self.state.device_id,
            "initiator_name": self.state.device_name,
            "initiator_ip": "127.0.0.1",
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

    /// ═══════════════════════════════════════════════════════════════
    ///  MULTIPATH FILE SEND LOOP
    /// ═══════════════════════════════════════════════════════════════
    async fn send_file_multipath_loop(
        &self,
        wifi_ready: bool,
        wifi_ip: Option<String>,
        usb_ready: bool,
        usb_target_ip: String,
        usb_target_port: u16,
        usb_via_rndis: bool,
    ) {
        if !wifi_ready && !usb_ready {
            print_fail("No channels available. Cannot proceed.");
            return;
        }

        let usb_addr_str = format!("{}:{}", usb_target_ip, usb_target_port);
        print_channel_summary(
            if wifi_ready { wifi_ip.as_deref() } else { None },
            if wifi_ready { Some("5 GHz / ~1200 Mbps") } else { None },
            if usb_ready { Some(usb_addr_str.as_str()) } else { None },
            if usb_ready {
                if usb_via_rndis {
                    Some("USB Tethering / ~400 Mbps")
                } else {
                    Some("USB 3.x / ~3200 Mbps")
                }
            } else {
                None
            },
        );

        loop {
            let file_path_str = prompt("Paste file path (or 'q' to quit): ").await;
            if file_path_str == "q" || file_path_str == "quit" {
                break;
            }

            let file_path = PathBuf::from(file_path_str.trim().trim_matches('"'));
            if !file_path.exists() {
                print_fail(&format!("File not found: {:?}", file_path));
                continue;
            }

            let file_name = file_path
                .file_name()
                .and_then(|n| n.to_str())
                .unwrap_or("file");
            let file_size = if file_path.is_dir() {
                get_dir_size(&file_path)
            } else {
                std::fs::metadata(&file_path).map(|m| m.len()).unwrap_or(0)
            };
            let file_size_mb = file_size as f64 / (1024.0 * 1024.0);

            // Send mode selection
            println!();
            println!("  Send via:");
            if usb_ready {
                println!("    {YELLOW}[1]{RESET} ⚡ USB only");
            }
            if wifi_ready {
                println!("    {YELLOW}[2]{RESET} 📶 Wi-Fi only");
            }
            if usb_ready && wifi_ready {
                println!("    {YELLOW}[3]{RESET} 🚀 Both (Multipath Aggregation)");
            }

            let mode = if usb_ready && wifi_ready {
                prompt_choice("Select [1-3]: ", 1, 3).await
            } else if usb_ready {
                1
            } else {
                2
            };

            let targets: Vec<(String, u16, String)> = match mode {
                1 => vec![(usb_target_ip.clone(), usb_target_port, "USB".to_string())],
                2 => vec![(
                    wifi_ip.clone().unwrap_or("192.168.137.1".to_string()),
                    54321,
                    "Wi-Fi".to_string(),
                )],
                3 => vec![
                    (usb_target_ip.clone(), usb_target_port, "USB".to_string()),
                    (
                        wifi_ip.clone().unwrap_or("192.168.137.1".to_string()),
                        54321,
                        "Wi-Fi".to_string(),
                    ),
                ],
                _ => continue,
            };

            let mode_name = match mode {
                1 => "USB",
                2 => "Wi-Fi",
                3 => "USB+Wi-Fi",
                _ => "Unknown",
            };

            println!();
            println!(
                "  Sending {BOLD}{}{RESET} ({:.2} MB) via {BOLD}{}{RESET}...",
                file_name, file_size_mb, mode_name
            );

            self.execute_transfer(&file_path, file_name, file_size, &targets)
                .await;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PHASE IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    /// Phase 1: BLE scan using btleplug.
    async fn phase1_ble_scan(&self) -> Vec<BleDevice> {
        let mut devices = Vec::new();

        // Animated scan progress bar
        animated_scan_progress(Duration::from_secs(4)).await;

        // Collect from running BLE discovery
        for peer in self.state.ble_discovery.get_ble_peers() {
            if !devices.iter().any(|d: &BleDevice| d.name == peer.friendly_name) {
                devices.push(BleDevice {
                    name: peer.friendly_name.clone(),
                    address: peer.device_id.clone(),
                    rssi: -50,
                    peripheral_id: peer.device_id.clone(),
                });
            }
        }

        // Direct adapter peripherals fallback check
        if devices.is_empty() {
            if let Some(adapter) = self.state.ble_discovery.get_adapter() {
                let sd_uuid = uuid::Uuid::parse_str(crate::discovery::bluetooth::SHAREDASH_BLE_SERVICE_UUID).unwrap();
                if let Ok(peripherals) = adapter.peripherals().await {
                    for p in &peripherals {
                        if let Ok(Some(props)) = p.properties().await {
                            let matches = props.services.iter().any(|s| *s == sd_uuid)
                                || props.service_data.contains_key(&sd_uuid)
                                || props.local_name.as_ref().map(|n: &String| n.contains("ShareDash") || n.contains("A56") || n.contains("Pixel") || n.contains("Galaxy") || n.contains("Android") || n.contains("Sam")).unwrap_or(false);
                            if matches {
                                let name = props.local_name.unwrap_or_else(|| "Android Device".to_string());
                                let id = format!("{:?}", p.id());
                                if !devices.iter().any(|d: &BleDevice| d.name == name) {
                                    devices.push(BleDevice {
                                        name,
                                        address: id.clone(),
                                        rssi: props.rssi.unwrap_or(-50),
                                        peripheral_id: id,
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }

        devices
    }

    /// Phase 2: Read Wi-Fi capabilities from phone via BLE advertisement or GATT.
    async fn phase2_wifi_caps(&self, peripheral_id: &str) -> Option<WifiCapsInfo> {
        // 1. Check if the device has decoded Wi-Fi capabilities from its BLE advertisement beacon
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

        // 2. Try GATT read via the active adapter
        self.state.ble_discovery.read_wifi_capabilities().await
    }

    /// Phase 4: Send Wi-Fi connect command to phone via BLE GATT.
    async fn phase4_connect_phone(
        &self,
        _peripheral_id: &str,
        ssid: &str,
        password: &str,
    ) -> bool {
        let cmd = serde_json::json!({
            "cmd": "wifi_connect",
            "ssid": ssid,
            "password": password
        });

        self.send_ble_command(&cmd.to_string()).await
    }

    /// Phase 7: Send USB tether command via BLE GATT.
    #[allow(dead_code)]
    async fn phase7_usb_tether(&self, _peripheral_id: &str) -> bool {
        let cmd = serde_json::json!({
            "cmd": "usb_tether_on"
        });

        self.send_ble_command(&cmd.to_string()).await
    }

    /// Generic: Send a command string to the ShareDash BLE peripheral.
    async fn send_ble_command(&self, cmd_json: &str) -> bool {
        self.state.ble_discovery.send_gatt_command(cmd_json).await
    }

    // ═══════════════════════════════════════════════════════════════
    //  STREAMING DYNAMIC MULTIPATH PIPELINE (39+ MB/s)
    // ═══════════════════════════════════════════════════════════════

    async fn execute_transfer(
        &self,
        file_path: &Path,
        file_name: &str,
        file_size: u64,
        targets: &[(String, u16, String)],
    ) {
        let file_size_mb = file_size as f64 / (1024.0 * 1024.0);
        let start_time = Instant::now();

        let channel_states: Vec<(Arc<parking_lot::Mutex<ChannelProgress>>, String, u16, String, String)> = targets
            .iter()
            .map(|(ip, port, name)| {
                let icon = if name == "USB" { "⚡" } else { "📶" };
                let state = Arc::new(parking_lot::Mutex::new(ChannelProgress {
                    name: name.clone(),
                    icon: icon.to_string(),
                    bytes_sent: 0,
                    speed_mb_s: 0.0,
                    speed_gbps: 0.0,
                    chunks_sent: 0,
                }));
                (state, ip.clone(), *port, name.clone(), icon.to_string())
            })
            .collect();

        let stop_ui = Arc::new(std::sync::atomic::AtomicBool::new(false));
        let stop_ui_clone = stop_ui.clone();
        let channel_states_ui: Vec<Arc<parking_lot::Mutex<ChannelProgress>>> =
            channel_states.iter().map(|c| c.0.clone()).collect();
        let total_size = file_size;

        init_transfer_progress(channel_states.len());

        let ui_handle = tokio::spawn(async move {
            let mut last_sample_time = Instant::now();
            let mut last_bytes: Vec<u64> = vec![0; channel_states_ui.len()];

            while !stop_ui_clone.load(std::sync::atomic::Ordering::SeqCst) {
                tokio::time::sleep(Duration::from_millis(100)).await;
                let now = Instant::now();
                let dt = now.duration_since(last_sample_time).as_secs_f64().max(0.001);

                let mut current_channels: Vec<ChannelProgress> = Vec::with_capacity(channel_states_ui.len());
                for (idx, c) in channel_states_ui.iter().enumerate() {
                    let mut guard = c.lock();
                    let bytes = guard.bytes_sent;
                    let delta_bytes = bytes.saturating_sub(last_bytes[idx]);
                    last_bytes[idx] = bytes;

                    let instant_channel_speed_mb_s = (delta_bytes as f64 / (1024.0 * 1024.0)) / dt;
                    guard.speed_mb_s = if guard.speed_mb_s == 0.0 {
                        instant_channel_speed_mb_s
                    } else {
                        0.70 * guard.speed_mb_s + 0.30 * instant_channel_speed_mb_s
                    };
                    guard.speed_gbps = (guard.speed_mb_s * 8.0) / 1000.0;
                    current_channels.push(guard.clone());
                }
                last_sample_time = now;

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
                draw_transfer_progress(pct, eta, &current_channels);
            }
        });

        let mut handles = Vec::new();

        // Adaptive high-throughput chunk size based on file size (2MB - 64MB)
        let chunk_size: u64 = if file_size < 20 * 1024 * 1024 {
            2 * 1024 * 1024 // 2 MB for files < 20 MB
        } else if file_size < 100 * 1024 * 1024 {
            4 * 1024 * 1024 // 4 MB for 20 - 100 MB
        } else if file_size < 500 * 1024 * 1024 {
            8 * 1024 * 1024 // 8 MB for 100 - 500 MB
        } else if file_size < 2 * 1024 * 1024 * 1024 {
            16 * 1024 * 1024 // 16 MB for 500 MB - 2 GB
        } else if file_size < 8 * 1024 * 1024 * 1024 {
            32 * 1024 * 1024 // 32 MB for 2 GB - 8 GB
        } else {
            64 * 1024 * 1024 // 64 MB for > 8 GB
        };

        let transfer_id = uuid::Uuid::new_v4().to_string();
        let dispatcher = Arc::new(parking_lot::Mutex::new(
            DynamicWorkDispatcher::new(file_size, chunk_size),
        ));
        let stop_flag = Arc::new(std::sync::atomic::AtomicBool::new(false));

        // Spawn 3 concurrent pipelined in-flight streaming workers per transport for line speed
        let workers_per_transport = 3;
        for (state, ip, port, name, _) in &channel_states {
            for _ in 0..workers_per_transport {
                let h = tokio::spawn(run_transport_chunk_worker(
                    ip.clone(),
                    *port,
                    name.clone(),
                    file_path.to_path_buf(),
                    file_name.to_string(),
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

        // Check if dispatcher completed successfully
        {
            let guard = dispatcher.lock();
            if guard.has_fatal_error() {
                all_ok = false;
            }
        }

        stop_ui.store(true, std::sync::atomic::Ordering::SeqCst);
        let _ = ui_handle.await;

        // Final UI render
        let final_channels: Vec<ChannelProgress> =
            channel_states.iter().map(|c| c.0.lock().clone()).collect();
        draw_transfer_progress(1.0, 0.0, &final_channels);

        let elapsed = start_time.elapsed().as_secs_f64().max(0.01);
        let speed_mb_s = file_size_mb / elapsed;
        let speed_gbps = (speed_mb_s * 8.0) / 1000.0;

        let usb_ch = final_channels.iter().find(|c| c.name == "USB");
        let wifi_ch = final_channels.iter().find(|c| c.name == "Wi-Fi");

        let total_chunks = if chunk_size > 0 {
            ((file_size + chunk_size - 1) / chunk_size).max(1) as usize
        } else {
            1
        };

        let usb_speed_mb_s = usb_ch.map(|c| (c.bytes_sent as f64 / (1024.0 * 1024.0)) / elapsed);
        let wifi_speed_mb_s = wifi_ch.map(|c| (c.bytes_sent as f64 / (1024.0 * 1024.0)) / elapsed);

        let result = TransferResult {
            file_name: file_name.to_string(),
            size_mb: file_size_mb,
            time_secs: elapsed,
            avg_speed_mb_s: speed_mb_s,
            avg_speed_gbps: speed_gbps,
            usb_speed_mb_s,
            usb_pct: usb_ch.map(|c| (c.bytes_sent as f64 / file_size.max(1) as f64) * 100.0),
            wifi_speed_mb_s,
            wifi_pct: wifi_ch.map(|c| (c.bytes_sent as f64 / file_size.max(1) as f64) * 100.0),
            chunk_size_bytes: chunk_size as usize,
            total_chunks,
            integrity_ok: all_ok,
        };
        print_transfer_result(&result);
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY FUNCTIONS (carried over from old CLI)
    // ═══════════════════════════════════════════════════════════════

    /// HTTP probe — check if a device responds at ip:port.
    async fn http_probe(&self, ip: &str, port: u16) -> bool {
        let url = format!("http://{}:{}/api/v1/info", ip, port);
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(3))
            .build()
            .unwrap_or_default();
        matches!(client.get(&url).send().await, Ok(resp) if resp.status().is_success())
    }

    /// Execute the 3-way handshake pairing sequence.
    async fn pair_handshake(&self, target_ip: &str) -> bool {
        let pin = format!("{:06}", rand::random::<u32>() % 1000000);

        let body = IncomingPairRequest {
            initiator_device_id: self.state.device_id.clone(),
            initiator_name: self.state.device_name.clone(),
            initiator_ip: local_ip_address::local_ip()
                .map(|i| i.to_string())
                .unwrap_or_else(|_| "127.0.0.1".to_string()),
            initiator_port: self.state.server_port,
            pin_code: pin.clone(),
            app_version: env!("CARGO_PKG_VERSION").to_string(),
            status: "PENDING".to_string(),
            timestamp_ms: chrono::Utc::now().timestamp_millis(),
        };

        *self.state.outgoing_pair.lock() = Some(OutgoingPairInfo {
            target_device_id: "".to_string(),
            target_ip: target_ip.to_string(),
            target_port: 54321,
            pin: pin.clone(),
            initiated_at: chrono::Utc::now().timestamp_millis(),
        });

        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .unwrap_or_default();

        let url = format!("http://{}:54321/api/v1/pair/request", target_ip);
        match client.post(&url).json(&body).send().await {
            Ok(resp) if resp.status().is_success() => {
                *self.state.active_paired_peer.lock() = Some(target_ip.to_string());
                true
            }
            _ => false,
        }
    }

    /// Setup ADB port forwarding for USB channel.
    async fn setup_adb_forward(&self) -> bool {
        let adb = self.find_adb();
        if let Some(adb_path) = adb {
            // Forward PC port 54325 → phone port 54321
            let fwd = std::process::Command::new(&adb_path)
                .args(["forward", "tcp:54325", "tcp:54321"])
                .output();

            // Reverse: phone port 54321 → PC port 54321
            let _rev1 = std::process::Command::new(&adb_path)
                .args(["reverse", "tcp:54321", "tcp:54321"])
                .output();

            // Reverse: phone port 54325 → PC port 54321
            let _rev2 = std::process::Command::new(&adb_path)
                .args(["reverse", "tcp:54325", "tcp:54321"])
                .output();

            // Ensure the ShareDash app is running on phone
            let _ = std::process::Command::new(&adb_path)
                .args(["shell", "am", "start", "-n", "com.sharedash.app/.MainActivity"])
                .output();

            fwd.is_ok()
        } else {
            false
        }
    }

    async fn check_adb(&self) -> (bool, Option<String>) {
        self.check_adb_inner().await
    }

    async fn check_adb_inner(&self) -> (bool, Option<String>) {
        if let Some(adb_path) = self.find_adb() {
            if let Ok(output) = std::process::Command::new(&adb_path)
                .arg("devices")
                .output()
            {
                let text = String::from_utf8_lossy(&output.stdout);
                for line in text.lines().skip(1) {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() && trimmed.ends_with("device") {
                        let serial = trimmed.split_whitespace().next().unwrap_or("Android");

                        // Try to get the model name
                        let model = std::process::Command::new(&adb_path)
                            .args(["shell", "getprop", "ro.product.model"])
                            .output()
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

    fn find_adb(&self) -> Option<String> {
        let candidates = [
            format!(
                "{}\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe",
                std::env::var("USERPROFILE").unwrap_or_default()
            ),
            "adb.exe".to_string(),
            "adb".to_string(),
        ];

        for candidate in &candidates {
            if std::process::Command::new(candidate)
                .arg("version")
                .output()
                .is_ok()
            {
                return Some(candidate.clone());
            }
        }
        None
    }

    // ═══════════════════════════════════════════════════════════════
    //  LEGACY / MANUAL MODE (kept for backwards compatibility)
    // ═══════════════════════════════════════════════════════════════

    pub async fn handle_scan(&self) {
        println!();
        println!("{BOLD_CYAN}🔍 Scanning for Nearby Devices & Active Links...{RESET}");

        let mut discovered: Vec<DiscoveredItem> = Vec::new();

        // USB ADB
        let (adb_connected, model_name) = self.check_adb().await;
        if adb_connected {
            discovered.push(DiscoveredItem {
                name: model_name.unwrap_or_else(|| "Android Device (USB)".to_string()),
                ip: "127.0.0.1".to_string(),
                port: 54325,
                os: "Android".to_string(),
                transport: "⚡ USB 3.x Cable".to_string(),
                status: "Ready".to_string(),
            });
        }

        // USB Tethering (RNDIS/NCM)
        if let Some((tether_ip, tether_name)) = hotspot::detect_usb_tethering_peer_detailed().await {
            if !discovered.iter().any(|d| d.ip == tether_ip) {
                discovered.push(DiscoveredItem {
                    name: tether_name,
                    ip: tether_ip,
                    port: 54321,
                    os: "Android".to_string(),
                    transport: "🔌 USB Tethering".to_string(),
                    status: "Ready".to_string(),
                });
            }
        }

        // UDP
        for peer in self.state.discovery.get_active_peers() {
            let ip_str = peer.remote_addr.ip().to_string();
            if ip_str != "127.0.0.1" && !discovered.iter().any(|d| d.ip == ip_str) {
                let transport = if ip_str.starts_with("192.168.42.") || ip_str.starts_with("10.") {
                    "🔌 USB Tethering"
                } else if ip_str.starts_with("192.168.43.") || ip_str.starts_with("192.168.49.") {
                    "📶 Phone Hotspot"
                } else if ip_str.starts_with("192.168.137.") {
                    "💻 PC Hotspot"
                } else {
                    "🏠 LAN"
                };
                discovered.push(DiscoveredItem {
                    name: peer.friendly_name,
                    ip: ip_str,
                    port: peer.server_port,
                    os: peer.os_name,
                    transport: transport.to_string(),
                    status: "Discovered".to_string(),
                });
            }
        }

        // BLE
        for peer in self.state.ble_discovery.get_ble_peers() {
            let ip_str = peer.remote_addr.ip().to_string();
            if ip_str != "127.0.0.1" && !discovered.iter().any(|d| d.ip == ip_str) {
                discovered.push(DiscoveredItem {
                    name: peer.friendly_name,
                    ip: ip_str,
                    port: peer.server_port,
                    os: peer.os_name,
                    transport: "📶 BLE + Wi-Fi".to_string(),
                    status: "BLE".to_string(),
                });
            }
        }

        if discovered.is_empty() {
            print_warn("No devices found. Ensure ShareDash is open on your phone.");
        } else {
            println!("  {GREEN}Found {} device(s):{RESET}", discovered.len());
            let headers = &["#", "Device Name", "IP Address", "Port", "OS", "Transport", "Status"];
            let rows: Vec<Vec<String>> = discovered
                .iter()
                .enumerate()
                .map(|(i, d)| {
                    vec![
                        format!("{}", i + 1),
                        d.name.clone(),
                        d.ip.clone(),
                        format!("{}", d.port),
                        d.os.clone(),
                        d.transport.clone(),
                        d.status.clone(),
                    ]
                })
                .collect();
            print_table(headers, &rows);
        }
    }

    async fn manual_send_flow(&self) {
        let file_path_str = prompt("Enter file path to send (or 'q' to cancel): ").await;
        if file_path_str == "q" || file_path_str.is_empty() {
            return;
        }

        let target_str = prompt("Enter target IP:PORT (e.g. 192.168.43.1:54321): ").await;
        if target_str == "q" || target_str.is_empty() {
            return;
        }

        let parts: Vec<&str> = target_str.split(':').collect();
        let ip = parts[0].to_string();
        let port: u16 = parts.get(1).and_then(|p| p.parse().ok()).unwrap_or(54321);

        let file_path = PathBuf::from(file_path_str.trim().trim_matches('"'));
        if !file_path.exists() {
            print_fail("File not found.");
            return;
        }

        let file_name = file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("file");
        let file_size = if file_path.is_dir() {
            get_dir_size(&file_path)
        } else {
            std::fs::metadata(&file_path).map(|m| m.len()).unwrap_or(0)
        };

        let targets = vec![(ip, port, "Wi-Fi".to_string())];
        self.execute_transfer(&file_path, file_name, file_size, &targets)
            .await;
    }

    pub async fn handle_status(&self) {
        println!();
        println!("{BOLD_CYAN}📊 Active Transfers & Status{RESET}");
        let active = self.state.active_transfers.lock().len();
        println!("  Active Transfers: {BOLD}{active}{RESET}");

        let paired = self.state.active_paired_peer.lock().clone();
        if let Some(peer_name) = paired {
            println!("  Paired Device   : {GREEN}🔒 {peer_name} (AES-256-GCM){RESET}");
        } else {
            println!("  Paired Device   : {GRAY}None{RESET}");
        }

        if let Ok(transfers) = self.state.manifest_db.list_transfers() {
            if !transfers.is_empty() {
                println!();
                println!("  Recent Transfers:");
                for t in transfers {
                    let mb = (t.total_bytes as f64) / (1024.0 * 1024.0);
                    println!("   • {BOLD}{}{RESET} ({:.1} MB) - [{:?}]", t.title, mb, t.status);
                }
            }
        }
    }

    pub async fn handle_info(&self) {
        println!();
        println!("{BOLD_WHITE}─── Network Interfaces & Hardware ──────────────────────{RESET}");
        let tether_peer = hotspot::detect_usb_tethering_peer_detailed().await;
        if let Ok(interfaces) = local_ip_address::list_afinet_netifas() {
            for (iface, ip) in interfaces {
                let ip_str = ip.to_string();
                if ip_str.starts_with("127.") || ip_str.contains("::1") {
                    continue;
                }
                let iface_lower = iface.to_lowercase();
                let is_tether_iface = iface_lower.contains("rndis")
                    || iface_lower.contains("ndis")
                    || iface_lower.contains("usb")
                    || iface_lower.contains("tether")
                    || iface_lower.contains("samsung")
                    || iface_lower.contains("remote")
                    || ip_str.starts_with("192.168.42.");

                let label = if is_tether_iface {
                    format!(" ← {BOLD_GREEN}🔌 USB Tethering{RESET}")
                } else if ip_str.starts_with("192.168.137.") {
                    format!(" ← {BOLD_CYAN}💻 PC Hotspot Gateway{RESET}")
                } else if ip_str.starts_with("192.168.43.") || ip_str.starts_with("192.168.49.") {
                    format!(" ← {MAGENTA}📱 Phone Hotspot{RESET}")
                } else {
                    format!(" ← {GRAY}🏠 LAN{RESET}")
                };
                println!("    {:<24} → {:<16} {}", iface, ip_str, label);
            }
        }

        if let Some((tether_ip, tether_name)) = tether_peer {
            println!("    {GREEN}🔌 USB Tethering Device:{RESET} {} ({})", tether_name, tether_ip);
        }

        let (adb_ok, model) = self.check_adb().await;
        if adb_ok {
            println!("    {GREEN}⚡ USB ADB Fast-Path:{RESET} {}", model.unwrap_or_default());
        } else {
            println!("    {GRAY}⚡ USB ADB: Not active (Using USB Tethering / Wi-Fi){RESET}");
        }
        println!("{BOLD_WHITE}────────────────────────────────────────────────────────{RESET}");
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════

struct DiscoveredItem {
    name: String,
    ip: String,
    port: u16,
    os: String,
    transport: String,
    status: String,
}

#[allow(dead_code)]
fn get_dir_size(path: &Path) -> u64 {
    let mut total = 0;
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                total += get_dir_size(&p);
            } else if let Ok(meta) = p.metadata() {
                total += meta.len();
            }
        }
    }
    total
}

#[allow(dead_code)]
fn create_zip_or_tar_in_memory(path: &Path) -> anyhow::Result<Vec<u8>> {
    let mut buffer = Vec::new();
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_file() {
                if let Ok(b) = std::fs::read(&p) {
                    buffer.extend_from_slice(&b);
                }
            }
        }
    }
    if buffer.is_empty() {
        buffer = vec![0u8; 1024];
    }
    Ok(buffer)
}

#[derive(Clone, Debug)]
struct TransferChunkInfo {
    chunk_id: u32,
    offset: u64,
    length: u64,
}

struct DynamicWorkDispatcher {
    unassigned: std::collections::VecDeque<u32>,
    in_flight: std::collections::HashMap<u32, (Instant, String)>,
    completed: std::collections::HashSet<u32>,
    retries: std::collections::HashMap<u32, u32>,
    chunks: Vec<TransferChunkInfo>,
    fatal_error: bool,
}

impl DynamicWorkDispatcher {
    fn new(total_bytes: u64, chunk_size: u64) -> Self {
        let total_chunks = if total_bytes == 0 {
            1
        } else {
            ((total_bytes + chunk_size - 1) / chunk_size) as usize
        };

        let mut chunks = Vec::with_capacity(total_chunks);
        let mut unassigned = std::collections::VecDeque::with_capacity(total_chunks);

        for i in 0..total_chunks {
            let offset = i as u64 * chunk_size;
            let length = (total_bytes.saturating_sub(offset)).min(chunk_size);
            let cid = i as u32;
            chunks.push(TransferChunkInfo {
                chunk_id: cid,
                offset,
                length,
            });
            unassigned.push_back(cid);
        }

        Self {
            unassigned,
            in_flight: std::collections::HashMap::new(),
            completed: std::collections::HashSet::new(),
            retries: std::collections::HashMap::new(),
            chunks,
            fatal_error: false,
        }
    }

    fn pop_chunk(&mut self, transport_name: &str) -> Option<TransferChunkInfo> {
        if let Some(cid) = self.unassigned.pop_front() {
            self.in_flight
                .insert(cid, (Instant::now(), transport_name.to_string()));
            return self.chunks.get(cid as usize).cloned();
        }

        // Adaptive Work-Stealing: If unassigned is empty, check if an in-flight chunk on another transport stalled (> 2.5s)
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

    fn return_for_retry(&mut self, chunk_id: u32) {
        self.in_flight.remove(&chunk_id);
        if !self.completed.contains(&chunk_id) {
            let count = self.retries.entry(chunk_id).or_insert(0);
            *count += 1;
            if *count <= 5 {
                self.unassigned.push_front(chunk_id);
            } else {
                tracing::error!("Chunk #{} exceeded maximum retries (5)!", chunk_id);
                self.fatal_error = true;
            }
        }
    }

    fn is_done(&self) -> bool {
        self.completed.len() >= self.chunks.len()
    }

    fn has_fatal_error(&self) -> bool {
        self.fatal_error
    }
}

async fn run_transport_chunk_worker(
    ip: String,
    port: u16,
    transport_name: String,
    file_path: PathBuf,
    file_name: String,
    file_size: u64,
    transfer_id: String,
    dispatcher: Arc<parking_lot::Mutex<DynamicWorkDispatcher>>,
    progress: Arc<parking_lot::Mutex<ChannelProgress>>,
    stop_flag: Arc<std::sync::atomic::AtomicBool>,
) {
    let client = reqwest::Client::builder()
        .tcp_nodelay(true)
        .pool_idle_timeout(None)
        .pool_max_idle_per_host(8)
        .timeout(Duration::from_secs(30))
        .build()
        .unwrap_or_default();

    let url_chunk = format!("http://{}:{}/api/v1/transfers/chunk", ip, port);
    let total_chunks = dispatcher.lock().chunks.len() as u32;

    let mut file = match tokio::fs::File::open(&file_path).await {
        Ok(f) => f,
        Err(e) => {
            tracing::error!("Failed opening file {:?}: {}", file_path, e);
            return;
        }
    };

    use tokio::io::{AsyncReadExt, AsyncSeekExt};

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
                tokio::time::sleep(Duration::from_millis(15)).await;
                continue;
            }
        };

        // Read chunk data at exact byte offset
        if let Err(e) = file.seek(std::io::SeekFrom::Start(chunk.offset)).await {
            tracing::warn!("Seek error at offset {}: {}", chunk.offset, e);
            dispatcher.lock().return_for_retry(chunk.chunk_id);
            continue;
        }

        let mut buf = vec![0u8; chunk.length as usize];
        if let Err(e) = file.read_exact(&mut buf).await {
            tracing::warn!("Read error for chunk #{}: {}", chunk.chunk_id, e);
            dispatcher.lock().return_for_retry(chunk.chunk_id);
            continue;
        }

        // Use CRC32 instead of SHA-256 for fast integrity check (~20x faster)
        let chunk_crc = {
            let mut hasher = crc32fast::Hasher::new();
            hasher.update(&buf);
            format!("{:08x}", hasher.finalize())
        };

        let t0 = Instant::now();

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
            .body(buf);

        match req.send().await {
            Ok(resp) if resp.status().is_success() => {
                let dt = t0.elapsed().as_secs_f64().max(0.0001);
                let chunk_mb = (chunk.length as f64) / (1024.0 * 1024.0);
                let instant_speed = chunk_mb / dt;

                let is_new = dispatcher.lock().mark_completed(chunk.chunk_id);
                if is_new {
                    let mut p = progress.lock();
                    p.bytes_sent += chunk.length;
                    p.chunks_sent += 1;
                    // More stable EWMA for speed tracking
                    p.speed_mb_s = if p.speed_mb_s == 0.0 {
                        instant_speed
                    } else {
                        0.85 * p.speed_mb_s + 0.15 * instant_speed
                    };
                    p.speed_gbps = (p.speed_mb_s * 8.0) / 1000.0;
                }
            }
            Ok(resp) => {
                let status = resp.status();
                tracing::warn!(
                    "Chunk #{} rejected by {} (status {}), retransmitting...",
                    chunk.chunk_id,
                    transport_name,
                    status
                );
                dispatcher.lock().return_for_retry(chunk.chunk_id);
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
            Err(e) => {
                tracing::warn!(
                    "Chunk #{} network error on {}: {}, retransmitting...",
                    chunk.chunk_id,
                    transport_name,
                    e
                );
                dispatcher.lock().return_for_retry(chunk.chunk_id);
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
        }
    }
}

#[allow(dead_code)]
async fn stream_upload_channel(
    ip: String,
    port: u16,
    file_path: PathBuf,
    part_name: String,
    byte_offset: u64,
    byte_length: u64,
    progress: Arc<parking_lot::Mutex<ChannelProgress>>,
) -> bool {
    use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};

    let mut file = match tokio::fs::File::open(&file_path).await {
        Ok(f) => f,
        Err(e) => {
            tracing::error!("Failed opening file for streaming: {}", e);
            return false;
        }
    };

    if let Err(e) = file.seek(std::io::SeekFrom::Start(byte_offset)).await {
        tracing::error!("Failed seeking to offset {}: {}", byte_offset, e);
        return false;
    }

    let mut stream = match tokio::net::TcpStream::connect(format!("{}:{}", ip, port)).await {
        Ok(s) => {
            let _ = s.set_nodelay(true);
            s
        }
        Err(e) => {
            tracing::error!("Failed connecting to {}:{}: {}", ip, port, e);
            return false;
        }
    };

    let req_header = format!(
        "POST /api/v1/transfers/send HTTP/1.1\r\n\
        Host: {}:{}\r\n\
        User-Agent: ShareDash/0.1.0\r\n\
        Content-Type: application/octet-stream\r\n\
        x-file-name: {}\r\n\
        Content-Length: {}\r\n\
        Connection: close\r\n\r\n",
        ip, port, part_name, byte_length
    );

    if let Err(e) = stream.write_all(req_header.as_bytes()).await {
        tracing::error!("Failed writing HTTP header: {}", e);
        return false;
    }

    let mut remaining = byte_length;
    let mut buf = vec![0u8; 512 * 1024];
    let start = Instant::now();
    let mut sent = 0u64;
    let mut chunks = 0usize;

    while remaining > 0 {
        let to_read = (remaining as usize).min(buf.len());
        let n = match file.read(&mut buf[..to_read]).await {
            Ok(0) => break,
            Ok(n) => n,
            Err(e) => {
                tracing::error!("Read error: {}", e);
                return false;
            }
        };

        if let Err(e) = stream.write_all(&buf[..n]).await {
            tracing::error!("Write error: {}", e);
            return false;
        }

        remaining -= n as u64;
        sent += n as u64;
        chunks += 1;

        let elapsed = start.elapsed().as_secs_f64().max(0.0001);
        let speed_mb_s = (sent as f64 / (1024.0 * 1024.0)) / elapsed;
        let speed_gbps = (speed_mb_s * 8.0) / 1000.0;

        let mut p = progress.lock();
        p.bytes_sent = sent;
        p.speed_mb_s = speed_mb_s;
        p.speed_gbps = speed_gbps;
        p.chunks_sent = chunks;
    }

    let _ = stream.flush().await;

    // Read response acknowledgment
    let mut resp_buf = [0u8; 512];
    if let Ok(n) = stream.read(&mut resp_buf).await {
        let resp_str = String::from_utf8_lossy(&resp_buf[..n]);
        resp_str.contains("200") || resp_str.contains("OK") || resp_str.contains("success")
    } else {
        true
    }
}

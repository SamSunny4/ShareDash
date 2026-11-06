//! ShareDash PC-to-PC Wireless Mode View
//!
//! Visualizes Bluetooth LE discovery, hardware compatibility check,
//! hotspot creation, and high-speed Wi-Fi channel establishment.

use gpui::*;
use super::theme;
use super::widgets::*;

#[derive(Clone, Debug)]
pub struct PcToPcViewState {
    pub current_phase: u8,
    pub is_scanning: bool,
    pub pc_wifi_standard: String,
    pub pc_wifi_phy: u32,
    pub pc_wifi_enabled: bool,
    pub bt_available: bool,
    pub discovered_pcs: Vec<crate::gui::engine::DiscoveredDevice>,
    pub selected_pc_name: String,
    pub target_ip: String,
    pub target_port: u16,
    pub selected_band: String,
    pub host_decision: String,
    pub wifi_ready: bool,
    pub manual_ip_input: String,
    pub logs: Vec<LogEntry>,
}

impl Default for PcToPcViewState {
    fn default() -> Self {
        Self {
            current_phase: 1,
            is_scanning: false,
            pc_wifi_standard: "Wi-Fi 6 (802.11ax)".into(),
            pc_wifi_phy: 1200,
            pc_wifi_enabled: true,
            bt_available: true,
            discovered_pcs: Vec::new(),
            selected_pc_name: String::new(),
            target_ip: String::new(),
            target_port: 54321,
            selected_band: "5 GHz".into(),
            host_decision: String::new(),
            wifi_ready: false,
            manual_ip_input: "192.168.137.1:54321".into(),
            logs: vec![
                LogEntry::info("Starting PC-to-PC Pure Wireless Mode..."),
            ],
        }
    }
}

pub fn render_pc2pc_view(
    state: &PcToPcViewState,
    on_back: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_start_scan: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_connect_pc: impl Fn(String, String, u16) + 'static + Send + Sync,
    on_manual_connect: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_proceed_to_send: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    let mut root = div()
        .flex()
        .flex_col()
        .size_full()
        .bg(theme::bg_base())
        .gap_4()
        .p_6();

    // ── Header Bar ───────────────────────────────────────────
    root = root.child(
        div()
            .flex()
            .flex_row()
            .items_center()
            .justify_between()
            .child(back_button().on_click(on_back))
            .child(
                div()
                    .text_size(px(16.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(theme::text_primary())
                    .child("💻 ShareDash PC-to-PC Wireless Mode"),
            )
            .child(
                div()
                    .px_3()
                    .py_1()
                    .rounded(theme::corner_sm())
                    .bg(if state.wifi_ready {
                        theme::bg_active()
                    } else {
                        theme::bg_elevated()
                    })
                    .text_size(px(12.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(if state.wifi_ready {
                        theme::accent_green()
                    } else {
                        theme::accent_cyan()
                    })
                    .child(format!("Phase {}/5", state.current_phase)),
            ),
    );

    // ── Phase 1: Local Radios Verification ─────────────────────
    let p1_card = div()
        .w_full()
        .p_4()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(if state.current_phase == 1 {
            theme::accent_cyan()
        } else {
            theme::border_card()
        })
        .child(phase_header(1, "Local Wi-Fi & Bluetooth Radio Verification"))
        .child(tree_item(
            "Local Wi-Fi",
            &format!(
                "{} (PHY: {} Mbps) [{}]",
                state.pc_wifi_standard,
                state.pc_wifi_phy,
                if state.pc_wifi_enabled { "ACTIVE" } else { "OFF" }
            ),
            false,
        ))
        .child(tree_item(
            "Bluetooth Radio",
            if state.bt_available { "Ready (BLE 4.0+ Active)" } else { "Unavailable / Limited" },
            true,
        ));
    root = root.child(p1_card);

    // ── Phase 2: Bluetooth LE Radar & Peer Discovery ───────────
    if state.current_phase >= 2 {
        let on_connect_arc = std::sync::Arc::new(on_connect_pc);
        let mut p2_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(if state.current_phase == 2 {
                theme::accent_cyan()
            } else {
                theme::border_card()
            })
            .child(phase_header(2, "Bluetooth LE Discovery & Radar Scan"));

        p2_card = p2_card.child(
            div()
                .flex()
                .flex_row()
                .justify_between()
                .items_center()
                .py_2()
                .child(
                    div()
                        .text_size(px(13.0))
                        .text_color(theme::text_secondary())
                        .child("Scanning for nearby ShareDash PCs over Bluetooth & Wi-Fi..."),
                )
                .child(
                    action_button("btn-refresh-scan", if state.is_scanning { "Scanning..." } else { "🔄 Scan Again" }, false)
                        .on_click(on_start_scan),
                ),
        );

        if state.discovered_pcs.is_empty() {
            p2_card = p2_card.child(
                div()
                    .p_3()
                    .rounded(theme::corner_sm())
                    .bg(theme::bg_elevated())
                    .child(
                        div()
                            .text_size(px(12.0))
                            .text_color(theme::text_secondary())
                            .child("Listening for nearby ShareDash PCs... Make sure ShareDash is open in PC-to-PC mode on the other computer."),
                    ),
            );
        } else {
            p2_card = p2_card.child(
                div()
                    .flex()
                    .flex_col()
                    .gap_2()
                    .children(state.discovered_pcs.iter().map(|dev| {
                        let dev_name = dev.name.clone();
                        let dev_addr = dev.address.clone();
                        let connect_cb = on_connect_arc.clone();
                        let dev_name_click = dev_name.clone();
                        let dev_addr_click = dev_addr.clone();

                        div()
                            .flex()
                            .flex_row()
                            .justify_between()
                            .items_center()
                            .p_3()
                            .rounded(theme::corner_sm())
                            .bg(theme::bg_elevated())
                            .border_1()
                            .border_color(theme::border_default())
                            .child(
                                div()
                                    .flex()
                                    .flex_col()
                                    .child(
                                        div()
                                            .text_size(px(14.0))
                                            .font_weight(FontWeight::BOLD)
                                            .text_color(theme::text_primary())
                                            .child(format!("💻 {}", dev_name)),
                                    )
                                    .child(
                                        div()
                                            .text_size(px(11.0))
                                            .text_color(theme::text_secondary())
                                            .child(format!("{} · RSSI: {} dBm · IP: {}", dev.transport, dev.rssi, dev_addr)),
                                    ),
                            )
                            .child(
                                action_button(&format!("btn-connect-{}", dev_name), "Connect", true)
                                    .on_click(move |_, _, _| {
                                        connect_cb(dev_name_click.clone(), dev_addr_click.clone(), 54321);
                                    }),
                            )
                    })),
            );
        }

        // Direct IP Connect Bar
        p2_card = p2_card.child(
            div()
                .pt_3()
                .flex()
                .flex_row()
                .gap_3()
                .items_center()
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .child("Or connect by IP:"),
                )
                .child(
                    action_button("btn-manual-ip", "Connect Direct IP", false)
                        .on_click(on_manual_connect),
                ),
        );

        root = root.child(p2_card);
    }

    // ── Phase 3 & 4: Band Selection & Hotspot Handshake ────────
    if state.current_phase >= 3 {
        let p3_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(if state.current_phase == 3 || state.current_phase == 4 {
                theme::accent_cyan()
            } else {
                theme::border_card()
            })
            .child(phase_header(3, "Optimal Band Selection & Hotspot Provisioning"))
            .child(status_ok(&format!(
                "Connected to Remote PC: {}",
                state.selected_pc_name
            )))
            .child(status_ok(&format!(
                "Fastest Supported Band: {} Hotspot ({})",
                state.selected_band, state.host_decision
            )));
        root = root.child(p3_card);
    }

    // ── Phase 5: Wireless Channel Ready ────────────────────────
    if state.current_phase >= 5 {
        let mut p5_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(theme::accent_green())
            .child(phase_header(5, &format!("Ultra-Fast {} Wi-Fi Transfer Channel Ready", state.selected_band)));

        if state.wifi_ready {
            p5_card = p5_card
                .child(status_ok(&format!(
                    "🔒 Direct Wi-Fi Channel READY (AES-256-GCM Line Speed) → {}:{}",
                    state.target_ip, state.target_port
                )))
                .child(
                    div()
                        .pt_3()
                        .child(
                            action_button("btn-pc2pc-send", "🚀 Select Files / Folders to Send", true)
                                .on_click(on_proceed_to_send),
                        ),
                );
        } else {
            p5_card = p5_card.child(status_spin("Completing 3-way pairing handshake..."));
        }
        root = root.child(p5_card);
    }

    // ── Activity Log Terminal ─────────────────────────────────
    root = root.child(
        div()
            .flex()
            .flex_col()
            .w_full()
            .p_3()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(theme::border_card())
            .child(
                div()
                    .text_size(px(13.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(theme::text_secondary())
                    .pb_2()
                    .child("Activity Log"),
            )
            .child(log_list(&state.logs)),
    );

    root
}


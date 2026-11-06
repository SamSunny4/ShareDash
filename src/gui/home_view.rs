//! ShareDash Home View — Mode Selection & Status Overview

use gpui::*;
use super::theme;
use super::widgets::*;

#[derive(Clone, Debug, Default)]
pub struct HomeStatus {
    pub device_name: String,
    pub server_port: u16,
    pub usb_connected: bool,
    pub usb_name: Option<String>,
    pub wifi_standard: String,
    pub wifi_phy_rate: u32,
    pub wifi_enabled: bool,
    pub paired_peer: Option<String>,
    pub pending_pair: Option<PendingPairData>,
}

#[derive(Clone, Debug)]
pub struct PendingPairData {
    pub initiator_name: String,
    pub initiator_ip: String,
    pub initiator_port: u16,
    pub pin_code: String,
}

pub fn render_home_view(
    status: &HomeStatus,
    on_select_wizard: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_select_pc2pc: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_select_transfer: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_select_info: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_open_downloads: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_accept_pair: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_reject_pair: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    let mut root = div()
        .flex()
        .flex_col()
        .size_full()
        .bg(theme::bg_base())
        .gap_4()
        .p_6();

    // ── Incoming Pairing Request Alert (if any) ───────────────
    if let Some(ref req) = status.pending_pair {
        root = root.child(
            div()
                .w_full()
                .p_4()
                .rounded(theme::corner_md())
                .bg(theme::bg_surface())
                .border_2()
                .border_color(theme::accent_yellow())
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_2()
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .justify_between()
                                .items_center()
                                .child(
                                    div()
                                        .text_size(px(16.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::accent_yellow())
                                        .child("🔔 Incoming Connection Request"),
                                )
                                .child(
                                    div()
                                        .px_3()
                                        .py_1()
                                        .rounded(theme::corner_sm())
                                        .bg(theme::bg_elevated())
                                        .text_size(px(14.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::accent_green())
                                        .child(format!("PIN: {}", req.pin_code)),
                                ),
                        )
                        .child(
                            div()
                                .text_size(px(13.0))
                                .text_color(theme::text_primary())
                                .child(format!(
                                    "Device \"{}\" ({}:{}) wants to pair with this PC.",
                                    req.initiator_name, req.initiator_ip, req.initiator_port
                                )),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .gap_3()
                                .pt_2()
                                .child(
                                    action_button("btn-accept-pair", "✔ Accept Connection", true)
                                        .on_click(on_accept_pair),
                                )
                                .child(
                                    action_button("btn-reject-pair", "✖ Reject", false)
                                        .on_click(on_reject_pair),
                                ),
                        ),
                ),
        );
    }

    // ── 1. Top Brand & Profile Bar (matching Android HomeScreen) ──
    let (status_text, status_color) = if let Some(ref peer) = status.paired_peer {
        (format!("Connected to {}", peer), theme::accent_green())
    } else if status.usb_connected {
        let name = status.usb_name.as_deref().unwrap_or("Fast-Path");
        (format!("USB Cable Detected: {}", name), theme::accent_cyan())
    } else {
        ("Waiting for Connection".to_string(), theme::text_muted())
    };

    root = root.child(brand_top_bar(
        &status.device_name,
        &status_text,
        status_color,
        on_open_downloads,
        on_select_info,
    ));

    let on_wizard = std::sync::Arc::new(on_select_wizard);
    let on_pc2pc = std::sync::Arc::new(on_select_pc2pc);

    let w1 = on_wizard.clone();
    let w2 = on_wizard.clone();
    let w3 = on_wizard.clone();

    let p1 = on_pc2pc.clone();
    let p2 = on_pc2pc.clone();
    let p3 = on_pc2pc.clone();

    // ── 2. Mode Selector Segmented Pill (Phone to PC vs PC to PC) ──
    root = root.child(mode_pill_selector(
        false,
        move |e, w, a| w1(e, w, a),
        move |e, w, a| p1(e, w, a),
    ));

    // ── 3. Hero Connection Hub (matching Android glowing central hub) ──
    root = root.child(hero_connection_hub(
        status.usb_connected,
        status.usb_name.as_deref(),
        status.wifi_enabled,
        status.paired_peer.as_deref(),
        move |e, w, a| w2(e, w, a),
        move |e, w, a| p2(e, w, a),
    ));

    // ── 4. Hardware Channel Status Overview Chips ─────────────
    let usb_label = if status.usb_connected {
        format!(
            "⚡ USB: Linked ({})",
            status.usb_name.as_deref().unwrap_or("Fast-Path")
        )
    } else {
        "⚡ USB: Disconnected".to_string()
    };

    let wifi_label = format!(
        "📶 Wi-Fi: {} ({} Mbps) [{}]",
        status.wifi_standard,
        status.wifi_phy_rate,
        if status.wifi_enabled { "ON" } else { "OFF" }
    );

    let paired_label = if let Some(ref peer) = status.paired_peer {
        format!("🔒 Paired: {}", peer)
    } else {
        "🔒 Not paired".to_string()
    };

    root = root.child(
        div()
            .flex()
            .flex_row()
            .gap_3()
            .w_full()
            .child(channel_badge(
                "⚡",
                "USB Fast-Path",
                &usb_label,
                status.usb_connected,
            ))
            .child(channel_badge(
                "📶",
                "Wi-Fi Radio",
                &wifi_label,
                status.wifi_enabled,
            ))
            .child(channel_badge(
                "🔒",
                "Peer Handshake",
                &paired_label,
                status.paired_peer.is_some(),
            )),
    );

    // ── 5. Quick Feature Mode Cards ───────────────────────────
    root = root.child(
        div()
            .flex()
            .flex_col()
            .gap_3()
            .w_full()
            .child(
                card_button(
                    "card-phone-wizard",
                    "📱",
                    "Auto-Connect Phone Wizard (USB Fast-Path → 5GHz Wi-Fi Direct)",
                    "Automatic multi-gigabit link setup. Auto-detects USB cable, exchanges specs, and connects to Phone 5GHz Wi-Fi Direct AP (~3+ Gbps combined line speed).",
                )
                .on_click(move |e, w, a| w3(e, w, a)),
            )
            .child(
                card_button(
                    "card-pc2pc-wizard",
                    "💻",
                    "PC-to-PC Wireless Mode (Bluetooth LE Radar → Direct Hotspot)",
                    "Direct peer-to-peer transmission between PCs without router or internet. Sweeps Bluetooth radar and handshakes to optimal 5GHz/6GHz hotspot.",
                )
                .on_click(move |e, w, a| p3(e, w, a)),
            )
            .child(
                card_button(
                    "card-transfer-direct",
                    "🚀",
                    "Direct File & Folder Sender (Multipath)",
                    "Select files or entire folder directories to stream simultaneously across USB and Wi-Fi Direct with real-time speedometers.",
                )
                .on_click(on_select_transfer),
            ),
    );

    root
}


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

    // ── Hardware Status Overview Strip ────────────────────────
    let usb_label = if status.usb_connected {
        format!(
            "⚡ USB: Connected ({})",
            status.usb_name.as_deref().unwrap_or("Fast-Path")
        )
    } else {
        "⚡ USB: Not connected".to_string()
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
                "USB Link",
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
                "Pairing",
                &paired_label,
                status.paired_peer.is_some(),
            )),
    );

    // ── Mode Selection Header ────────────────────────────────
    root = root.child(
        div()
            .pt_2()
            .child(
                div()
                    .text_size(px(18.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(theme::text_primary())
                    .child("Select Transfer Mode"),
            )
            .child(
                div()
                    .text_size(px(13.0))
                    .text_color(theme::text_secondary())
                    .child("Multipath zero-latency file transfer between Windows PC and Android / PC"),
            ),
    );

    // ── Mode Cards Grid (Options 1, 2, 3, Transfer) ───────────
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
                    "[1] Auto-Connect Wizard (PC ↔ Phone: USB → BLE → Wi-Fi)",
                    "Recommended: Automatic multi-gigabit link setup. Auto-detects USB cable, exchanges specs, and sets up 5GHz Wi-Fi aggregation (~3+ Gbps line speed).",
                )
                .on_click(on_select_wizard),
            )
            .child(
                card_button(
                    "card-pc2pc-wizard",
                    "💻",
                    "[2] PC-to-PC Wireless Mode (Bluetooth Discovery → Fast Hotspot)",
                    "Peer-to-peer wireless between two PCs without routers or internet. Bluetooth LE beacons handshake to optimal 5GHz/6GHz mobile hotspot.",
                )
                .on_click(on_select_pc2pc),
            )
            .child(
                card_button(
                    "card-transfer-direct",
                    "🚀",
                    "Direct File & Folder Sender (Multipath)",
                    "Send files or entire directory trees with live parallel chunk streaming across USB and Wi-Fi simultaneously.",
                )
                .on_click(on_select_transfer),
            )
            .child(
                card_button(
                    "card-network-info",
                    "🔧",
                    "[3] Network Interfaces & Hardware Info",
                    "Inspect active subnets, USB tethering interfaces, Wi-Fi hardware standard, PHY link rate, and local IP addresses.",
                )
                .on_click(on_select_info),
            ),
    );

    root
}


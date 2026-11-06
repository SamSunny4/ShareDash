//! ShareDash Transfer View — Live Multipath File & Folder Transmission
//!
//! Visualizes real-time aggregate speed, ETA, channel progress (USB + Wi-Fi),
//! and completion verification statistics.

use std::path::PathBuf;
use gpui::*;
use super::theme;
use super::widgets::*;

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum SendModeChoice {
    UsbOnly,
    WifiOnly,
    Multipath,
}

#[derive(Clone, Debug)]
pub struct TransferViewState {
    pub target_name: String,
    pub usb_target_ip: String,
    pub usb_target_port: u16,
    pub usb_ready: bool,
    pub wifi_target_ip: String,
    pub wifi_target_port: u16,
    pub wifi_ready: bool,
    pub selected_path: Option<PathBuf>,
    pub selected_file_name: String,
    pub selected_file_size: u64,
    pub is_directory: bool,
    pub send_mode: SendModeChoice,
    pub is_transferring: bool,
    pub live_progress: Option<crate::gui::engine::TransferProgressSnapshot>,
    pub completed_result: Option<TransferResultData>,
    pub error_message: Option<String>,
}

impl Default for TransferViewState {
    fn default() -> Self {
        Self {
            target_name: "Android Device".into(),
            usb_target_ip: "127.0.0.1".into(),
            usb_target_port: 54325,
            usb_ready: false,
            wifi_target_ip: "192.168.137.1".into(),
            wifi_target_port: 54321,
            wifi_ready: true,
            selected_path: None,
            selected_file_name: String::new(),
            selected_file_size: 0,
            is_directory: false,
            send_mode: SendModeChoice::Multipath,
            is_transferring: false,
            live_progress: None,
            completed_result: None,
            error_message: None,
        }
    }
}

pub fn render_transfer_view(
    state: &TransferViewState,
    on_back: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_browse_file: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_browse_folder: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_set_mode_usb: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_set_mode_wifi: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_set_mode_multipath: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_start_transfer: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_cancel_transfer: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_reset: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
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
                    .child("🚀 Multipath File & Folder Transfer"),
            )
            .child(
                div()
                    .px_3()
                    .py_1()
                    .rounded(theme::corner_sm())
                    .bg(theme::bg_elevated())
                    .text_size(px(12.0))
                    .text_color(theme::accent_green())
                    .child(format!("Target: {}", state.target_name)),
            ),
    );

    // ── Active Channels Summary ───────────────────────────────
    let usb_label = if state.usb_ready {
        format!("⚡ USB: Ready ({}:{})", state.usb_target_ip, state.usb_target_port)
    } else {
        "⚡ USB: Offline".to_string()
    };
    let wifi_label = if state.wifi_ready {
        format!("📶 Wi-Fi: Ready ({}:{})", state.wifi_target_ip, state.wifi_target_port)
    } else {
        "📶 Wi-Fi: Offline".to_string()
    };

    root = root.child(
        div()
            .flex()
            .flex_row()
            .gap_3()
            .child(channel_badge("⚡", "USB Channel", &usb_label, state.usb_ready))
            .child(channel_badge("📶", "Wi-Fi Channel", &wifi_label, state.wifi_ready))
            .child(channel_badge(
                "🚀",
                "Multipath",
                if state.usb_ready && state.wifi_ready {
                    "Aggregation Active"
                } else {
                    "Single Channel"
                },
                state.usb_ready && state.wifi_ready,
            )),
    );

    // ── Completed Result View ─────────────────────────────────
    if let Some(ref result) = state.completed_result {
        return root
            .child(transfer_result_box(result))
            .child(
                div()
                    .pt_3()
                    .child(action_button("btn-send-another", "Send Another File", true).on_click(on_reset)),
            );
    }

    // ── Live Transfer Progress View ───────────────────────────
    if state.is_transferring {
        if let Some(ref telem) = state.live_progress {
            let mut trans_card = div()
                .w_full()
                .p_4()
                .rounded(theme::corner_md())
                .bg(theme::bg_surface())
                .border_1()
                .border_color(theme::accent_cyan())
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .items_center()
                        .pb_2()
                        .child(
                            div()
                                .text_size(px(16.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::accent_cyan())
                                .child(format!("Sending: {}", telem.file_name)),
                        )
                        .child(
                            div()
                                .text_size(px(14.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::accent_green())
                                .child(format!(
                                    "{:.1} MB/s ({:.2} Gbps)",
                                    telem.speed_mb_s, telem.speed_gbps
                                )),
                        ),
                )
                .child(
                    div()
                        .pb_3()
                        .child(progress_bar(telem.progress_pct, 500.0)),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .pb_3()
                        .child(format!(
                            "{:.1} / {:.1} MB transferred",
                            telem.bytes_transferred as f64 / (1024.0 * 1024.0),
                            telem.total_bytes as f64 / (1024.0 * 1024.0)
                        ))
                        .child(format!("ETA: {:.1}s remaining", telem.eta_seconds)),
                );

            // Channel by channel breakdown
            trans_card = trans_card.child(
                div()
                    .flex()
                    .flex_col()
                    .gap_2()
                    .children(telem.channels.iter().map(|ch| {
                        let mb = ch.bytes_sent as f64 / (1024.0 * 1024.0);
                        div()
                            .flex()
                            .flex_row()
                            .justify_between()
                            .p_2()
                            .rounded(theme::corner_sm())
                            .bg(theme::bg_elevated())
                            .child(
                                div()
                                    .text_size(px(12.0))
                                    .text_color(theme::text_primary())
                                    .child(format!("{} {}: {:.1} MB", ch.icon, ch.name, mb)),
                            )
                            .child(
                                div()
                                    .text_size(px(12.0))
                                    .font_weight(FontWeight::BOLD)
                                    .text_color(theme::accent_cyan())
                                    .child(format!("{:.1} MB/s ({:.2} Gbps) · {} chunks", ch.speed_mb_s, ch.speed_gbps, ch.chunks_sent)),
                            )
                    })),
            );

            trans_card = trans_card.child(
                div()
                    .pt_4()
                    .child(
                        action_button("btn-cancel-transfer", "✖ Cancel Transfer", false)
                            .on_click(on_cancel_transfer),
                    ),
            );

            return root.child(trans_card);
        }
    }

    // ── File Selection Card ───────────────────────────────────
    let mut file_card = div()
        .w_full()
        .p_4()
        .rounded(theme::corner_md())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_default())
        .child(
            div()
                .text_size(px(14.0))
                .font_weight(FontWeight::BOLD)
                .text_color(theme::text_primary())
                .pb_2()
                .child("1. Select Content to Send"),
        )
        .child(
            div()
                .flex()
                .flex_row()
                .gap_3()
                .child(
                    action_button("btn-browse-file", "📄 Browse File...", true)
                        .on_click(on_browse_file),
                )
                .child(
                    action_button("btn-browse-folder", "📦 Browse Folder (Auto-Zip)...", false)
                        .on_click(on_browse_folder),
                ),
        );

    if let Some(ref path) = state.selected_path {
        let size_mb = state.selected_file_size as f64 / (1024.0 * 1024.0);
        file_card = file_card.child(
            div()
                .pt_3()
                .p_3()
                .rounded(theme::corner_sm())
                .bg(theme::bg_elevated())
                .border_1()
                .border_color(theme::accent_green())
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .child(
                            div()
                                .text_size(px(14.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::accent_green())
                                .child(format!(
                                    "Selected {}: {}",
                                    if state.is_directory { "Folder" } else { "File" },
                                    state.selected_file_name
                                )),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child(format!("Path: {}", path.to_string_lossy())),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::text_primary())
                                .child(format!("Size: {:.2} MB", size_mb)),
                        ),
                ),
        );
    }
    root = root.child(file_card);

    // ── Send Mode Selection Card ──────────────────────────────
    if state.selected_path.is_some() {
        let mode_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_md())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(theme::border_default())
            .child(
                div()
                    .text_size(px(14.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(theme::text_primary())
                    .pb_2()
                    .child("2. Select Transport Mode"),
            )
            .child(
                div()
                    .flex()
                    .flex_row()
                    .gap_3()
                    .child(
                        action_button(
                            "btn-mode-usb",
                            "⚡ USB Only",
                            state.send_mode == SendModeChoice::UsbOnly,
                        )
                        .on_click(on_set_mode_usb),
                    )
                    .child(
                        action_button(
                            "btn-mode-wifi",
                            "📶 Wi-Fi Only",
                            state.send_mode == SendModeChoice::WifiOnly,
                        )
                        .on_click(on_set_mode_wifi),
                    )
                    .child(
                        action_button(
                            "btn-mode-both",
                            "🚀 Both (Multipath Aggregation)",
                            state.send_mode == SendModeChoice::Multipath,
                        )
                        .on_click(on_set_mode_multipath),
                    ),
            );
        root = root.child(mode_card);

        // ── Action: Start Transfer ────────────────────────────────
        root = root.child(
            div()
                .pt_2()
                .child(
                    action_button("btn-start-transfer", "🚀 Send Content Now", true)
                        .on_click(on_start_transfer),
                ),
        );
    }

    if let Some(ref err) = state.error_message {
        root = root.child(status_fail(err));
    }

    root
}


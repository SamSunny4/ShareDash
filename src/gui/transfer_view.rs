//! ShareDash Transfer View — Live Multipath File & Folder Transmission
//!
//! Visualizes real-time aggregate speed, ETA, channel progress (USB + Wi-Fi),
//! and completion verification statistics.

use super::theme;
use super::widgets::*;
use gpui::*;
use std::path::PathBuf;

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
    let mut root = div().flex().flex_col().w_full().gap_5().p_6().child(
        div()
            .flex()
            .items_center()
            .justify_between()
            .gap_4()
            .child(page_heading(
                "File transfer",
                &format!("Send to {}", state.target_name),
            ))
            .child(back_button().on_click(on_back)),
    );
    let usb = if state.usb_ready {
        format!("{}:{}", state.usb_target_ip, state.usb_target_port)
    } else {
        "Not connected".into()
    };
    let wifi = if state.wifi_ready {
        format!("{}:{}", state.wifi_target_ip, state.wifi_target_port)
    } else {
        "Not connected".into()
    };
    root = root.child(
        div()
            .flex()
            .gap_3()
            .child(channel_badge("", "USB connection", &usb, state.usb_ready))
            .child(channel_badge(
                "",
                "Wi-Fi connection",
                &wifi,
                state.wifi_ready,
            )),
    );

    if let Some(result) = &state.completed_result {
        return root
            .child(transfer_result_box(result))
            .child(div().flex().child(
                action_button("btn-send-another", "Send another file", true).on_click(on_reset),
            ));
    }
    if state.is_transferring {
        if let Some(telem) = &state.live_progress {
            let completed_chunks = telem.channels.iter().map(|c| c.chunks_sent).sum::<usize>();
            root = root
                .child(
                    div()
                        .text_size(px(16.0))
                        .font_weight(FontWeight::SEMIBOLD)
                        .truncate()
                        .child(telem.file_name.clone()),
                )
                .child(speedometer_panel(
                    telem.speed_mb_s,
                    telem.speed_gbps,
                    telem.bytes_transferred,
                    telem.total_bytes,
                    telem.progress_pct,
                    telem.eta_seconds,
                    &telem.channels,
                ))
                .child(piece_map_visualizer(telem.progress_pct, completed_chunks));
        } else {
            root = root.child(panel()
                .child(section_title("Preparing your transfer"))
                .child(status_spin("Preparing the file and opening connections. Folders are packaged as ZIP archives.")));
        }
        return root.child(
            div().flex().child(
                action_button("btn-cancel-transfer", "Cancel transfer", false)
                    .on_click(on_cancel_transfer),
            ),
        );
    }

    let mut file_card = panel()
        .gap_4()
        .child(phase_header(1, "Choose what to send"))
        .child(
            div()
                .flex()
                .gap_3()
                .child(
                    action_button("btn-browse-file", "Choose file", true).on_click(on_browse_file),
                )
                .child(
                    action_button("btn-browse-folder", "Choose folder", false)
                        .on_click(on_browse_folder),
                ),
        );
    if let Some(path) = &state.selected_path {
        file_card = file_card.child(
            div()
                .flex()
                .flex_col()
                .gap_2()
                .p_4()
                .rounded(theme::corner_md())
                .bg(theme::bg_inset())
                .child(
                    div()
                        .text_size(px(15.0))
                        .font_weight(FontWeight::SEMIBOLD)
                        .truncate()
                        .child(state.selected_file_name.clone()),
                )
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .truncate()
                        .child(path.to_string_lossy().to_string()),
                )
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::accent_cyan())
                        .child(format!(
                            "{} · {}",
                            format_bytes(state.selected_file_size),
                            if state.is_directory {
                                "Folder · sent as a ZIP archive"
                            } else {
                                "File"
                            }
                        )),
                ),
        );
    } else {
        file_card = file_card.child(
            div()
                .text_size(px(13.0))
                .text_color(theme::text_secondary())
                .child("Choose a file from this computer, or send a folder as a ZIP archive."),
        );
    }
    root = root.child(file_card);
    if state.selected_path.is_some() {
        let description = match state.send_mode {
            SendModeChoice::UsbOnly => "Send through the USB connection.",
            SendModeChoice::WifiOnly => "Send through the wireless connection.",
            SendModeChoice::Multipath => "Use available USB and Wi-Fi connections together.",
        };
        root = root.child(
            panel()
                .child(phase_header(2, "Choose a connection"))
                .child(
                    div()
                        .flex()
                        .gap_3()
                        .child(
                            action_button(
                                "btn-mode-usb",
                                "USB only",
                                state.send_mode == SendModeChoice::UsbOnly,
                            )
                            .on_click(on_set_mode_usb),
                        )
                        .child(
                            action_button(
                                "btn-mode-wifi",
                                "Wi-Fi only",
                                state.send_mode == SendModeChoice::WifiOnly,
                            )
                            .on_click(on_set_mode_wifi),
                        )
                        .child(
                            action_button(
                                "btn-mode-both",
                                "USB + Wi-Fi",
                                state.send_mode == SendModeChoice::Multipath,
                            )
                            .on_click(on_set_mode_multipath),
                        ),
                )
                .child(
                    div()
                        .text_size(px(13.0))
                        .text_color(theme::text_secondary())
                        .child(description),
                ),
        );
        root = root.child(
            div()
                .flex()
                .items_center()
                .justify_between()
                .gap_4()
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_muted())
                        .child("Keep the receiving device connected until the transfer finishes."),
                )
                .child(
                    action_button("btn-start-transfer", "Start transfer  →", true)
                        .on_click(on_start_transfer),
                ),
        );
    }
    if let Some(err) = &state.error_message {
        root = root.child(
            panel()
                .border_color(theme::accent_red())
                .child(status_fail(err)),
        );
    }
    root
}

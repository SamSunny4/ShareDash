//! Reusable GPUI widget helpers for the ShareDash GUI.

use gpui::*;
use super::theme;

// ═══════════════════════════════════════════════════════════
//  PHASE HEADER — matches cli_widgets::print_phase_header
// ═══════════════════════════════════════════════════════════

pub fn phase_header(num: u8, title: &str) -> Div {
    div()
        .w_full()
        .py_2()
        .px_4()
        .mb_2()
        .bg(theme::bg_elevated())
        .rounded(theme::corner_md())
        .child(
            div()
                .flex()
                .flex_row()
                .gap_2()
                .items_center()
                .child(
                    div()
                        .flex_shrink_0()
                        .w(px(28.0))
                        .h(px(28.0))
                        .rounded_full()
                        .bg(theme::accent_cyan())
                        .flex()
                        .items_center()
                        .justify_center()
                        .text_size(px(13.0))
                        .text_color(rgb(0x000000))
                        .font_weight(FontWeight::BOLD)
                        .child(format!("{}", num)),
                )
                .child(
                    div()
                        .text_size(px(15.0))
                        .font_weight(FontWeight::BOLD)
                        .text_color(theme::text_primary())
                        .child(title.to_string()),
                ),
        )
}

// ═══════════════════════════════════════════════════════════
//  STATUS LINE — ✔/✖/⚠ prefixed
// ═══════════════════════════════════════════════════════════

pub fn status_ok(msg: &str) -> Div {
    status_line("✔", theme::accent_green(), msg)
}

pub fn status_fail(msg: &str) -> Div {
    status_line("✖", theme::accent_red(), msg)
}

pub fn status_warn(msg: &str) -> Div {
    status_line("⚠", theme::accent_yellow(), msg)
}

pub fn status_info(msg: &str) -> Div {
    status_line("ℹ", theme::accent_blue(), msg)
}

pub fn status_spin(msg: &str) -> Div {
    status_line("⣾", theme::accent_cyan(), msg)
}

fn status_line(icon: &str, color: Hsla, msg: &str) -> Div {
    div()
        .flex()
        .flex_row()
        .gap_2()
        .px_4()
        .py_1()
        .child(
            div()
                .text_size(px(14.0))
                .text_color(color)
                .child(icon.to_string()),
        )
        .child(
            div()
                .text_size(px(13.0))
                .text_color(theme::text_primary())
                .child(msg.to_string()),
        )
}

// ═══════════════════════════════════════════════════════════
//  TREE ITEM — ├─ / └─ style key : value
// ═══════════════════════════════════════════════════════════

pub fn tree_item(key: &str, value: &str, is_last: bool) -> Div {
    let connector = if is_last { "└─" } else { "├─" };
    div()
        .flex()
        .flex_row()
        .gap_1()
        .px_6()
        .py(px(1.0))
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_muted())
                .child(connector.to_string()),
        )
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .min_w(px(140.0))
                .child(format!("{}: ", key)),
        )
        .child(
            div()
                .text_size(px(12.0))
                .font_weight(FontWeight::BOLD)
                .text_color(theme::text_primary())
                .child(value.to_string()),
        )
}

// ═══════════════════════════════════════════════════════════
//  PROGRESS BAR — horizontal bar with percentage
// ═══════════════════════════════════════════════════════════

pub fn progress_bar(pct: f64, width_px: f32) -> Div {
    let filled_w = (pct.clamp(0.0, 1.0) * width_px as f64) as f32;
    let color = if pct >= 1.0 {
        theme::accent_green()
    } else {
        theme::accent_cyan()
    };

    div()
        .flex()
        .flex_row()
        .gap_2()
        .items_center()
        .child(
            div()
                .w(px(width_px))
                .h(px(14.0))
                .rounded(theme::corner_sm())
                .bg(theme::bg_elevated())
                .overflow_hidden()
                .child(
                    div()
                        .h_full()
                        .w(px(filled_w))
                        .bg(color)
                        .rounded(theme::corner_sm()),
                ),
        )
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .child(format!("{:.1}%", pct * 100.0)),
        )
}

// ═══════════════════════════════════════════════════════════
//  CHANNEL BADGE — ⚡ USB / 📶 Wi-Fi chip
// ═══════════════════════════════════════════════════════════

pub fn channel_badge(icon: &str, name: &str, speed: &str, active: bool) -> Div {
    let (bg, border) = if active {
        (theme::bg_active(), theme::accent_green())
    } else {
        (theme::bg_elevated(), theme::border_default())
    };

    div()
        .flex()
        .flex_row()
        .gap_2()
        .items_center()
        .px_3()
        .py_1()
        .rounded(theme::corner_md())
        .bg(bg)
        .border_1()
        .border_color(border)
        .child(
            div().text_size(px(14.0)).child(icon.to_string()),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .child(
                    div()
                        .text_size(px(12.0))
                        .font_weight(FontWeight::BOLD)
                        .text_color(if active { theme::accent_green() } else { theme::text_primary() })
                        .child(name.to_string()),
                )
                .child(
                    div()
                        .text_size(px(11.0))
                        .text_color(theme::text_secondary())
                        .child(speed.to_string()),
                ),
        )
}

// ═══════════════════════════════════════════════════════════
//  CARD BUTTON — clickable card for home menu
// ═══════════════════════════════════════════════════════════

pub fn card_button(id: &str, icon: &str, title: &str, subtitle: &str) -> Stateful<Div> {
    div()
        .id(SharedString::from(id.to_string()))
        .cursor_pointer()
        .flex()
        .flex_row()
        .gap_3()
        .items_center()
        .w_full()
        .px_4()
        .py_3()
        .rounded(theme::corner_md())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_default())
        .hover(|s| s.bg(theme::bg_hover()).border_color(theme::accent_cyan()))
        .child(
            div()
                .text_size(px(28.0))
                .flex_shrink_0()
                .child(icon.to_string()),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .gap(px(2.0))
                .child(
                    div()
                        .text_size(px(15.0))
                        .font_weight(FontWeight::BOLD)
                        .text_color(theme::text_primary())
                        .child(title.to_string()),
                )
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .child(subtitle.to_string()),
                ),
        )
}

// ═══════════════════════════════════════════════════════════
//  ANDROID-STYLED DESIGN SYSTEM COMPONENTS
// ═══════════════════════════════════════════════════════════

/// Top Brand & Status Header Bar (matching Android HomeScreen top bar)
pub fn brand_top_bar(
    device_name: &str,
    status_text: &str,
    status_color: Hsla,
    on_open_downloads: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_open_info: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    div()
        .w_full()
        .p_4()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_card())
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .items_center()
                        .gap_3()
                        .child(
                            div()
                                .w(px(42.0))
                                .h(px(42.0))
                                .rounded(theme::corner_md())
                                .bg(theme::accent_blue())
                                .flex()
                                .items_center()
                                .justify_center()
                                .text_size(px(22.0))
                                .text_color(rgb(0xffffff))
                                .child("⚡"),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .gap_2()
                                        .items_center()
                                        .child(
                                            div()
                                                .text_size(px(17.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(theme::text_primary())
                                                .child("ShareDash"),
                                        )
                                        .child(
                                            div()
                                                .px_2()
                                                .py(px(1.0))
                                                .rounded(theme::corner_sm())
                                                .bg(theme::bg_elevated())
                                                .text_size(px(10.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(theme::accent_cyan())
                                                .child("DESKTOP"),
                                        ),
                                )
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap_2()
                                        .child(
                                            div()
                                                .w(px(8.0))
                                                .h(px(8.0))
                                                .rounded_full()
                                                .bg(status_color),
                                        )
                                        .child(
                                            div()
                                                .text_size(px(12.0))
                                                .text_color(theme::text_secondary())
                                                .child(status_text.to_string()),
                                        )
                                        .child(
                                            div()
                                                .text_size(px(11.0))
                                                .text_color(theme::text_muted())
                                                .child(format!("({})", device_name)),
                                        ),
                                ),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .gap_2()
                        .child(
                            div()
                                .id("btn-top-downloads")
                                .cursor_pointer()
                                .flex()
                                .flex_row()
                                .gap_2()
                                .items_center()
                                .px_3()
                                .py_2()
                                .rounded(theme::corner_md())
                                .bg(theme::bg_elevated())
                                .border_1()
                                .border_color(theme::border_default())
                                .hover(|s| s.bg(theme::bg_hover()).border_color(theme::accent_cyan()))
                                .on_click(on_open_downloads)
                                .child(div().text_size(px(15.0)).child("📁"))
                                .child(
                                    div()
                                        .text_size(px(12.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::accent_cyan())
                                        .child("Received Files"),
                                ),
                        )
                        .child(
                            div()
                                .id("btn-top-info")
                                .cursor_pointer()
                                .flex()
                                .flex_row()
                                .gap_2()
                                .items_center()
                                .px_3()
                                .py_2()
                                .rounded(theme::corner_md())
                                .bg(theme::bg_elevated())
                                .border_1()
                                .border_color(theme::border_default())
                                .hover(|s| s.bg(theme::bg_hover()).border_color(theme::accent_cyan()))
                                .on_click(on_open_info)
                                .child(div().text_size(px(15.0)).child("⚙"))
                                .child(
                                    div()
                                        .text_size(px(12.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_primary())
                                        .child("Network Info"),
                                ),
                        ),
                ),
        )
}

/// Mode Selector Segmented Pill (matching Android HomeScreen mode pill)
pub fn mode_pill_selector(
    is_pc2pc_active: bool,
    on_select_phone: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_select_pc2pc: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    div()
        .w_full()
        .p_1()
        .rounded(theme::corner_lg())
        .bg(theme::bg_inset())
        .border_1()
        .border_color(theme::border_default())
        .child(
            div()
                .flex()
                .flex_row()
                .gap_2()
                .child(
                    div()
                        .id("pill-mode-phone")
                        .cursor_pointer()
                        .flex_1()
                        .py_2()
                        .px_4()
                        .rounded(theme::corner_md())
                        .bg(if !is_pc2pc_active { theme::bg_surface() } else { theme::bg_inset() })
                        .border_1()
                        .border_color(if !is_pc2pc_active { theme::accent_cyan() } else { theme::bg_inset() })
                        .hover(|s| s.bg(theme::bg_hover()))
                        .on_click(on_select_phone)
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_center()
                                .gap_2()
                                .child(div().text_size(px(16.0)).child("📱"))
                                .child(
                                    div()
                                        .text_size(px(13.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(if !is_pc2pc_active { theme::text_primary() } else { theme::text_muted() })
                                        .child("Phone to PC (USB Fast-Path + 5GHz Direct)"),
                                ),
                        ),
                )
                .child(
                    div()
                        .id("pill-mode-pc2pc")
                        .cursor_pointer()
                        .flex_1()
                        .py_2()
                        .px_4()
                        .rounded(theme::corner_md())
                        .bg(if is_pc2pc_active { theme::bg_surface() } else { theme::bg_inset() })
                        .border_1()
                        .border_color(if is_pc2pc_active { theme::accent_purple() } else { theme::bg_inset() })
                        .hover(|s| s.bg(theme::bg_hover()))
                        .on_click(on_select_pc2pc)
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .items_center()
                                .justify_center()
                                .gap_2()
                                .child(div().text_size(px(16.0)).child("💻"))
                                .child(
                                    div()
                                        .text_size(px(13.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(if is_pc2pc_active { theme::text_primary() } else { theme::text_muted() })
                                        .child("PC to PC (Bluetooth LE Radar + Direct Hotspot)"),
                                ),
                        ),
                ),
        )
}

/// Hero Connection Box (matching Android HomeScreen line 386)
pub fn hero_connection_hub(
    usb_connected: bool,
    usb_name: Option<&str>,
    _wifi_enabled: bool,
    paired_peer: Option<&str>,
    on_start_wizard: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_skip_wireless: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    let (hub_bg, hub_icon, hub_status, hub_desc) = if let Some(peer) = paired_peer {
        (
            theme::accent_green(),
            "✔",
            format!("Connected to {}", peer),
            "High-speed multipath channel active. Ready for instantaneous line-speed file transfer.".to_string(),
        )
    } else if usb_connected {
        let name = usb_name.unwrap_or("Android Device");
        (
            theme::accent_cyan(),
            "⚡",
            format!("USB Cable Detected: {}", name),
            "Hardware Fast-Path link ready. Click Start to auto-negotiate 5GHz Wi-Fi Direct aggregation (~3+ Gbps combined line speed).".to_string(),
        )
    } else {
        (
            theme::accent_blue(),
            "🔌",
            "Waiting for Phone / USB Cable".to_string(),
            "Connect USB cable from Android phone to PC, then enable USB Tethering for maximum line speed. Or continue wirelessly.".to_string(),
        )
    };

    div()
        .w_full()
        .p_6()
        .rounded(theme::corner_xl())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_card())
        .child(
            div()
                .flex()
                .flex_col()
                .items_center()
                .justify_center()
                .gap_4()
                .child(
                    // Glowing Center Hub Icon in Inset Well
                    div()
                        .w(px(90.0))
                        .h(px(90.0))
                        .rounded_full()
                        .bg(theme::bg_inset())
                        .border_2()
                        .border_color(theme::border_default())
                        .flex()
                        .items_center()
                        .justify_center()
                        .child(
                            div()
                                .w(px(64.0))
                                .h(px(64.0))
                                .rounded_full()
                                .bg(hub_bg)
                                .flex()
                                .items_center()
                                .justify_center()
                                .text_size(px(28.0))
                                .text_color(rgb(0xffffff))
                                .child(hub_icon),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .items_center()
                        .gap_1()
                        .child(
                            div()
                                .text_size(px(18.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(if usb_connected { theme::accent_cyan() } else { theme::text_primary() })
                                .child(hub_status),
                        )
                        .child(
                            div()
                                .text_size(px(13.0))
                                .text_color(theme::text_secondary())
                                .text_align(TextAlign::Center)
                                .max_w(px(600.0))
                                .child(hub_desc),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .gap_3()
                        .pt_2()
                        .child(
                            div()
                                .id("btn-hero-start")
                                .cursor_pointer()
                                .px_6()
                                .py_3()
                                .rounded(theme::corner_md())
                                .bg(theme::accent_blue())
                                .hover(|s| s.bg(theme::accent_cyan()))
                                .on_click(on_start_wizard)
                                .child(
                                    div()
                                        .text_size(px(14.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(rgb(0xffffff))
                                        .child("🚀 Start Auto-Connect Wizard"),
                                ),
                        )
                        .child(
                            div()
                                .id("btn-hero-wireless")
                                .cursor_pointer()
                                .px_5()
                                .py_3()
                                .rounded(theme::corner_md())
                                .bg(theme::bg_elevated())
                                .border_1()
                                .border_color(theme::border_default())
                                .hover(|s| s.bg(theme::bg_hover()).border_color(theme::accent_cyan()))
                                .on_click(on_skip_wireless)
                                .child(
                                    div()
                                        .text_size(px(14.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_primary())
                                        .child("📡 Continue Wirelessly (Skip USB)"),
                                ),
                        ),
                ),
        )
}

/// BitTorrent Piece Map / Chunk Visualizer (matching Android PieceGrid.kt)
pub fn piece_map_visualizer(
    completed_chunks: usize,
    total_chunks: usize,
    in_flight_chunks: usize,
) -> Div {
    let pct = if total_chunks > 0 {
        ((completed_chunks as f64 / total_chunks as f64) * 100.0).min(100.0)
    } else {
        0.0
    };

    let grid_blocks: usize = 48;
    let completed_blocks = if total_chunks > 0 {
        ((completed_chunks as f64 / total_chunks as f64) * grid_blocks as f64).round() as usize
    } else {
        0
    };
    let in_flight_blocks = if in_flight_chunks > 0 {
        2.min(grid_blocks.saturating_sub(completed_blocks))
    } else {
        0
    };

    div()
        .w_full()
        .p_4()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_card())
        .child(
            div()
                .flex()
                .flex_col()
                .gap_3()
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .items_center()
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_size(px(11.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_muted())
                                        .child("BITTORRENT PIECE MAP"),
                                )
                                .child(
                                    div()
                                        .text_size(px(13.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_primary())
                                        .child(format!(
                                            "{} of {} Pieces Verified ({:.1}% Complete)",
                                            completed_chunks, total_chunks, pct
                                        )),
                                ),
                        )
                        .child(
                            div()
                                .px_2()
                                .py(px(2.0))
                                .rounded(theme::corner_sm())
                                .bg(theme::bg_inset())
                                .text_size(px(11.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::accent_green())
                                .child("BLAKE3 VERIFIED"),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_wrap()
                        .gap(px(4.0))
                        .p_2()
                        .rounded(theme::corner_md())
                        .bg(theme::bg_inset())
                        .children((0..grid_blocks).map(|i| {
                            let (bg_color, border_color) = if i < completed_blocks {
                                (theme::accent_green(), theme::accent_green())
                            } else if i < completed_blocks + in_flight_blocks {
                                (theme::accent_yellow(), theme::accent_yellow())
                            } else {
                                (theme::bg_surface(), theme::border_default())
                            };

                            div()
                                .w(px(12.0))
                                .h(px(12.0))
                                .rounded(px(2.0))
                                .bg(bg_color)
                                .border_1()
                                .border_color(border_color)
                        })),
                ),
        )
}

/// Speedometer Gauges Panel (matching Android SpeedometerGauges.kt)
pub fn speedometer_panel(
    aggregate_mbps: f64,
    aggregate_gbps: f64,
    bytes_transferred: u64,
    total_bytes: u64,
    progress_pct: f64,
    eta_seconds: f64,
    channels: &[crate::gui::engine::ChannelStats],
) -> Div {
    div()
        .w_full()
        .p_5()
        .rounded(theme::corner_xl())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_card())
        .child(
            div()
                .flex()
                .flex_col()
                .gap_4()
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .items_end()
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_size(px(11.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_muted())
                                        .child("AGGREGATE BANDWIDTH"),
                                )
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_baseline()
                                        .gap_2()
                                        .child(
                                            div()
                                                .text_size(px(38.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(theme::text_primary())
                                                .child(format!("{:.1}", aggregate_mbps)),
                                        )
                                        .child(
                                            div()
                                                .text_size(px(16.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(theme::text_secondary())
                                                .child("MB/s"),
                                        ),
                                ),
                        )
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .items_end()
                                .child(
                                    div()
                                        .text_size(px(16.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::accent_green())
                                        .child(format!("~{:.2} Gbps", aggregate_gbps)),
                                )
                                .child(
                                    div()
                                        .text_size(px(12.0))
                                        .text_color(theme::text_secondary())
                                        .child(if eta_seconds > 0.0 {
                                            format!("ETA: {:.0}s remaining", eta_seconds)
                                        } else {
                                            "Finalizing...".to_string()
                                        }),
                                ),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_1()
                        .child(progress_bar(progress_pct, 600.0))
                        .child(
                            div()
                                .flex()
                                .flex_row()
                                .justify_between()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child(format!(
                                    "{:.1} / {:.1} MB transferred",
                                    bytes_transferred as f64 / (1024.0 * 1024.0),
                                    total_bytes as f64 / (1024.0 * 1024.0)
                                ))
                                .child(format!("{:.1}% Complete", progress_pct * 100.0)),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_2()
                        .pt_2()
                        .children(channels.iter().map(|ch| {
                            let mb = ch.bytes_sent as f64 / (1024.0 * 1024.0);
                            let is_usb = ch.name.contains("USB");
                            let accent = if is_usb { theme::accent_cyan() } else { theme::accent_green() };

                            div()
                                .flex()
                                .flex_row()
                                .justify_between()
                                .items_center()
                                .p_3()
                                .rounded(theme::corner_md())
                                .bg(theme::bg_inset())
                                .border_1()
                                .border_color(theme::border_default())
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap_2()
                                        .child(div().text_size(px(16.0)).child(ch.icon.clone()))
                                        .child(
                                            div()
                                                .text_size(px(13.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(theme::text_primary())
                                                .child(ch.name.clone()),
                                        )
                                        .child(
                                            div()
                                                .px_2()
                                                .py(px(1.0))
                                                .rounded(theme::corner_sm())
                                                .bg(theme::bg_surface())
                                                .text_size(px(11.0))
                                                .text_color(theme::text_secondary())
                                                .child(format!("{:.1} MB", mb)),
                                        ),
                                )
                                .child(
                                    div()
                                        .flex()
                                        .flex_row()
                                        .items_center()
                                        .gap_3()
                                        .child(
                                            div()
                                                .text_size(px(13.0))
                                                .font_weight(FontWeight::BOLD)
                                                .text_color(accent)
                                                .child(format!("{:.1} MB/s (~{:.2} Gbps)", ch.speed_mb_s, ch.speed_gbps)),
                                        )
                                        .child(
                                            div()
                                                .text_size(px(11.0))
                                                .text_color(theme::text_muted())
                                                .child(format!("{} chunks", ch.chunks_sent)),
                                        ),
                                )
                        })),
                ),
        )
}

pub fn banner(device_name: &str, port: u16) -> Div {
    div()
        .w_full()
        .px_4()
        .py_3()
        .bg(theme::bg_surface())
        .border_b_1()
        .border_color(theme::border_default())
        .child(
            div()
                .flex()
                .flex_row()
                .items_center()
                .justify_between()
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .gap_2()
                        .items_center()
                        .child(
                            div()
                                .text_size(px(18.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(theme::accent_cyan())
                                .child("ShareDash"),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_muted())
                                .child("v0.1.0"),
                        )
                        .child(
                            div()
                                .text_size(px(11.0))
                                .text_color(theme::text_secondary())
                                .child("— Multipath File Transfer Engine"),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_row()
                        .gap_3()
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child(format!("📡 {}", device_name)),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::accent_green())
                                .child(format!(":{}", port)),
                        ),
                ),
        )
}

// ═══════════════════════════════════════════════════════════
//  LOG ENTRY — timestamped message in a scrollable log
// ═══════════════════════════════════════════════════════════

#[derive(Clone, Debug)]
pub struct LogEntry {
    pub icon: String,
    pub color: Hsla,
    pub message: String,
}

impl LogEntry {
    pub fn ok(msg: impl Into<String>) -> Self {
        Self { icon: "✔".into(), color: theme::accent_green(), message: msg.into() }
    }
    pub fn fail(msg: impl Into<String>) -> Self {
        Self { icon: "✖".into(), color: theme::accent_red(), message: msg.into() }
    }
    pub fn warn(msg: impl Into<String>) -> Self {
        Self { icon: "⚠".into(), color: theme::accent_yellow(), message: msg.into() }
    }
    pub fn info(msg: impl Into<String>) -> Self {
        Self { icon: "→".into(), color: theme::accent_cyan(), message: msg.into() }
    }
    pub fn spin(msg: impl Into<String>) -> Self {
        Self { icon: "⣾".into(), color: theme::accent_cyan(), message: msg.into() }
    }
}

pub fn log_list(entries: &[LogEntry]) -> Div {
    div()
        .flex()
        .flex_col()
        .gap(px(2.0))
        .w_full()
        .children(entries.iter().map(|e| {
            div()
                .flex()
                .flex_row()
                .gap_2()
                .px_4()
                .py(px(2.0))
                .child(
                    div()
                        .text_size(px(13.0))
                        .text_color(e.color)
                        .min_w(px(16.0))
                        .child(e.icon.clone()),
                )
                .child(
                    div()
                        .text_size(px(13.0))
                        .text_color(theme::text_primary())
                        .child(e.message.clone()),
                )
        }))
}

// ═══════════════════════════════════════════════════════════
//  SECTION TITLE — for grouping
// ═══════════════════════════════════════════════════════════

pub fn section_title(title: &str) -> Div {
    div()
        .w_full()
        .px_4()
        .pt_3()
        .pb_1()
        .child(
            div()
                .text_size(px(14.0))
                .font_weight(FontWeight::BOLD)
                .text_color(theme::text_primary())
                .child(title.to_string()),
        )
}

// ═══════════════════════════════════════════════════════════
//  BUTTON — styled action button
// ═══════════════════════════════════════════════════════════

pub fn action_button(id: &str, label: &str, primary: bool) -> Stateful<Div> {
    let (bg, text_c, border_c) = if primary {
        (theme::accent_cyan(), rgb(0x000000).into(), theme::accent_cyan())
    } else {
        (theme::bg_elevated(), theme::text_primary(), theme::border_default())
    };

    div()
        .id(SharedString::from(id.to_string()))
        .cursor_pointer()
        .px_4()
        .py_2()
        .rounded(theme::corner_md())
        .bg(bg)
        .border_1()
        .border_color(border_c)
        .text_size(px(13.0))
        .font_weight(FontWeight::BOLD)
        .text_color(text_c)
        .hover(|s| s.bg(theme::bg_hover()))
        .child(label.to_string())
}

// ═══════════════════════════════════════════════════════════
//  BACK BUTTON — navigation back
// ═══════════════════════════════════════════════════════════

pub fn back_button() -> Stateful<Div> {
    div()
        .id("btn-back")
        .cursor_pointer()
        .flex()
        .flex_row()
        .gap_1()
        .items_center()
        .px_3()
        .py_1()
        .rounded(theme::corner_sm())
        .text_size(px(12.0))
        .text_color(theme::text_secondary())
        .hover(|s| s.text_color(theme::text_primary()).bg(theme::bg_hover()))
        .child("← Back")
}

// ═══════════════════════════════════════════════════════════
//  TRANSFER RESULT BOX — matches cli_widgets::print_transfer_result
// ═══════════════════════════════════════════════════════════

#[derive(Clone, Debug)]
pub struct TransferResultData {
    pub file_name: String,
    pub size_mb: f64,
    pub time_secs: f64,
    pub avg_speed_mb_s: f64,
    pub avg_speed_gbps: f64,
    pub usb_speed_mb_s: Option<f64>,
    pub usb_pct: Option<f64>,
    pub wifi_speed_mb_s: Option<f64>,
    pub wifi_pct: Option<f64>,
    pub chunk_size_bytes: usize,
    pub total_chunks: usize,
    pub integrity_ok: bool,
}

pub fn transfer_result_box(r: &TransferResultData) -> Div {
    let mut rows: Vec<Div> = Vec::new();

    rows.push(result_row("File", &r.file_name));
    rows.push(result_row("Size", &format!("{:.2} MB", r.size_mb)));
    rows.push(result_row("Time", &format!("{:.2}s", r.time_secs)));
    rows.push(result_row(
        "Avg Speed",
        &format!("{:.1} MB/s ({:.2} Gbps)", r.avg_speed_mb_s, r.avg_speed_gbps),
    ));
    if let (Some(spd), Some(pct)) = (r.usb_speed_mb_s, r.usb_pct) {
        rows.push(result_row("USB Speed", &format!("{:.1} MB/s · {:.1}%", spd, pct)));
    }
    if let (Some(spd), Some(pct)) = (r.wifi_speed_mb_s, r.wifi_pct) {
        rows.push(result_row("Wi-Fi Speed", &format!("{:.1} MB/s · {:.1}%", spd, pct)));
    }
    let chunk_mb = r.chunk_size_bytes as f64 / (1024.0 * 1024.0);
    rows.push(result_row("Chunks", &format!("{:.0} MB × {} chunks", chunk_mb, r.total_chunks)));
    let integrity_str = if r.integrity_ok { "SHA-256 ✔ VERIFIED" } else { "SHA-256 ✖ FAILED" };
    rows.push(result_row("Integrity", integrity_str));

    div()
        .w_full()
        .p_4()
        .rounded(theme::corner_md())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(if r.integrity_ok { theme::accent_green() } else { theme::accent_red() })
        .child(
            div()
                .flex()
                .flex_row()
                .gap_2()
                .pb_2()
                .child(
                    div()
                        .text_size(px(15.0))
                        .font_weight(FontWeight::BOLD)
                        .text_color(theme::accent_green())
                        .child("✔ Transfer COMPLETED"),
                ),
        )
        .children(rows)
}

fn result_row(key: &str, val: &str) -> Div {
    div()
        .flex()
        .flex_row()
        .gap_2()
        .py(px(1.0))
        .child(
            div()
                .min_w(px(100.0))
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .child(format!("{}:", key)),
        )
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_primary())
                .child(val.to_string()),
        )
}


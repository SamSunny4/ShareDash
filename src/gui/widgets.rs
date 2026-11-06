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
//  BANNER — top header bar
// ═══════════════════════════════════════════════════════════

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


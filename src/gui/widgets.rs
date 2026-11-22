//! Reusable presentation components for the ShareDash desktop interface.
use super::theme;
use gpui::*;

pub fn panel() -> Div {
    div()
        .flex()
        .flex_col()
        .w_full()
        .p_5()
        .gap_3()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_default())
}
pub fn page_heading(title: &str, description: &str) -> Div {
    div()
        .flex()
        .flex_col()
        .gap_2()
        .child(
            div()
                .text_size(px(28.0))
                .font_weight(FontWeight::SEMIBOLD)
                .text_color(theme::text_primary())
                .child(title.to_owned()),
        )
        .child(
            div()
                .text_size(px(14.0))
                .text_color(theme::text_secondary())
                .child(description.to_owned()),
        )
}
pub fn section_title(title: &str) -> Div {
    div()
        .text_size(px(15.0))
        .font_weight(FontWeight::SEMIBOLD)
        .text_color(theme::text_primary())
        .child(title.to_owned())
}
pub fn status_pill(label: &str, color: Hsla) -> Div {
    div()
        .flex()
        .items_center()
        .gap_2()
        .px_3()
        .py_1()
        .rounded_full()
        .bg(theme::bg_elevated())
        .child(div().size(px(6.0)).flex_shrink_0().rounded_full().bg(color))
        .child(
            div()
                .text_size(px(12.0))
                .text_color(color)
                .child(label.to_owned()),
        )
}
pub fn phase_header(num: u8, title: &str) -> Div {
    div()
        .flex()
        .items_center()
        .gap_3()
        .mb_3()
        .child(
            div()
                .flex()
                .items_center()
                .justify_center()
                .size(px(28.0))
                .flex_shrink_0()
                .rounded(theme::corner_sm())
                .bg(theme::bg_active())
                .text_color(theme::accent_blue())
                .text_size(px(12.0))
                .font_weight(FontWeight::SEMIBOLD)
                .child(format!("{num:02}")),
        )
        .child(section_title(title))
}
/// Outline of the existing five-step connection workflow.
pub fn connection_steps(current: u8, labels: [&str; 5]) -> Div {
    div()
        .flex()
        .gap_2()
        .w_full()
        .children(labels.iter().enumerate().map(|(i, label)| {
            let step = i as u8 + 1;
            let color = if step == current {
                theme::accent_blue()
            } else if step < current {
                theme::accent_green()
            } else {
                theme::text_muted()
            };
            div()
                .flex()
                .flex_col()
                .flex_1()
                .min_w(px(0.0))
                .gap_2()
                .child(
                    div()
                        .h(px(3.0))
                        .w_full()
                        .rounded_full()
                        .bg(if step <= current {
                            color
                        } else {
                            theme::border_default()
                        }),
                )
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(color)
                        .child(format!("{step:02}  {label}")),
                )
        }))
}
pub fn status_ok(msg: &str) -> Div {
    status_line("OK", theme::accent_green(), msg)
}
pub fn status_fail(msg: &str) -> Div {
    status_line("Error", theme::accent_red(), msg)
}
pub fn status_warn(msg: &str) -> Div {
    status_line("Note", theme::accent_yellow(), msg)
}
pub fn status_info(msg: &str) -> Div {
    status_line("Info", theme::accent_blue(), msg)
}
pub fn status_spin(msg: &str) -> Div {
    status_line("Busy", theme::accent_cyan(), msg)
}
fn status_line(label: &str, color: Hsla, msg: &str) -> Div {
    div()
        .flex()
        .items_start()
        .gap_3()
        .py_2()
        .child(
            div()
                .min_w(px(40.0))
                .flex_shrink_0()
                .text_size(px(11.0))
                .font_weight(FontWeight::SEMIBOLD)
                .text_color(color)
                .child(label.to_owned()),
        )
        .child(
            div()
                .flex_1()
                .min_w(px(0.0))
                .text_size(px(13.0))
                .text_color(theme::text_secondary())
                .child(msg.to_owned()),
        )
}
pub fn tree_item(key: &str, value: &str, _is_last: bool) -> Div {
    div()
        .flex()
        .gap_4()
        .py_2()
        .child(
            div()
                .w(px(160.0))
                .flex_shrink_0()
                .text_size(px(13.0))
                .text_color(theme::text_secondary())
                .child(key.to_owned()),
        )
        .child(
            div()
                .flex_1()
                .min_w(px(0.0))
                .text_size(px(13.0))
                .text_color(theme::text_primary())
                .child(value.to_owned()),
        )
}
pub fn progress_bar(pct: f64, width_px: f32) -> Div {
    div()
        .flex()
        .items_center()
        .gap_2()
        .child(div().w(px(width_px)).child(fluid_progress_bar(pct)))
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .child(format!("{:.0}%", pct.clamp(0.0, 1.0) * 100.0)),
        )
}
pub fn fluid_progress_bar(pct: f64) -> Div {
    let pct = if pct.is_finite() {
        pct.clamp(0.0, 1.0)
    } else {
        0.0
    };
    div()
        .w_full()
        .h(px(6.0))
        .rounded_full()
        .bg(theme::bg_elevated())
        .overflow_hidden()
        .child(
            div()
                .h_full()
                .w(relative(pct as f32))
                .rounded_full()
                .bg(if pct >= 1.0 {
                    theme::accent_green()
                } else {
                    theme::accent_blue()
                }),
        )
}
pub fn channel_badge(_icon: &str, name: &str, detail: &str, active: bool) -> Div {
    div()
        .flex()
        .flex_col()
        .flex_1()
        .min_w(px(0.0))
        .gap_2()
        .p_4()
        .rounded(theme::corner_md())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_default())
        .child(
            div()
                .flex()
                .items_center()
                .gap_2()
                .child(
                    div()
                        .size(px(6.0))
                        .flex_shrink_0()
                        .rounded_full()
                        .bg(if active {
                            theme::accent_green()
                        } else {
                            theme::text_muted()
                        }),
                )
                .child(
                    div()
                        .text_size(px(12.0))
                        .font_weight(FontWeight::SEMIBOLD)
                        .text_color(theme::text_secondary())
                        .child(name.to_owned()),
                ),
        )
        .child(
            div()
                .text_size(px(13.0))
                .text_color(theme::text_primary())
                .truncate()
                .child(detail.to_owned()),
        )
}
pub fn card_button(id: &str, tag: &str, title: &str, subtitle: &str) -> Stateful<Div> {
    div()
        .id(SharedString::from(id.to_owned()))
        .tab_index(0)
        .focus(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .cursor_pointer()
        .flex()
        .flex_col()
        .flex_1()
        .min_w(px(0.0))
        .gap_3()
        .p_5()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(theme::border_default())
        .hover(|s| {
            s.bg(theme::bg_elevated())
                .border_color(theme::border_active())
        })
        .child(
            div()
                .text_size(px(11.0))
                .font_weight(FontWeight::SEMIBOLD)
                .text_color(theme::accent_blue())
                .child(tag.to_owned()),
        )
        .child(
            div()
                .text_size(px(18.0))
                .font_weight(FontWeight::SEMIBOLD)
                .text_color(theme::text_primary())
                .child(title.to_owned()),
        )
        .child(
            div()
                .text_size(px(13.0))
                .text_color(theme::text_secondary())
                .child(subtitle.to_owned()),
        )
        .child(
            div()
                .pt_2()
                .text_size(px(12.0))
                .text_color(theme::accent_blue())
                .child("Open  →"),
        )
}
pub fn action_button(id: &str, label: &str, primary: bool) -> Stateful<Div> {
    div()
        .id(SharedString::from(id.to_owned()))
        .tab_index(0)
        .focus(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .cursor_pointer()
        .flex()
        .items_center()
        .justify_center()
        .px_4()
        .py_2()
        .min_h(px(36.0))
        .rounded(theme::corner_sm())
        .border_1()
        .bg(if primary {
            theme::button_primary()
        } else {
            theme::bg_elevated()
        })
        .border_color(if primary {
            theme::button_primary()
        } else {
            theme::border_default()
        })
        .text_size(px(13.0))
        .font_weight(FontWeight::SEMIBOLD)
        .text_color(rgb(0xffffff))
        .hover(move |s| {
            s.bg(if primary {
                theme::button_hover()
            } else {
                theme::bg_hover()
            })
        })
        .child(label.to_owned())
}
pub fn back_button() -> Stateful<Div> {
    div()
        .id("btn-back")
        .tab_index(0)
        .focus(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .cursor_pointer()
        .px_3()
        .py_2()
        .rounded(theme::corner_sm())
        .text_size(px(12.0))
        .text_color(theme::text_secondary())
        .hover(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .child("← Overview")
}
pub fn nav_button(id: &str, label: &str, active: bool) -> Stateful<Div> {
    div()
        .id(SharedString::from(id.to_owned()))
        .tab_index(0)
        .focus(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .cursor_pointer()
        .px_4()
        .py_2()
        .rounded(theme::corner_sm())
        .text_size(px(13.0))
        .font_weight(FontWeight::MEDIUM)
        .bg(if active {
            theme::bg_active()
        } else {
            theme::bg_surface()
        })
        .text_color(if active {
            theme::accent_blue()
        } else {
            theme::text_secondary()
        })
        .hover(|s| s.bg(theme::bg_hover()).text_color(theme::text_primary()))
        .child(label.to_owned())
}
pub fn banner(device_name: &str, port: u16) -> Div {
    div()
        .flex()
        .items_center()
        .justify_between()
        .flex_shrink_0()
        .px_6()
        .py_4()
        .bg(theme::bg_surface())
        .child(
            div()
                .flex()
                .items_center()
                .gap_3()
                .child(
                    div()
                        .flex()
                        .items_center()
                        .justify_center()
                        .size(px(32.0))
                        .rounded(theme::corner_sm())
                        .bg(theme::button_primary())
                        .text_color(rgb(0xffffff))
                        .text_size(px(13.0))
                        .font_weight(FontWeight::BOLD)
                        .child("SD"),
                )
                .child(
                    div()
                        .text_size(px(17.0))
                        .font_weight(FontWeight::SEMIBOLD)
                        .child("ShareDash"),
                )
                .child(
                    div()
                        .text_size(px(11.0))
                        .text_color(theme::text_muted())
                        .child(env!("CARGO_PKG_VERSION")),
                ),
        )
        .child(
            div()
                .flex()
                .items_center()
                .gap_3()
                .child(
                    div()
                        .max_w(px(260.0))
                        .truncate()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .child(device_name.to_owned()),
                )
                .child(status_pill(
                    &format!("Listening on {port}"),
                    theme::accent_green(),
                )),
        )
}
/// The snapshot exposes aggregate bytes, not a verified per-piece bitmap.
pub fn piece_map_visualizer(progress: f64, acknowledged_chunks: usize) -> Div {
    let progress = if progress.is_finite() {
        progress.clamp(0.0, 1.0)
    } else {
        0.0
    };
    let filled = (progress * 40.0).floor() as usize;
    panel()
        .child(
            div()
                .flex()
                .justify_between()
                .items_center()
                .child(section_title("Transfer activity"))
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .child(format!("{acknowledged_chunks} chunks acknowledged")),
                ),
        )
        .child(div().flex().gap(px(4.0)).children((0..40).map(|i| {
            div()
                .flex_1()
                .h(px(18.0))
                .rounded(px(3.0))
                .bg(if i < filled {
                    theme::accent_cyan()
                } else {
                    theme::bg_elevated()
                })
        })))
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_muted())
                .child("Each segment represents a share of the file's byte progress."),
        )
}
pub fn speedometer_panel(
    aggregate_mbps: f64,
    _aggregate_gbps: f64,
    bytes_transferred: u64,
    total_bytes: u64,
    progress_pct: f64,
    eta_seconds: f64,
    channels: &[crate::gui::engine::ChannelStats],
) -> Div {
    panel()
        .gap_4()
        .child(
            div()
                .flex()
                .justify_between()
                .items_end()
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_1()
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child("Average transfer speed"),
                        )
                        .child(
                            div()
                                .flex()
                                .items_baseline()
                                .gap_2()
                                .child(
                                    div()
                                        .text_size(px(42.0))
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .child(format!("{aggregate_mbps:.1}")),
                                )
                                .child(
                                    div()
                                        .text_size(px(16.0))
                                        .text_color(theme::text_secondary())
                                        .child("MiB/s"),
                                ),
                        ),
                )
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .items_end()
                        .gap_2()
                        .child(
                            div()
                                .text_size(px(24.0))
                                .font_weight(FontWeight::SEMIBOLD)
                                .text_color(theme::accent_blue())
                                .child(format!("{:.1}%", progress_pct.clamp(0.0, 1.0) * 100.0)),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child(if eta_seconds > 0.0 {
                                    format!("About {:.0}s remaining", eta_seconds)
                                } else {
                                    "Calculating time remaining…".to_owned()
                                }),
                        ),
                ),
        )
        .child(fluid_progress_bar(progress_pct))
        .child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .child(format!(
                    "{} of {} transferred",
                    format_bytes(bytes_transferred),
                    format_bytes(total_bytes)
                )),
        )
        .children(channels.iter().map(|ch| {
            let color = if ch.name.contains("USB") {
                theme::accent_cyan()
            } else {
                theme::accent_green()
            };
            div()
                .flex()
                .items_center()
                .justify_between()
                .gap_3()
                .p_3()
                .rounded(theme::corner_sm())
                .bg(theme::bg_inset())
                .child(
                    div()
                        .flex()
                        .flex_col()
                        .gap_1()
                        .child(
                            div()
                                .text_size(px(13.0))
                                .font_weight(FontWeight::MEDIUM)
                                .child(ch.name.clone()),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .text_color(theme::text_secondary())
                                .child(format!(
                                    "{} · {} chunks",
                                    format_bytes(ch.bytes_sent),
                                    ch.chunks_sent
                                )),
                        ),
                )
                .child(
                    div()
                        .text_size(px(15.0))
                        .font_weight(FontWeight::SEMIBOLD)
                        .text_color(color)
                        .child(format!("{:.1} MiB/s", ch.speed_mb_s)),
                )
        }))
}
pub fn format_bytes(bytes: u64) -> String {
    if bytes >= 1024 * 1024 * 1024 {
        format!("{:.2} GiB", bytes as f64 / 1_073_741_824.0)
    } else if bytes >= 1024 * 1024 {
        format!("{:.1} MiB", bytes as f64 / 1_048_576.0)
    } else if bytes >= 1024 {
        format!("{:.1} KiB", bytes as f64 / 1024.0)
    } else {
        format!("{bytes} B")
    }
}
#[derive(Clone, Debug)]
pub struct LogEntry {
    pub icon: String,
    pub color: Hsla,
    pub message: String,
}
impl LogEntry {
    pub fn ok(msg: impl Into<String>) -> Self {
        Self {
            icon: "OK".into(),
            color: theme::accent_green(),
            message: msg.into(),
        }
    }
    pub fn fail(msg: impl Into<String>) -> Self {
        Self {
            icon: "Error".into(),
            color: theme::accent_red(),
            message: msg.into(),
        }
    }
    pub fn warn(msg: impl Into<String>) -> Self {
        Self {
            icon: "Note".into(),
            color: theme::accent_yellow(),
            message: msg.into(),
        }
    }
    pub fn info(msg: impl Into<String>) -> Self {
        Self {
            icon: "Info".into(),
            color: theme::accent_blue(),
            message: msg.into(),
        }
    }
    pub fn spin(msg: impl Into<String>) -> Self {
        Self {
            icon: "Busy".into(),
            color: theme::accent_cyan(),
            message: msg.into(),
        }
    }
}
pub fn log_list(entries: &[LogEntry]) -> Div {
    div().flex().flex_col().w_full().children(
        entries
            .iter()
            .map(|entry| status_line(&entry.icon, entry.color, &entry.message)),
    )
}
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
    let color = if r.integrity_ok {
        theme::accent_green()
    } else {
        theme::accent_red()
    };
    let mut card = panel()
        .child(status_pill(
            if r.integrity_ok {
                "Transfer complete"
            } else {
                "Transfer needs attention"
            },
            color,
        ))
        .child(
            div()
                .text_size(px(22.0))
                .font_weight(FontWeight::SEMIBOLD)
                .truncate()
                .child(r.file_name.clone()),
        )
        .child(tree_item(
            "File size",
            &format!("{:.2} MiB", r.size_mb),
            false,
        ))
        .child(tree_item(
            "Elapsed time",
            &format!("{:.2} seconds", r.time_secs),
            false,
        ))
        .child(tree_item(
            "Average speed",
            &format!("{:.1} MiB/s", r.avg_speed_mb_s),
            false,
        ));
    if let (Some(speed), Some(pct)) = (r.usb_speed_mb_s, r.usb_pct) {
        card = card.child(tree_item(
            "USB",
            &format!("{speed:.1} MiB/s · {pct:.1}% of bytes"),
            false,
        ));
    }
    if let (Some(speed), Some(pct)) = (r.wifi_speed_mb_s, r.wifi_pct) {
        card = card.child(tree_item(
            "Wi-Fi",
            &format!("{speed:.1} MiB/s · {pct:.1}% of bytes"),
            false,
        ));
    }
    card.child(tree_item(
        "Chunks",
        &format!(
            "{} · average {}",
            r.total_chunks,
            format_bytes(r.chunk_size_bytes as u64)
        ),
        false,
    ))
    .child(status_line(
        if r.integrity_ok { "OK" } else { "Error" },
        color,
        if r.integrity_ok {
            "Sender finished without reported worker errors. Chunks include CRC32 checksums."
        } else {
            "The sender reported a worker error. Check the destination before using this file."
        },
    ))
}

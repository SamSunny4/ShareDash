use gpui::{prelude::FluentBuilder, *};

use super::receive::{download_directory, ReceiveActivity, ReceiveStatus, ReceivedFile};
use super::theme;
use super::widgets::{
    action_button, format_bytes, page_heading, panel, section_title, status_pill,
};

pub(super) fn render_receive_view(
    activity: &ReceiveActivity,
    port: u16,
    open_folder: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    let featured = activity
        .files
        .iter()
        .find(|file| file.status.is_active())
        .or_else(|| activity.files.first());
    let (heading, description) = match featured.map(|file| file.status) {
        Some(ReceiveStatus::Receiving | ReceiveStatus::Saving) => (
            "Incoming file",
            "Follow your transfer as it arrives on this PC.",
        ),
        Some(ReceiveStatus::Completed) => (
            "File received",
            "Your file is ready to open from the received files folder.",
        ),
        Some(ReceiveStatus::Failed) => (
            "Transfer interrupted",
            "Try resending the file from the sending device.",
        ),
        None => (
            "Receive files",
            "Send a file to this PC from ShareDash on your phone or another computer.",
        ),
    };
    let destination = download_directory();
    let folder_name = destination
        .file_name()
        .unwrap_or(destination.as_os_str())
        .to_string_lossy();
    let folder_parent = destination
        .parent()
        .filter(|path| !path.as_os_str().is_empty());

    div()
        .flex()
        .flex_col()
        .gap_5()
        .p_6()
        .child(page_heading(heading, description))
        .child(
            div()
                .flex()
                .items_start()
                .gap_4()
                .child(
                    div()
                        .flex_1()
                        .min_w(px(0.))
                        .child(featured_card(featured, port)),
                )
                .child(
                    panel()
                        .w(px(248.))
                        .flex_shrink_0()
                        .gap_4()
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .gap_2()
                                .child(section_title("Save location"))
                                .child(
                                    div()
                                        .text_size(px(16.))
                                        .font_weight(FontWeight::SEMIBOLD)
                                        .child(folder_name.into_owned()),
                                )
                                .when_some(folder_parent, |el, parent| {
                                    el.child(
                                        div()
                                            .text_size(px(12.))
                                            .text_color(theme::text_muted())
                                            .child(parent.display().to_string()),
                                    )
                                }),
                        )
                        .child(
                            action_button("receive-open-folder", "Open received files", true)
                                .on_click(open_folder),
                        ),
                ),
        )
        .when(activity.files.len() > 1, |el| {
            el.child(
                panel()
                    .gap_2()
                    .child(section_title("Other files"))
                    .children(
                        activity
                            .files
                            .iter()
                            .filter(|file| featured.is_none_or(|featured| file.id != featured.id))
                            .map(file_row),
                    ),
            )
        })
}

fn featured_card(file: Option<&ReceivedFile>, port: u16) -> Div {
    let (label, color, symbol) = match file.map(|file| file.status) {
        Some(ReceiveStatus::Completed) => ("Saved", theme::accent_green(), "✓"),
        Some(ReceiveStatus::Failed) => ("Could not save", theme::accent_red(), "!"),
        Some(ReceiveStatus::Saving) => ("Saving", theme::accent_cyan(), "↓"),
        Some(ReceiveStatus::Receiving) => ("Receiving", theme::accent_cyan(), "↓"),
        None => ("Listening", theme::accent_green(), "↓"),
    };
    let title = file
        .map(|file| file.name.as_str())
        .unwrap_or("Waiting for a file");
    let detail = file.map_or_else(|| format!("Port {port}"), file_detail);

    div()
        .flex()
        .flex_col()
        .gap_5()
        .p_6()
        .rounded(theme::corner_xl())
        .border_1()
        .border_color(rgb(0x335057))
        .bg(linear_gradient(
            125.,
            linear_color_stop(rgb(0x1c3339), 0.),
            linear_color_stop(rgb(0x151c27), 1.),
        ))
        .child(
            div()
                .flex()
                .justify_between()
                .items_start()
                .child(
                    div()
                        .flex()
                        .items_center()
                        .justify_center()
                        .size(px(66.))
                        .rounded(theme::corner_lg())
                        .border_1()
                        .border_color(rgb(0x426069))
                        .bg(rgb(0x243f45))
                        .text_color(color)
                        .text_size(px(36.))
                        .font_weight(FontWeight::SEMIBOLD)
                        .child(symbol),
                )
                .child(status_pill(label, color)),
        )
        .child(
            div()
                .flex()
                .flex_col()
                .gap_1()
                .min_w(px(0.))
                .child(
                    div()
                        .text_size(px(25.))
                        .line_height(px(34.))
                        .font_weight(FontWeight::SEMIBOLD)
                        .truncate()
                        .child(title.to_owned()),
                )
                .child(
                    div()
                        .text_size(px(13.))
                        .text_color(theme::text_secondary())
                        .child(detail),
                ),
        )
        .when_some(file, |el, file| el.child(progress_details(file, color)))
}

fn file_detail(file: &ReceivedFile) -> String {
    match (file.usb, file.wifi) {
        (true, true) => "USB + Wi-Fi".into(),
        (true, false) => "USB".into(),
        (false, true) => "Wi-Fi / LAN".into(),
        (false, false) => "Incoming transfer".into(),
    }
}

fn progress_details(file: &ReceivedFile, color: Hsla) -> Div {
    let progress = file.progress();
    let bytes = match (file.status, file.total_bytes) {
        (ReceiveStatus::Completed, _) => format!("{} saved", format_bytes(file.received_bytes)),
        (_, Some(total)) => format!(
            "{} of {}",
            format_bytes(file.received_bytes),
            format_bytes(total)
        ),
        _ => format!("{} received", format_bytes(file.received_bytes)),
    };
    let speed = (file.status == ReceiveStatus::Receiving && file.bytes_per_second > 0.)
        .then(|| format!("{}/s", format_bytes(file.bytes_per_second as u64)));

    div()
        .flex()
        .flex_col()
        .gap_2()
        .when_some(progress, |el, progress| {
            el.child(
                div()
                    .w_full()
                    .h(px(8.))
                    .overflow_hidden()
                    .rounded_full()
                    .bg(rgb(0x0f181f))
                    .child(
                        div()
                            .h_full()
                            .w(relative(progress))
                            .rounded_full()
                            .bg(color),
                    ),
            )
        })
        .child(
            div()
                .flex()
                .items_center()
                .justify_between()
                .gap_3()
                .text_size(px(13.))
                .child(div().text_color(theme::text_secondary()).child(bytes))
                .child(
                    div()
                        .flex()
                        .items_center()
                        .gap_3()
                        .when_some(speed, |el, speed| {
                            el.child(div().text_color(theme::text_secondary()).child(speed))
                        })
                        .when_some(progress, |el, progress| {
                            el.child(
                                div()
                                    .font_weight(FontWeight::SEMIBOLD)
                                    .text_color(color)
                                    .child(format!("{:.0}%", progress * 100.)),
                            )
                        }),
                ),
        )
}

fn file_row(file: &ReceivedFile) -> Div {
    let (label, color) = match file.status {
        ReceiveStatus::Receiving => ("Receiving", theme::accent_cyan()),
        ReceiveStatus::Saving => ("Saving", theme::accent_cyan()),
        ReceiveStatus::Completed => ("Saved", theme::accent_green()),
        ReceiveStatus::Failed => ("Could not save", theme::accent_red()),
    };
    div()
        .flex()
        .items_center()
        .justify_between()
        .gap_4()
        .px_3()
        .py_2()
        .rounded(theme::corner_sm())
        .bg(theme::bg_inset())
        .child(
            div()
                .flex_1()
                .min_w(px(0.))
                .truncate()
                .text_size(px(13.))
                .child(file.name.clone()),
        )
        .child(
            div()
                .text_size(px(12.))
                .text_color(theme::text_muted())
                .child(format_bytes(file.received_bytes)),
        )
        .child(status_pill(label, color))
}

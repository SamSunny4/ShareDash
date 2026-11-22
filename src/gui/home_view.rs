//! Desktop overview: connection status and entry points for the existing flows.
use super::theme;
use super::widgets::*;
use gpui::*;

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
    let mut root = div().flex().flex_col().w_full().gap_6().p_6();
    root = root.child(
        div()
            .flex()
            .items_center()
            .justify_between()
            .gap_4()
            .child(page_heading(
                "Overview",
                "Connect a device and move files over USB, Wi-Fi, or both.",
            ))
            .child(
                action_button("btn-top-downloads", "Received files", false)
                    .on_click(on_open_downloads),
            ),
    );
    if let Some(req) = &status.pending_pair {
        root = root.child(
            panel()
                .border_color(theme::accent_yellow())
                .child(
                    div()
                        .flex()
                        .items_center()
                        .justify_between()
                        .child(section_title("Connection request"))
                        .child(status_pill(
                            &format!("PIN {}", req.pin_code),
                            theme::accent_yellow(),
                        )),
                )
                .child(div().text_size(px(14.0)).child(format!(
                    "{} wants to connect to this PC.",
                    req.initiator_name
                )))
                .child(
                    div()
                        .text_size(px(12.0))
                        .text_color(theme::text_secondary())
                        .child(format!(
                            "{}:{} · Confirm that you recognize this device.",
                            req.initiator_ip, req.initiator_port
                        )),
                )
                .child(
                    div()
                        .flex()
                        .gap_3()
                        .child(
                            action_button("btn-accept-pair", "Accept connection", true)
                                .on_click(on_accept_pair),
                        )
                        .child(
                            action_button("btn-reject-pair", "Decline", false)
                                .on_click(on_reject_pair),
                        ),
                ),
        );
    }
    let usb = if status.usb_connected {
        status
            .usb_name
            .clone()
            .unwrap_or_else(|| "Cable connected".into())
    } else {
        "No device connected".into()
    };
    let wifi = if status.wifi_enabled {
        format!(
            "{} · {} Mbps link",
            status.wifi_standard, status.wifi_phy_rate
        )
    } else {
        "Wi-Fi is turned off".into()
    };
    root = root.child(
        div()
            .flex()
            .gap_3()
            .child(channel_badge(
                "",
                "USB connection",
                &usb,
                status.usb_connected,
            ))
            .child(channel_badge(
                "",
                "Wi-Fi adapter",
                &wifi,
                status.wifi_enabled,
            ))
            .child(channel_badge(
                "",
                "Paired device",
                status.paired_peer.as_deref().unwrap_or("Ready to connect"),
                status.paired_peer.is_some(),
            )),
    );
    root = root.child(panel().p_6().gap_4()
        .child(div().text_size(px(11.0)).font_weight(FontWeight::SEMIBOLD)
            .text_color(theme::accent_blue()).child("PHONE TO COMPUTER"))
        .child(div().text_size(px(25.0)).font_weight(FontWeight::SEMIBOLD).child("Start with your phone."))
        .child(div().max_w(px(620.0)).text_size(px(14.0)).text_color(theme::text_secondary())
            .child("Open ShareDash on Android, then connect a USB cable or use a wireless connection. The setup assistant checks the available connections for you."))
        .child(div().flex().items_center().gap_4()
            .child(action_button("card-phone-wizard", "Connect phone  →", true).on_click(on_select_wizard))
            .child(div().text_size(px(12.0)).text_color(theme::text_muted()).child("No cloud account required"))));
    root = root.child(div().flex().gap_4()
        .child(card_button("card-pc2pc-wizard", "COMPUTER TO COMPUTER", "Connect another PC",
            "Discover a nearby ShareDash computer and establish a wireless connection.").on_click(on_select_pc2pc))
        .child(card_button("card-transfer-direct", "FILE TRANSFER", "Send files or a folder",
            "Use an available connection. Choose a file, or send a folder as a ZIP archive.").on_click(on_select_transfer)));
    root.child(
        div()
            .flex()
            .items_center()
            .justify_between()
            .gap_4()
            .child(
                div()
                    .text_size(px(12.0))
                    .text_color(theme::text_muted())
                    .child("Keep ShareDash open on both devices while transferring."),
            )
            .child(
                action_button("btn-top-info", "Device & network details", false)
                    .on_click(on_select_info),
            ),
    )
}

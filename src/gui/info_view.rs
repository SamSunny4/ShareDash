//! ShareDash Info View — Network Interfaces, Radios & Hardware Inspection

use gpui::*;
use super::theme;
use super::widgets::*;

#[derive(Clone, Debug, Default)]
pub struct InterfaceEntry {
    pub name: String,
    pub ip: String,
    pub label: String,
    pub is_usb: bool,
}

#[derive(Clone, Debug, Default)]
pub struct InfoViewState {
    pub interfaces: Vec<InterfaceEntry>,
    pub usb_tether_device: Option<String>,
    pub usb_adb_device: Option<String>,
    pub wifi_standard: String,
    pub wifi_phy: u32,
    pub wifi_bands: Vec<String>,
    pub server_port: u16,
    pub download_dir: String,
}

pub fn render_info_view(
    state: &InfoViewState,
    on_back: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_open_wifi_settings: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_open_hotspot_settings: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_open_downloads: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
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
                    .child("🔧 Network Interfaces & Hardware"),
            )
            .child(
                div()
                    .px_3()
                    .py_1()
                    .rounded(theme::corner_sm())
                    .bg(theme::bg_elevated())
                    .text_size(px(12.0))
                    .text_color(theme::accent_cyan())
                    .child(format!("Port :{}", state.server_port)),
            ),
    );

    // ── Quick Action Shortcuts ────────────────────────────────
    root = root.child(
        div()
            .flex()
            .flex_row()
            .gap_3()
            .child(
                action_button("btn-open-wifi", "📶 Open Windows Wi-Fi Settings", false)
                    .on_click(on_open_wifi_settings),
            )
            .child(
                action_button("btn-open-hotspot", "💻 Open Hotspot Settings", false)
                    .on_click(on_open_hotspot_settings),
            )
            .child(
                action_button("btn-open-folder", "📁 Open Received Folder", true)
                    .on_click(on_open_downloads),
            ),
    );

    // ── Hardware Radios Summary Card ──────────────────────────
    let hw_card = div()
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
                .child("Local Hardware Radios"),
        )
        .child(tree_item(
            "Wi-Fi Generation",
            &format!("{} (PHY: {} Mbps)", state.wifi_standard, state.wifi_phy),
            false,
        ))
        .child(tree_item(
            "Supported Bands",
            &state.wifi_bands.join(", "),
            false,
        ))
        .child(tree_item(
            "USB ADB Fast-Path",
            state.usb_adb_device.as_deref().unwrap_or("Not active"),
            false,
        ))
        .child(tree_item(
            "USB Tethering (RNDIS)",
            state.usb_tether_device.as_deref().unwrap_or("Not active"),
            true,
        ));
    root = root.child(hw_card);

    // ── Network Interfaces Table Card ─────────────────────────
    let mut iface_card = div()
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
                .child("Network Interfaces & Subnets"),
        );

    if state.interfaces.is_empty() {
        iface_card = iface_card.child(
            div()
                .text_size(px(12.0))
                .text_color(theme::text_secondary())
                .child("Scanning interfaces..."),
        );
    } else {
        iface_card = iface_card.child(
            div()
                .flex()
                .flex_col()
                .gap_2()
                .children(state.interfaces.iter().map(|iface| {
                    div()
                        .flex()
                        .flex_row()
                        .justify_between()
                        .items_center()
                        .p_2()
                        .rounded(theme::corner_sm())
                        .bg(theme::bg_elevated())
                        .child(
                            div()
                                .flex()
                                .flex_col()
                                .child(
                                    div()
                                        .text_size(px(13.0))
                                        .font_weight(FontWeight::BOLD)
                                        .text_color(theme::text_primary())
                                        .child(iface.name.clone()),
                                )
                                .child(
                                    div()
                                        .text_size(px(12.0))
                                        .text_color(theme::accent_cyan())
                                        .child(format!("IP: {}", iface.ip)),
                                ),
                        )
                        .child(
                            div()
                                .text_size(px(12.0))
                                .font_weight(FontWeight::BOLD)
                                .text_color(if iface.is_usb {
                                    theme::accent_green()
                                } else {
                                    theme::text_secondary()
                                })
                                .child(iface.label.clone()),
                        )
                })),
        );
    }
    root = root.child(iface_card);

    root
}


//! ShareDash Auto-Connect Wizard View (PC ↔ Phone)
//!
//! Visualizes Phases 1–5: USB check, capability exchange, hotspot setup, handshake, and ready state.

use super::theme;
use super::widgets::*;
use gpui::*;

#[derive(Clone, Debug)]
pub struct WizardViewState {
    pub current_phase: u8,
    pub is_running: bool,
    pub waiting_for_usb: bool,
    pub usb_connected: bool,
    pub usb_name: Option<String>,
    pub is_rndis: bool,
    pub pc_wifi_standard: String,
    pub pc_wifi_phy: u32,
    pub pc_wifi_enabled: bool,
    pub phone_wifi_standard: String,
    pub phone_wifi_phy: u32,
    pub host_choice: String,
    pub host_reason: String,
    pub hotspot_ssid: String,
    pub hotspot_band: String,
    pub wifi_ip: Option<String>,
    pub usb_ip: String,
    pub usb_port: u16,
    pub wifi_ready: bool,
    pub usb_ready: bool,
    pub logs: Vec<LogEntry>,
}

impl Default for WizardViewState {
    fn default() -> Self {
        Self {
            current_phase: 1,
            is_running: false,
            waiting_for_usb: false,
            usb_connected: false,
            usb_name: None,
            is_rndis: false,
            pc_wifi_standard: "Wi-Fi 6 (802.11ax)".into(),
            pc_wifi_phy: 1200,
            pc_wifi_enabled: true,
            phone_wifi_standard: "Wi-Fi 6 (802.11ax)".into(),
            phone_wifi_phy: 1200,
            host_choice: "Phone".into(),
            host_reason: "Phone hotspot preferred when available".into(),
            hotspot_ssid: String::new(),
            hotspot_band: "5 GHz".into(),
            wifi_ip: None,
            usb_ip: "127.0.0.1".into(),
            usb_port: 54325,
            wifi_ready: false,
            usb_ready: false,
            logs: vec![LogEntry::info("Initializing Auto-Connect Phone Wizard...")],
        }
    }
}

pub fn render_wizard_view(
    state: &WizardViewState,
    on_back: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_skip_usb: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_enable_wifi: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_continue_usb_only: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
    on_proceed_to_send: impl Fn(&ClickEvent, &mut Window, &mut App) + 'static,
) -> Div {
    let mut root = div()
        .flex()
        .flex_col()
        .w_full()
        .bg(theme::bg_base())
        .gap_5()
        .p_6();

    root = root.child(
        div()
            .flex()
            .items_center()
            .justify_between()
            .gap_4()
            .child(page_heading(
                "Connect your phone",
                "A guided setup for USB and wireless connections.",
            ))
            .child(back_button().on_click(on_back)),
    );
    root = root.child(connection_steps(
        state.current_phase,
        ["USB", "Capabilities", "Host", "Wi-Fi", "Ready"],
    ));
    // ── Phase 1: USB Connection Priority Card ─────────────────
    let mut p1_card = div()
        .w_full()
        .p_4()
        .rounded(theme::corner_lg())
        .bg(theme::bg_surface())
        .border_1()
        .border_color(if state.current_phase == 1 {
            theme::accent_cyan()
        } else {
            theme::border_card()
        })
        .child(phase_header(1, "Check the USB connection"));

    if state.usb_connected {
        p1_card = p1_card.child(status_ok(&format!(
            "USB Connected: {} ({})",
            state.usb_name.as_deref().unwrap_or("Android Device"),
            if state.is_rndis {
                "USB Tethering RNDIS"
            } else {
                "USB ADB Fast-Path"
            }
        )));
    } else if state.waiting_for_usb {
        p1_card = p1_card
            .child(status_spin("Plug in your phone and enable USB tethering."))
            .child(
                div()
                    .px_4()
                    .pt_2()
                    .flex()
                    .flex_row()
                    .gap_3()
                    .child(
                        action_button("btn-skip-usb", "Use wireless connection", false)
                            .on_click(on_skip_usb),
                    ),
            );
    } else {
        p1_card = p1_card.child(status_warn("Continuing in Wireless Mode without USB."));
    }
    if state.current_phase == 1 { root = root.child(p1_card); }

    // ── Phase 2: Hardware Capabilities ─────────────────────────
    if state.current_phase == 2 {
        let mut p2_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(if state.current_phase == 2 {
                theme::accent_cyan()
            } else {
                theme::border_card()
            })
            .child(phase_header(2, "Compare device capabilities"));

        p2_card = p2_card
            .child(tree_item(
                "PC Wi-Fi",
                &format!(
                    "{} (PHY: {} Mbps) [{}]",
                    state.pc_wifi_standard,
                    state.pc_wifi_phy,
                    if state.pc_wifi_enabled { "ON" } else { "OFF" }
                ),
                false,
            ))
            .child(tree_item(
                "Phone Wi-Fi",
                &format!(
                    "{} (PHY: {} Mbps)",
                    state.phone_wifi_standard, state.phone_wifi_phy
                ),
                true,
            ));

        if !state.pc_wifi_enabled {
            p2_card = p2_card
                .child(
                    div()
                        .px_4()
                        .pt_2()
                        .text_size(px(12.0))
                        .text_color(theme::accent_yellow())
                        .child("Wi-Fi is turned off. Enable it to add a wireless connection, or continue with USB."),
                )
                .child(
                    div()
                        .px_4()
                        .pt_2()
                        .flex()
                        .flex_row()
                        .gap_3()
                        .child(
                            action_button("btn-enable-wifi", "Open Wi-Fi settings", true)
                                .on_click(on_enable_wifi),
                        )
                        .child(
                            action_button("btn-usb-only", "Use USB only", false)
                                .on_click(on_continue_usb_only),
                        ),
                );
        }
        root = root.child(p2_card);
    }

    // ── Phase 3 & 4: Hotspot Provisioning ─────────────────────
    if (3..5).contains(&state.current_phase) {
        let p3_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(if state.current_phase == 3 || state.current_phase == 4 {
                theme::accent_cyan()
            } else {
                theme::border_card()
            })
            .child(phase_header(3, "Set up the wireless connection"))
            .child(status_ok(&format!(
                "Host: {} · {}",
                state.host_choice, state.host_reason
            )))
            .child(if !state.hotspot_ssid.is_empty() {
                status_ok(&format!(
                    "Hotspot Active: SSID='{}', Band={}",
                    state.hotspot_ssid, state.hotspot_band
                ))
            } else {
                status_spin("Check your phone to finish Wi-Fi setup…")
            });
        root = root.child(p3_card);
    }

    // ── Phase 5: Handshake & Channels Ready ───────────────────
    if state.current_phase >= 5 {
        let mut p5_card = div()
            .w_full()
            .p_4()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(if state.wifi_ready || state.usb_ready {
                theme::accent_green()
            } else {
                theme::accent_cyan()
            })
            .child(phase_header(5, "Connection ready"));

        if state.usb_ready {
            p5_card = p5_card.child(status_ok(&format!(
                "USB connection ready → {}:{}",
                state.usb_ip, state.usb_port
            )));
        }
        if state.wifi_ready {
            p5_card = p5_card.child(status_ok(&format!(
                "Wi-Fi connection ready → {}",
                state.wifi_ip.as_deref().unwrap_or("Connected")
            )));
        }

        if state.usb_ready || state.wifi_ready {
            p5_card = p5_card.child(
                div().pt_3().flex().flex_row().gap_3().child(
                    action_button("btn-send-files", "Choose files to send", true)
                        .on_click(on_proceed_to_send),
                ),
            );
        }
        root = root.child(p5_card);
    }

    // ── Connection activity Terminal ─────────────────────────────────
    root = root.child(
        div()
            .flex()
            .flex_col()
            .w_full()
            .p_3()
            .rounded(theme::corner_lg())
            .bg(theme::bg_surface())
            .border_1()
            .border_color(theme::border_card())
            .child(
                div()
                    .text_size(px(13.0))
                    .font_weight(FontWeight::BOLD)
                    .text_color(theme::text_secondary())
                    .pb_2()
                    .child("Connection activity"),
            )
            .child(log_list(&state.logs[state.logs.len().saturating_sub(1)..])),
    );

    root
}

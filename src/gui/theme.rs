//! ShareDash GUI Theme — Neomorphic Cyberpunk palette exactly matching the Android App design.

use gpui::*;

// ── Backgrounds & Surfaces (matching Android DarkShareDashColors) ──
pub fn bg_base() -> Hsla      { rgb(0x080a0f).into() } // NeoBg
pub fn bg_surface() -> Hsla   { rgb(0x111318).into() } // NeoCard
pub fn bg_inset() -> Hsla     { rgb(0x0a0c10).into() } // NeoCardPressed / NeoInset
pub fn bg_elevated() -> Hsla  { rgb(0x181c24).into() } // NeoLightShadow surface
pub fn bg_hover() -> Hsla     { rgb(0x1f2430).into() }
pub fn bg_active() -> Hsla    { rgb(0x252c3c).into() }

// ── Text Tokens (matching Android TextPrimary / Secondary / Muted) ──
pub fn text_primary() -> Hsla   { rgb(0xf0f4ff).into() }
pub fn text_secondary() -> Hsla { rgb(0x8899bb).into() }
pub fn text_muted() -> Hsla     { rgb(0x4a5a78).into() }

// ── Brand & Channel Accents (matching Android Neo* tokens) ──
pub fn accent_blue() -> Hsla    { rgb(0x3b82f6).into() } // NeoBlue (Primary Action)
pub fn accent_cyan() -> Hsla    { rgb(0x06b6d4).into() } // NeoCyan / UsbTeal (USB Fast-Path)
pub fn accent_green() -> Hsla   { rgb(0x10b981).into() } // NeoGreen / WifiGreen (5GHz Wi-Fi)
pub fn accent_yellow() -> Hsla  { rgb(0xf59e0b).into() } // NeoAmber (Cable plugged / In-flight)
pub fn accent_purple() -> Hsla  { rgb(0x8b5cf6).into() } // NeoPurple (Phone-to-Phone / Media)
pub fn accent_red() -> Hsla     { rgb(0xef4444).into() } // NeoRed (Danger / Abort)

// ── Channel Pill Backgrounds ──
pub fn usb_badge_bg() -> Hsla   { rgba(0x06b6d426).into() }
pub fn wifi_badge_bg() -> Hsla  { rgba(0x10b98126).into() }
pub fn lan_badge_bg() -> Hsla   { rgba(0x38bdf826).into() }

// ── Borders ──
pub fn border_default() -> Hsla { rgb(0x1c2130).into() }
pub fn border_card() -> Hsla    { rgb(0x242b3d).into() }
pub fn border_active() -> Hsla  { rgb(0x06b6d4).into() }

// ── Corner Radii (matching Android 8.dp, 14.dp, 20.dp, 24.dp) ──
pub fn corner_sm() -> Pixels { px(8.0) }
pub fn corner_md() -> Pixels { px(14.0) }
pub fn corner_lg() -> Pixels { px(20.0) }
pub fn corner_xl() -> Pixels { px(24.0) }


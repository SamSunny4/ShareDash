//! ShareDash GUI Theme — dark palette matching CLI ANSI colors.

use gpui::*;

// ── Background ──────────────────────────────────────────
pub fn bg_base() -> Hsla      { rgb(0x0f0f1a).into() }
pub fn bg_surface() -> Hsla   { rgb(0x1a1a2e).into() }
pub fn bg_elevated() -> Hsla  { rgb(0x252540).into() }
pub fn bg_hover() -> Hsla     { rgb(0x2d2d4a).into() }
pub fn bg_active() -> Hsla    { rgb(0x353560).into() }

// ── Text ────────────────────────────────────────────────
pub fn text_primary() -> Hsla   { rgb(0xe0e0e0).into() }
pub fn text_secondary() -> Hsla { rgb(0x888899).into() }
pub fn text_muted() -> Hsla     { rgb(0x555566).into() }

// ── Accent / Status ─────────────────────────────────────
pub fn accent_cyan() -> Hsla    { rgb(0x00d4ff).into() }
pub fn accent_green() -> Hsla   { rgb(0x00ff88).into() }
pub fn accent_yellow() -> Hsla  { rgb(0xffcc00).into() }
pub fn accent_red() -> Hsla     { rgb(0xff4444).into() }
pub fn accent_blue() -> Hsla    { rgb(0x4488ff).into() }
pub fn accent_magenta() -> Hsla { rgb(0xcc66ff).into() }

// ── Borders ─────────────────────────────────────────────
pub fn border_default() -> Hsla { rgb(0x333355).into() }
pub fn border_active() -> Hsla  { rgb(0x00d4ff).into() }

// ── Sizing tokens ───────────────────────────────────────
pub fn corner_sm() -> Pixels { px(4.0) }
pub fn corner_md() -> Pixels { px(8.0) }
pub fn corner_lg() -> Pixels { px(12.0) }


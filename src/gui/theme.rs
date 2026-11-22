//! Shared visual tokens for the native desktop interface.
use gpui::*;
pub fn bg_base() -> Hsla {
    rgb(0x101318).into()
}
pub fn bg_surface() -> Hsla {
    rgb(0x191d24).into()
}
pub fn bg_inset() -> Hsla {
    rgb(0x13171d).into()
}
pub fn bg_elevated() -> Hsla {
    rgb(0x222831).into()
}
pub fn bg_hover() -> Hsla {
    rgb(0x2c3440).into()
}
pub fn bg_active() -> Hsla {
    rgb(0x24334b).into()
}
pub fn text_primary() -> Hsla {
    rgb(0xf0f3f8).into()
}
pub fn text_secondary() -> Hsla {
    rgb(0xb0bac9).into()
}
pub fn text_muted() -> Hsla {
    rgb(0x8f9bab).into()
}
pub fn accent_blue() -> Hsla {
    rgb(0x80aaff).into()
}
pub fn accent_cyan() -> Hsla {
    rgb(0x79c8d0).into()
}
pub fn accent_green() -> Hsla {
    rgb(0x85c7a5).into()
}
pub fn accent_yellow() -> Hsla {
    rgb(0xe5bc78).into()
}
pub fn accent_red() -> Hsla {
    rgb(0xf29696).into()
}
pub fn button_primary() -> Hsla {
    rgb(0x4772c4).into()
}
pub fn button_hover() -> Hsla {
    rgb(0x5683d7).into()
}
pub fn border_default() -> Hsla {
    rgb(0x303843).into()
}
pub fn border_card() -> Hsla {
    rgb(0x303843).into()
}
pub fn border_active() -> Hsla {
    rgb(0x80aaff).into()
}
pub fn corner_sm() -> Pixels {
    px(6.0)
}
pub fn corner_md() -> Pixels {
    px(10.0)
}
pub fn corner_lg() -> Pixels {
    px(12.0)
}
pub fn corner_xl() -> Pixels {
    px(16.0)
}

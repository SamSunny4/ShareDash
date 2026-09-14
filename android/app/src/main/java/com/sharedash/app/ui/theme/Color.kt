package com.sharedash.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
//  THEME DATA CLASS & PALETTES
// ═══════════════════════════════════════════════════════════════

data class ShareDashColors(
    val bg: Color,
    val card: Color,
    val cardPressed: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean
)

val DarkShareDashColors = ShareDashColors(
    bg = Color(0xFF161A22),
    card = Color(0xFF202632),
    cardPressed = Color(0xFF14171E),
    lightShadow = Color(0xFF2C3444),
    darkShadow = Color(0xFF0C0E13),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    isDark = true
)

val LightShareDashColors = ShareDashColors(
    bg = Color(0xFFF1F5F9),
    card = Color(0xFFFFFFFF),
    cardPressed = Color(0xFFE2E8F0),
    lightShadow = Color(0xFFFFFFFF),
    darkShadow = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    isDark = false
)

val LocalShareDashColors = staticCompositionLocalOf { DarkShareDashColors }

// Dynamic Composable Getters
val NeoBg: Color @Composable get() = LocalShareDashColors.current.bg
val NeoCard: Color @Composable get() = LocalShareDashColors.current.card
val NeoCardPressed: Color @Composable get() = LocalShareDashColors.current.cardPressed
val NeoLightShadow: Color @Composable get() = LocalShareDashColors.current.lightShadow
val NeoDarkShadow: Color @Composable get() = LocalShareDashColors.current.darkShadow

// Text
val TextPrimary: Color @Composable get() = LocalShareDashColors.current.textPrimary
val TextSecondary: Color @Composable get() = LocalShareDashColors.current.textSecondary
val TextMuted: Color @Composable get() = LocalShareDashColors.current.textMuted

// Accents
val NeoBlue = Color(0xFF3B82F6)
val NeoCyan = Color(0xFF06B6D4)
val NeoGreen = Color(0xFF10B981)
val NeoAmber = Color(0xFFF59E0B)
val NeoYellow = Color(0xFFF59E0B)
val NeoPurple = Color(0xFF8B5CF6)
val NeoRed = Color(0xFFEF4444)

// Backward-compatible Aliases
val BgApp: Color @Composable get() = LocalShareDashColors.current.bg
val BgSurface: Color @Composable get() = LocalShareDashColors.current.card
val BgSurfaceElevated: Color @Composable get() = LocalShareDashColors.current.card
val BgSurfaceHover: Color @Composable get() = LocalShareDashColors.current.lightShadow
val AccentBlue = NeoBlue
val AccentCyan = NeoCyan
val UsbTeal = NeoCyan
val WifiGreen = NeoGreen
val LanSky = Color(0xFF38BDF8)
val QuicPurple = NeoPurple
val InFlightYellow = NeoAmber
val DangerRed = NeoRed

val UsbBg = Color(0x1F06B6D4)
val WifiBg = Color(0x1F10B981)
val LanBg = Color(0x1F38BDF8)
val QuicBg = Color(0x1F8B5CF6)
val BorderSubtle: Color @Composable get() = if (LocalShareDashColors.current.isDark) Color(0x20FFFFFF) else Color(0x1A000000)

package com.sharedash.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.sharedash.app.storage.SettingsManager

@Composable
fun ShareDashTheme(
    themeMode: String = SettingsManager.THEME_SYSTEM,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        SettingsManager.THEME_LIGHT -> false
        SettingsManager.THEME_DARK -> true
        else -> isSystemDark
    }

    val colors = if (isDark) DarkShareDashColors else LightShareDashColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            background = colors.bg,
            surface = colors.card,
            surfaceVariant = colors.card,
            onPrimary = colors.textPrimary,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            background = colors.bg,
            surface = colors.card,
            surfaceVariant = colors.card,
            onPrimary = colors.textPrimary,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.bg.toArgb()
                window.navigationBarColor = colors.bg.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalShareDashColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

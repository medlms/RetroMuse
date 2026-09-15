package com.retro.grooveplayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.retro.grooveplayer.playback.PlaybackManager

@Composable
fun GroovePlayerTheme(
    accentColorHex: String = "#7C4DFF",
    content: @Composable () -> Unit
) {
    // Resolve the named theme first; every colour token reads from it.
    val systemDark = isSystemInDarkTheme()
    val theme = AppThemes.resolve(PlaybackManager.themeMode, systemDark)
    PlaybackManager.activeTheme = theme
    PlaybackManager.isDarkTheme = theme.isDark

    // The accent follows the theme unless the user picked a swatch of their own.
    val accentColor = if (PlaybackManager.useThemeAccent) {
        theme.accent
    } else {
        try {
            Color(android.graphics.Color.parseColor(accentColorHex))
        } catch (e: Exception) {
            theme.accent
        }
    }

    val colorScheme = if (theme.isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = if (accentColor.luminanceIsHigh()) Color.Black else Color.White,
            secondary = accentColor,
            background = theme.background,
            onBackground = theme.textPrimary,
            surface = theme.modal,
            onSurface = theme.textPrimary,
            surfaceVariant = theme.card2,
            onSurfaceVariant = theme.textSecondary,
            outline = theme.border,
            error = DangerColor
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = if (accentColor.luminanceIsHigh()) Color.Black else Color.White,
            secondary = accentColor,
            background = theme.background,
            onBackground = theme.textPrimary,
            surface = theme.modal,
            onSurface = theme.textPrimary,
            surfaceVariant = theme.card2,
            onSurfaceVariant = theme.textSecondary,
            outline = theme.border,
            error = DangerColor
        )
    }

    // Keep the system bars in step, including icon tint - a light theme needs dark
    // status icons or they vanish against the canvas.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = theme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !theme.isDark
                isAppearanceLightNavigationBars = !theme.isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Picks readable foreground text for a coloured button. */
private fun Color.luminanceIsHigh(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.65f

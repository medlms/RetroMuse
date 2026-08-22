package com.retro.grooveplayer.ui.theme

import android.app.Activity
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
    val accentColor = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (e: Exception) {
        RetroPurple
    }

    val isDark = PlaybackManager.isDarkTheme
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            secondary = accentColor,
            background = DarkBgColor,
            onBackground = DarkTextPrimaryColor,
            surface = DarkBgModalColor,
            onSurface = DarkTextPrimaryColor,
            surfaceVariant = DarkBgCard2Color,
            onSurfaceVariant = DarkTextSecondaryColor,
            outline = DarkBorderColor,
            error = DangerColor
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            secondary = accentColor,
            background = LightBgColor,
            onBackground = LightTextPrimaryColor,
            surface = LightBgModalColor,
            onSurface = LightTextPrimaryColor,
            surfaceVariant = LightBgCard2Color,
            onSurfaceVariant = LightTextSecondaryColor,
            outline = LightBorderColor,
            error = DangerColor
        )
    }

    // Keep the system bars in step with the theme, including the icon tint - light
    // mode needs dark status bar icons or they vanish against the white canvas.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

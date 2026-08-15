package com.retro.grooveplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.retro.grooveplayer.playback.PlaybackManager

@Composable
fun GroovePlayerTheme(
    accentColorHex: String = "#a855f7",
    content: @Composable () -> Unit
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (e: Exception) {
        Color(0xFFA855F7)
    }

    val isDark = PlaybackManager.isDarkTheme
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentColor,
            background = DarkBgColor,
            surface = DarkBgModalColor,
            onPrimary = Color.White,
            onBackground = DarkTextPrimaryColor,
            onSurface = DarkTextPrimaryColor,
            error = DangerColor
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            background = LightBgColor,
            surface = LightBgModalColor,
            onPrimary = Color.White,
            onBackground = LightTextPrimaryColor,
            onSurface = LightTextPrimaryColor,
            error = DangerColor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

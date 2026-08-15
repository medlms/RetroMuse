package com.retro.grooveplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

    val colorScheme = lightColorScheme(
        primary = accentColor,
        background = BgColor,
        surface = BgModalColor,
        onPrimary = Color.White,
        onBackground = TextPrimaryColor,
        onSurface = TextPrimaryColor,
        error = DangerColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.retro.grooveplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.retro.grooveplayer.playback.PlaybackManager

// --- Light Theme Colors ---
val LightBgColor = Color(0xFFFAF7F2) // Soft retro cream warm background
val LightBgCardColor = Color(0xD9FFFFFF) // Semi-transparent glassmorphic white card
val LightBgCard2Color = Color(0xFFFFFFFF) // Solid pristine white card
val LightBgModalColor = Color(0xFFFAF7F2)
val LightBorderColor = Color(0xFFEADBCE) // Soft beige border
val LightTextPrimaryColor = Color(0xFF1E1725) // Deep plum charcoal (excellent contrast)
val LightTextSecondaryColor = Color(0xFF5F516A) // Muted plum charcoal
val LightTextMutedColor = Color(0xFF9F8FA8) // Soft lavender gray

// --- Dark Theme Colors ---
val DarkBgColor = Color(0xFF040308) // Deep space synthwave background
val DarkBgCardColor = Color(0xBF0F0D1B) // Semi-transparent glassmorphic card
val DarkBgCard2Color = Color(0xE61B172E) // Solid retro card
val DarkBgModalColor = Color(0xFF0C0A15)
val DarkBorderColor = Color(0x2600FFFF) // Cyber neon cyan borders
val DarkTextPrimaryColor = Color(0xFFF9F7FC) // Off-white
val DarkTextSecondaryColor = Color(0xFFA49EB2) // Muted lavender-gray
val DarkTextMutedColor = Color(0xFF5B546D) // Deep muted lavender-gray

// --- Static Branding & Palette Accent Colors ---
val RetroPurple = Color(0xFF9E00FF)
val RetroCyan = Color(0xFF00A2D1) // Deep vaporwave cyan
val RetroPink = Color(0xFFE60067) // Deep vaporwave magenta
val RetroGold = Color(0xFFD47A00) // Deep warm amber gold

val DangerColor = Color(0xFFD6003A)
val WarningColor = Color(0xFFD47A00)
val SuccessColor = Color(0xFF009C75)

// --- Composable Dynamic Colors ---
val BgColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgColor else LightBgColor
val BgCardColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgCardColor else LightBgCardColor
val BgCard2Color: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgCard2Color else LightBgCard2Color
val BgModalColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgModalColor else LightBgModalColor
val BorderColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBorderColor else LightBorderColor
val TextPrimaryColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextPrimaryColor else LightTextPrimaryColor
val TextSecondaryColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextSecondaryColor else LightTextSecondaryColor
val TextMutedColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextMutedColor else LightTextMutedColor

val RetroNeonGradient: Brush @Composable get() = if (PlaybackManager.isDarkTheme) {
    Brush.verticalGradient(
        colors = listOf(Color(0xFF140C24), Color(0xFF050309))
    )
} else {
    Brush.verticalGradient(
        colors = listOf(Color(0xFFECE5DB), Color(0xFFFAF7F2))
    )
}

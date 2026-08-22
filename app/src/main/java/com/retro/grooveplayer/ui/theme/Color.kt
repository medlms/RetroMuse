package com.retro.grooveplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.retro.grooveplayer.playback.PlaybackManager

// --- Light palette -----------------------------------------------------------
// A warm, paper-like neutral canvas. Depth comes from elevation and hairline
// dividers rather than heavy borders, so the accent colour is the only saturated
// thing on screen.
val LightBgColor = Color(0xFFFCFBFA)          // Page canvas, barely-warm white
val LightBgElevatedColor = Color(0xFFFFFFFF)  // Raised surfaces: cards, sheets
val LightBgSunkenColor = Color(0xFFF3F1EE)    // Recessed surfaces: tracks, wells
val LightBgCardColor = Color(0xFFFFFFFF)
val LightBgCard2Color = Color(0xFFF6F4F1)
val LightBgModalColor = Color(0xFFFFFFFF)
val LightBorderColor = Color(0x14171214)      // 8% hairline
val LightTextPrimaryColor = Color(0xFF171214) // Near-black with a hint of warmth
val LightTextSecondaryColor = Color(0xFF5C5459)
val LightTextMutedColor = Color(0xFF938A8F)

// --- Dark palette ------------------------------------------------------------
// Neutral charcoal rather than saturated near-black, so artwork and the accent
// read cleanly against it.
val DarkBgColor = Color(0xFF0E0D10)
val DarkBgElevatedColor = Color(0xFF191820)
val DarkBgSunkenColor = Color(0xFF08070A)
val DarkBgCardColor = Color(0xFF191820)
val DarkBgCard2Color = Color(0xFF211F29)
val DarkBgModalColor = Color(0xFF17161D)
val DarkBorderColor = Color(0x1AFFFFFF)       // 10% hairline
val DarkTextPrimaryColor = Color(0xFFF7F5F8)
val DarkTextSecondaryColor = Color(0xFFA9A3AE)
val DarkTextMutedColor = Color(0xFF6E6875)

// --- Status colours ----------------------------------------------------------
val DangerColor = Color(0xFFD92D3E)
val WarningColor = Color(0xFFC77A00)
val SuccessColor = Color(0xFF00875A)

// Legacy branding aliases, still referenced by a few screens.
val RetroPurple = Color(0xFF7C4DFF)
val RetroCyan = Color(0xFF0091AE)
val RetroPink = Color(0xFFD81B60)
val RetroGold = Color(0xFFC77A00)

// --- Dynamic tokens ----------------------------------------------------------
val BgColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgColor else LightBgColor
val BgElevatedColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgElevatedColor else LightBgElevatedColor
val BgSunkenColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgSunkenColor else LightBgSunkenColor
val BgCardColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgCardColor else LightBgCardColor
val BgCard2Color: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgCard2Color else LightBgCard2Color
val BgModalColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBgModalColor else LightBgModalColor
val BorderColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkBorderColor else LightBorderColor
val TextPrimaryColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextPrimaryColor else LightTextPrimaryColor
val TextSecondaryColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextSecondaryColor else LightTextSecondaryColor
val TextMutedColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) DarkTextMutedColor else LightTextMutedColor

/** Shadow colour tuned per theme - dark mode gets no visible drop shadow. */
val ShadowColor: Color @Composable get() = if (PlaybackManager.isDarkTheme) Color.Transparent else Color(0x14000000)

/** Page background wash. Subtle enough to read as a flat surface. */
val RetroNeonGradient: Brush @Composable get() = if (PlaybackManager.isDarkTheme) {
    Brush.verticalGradient(colors = listOf(Color(0xFF16151B), Color(0xFF0E0D10)))
} else {
    Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFF7F5F2)))
}

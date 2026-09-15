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
// Every token now reads from the active AppTheme, so adding a theme needs no changes
// anywhere else in the UI.

val ActiveTheme: AppTheme
    @Composable get() = PlaybackManager.activeTheme

val BgColor: Color @Composable get() = ActiveTheme.background
val BgElevatedColor: Color @Composable get() = ActiveTheme.elevated
val BgSunkenColor: Color @Composable get() = ActiveTheme.sunken
val BgCardColor: Color @Composable get() = ActiveTheme.card
val BgCard2Color: Color @Composable get() = ActiveTheme.card2
val BgModalColor: Color @Composable get() = ActiveTheme.modal
val BorderColor: Color @Composable get() = ActiveTheme.border
val TextPrimaryColor: Color @Composable get() = ActiveTheme.textPrimary
val TextSecondaryColor: Color @Composable get() = ActiveTheme.textSecondary
val TextMutedColor: Color @Composable get() = ActiveTheme.textMuted

/** Shadow colour tuned per theme - dark themes get no visible drop shadow. */
val ShadowColor: Color @Composable get() =
    if (ActiveTheme.isDark) Color.Transparent else Color(0x14000000)

/** Page background wash. Subtle enough to read as a flat surface. */
val RetroNeonGradient: Brush @Composable get() {
    val theme = ActiveTheme
    return Brush.verticalGradient(colors = listOf(theme.elevated, theme.background))
}

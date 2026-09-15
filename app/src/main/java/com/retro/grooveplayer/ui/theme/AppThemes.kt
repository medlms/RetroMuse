package com.retro.grooveplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A named colour scheme.
 *
 * Previously the app only had a light/dark/system switch plus an accent swatch, so
 * every theme was the same neutral canvas. A theme now carries its own background,
 * surfaces, text and default accent, which is what makes them feel distinct.
 */
data class AppTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val isDark: Boolean,
    val accent: Color,
    val background: Color,
    val elevated: Color,
    val sunken: Color,
    val card: Color,
    val card2: Color,
    val modal: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

object AppThemes {

    /** Follows the system light/dark setting, resolving to Daylight or Midnight. */
    const val SYSTEM = "system"

    val Daylight = AppTheme(
        id = "daylight", name = "Daylight", emoji = "☀️", isDark = false,
        accent = Color(0xFF7C4DFF),
        background = Color(0xFFFCFBFA), elevated = Color(0xFFFFFFFF),
        sunken = Color(0xFFF3F1EE), card = Color(0xFFFFFFFF),
        card2 = Color(0xFFF6F4F1), modal = Color(0xFFFFFFFF),
        border = Color(0x14171214),
        textPrimary = Color(0xFF171214), textSecondary = Color(0xFF5C5459),
        textMuted = Color(0xFF938A8F)
    )

    val Midnight = AppTheme(
        id = "midnight", name = "Midnight", emoji = "🌙", isDark = true,
        accent = Color(0xFF9E7BFF),
        background = Color(0xFF0E0D10), elevated = Color(0xFF191820),
        sunken = Color(0xFF08070A), card = Color(0xFF191820),
        card2 = Color(0xFF211F29), modal = Color(0xFF17161D),
        border = Color(0x1AFFFFFF),
        textPrimary = Color(0xFFF7F5F8), textSecondary = Color(0xFFA9A3AE),
        textMuted = Color(0xFF6E6875)
    )

    val Paper = AppTheme(
        id = "paper", name = "Paper", emoji = "📜", isDark = false,
        accent = Color(0xFF9A6B3F),
        background = Color(0xFFF7F1E4), elevated = Color(0xFFFFFAF0),
        sunken = Color(0xFFEDE4D2), card = Color(0xFFFFFAF0),
        card2 = Color(0xFFF1E8D8), modal = Color(0xFFFFFAF0),
        border = Color(0x1A4A3A28),
        textPrimary = Color(0xFF2E2418), textSecondary = Color(0xFF6B5B47),
        textMuted = Color(0xFF9C8C76)
    )

    val Nord = AppTheme(
        id = "nord", name = "Nord", emoji = "❄️", isDark = true,
        accent = Color(0xFF88C0D0),
        background = Color(0xFF2E3440), elevated = Color(0xFF3B4252),
        sunken = Color(0xFF272C36), card = Color(0xFF3B4252),
        card2 = Color(0xFF434C5E), modal = Color(0xFF3B4252),
        border = Color(0x1AECEFF4),
        textPrimary = Color(0xFFECEFF4), textSecondary = Color(0xFFD8DEE9),
        textMuted = Color(0xFF7B879C)
    )

    val Forest = AppTheme(
        id = "forest", name = "Forest", emoji = "🌲", isDark = true,
        accent = Color(0xFF6FCF97),
        background = Color(0xFF101713), elevated = Color(0xFF1A241E),
        sunken = Color(0xFF0B110D), card = Color(0xFF1A241E),
        card2 = Color(0xFF223029), modal = Color(0xFF18211C),
        border = Color(0x1AD8F3E4),
        textPrimary = Color(0xFFEAF6EE), textSecondary = Color(0xFFA5BFB0),
        textMuted = Color(0xFF6B857A)
    )

    val Sunset = AppTheme(
        id = "sunset", name = "Sunset", emoji = "🌇", isDark = false,
        accent = Color(0xFFE2574C),
        background = Color(0xFFFFF6F1), elevated = Color(0xFFFFFFFF),
        sunken = Color(0xFFFBE8DE), card = Color(0xFFFFFFFF),
        card2 = Color(0xFFFDEEE6), modal = Color(0xFFFFFFFF),
        border = Color(0x1A6B3A2E),
        textPrimary = Color(0xFF3A1F18), textSecondary = Color(0xFF7A5247),
        textMuted = Color(0xFFAD8578)
    )

    val Mono = AppTheme(
        id = "mono", name = "Graphite", emoji = "◐", isDark = true,
        accent = Color(0xFFE6E6E6),
        background = Color(0xFF121212), elevated = Color(0xFF1E1E1E),
        sunken = Color(0xFF0A0A0A), card = Color(0xFF1E1E1E),
        card2 = Color(0xFF272727), modal = Color(0xFF1C1C1C),
        border = Color(0x1FFFFFFF),
        textPrimary = Color(0xFFF2F2F2), textSecondary = Color(0xFFA8A8A8),
        textMuted = Color(0xFF6E6E6E)
    )

    val Neon = AppTheme(
        id = "neon", name = "Neon", emoji = "🌀", isDark = true,
        accent = Color(0xFF00E5C0),
        background = Color(0xFF0A0F1A), elevated = Color(0xFF131B2B),
        sunken = Color(0xFF060A12), card = Color(0xFF131B2B),
        card2 = Color(0xFF1A2438), modal = Color(0xFF111827),
        border = Color(0x2200E5C0),
        textPrimary = Color(0xFFE8FBF7), textSecondary = Color(0xFF93AFC4),
        textMuted = Color(0xFF5E7A88)
    )

    val all = listOf(Daylight, Paper, Sunset, Midnight, Nord, Forest, Mono, Neon)

    fun byId(id: String): AppTheme? = all.firstOrNull { it.id == id }

    /** Resolves the stored preference, honouring the system setting. */
    fun resolve(id: String, systemDark: Boolean): AppTheme = when (id) {
        SYSTEM -> if (systemDark) Midnight else Daylight
        // Legacy values from before named themes existed.
        "light" -> Daylight
        "dark" -> Midnight
        else -> byId(id) ?: Daylight
    }
}

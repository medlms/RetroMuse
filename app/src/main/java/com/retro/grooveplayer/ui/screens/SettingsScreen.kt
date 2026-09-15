package com.retro.grooveplayer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.dsp.RackSettings
import com.retro.grooveplayer.ui.theme.ActiveTheme
import com.retro.grooveplayer.ui.theme.AppThemes
import com.retro.grooveplayer.ui.theme.BgColor
import com.retro.grooveplayer.ui.theme.BgSunkenColor
import com.retro.grooveplayer.ui.theme.BorderColor
import com.retro.grooveplayer.ui.theme.TextPrimaryColor
import com.retro.grooveplayer.ui.theme.TextSecondaryColor
import com.retro.grooveplayer.ui.theme.TextMutedColor
import com.retro.grooveplayer.ui.theme.DangerColor
import com.retro.grooveplayer.ui.theme.WarningColor
import com.retro.grooveplayer.ui.theme.SuccessColor
import com.retro.grooveplayer.ui.theme.RetroPink
import com.retro.grooveplayer.ui.theme.RetroCyan
import com.retro.grooveplayer.ui.theme.ShadowColor

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val accentColorHex = PlaybackManager.accentColor
    val accentColor = Color(android.graphics.Color.parseColor(accentColorHex))

    // Read straight from PlaybackManager, which loads and saves these through
    // StorageManager. They used to be local remember{} state seeded with hardcoded
    // constants, so nothing survived leaving the screen and nothing was applied.
    val gapless = PlaybackManager.gaplessEnabled
    val crossfade = PlaybackManager.crossfadeEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Column {
                Text(
                    text = "Settings",
                    color = TextPrimaryColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = "Playback, sound and appearance",
                    color = TextMutedColor,
                    fontSize = 13.sp
                )
            }
        }

        // Section Playback
        SectionTitle(title = "Playback", accentColor = accentColor)
        
        SettingRow(
            icon = "🎵",
            title = "Gapless Playback",
            subtitle = "Remove silence between tracks",
            rightContent = {
                Switch(
                    checked = gapless,
                    onCheckedChange = { PlaybackManager.changeGapless(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "🔁",
            title = "Crossfade",
            subtitle = "Blend tracks together",
            rightContent = {
                Switch(
                    checked = crossfade,
                    onCheckedChange = { PlaybackManager.changeCrossfade(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        // Bass Boost, 3D Surround, Master Limiter, Playback Speed and Equalizer Preset
        // used to live here as well. They duplicated controls that the Audio Effects
        // sheet and the Studio Rack already own, so this screen keeps only the
        // playback options that exist nowhere else.

        SettingRow(
            icon = "🎚",
            title = "Per-song Studio Rack",
            subtitle = "Remember effect settings separately for each track",
            rightContent = {
                Switch(
                    checked = PlaybackManager.perSongRack,
                    onCheckedChange = { PlaybackManager.changePerSongRack(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }
        )

        // Section Interface
        SectionTitle(title = "Interface", accentColor = accentColor)

        // Appearance: named themes, each carrying its own canvas and accent, rather
        // than a bare light/dark switch.
        SettingRow(
            icon = "🎨",
            title = "Appearance",
            subtitle = "Theme: ${ActiveTheme.name}",
            rightContent = null
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            val followSystem = PlaybackManager.themeMode == AppThemes.SYSTEM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (followSystem) accentColor.copy(alpha = 0.14f) else BgSunkenColor)
                    .clickable { PlaybackManager.changeThemeMode(AppThemes.SYSTEM) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📱", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Follow system",
                        color = if (followSystem) accentColor else TextPrimaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Daylight by day, Midnight at night",
                        color = TextMutedColor,
                        fontSize = 12.sp
                    )
                }
                if (followSystem) Text("✓", color = accentColor, fontSize = 16.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Two-column grid of theme swatches.
            AppThemes.all.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { theme ->
                        val isSelected = PlaybackManager.themeMode == theme.id
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.elevated)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) accentColor else BorderColor,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { PlaybackManager.changeThemeMode(theme.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Preview dot in the theme's own accent.
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(theme.accent)
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = theme.name,
                                color = theme.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        SettingRow(
            icon = "🎨",
            title = "Accent Color",
            subtitle = "Choose your theme color",
            rightContent = null
        )

        // Swatches
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaybackManager.ACCENT_PALETTE.forEach { colorStr ->
                val swatchColor = Color(android.graphics.Color.parseColor(colorStr))
                val isSelected = accentColorHex == colorStr
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            PlaybackManager.changeAccentColor(colorStr)
                        }
                )
            }
        }

        // Section Notifications
        SectionTitle(title = "Notification & Lock Screen", accentColor = accentColor)

        // Background playback requires a foreground-service notification, and the lock
        // screen player is that same notification - neither can be switched off from
        // inside the app, so send the user to the setting that actually governs it.
        Box(modifier = Modifier.clickable {
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't open notification settings.", Toast.LENGTH_SHORT).show()
            }
        }) {
            SettingRow(
                icon = "🔔",
                title = "Notification & Lock Screen",
                subtitle = "Manage the media player shown outside the app",
                rightContent = {
                    Text("›", color = TextMutedColor, fontSize = 22.sp)
                }
            )
        }

        // Only shown when something has actually crashed, so it stays out of the way.
        if (com.retro.grooveplayer.data.CrashLog.hasEntries(context)) {
            SectionTitle(title = "Diagnostics", accentColor = accentColor)
            Box(modifier = Modifier.clickable {
                try {
                    val report = com.retro.grooveplayer.data.CrashLog.read(context)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                        android.content.Intent.EXTRA_SUBJECT,
                        "${context.getString(com.retro.grooveplayer.R.string.app_name)} crash log"
                    )
                        putExtra(android.content.Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(intent, "Send crash log")
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't open the log.", Toast.LENGTH_SHORT).show()
                }
            }) {
                SettingRow(
                    icon = "🐞",
                    title = "Crash Log",
                    subtitle = "A crash was recorded. Tap to review or send it.",
                    rightContent = {
                        Text(
                            "Clear",
                            color = DangerColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                com.retro.grooveplayer.data.CrashLog.clear(context)
                                Toast.makeText(context, "Crash log cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }
        }

        // Storage & Library
        SectionTitle(title = "Storage & Library", accentColor = accentColor)
        
        Box(modifier = Modifier.clickable {
            PlaybackManager.scanDeviceLibrary()
            Toast.makeText(context, "Scanning storage for audio files...", Toast.LENGTH_SHORT).show()
        }) {
            SettingRow(
                icon = "🔄",
                title = "Scan Media Library",
                subtitle = "Scan storage folders for newly added songs",
                rightContent = {
                    Text("›", color = TextMutedColor, fontSize = 22.sp)
                }
            )
        }

        SettingRow(
            icon = "⏳",
            title = "Filter Short Tracks",
            subtitle = "Hide voice notes/ringtones under ${PlaybackManager.minDuration}s",
            rightContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf(0 to "0s", 10 to "10s", 30 to "30s", 60 to "1m", 120 to "2m").forEach { (sec, label) ->
                        val isSelected = PlaybackManager.minDuration == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .border(1.dp, BorderColor, RoundedCornerShape(99.dp))
                                .clickable {
                                    PlaybackManager.changeMinDuration(sec)
                                    Toast.makeText(context, "Library filtered by tracks > ${label}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )

        Box(modifier = Modifier.clickable {
            Toast.makeText(
                context,
                "All exported tracks are saved in the 'Music/RetroMuse' folder of your device storage.",
                Toast.LENGTH_LONG
            ).show()
        }) {
            SettingRow(
                icon = "📂",
                title = "Export Destination",
                subtitle = "Music/RetroMuse",
                rightContent = {
                    Text("›", color = TextMutedColor, fontSize = 22.sp)
                }
            )
        }

        Box(modifier = Modifier.clickable {
            PlaybackManager.clearLibrary()
            Toast.makeText(context, "Library Cleared", Toast.LENGTH_SHORT).show()
        }) {
            SettingRow(
                icon = "🗑️",
                title = "Clear Library",
                subtitle = "Reset audio list and clear cache",
                rightContent = {
                    Text("›", color = TextMutedColor, fontSize = 22.sp)
                }
            )
        }

        // About
        SectionTitle(title = "About", accentColor = accentColor)
        SettingRow(
            "🎵",
            androidx.compose.ui.res.stringResource(com.retro.grooveplayer.R.string.app_name),
            "Version ${com.retro.grooveplayer.BuildConfig.VERSION_NAME}",
            null
        )
        // The engine is Kotlin, not C++ - the previous wording claimed otherwise.
        SettingRow("💜", "DSP Engine", "Real-time Kotlin effect rack", null)
    }
}

@Composable
fun SectionTitle(title: String, accentColor: Color) {
    Text(
        text = title.uppercase(),
        color = TextMutedColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingRow(
    icon: String,
    title: String,
    subtitle: String,
    rightContent: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BgSunkenColor),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 17.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimaryColor, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMutedColor, fontSize = 12.5.sp, modifier = Modifier.padding(top = 1.dp))
        }
        if (rightContent != null) {
            rightContent()
        }
    }
}

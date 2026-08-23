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
    val bassBoost = PlaybackManager.fxBass > 0
    val surround = PlaybackManager.surroundEnabled
    val visualizer = PlaybackManager.visualizerEnabled

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

        SettingRow(
            icon = "🔊",
            title = "Bass Boost",
            subtitle = "Enhance low frequencies",
            rightContent = {
                Switch(
                    checked = bassBoost,
                    onCheckedChange = { PlaybackManager.applyBassBoost(if (it) 80 else 0) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "📻",
            title = "3D Surround Sound",
            subtitle = "Virtual spatial audio",
            rightContent = {
                Switch(
                    checked = surround,
                    onCheckedChange = { PlaybackManager.changeSurround(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "🛡️",
            title = "Master Limiter",
            subtitle = "Prevent clipping in custom DSP effect rack",
            rightContent = {
                Switch(
                    checked = RackSettings.limiterEnabled,
                    onCheckedChange = {
                        RackSettings.limiterEnabled = it
                        RackSettings.touch()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "⚡",
            title = "Playback Speed",
            subtitle = "Current: ${PlaybackManager.speed}x",
            rightContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        val isSelected = PlaybackManager.speed == s
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .border(1.dp, BorderColor, RoundedCornerShape(99.dp))
                                .clickable {
                                    PlaybackManager.speed = s
                                    PlaybackManager.fxSpeed = s
                                    PlaybackManager.updatePlaybackParameters()
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${s}x",
                                color = if (isSelected) Color.White else TextSecondaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        )

        SettingRow(
            icon = "🎛️",
            title = "Equalizer Preset",
            subtitle = "Active: ${PlaybackManager.eqPreset}",
            rightContent = null
        )

        // Section Interface
        SectionTitle(title = "Interface", accentColor = accentColor)

        SettingRow(
            icon = "🎨",
            title = "Theme Mode",
            subtitle = "Active: ${PlaybackManager.themeMode.uppercase()}",
            rightContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf("light" to "☀️ Light", "dark" to "🌙 Dark", "system" to "📱 System").forEach { (mode, label) ->
                        val isSelected = PlaybackManager.themeMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (isSelected) accentColor else Color.Transparent)
                                .border(1.dp, BorderColor, RoundedCornerShape(99.dp))
                                .clickable {
                                    PlaybackManager.changeThemeMode(mode)
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

        SettingRow(
            icon = "✨",
            title = "Visualizer",
            subtitle = "Show audio bars while playing",
            rightContent = {
                Switch(
                    checked = visualizer,
                    onCheckedChange = { PlaybackManager.changeVisualizerEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

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
        SettingRow("🎵", "RetroMuse Music Player", "Version ${com.retro.grooveplayer.BuildConfig.VERSION_NAME} – Sideload & Edit Pro", null)
        SettingRow("💜", "DSP Engine", "Custom C++ Real-time Effect Rack", null)
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

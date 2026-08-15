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
import com.retro.grooveplayer.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val accentColorHex = PlaybackManager.accentColor
    val accentColor = Color(android.graphics.Color.parseColor(accentColorHex))

    // States
    var gapless by remember { mutableStateOf(true) }
    var crossfade by remember { mutableStateOf(false) }
    var bassBoost by remember { mutableStateOf(PlaybackManager.fxBass > 0) }
    var surround by remember { mutableStateOf(false) }
    var visualizer by remember { mutableStateOf(true) }
    var spinningDisc by remember { mutableStateOf(true) }
    var notification by remember { mutableStateOf(true) }
    var lockscreen by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF160D27), BgColor)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "RETROMUSE",
                    color = RetroPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "System Configuration",
                    color = RetroCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
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
                    onCheckedChange = { gapless = it },
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
                    onCheckedChange = { crossfade = it },
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
                    onCheckedChange = {
                        bassBoost = it
                        PlaybackManager.applyBassBoost(if (it) 80 else 0)
                    },
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
                    onCheckedChange = {
                        surround = it
                        PlaybackManager.applyVirtualizer(it)
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
            icon = "✨",
            title = "Visualizer",
            subtitle = "Show audio bars while playing",
            rightContent = {
                Switch(
                    checked = visualizer,
                    onCheckedChange = { visualizer = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "💿",
            title = "Spinning Disc",
            subtitle = "Rotate artwork while playing",
            rightContent = {
                Switch(
                    checked = spinningDisc,
                    onCheckedChange = { spinningDisc = it },
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

        SettingRow(
            icon = "🔔",
            title = "Media Notification",
            subtitle = "Show controls in notification bar",
            rightContent = {
                Switch(
                    checked = notification,
                    onCheckedChange = { notification = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        SettingRow(
            icon = "🔒",
            title = "Lock Screen Controls",
            subtitle = "Control playback from lock screen",
            rightContent = {
                Switch(
                    checked = lockscreen,
                    onCheckedChange = { lockscreen = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                )
            }
        )

        // Storage
        SectionTitle(title = "Storage", accentColor = accentColor)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    PlaybackManager.clearLibrary()
                    Toast.makeText(context, "Library Cleared", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🗑️", fontSize = 22.sp, modifier = Modifier.width(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Clear Library", color = TextPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Reset audio list and clear cache", color = TextSecondaryColor, fontSize = 12.sp)
            }
            Text("›", color = TextMutedColor, fontSize = 20.sp)
        }

        // About
        SectionTitle(title = "About", accentColor = accentColor)
        SettingRow("🎵", "RetroMuse Music Player", "Version 1.0.0 – Built with Jetpack Compose", null)
        SettingRow("💜", "Made with", "Kotlin + Jetpack Media3 ExoPlayer", null)
    }
}

@Composable
fun SectionTitle(title: String, accentColor: Color) {
    Text(
        text = title.uppercase(),
        color = accentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.3.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 6.dp)
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(icon, fontSize = 22.sp, modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondaryColor, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
        }
        if (rightContent != null) {
            rightContent()
        }
    }
}

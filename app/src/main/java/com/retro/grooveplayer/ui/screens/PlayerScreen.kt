package com.retro.grooveplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.playback.RepeatMode
import com.retro.grooveplayer.playback.VocalProcessor
import com.retro.grooveplayer.ui.components.BottomModal
import com.retro.grooveplayer.ui.components.formatTime
import com.retro.grooveplayer.ui.theme.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBackClick: () -> Unit) {
    val currentSong = PlaybackManager.currentSong
    val accentColorHex = PlaybackManager.accentColor
    val accentColor = Color(android.graphics.Color.parseColor(accentColorHex))

    // Modals
    var showEQ by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showVocalIsolatorModal by remember { mutableStateOf(false) }

    if (currentSong == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF080810))
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎵", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Song Playing",
                color = TextPrimaryColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Go to Library and pick a song",
                color = TextSecondaryColor,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(99.dp)
            ) {
                Text("Browse Library", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val isPlaying = PlaybackManager.isPlaying
    val position = PlaybackManager.position
    val duration = PlaybackManager.duration
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    val isFav = PlaybackManager.favourites.contains(currentSong.id)

    // Disc rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "disc")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Restart), label = "rotation"
    )
    val discRotation = if (isPlaying) rotation else 0f

    // Visualizer randomized heights
    val vizHeights = remember { mutableStateListOf<Float>().apply { addAll(List(28) { 4f }) } }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                for (i in 0 until 28) {
                    vizHeights[i] = 4f + Random.nextFloat() * 28f
                }
                kotlinx.coroutines.delay(100)
            }
        } else {
            for (i in 0 until 28) {
                vizHeights[i] = 4f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.22f), Color(0xFF080810), Color(0xFF080810))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Text("‹", color = TextPrimaryColor, fontSize = 32.sp)
                }
                Text(
                    text = "NOW PLAYING",
                    color = TextSecondaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                IconButton(onClick = { showQueue = true }) {
                    Text("☰", color = TextPrimaryColor, fontSize = 20.sp)
                }
            }

            // Artwork Disc
            val discSize = if (isPlaying) 150.dp else 230.dp
            val innerDiscSize = if (isPlaying) 130.dp else 200.dp
            Box(
                modifier = Modifier
                    .size(discSize)
                    .align(Alignment.CenterHorizontally)
                    .rotate(discRotation),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow shadow Box
                Box(
                    modifier = Modifier
                        .size(innerDiscSize)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.3f), accentColor)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    com.retro.grooveplayer.ui.components.ArtworkImage(
                        artworkUri = currentSong.albumArtUri,
                        songColorHex = currentSong.color,
                        modifier = Modifier.fillMaxSize(),
                        iconSizeSp = if (isPlaying) 44 else 64
                    )
                }
            }

            // Song details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentSong.name,
                        color = TextPrimaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isFav) "❤️" else "♡",
                        fontSize = 24.sp,
                        modifier = Modifier.clickable { PlaybackManager.toggleFavourite(currentSong.id) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = currentSong.artist, color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = currentSong.album, color = TextMutedColor, fontSize = 12.sp)
            }

            // Visualizer bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom
            ) {
                vizHeights.forEachIndexed { i, h ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor.copy(alpha = 0.6f + (i % 3) * 0.15f))
                    )
                }
            }

            // Progress Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(position), color = TextMutedColor, fontSize = 12.sp)
                    Text(formatTime(duration), color = TextMutedColor, fontSize = 12.sp)
                }
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { PlaybackManager.seekTo((it * duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0x26FFFFFF)
                    )
                )
            }

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { PlaybackManager.toggleShuffle() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (PlaybackManager.shuffleOn) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, BorderColor, CircleShape)
                ) {
                    Text(
                        "⇀",
                        color = if (PlaybackManager.shuffleOn) accentColor else TextSecondaryColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Prev
                IconButton(
                    onClick = { PlaybackManager.prevSong() },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(BgCard2Color)
                        .border(1.dp, BorderColor, CircleShape)
                ) {
                    Text("⏮", color = TextPrimaryColor, fontSize = 22.sp)
                }

                // Play
                IconButton(
                    onClick = { PlaybackManager.togglePlay() },
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Text(
                        if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 28.sp
                    )
                }

                // Next
                IconButton(
                    onClick = { PlaybackManager.nextSong() },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(BgCard2Color)
                        .border(1.dp, BorderColor, CircleShape)
                ) {
                    Text("⏭", color = TextPrimaryColor, fontSize = 22.sp)
                }

                // Repeat
                val repActive = PlaybackManager.repeatMode != RepeatMode.NONE
                IconButton(
                    onClick = { PlaybackManager.cycleRepeat() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (repActive) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, BorderColor, CircleShape)
                ) {
                    val label = when (PlaybackManager.repeatMode) {
                        RepeatMode.NONE -> "↻"
                        RepeatMode.ONE -> "🔂"
                        RepeatMode.ALL -> "🔁"
                    }
                    Text(
                        label,
                        color = if (repActive) accentColor else TextSecondaryColor,
                        fontSize = 18.sp
                    )
                }
            }

            // Volume controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔈", fontSize = 18.sp)
                Slider(
                    value = PlaybackManager.volume,
                    onValueChange = { PlaybackManager.setPlayerVolume(it) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0x26FFFFFF)
                    )
                )
                Text("🔊", fontSize = 18.sp)
            }

            // Sound Editing dashboard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Equalizer Card
                    DashboardCard(
                        icon = "🎛️",
                        title = "Equalizer",
                        subtitle = PlaybackManager.eqPreset,
                        active = PlaybackManager.eqPreset != "Flat",
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showEQ = true }
                    )
                    // Audio FX Card
                    DashboardCard(
                        icon = "🌀",
                        title = "Effects / Speed",
                        subtitle = "${PlaybackManager.fxSpeed}x | Pitch ${if (PlaybackManager.fxPitch > 0) "+" else ""}${PlaybackManager.fxPitch}",
                        active = PlaybackManager.fxSpeed != 1.0f || PlaybackManager.fxPitch != 0 || PlaybackManager.fxReverb > 0,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showEffects = true }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Vocal Isolator Card
                    val isolatorModeLabel = when (PlaybackManager.vocalProcessor.mode) {
                        VocalProcessor.Mode.OFF -> "Off"
                        VocalProcessor.Mode.ISOLATE_INSTRUMENTAL -> "Instrumental"
                        VocalProcessor.Mode.ISOLATE_VOCAL -> "Vocals Only"
                    }
                    DashboardCard(
                        icon = "🎙️",
                        title = "Vocal Isolator",
                        subtitle = isolatorModeLabel,
                        active = PlaybackManager.vocalProcessor.mode != VocalProcessor.Mode.OFF,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showVocalIsolatorModal = true }
                    )
                    // Timers Card
                    val timerActive = PlaybackManager.sleepTimerEndTime != null || PlaybackManager.startTimerEndTime != null
                    val timerLabel = if (PlaybackManager.sleepTimerEndTime != null) {
                        "Sleep: ${PlaybackManager.sleepTimerCountdown}"
                    } else if (PlaybackManager.startTimerEndTime != null) {
                        "Start: ${PlaybackManager.startTimerCountdown}"
                    } else "None Set"
                    DashboardCard(
                        icon = "⏱️",
                        title = "Timers",
                        subtitle = timerLabel,
                        active = timerActive,
                        accentColor = WarningColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showTimer = true }
                    )
                }
            }

            // Quick Actions: Karaoke Lyrics & Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtraButton(
                    label = "🎤 Karaoke Lyrics",
                    modifier = Modifier.weight(1f),
                    onClick = { showLyrics = true }
                )
                ExtraButton(
                    label = "☰ Up Next Queue",
                    modifier = Modifier.weight(1f),
                    onClick = { showQueue = true }
                )
            }
        }
    }

    // Synchronized Karaoke Lyrics Bottom Sheet
    BottomModal(visible = showLyrics, onDismissRequest = { showLyrics = false }, title = "Synchronized Lyrics 🎤") {
        val lyrics = remember(currentSong) { PlaybackManager.getLyricsForCurrentSong() }
        val listState = rememberLazyListState()

        // Find active line index based on playback position
        val activeLineIdx = remember(position, lyrics) {
            val idx = lyrics.indexOfLast { position >= it.timeMs }
            if (idx >= 0) idx else 0
        }

        LaunchedEffect(activeLineIdx) {
            if (lyrics.isNotEmpty()) {
                listState.animateScrollToItem(activeLineIdx)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { idx, line ->
                val isActive = idx == activeLineIdx
                Text(
                    text = line.text,
                    color = if (isActive) accentColor else TextMutedColor,
                    fontSize = if (isActive) 20.sp else 15.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { PlaybackManager.seekTo(line.timeMs) }
                )
            }
        }
    }

    // EQ Bottom Sheet
    BottomModal(visible = showEQ, onDismissRequest = { showEQ = false }, title = "Equalizer") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaybackManager.EQ_PRESETS.keys.forEach { name ->
                val isSelected = PlaybackManager.eqPreset == name
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isSelected) accentColor else BgCardColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(99.dp))
                        .clickable { PlaybackManager.applyEqPreset(name) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        name,
                        color = if (isSelected) Color.White else TextSecondaryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Custom Frequency Sliders
        Text(
            text = "CUSTOM EQUALIZER BANDS",
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        val customFreqs = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            customFreqs.forEachIndexed { idx, freqName ->
                val currentDb = PlaybackManager.customEqBands.getOrElse(idx) { 0 }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(freqName, color = TextPrimaryColor, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                    Slider(
                        value = currentDb.toFloat(),
                        onValueChange = { PlaybackManager.setCustomBandLevel(idx, it.toInt()) },
                        valueRange = -6f..6f,
                        steps = 11,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                    Text(
                        text = if (currentDb > 0) "+${currentDb}dB" else "${currentDb}dB",
                        color = TextSecondaryColor,
                        fontSize = 11.sp,
                        modifier = Modifier.width(42.dp)
                    )
                }
            }
        }
    }

    // Timers Bottom Sheet
    BottomModal(visible = showTimer, onDismissRequest = { showTimer = false }, title = "Timers") {
        var timerTab by remember { mutableStateOf("stop") } // "stop" or "start"
        var customMins by remember { mutableStateOf(30) }
        var customStartMins by remember { mutableStateOf(30) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            TabRowButton(
                title = "Stop Music",
                selected = timerTab == "stop",
                onClick = { timerTab = "stop" },
                accentColor = accentColor
            )
            TabRowButton(
                title = "Start Music",
                selected = timerTab == "start",
                onClick = { timerTab = "start" },
                accentColor = accentColor
            )
        }
        Divider(color = BorderColor, thickness = 1.dp)

        Spacer(modifier = Modifier.height(14.dp))

        if (timerTab == "stop") {
            // Stop grid options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val stopMinutes = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 0)
                // Render grids
                val chunked = stopMinutes.chunked(3)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgCard2Color)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        PlaybackManager.startSleepTimer(mins)
                                        showTimer = false
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mins == 0) "End Song" else "$mins min",
                                    color = TextPrimaryColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Slider
                Text(
                    text = "Custom Sleep Timer: $customMins min",
                    color = TextSecondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = customMins.toFloat(),
                    onValueChange = { customMins = it.toInt() },
                    valueRange = 1f..540f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Button(
                    onClick = {
                        PlaybackManager.startSleepTimer(customMins)
                        showTimer = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Custom Sleep Timer", color = Color.White)
                }
            }
        } else {
            // Start grid options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val startMinutes = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
                val chunked = startMinutes.chunked(3)
                chunked.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgCard2Color)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        PlaybackManager.startStartTimer(mins)
                                        showTimer = false
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins min",
                                    color = TextPrimaryColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Slider
                Text(
                    text = "Start Music in: $customStartMins min",
                    color = TextSecondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = customStartMins.toFloat(),
                    onValueChange = { customStartMins = it.toInt() },
                    valueRange = 1f..540f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Button(
                    onClick = {
                        PlaybackManager.startStartTimer(customStartMins)
                        showTimer = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                    shape = RoundedCornerShape(99.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Custom Start Timer", color = Color.White)
                }
            }
        }

        val sleepActive = PlaybackManager.sleepTimerEndTime != null || PlaybackManager.isSleepTimerEndOfSong
        val startActive = PlaybackManager.startTimerEndTime != null
        if (sleepActive || startActive) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (sleepActive) {
                    "⏱ Active: Sleep in ${PlaybackManager.sleepTimerCountdown}"
                } else {
                    "⏱ Active: Start in ${PlaybackManager.startTimerCountdown}"
                },
                color = WarningColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    PlaybackManager.clearSleepTimer()
                    PlaybackManager.clearStartTimer()
                    showTimer = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerColor.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, DangerColor),
                shape = RoundedCornerShape(99.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel Active Timers", color = DangerColor, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Audio Effects Bottom Sheet
    BottomModal(visible = showEffects, onDismissRequest = { showEffects = false }, title = "Audio Effects") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Speed
            Column {
                Text(
                    text = String.format("⚡ Speed (%.2fx)", PlaybackManager.fxSpeed),
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = PlaybackManager.fxSpeed,
                    onValueChange = {
                        PlaybackManager.fxSpeed = it
                        PlaybackManager.updatePlaybackParameters()
                    },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0.5x", color = TextMutedColor, fontSize = 10.sp)
                    Text("1.0x (Normal)", color = TextMutedColor, fontSize = 10.sp)
                    Text("2.0x", color = TextMutedColor, fontSize = 10.sp)
                }
            }

            // Pitch
            Column {
                Text(
                    text = "🎵 Pitch (${if (PlaybackManager.fxPitch > 0) "+" else ""}${PlaybackManager.fxPitch} semitones)",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = PlaybackManager.fxPitch.toFloat(),
                    onValueChange = {
                        PlaybackManager.fxPitch = it.toInt()
                        PlaybackManager.updatePlaybackParameters()
                    },
                    valueRange = -5f..5f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-5st", color = TextMutedColor, fontSize = 10.sp)
                    Text("0 (Normal)", color = TextMutedColor, fontSize = 10.sp)
                    Text("+5st", color = TextMutedColor, fontSize = 10.sp)
                }
            }

            // Reverb
            Column {
                Text(
                    text = "🌊 Reverb/3D Space (${PlaybackManager.fxReverb}%)",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = PlaybackManager.fxReverb.toFloat(),
                    onValueChange = {
                        PlaybackManager.applyReverb(it.toInt())
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0% (Dry)", color = TextMutedColor, fontSize = 10.sp)
                    Text("50%", color = TextMutedColor, fontSize = 10.sp)
                    Text("100% (Wet)", color = TextMutedColor, fontSize = 10.sp)
                }
            }

            // Bass Boost
            Column {
                Text(
                    text = "🔊 Bass Boost (${PlaybackManager.fxBass}%)",
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = PlaybackManager.fxBass.toFloat(),
                    onValueChange = {
                        PlaybackManager.applyBassBoost(it.toInt())
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("0%", color = TextMutedColor, fontSize = 10.sp)
                    Text("50%", color = TextMutedColor, fontSize = 10.sp)
                    Text("100%", color = TextMutedColor, fontSize = 10.sp)
                }
            }

            Text(
                text = "💡 Notice: Audio pitch/tempo resampler maps natively onto ExoPlayer engine.",
                color = TextMutedColor,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Queue Bottom Sheet
    BottomModal(visible = showQueue, onDismissRequest = { showQueue = false }, title = "Up Next") {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
        ) {
            items(PlaybackManager.songs) { song ->
                val isCurrent = song.id == currentSong.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable {
                            PlaybackManager.playSong(song, PlaybackManager.songs)
                            showQueue = false
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCurrent) "▶  ${song.name}" else song.name,
                            color = if (isCurrent) accentColor else TextPrimaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            color = TextSecondaryColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatTime(song.duration),
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
        // Vocal Isolator Bottom Sheet
        BottomModal(visible = showVocalIsolatorModal, onDismissRequest = { showVocalIsolatorModal = false }, title = "Live Vocal Isolator 🎙️") {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Isolate singing or instrumentals live using real-time phase-cancellation DSP.",
                    color = TextSecondaryColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                val currentMode = PlaybackManager.vocalProcessor.mode
                listOf(
                    Triple(com.retro.grooveplayer.playback.VocalProcessor.Mode.OFF, "🎙️ Normal Mode", "Play standard audio without filtering"),
                    Triple(com.retro.grooveplayer.playback.VocalProcessor.Mode.ISOLATE_INSTRUMENTAL, "🎸 Isolate Instrumentals", "Remove vocals using phase subtraction"),
                    Triple(com.retro.grooveplayer.playback.VocalProcessor.Mode.ISOLATE_VOCAL, "🗣️ Isolate Vocals", "Extract center singing using mono sum")
                ).forEach { (mode, title, desc) ->
                    val isSelected = currentMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else BgCard2Color)
                            .border(1.dp, if (isSelected) accentColor else BorderColor, RoundedCornerShape(10.dp))
                            .clickable {
                                PlaybackManager.vocalProcessor.mode = mode
                                showVocalIsolatorModal = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, color = if (isSelected) accentColor else TextPrimaryColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = desc, color = TextMutedColor, fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Text("✨", fontSize = 16.sp, color = accentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    icon: String,
    title: String,
    subtitle: String,
    active: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCardColor.copy(alpha = 0.8f))
            .border(
                1.dp,
                if (active) accentColor else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (active) accentColor.copy(alpha = 0.15f) else Color(0x10FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 18.sp)
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = if (active) accentColor else TextMutedColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExtraButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    active: Boolean = false,
    activeColor: Color = Color(0xFFA855F7)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) activeColor.copy(alpha = 0.15f) else BgCardColor)
            .border(
                width = 1.dp,
                color = if (active) activeColor else BorderColor,
                shape = RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else TextSecondaryColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RowScope.TabRowButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (selected) accentColor else TextSecondaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (selected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(accentColor)
                )
            }
        }
    }
}

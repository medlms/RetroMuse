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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Hands the rendered file to the system share sheet. */
private fun shareAudio(
    context: android.content.Context,
    uri: android.net.Uri,
    songName: String,
    presetLabel: String
) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_TITLE, "$songName ($presetLabel)")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share audio"))
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(
            context,
            "Saved to Music/RetroMuse, but sharing isn't available.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

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
    var showPresets by remember { mutableStateOf(false) }

    if (currentSong == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
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

    // Bars driven by the real output spectrum (see AudioLevels), not random numbers.
    val barCount = com.retro.grooveplayer.playback.AudioLevels.BAR_COUNT
    val vizHeights = remember { mutableStateListOf<Float>().apply { addAll(List(barCount) { 3f }) } }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            com.retro.grooveplayer.playback.AudioLevels.start()
            while (true) {
                for (i in 0 until barCount) {
                    vizHeights[i] = 3f + com.retro.grooveplayer.playback.AudioLevels.bars[i] * 32f
                }
                kotlinx.coroutines.delay(50)
            }
        } else {
            com.retro.grooveplayer.playback.AudioLevels.stop()
            for (i in 0 until barCount) {
                vizHeights[i] = 3f
            }
        }
    }

    // Surface a failed track once, then clear it.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(PlaybackManager.playbackError) {
        PlaybackManager.playbackError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            PlaybackManager.playbackError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.15f), BgColor, BgColor)
                    )
                )
        )

        // Scrollable so the controls stay reachable in landscape and on short screens,
        // where the fixed column used to clip the dashboard off the bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "NOW PLAYING",
                    color = TextMutedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Compact now-playing header. The large rotating disc used to occupy the
            // top third of the screen; the editing controls need that room more.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(
                            elevation = if (PlaybackManager.isDarkTheme) 0.dp else 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = accentColor.copy(alpha = 0.25f),
                            spotColor = accentColor.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgCard2Color)
                ) {
                    com.retro.grooveplayer.ui.components.ArtworkImage(
                        artworkUri = currentSong.albumArtUri,
                        songColorHex = currentSong.color,
                        modifier = Modifier.fillMaxSize(),
                        iconSizeSp = 24
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong.name,
                        color = TextPrimaryColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentSong.artist} · ${currentSong.album}",
                        color = TextMutedColor,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (isFav) accentColor else TextMutedColor,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { PlaybackManager.toggleFavourite(currentSong.id) }
                )
            }

            // Visualizer bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (PlaybackManager.visualizerEnabled) 44.dp else 12.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom
            ) {
                if (PlaybackManager.visualizerEnabled) {
                    vizHeights.forEachIndexed { i, h ->
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accentColor.copy(alpha = 0.55f + (i % 3) * 0.15f))
                        )
                    }
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
                        inactiveTrackColor = BgSunkenColor
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
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (PlaybackManager.shuffleOn) accentColor else TextMutedColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                // Prev
                IconButton(
                    onClick = { PlaybackManager.prevSong() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Play
                IconButton(
                    onClick = { PlaybackManager.togglePlay() },
                    modifier = Modifier
                        .size(70.dp)
                        .shadow(
                            elevation = if (PlaybackManager.isDarkTheme) 0.dp else 14.dp,
                            shape = CircleShape,
                            ambientColor = accentColor.copy(alpha = 0.5f),
                            spotColor = accentColor.copy(alpha = 0.5f)
                        )
                        .clip(CircleShape)
                        .background(accentColor)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { PlaybackManager.nextSong() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = TextPrimaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat
                val repActive = PlaybackManager.repeatMode != RepeatMode.NONE
                IconButton(
                    onClick = { PlaybackManager.cycleRepeat() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (PlaybackManager.repeatMode) {
                            RepeatMode.ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repActive) accentColor else TextMutedColor,
                        modifier = Modifier.size(21.dp)
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
                Icon(
                    Icons.Filled.VolumeMute,
                    contentDescription = null,
                    tint = TextMutedColor,
                    modifier = Modifier.size(18.dp)
                )
                Slider(
                    value = PlaybackManager.volume,
                    onValueChange = { PlaybackManager.setPlayerVolume(it) },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = BgSunkenColor
                    )
                )
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = TextMutedColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Effect presets - the app's headline feature, so it sits above the
            // technical controls rather than buried in a sub-sheet.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EFFECTS",
                        color = TextMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Save & Share",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showPresets = true }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaybackManager.FX_PRESETS.forEach { preset ->
                        val selected = PlaybackManager.activePreset == preset.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(if (selected) accentColor else BgSunkenColor)
                                .clickable { PlaybackManager.applyFxPreset(preset) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = preset.label,
                                color = if (selected) Color.White else TextSecondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
                    val isolatorModeLabel = when (PlaybackManager.vocalMode) {
                        VocalProcessor.Mode.OFF -> "Off"
                        VocalProcessor.Mode.ISOLATE_INSTRUMENTAL -> "Instrumental"
                        VocalProcessor.Mode.ISOLATE_VOCAL -> "Vocals Only"
                    }
                    DashboardCard(
                        icon = "🎙️",
                        title = "Vocal Isolator",
                        subtitle = isolatorModeLabel,
                        active = PlaybackManager.vocalMode != VocalProcessor.Mode.OFF,
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

            // Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtraButton(
                    label = "🎤 Lyrics",
                    modifier = Modifier.weight(1f),
                    onClick = { showLyrics = true }
                )
                ExtraButton(
                    label = "☰ Queue",
                    modifier = Modifier.weight(1f),
                    onClick = { showQueue = true }
                )
            }

            // Studio rack, inline with the rest of the controls rather than hidden in a
            // sheet. The screen already scrolls, and the disc that used to sit at the
            // top made room for it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STUDIO RACK",
                        color = TextMutedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = if (com.retro.grooveplayer.dsp.RackSettings.anyEnabled) "Active" else "Off",
                        color = if (com.retro.grooveplayer.dsp.RackSettings.anyEnabled) accentColor else TextMutedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                EffectRackContent(accentColor = accentColor)

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(99.dp))
                        .background(accentColor)
                        .clickable { showPresets = true }
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Save this edit",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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

        if (lyrics.isEmpty()) {
            // Previously this showed invented placeholder verses as if they were the
            // song's real lyrics.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎤", fontSize = 36.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "No lyrics for this track",
                    color = TextPrimaryColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Put a matching .lrc file next to the audio file and it will sync here automatically.",
                    color = TextMutedColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            return@BottomModal
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
                                    text = if (mins == 0) "End Song" else PlaybackManager.formatTimerLabel(mins),
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
                    text = "Custom Sleep Timer: ${PlaybackManager.formatTimerLabel(customMins)}",
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
                                    text = PlaybackManager.formatTimerLabel(mins),
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
                    text = "Start Music in: ${PlaybackManager.formatTimerLabel(customStartMins)}",
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
                    // Retiming the audio pipeline mid-drag rebuffers the player on every
                    // frame, so only the label follows the thumb - the engine is retuned
                    // once, when the drag ends.
                    onValueChange = { PlaybackManager.fxSpeed = it },
                    onValueChangeFinished = { PlaybackManager.updatePlaybackParameters() },
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
                    onValueChange = { PlaybackManager.fxPitch = it.toInt() },
                    onValueChangeFinished = { PlaybackManager.updatePlaybackParameters() },
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
                    onValueChange = { PlaybackManager.fxReverb = it.toInt() },
                    onValueChangeFinished = { PlaybackManager.applyReverb(PlaybackManager.fxReverb) },
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
    }

    // Export / share sheet. Rendering the processed version to a file is what turns
    // the effects from a private playback toy into something people can post.
    LaunchedEffect(PlaybackManager.exportedUri) {
        PlaybackManager.exportedUri?.let { uri ->
            showPresets = false
            shareAudio(context, uri, currentSong.name, PlaybackManager.exportedLabel)
            PlaybackManager.exportedUri = null
        }
    }
    LaunchedEffect(PlaybackManager.exportError) {
        PlaybackManager.exportError?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            PlaybackManager.exportError = null
        }
    }

    BottomModal(
        visible = showPresets,
        onDismissRequest = { showPresets = false },
        title = "Save & Share"
    ) {
        val preset = PlaybackManager.FX_PRESETS
            .firstOrNull { it.id == PlaybackManager.activePreset }
            ?: PlaybackManager.FX_PRESETS.first()

        Text(
            text = "Render \"${currentSong.name}\" with the current effects into a new audio file you can share.",
            color = TextSecondaryColor,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgSunkenColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(preset.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.label,
                    color = TextPrimaryColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${String.format("%.2f", PlaybackManager.fxSpeed)}x · " +
                        "pitch ${PlaybackManager.fxPitch} · reverb ${PlaybackManager.fxReverb}%",
                    color = TextMutedColor,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        val progress = PlaybackManager.exportProgress
        if (progress != null) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = BgSunkenColor
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Rendering ${(progress * 100).toInt()}% - keep the app open",
                color = TextMutedColor,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(99.dp))
                    .background(accentColor)
                    .clickable { PlaybackManager.startExport(currentSong, preset) }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⬇", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Export & Share",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Saved to Music/RetroMuse on your device.",
                color = TextMutedColor,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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

                val currentMode = PlaybackManager.vocalMode
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
                                PlaybackManager.changeVocalMode(mode)
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
    com.retro.grooveplayer.ui.components.SoftCard(
        modifier = modifier.height(70.dp),
        corner = 16.dp,
        elevation = 4.dp,
        background = if (active) accentColor.copy(alpha = 0.10f) else BgCardColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) accentColor.copy(alpha = 0.18f) else BgSunkenColor),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    color = TextPrimaryColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = if (active) accentColor else TextMutedColor,
                    fontSize = 11.5.sp,
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
            .background(if (active) activeColor.copy(alpha = 0.14f) else BgSunkenColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else TextSecondaryColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
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

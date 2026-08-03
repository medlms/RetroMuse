package com.retro.grooveplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.data.Song
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.theme.*

import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}

@Composable
fun ArtworkImage(
    artworkUri: String?,
    songColorHex: String,
    modifier: Modifier = Modifier,
    iconSizeSp: Int = 22
) {
    val context = LocalContext.current
    val songBaseColor = try {
        Color(android.graphics.Color.parseColor(songColorHex))
    } catch (e: Exception) {
        Color(0xFFA855F7)
    }

    if (!artworkUri.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artworkUri)
                .crossfade(true)
                .build(),
            contentDescription = "Album Artwork",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(songBaseColor.copy(alpha = 0.5f), Color(0xFF0A0A1A))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Text("🎵", fontSize = iconSizeSp.sp)
        }
    }
}

@Composable
fun ActivePlayingIndicator(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "indicator")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 2f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(24.dp).padding(bottom = 2.dp)
    ) {
        Box(Modifier.width(4.dp).height(h1.dp).background(accentColor, RoundedCornerShape(2.dp)))
        Box(Modifier.width(4.dp).height(h2.dp).background(accentColor, RoundedCornerShape(2.dp)))
        Box(Modifier.width(4.dp).height(h3.dp).background(accentColor, RoundedCornerShape(2.dp)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    showFav: Boolean = false,
    onPress: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onMorePress: (() -> Unit)? = null
) {
    val isCurrent = PlaybackManager.currentSong?.id == song.id
    val isPlaying = PlaybackManager.isPlaying
    val isFav = PlaybackManager.favourites.contains(song.id)
    val accentColor = Color(android.graphics.Color.parseColor(PlaybackManager.accentColor))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) accentColor.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(
                onClick = onPress,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active indicator bar
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(2.dp))
            )
        }

        // Artwork
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            ArtworkImage(
                artworkUri = song.albumArtUri,
                songColorHex = song.color,
                modifier = Modifier.fillMaxSize(),
                iconSizeSp = 22
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    ActivePlayingIndicator(accentColor)
                }
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                color = if (isCurrent) accentColor else TextPrimaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                color = TextSecondaryColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showFav && isFav) {
                Text("❤️", fontSize = 14.sp)
            }
            Text(
                text = formatTime(song.duration),
                color = TextMutedColor,
                fontSize = 12.sp
            )
            if (onMorePress != null) {
                IconButton(onClick = onMorePress, modifier = Modifier.size(24.dp)) {
                    Text("⋮", color = TextMutedColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 60.sp, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = text,
            color = TextSecondaryColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgModalColor,
            contentColor = TextPrimaryColor,
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(BorderColor, RoundedCornerShape(99.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimaryColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                content()
            }
        }
    }
}

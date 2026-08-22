package com.retro.grooveplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.data.Song
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.theme.*

import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/** Rolls over into an hours field past 60 minutes, so a long mix reads 2:54:07 not 174:07. */
fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}

/** Accent colour as chosen in Settings. */
val accent: Color
    @Composable get() = try {
        Color(android.graphics.Color.parseColor(PlaybackManager.accentColor))
    } catch (e: Exception) {
        RetroPurple
    }

/**
 * Raised surface used for every card in the app. Light mode gets a soft drop
 * shadow; dark mode gets a hairline outline instead, since shadows are invisible
 * against a dark canvas.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    elevation: Dp = 6.dp,
    background: Color = BgCardColor,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (PlaybackManager.isDarkTheme) 0.dp else elevation,
                shape = shape,
                ambientColor = ShadowColor,
                spotColor = ShadowColor
            )
            .clip(shape)
            .background(background)
            .then(
                if (PlaybackManager.isDarkTheme) Modifier.border(1.dp, BorderColor, shape)
                else Modifier
            ),
        content = content
    )
}

/** Small uppercase section heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = TextMutedColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
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
        RetroPurple
    }

    // The tinted placeholder always sits underneath, so a MediaStore album-art URI
    // that fails to resolve degrades to the gradient instead of a blank hole.
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    songBaseColor.copy(alpha = 0.30f),
                    songBaseColor.copy(alpha = 0.12f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = songBaseColor.copy(alpha = 0.9f),
            modifier = Modifier.size((iconSizeSp * 1.1f).dp)
        )

        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album artwork",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ActivePlayingIndicator(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "indicator")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 3f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse), label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp)
    ) {
        Box(Modifier.width(3.dp).height(h1.dp).background(accentColor, RoundedCornerShape(2.dp)))
        Box(Modifier.width(3.dp).height(h2.dp).background(accentColor, RoundedCornerShape(2.dp)))
        Box(Modifier.width(3.dp).height(h3.dp).background(accentColor, RoundedCornerShape(2.dp)))
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
    val accentColor = accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) accentColor.copy(alpha = 0.09f) else Color.Transparent)
            .combinedClickable(
                onClick = onPress,
                onLongClick = onLongPress
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            ArtworkImage(
                artworkUri = song.albumArtUri,
                songColorHex = song.color,
                modifier = Modifier.fillMaxSize(),
                iconSizeSp = 20
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    ActivePlayingIndicator(Color.White)
                }
            }
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.name,
                color = if (isCurrent) accentColor else TextPrimaryColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${song.artist} · ${song.album}",
                color = TextMutedColor,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showFav && isFav) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favourite",
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = formatTime(song.duration),
                color = TextMutedColor,
                fontSize = 12.sp
            )
            if (onMorePress != null) {
                IconButton(onClick = onMorePress, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = TextMutedColor,
                        modifier = Modifier.size(19.dp)
                    )
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
            .padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 32.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = text,
            color = TextSecondaryColor,
            fontSize = 14.sp,
            lineHeight = 21.sp,
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
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(top = 14.dp, bottom = 6.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(TextMutedColor.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimaryColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                )
                content()
            }
        }
    }
}

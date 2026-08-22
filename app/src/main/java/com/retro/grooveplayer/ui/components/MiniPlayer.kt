package com.retro.grooveplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.theme.*

@Composable
fun MiniPlayer(onMiniPlayerClick: () -> Unit) {
    val song = PlaybackManager.currentSong ?: return
    val isPlaying = PlaybackManager.isPlaying
    val position = PlaybackManager.position
    val duration = PlaybackManager.duration
    val accentColor = accent

    val progress = if (duration > 0) position.toFloat() / duration else 0f

    // Floating card rather than a bar welded to the nav - reads as a live object
    // sitting above the page.
    SoftCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        corner = 20.dp,
        elevation = 12.dp,
        background = BgElevatedColor
    ) {
        Column(modifier = Modifier.clickable(onClick = onMiniPlayerClick)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkImage(
                        artworkUri = song.albumArtUri,
                        songColorHex = song.color,
                        modifier = Modifier.fillMaxSize(),
                        iconSizeSp = 18
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.name,
                        color = TextPrimaryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = TextMutedColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { PlaybackManager.prevSong() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentColor)
                            .clickable { PlaybackManager.togglePlay() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { PlaybackManager.nextSong() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = TextSecondaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Hairline progress at the foot of the card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 10.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BgSunkenColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

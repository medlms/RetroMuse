package com.retro.grooveplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val accentColor = Color(android.graphics.Color.parseColor(PlaybackManager.accentColor))

    val progress = if (duration > 0) position.toFloat() / duration else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgModalColor.copy(alpha = 0.95f)) // Dynamic theme-relative background
            .clickable(onClick = onMiniPlayerClick)
    ) {
        // Top progress line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(BorderColor.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accentColor, Color(0xFFEC4899))
                        )
                    )
            )
        }

        // Inner row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                ArtworkImage(
                    artworkUri = song.albumArtUri,
                    songColorHex = song.color,
                    modifier = Modifier.fillMaxSize(),
                    iconSizeSp = 20
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    color = TextPrimaryColor,
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

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Prev
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { PlaybackManager.prevSong() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏮", color = TextPrimaryColor, fontSize = 18.sp)
                }

                // Play/Pause
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor)
                        .clickable { PlaybackManager.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                // Next
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { PlaybackManager.nextSong() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏭", color = TextPrimaryColor, fontSize = 18.sp)
                }
            }
        }
    }
}

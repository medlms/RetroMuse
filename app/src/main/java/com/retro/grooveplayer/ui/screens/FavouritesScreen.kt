package com.retro.grooveplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.components.EmptyState
import com.retro.grooveplayer.ui.components.SongItem
import com.retro.grooveplayer.ui.theme.TextPrimaryColor
import com.retro.grooveplayer.ui.theme.TextSecondaryColor

@Composable
fun FavouritesScreen(onSongSelect: () -> Unit) {
    val favourites = PlaybackManager.favourites
    val songs = PlaybackManager.songs
    val favSongs = songs.filter { favourites.contains(it.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080810))
    ) {
        // Header with gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0A18), Color(0xFF080810))
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "Favourites",
                    color = TextPrimaryColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${favSongs.size} songs",
                    color = TextSecondaryColor,
                    fontSize = 13.sp
                )
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (favSongs.isEmpty()) {
                item {
                    EmptyState(
                        icon = "❤️",
                        text = "No Favourites Yet\nTap the ♡ heart icon on any song to save it here"
                    )
                }
            } else {
                items(favSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        showFav = true,
                        onPress = {
                            PlaybackManager.playSong(song, favSongs)
                            onSongSelect()
                        }
                    )
                }
            }
        }
    }
}

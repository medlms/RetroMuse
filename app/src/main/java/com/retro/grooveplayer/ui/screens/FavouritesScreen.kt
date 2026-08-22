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
import androidx.compose.ui.text.font.FontFamily
import com.retro.grooveplayer.ui.theme.*

@Composable
fun FavouritesScreen(onSongSelect: () -> Unit) {
    val favourites = PlaybackManager.favourites
    val songs = PlaybackManager.songs
    val favSongs = songs.filter { favourites.contains(it.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)
        ) {
            Column {
                Text(
                    text = "Favourites",
                    color = TextPrimaryColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp
                )
                Text(
                    text = if (favSongs.size == 1) "1 track" else "${favSongs.size} tracks",
                    color = TextMutedColor,
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

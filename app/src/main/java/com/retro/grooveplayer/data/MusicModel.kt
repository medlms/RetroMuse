package com.retro.grooveplayer.data

data class Song(
    val id: String,
    val uri: String,
    val name: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val size: Long,
    val folder: String,
    val mimeType: String,
    val addedAt: Long,
    val color: String, // Hex color string, e.g. "#A855F7"
    val albumArtUri: String? = null
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList()
)

data class Album(
    val name: String,
    val songs: List<Song>,
    val color: String
)

data class Artist(
    val name: String,
    val songs: List<Song>,
    val color: String
)

data class Folder(
    val name: String,
    val songs: List<Song>
)

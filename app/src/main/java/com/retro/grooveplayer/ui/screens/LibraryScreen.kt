package com.retro.grooveplayer.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.retro.grooveplayer.data.*
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.components.BottomModal
import com.retro.grooveplayer.ui.components.EmptyState
import com.retro.grooveplayer.ui.components.SongItem
import com.retro.grooveplayer.ui.theme.*

private val TABS = listOf("Songs", "Albums", "Artists", "Playlists", "Folders")
private val SORT_OPTIONS = listOf("Title", "Artist", "Album", "Duration", "Date Added")

@Composable
fun LibraryScreen(onSongSelect: () -> Unit) {
    val context = LocalContext.current
    val accentColor = Color(android.graphics.Color.parseColor(PlaybackManager.accentColor))

    var activeTab by remember { mutableStateOf("Songs") }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Title") }

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextSong by remember { mutableStateOf<Song?>(null) }
    var showEditTagDialog by remember { mutableStateOf(false) }
    
    // Playlists
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showAddToPlaylistModal by remember { mutableStateOf(false) }

    // Activity result launchers
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { PlaybackManager.importAudioUri(it) }
            Toast.makeText(context, "Imported ${uris.size} audio file(s).", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            PlaybackManager.scanDeviceLibrary()
            Toast.makeText(context, "Scanning storage...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            PlaybackManager.scanDeviceLibrary()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // Dynamic grouping
    val sortedSongs = remember(PlaybackManager.songs, sortBy, searchQuery) {
        val list = PlaybackManager.songs.toMutableList()
        when (sortBy) {
            "Title" -> list.sortBy { it.name.lowercase() }
            "Artist" -> list.sortBy { it.artist.lowercase() }
            "Album" -> list.sortBy { it.album.lowercase() }
            "Duration" -> list.sortBy { it.duration }
            "Date Added" -> list.sortByDescending { it.addedAt }
        }
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            list.filter {
                it.name.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        } else {
            list
        }
    }

    val albums = remember(sortedSongs) {
        sortedSongs.groupBy { it.album }
            .map { (name, list) -> Album(name, list, list.firstOrNull()?.color ?: "#a855f7") }
    }

    val artists = remember(sortedSongs) {
        sortedSongs.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list, list.firstOrNull()?.color ?: "#a855f7") }
    }

    val folders = remember(sortedSongs) {
        sortedSongs.groupBy { it.folder }
            .map { (name, list) -> Folder(name, list) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
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
                            text = "Audio Workspace",
                            color = RetroCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Search trigger
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Text("🔍", fontSize = 20.sp)
                        }
                        // Sort trigger
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Text("⬇️", fontSize = 20.sp)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(BgModalColor)
                            ) {
                                SORT_OPTIONS.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, color = TextPrimaryColor) },
                                        onClick = {
                                            sortBy = opt
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search field
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        placeholder = { Text("Search songs, artists, albums…", color = TextMutedColor, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryColor,
                            unfocusedTextColor = TextPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = BgCardColor,
                            unfocusedContainerColor = BgCardColor
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }



                // Tabs row
                ScrollableTabRow(
                    selectedTabIndex = TABS.indexOf(activeTab),
                    containerColor = Color.Transparent,
                    contentColor = accentColor,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[TABS.indexOf(activeTab)]),
                            color = accentColor
                        )
                    },
                    divider = {}
                ) {
                    TABS.forEach { tab ->
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            text = { Text(tab, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                            selectedContentColor = accentColor,
                            unselectedContentColor = TextSecondaryColor
                        )
                    }
                }
            }
        }

        // Contents
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                "Songs" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        if (sortedSongs.isEmpty()) {
                            item {
                                EmptyState("🎶", "No songs yet. Pick files or scan library.")
                            }
                        } else {
                            items(sortedSongs, key = { it.id }) { song ->
                                SongItem(
                                    song = song,
                                    showFav = true,
                                    onPress = {
                                        PlaybackManager.playSong(song, sortedSongs)
                                        onSongSelect()
                                    },
                                    onLongPress = { contextSong = song },
                                    onMorePress = { contextSong = song }
                                )
                            }
                        }
                    }
                }

                "Albums" -> {
                    if (albums.isEmpty()) {
                        EmptyState("💿", "No albums found.")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albums) { album ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (album.songs.isNotEmpty()) {
                                                PlaybackManager.playSong(album.songs[0], album.songs)
                                                onSongSelect()
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.retro.grooveplayer.ui.components.ArtworkImage(
                                            artworkUri = album.songs.firstOrNull()?.albumArtUri,
                                            songColorHex = album.color,
                                            modifier = Modifier.fillMaxSize(),
                                            iconSizeSp = 38
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = album.name,
                                        color = TextPrimaryColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${album.songs.size} songs",
                                        color = TextSecondaryColor,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                "Artists" -> {
                    if (artists.isEmpty()) {
                        EmptyState("🎤", "No artists found.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(artists) { artist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (artist.songs.isNotEmpty()) {
                                                PlaybackManager.playSong(artist.songs[0], artist.songs)
                                                onSongSelect()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    val artistColor = Color(android.graphics.Color.parseColor(artist.color))
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(artistColor, Color(0xFF1A0533))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🎤", fontSize = 24.sp)
                                    }
                                    Column {
                                        Text(
                                            text = artist.name,
                                            color = TextPrimaryColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${artist.songs.size} songs",
                                            color = TextSecondaryColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "Playlists" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Create Playlist Button
                        Button(
                            onClick = { showNewPlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .border(1.dp, accentColor, RoundedCornerShape(99.dp)),
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text("+ New Playlist", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Playlists List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            // 1. Recently Played
                            val rpIds = PlaybackManager.recentlyPlayed
                            val rpSongs = rpIds.mapNotNull { id -> PlaybackManager.songs.find { it.id == id } }
                            
                            // 2. Most Played
                            val mpMap = PlaybackManager.mostPlayed
                            val mpSongs = PlaybackManager.songs
                                .filter { mpMap.containsKey(it.id) }
                                .sortedByDescending { mpMap[it.id] ?: 0 }
                            
                            // 3. Recently Added
                            val raSongs = PlaybackManager.songs
                                .sortedByDescending { it.addedAt }
                                .take(50)

                            // Render Recently Played
                            item {
                                SmartPlaylistRow(
                                    title = "Recently Played",
                                    subtitle = "${rpSongs.size} tracks",
                                    icon = "🕒",
                                    color = RetroCyan,
                                    onClick = {
                                        if (rpSongs.isNotEmpty()) {
                                            PlaybackManager.playSong(rpSongs[0], rpSongs)
                                            onSongSelect()
                                        } else {
                                            Toast.makeText(context, "No tracks played recently.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            
                            // Render Most Played
                            item {
                                SmartPlaylistRow(
                                    title = "Most Played",
                                    subtitle = "${mpSongs.size} tracks",
                                    icon = "🔥",
                                    color = RetroPink,
                                    onClick = {
                                        if (mpSongs.isNotEmpty()) {
                                            PlaybackManager.playSong(mpSongs[0], mpSongs)
                                            onSongSelect()
                                        } else {
                                            Toast.makeText(context, "Start playing music to build stats!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            
                            // Render Recently Added
                            item {
                                SmartPlaylistRow(
                                    title = "Recently Added",
                                    subtitle = "${raSongs.size} tracks",
                                    icon = "✨",
                                    color = RetroGold,
                                    onClick = {
                                        if (raSongs.isNotEmpty()) {
                                            PlaybackManager.playSong(raSongs[0], raSongs)
                                            onSongSelect()
                                        } else {
                                            Toast.makeText(context, "No local tracks found.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            // Divider
                            item {
                                Divider(
                                    color = BorderColor,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }

                            val pls = PlaybackManager.playlists
                            if (pls.isEmpty()) {
                                item {
                                    EmptyState("📋", "No custom playlists yet. Create one!")
                                }
                            } else {
                                items(pls) { pl ->
                                    val plSongs = pl.songIds.mapNotNull { id -> PlaybackManager.songs.find { it.id == id } }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (plSongs.isNotEmpty()) {
                                                    PlaybackManager.playSong(plSongs[0], plSongs)
                                                    onSongSelect()
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(accentColor, Color(0xFF1A0533))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("📋", fontSize = 24.sp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                color = TextPrimaryColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${plSongs.size} songs",
                                                color = TextSecondaryColor,
                                                fontSize = 12.sp
                                            )
                                        }
                                        IconButton(onClick = { PlaybackManager.removePlaylist(pl.id) }) {
                                            Text("🗑️", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Folders" -> {
                    if (folders.isEmpty()) {
                        EmptyState("📁", "No folders found.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(folders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (folder.songs.isNotEmpty()) {
                                                PlaybackManager.playSong(folder.songs[0], folder.songs)
                                                onSongSelect()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BgCardColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📁", fontSize = 24.sp)
                                    }
                                    Column {
                                        Text(
                                            text = folder.name,
                                            color = TextPrimaryColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${folder.songs.size} songs",
                                            color = TextSecondaryColor,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Context Menu Bottom Sheet
    contextSong?.let { song ->
        val isFav = PlaybackManager.favourites.contains(song.id)
        BottomModal(
            visible = true,
            onDismissRequest = { contextSong = null },
            title = song.name
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Play Now
                TextButton(
                    onClick = {
                        PlaybackManager.playSong(song, PlaybackManager.songs)
                        contextSong = null
                        onSongSelect()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("▶  Play Now", color = TextPrimaryColor, fontSize = 16.sp)
                    }
                }

                // Favourite Toggle
                TextButton(
                    onClick = {
                        PlaybackManager.toggleFavourite(song.id)
                        contextSong = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(
                            text = if (isFav) "❤️  Remove Favourite" else "♡  Add to Favourite",
                            color = TextPrimaryColor,
                            fontSize = 16.sp
                        )
                    }
                }

                // Add to Playlist
                TextButton(
                    onClick = {
                        showAddToPlaylistModal = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("➕  Add to Playlist", color = TextPrimaryColor, fontSize = 16.sp)
                    }
                }

                // Edit Tag Metadata
                TextButton(
                    onClick = {
                        showEditTagDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("✏️  Edit Tag Metadata", color = TextPrimaryColor, fontSize = 16.sp)
                    }
                }

                // Song Info
                TextButton(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Artist: ${song.artist}\nAlbum: ${song.album}\nFolder: ${song.folder}",
                            Toast.LENGTH_LONG
                        ).show()
                        contextSong = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("ℹ️  Song Info", color = TextPrimaryColor, fontSize = 16.sp)
                    }
                }

                // Remove
                TextButton(
                    onClick = {
                        PlaybackManager.removeSong(song.id)
                        contextSong = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("🗑️  Remove", color = DangerColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Add to Playlist Selection Modal
    if (showAddToPlaylistModal && contextSong != null) {
        val song = contextSong!!
        BottomModal(
            visible = showAddToPlaylistModal,
            onDismissRequest = { showAddToPlaylistModal = false },
            title = "Add to Playlist"
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlaybackManager.playlists) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlaybackManager.addSongToPlaylist(pl.id, song.id)
                                showAddToPlaylistModal = false
                                contextSong = null
                                Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📋  ", fontSize = 18.sp)
                        Text(pl.name, color = TextPrimaryColor, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // Create Playlist dialog
    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            containerColor = BgModalColor,
            title = { Text("New Playlist", color = TextPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name…", color = TextMutedColor) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimaryColor,
                        unfocusedTextColor = TextPrimaryColor,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = BorderColor
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.trim().isNotEmpty()) {
                            PlaybackManager.createPlaylist(newPlaylistName.trim())
                            newPlaylistName = ""
                            showNewPlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) {
                    Text("Cancel", color = TextSecondaryColor)
                }
            }
        )
    }

    // Edit Tag Metadata Dialog
    if (showEditTagDialog && contextSong != null) {
        val song = contextSong!!
        var titleText by remember(song) { mutableStateOf(song.name) }
        var artistText by remember(song) { mutableStateOf(song.artist) }
        var albumText by remember(song) { mutableStateOf(song.album) }

        AlertDialog(
            onDismissRequest = { showEditTagDialog = false },
            containerColor = BgModalColor,
            title = { Text("Edit Metadata Tags ✏️", color = TextPrimaryColor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Title", color = TextMutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryColor,
                            unfocusedTextColor = TextPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    OutlinedTextField(
                        value = artistText,
                        onValueChange = { artistText = it },
                        label = { Text("Artist", color = TextMutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryColor,
                            unfocusedTextColor = TextPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    OutlinedTextField(
                        value = albumText,
                        onValueChange = { albumText = it },
                        label = { Text("Album", color = TextMutedColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryColor,
                            unfocusedTextColor = TextPrimaryColor,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PlaybackManager.updateSongMetadata(
                            song.id,
                            titleText.trim(),
                            artistText.trim(),
                            albumText.trim()
                        )
                        showEditTagDialog = false
                        contextSong = null
                        Toast.makeText(context, "Metadata Updated!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Changes", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTagDialog = false }) {
                    Text("Cancel", color = TextSecondaryColor)
                }
            }
        )
    }
}

@Composable
fun SmartPlaylistRow(
    title: String,
    subtitle: String,
    icon: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondaryColor,
                fontSize = 12.sp
            )
        }
        Text("›", color = TextMutedColor, fontSize = 24.sp)
    }
}

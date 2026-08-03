package com.retro.grooveplayer.playback

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.retro.grooveplayer.data.Playlist
import com.retro.grooveplayer.data.Song
import com.retro.grooveplayer.data.StorageManager
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.pow
import kotlin.random.Random

enum class RepeatMode { NONE, ALL, ONE }

object PlaybackManager {
    lateinit var exoPlayer: ExoPlayer
    private lateinit var storageManager: StorageManager
    private lateinit var context: Context

    // State observable by Compose
    var songs by mutableStateOf<List<Song>>(emptyList())
    var queue by mutableStateOf<List<Int>>(emptyList())
    var queueIndex by mutableStateOf(-1)
    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    
    var volume by mutableStateOf(1.0f)
    var speed by mutableStateOf(1.0f)
    var shuffleOn by mutableStateOf(false)
    var repeatMode by mutableStateOf(RepeatMode.NONE)
    var favourites by mutableStateOf<List<String>>(emptyList())
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    
    var accentColor by mutableStateOf("#a855f7")
    var eqPreset by mutableStateOf("Flat")

    // Sleep Timer
    var sleepTimerEndTime by mutableStateOf<Long?>(null)
    var sleepTimerLabel by mutableStateOf("")
    var sleepTimerCountdown by mutableStateOf("")
    private var sleepTimerJob: Job? = null
    var isSleepTimerEndOfSong by mutableStateOf(false)

    // Start Timer
    var startTimerEndTime by mutableStateOf<Long?>(null)
    var startTimerLabel by mutableStateOf("")
    var startTimerCountdown by mutableStateOf("")
    private var startTimerJob: Job? = null

    // Audio Effects
    var fxSpeed by mutableStateOf(1.0f)
    var fxPitch by mutableStateOf(0) // Semitones (-5 to +5)
    var fxReverb by mutableStateOf(40) // 0-100%
    var fxBass by mutableStateOf(0) // 0-100%

    // Native Effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = -1

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    val ACCENT_PALETTE = listOf("#a855f7", "#3b82f6", "#ec4899", "#10b981", "#f59e0b", "#ef4444")
    
    val EQ_PRESETS = mapOf(
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Bass" to listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0),
        "Treble" to listOf(0, 0, 0, 0, 0, 2, 3, 4, 5, 6),
        "Pop" to listOf(-1, 2, 4, 4, 3, 1, -1, -2, -2, -2),
        "Rock" to listOf(4, 3, 2, 0, -1, 2, 3, 4, 4, 4),
        "Jazz" to listOf(3, 2, 1, 2, -2, -2, 0, 1, 2, 3),
        "Classical" to listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4),
        "Vocal" to listOf(-2, -1, 0, 2, 4, 4, 3, 2, 1, 0),
        "Club" to listOf(0, 0, 4, 5, 5, 5, 4, 2, 0, 0),
        "Dance" to listOf(5, 4, 2, 0, 0, -2, -3, -3, 0, 0)
    )

    fun init(ctx: Context) {
        context = ctx.applicationContext
        storageManager = StorageManager(context)
        
        // Load settings
        favourites = storageManager.getFavourites()
        playlists = storageManager.getPlaylists()
        songs = storageManager.getCachedSongs()
        accentColor = storageManager.getAccentColor()
        eqPreset = storageManager.getEqPreset()
        speed = storageManager.getSpeed()
        fxSpeed = speed
        fxBass = if (storageManager.getBassBoost()) 80 else 0
        
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            volume = PlaybackManager.volume
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                    PlaybackManager.isPlaying = isPlayingChanged
                    if (isPlayingChanged) {
                        PlaybackManager.startProgressTracker()
                        try {
                            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        PlaybackManager.stopProgressTracker()
                        try {
                            context.startService(Intent(context, PlaybackService::class.java))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        PlaybackManager.handleSongEnd()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId > 0 && audioSessionId != PlaybackManager.currentSessionId) {
                        PlaybackManager.currentSessionId = audioSessionId
                        PlaybackManager.setupNativeAudioEffects(audioSessionId)
                    }
                }
            })
        }
    }

    private fun setupNativeAudioEffects(sessionId: Int) {
        if (sessionId <= 0) return
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()

            equalizer = Equalizer(0, sessionId).apply { enabled = true }
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, sessionId).apply { enabled = true }

            applyEqPreset(eqPreset)
            applyBassBoost(fxBass)
            applyVirtualizer(storageManager.getSurround())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var customEqBands by mutableStateOf(listOf(0, 0, 0, 0, 0)) // 5 bands in dB (-6 to +6)

    fun setCustomBandLevel(bandIndex: Int, db: Int) {
        val list = customEqBands.toMutableList()
        if (bandIndex in list.indices) {
            list[bandIndex] = db
            customEqBands = list
            eqPreset = "Custom"
            
            val eq = equalizer ?: return
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]
            val mB = (db * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())

            if (bandIndex < numBands) {
                try {
                    eq.setBandLevel(bandIndex.toShort(), mB.toShort())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateSongMetadata(songId: String, newName: String, newArtist: String, newAlbum: String) {
        songs = songs.map { song ->
            if (song.id == songId) {
                song.copy(name = newName, artist = newArtist, album = newAlbum)
            } else song
        }
        storageManager.saveCachedSongs(songs)
        if (currentSong?.id == songId) {
            currentSong = currentSong?.copy(name = newName, artist = newArtist, album = newAlbum)
        }
    }

    fun getLyricsForCurrentSong(): List<com.retro.grooveplayer.data.LyricLine> {
        val song = currentSong ?: return emptyList()
        // Provide sample synchronized lyrics for demonstration/embedded tracks
        return listOf(
            com.retro.grooveplayer.data.LyricLine(0L, "♪ (${song.name} - ${song.artist}) ♪"),
            com.retro.grooveplayer.data.LyricLine(3000L, "Intro music playing..."),
            com.retro.grooveplayer.data.LyricLine(12000L, "Feeling the rhythm in the night"),
            com.retro.grooveplayer.data.LyricLine(20000L, "Groove Player taking taking flight"),
            com.retro.grooveplayer.data.LyricLine(28000L, "Turn the bass up, let it flow"),
            com.retro.grooveplayer.data.LyricLine(36000L, "Lost inside the audio glow"),
            com.retro.grooveplayer.data.LyricLine(45000L, "Every beat, every sound"),
            com.retro.grooveplayer.data.LyricLine(54000L, "Best player in the town"),
            com.retro.grooveplayer.data.LyricLine(65000L, "♪ Outro fading smoothly... ♪")
        )
    }

    fun applyEqPreset(preset: String) {
        eqPreset = preset
        storageManager.saveEqPreset(preset)
        val eq = equalizer ?: return
        val bands = EQ_PRESETS[preset] ?: EQ_PRESETS["Flat"] ?: return
        
        val numBands = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]

        for (band in 0 until numBands) {
            val centerFreq = eq.getCenterFreq(band.toShort()) / 1000 // In Hz
            val closestIdx = findClosestBandIndex(centerFreq)
            val db = bands.getOrElse(closestIdx) { 0 }
            val mB = (db * 100).toInt().coerceIn(minLevel.toInt(), maxLevel.toInt())
            try {
                eq.setBandLevel(band.toShort(), mB.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun findClosestBandIndex(freqHz: Int): Int {
        val targets = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        var minDiff = Int.MAX_VALUE
        var index = 0
        for (i in targets.indices) {
            val diff = kotlin.math.abs(targets[i] - freqHz)
            if (diff < minDiff) {
                minDiff = diff
                index = i
            }
        }
        return index
    }

    fun applyBassBoost(percent: Int) {
        fxBass = percent
        val boost = bassBoost ?: return
        try {
            if (percent > 0) {
                boost.enabled = true
                boost.setStrength((percent * 10).toShort()) // 0 - 1000 range
            } else {
                boost.enabled = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyVirtualizer(enabled: Boolean) {
        val virt = virtualizer ?: return
        try {
            if (enabled) {
                virt.enabled = true
                virt.setStrength(1000.toShort())
            } else {
                virt.enabled = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Playback control
    fun playSong(song: Song, songList: List<Song> = songs) {
        songs = songList
        storageManager.saveCachedSongs(songs)
        
        currentSong = song
        duration = song.duration
        position = 0L

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.uri))
            .setMediaId(song.id)
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        updatePlaybackParameters()
        exoPlayer.play()

        buildQueue(songs, song.id)
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(posMs: Long) {
        exoPlayer.seekTo(posMs)
        position = posMs
    }

    fun setPlayerVolume(vol: Float) {
        volume = vol
        exoPlayer.volume = vol
    }

    fun updatePlaybackParameters() {
        val pitchFactor = 2.0.pow(fxPitch.toDouble() / 12.0).toFloat()
        val rate = fxSpeed * pitchFactor
        val clampedRate = rate.coerceIn(0.5f, 2.0f)
        
        // If pitch is 0, we can use pitch correction. Otherwise let pitch change naturally (which changes speed).
        val params = PlaybackParameters(clampedRate, if (fxPitch == 0) 1.0f else pitchFactor)
        exoPlayer.playbackParameters = params
    }

    fun nextSong() {
        if (queue.isEmpty()) return
        val nextIdx = (queueIndex + 1) % queue.size
        queueIndex = nextIdx
        val songIdx = queue[nextIdx]
        songs.getOrNull(songIdx)?.let { playSong(it, songs) }
    }

    fun prevSong() {
        if (position > 3000L) {
            seekTo(0L)
            return
        }
        if (queue.isEmpty()) return
        val prevIdx = (queueIndex - 1 + queue.size) % queue.size
        queueIndex = prevIdx
        val songIdx = queue[prevIdx]
        songs.getOrNull(songIdx)?.let { playSong(it, songs) }
    }

    fun toggleShuffle() {
        shuffleOn = !shuffleOn
        currentSong?.let { buildQueue(songs, it.id) }
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    private fun buildQueue(songList: List<Song>, startId: String? = null) {
        val indices = songList.indices.toList()
        queue = if (shuffleOn) {
            indices.shuffled()
        } else {
            indices
        }
        startId?.let { id ->
            val songIdx = songList.indexOfFirst { it.id == id }
            val qi = queue.indexOf(songIdx)
            queueIndex = if (qi >= 0) qi else 0
        }
    }

    private fun handleSongEnd() {
        if (repeatMode == RepeatMode.ONE) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            return
        }
        if (isSleepTimerEndOfSong) {
            clearSleepTimer()
            exoPlayer.pause()
            return
        }
        val nextIdx = queueIndex + 1
        if (nextIdx < queue.size) {
            queueIndex = nextIdx
            songs.getOrNull(queue[nextIdx])?.let { playSong(it, songs) }
        } else if (repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
            queueIndex = 0
            songs.getOrNull(queue[0])?.let { playSong(it, songs) }
        } else {
            exoPlayer.pause()
        }
    }

    // Favourites & Playlists
    fun toggleFavourite(songId: String) {
        val list = favourites.toMutableList()
        if (list.contains(songId)) {
            list.remove(songId)
        } else {
            list.add(songId)
        }
        favourites = list
        storageManager.saveFavourites(list)
    }

    fun createPlaylist(name: String) {
        val pl = Playlist(id = "pl_" + System.currentTimeMillis(), name = name)
        playlists = playlists + pl
        storageManager.savePlaylists(playlists)
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) {
                if (pl.songIds.contains(songId)) pl
                else pl.copy(songIds = pl.songIds + songId)
            } else pl
        }
        storageManager.savePlaylists(playlists)
    }

    fun removePlaylist(playlistId: String) {
        playlists = playlists.filter { it.id != playlistId }
        storageManager.savePlaylists(playlists)
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(songIds = pl.songIds.filter { it != songId })
            } else pl
        }
        storageManager.savePlaylists(playlists)
    }

    fun removeSong(songId: String) {
        songs = songs.filter { it.id != songId }
        storageManager.saveCachedSongs(songs)
        if (currentSong?.id == songId) {
            exoPlayer.stop()
            currentSong = null
            isPlaying = false
        }
    }

    fun changeAccentColor(color: String) {
        accentColor = color
        storageManager.saveAccentColor(color)
    }

    fun clearLibrary() {
        songs = emptyList()
        storageManager.saveCachedSongs(songs)
        exoPlayer.stop()
        currentSong = null
        isPlaying = false
    }

    // Timers
    fun startSleepTimer(minutes: Int) {
        clearSleepTimer()
        if (minutes == 0) {
            isSleepTimerEndOfSong = true
            sleepTimerLabel = "After song ends"
            sleepTimerCountdown = "After song ends"
            return
        }
        val durationMs = minutes * 60 * 1000L
        sleepTimerEndTime = System.currentTimeMillis() + durationMs
        sleepTimerLabel = "$minutes min"
        
        sleepTimerJob = coroutineScope.launch {
            while (true) {
                val remaining = (sleepTimerEndTime ?: 0L) - System.currentTimeMillis()
                if (remaining <= 0) {
                    exoPlayer.pause()
                    clearSleepTimer()
                    break
                }
                val m = remaining / 60000
                val s = (remaining % 60000) / 1000
                sleepTimerCountdown = String.format("%d:%02d", m, s)
                delay(1000)
            }
        }
    }

    fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndTime = null
        sleepTimerLabel = ""
        sleepTimerCountdown = ""
        isSleepTimerEndOfSong = false
    }

    fun startStartTimer(minutes: Int) {
        clearStartTimer()
        val durationMs = minutes * 60 * 1000L
        startTimerEndTime = System.currentTimeMillis() + durationMs
        startTimerLabel = "$minutes min"

        startTimerJob = coroutineScope.launch {
            while (true) {
                val remaining = (startTimerEndTime ?: 0L) - System.currentTimeMillis()
                if (remaining <= 0) {
                    if (songs.isNotEmpty()) {
                        playSong(songs[0], songs)
                    }
                    clearStartTimer()
                    break
                }
                val m = remaining / 60000
                val s = (remaining % 60000) / 1000
                startTimerCountdown = String.format("%d:%02d", m, s)
                delay(1000)
            }
        }
    }

    fun clearStartTimer() {
        startTimerJob?.cancel()
        startTimerJob = null
        startTimerEndTime = null
        startTimerLabel = ""
        startTimerCountdown = ""
    }

    // Media Scanning & File Picker Utils
    fun scanDeviceLibrary() {
        val list = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol)
                val name = cursor.getString(nameCol).replace(Regex("\\.[^/.]+$"), "") // Strip extension
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durCol)
                val size = cursor.getLong(sizeCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()
                
                val folder = try {
                    File(path).parentFile?.name ?: "Music"
                } catch (e: Exception) {
                    "Music"
                }

                list.add(Song(
                    id = "ml_$id",
                    uri = contentUri,
                    name = name,
                    artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                    album = if (album == "<unknown>") "Unknown Album" else album,
                    duration = duration,
                    size = size,
                    folder = folder,
                    mimeType = "audio/mpeg",
                    addedAt = System.currentTimeMillis(),
                    color = ACCENT_PALETTE[Random.nextInt(ACCENT_PALETTE.size)],
                    albumArtUri = albumArtUri
                ))
            }
        }
        
        addSongs(list)
    }

    fun addSongs(newSongs: List<Song>) {
        val currentIds = songs.map { it.id }.toSet()
        val unique = newSongs.filter { !currentIds.contains(it.id) }
        songs = songs + unique
        storageManager.saveCachedSongs(songs)
    }

    fun stopPlayback() {
        try {
            exoPlayer.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentSong = null
        isPlaying = false
        position = 0L
        stopProgressTracker()
    }

    fun importAudioUri(uri: Uri) {
        try {
            var name = "Unknown"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) name = cursor.getString(nameIdx).replace(Regex("\\.[^/.]+$"), "")
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }

            var artist = "Local Import"
            var album = "Imported Files"
            var duration = 0L

            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val rTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val rArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val rAlbum = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val rDur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)

                if (!rTitle.isNullOrBlank()) name = rTitle
                if (!rArtist.isNullOrBlank()) artist = rArtist
                if (!rAlbum.isNullOrBlank()) album = rAlbum
                if (!rDur.isNullOrBlank()) duration = rDur.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val newSong = Song(
                id = "picked_" + System.currentTimeMillis() + "_" + Random.nextInt(1000),
                uri = uri.toString(),
                name = name,
                artist = artist,
                album = album,
                duration = duration,
                size = size,
                folder = "Local Files",
                mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg",
                addedAt = System.currentTimeMillis(),
                color = ACCENT_PALETTE[Random.nextInt(ACCENT_PALETTE.size)],
                albumArtUri = uri.toString()
            )
            addSongs(listOf(newSong))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Helper progress tracker coroutine
    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (true) {
                position = exoPlayer.currentPosition
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}

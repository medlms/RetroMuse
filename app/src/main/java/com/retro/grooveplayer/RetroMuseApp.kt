package com.retro.grooveplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.retro.grooveplayer.data.AudioCoverFetcher
import com.retro.grooveplayer.data.AudioCoverKeyer
import com.retro.grooveplayer.data.CrashLog
import com.retro.grooveplayer.playback.PlaybackManager

/**
 * Binds playback initialisation to the process rather than to MainActivity.
 *
 * The widget, the start-timer alarm and a START_STICKY service restart can all wake the
 * app without any activity ever being created. Previously those paths reached a
 * PlaybackManager whose ExoPlayer had never been assigned, which crashed the widget and
 * made the start timer silently do nothing.
 */
class RetroMuseApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Installed first, so a failure during init is itself recorded.
        CrashLog.install(this)
        try {
            PlaybackManager.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Registers the embedded-artwork fetcher so every AsyncImage in the app can read
     * cover art straight out of the audio file's tags.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(AudioCoverFetcher.Factory())
                add(AudioCoverKeyer())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("cover_art"))
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

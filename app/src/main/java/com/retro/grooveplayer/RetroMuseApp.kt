package com.retro.grooveplayer

import android.app.Application
import com.retro.grooveplayer.playback.PlaybackManager

/**
 * Binds playback initialisation to the process rather than to MainActivity.
 *
 * The widget, the start-timer alarm and a START_STICKY service restart can all wake the
 * app without any activity ever being created. Previously those paths reached a
 * PlaybackManager whose ExoPlayer had never been assigned, which crashed the widget and
 * made the start timer silently do nothing.
 */
class RetroMuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            PlaybackManager.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

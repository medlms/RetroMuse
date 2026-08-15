package com.retro.grooveplayer.playback

import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        try {
            mediaSession = MediaSession.Builder(this, PlaybackManager.exoPlayer).build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_AUTO_START") {
            PlaybackManager.playFirstSong()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && (!player.playWhenReady || player.mediaItemCount == 0)) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            mediaSession?.run {
                release()
                mediaSession = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}

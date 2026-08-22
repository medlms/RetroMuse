package com.retro.grooveplayer.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.retro.grooveplayer.MainActivity
import com.retro.grooveplayer.R
import kotlinx.coroutines.*

/**
 * Hosts the media session and owns the playback notification.
 *
 * Media3's own notification provider never posts here - the app drives the shared
 * ExoPlayer directly rather than through a MediaController, so its notification
 * manager stays dormant. We therefore build the MediaStyle notification ourselves and
 * keep it in step with the player, which is what gives the compact player in the
 * shade, on the lock screen, and on the always-on display.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var artworkJob: Job? = null
    private var artworkSongId: String? = null
    private var artwork: Bitmap? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNotification()
        override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) =
            refreshNotification()

        override fun onPlaybackStateChanged(state: Int) = refreshNotification()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            val sessionActivity = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession = MediaSession.Builder(this, PlaybackManager.exoPlayer)
                .setSessionActivity(sessionActivity)
                .build()
            PlaybackManager.exoPlayer.addListener(playerListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The system kills the process if startForegroundService() is not followed by
        // startForeground() within ~5s, so claim the slot before doing anything else.
        startForegroundWith(buildNotification())

        when (intent?.action) {
            ACTION_TOGGLE -> PlaybackManager.togglePlay()
            ACTION_NEXT -> PlaybackManager.nextSong()
            ACTION_PREV -> PlaybackManager.prevSong()
            "ACTION_AUTO_START" -> PlaybackManager.playFirstSong()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Playback",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            setShowBadge(false)
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): android.app.Notification {
        val song = PlaybackManager.currentSong
        val isPlaying = PlaybackManager.isPlaying

        ensureArtwork(song?.id, song?.uri)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(song?.name ?: "RetroMuse")
            .setContentText(song?.artist ?: "Ready to play")
            .setSubText(song?.album)
            .setLargeIcon(artwork)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Without PUBLIC the shade hides the whole thing behind the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                actionIntent(ACTION_PREV, 1)
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                actionIntent(ACTION_TOGGLE, 2)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                actionIntent(ACTION_NEXT, 3)
            )

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionCompatToken?.let { style.setMediaSession(it) }
        builder.setStyle(style)

        return builder.build()
    }

    private fun startForegroundWith(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied - playback continues without the shade player.
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Pulls the embedded cover art off the track. MediaStore's albumart URIs frequently
     * 404 on sideloaded files, so the tag is the more reliable source.
     */
    private fun ensureArtwork(songId: String?, songUri: String?) {
        if (songId == null || songUri == null) {
            artwork = null
            artworkSongId = null
            return
        }
        if (songId == artworkSongId) return

        artworkSongId = songId
        artwork = null
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(this@PlaybackService, Uri.parse(songUri))
                    val bytes = retriever.embeddedPicture
                    retriever.release()
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null && artworkSongId == songId) {
                artwork = bitmap
                refreshNotification()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            scope.cancel()
            PlaybackManager.exoPlayer.removeListener(playerListener)
            mediaSession?.run {
                release()
                mediaSession = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "default_channel_id"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE = "com.retro.retromuse.TOGGLE"
        const val ACTION_NEXT = "com.retro.retromuse.NEXT"
        const val ACTION_PREV = "com.retro.retromuse.PREV"
    }
}

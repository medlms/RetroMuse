package com.retro.grooveplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.RemoteViews
import com.retro.grooveplayer.MainActivity
import com.retro.grooveplayer.R
import com.retro.grooveplayer.playback.PlaybackManager

class RetroMuseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // A widget outlives the process, so every control has to assume the player may
        // not exist yet. Without this the buttons threw UninitializedPropertyAccess.
        try {
            when (intent.action) {
                ACTION_PLAY -> {
                    PlaybackManager.ensureInitialised(context)
                    if (PlaybackManager.currentSong == null) {
                        PlaybackManager.playFirstSong()
                    } else {
                        PlaybackManager.togglePlay()
                    }
                    updateAllWidgets(context)
                }
                ACTION_NEXT -> {
                    PlaybackManager.ensureInitialised(context)
                    PlaybackManager.nextSong()
                    updateAllWidgets(context)
                }
                ACTION_PREV -> {
                    PlaybackManager.ensureInitialised(context)
                    PlaybackManager.prevSong()
                    updateAllWidgets(context)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_PLAY = "ACTION_WIDGET_PLAY"
        const val ACTION_NEXT = "ACTION_WIDGET_NEXT"
        const val ACTION_PREV = "ACTION_WIDGET_PREV"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, RetroMuseWidgetProvider::class.java)
                for (widgetId in appWidgetManager.getAppWidgetIds(thisWidget)) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun buttonIntent(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, RetroMuseWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val currentSong = if (PlaybackManager.isInitialised) PlaybackManager.currentSong else null
            val isPlaying = PlaybackManager.isInitialised && PlaybackManager.isPlaying

            if (currentSong != null) {
                views.setTextViewText(R.id.widget_title, currentSong.name)
                views.setTextViewText(R.id.widget_artist, currentSong.artist)
            } else {
                views.setTextViewText(R.id.widget_title, "RetroMuse")
                views.setTextViewText(R.id.widget_artist, "Select a song to start")
            }

            views.setImageViewResource(
                R.id.widget_btn_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            // Cover art from the file's tags, falling back to the launcher icon.
            val art = currentSong?.uri?.let { loadArtwork(context, it) }
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_art, art)
            } else {
                views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher_round)
            }

            views.setOnClickPendingIntent(R.id.widget_btn_play, buttonIntent(context, ACTION_PLAY, 201))
            views.setOnClickPendingIntent(R.id.widget_btn_next, buttonIntent(context, ACTION_NEXT, 202))
            views.setOnClickPendingIntent(R.id.widget_btn_prev, buttonIntent(context, ACTION_PREV, 203))

            // Tapping the body opens the app.
            views.setOnClickPendingIntent(
                R.id.widget_art,
                PendingIntent.getActivity(
                    context,
                    204,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun loadArtwork(context: Context, uri: String): android.graphics.Bitmap? {
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.parse(uri))
                val bytes = retriever.embeddedPicture
                retriever.release()
                bytes?.let {
                    // Widgets have a hard limit on RemoteViews payload size.
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(it, 0, it.size, options)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

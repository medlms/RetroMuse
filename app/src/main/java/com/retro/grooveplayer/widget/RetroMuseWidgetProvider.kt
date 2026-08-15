package com.retro.grooveplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
        when (intent.action) {
            "ACTION_WIDGET_PLAY" -> {
                PlaybackManager.togglePlay()
                updateAllWidgets(context)
            }
            "ACTION_WIDGET_NEXT" -> {
                PlaybackManager.nextSong()
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, RetroMuseWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                for (widgetId in allWidgetIds) {
                    updateAppWidget(context, appWidgetManager, widgetId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val currentSong = PlaybackManager.currentSong
            val isPlaying = PlaybackManager.isPlaying

            if (currentSong != null) {
                views.setTextViewText(R.id.widget_title, currentSong.name)
                views.setTextViewText(R.id.widget_artist, currentSong.artist)
                views.setTextViewText(R.id.widget_btn_play, if (isPlaying) "⏸" else "▶")
            } else {
                views.setTextViewText(R.id.widget_title, "RetroMuse")
                views.setTextViewText(R.id.widget_artist, "Select a song to start")
                views.setTextViewText(R.id.widget_btn_play, "▶")
            }

            // Set up PendingIntents for buttons
            val playIntent = Intent(context, RetroMuseWidgetProvider::class.java).apply {
                action = "ACTION_WIDGET_PLAY"
            }
            val playPI = PendingIntent.getBroadcast(
                context,
                201,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play, playPI)

            val nextIntent = Intent(context, RetroMuseWidgetProvider::class.java).apply {
                action = "ACTION_WIDGET_NEXT"
            }
            val nextPI = PendingIntent.getBroadcast(
                context,
                202,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPI)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

package com.retro.grooveplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RetroMuse::TimerWakeLock")
        try {
            wakeLock.acquire(10_000L)

            // The alarm routinely fires into a cold process, where the library has
            // never been loaded and playFirstSong() would find an empty list.
            PlaybackManager.ensureInitialised(context)

            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = "ACTION_AUTO_START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (wakeLock.isHeld) wakeLock.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

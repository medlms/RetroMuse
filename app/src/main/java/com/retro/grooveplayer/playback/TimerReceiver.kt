package com.retro.grooveplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

/**
 * Fires when the start timer is due.
 *
 * Reaching this point at all depends on the alarm actually having been scheduled -
 * see PlaybackManager.startStartTimer, which used to swallow the SecurityException
 * thrown when exact alarms are denied and leave nothing registered.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RetroMuse::TimerWakeLock")

        try {
            wakeLock.acquire(30_000L)

            // The alarm routinely fires into a cold process, where the library has
            // never been loaded and playFirstSong() would find an empty list.
            PlaybackManager.ensureInitialised(context)

            // The alarm has been consumed; don't let a restored countdown resurrect it.
            PlaybackManager.clearStartTimer()

            val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START_MUSIC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Most likely ForegroundServiceStartNotAllowedException if the alarm did
            // not carry a background-start exemption. Fall back to starting playback
            // directly; the service will be promoted when the app is next opened.
            e.printStackTrace()
            try {
                PlaybackManager.playFirstSong()
            } catch (inner: Exception) {
                inner.printStackTrace()
            }
        } finally {
            try {
                if (wakeLock.isHeld) wakeLock.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val ACTION_START_MUSIC = "com.retro.retromuse.START_MUSIC"
    }
}

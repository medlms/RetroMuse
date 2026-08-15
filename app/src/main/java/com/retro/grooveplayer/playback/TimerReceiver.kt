package com.retro.grooveplayer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire a temporary WakeLock to keep the CPU awake
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RetroMuse::TimerWakeLock")
        try {
            wakeLock.acquire(10000) // Keep CPU awake for 10 seconds
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start PlaybackService with ACTION_AUTO_START
        val serviceIntent = Intent(context, PlaybackService::class.java).apply {
            action = "ACTION_AUTO_START"
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

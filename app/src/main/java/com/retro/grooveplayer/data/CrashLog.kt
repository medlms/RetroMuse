package com.retro.grooveplayer.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a local file.
 *
 * A full crash-reporting SDK needs a Firebase or Sentry account wired up, which is the
 * user's to configure. This costs nothing and still means a field crash leaves
 * evidence the user can read and send, instead of vanishing.
 */
object CrashLog {

    private const val FILE_NAME = "crash_log.txt"
    private const val MAX_BYTES = 96 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                append(appContext, thread, throwable)
            } catch (e: Throwable) {
                // Never let logging replace the original crash.
                e.printStackTrace()
            }
            // Hand back to the platform so the crash still surfaces normally.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val entry = buildString {
            appendLine("---- $timestamp ----")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(stack.toString())
        }

        val target = file(context)
        // Keep the newest entries only, so the file cannot grow without bound.
        if (target.exists() && target.length() > MAX_BYTES) {
            target.writeText(target.readText().takeLast(MAX_BYTES / 2))
        }
        target.appendText(entry)
    }

    fun read(context: Context): String = try {
        val target = file(context)
        if (target.exists()) target.readText() else ""
    } catch (e: Exception) {
        ""
    }

    fun hasEntries(context: Context): Boolean = read(context).isNotBlank()

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

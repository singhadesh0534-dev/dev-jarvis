package com.adesh.devjarvis.core

import android.content.Context
import android.os.Build
import com.adesh.devjarvis.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash ho to stack trace phone pe save (files/crash/). Agli baar boot screen + Settings > Diagnostics mein dikhta hai.
 * Sirf stack trace + device/app version — koi API key, transcript ya personal data nahi.
 */
object CrashReporter {

    private fun dir(context: Context) = File(context.filesDir, "crash").apply { mkdirs() }
    private fun crashFile(context: Context) = File(dir(context), "last_crash.txt")
    private fun logFile(context: Context) = File(dir(context), "errors.log")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile(app).writeText(format("CRASH on thread ${thread.name}", e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** A caught-and-recovered error (doesn't count as a crash). Log is capped at ~64 KB. */
    fun record(context: Context, where: String, e: Throwable) {
        runCatching {
            val f = logFile(context)
            if (f.length() > 64 * 1024) f.writeText(f.readText().takeLast(32 * 1024))
            f.appendText(format("RECOVERED in $where", e) + "\n\n")
        }
    }

    fun lastCrash(context: Context): String? = crashFile(context).takeIf { it.exists() }?.readText()

    fun recoveredLog(context: Context): String? = logFile(context).takeIf { it.exists() && it.length() > 0 }?.readText()

    fun clear(context: Context) {
        crashFile(context).delete()
        logFile(context).delete()
    }

    private fun format(title: String, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())
        return buildString {
            appendLine("$title at $time")
            appendLine("JARVIS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            append(sw.toString().take(12_000))
        }
    }
}

package com.adesh.devjarvis.apps

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.adesh.devjarvis.R
import java.util.Locale

/**
 * window.JarvisPhone inside the apps JARVIS builds (the app kit's `jarvis.*` uses it when present): vibrate, speak,
 * notify, toast. Only these harmless calls — no files, contacts, calls or messages.
 */
class PhoneBridge(private val activity: Activity, private val appName: String) {
    private var tts: TextToSpeech? = null

    @JavascriptInterface
    fun vibrate(ms: Int) {
        val v = activity.getSystemService(Vibrator::class.java) ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms.coerceIn(10, 2_000).toLong(), VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    @JavascriptInterface
    fun speak(text: String) {
        val say = text.take(500)
        activity.runOnUiThread {
            val t = tts
            if (t != null) t.speak(say, TextToSpeech.QUEUE_FLUSH, null, "app")
            else tts = TextToSpeech(activity) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("hi", "IN")
                    tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null, "app")
                }
            }
        }
    }

    @JavascriptInterface
    fun notify(title: String, text: String) {
        val nm = activity.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "JARVIS ke banaye apps", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(activity, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle(title.take(80).ifBlank { appName })
            .setContentText(text.take(300))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify((appName + title).hashCode(), n) }
    }

    @JavascriptInterface
    fun toast(text: String) {
        activity.runOnUiThread { Toast.makeText(activity, text.take(120), Toast.LENGTH_SHORT).show() }
    }

    fun close() {
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val NAME = "JarvisPhone"
        private const val CHANNEL = "jarvis_built_apps"

    }
}

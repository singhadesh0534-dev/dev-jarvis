package com.adesh.devjarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Baat-cheet ke dauraan music pause/duck karo; khatam hone pe wapas. */
@Singleton
class AudioFocusHelper @Inject constructor(@ApplicationContext context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    /**
     * Call (ya koi aur app ke communication mode) shuru/khatam hone par turant pata chale, taaki JARVIS
     * call ke beech mic na pakde aur na bole. [onCallStateChanged] har mode badlav pe chalega.
     */
    fun watchCallState(onCallStateChanged: () -> Unit) {
        val am = audio ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { am.addOnModeChangedListener({ it.run() }, { onCallStateChanged() }) }
        }
    }
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()
    private var held = false

    @Synchronized
    fun acquire() {
        if (held || audio == null) return
        held = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Synchronized
    fun release() {
        if (!held || audio == null) return
        audio.abandonAudioFocusRequest(request)
        held = false
    }

    /** Set while JARVIS's own Live session has the phone in communication mode. */
    @Volatile var ownCommunicationMode = false

    /** True if the user is in a call or the phone is ringing — JARVIS should stay quiet. */
    fun inCall(): Boolean = audio?.mode == AudioManager.MODE_IN_CALL ||
        (audio?.mode == AudioManager.MODE_IN_COMMUNICATION && !ownCommunicationMode) ||
        audio?.mode == AudioManager.MODE_RINGTONE
}

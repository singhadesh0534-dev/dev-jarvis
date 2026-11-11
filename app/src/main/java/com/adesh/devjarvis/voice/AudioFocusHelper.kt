package com.adesh.devjarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Baat-cheet ke dauraan music pause/duck karo; khatam hone pe wapas. */
@Singleton
class AudioFocusHelper @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("voice_volume", Context.MODE_PRIVATE)

    private fun headphones(): Boolean = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    } == true

    /**
     * JARVIS full volume pe bole, par Sir ka media volume jahan tha wahin rahe: baat-cheet ke dauraan (jab video /
     * gaana waise bhi ruka hota hai) media volume poora kar do, khatam hote hi wapas wahi. Sirf phone ke speaker pe —
     * earphones mein kabhi nahi.
     */
    private fun boostVoice() {
        val am = audio ?: return
        // A muted media stream means JARVIS is silent whatever the volume says.
        runCatching { if (am.isStreamMute(AudioManager.STREAM_MUSIC)) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
        if (!settings.current.fullVoiceVolume || headphones()) return
        runCatching {
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (current < max && prefs.getInt(K_RESTORE, -1) < 0) {
                prefs.edit().putInt(K_RESTORE, current).commit()
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            }
        }
    }

    /** Call right before JARVIS speaks (voice or typed chat): makes sure he can be heard. */
    fun prepareToSpeak() = boostVoice()

    /** "vol=7/15 muted=false" for the flight recorder. */
    fun describeVolume(): String = runCatching {
        val am = audio ?: return "no audio"
        "vol=${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} muted=${am.isStreamMute(AudioManager.STREAM_MUSIC)}"
    }.getOrDefault("?")

    /** Put Sir's media volume back. Also called at app start, in case JARVIS was killed mid-conversation. */
    fun restoreVolume() {
        val saved = prefs.getInt(K_RESTORE, -1)
        if (saved < 0) return
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0) }
        prefs.edit().remove(K_RESTORE).apply()
    }

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
        boostVoice()
    }

    @Synchronized
    fun release() {
        restoreVolume() // before the video / song resumes
        if (!held || audio == null) return
        audio.abandonAudioFocusRequest(request)
        held = false
    }

    /** Set while JARVIS's own Live session has the phone in communication mode. */
    @Volatile var ownCommunicationMode = false

    /** Phone is ringing right now (not yet answered). */
    fun ringing(): Boolean = audio?.mode == AudioManager.MODE_RINGTONE

    private companion object {
        const val K_RESTORE = "media_volume_to_restore"
    }

    /** True if the user is in a call or the phone is ringing — JARVIS should stay quiet. */
    fun inCall(): Boolean = audio?.mode == AudioManager.MODE_IN_CALL ||
        (audio?.mode == AudioManager.MODE_IN_COMMUNICATION && !ownCommunicationMode) ||
        audio?.mode == AudioManager.MODE_RINGTONE
}

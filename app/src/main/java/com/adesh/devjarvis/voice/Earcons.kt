package com.adesh.devjarvis.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Chhoti futuristic awaazein + haptic tick. Code se generate hoti hain (koi audio file nahi).
 */
@Singleton
class Earcons @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val main = Handler(Looper.getMainLooper())
    private val wakeTone: ShortArray by lazy { chime(listOf(1046.5, 1568.0), noteMs = 75) }
    private val sleepTone: ShortArray by lazy { chime(listOf(1318.5, 880.0), noteMs = 70, volume = 0.22) }
    private val successTone: ShortArray by lazy { chime(listOf(1760.0), noteMs = 45, volume = 0.16) }
    private val errorTone: ShortArray by lazy { chime(listOf(392.0, 330.0), noteMs = 80, volume = 0.22) }

    private val enabled get() = settings.current.soundEffects

    /** Listening start: rising two-note chime + tick. */
    fun wake() {
        tick()
        if (enabled) play(wakeTone)
    }

    /** Conversation window closed: soft falling chime. */
    fun sleep() { if (enabled) play(sleepTone) }

    /** A tool did its job: tiny high blip. */
    fun success() { if (enabled) play(successTone) }

    /** Something failed: low two-note. */
    fun error() { if (enabled) play(errorTone) }

    fun tick() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= 29) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        }
    }

    private fun play(samples: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            val durationMs = samples.size * 1000L / RATE
            main.postDelayed({ runCatching { track.release() } }, durationMs + 150)
        }
    }

    private fun chime(freqs: List<Double>, noteMs: Int, volume: Double = 0.30): ShortArray {
        val noteLen = RATE * noteMs / 1000
        val tail = RATE * 120 / 1000
        val out = ShortArray(noteLen * freqs.size + tail)
        freqs.forEachIndexed { idx, f ->
            val start = idx * noteLen
            val len = if (idx == freqs.lastIndex) noteLen + tail else noteLen * 2
            for (i in 0 until len) {
                val pos = start + i
                if (pos >= out.size) break
                val t = i.toDouble() / RATE
                val env = (1 - exp(-t * 400)) * exp(-t * 18)
                val s = sin(2 * PI * f * t) + 0.25 * sin(2 * PI * f * 2 * t) + 0.08 * sin(2 * PI * f * 3 * t)
                val mixed = out[pos] + (s * env * volume * Short.MAX_VALUE / 1.33).toInt()
                out[pos] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return out
    }

    private companion object {
        const val RATE = 44_100
    }
}

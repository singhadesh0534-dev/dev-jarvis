package com.adesh.devjarvis.live

import android.content.Context
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode
import com.konovalov.vad.silero.config.SampleRate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silero VAD (github.com/snakers4/silero-vad via github.com/gkonovalov/android-vad, MIT): a small neural model on
 * the phone that tells human speech from noise — fan, traffic, TV music, claps, dishes. Loudness alone can't.
 */
@Singleton
class SileroSpeech @Inject constructor(@ApplicationContext private val context: Context) {
    /** One stream per conversation; null when the model can't load on this phone (then loudness alone decides). */
    fun open(): Stream? = runCatching { Stream(context) }.getOrNull()

    class Stream(context: Context) : Closeable {
        private val vad = VadSilero(
            context,
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_512,
            mode = Mode.NORMAL,
            speechDurationMs = 50,
            silenceDurationMs = 300, // keeps word endings and short pauses inside a sentence
        )
        private val frames = SpeechFrames(512) { f -> runCatching { vad.isSpeech(f) }.getOrDefault(true) }

        fun offer(samples: ShortArray, n: Int): Boolean = frames.offer(samples, n)

        override fun close() = runCatching { vad.close() }.let { }
    }
}

package com.adesh.devjarvis.voice

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Simple energy VAD for our own recordings (Groq Whisper mode): kab bolna shuru hua, kab ruk gaye.
 * Feed 20–50 ms frames; it adapts to the room's noise floor. Pure, unit-tested.
 */
class EndpointDetector(
    private val sampleRate: Int = 16_000,
    private val endSilenceMs: Int = 900,
    private val noSpeechTimeoutMs: Int = 6_000,
    private val maxUtteranceMs: Int = 15_000,
    private val minSpeechMs: Int = 250,
) {
    enum class State { WAITING, SPEAKING, DONE, NOTHING_HEARD }

    var state = State.WAITING
        private set

    private var noiseFloor = 300.0
    private var elapsedMs = 0
    private var speechMs = 0
    private var silenceMs = 0
    /** Last frame loudness 0..1 for the HUD. */
    var level = 0f
        private set

    fun feed(frame: ShortArray, count: Int = frame.size): State {
        if (state == State.DONE || state == State.NOTHING_HEARD || count <= 0) return state
        val ms = count * 1000 / sampleRate
        elapsedMs += ms
        val r = rms(frame, count)
        level = ((r / 4000.0).coerceIn(0.0, 1.0)).toFloat()
        val threshold = max(noiseFloor * 2.8, 450.0)
        val loud = r > threshold

        when (state) {
            State.WAITING -> {
                if (loud) {
                    speechMs += ms
                    if (speechMs >= minSpeechMs) { state = State.SPEAKING; silenceMs = 0 }
                } else {
                    speechMs = 0
                    noiseFloor = noiseFloor * 0.95 + r * 0.05
                    if (elapsedMs >= noSpeechTimeoutMs) state = State.NOTHING_HEARD
                }
            }
            State.SPEAKING -> {
                if (loud) silenceMs = 0 else silenceMs += ms
                if (silenceMs >= endSilenceMs || elapsedMs >= maxUtteranceMs) state = State.DONE
            }
            else -> Unit
        }
        return state
    }

    private fun rms(frame: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) { val v = frame[i].toDouble(); sum += v * v }
        return sqrt(sum / count)
    }
}

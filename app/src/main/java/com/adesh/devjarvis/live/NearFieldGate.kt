package com.adesh.devjarvis.live

import kotlin.math.max
import kotlin.math.min

/**
 * Shor filter (Live mode): server ko sirf wahi awaaz jaati hai jo aas-paas ke shor se saaf zyada tez ho —
 * yaani phone ke paas bolne wala. Door ki baatein, TV, gaadi, music ka lagataar shor "silence" ban ke jaata hai,
 * to JARVIS un par jawab nahi deta. Ye awaaz pehchaanta nahi: paas khada doosra aadmi zor se bole to wo bhi niklega.
 * Pure, unit-tested. Level = 0..1 (RMS / 4000), ek call per ~50 ms chunk.
 */
class NearFieldGate(
    /** How many times louder than the background a voice must be. */
    private val ratio: Float = 2.2f,
    /** Never open below this, however quiet the room is. */
    private val absMin: Float = 0.04f,
    private val attackChunks: Int = 2,
    private val hangChunks: Int = 16,
) {
    private var floor = 0.02f
    private var loud = 0
    private var hang = 0
    private val recent = ArrayDeque<Float>() // levels since the gate opened (last ~1.5 s)

    var isOpen = false
        private set

    fun offer(level: Float): Boolean {
        val threshold = min(MAX_THRESHOLD, max(absMin, floor * ratio))
        if (isOpen) {
            if (level >= threshold * 0.6f) hang = hangChunks else hang--
            // Speech has gaps between words, a steady noise doesn't: follow the quietest moment of the last 1.5 s.
            // So a bike / mixer / song that starts suddenly becomes the new background and the gate closes again.
            recent.addLast(level)
            if (recent.size > WINDOW_CHUNKS) recent.removeFirst()
            if (recent.size == WINDOW_CHUNKS) floor += (recent.min() - floor) * 0.05f
            if (hang <= 0) close()
        } else {
            loud = if (level >= threshold) loud + 1 else 0
            if (loud >= attackChunks) {
                isOpen = true
                hang = hangChunks
                loud = 0
                recent.clear()
            } else {
                floor += (level - floor) * 0.05f // background level, ~1 s memory
            }
        }
        return isOpen
    }

    private fun close() {
        isOpen = false
        loud = 0
        recent.clear()
    }

    companion object {
        const val MAX_THRESHOLD = 0.9f
        const val WINDOW_CHUNKS = 30
        const val OFF = 0
        const val NORMAL = 1
        const val STRONG = 2
        /** Crowd / market / function: only a voice right at the phone (mouth a hand-width away) gets through. */
        const val CROWD = 3

        fun forLevel(level: Int): NearFieldGate? = when (level) {
            OFF -> null
            STRONG -> NearFieldGate(ratio = 3.0f, absMin = 0.08f, attackChunks = 3)
            CROWD -> NearFieldGate(ratio = 4.2f, absMin = 0.15f, attackChunks = 3, hangChunks = 12)
            else -> NearFieldGate()
        }
    }
}

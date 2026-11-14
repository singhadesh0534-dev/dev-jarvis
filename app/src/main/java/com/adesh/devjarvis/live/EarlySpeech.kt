package com.adesh.devjarvis.live

/**
 * Jo Sir "Hello Dev" ke turant baad bolte hain, jab tak Live server jud raha hota hai (1.5-5 s on mobile data):
 * pehle wo phenk diya jaata tha, to "Hello Dev, torch on karo" ek saans mein bolne par command kho jaati thi aur
 * JARVIS greeting bolta rehta. Ab wo audio sambhal ke rakha jaata hai; Sir sach mein bol rahe the to server ko wahi
 * jaata hai aur greeting chhod di jaati hai. Pure, unit-tested. One chunk = ~50 ms of 16 kHz 16-bit mono.
 */
class EarlySpeech(
    /** The first ~0.5 s can still be the end of the wake word itself: it doesn't count as his request. */
    private val tailChunks: Int = 10,
    /** ~0.4 s of real speech after that = Sir has started saying what he wants. */
    private val minVoiced: Int = 8,
    /** Keep at most ~8 s. */
    private val maxChunks: Int = 160,
    /** RMS that counts as a voice (the noise filter already sends silence for background). */
    private val minRms: Double = 320.0,
) {
    private val kept = ArrayDeque<String>()
    private var seen = 0
    private var voiced = 0

    /** Store one chunk ([message] = the ready-to-send JSON) that arrived before the session was ready. */
    fun offer(pcm: ByteArray, length: Int, message: String) {
        seen++
        if (seen > tailChunks && rms(pcm, length) >= minRms) voiced++
        kept.addLast(message)
        while (kept.size > maxChunks) kept.removeFirst()
    }

    /** Did Sir start his request while JARVIS was connecting? */
    val spoke: Boolean get() = voiced >= minVoiced

    /** The chunks to send now (only when he spoke), then forget everything. */
    fun drain(): List<String> {
        val out = if (spoke) kept.toList() else emptyList()
        kept.clear()
        return out
    }

    companion object {
        /** Loudness of 16-bit little-endian PCM. */
        fun rms(pcm: ByteArray, length: Int): Double {
            val n = (minOf(length, pcm.size) / 2)
            if (n == 0) return 0.0
            var sum = 0.0
            for (i in 0 until n) {
                val v = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toDouble()
                sum += v * v
            }
            return kotlin.math.sqrt(sum / n)
        }
    }
}

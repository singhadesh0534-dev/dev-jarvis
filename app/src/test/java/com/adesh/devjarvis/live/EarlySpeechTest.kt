package com.adesh.devjarvis.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlySpeechTest {
    private fun chunk(amplitude: Int): ByteArray {
        val b = ByteArray(1600) // 800 samples = 50 ms
        for (i in 0 until 800) {
            val v = if (i % 2 == 0) amplitude else -amplitude
            b[2 * i] = (v and 0xFF).toByte()
            b[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return b
    }

    @Test fun rmsOfLoudAndSilent() {
        assertEquals(0.0, EarlySpeech.rms(chunk(0), 1600), 0.01)
        assertEquals(2000.0, EarlySpeech.rms(chunk(2000), 1600), 1.0)
    }

    @Test fun commandSaidWhileConnectingIsKeptAndSent() {
        val e = EarlySpeech()
        repeat(10) { e.offer(chunk(3000), 1600, "tail$it") } // end of "Hello Dev"
        repeat(12) { e.offer(chunk(2500), 1600, "cmd$it") }  // "torch on karo"
        assertTrue(e.spoke)
        val sent = e.drain()
        assertEquals(22, sent.size)
        assertEquals("tail0", sent.first())
        assertTrue(e.drain().isEmpty())
    }

    @Test fun justTheWakeWordThenSilenceGetsTheGreeting() {
        val e = EarlySpeech()
        repeat(10) { e.offer(chunk(3000), 1600, "tail") }
        repeat(40) { e.offer(chunk(0), 1600, "quiet") }
        assertFalse(e.spoke)
        assertTrue(e.drain().isEmpty())
    }

    @Test fun keepsOnlyTheLastEightSeconds() {
        val e = EarlySpeech(maxChunks = 5)
        repeat(30) { e.offer(chunk(2500), 1600, "c$it") }
        assertEquals(listOf("c25", "c26", "c27", "c28", "c29"), e.drain())
    }
}

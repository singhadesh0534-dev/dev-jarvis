package com.adesh.devjarvis.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearFieldGateTest {
    private fun NearFieldGate.feed(level: Float, chunks: Int): Boolean {
        var last = false
        repeat(chunks) { last = offer(level) }
        return last
    }

    @Test fun quietRoomSpeechOpensQuickly() {
        val g = NearFieldGate()
        assertFalse(g.feed(0.01f, 40))
        assertFalse(g.offer(0.2f))
        assertTrue(g.offer(0.2f)) // second loud chunk (100 ms)
    }

    @Test fun steadyLoudNoiseStaysClosed() {
        val g = NearFieldGate()
        g.feed(0.01f, 20)
        g.feed(0.3f, 400) // a bike / fan / crowd starts and keeps going: may open once, must not stay open
        assertFalse(g.isOpen)
        assertFalse(g.feed(0.33f, 40)) // small wobble in the noise
    }

    @Test fun voiceOverNoiseOpens() {
        val g = NearFieldGate()
        g.feed(0.2f, 400)
        assertFalse(g.isOpen)
        assertTrue(g.feed(0.7f, 3))
    }

    @Test fun closesAfterSirStops() {
        val g = NearFieldGate(hangChunks = 16)
        g.feed(0.01f, 40)
        assertTrue(g.feed(0.3f, 10))
        assertTrue(g.feed(0.01f, 15)) // short pause between words: still open
        assertFalse(g.feed(0.01f, 5))
    }

    @Test fun singleClickDoesNotOpen() {
        val g = NearFieldGate()
        g.feed(0.01f, 40)
        assertFalse(g.offer(0.9f))
        assertFalse(g.offer(0.01f))
        assertFalse(g.offer(0.9f))
        assertFalse(g.offer(0.01f))
    }

    @Test fun levels() {
        assertNull(NearFieldGate.forLevel(NearFieldGate.OFF))
        val strong = NearFieldGate.forLevel(NearFieldGate.STRONG)!!
        strong.feed(0.01f, 40)
        assertFalse(strong.feed(0.06f, 10)) // too soft for the strong filter
        assertTrue(NearFieldGate.forLevel(NearFieldGate.NORMAL)!!.apply { feed(0.01f, 40) }.feed(0.06f, 3))
    }
}

class LiveVideoMessageTest {
    @Test fun videoFrameIsRealtimeJpeg() {
        val msg = LiveMessages.video(byteArrayOf(1, 2, 3))
        assertTrue(msg.contains("\"realtimeInput\""))
        assertTrue(msg.contains("\"video\""))
        assertTrue(msg.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(msg.contains("\"data\":\"AQID\""))
    }
}

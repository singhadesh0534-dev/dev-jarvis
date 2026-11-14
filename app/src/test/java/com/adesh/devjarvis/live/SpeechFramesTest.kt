package com.adesh.devjarvis.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFramesTest {
    @Test fun chunksBecomeFullFramesForTheModel() {
        val seen = mutableListOf<Int>()
        val f = SpeechFrames(512) { frame -> seen += frame.size; frame[0] > 0 }
        assertTrue(f.offer(ShortArray(800) { 1 }, 800)) // one frame judged, 288 samples wait for the next
        assertEquals(listOf(512), seen)
        // 288 ones + 224 zeros = frame 2 (starts with speech), then 512 zeros = frame 3 (silence), 64 left over
        assertFalse(f.offer(ShortArray(800) { 0 }, 800))
        assertEquals(listOf(512, 512, 512), seen)
    }
}

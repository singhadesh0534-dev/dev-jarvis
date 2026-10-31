package com.adesh.devjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioUtilsTest {

    private fun frame(amp: Int, n: Int = 640) = ShortArray(n) { i -> (if (i % 2 == 0) amp else -amp).toShort() }

    @Test fun vadDetectsSpeechThenSilence() {
        val vad = EndpointDetector(endSilenceMs = 400, minSpeechMs = 120)
        repeat(10) { vad.feed(frame(50)) }                  // quiet room
        assertEquals(EndpointDetector.State.WAITING, vad.state)
        repeat(10) { vad.feed(frame(5000)) }                // speaking
        assertEquals(EndpointDetector.State.SPEAKING, vad.state)
        repeat(12) { vad.feed(frame(50)) }                  // stopped
        assertEquals(EndpointDetector.State.DONE, vad.state)
    }

    @Test fun vadTimesOutWithNoSpeech() {
        val vad = EndpointDetector(noSpeechTimeoutMs = 1000)
        repeat(30) { vad.feed(frame(40)) }
        assertEquals(EndpointDetector.State.NOTHING_HEARD, vad.state)
    }

    @Test fun vadIgnoresShortClick() {
        val vad = EndpointDetector(minSpeechMs = 250)
        vad.feed(frame(9000))                               // one 40 ms click
        repeat(5) { vad.feed(frame(40)) }
        assertEquals(EndpointDetector.State.WAITING, vad.state)
    }

    @Test fun wavHeader() {
        val pcm = shortArrayOf(1, -1, 300)
        val wav = Wav.fromPcm16(pcm, 3, 16000)
        assertEquals(44 + 6, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, bb.getInt(24))
        assertEquals(6, bb.getInt(40))
        assertEquals(300.toShort(), bb.getShort(48))
    }
}

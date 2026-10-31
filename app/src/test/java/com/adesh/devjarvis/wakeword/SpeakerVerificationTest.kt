package com.adesh.devjarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerVerificationTest {
    @Test fun parsesSpkVector() {
        val v = SpeakerVerification.parseVector("""{"text":"hello dev","spk":[0.1,0.2,-0.3],"spk_frames":40}""")!!
        assertEquals(3, v.size)
        assertEquals(0.2f, v[1], 1e-6f)
    }

    @Test fun noSpkIsNull() {
        assertNull(SpeakerVerification.parseVector("""{"text":"hello dev"}"""))
        assertNull(SpeakerVerification.parseVector("not json"))
    }

    @Test fun cosineSameIsOne() {
        val a = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, SpeakerVerification.cosine(a, a), 1e-5f)
    }

    @Test fun cosineOppositeIsNegative() {
        assertTrue(SpeakerVerification.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)) < 0)
        assertEquals(0f, SpeakerVerification.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
    }

    @Test fun averageNormalises() {
        val avg = SpeakerVerification.average(listOf(floatArrayOf(2f, 0f), floatArrayOf(0f, 2f)))!!
        assertEquals(1f, avg[0], 1e-6f)
        assertEquals(1f, avg[1], 1e-6f)
        assertNull(SpeakerVerification.average(listOf(floatArrayOf(1f), floatArrayOf(1f, 2f))))
        assertNull(SpeakerVerification.average(emptyList()))
    }

    @Test fun matchesAbovethreshold() {
        val me = floatArrayOf(1f, 1f, 0.2f)
        val meAgain = floatArrayOf(0.9f, 1.1f, 0.25f)   // similar
        val other = floatArrayOf(-1f, 0.8f, -0.5f)       // different
        assertTrue(SpeakerVerification.matches(me, meAgain, 0.45f))
        assertFalse(SpeakerVerification.matches(me, other, 0.45f))
    }

    @Test fun encodeDecodeRoundtrip() {
        val v = FloatArray(128) { it * 0.01f }
        val back = SpeakerVerification.decode(SpeakerVerification.encode(v))!!
        assertEquals(128, back.size)
        assertEquals(v[64], back[64], 1e-4f)
        assertNull(SpeakerVerification.decode(""))
        assertNull(SpeakerVerification.decode("1,2,3")) // too short to be a real profile
    }
}

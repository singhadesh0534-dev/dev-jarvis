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

    @Test fun blendMovesThePrintTowardSirsRealVoice() {
        val profile = floatArrayOf(1f, 0f)
        val sample = floatArrayOf(0f, 1f)
        val before = SpeakerVerification.cosine(profile, sample)
        val after = SpeakerVerification.cosine(SpeakerVerification.blend(profile, sample, 0.15f), sample)
        assertTrue(after > before)
        assertTrue(SpeakerVerification.cosine(SpeakerVerification.blend(profile, sample, 0.05f), profile) > 0.99f)
    }

    @Test fun thirdCallInTheWindowIsAccepted() {
        val i = SpeakerVerification.Insistence(windowMs = 45_000, needed = 2)
        assertFalse(i.shouldAccept(0))
        i.refused(0)
        assertFalse(i.shouldAccept(5_000))
        i.refused(5_000)
        assertTrue(i.shouldAccept(10_000))
        i.clear()
        assertFalse(i.shouldAccept(10_000))
        i.refused(0); i.refused(1_000)
        assertFalse(i.shouldAccept(60_000)) // too long ago: a fresh start
    }
}

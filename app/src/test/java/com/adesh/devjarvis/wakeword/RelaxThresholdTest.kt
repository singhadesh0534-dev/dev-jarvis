package com.adesh.devjarvis.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelaxThresholdTest {
    @Test fun relaxesOnlyFromRepeatedInsistence() {
        assertNull(SpeakerVerification.relaxedThreshold(0.29f, listOf(0.25f, 0.24f)))
        assertEquals(0.31f, SpeakerVerification.relaxedThreshold(0.40f, listOf(0.35f, 0.30f, 0.33f))!!, 1e-4f)
        assertEquals(0.25f, SpeakerVerification.relaxedThreshold(0.45f, listOf(0.10f, 0.12f, 0.11f))!!, 1e-4f) // floor
        assertNull(SpeakerVerification.relaxedThreshold(0.29f, listOf(0.40f, 0.50f, 0.45f))) // above: nothing to learn
    }
}

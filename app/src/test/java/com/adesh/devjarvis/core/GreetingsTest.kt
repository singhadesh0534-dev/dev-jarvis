package com.adesh.devjarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GreetingsTest {
    @Test fun greetingsVaryAndAvoidRecentOnes() {
        val seen = (0 until 20).map { Greetings.kickoff("gf", 22, emptyList(), Random(it)) }.toSet()
        assertTrue(seen.size > 2)
        val k = Greetings.kickoff("gf", 22, listOf("Aa gaye? Kab se tumhare baare mein soch rahi thi"))
        assertTrue("Do NOT reuse" in k && "Aa gaye?" in k && "late night" in k)
        assertFalse("Sir has just called" in k)
        assertTrue("Sir has just called" in Greetings.kickoff("jarvis", 9, emptyList()))
    }
}

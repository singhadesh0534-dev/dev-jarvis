package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPoolTest {
    private var t = 0L
    private val pool = KeyPool { t }
    private val raw = "k1, k2\nk3\nk1"

    @Test fun parsesAnySeparatorAndDropsDuplicates() = assertEquals(listOf("k1", "k2", "k3"), pool.parse(raw))

    @Test fun rotatesInstantlyAndComesBack() {
        assertEquals("k1", pool.current(raw))
        assertTrue(pool.rest(raw, "k1", 60_000))
        assertEquals("k2", pool.current(raw))
        assertTrue(pool.rest(raw, "k2", 60_000))
        assertEquals("k3", pool.current(raw))
        assertFalse(pool.rest(raw, "k3", 30_000))   // all resting now
        assertEquals(0, pool.readyCount(raw))
        assertEquals("k3", pool.current(raw))        // back soonest
        t = 61_000
        assertEquals("k1", pool.current(raw))
        assertEquals(3, pool.readyCount(raw))
    }

    @Test fun singleKeyAndEmpty() {
        assertEquals("only", pool.current("only"))
        assertFalse(pool.rest("only", "only", 1_000))
        assertEquals("", pool.current("  "))
    }
}

class KeyPasteTest {
    private val pool = KeyPool()

    @Test fun keysWithSlashesEqualsColonsAndDotsStayWhole() {
        val a = "AQ.Ab8RNexample/part+two=="
        val b = "AIzaSyExample_key-with:colon"
        assertEquals(listOf(a, b), pool.parse("$a\n$b"))
        assertEquals(listOf(a, b), pool.parse("$a\n$b"))
    }

    @Test fun copyPasteNoiseIsRemoved() {
        assertEquals(listOf("AIzaSyAbc123def456"), pool.parse("  \"AIzaSyAbc123def456\"  "))
        assertEquals(listOf("gsk_abc", "gsk_def"), pool.parse("[gsk_abc]; (gsk_def)"))
    }

    @Test fun aSpaceOrLineBreakInsideAPastedKeyIsRemoved() {
        assertEquals(listOf("AQ.Ab8RNfirstpartSECONDPART"), pool.parse(pool.store(listOf("AQ.Ab8RNfirstpart SECONDPART"))))
        assertEquals(listOf("AIzaFirstSecond"), pool.parse(pool.store(listOf("AIzaFirst\tSecond"))))
    }

    @Test fun boxesKeepTheirPlace() {
        val stored = pool.store(listOf("", "", "", "AQ.key4"))
        assertEquals(listOf("", "", "", "AQ.key4"), pool.boxes(stored))
        assertEquals(listOf("AQ.key4"), pool.parse(stored))
        assertEquals("AIza1\n\nAIza3", pool.store(listOf("AIza1", "", "AIza3", "", "")))
    }

    @Test fun olderCommaSeparatedValuesStillLoad() {
        assertEquals(listOf("AIza1", "AIza2"), pool.parse("AIza1, AIza2"))
        assertEquals(listOf("AIza1", "AIza2"), pool.boxes("AIza1, AIza2"))
    }
}

class KeyRestMemoryTest {
    @Test fun longRestsSurviveARestartShortOnesDoNot() {
        var t = 1_000L
        var saved = ""
        val first = KeyPool(onChange = { saved = it }) { t }
        first.rest("bad\ngood", "bad", 24 * 3_600_000L)   // rejected key
        first.rest("bad\ngood\nbusy", "busy", 30_000L)     // short rate limit
        assertFalse("bad" in saved)                         // only fingerprints are stored

        val afterRestart = KeyPool(saved = saved) { t }
        assertEquals("good", afterRestart.current("bad\ngood\nbusy"))
        assertTrue(afterRestart.isResting("bad"))
        assertFalse(afterRestart.isResting("busy"))
        t += 25 * 3_600_000L
        assertEquals("bad", KeyPool(saved = saved) { t }.current("bad\ngood"))
    }
}

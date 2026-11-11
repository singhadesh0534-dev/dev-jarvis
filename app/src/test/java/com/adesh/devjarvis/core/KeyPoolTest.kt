package com.adesh.devjarvis.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPoolTest {
    private var t = 0L
    private val pool = KeyPool { t }
    private val raw = "k1, k2\nk3  k1"

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
        assertEquals(listOf(a, b), pool.parse("$a, $b"))
        assertEquals(listOf(a, b), pool.parse("$a\n$b"))
    }

    @Test fun copyPasteNoiseIsRemoved() {
        assertEquals(listOf("AIzaSyAbc123def456"), pool.parse("  \"AIzaSyAbc123def456\"  "))
        assertEquals(listOf("gsk_abc", "gsk_def"), pool.parse("[gsk_abc]; (gsk_def)"))
    }

    @Test fun boxesJoinedByTheAppComeBackTheSame() {
        val keys = listOf("AQ.one/two=", "AIzaThree", "gsk_four")
        assertEquals(keys, pool.parse(keys.joinToString(", ")))
    }
}

package com.adesh.devjarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryImportTest {
    @Test fun flattenKeepsWhatWasSaidAndDropsIdsAndDates() {
        val raw = """[{"uuid":"3791f23306bad6da43d8ab19de8270de","created_at":"2026-11-10T10:55:46.176806+00:00",
            "name":"Channel ideas","chat_messages":[{"sender":"human","text":"Mera YouTube channel tech ke baare mein hai"},
            {"sender":"assistant","text":"Great, here are ten ideas for it"}],"url":"https://example.com/x"}]"""
        val flat = MemoryImport.flatten(raw)
        assertTrue("Mera YouTube channel tech ke baare mein hai" in flat)
        assertTrue("Channel ideas" in flat)
        assertFalse("3791f233" in flat)
        assertFalse("2026-11-10" in flat)
        assertFalse("example.com" in flat)
        assertFalse("human" in flat) // short structural values are dropped
    }

    @Test fun plainTextIsLeftAlone() {
        assertEquals("Sir likes chai.\nSir lives in Lucknow.", MemoryImport.flatten("  Sir likes chai.\nSir lives in Lucknow.  "))
        assertEquals("{not json at all", MemoryImport.flatten("{not json at all"))
    }

    @Test fun chunksRespectSizeAndCap() {
        val text = (1..100).joinToString("\n") { "line number $it of the export file" }
        val parts = MemoryImport.chunks(text, size = 200, max = 50)
        assertTrue(parts.size > 5)
        assertTrue(parts.all { it.length <= 200 })
        assertTrue("line number 1 of" in parts.first() && "line number 100 of" in parts.last())
        assertEquals(3, MemoryImport.chunks(text, size = 200, max = 3).size)
        assertEquals(listOf("a".repeat(10), "a".repeat(10), "a".repeat(5)), MemoryImport.chunks("a".repeat(25), size = 10))
        assertTrue(MemoryImport.chunks("   \n  ").isEmpty())
    }

    @Test fun parseTakesMoreFactsForImports() {
        val facts = (1..12).joinToString(",") { """{"text":"Sir likes thing number $it a lot","category":"preference"}""" }
        val raw = """{"facts":[$facts],"summary":""}"""
        assertEquals(5, MemoryExtraction.parse(raw)!!.facts.size)
        assertEquals(12, MemoryExtraction.parse(raw, max = MemoryImport.MAX_FACTS_PER_CHUNK)!!.facts.size)
    }
}

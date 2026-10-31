package com.adesh.devjarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private val facts = listOf(
        Fact(1, "Sir's mother is saved as 'Mummy Ji' in contacts.", "person", now - 90 * day, now - 60 * day),
        Fact(2, "Sir prefers tea over coffee in the morning.", "preference", now - 10 * day, now - 10 * day),
        Fact(3, "Sir goes to the gym at 6 am on weekdays.", "routine", now - 5 * day, now - 1 * day),
        Fact(4, "Sir is building an Android app called JARVIS.", "project", now - 2 * day, now - 2 * day),
        Fact(5, "Sir's brother Rahul lives in Pune.", "person", now - 30 * day, now - 30 * day),
    )

    @Test fun keywordMatchWins() {
        val top = MemoryRanker.top(facts, "gym kab hai?", now, limit = 1)
        assertEquals(3L, top.single().id)
    }

    @Test fun aliasMatchMomToMother() {
        val top = MemoryRanker.top(facts, "mummy ko call karo", now, limit = 1)
        assertEquals(1L, top.single().id)
    }

    @Test fun relationAliasBhai() {
        val top = MemoryRanker.top(facts, "bhai kahan rehta hai", now, limit = 2).map { it.id }
        assertTrue(5L in top)
    }

    @Test fun emptyQueryFallsBackToRecentAndPreferences() {
        // No query (Live session start): recent facts, with a small boost for people/preferences.
        val top = MemoryRanker.top(facts, "", now, limit = 2).map { it.id }
        assertEquals(listOf(2L, 3L), top)
        assertTrue(1L !in top) // used 60 days ago
    }

    @Test fun duplicateDetection() {
        assertNotNull(MemoryRanker.isDuplicate("Sir prefers tea over coffee in the morning", facts))
        assertNull(MemoryRanker.isDuplicate("Sir is allergic to peanuts.", facts))
    }

    @Test fun parsesExtractionJsonWithFences() {
        val raw = "```json\n{\"facts\":[{\"text\":\"Sir's sister Priya studies in Delhi.\",\"category\":\"person\"},{\"text\":\"Sir likes jazz.\",\"category\":\"music\"}],\"summary\":\"Talked about family\"}\n```"
        val r = MemoryExtraction.parse(raw)!!
        assertEquals(2, r.facts.size)
        assertEquals("person", r.facts[0].second)
        assertEquals("other", r.facts[1].second)
        assertEquals("Talked about family", r.summary)
    }

    @Test fun neverStoresSecrets() {
        val raw = """{"facts":[{"text":"Sir's UPI PIN is 4321.","category":"other"},{"text":"Sir's OTP was 839201","category":"other"},{"text":"Sir loves biryani.","category":"preference"}],"summary":""}"""
        val r = MemoryExtraction.parse(raw)!!
        assertEquals(listOf("Sir loves biryani."), r.facts.map { it.first })
    }

    @Test fun badJsonIsNull() {
        assertNull(MemoryExtraction.parse("sorry I can't"))
        assertNull(MemoryExtraction.parse(null))
    }
}

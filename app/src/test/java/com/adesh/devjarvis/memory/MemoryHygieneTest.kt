package com.adesh.devjarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryHygieneTest {
    private val facts = listOf(
        Fact(1, "Sir lives in Delhi", "personal", 1_000, 1_000),
        Fact(2, "Sir moved to Pune in 2026", "personal", 9_000, 9_000),
        Fact(3, "Sir likes chai", "preference", 2_000, 2_000),
        Fact(4, "Sir loves tea (chai)", "preference", 3_000, 3_000),
        Fact(5, "Lesson: search inside the app Sir named", "other", 4_000, 4_000),
    )

    @Test fun safePlanOnly() {
        val raw = """{"merge":[{"ids":[3,4],"text":"Sir loves chai"},{"ids":[5,3],"text":"x y z w"}],
            "remove":[{"id":1,"because":"replaced by 2"},{"id":5,"because":"old"},{"id":99,"because":"?"}]}"""
        val p = MemoryHygiene.parse(raw, facts)
        assertEquals(listOf(MemoryHygiene.Merge(listOf(3L, 4L), "Sir loves chai")), p.merges)
        assertEquals(listOf(1L), p.removes.map { it.id })
        assertTrue(MemoryHygiene.parse("garbage", facts).size == 0)
    }

    @Test fun dueEveryFewDays() {
        assertTrue(MemoryHygiene.due(0, 4 * 86_400_000L, 20))
        assertTrue(!MemoryHygiene.due(0, 4 * 86_400_000L, 5))
        assertTrue(!MemoryHygiene.due(86_400_000L, 2 * 86_400_000L, 20))
    }
}

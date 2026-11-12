package com.adesh.devjarvis.brain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillWriterTest {
    @After fun reset() { Skills.learned = emptyList() }

    private val good = """Sure! {"id":"Order Groceries Blinkit","when":"Ordering groceries on Blinkit",
        "steps":["1. open_app Blinkit","- read_screen and tap the search box","type_text the item with submit=true","Stop before payment; Sir pays himself"]}"""

    @Test fun parsesCleansAndNumbers() {
        val s = SkillWriter.parse(good)!!
        assertEquals("order_groceries_blinkit", s.id)
        assertEquals("learned", s.area)
        assertTrue(s.playbook.startsWith("1. open_app Blinkit\n2. read_screen"))
        assertTrue(s.playbook.contains("4. Stop before payment"))
    }

    @Test fun rejectsUnusableAndKeepsIdWhenImproving() {
        assertNull(SkillWriter.parse("""{"id":"x","when":"a","steps":["one"]}"""))
        assertNull(SkillWriter.parse("not json"))
        assertNull(SkillWriter.parse("""{"id":"ok_id","when":"","steps":["a","b","c"]}"""))
        assertEquals("youtube_play", SkillWriter.parse(good, keepId = "youtube_play")!!.id)
    }

    @Test fun learnedSkillsAreFoundFirstAndSurviveSaving() {
        val s = SkillWriter.parse(good)!!
        Skills.learned = SkillWriter.decode(SkillWriter.encode(SkillWriter.merge(emptyList(), s)))
        assertNotNull(Skills.find("order_groceries_blinkit"))
        assertEquals("order_groceries_blinkit", Skills.search("order groceries on blinkit").first().id)
        // An improved version replaces the old one.
        val better = s.copy(playbook = "1. a\n2. b\n3. c")
        assertEquals(1, SkillWriter.merge(Skills.learned, better).size)
        assertEquals("1. a\n2. b\n3. c", SkillWriter.merge(Skills.learned, better).single().playbook)
    }

    @Test fun systemPromptCarriesToolsAndSafety() {
        val p = SkillWriter.system(listOf("open_app", "tap"))
        assertTrue(p.contains("open_app, tap") && p.contains("never payments"))
    }
}

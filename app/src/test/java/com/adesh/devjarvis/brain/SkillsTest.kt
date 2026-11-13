package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    @Test fun moreThanTwoHundredFiftySkillsWithUniqueIdsAndRealSteps() {
        assertTrue(Skills.CORE.size >= 50)
        assertTrue(Skills.ALL.size - Skills.CORE.size >= 200)
        assertEquals(Skills.ALL.size, Skills.ALL.map { it.id }.toSet().size)
        Skills.ALL.forEach { s ->
            assertTrue(s.id, s.id.matches(Regex("[a-z_]+")))
            assertTrue(s.id, Skills.playbook(s).lines().count { it.trim().firstOrNull()?.isDigit() == true } >= 3)
        }
    }

    @Test fun findIsForgiving() {
        assertNotNull(Skills.find("Deep Research"))
        assertNotNull(Skills.find(" screen-operator "))
        assertNotNull(Skills.find("emi_calculator"))
        assertNull(Skills.find("teleport"))
    }

    @Test fun searchFindsByWords() {
        assertEquals("emi_calculator", Skills.search("EMI loan interest").first().id)
        assertEquals("wifi_fix", Skills.search("wifi not connecting").first().id)
        assertEquals("resume_writer", Skills.search("make my resume").first().id)
        assertEquals("deep_research", Skills.search("deep_research").single().id)
        assertTrue(Skills.search("zzzz qqqq").isEmpty())
    }

    @Test fun indexesListEverySkill() {
        val all = Skills.index() + Skills.libraryIndex()
        Skills.ALL.forEach { assertTrue(it.id, it.id in all) }
    }

    @Test fun hinglishWordsFindSkillsAndEveryPlaybookCarriesTheStandard() {
        assertTrue(Skills.search("kist kitni banegi").any { it.id == "emi_calculator" })
        assertTrue(Skills.search("neend nahi aati").any { it.id == "sleep_better" })
        assertTrue(Skills.playbook(Skills.ALL.first()).contains("write_skill"))
    }
}

package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    @Test fun moreThanTenSkillsWithUniqueIdsAndRealSteps() {
        assertTrue(Skills.ALL.size >= 12)
        assertEquals(Skills.ALL.size, Skills.ALL.map { it.id }.toSet().size)
        Skills.ALL.forEach { s ->
            assertTrue(s.id, s.id.matches(Regex("[a-z_]+")))
            assertTrue(s.id, Skills.playbook(s).lines().count { it.trim().firstOrNull()?.isDigit() == true } >= 3)
        }
    }

    @Test fun findIsForgiving() {
        assertNotNull(Skills.find("Deep Research"))
        assertNotNull(Skills.find(" screen-operator "))
        assertNull(Skills.find("teleport"))
    }

    @Test fun indexListsEverySkill() {
        val index = Skills.index()
        Skills.ALL.forEach { assertTrue(it.id in index) }
    }
}

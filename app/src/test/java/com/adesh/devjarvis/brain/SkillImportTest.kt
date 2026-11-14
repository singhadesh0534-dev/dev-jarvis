package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillImportTest {
    @Test fun manualSkill() {
        val s = SkillImport.manual("Shaadi ka budget", "jab shaadi ka kharcha plan karna ho", "Guest list lo\n2. Venue ka rate\n- Total jodo")!!
        assertEquals("shaadi_ka_budget", s.id)
        assertEquals("1. Guest list lo\n2. Venue ka rate\n3. Total jodo", s.playbook)
        assertNull(SkillImport.manual("", "x", "a"))
        assertTrue(SkillImport.idFrom("शादी").matches(Regex("[a-z][a-z_]{2,39}")))
    }

    @Test fun claudeStyleSkillMd() {
        val md = "---\nname: pdf-summary\ndescription: Summarise a PDF in Hinglish\n---\n# PDF summary\n\n1. Open the file\n2. Read every page\n3. Give five key points\n"
        val s = SkillImport.fromFile("pdf/SKILL.md", md).single()
        assertEquals("pdf_summary", s.id)
        assertEquals("Summarise a PDF in Hinglish", s.whenToUse)
        assertTrue(s.playbook.startsWith("1. Open the file"))
    }

    @Test fun jsonArray() {
        val js = """[{"name":"gym plan","when":"workout banana ho","steps":["warm up","chest","cardio"]},{"id":"bad"}]"""
        val list = SkillImport.fromFile("skills.json", js)
        assertEquals(1, list.size)
        assertEquals("gym_plan", list[0].id)
    }
}

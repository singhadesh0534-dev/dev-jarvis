package com.adesh.devjarvis.proactive


class BriefingSpecialDaysTest {
    @org.junit.Test fun findsBirthdaysAndInterests() {
        val facts = listOf("Sir's mother's birthday is 12 March", "Anniversary on March 3rd", "Sir likes cricket and AI", "Meeting on 12 March")
        org.junit.Assert.assertEquals(listOf("aaj: Sir's mother's birthday is 12 March"), BriefingTemplate.specialDaysOn(facts, 12, 3))
        org.junit.Assert.assertEquals(listOf("kal: Anniversary on March 3rd"), BriefingTemplate.specialDaysOn(facts, 3, 3, tomorrow = true))
        org.junit.Assert.assertTrue(BriefingTemplate.specialDaysOn(facts, 2, 3).isEmpty())
        org.junit.Assert.assertEquals(listOf("cricket", "ai"), BriefingTemplate.interests(facts))
    }
}

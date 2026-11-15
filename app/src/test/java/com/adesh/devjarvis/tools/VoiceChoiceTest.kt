package com.adesh.devjarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceChoiceTest {
    @Test fun eachRoleHasItsOwnVoiceSetting() {
        assertEquals("live_voice", VoiceChoice.itemFor("jarvis"))
        assertEquals("dost_voice", VoiceChoice.itemFor("dost"))
        assertEquals("gf_voice", VoiceChoice.itemFor("gf"))
    }

    @Test fun voiceNamesAreFoundInAnyCase() {
        assertEquals("Fenrir", VoiceChoice.voiceName("fenrir"))
        assertEquals("Kore", VoiceChoice.voiceName("Kore wali awaaz"))
        assertNull(VoiceChoice.voiceName("robot"))
    }

    @Test fun pitchWordsBecomeNumbers() {
        assertEquals(105, VoiceChoice.level("+5", 100, 50, 150))
        assertEquals(115, VoiceChoice.level("high", 100, 50, 150))
        assertEquals(85, VoiceChoice.level("thoda bhaari", 100, 50, 150))
        assertEquals(120, VoiceChoice.level("120", 100, 50, 150))
        assertEquals(100, VoiceChoice.level("normal", 130, 50, 150))
        assertEquals(150, VoiceChoice.level("+90", 100, 50, 150))
        assertNull(VoiceChoice.level("blue", 100, 50, 150))
    }

    @Test fun everyVoiceHasACharacterForChoosing() {
        (com.adesh.devjarvis.brain.Persona.HIS_VOICES + com.adesh.devjarvis.brain.Persona.HER_VOICES).forEach { v ->
            org.junit.Assert.assertTrue(v, VoiceChoice.CHARACTER.containsKey(v))
        }
        org.junit.Assert.assertTrue(VoiceChoice.described(listOf("Algenib")).contains("gravelly"))
    }
}

class VoiceDescriptionTest {
    @Test fun describedVoicesArePickedFromTheRightList() {
        val her = com.adesh.devjarvis.brain.Persona.HER_VOICES
        val strong = VoiceChoice.pick("tagdi wali awaaz", her, "Leda")!!
        org.junit.Assert.assertTrue(strong in her && strong != "Leda")
        org.junit.Assert.assertEquals("firm", VoiceChoice.CHARACTER[strong])
        val soft = VoiceChoice.pick("thodi soft aur meethi", her, "Kore")!!
        org.junit.Assert.assertTrue(soft in listOf("Achernar", "Vindemiatrix", "Sulafat"))
        org.junit.Assert.assertNull(VoiceChoice.pick("Aurora", her, "Kore"))
        org.junit.Assert.assertEquals("Kore", VoiceChoice.pick("kore", her, "Leda"))
    }
}

class OtherListVoiceTest {
    @Test fun aMansVoiceAskedForTheGfRoleBecomesTheClosestWomansVoice() {
        val her = com.adesh.devjarvis.brain.Persona.HER_VOICES
        val v = VoiceChoice.pick("Algenib", her, "Leda")!!
        org.junit.Assert.assertTrue(v in her && v != "Leda")
        org.junit.Assert.assertNull(VoiceChoice.pick("Arcturus", her, "Leda"))
    }
}

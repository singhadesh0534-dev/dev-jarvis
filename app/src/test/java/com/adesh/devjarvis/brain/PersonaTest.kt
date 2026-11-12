package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {
    @Test fun wordsMapToRoles() {
        assertEquals(Persona.PARTNER, Persona.normalize("Girlfriend"))
        assertEquals(Persona.DOST, Persona.normalize(" yaar "))
        assertEquals(Persona.JARVIS, Persona.normalize("something else"))
        assertEquals(Persona.JARVIS, Persona.normalize(null))
    }

    @Test fun jarvisHasNoOverlayOthersKeepTheRules() {
        assertEquals("", Persona.overlay(Persona.JARVIS))
        listOf(Persona.DOST, Persona.PARTNER).forEach {
            assertTrue(Persona.overlay(it).contains("safety rule"))
            assertTrue(Persona.overlay(it).contains("set_persona"))
        }
    }

    @Test fun rolesSwitchOffTheBriefButlerRulesAndPartnerStaysNonExplicit() {
        listOf(Persona.DOST, Persona.PARTNER).forEach {
            val o = Persona.overlay(it)
            assertTrue(o.contains("these earlier rules are OFF"))
            assertTrue(o.contains("no filler"))
        }
        val her = Persona.overlay(Persona.PARTNER)
        assertTrue(her.contains("nothing sexually explicit"))
        assertTrue(her.contains("lifts his mood"))
        assertTrue(her.contains("never pull him away from the real people"))
        assertTrue(Persona.isPartner("girlfriend"))
        assertTrue(!Persona.isPartner("dost") && !Persona.isPartner(null))
    }

    @Test fun fixedLinesChangeTheirAddressWithTheRole() {
        assertEquals("Volume badha diya, Sir.", Persona.address("Volume badha diya, Sir.", Persona.JARVIS))
        assertEquals("Volume badha diya, jaan.", Persona.address("Volume badha diya, Sir.", Persona.PARTNER))
        assertEquals("Bhai, network gayab hai.", Persona.address("Sir, network gayab hai.", Persona.DOST))
        assertEquals("Sirf ek minute, jaan.", Persona.address("Sirf ek minute, Sir.", "gf"))
    }

    @Test fun shortFormKeepsTheRulesThatMatter() {
        assertEquals("", Persona.overlay(Persona.JARVIS, compact = true))
        listOf(Persona.DOST, Persona.PARTNER).forEach {
            val short = Persona.overlay(it, compact = true)
            assertTrue(short.length < Persona.overlay(it).length / 2 || it == Persona.DOST)
            assertTrue(short.contains("safety rule") && short.contains("set_persona") && short.contains("OFF"))
        }
        assertTrue(Persona.overlay(Persona.PARTNER, compact = true).contains("nothing sexually explicit"))
        assertTrue(Persona.overlay(Persona.PARTNER).contains("How she sounds"))
    }

    @Test fun partnerGetsHerOwnVoice() {
        assertEquals("Charon", Persona.liveVoice(Persona.JARVIS, "Charon"))
        assertEquals("Charon", Persona.liveVoice(Persona.DOST, "Charon"))
        assertEquals("Aoede", Persona.liveVoice(Persona.PARTNER, "Charon"))
    }
}

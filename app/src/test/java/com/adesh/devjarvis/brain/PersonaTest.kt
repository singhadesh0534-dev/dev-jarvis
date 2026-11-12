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

    @Test fun partnerGetsHerOwnVoice() {
        assertEquals("Charon", Persona.liveVoice(Persona.JARVIS, "Charon"))
        assertEquals("Charon", Persona.liveVoice(Persona.DOST, "Charon"))
        assertEquals("Aoede", Persona.liveVoice(Persona.PARTNER, "Charon"))
    }
}

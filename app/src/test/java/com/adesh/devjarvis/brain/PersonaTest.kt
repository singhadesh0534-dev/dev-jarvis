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

    @Test fun hearsTheRoleInSirsOwnWords() {
        assertEquals(Persona.PARTNER, Persona.requested("gf ban ke baat karo"))
        assertEquals(Persona.PARTNER, Persona.requested("Jarvis, meri girlfriend ban jao"))
        assertEquals(Persona.PARTNER, Persona.requested("GF बन के बात करो"))
        assertEquals(Persona.PARTNER, Persona.requested("जीएफ बन जाओ"))
        assertEquals(Persona.PARTNER, Persona.requested("गर्लफ्रेंड की तरह बात करो"))
        assertEquals(Persona.PARTNER, Persona.requested("gf mode on karo"))
        assertEquals(Persona.DOST, Persona.requested("dost ban ke baat karo"))
        assertEquals(Persona.DOST, Persona.requested("दोस्त बनकर बात करो"))
        assertEquals(Persona.DOST, Persona.requested("ab yaar ki tarah baat kar"))
        assertEquals(Persona.JARVIS, Persona.requested("Jarvis ban jao"))
        assertEquals(Persona.JARVIS, Persona.requested("वापस जार्विस बन जाओ"))
        assertEquals(Persona.JARVIS, Persona.requested("normal ho jao"))
        assertEquals(Persona.JARVIS, Persona.requested("gf mode band karo"))
        assertEquals(Persona.JARVIS, Persona.requested("dost mat bano"))
    }

    @Test fun ordinaryTalkIsNotARoleRequest() {
        listOf(
            "Rahul mera dost ban gaya", "meri gf ko call karo", "dost ko message bhejo", "Jarvis focus mode on karo",
            "girlfriend ke liye gift batao", "volume badha do", "", "yaar aaj bahut thak gaya",
        ).forEach { assertEquals(it, null, Persona.requested(it)) }
    }

    @Test fun partnerGetsHerOwnVoice() {
        assertEquals("Charon", Persona.liveVoice(Persona.JARVIS, "Charon"))
        assertEquals("Charon", Persona.liveVoice(Persona.DOST, "Charon"))
        assertEquals("Aoede", Persona.liveVoice(Persona.PARTNER, "Charon"))
    }
}

package com.adesh.devjarvis.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SosTest {
    @Test fun contactsAreSplitTrimmedAndCapped() {
        assertEquals(listOf("Mummy", "98765 43210", "Papa"), Sos.contacts(" Mummy, 98765 43210; Papa ,, mummy"))
        assertEquals(emptyList<String>(), Sos.contacts("  "))
        assertEquals(Sos.MAX_CONTACTS, Sos.contacts("a,b,c,d,e,f,g").size)
    }

    @Test fun messageWithLocation() {
        val m = Sos.message(28.613939, 77.209021, 18.6f, "9:42 PM", "car accident")
        assertTrue(m.startsWith("EMERGENCY"))
        assertTrue("https://maps.google.com/?q=28.61394,77.20902" in m)
        assertTrue("+/- 18 m" in m)
        assertTrue("car accident" in m)
        assertTrue("9:42 PM" in m)
    }

    @Test fun messageWithoutLocationSaysSo() {
        val m = Sos.message(null, null, null, "9:42 PM")
        assertTrue("maps.google" !in m)
        assertTrue("please call me" in m)
    }
}

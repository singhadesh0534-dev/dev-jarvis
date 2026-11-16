package com.adesh.devjarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumbersTest {
    @Test fun whatsAppFormats() {
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("98765 43210"))
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("+91 98765-43210"))
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("09876543210"))
        assertEquals("14155550100", PhoneNumbers.forWhatsApp("+1 (415) 555-0100"))
    }

    @org.junit.Test fun shortServiceNumbersAreNumbersNotNames() {
        org.junit.Assert.assertEquals("101", PhoneNumbers.asDialable("101"))
        org.junit.Assert.assertEquals("108", PhoneNumbers.asDialable(" 1 0 8 "))
        org.junit.Assert.assertEquals("+919812345678", PhoneNumbers.asDialable("+91 98123-45678"))
        org.junit.Assert.assertNull(PhoneNumbers.asDialable("Kuldeep 100rupee"))
        org.junit.Assert.assertNull(PhoneNumbers.asDialable("12"))
        org.junit.Assert.assertNull(PhoneNumbers.asDialable("Mummy"))
    }
}

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
}

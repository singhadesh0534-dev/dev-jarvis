package com.adesh.devjarvis.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFilterTest {
    @Test fun otpAndBankingHidden() {
        assertTrue(SensitiveFilter.isSensitive("com.google.android.apps.messaging", "AX-HDFCBK", "Your OTP is 482913. Do not share."))
        assertTrue(SensitiveFilter.isSensitive("com.google.android.apps.messaging", "VM-SBIINB", "Rs.500 debited from A/c XX1234"))
        assertTrue(SensitiveFilter.isSensitive("com.google.android.apps.messaging", "Amazon", "482913 is your Amazon verification code"))
        assertTrue(SensitiveFilter.isSensitive("com.phonepe.app", "PhonePe", "Payment received"))
        assertTrue(SensitiveFilter.isSensitive("net.one97.paytm", "Paytm", "Cashback!"))
        assertTrue(SensitiveFilter.isSensitive("com.whatsapp", "Rahul", "bhai mera password bata"))
        assertTrue(SensitiveFilter.isSensitive("com.example.mybank", "Alert", "hello"))
    }

    @Test fun normalMessagesAllowed() {
        assertFalse(SensitiveFilter.isSensitive("com.whatsapp", "Mummy", "Khana kha liya?"))
        assertFalse(SensitiveFilter.isSensitive("com.google.android.gm", "Boss", "Meeting at 3 pm tomorrow"))
        assertFalse(SensitiveFilter.isSensitive("com.instagram.android", "priya", "liked your photo"))
    }
}

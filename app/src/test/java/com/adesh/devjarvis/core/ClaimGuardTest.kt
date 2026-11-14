package com.adesh.devjarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimGuardTest {
    @Test fun catchesDoneWithoutATool() {
        assertTrue(ClaimGuard.falseClaim("apni awaaz change karo", "Ji Sir, awaaz badal di.", 0))
        assertTrue(ClaimGuard.falseClaim("आवाज़ बदलो", "हो गया सर, आवाज़ बदल दी।", 0))
        assertTrue(ClaimGuard.falseClaim("wifi band karo", "Done, Sir.", 0))
    }

    @Test fun leavesRealWorkAndChatAlone() {
        assertFalse(ClaimGuard.falseClaim("wifi band karo", "Wi-Fi band kar diya, Sir.", 1))         // a tool ran
        assertFalse(ClaimGuard.falseClaim("kya haal hai", "Sab badhiya, Sir. Aap batao, kya ho gaya aaj?", 0)) // no request
        assertFalse(ClaimGuard.falseClaim("gf ban jao", "Ho gaya jaan.", 0, appActed = true))          // the app did it
        assertFalse(ClaimGuard.falseClaim("alarm laga do", "Kitne baje ka, Sir?", 0))                 // no claim
    }
}

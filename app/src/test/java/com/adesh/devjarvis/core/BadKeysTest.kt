package com.adesh.devjarvis.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadKeysTest {
    @Test fun marksClearsAndSavesOnlyFingerprints() {
        var saved = ""
        BadKeys.init("") { saved = it }
        BadKeys.mark("wrong-key-123")
        assertTrue(BadKeys.isBad("wrong-key-123"))
        assertTrue(BadKeys.isBad(" wrong-key-123 "))
        assertFalse(BadKeys.isBad("good-key"))
        assertFalse(BadKeys.isBad(""))
        assertFalse("wrong-key-123" in saved)
        assertTrue(saved.isNotEmpty())

        BadKeys.init(saved) { saved = it }          // app restart
        assertTrue(BadKeys.isBad("wrong-key-123"))
        BadKeys.clear("wrong-key-123")
        assertFalse(BadKeys.isBad("wrong-key-123"))
        assertTrue(saved.isEmpty())
    }
}

package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechSanitizerTest {

    @Test
    fun stripsMarkdown() {
        assertEquals("Bold and italic heading", SpeechSanitizer.clean("**Bold** and _italic_ ## heading"))
    }

    @Test
    fun stripsBullets() {
        assertEquals("first second", SpeechSanitizer.clean("- first\n2. second"))
    }

    @Test
    fun keepsLinkTextDropsUrl() {
        assertEquals("See the docs now", SpeechSanitizer.clean("See [the docs](https://x.y/z) https://a.b/c now"))
    }

    @Test
    fun removesEmoji() {
        assertEquals("Done, Sir.", SpeechSanitizer.clean("Done, Sir. 🚀👍🏽"))
    }

    @Test
    fun keepsDevanagariAndPunctuation() {
        assertEquals("ठीक है, Sir!", SpeechSanitizer.clean("ठीक है, Sir!"))
    }
}

package com.adesh.devjarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRouterTest {
    @Test fun knownSettingsGetDirectOrPage() {
        assertEquals(SettingsRouter.Direct.SCREEN_TIMEOUT, SettingsRouter.route("screen timeout").direct)
        assertEquals(SettingsRouter.Direct.FONT_SCALE, SettingsRouter.route("font size bada karo").direct)
        assertTrue(SettingsRouter.route("developer options kholo").pages.first().contains("DEVELOPMENT"))
        assertTrue(SettingsRouter.route("refresh rate 120hz").words.contains("refresh rate"))
    }

    @Test fun unknownSettingsStillGetSearchWords() {
        val r = SettingsRouter.route("game mode on karo")
        assertNull(r.direct)
        assertTrue(r.pages.isEmpty())
        assertTrue(r.words.contains("game mode"))
    }

    @Test fun resetIsNeverDone() {
        assertTrue(SettingsRouter.route("factory reset karo").forbidden)
        assertTrue(SettingsRouter.route("erase all data").forbidden)
    }

    @Test fun values() {
        assertEquals(120_000, SettingsRouter.timeoutMs("2 minute"))
        assertEquals(30_000, SettingsRouter.timeoutMs("30 sec"))
        assertEquals(30 * 60_000, SettingsRouter.timeoutMs("1 ghanta"))
        assertNull(SettingsRouter.timeoutMs("jaldi"))
        assertEquals(1.3f, SettingsRouter.fontScale("bada karo"))
        assertEquals(1.15f, SettingsRouter.fontScale("115"))
        assertEquals(1.0f, SettingsRouter.fontScale("normal"))
    }
}

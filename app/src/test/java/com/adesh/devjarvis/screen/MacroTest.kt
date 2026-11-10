package com.adesh.devjarvis.screen

import com.adesh.devjarvis.life.LifeCodec
import com.adesh.devjarvis.life.LifeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroTest {
    private fun el(id: Int, text: String = "", vid: String = "", click: Boolean = true, edit: Boolean = false, focused: Boolean = false) =
        ScreenElement(id, text, "", "android.widget.TextView", vid, click, edit, false, false, focused)

    @Test fun recordingDropsNoise() {
        var steps = emptyList<MacroStep>()
        listOf(
            MacroStep("open", app = "com.google.android.youtube"),
            MacroStep("open", app = "com.google.android.youtube"),                    // same window again
            MacroStep("tap", "com.google.android.youtube", "Search", "yt:id/search"),
            MacroStep("type", "com.google.android.youtube", viewId = "yt:id/box", text = "g"),
            MacroStep("type", "com.google.android.youtube", viewId = "yt:id/box", text = "gu"),
            MacroStep("type", "com.google.android.youtube", viewId = "yt:id/box", text = "guru randhawa"),
            MacroStep("open", app = "com.google.android.youtube"),                    // a new screen of the same app
            MacroStep("tap", "com.google.android.youtube"),                           // nothing to find it by
            MacroStep("tap", "com.google.android.youtube", "Guru Randhawa - Lahore", "yt:id/title"),
        ).forEach { steps = MacroLogic.add(steps, it) }
        assertEquals(listOf("open", "tap", "type", "tap"), steps.map { it.kind })
        assertEquals("guru randhawa", steps[2].text)
        assertEquals("open youtube → tap 'Search' → type 'guru randhawa' → tap 'Guru Randhawa - Lahore'", MacroLogic.describe(steps))
    }

    @Test fun stopsAtTheLimit() {
        var steps = emptyList<MacroStep>()
        repeat(MacroLogic.MAX_STEPS + 10) { steps = MacroLogic.add(steps, MacroStep("tap", "a", "button $it")) }
        assertEquals(MacroLogic.MAX_STEPS, steps.size)
    }

    @Test fun findsTheSameThingAgain() {
        val screen = ScreenSnapshot(
            "app", "App",
            listOf(el(1, "Home", "app:id/tab"), el(2, "Reports", "app:id/tab"), el(3, "", "app:id/fab"), el(4, "", "app:id/box", edit = true), el(5, "Send report")),
        )
        assertEquals(3, MacroLogic.locate(screen, MacroStep("tap", "app", "", "app:id/fab"))!!.id)           // unique id, no label
        assertEquals(2, MacroLogic.locate(screen, MacroStep("tap", "app", "Reports", "app:id/tab"))!!.id)    // shared id: the label decides
        assertEquals(5, MacroLogic.locate(screen, MacroStep("tap", "app", "Send report", "app:id/old"))!!.id) // id changed: by label
        assertEquals(4, MacroLogic.locate(screen, MacroStep("type", "app", viewId = "app:id/box", text = "x"))!!.id)
        assertNull(MacroLogic.locate(screen, MacroStep("tap", "app", "Settings", "app:id/tab")))             // not on this screen
    }

    @Test fun macrosSurviveSaving() {
        val steps = listOf(MacroStep("open", app = "com.app"), MacroStep("tap", "com.app", "Reports", "app:id/tab"), MacroStep("type", "com.app", "", "app:id/box", "hello"))
        val back = LifeCodec.decode(LifeCodec.encode(LifeData(macros = mapOf("office report" to steps))))
        assertEquals(steps, back.macros["office report"])
    }
}

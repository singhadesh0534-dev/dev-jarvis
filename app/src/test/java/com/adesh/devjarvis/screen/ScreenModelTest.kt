package com.adesh.devjarvis.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenModelTest {

    private fun el(id: Int, text: String = "", desc: String = "", cls: String = "android.widget.TextView", click: Boolean = false,
                   edit: Boolean = false, scroll: Boolean = false, pwd: Boolean = false, vid: String = "") =
        ScreenElement(id, text, desc, cls, vid, click, edit, scroll, pwd, focused = false)

    private val chat = ScreenSnapshot(
        "com.whatsapp", "WhatsApp",
        listOf(
            el(1, "Rahul"),
            el(2, "kal milte hain"),
            el(3, cls = "android.widget.EditText", edit = true, vid = "com.whatsapp:id/entry"),
            el(4, desc = "Send", cls = "android.widget.ImageButton", click = true),
            el(5, cls = "androidx.recyclerview.widget.RecyclerView", scroll = true),
        ),
    )

    @Test fun compactFormat() {
        val t = ScreenFormatter.compact(chat)
        assertTrue(t.startsWith("App: WhatsApp (com.whatsapp)"))
        assertTrue("[3] input \"entry\"" in t)
        assertTrue("[4] button \"Send\"" in t)
        assertTrue("[5] list" in t)
    }

    @Test fun readableText() {
        assertEquals("Rahul · kal milte hain · Send", ScreenFormatter.readable(chat))
    }

    @Test fun safety() {
        assertEquals(ScreenSafety.Verdict.Ok, ScreenSafety.check(chat))
        val login = chat.copy(elements = chat.elements + el(9, cls = "android.widget.EditText", edit = true, pwd = true))
        assertTrue(ScreenSafety.check(login) is ScreenSafety.Verdict.Blocked)
        val pay = ScreenSnapshot("com.example.shop", "Shop", listOf(el(1, "Proceed to Pay"), el(2, "₹499")))
        assertTrue(ScreenSafety.check(pay) is ScreenSafety.Verdict.Blocked)
        val bank = ScreenSnapshot("com.phonepe.app", "PhonePe", listOf(el(1, "Home")))
        assertTrue(ScreenSafety.check(bank) is ScreenSafety.Verdict.Blocked)
    }

    @Test fun finalActions() {
        assertEquals(FinalActionDetector.Kind.NEEDS_CONFIRMATION, FinalActionDetector.classify("Send"))
        assertEquals(FinalActionDetector.Kind.NEEDS_CONFIRMATION, FinalActionDetector.classify("Post"))
        assertEquals(FinalActionDetector.Kind.FORBIDDEN, FinalActionDetector.classify("Pay now"))
        assertEquals(FinalActionDetector.Kind.FORBIDDEN, FinalActionDetector.classify("Place order"))
        assertEquals(FinalActionDetector.Kind.NORMAL, FinalActionDetector.classify("Search"))
        assertEquals(FinalActionDetector.Kind.NORMAL, FinalActionDetector.classify("Rahul"))
    }

    @Test fun parseAgentActions() {
        assertEquals(AgentAction.Tap(4), AgentProtocol.parse("""{"action":"tap","id":4}"""))
        assertEquals(AgentAction.Tap(4), AgentProtocol.parse("""Sure! {"action":"click","id":"4"}"""))
        assertEquals(AgentAction.Type(3, "hi"), AgentProtocol.parse("""{"action":"type","id":3,"text":"hi"}"""))
        assertEquals(AgentAction.Type(null, "hi"), AgentProtocol.parse("""{"action":"type","text":"hi"}"""))
        assertEquals(AgentAction.Scroll(false), AgentProtocol.parse("""{"action":"scroll","direction":"up"}"""))
        assertEquals(AgentAction.OpenApp("WhatsApp"), AgentProtocol.parse("""{"action":"open_app","app":"WhatsApp"}"""))
        assertEquals(AgentAction.Done("sent"), AgentProtocol.parse("""```json
{"action":"done","note":"sent"}
```"""))
        assertNull(AgentProtocol.parse("""{"action":"tap"}"""))
        assertNull(AgentProtocol.parse("""{"action":"fly"}"""))
        assertNull(AgentProtocol.parse("no json"))
    }

    @Test fun loopDetection() {
        assertTrue(AgentProtocol.isLooping(listOf("open WhatsApp", "tap 'Rahul'", "tap 'Rahul'", "tap 'Rahul'")))
        assertFalse(AgentProtocol.isLooping(listOf("tap 'Rahul'", "type 'hi'", "tap 'Send'")))
    }

    @Test fun findByLabelPrefersClickable() {
        val s = ScreenSnapshot("x", "X", listOf(el(1, "Send"), el(2, "Send", click = true, cls = "android.widget.Button")))
        assertEquals(2, AgentProtocol.findByLabel(s, "send")!!.id)
        assertNull(AgentProtocol.findByLabel(s, "Delete account"))
    }
}

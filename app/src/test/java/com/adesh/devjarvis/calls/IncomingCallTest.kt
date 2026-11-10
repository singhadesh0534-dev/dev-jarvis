package com.adesh.devjarvis.calls

import com.adesh.devjarvis.calls.IncomingCall.Reply
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingCallTest {
    @Test fun answers() {
        listOf("utha lo", "Uthao", "haan utha lo na", "answer it", "pick up", "receive karo", "yes", "हाँ उठा लो", "ok")
            .forEach { assertEquals(it, Reply.ANSWER, IncomingCall.parse(it)) }
    }

    @Test fun rejects() {
        listOf("kaat do", "mat uthao", "nahi", "na uthana", "reject", "cut kar do", "ignore it", "काट दो", "मत उठाओ", "abhi busy hoon")
            .forEach { assertEquals(it, Reply.REJECT, IncomingCall.parse(it)) }
    }

    @Test fun unclear() {
        listOf(null, "", "kaun hai", "I know him", "handle").forEach { assertEquals(it.toString(), Reply.UNCLEAR, IncomingCall.parse(it)) }
    }

    @Test fun callerNames() {
        assertEquals("Mummy", IncomingCall.callerForSpeech(" Mummy "))
        assertEquals("ek unknown number", IncomingCall.callerForSpeech("+91 98765 43210"))
        assertEquals("kisi", IncomingCall.callerForSpeech(null))
    }

    @Test fun notificationButtons() {
        assertEquals(1, IncomingCall.pickAction(listOf("Decline", "Answer"), answer = true))
        assertEquals(0, IncomingCall.pickAction(listOf("Decline", "Answer"), answer = false))
        assertEquals(1, IncomingCall.pickAction(listOf("अस्वीकार करें", "स्वीकार करें"), answer = true))
        assertEquals(0, IncomingCall.pickAction(listOf("अस्वीकार करें", "स्वीकार करें"), answer = false))
        assertEquals(null, IncomingCall.pickAction(listOf("Mute", "Speaker"), answer = true))
    }
}

package com.adesh.devjarvis.tools

import com.adesh.devjarvis.tools.SpokenConfirmation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenConfirmationTest {
    private val q = "Sir, Mummy ko call karun?"

    @Test fun asksThenAcceptsYes() {
        val c = SpokenConfirmation()
        assertEquals(Outcome.Ask(q, false), c.request(q, 0))
        c.onModelSpoke()
        c.onUserSaid("haan kar do")
        assertEquals(Outcome.Answer(true), c.request(q, 3_000))
        assertNull(c.pendingQuestion)
    }

    @Test fun originalCommandCannotConfirmItself() {
        val c = SpokenConfirmation()
        c.request(q, 0)
        // Late transcript of the command arrives before JARVIS has asked anything.
        c.onUserSaid("mummy ko call karo")
        assertEquals(Outcome.Ask(q, false), c.request(q, 5_000))
    }

    @Test fun modelCannotCallAgainImmediately() {
        val c = SpokenConfirmation()
        c.request(q, 0)
        c.onModelSpoke()
        c.onUserSaid("haan")
        assertEquals(Outcome.Ask(q, true), c.request(q, 500)) // too fast to be a real answer
    }

    @Test fun noAndDevanagari() {
        val c = SpokenConfirmation()
        c.request(q, 0); c.onModelSpoke(); c.onUserSaid("नहीं रहने दो")
        assertEquals(Outcome.Answer(false), c.request(q, 4_000))
        c.request(q, 10_000); c.onModelSpoke(); c.onUserSaid("हाँ")
        assertEquals(Outcome.Answer(true), c.request(q, 14_000))
    }

    @Test fun unclearAsksAgainAndTapWins() {
        val c = SpokenConfirmation()
        c.request(q, 0); c.onModelSpoke(); c.onUserSaid("kya bola")
        assertEquals(Outcome.Ask(q, true), c.request(q, 4_000))
        c.onTap(true)
        assertEquals(Outcome.Answer(true), c.request(q, 4_100))
    }

    @Test fun differentQuestionOrExpiredStartsOver() {
        val c = SpokenConfirmation(validForMs = 10_000)
        c.request(q, 0); c.onModelSpoke(); c.onUserSaid("haan")
        assertEquals(Outcome.Ask("Sir, SMS bhej doon?", false), c.request("Sir, SMS bhej doon?", 3_000))
        c.onModelSpoke(); c.onUserSaid("haan")
        assertEquals(Outcome.Ask("Sir, SMS bhej doon?", false), c.request("Sir, SMS bhej doon?", 60_000))
    }
}

class SpokenConfirmationLoopTest {
    private val q = "Sir, Papa ko call karun?"

    @Test fun missingTranscriptDoesNotLoopForEver() {
        val c = SpokenConfirmation()
        c.request(q, 0); c.onModelSpoke()
        // Sir answered, JARVIS heard it, but the app got no transcript of the answer.
        assertEquals(SpokenConfirmation.Outcome.Ask(q, false), c.request(q, 4_000))
        assertEquals(SpokenConfirmation.Outcome.Answer(true), c.request(q, 9_000)) // second try: trust JARVIS
    }

    @Test fun aClearNoStillWinsOnTheSecondTry() {
        val c = SpokenConfirmation()
        c.request(q, 0); c.onModelSpoke()
        c.request(q, 4_000)
        c.onUserSaid("nahi rehne do")
        assertEquals(SpokenConfirmation.Outcome.Answer(false), c.request(q, 9_000))
    }

    @Test fun tooFastRepeatsNeverCountAsAgreement() {
        val c = SpokenConfirmation()
        c.request(q, 0); c.onModelSpoke()
        assertEquals(SpokenConfirmation.Outcome.Ask(q, false), c.request(q, 300))
        assertEquals(SpokenConfirmation.Outcome.Ask(q, false), c.request(q, 600))
        assertEquals(SpokenConfirmation.Outcome.Ask(q, false), c.request(q, 900))
    }
}

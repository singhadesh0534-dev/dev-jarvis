package com.adesh.devjarvis.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private data class C(val name: String)

    private val contacts = listOf(
        C("Mummy Ji ❤️"), C("Papa"), C("Papa Office"), C("Bhai (Rahul)"), C("Rahul Sharma"),
        C("Priya Didi"), C("Dr. Mehta"), C("Amit Kumar"),
    )

    private fun best(q: String) = FuzzyMatcher.rank(q, contacts) { listOf(it.name) }.firstOrNull()?.item?.name

    @Test fun exactWordInsideDecoratedName() = assertEquals("Mummy Ji ❤️", best("Mummy"))
    @Test fun nicknameAlias() = assertEquals("Mummy Ji ❤️", best("mom"))
    @Test fun relationAliasHindi() = assertEquals("Mummy Ji ❤️", best("maa"))
    @Test fun exactBeatsLonger() = assertEquals("Papa", best("papa"))
    @Test fun tokensAnyOrder() = assertEquals("Bhai (Rahul)", best("rahul bhai"))
    @Test fun typo() = assertEquals("Mummy Ji ❤️", best("mumy"))
    @Test fun aliasBro() = assertEquals("Bhai (Rahul)", best("bro"))
    @Test fun sisterToDidi() = assertEquals("Priya Didi", best("sister"))
    @Test fun punctuationIgnored() = assertEquals("Dr. Mehta", best("dr mehta"))

    @Test fun unknownNameNoMatch() {
        assertTrue(FuzzyMatcher.rank("Zubair", contacts) { listOf(it.name) }.isEmpty())
    }

    @Test fun ambiguousFirstName() {
        val matches = FuzzyMatcher.rank("rahul", contacts) { listOf(it.name) }
        assertTrue(FuzzyMatcher.isAmbiguous(matches) { a, b -> a.name == b.name })
    }

    @Test fun clearWinnerNotAmbiguous() {
        val matches = FuzzyMatcher.rank("papa", contacts) { listOf(it.name) }
        assertFalse(FuzzyMatcher.isAmbiguous(matches) { a, b -> a.name == b.name })
    }

    @Test fun appAliases() {
        val apps = listOf("YouTube", "YouTube Music", "Instagram", "Google Pay", "WhatsApp", "Gallery")
        fun app(q: String) = FuzzyMatcher.rank(q, apps) { listOf(it) }.first().item
        assertEquals("YouTube", app("youtube"))
        assertEquals("YouTube", app("yt"))
        assertEquals("Instagram", app("insta"))
        assertEquals("Google Pay", app("gpay"))
        assertEquals("WhatsApp", app("watsapp"))
        assertEquals("YouTube Music", app("youtube music"))
    }

    @Test fun jaroWinklerSane() {
        assertEquals(1.0, FuzzyMatcher.jaroWinkler("abc", "abc"), 1e-9)
        assertTrue(FuzzyMatcher.jaroWinkler("martha", "marhta") > 0.95)
        assertEquals(0.0, FuzzyMatcher.jaroWinkler("abc", "xyz"), 1e-9)
    }
}

class HindiNameMatchTest {
    private data class C(val name: String)
    private val contacts = listOf(C("Papa"), C("Mummy Ji"), C("Rahul Sharma"), C("पूजा दीदी"), C("Chacha Ji Kanpur"), C("Amit Kumar"))
    private fun best(q: String) = FuzzyMatcher.rank(q, contacts) { listOf(it.name) }.firstOrNull()?.item?.name

    @Test fun transliteration() {
        assertEquals("paapaa", Translit.toLatin("पापा"))
        assertEquals("raam", Translit.toLatin("राम"))
        assertEquals("mammee jee", Translit.toLatin("मम्मी जी"))
        assertEquals("Papa", Translit.toLatin("Papa"))
        assertEquals("papa", Translit.fold("पापा"))
        assertEquals(Translit.fold("pappa"), Translit.fold("Paapa"))
    }

    @Test fun hindiScriptQueryFindsEnglishContact() {
        assertEquals("Papa", best("पापा"))
        assertEquals("Mummy Ji", best("मम्मी"))
        assertEquals("Rahul Sharma", best("राहुल शर्मा"))
    }

    @Test fun englishQueryFindsHindiScriptContact() = assertEquals("पूजा दीदी", best("pooja didi"))

    @Test fun fillerWordsAreIgnored() {
        assertEquals("Papa", best("mere papa ji ko"))
        assertEquals("Papa", best("Papa ji"))
        assertEquals("Mummy Ji", best("apni mummy"))
        assertEquals("Chacha Ji Kanpur", best("chacha ji"))
    }

    @Test fun spellingVariants() {
        assertEquals("Papa", best("pappa"))
        assertEquals("Papa", best("paapa"))
        assertEquals("Amit Kumar", best("ameet kumaar"))
    }
}

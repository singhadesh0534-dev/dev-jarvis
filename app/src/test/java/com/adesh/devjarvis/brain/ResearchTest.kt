package com.adesh.devjarvis.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchTest {
    @Test fun queriesAndFallback() {
        assertEquals(listOf("a b", "c d"), Research.parseQueries("""x {"queries":["a b","c d","a b"]} y""", "q"))
        assertEquals(listOf("q"), Research.parseQueries("nope", "q"))
    }

    @Test fun siteNames() {
        assertEquals("wikipedia.org", Research.siteName("wikipedia.org", "https://vertexaisearch.cloud.google.com/grounding-api-redirect/X"))
        assertEquals("thehindu.com", Research.siteName("", "https://www.thehindu.com/news/x"))
    }

    @Test fun groundingSourcesAreParsed() {
        val c = GeminiStreamParser.parseLine("""data: {"candidates":[{"content":{"parts":[{"text":"hi"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://r/1","title":"bbc.com"}}]}}]}""")!!
        assertEquals(listOf("bbc.com" to "https://r/1"), c.sources)
    }
}

package com.adesh.devjarvis.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingsTest {
    @Test fun requestAndParse() {
        val b = Embeddings.batchBody(listOf("a", "b"), query = true)
        assertTrue(b.contains("RETRIEVAL_QUERY")); assertTrue(b.contains("\"outputDimensionality\":256"))
        val v = Embeddings.parseBatch("""{"embeddings":[{"values":[3,4]},{"values":[0,2]}]}""")!!
        assertEquals(0.6f, v[0][0], 1e-6f); assertEquals(1f, v[1][1], 1e-6f)
    }

    @Test fun vectorsRoundTripAndCompare() {
        val v = Embeddings.normalize(floatArrayOf(1f, 2f, 2f))
        val e = Embeddings.parseLine(Embeddings.line(7, "k1", v))
        assertNotNull(e); assertEquals(7L, e!!.id); assertEquals("k1", e.textKey)
        assertEquals(1.0, Embeddings.cosine(v, e.vector), 1e-6)
        assertEquals(0.0, Embeddings.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-9)
    }
}

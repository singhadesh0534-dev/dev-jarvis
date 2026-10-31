package com.adesh.devjarvis.brain

/**
 * Streaming text ko bolne-layak sentences mein todta hai.
 * Jaise hi ek sentence poora hota hai, use TTS ko de dete hain — isse pehla shabd ~1 sec mein aata hai,
 * poore jawab ka intezaar nahi karna padta.
 *
 * Pehle tukde ke liye comma pe bhi tod dete hain (agar kaafi lamba ho), taaki JARVIS jaldi bolna shuru kare.
 */
class SentenceChunker(
    private val firstChunkSoftLimit: Int = 60,
    private val hardLimit: Int = 220,
) {
    private val buffer = StringBuilder()
    private var emittedAny = false

    /** Feed a streamed delta; returns zero or more complete, speakable pieces. */
    fun feed(delta: String): List<String> {
        buffer.append(delta)
        val out = mutableListOf<String>()
        while (true) {
            val cut = findCut() ?: break
            val piece = buffer.substring(0, cut).trim()
            buffer.delete(0, cut)
            if (piece.isNotEmpty()) {
                out += piece
                emittedAny = true
            }
        }
        return out
    }

    /** Whatever is left when the stream ends. */
    fun flush(): String? {
        val rest = buffer.toString().trim()
        buffer.clear()
        return rest.ifEmpty { null }
    }

    private fun findCut(): Int? {
        val s = buffer
        for (i in s.indices) {
            val c = s[i]
            if (c == '\n') return i + 1
            if (c in SENTENCE_END) {
                // Need to see the next char to know the sentence really ended ("3.5", "Dr.").
                if (i + 1 >= s.length) return null
                val next = s[i + 1]
                if (!next.isWhitespace() && next !in CLOSERS) continue
                if (c == '.' && endsWithAbbreviation(s, i)) continue
                var end = i + 1
                while (end < s.length && s[end] in CLOSERS) end++
                return end
            }
            if (!emittedAny && c in SOFT_BREAKS && i >= firstChunkSoftLimit &&
                i + 1 < s.length && s[i + 1].isWhitespace()
            ) return i + 1
        }
        if (s.length >= hardLimit) {
            val space = s.lastIndexOf(' ', hardLimit)
            return if (space > 0) space + 1 else hardLimit
        }
        return null
    }

    private fun endsWithAbbreviation(s: CharSequence, dotIndex: Int): Boolean {
        var start = dotIndex
        while (start > 0 && s[start - 1].isLetter()) start--
        val word = s.substring(start, dotIndex).lowercase()
        return word in ABBREVIATIONS
    }

    private companion object {
        val SENTENCE_END = setOf('.', '!', '?', '।', '…')
        val SOFT_BREAKS = setOf(',', ';', ':', '—')
        val CLOSERS = setOf('"', '\'', ')', '”', '’')
        val ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "st", "sr", "jr", "vs", "etc", "e.g", "i.e", "no")
    }
}

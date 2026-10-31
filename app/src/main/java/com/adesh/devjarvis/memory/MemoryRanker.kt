package com.adesh.devjarvis.memory

import com.adesh.devjarvis.tools.FuzzyMatcher
import kotlin.math.exp

/** Memory store se pure function ka view (Room entity se independent, taaki test ho sake). */
data class Fact(val id: Long, val text: String, val category: String, val created: Long, val lastUsed: Long)

/**
 * Har turn ke liye top-5 kaam ki yaadein: keyword match (rishton ke aliases ke saath) + recency.
 * Embeddings baad mein; abhi tez aur free. Pure, unit-tested.
 */
object MemoryRanker {

    private val stopwords = setOf(
        "the", "a", "an", "is", "are", "to", "of", "and", "or", "in", "on", "for", "my", "me", "i", "you", "it",
        "kya", "hai", "hain", "ka", "ki", "ke", "ko", "se", "mein", "main", "mera", "meri", "mere", "aur", "bhi",
        "toh", "to", "ye", "yeh", "wo", "woh", "kar", "karo", "do", "please", "jarvis", "sir", "sirs", "sir's",
    )

    fun keywords(text: String): Set<String> =
        FuzzyMatcher.normalize(text).split(' ').filter { it.length >= 2 && it !in stopwords }.toSet()

    /** Expand "mom" -> {mom, mummy, maa, ...} so "mummy ka birthday" finds "Sir's mom ...". */
    private fun expand(words: Set<String>): Set<String> =
        words.flatMap { w -> FuzzyMatcher.ALIAS_GROUPS.firstOrNull { w in it } ?: setOf(w) }.toSet()

    fun score(fact: Fact, queryWords: Set<String>, now: Long): Double {
        val factWords = keywords(fact.text)
        val q = expand(queryWords)
        val overlap = factWords.count { fw -> fw in q || q.any { it.length >= 4 && fw.startsWith(it) } }
        val keyword = if (queryWords.isEmpty()) 0.0 else overlap.toDouble()
        val days = (now - fact.lastUsed).coerceAtLeast(0) / 86_400_000.0
        val recency = exp(-days / 30.0) * 0.5 // 0.5 today -> ~0.18 after a month
        val categoryBoost = if (fact.category == "person" || fact.category == "preference") 0.15 else 0.0
        return keyword * 1.0 + recency + categoryBoost
    }

    /** Top [limit]. With a query, facts need at least one keyword hit unless we have room to spare. */
    fun top(facts: List<Fact>, query: String, now: Long, limit: Int = 5): List<Fact> {
        val q = keywords(query)
        val scored = facts.map { it to score(it, q, now) }.sortedByDescending { it.second }
        if (q.isEmpty()) return scored.take(limit).map { it.first }
        val hits = scored.filter { it.second >= 1.0 }.map { it.first }
        val fill = scored.map { it.first }.filter { it !in hits && (it.category == "person" || it.category == "preference") }
        return (hits + fill).take(limit)
    }

    /** True if [candidate] says the same thing as an existing fact (skip duplicates). */
    fun isDuplicate(candidate: String, existing: List<Fact>): Fact? =
        existing.firstOrNull { FuzzyMatcher.score(candidate, it.text) >= 0.9 }
}

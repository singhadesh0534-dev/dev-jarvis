package com.adesh.devjarvis.memory

import com.adesh.devjarvis.data.ConversationDao
import com.adesh.devjarvis.data.ConversationEntity
import com.adesh.devjarvis.data.FactDao
import com.adesh.devjarvis.data.FactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** JARVIS ki lambi yaaddasht: facts + baat-cheet ke summaries (Room, sirf phone pe). */
@Singleton
class MemoryRepository @Inject constructor(
    private val facts: FactDao,
    private val conversations: ConversationDao,
    private val meaning: SemanticIndex,
) {
    val allFacts: Flow<List<Fact>> = facts.observeAll().map { list -> list.map { it.toFact() } }
    val recentSummaries: Flow<List<ConversationEntity>> = conversations.observeRecent()

    /** Adds a fact unless an equivalent one exists (then just marks it used). Returns the stored fact. */
    suspend fun add(text: String, category: String): Fact {
        val now = System.currentTimeMillis()
        val existing = facts.all().map { it.toFact() }
        MemoryRanker.isDuplicate(text, existing)?.let { dup ->
            facts.touch(listOf(dup.id), now)
            return dup
        }
        val cat = category.lowercase().takeIf { it in MemoryExtraction.CATEGORIES } ?: "other"
        val id = facts.insert(FactEntity(text = text.trim(), category = cat, created = now, lastUsed = now))
        return Fact(id, text.trim(), cat, now, now)
    }

    suspend fun update(fact: Fact, newText: String) {
        facts.update(FactEntity(fact.id, newText.trim(), fact.category, fact.created, System.currentTimeMillis()))
    }

    /** Top facts for this turn; marks them used so they stay "fresh". */
    suspend fun relevant(query: String, limit: Int = 5): List<Fact> {
        val now = System.currentTimeMillis()
        val all = facts.all().map { it.toFact() }
        // Meaning search on top of keywords (a short wait at most; on any trouble it is keywords only).
        val sims = if (query.isBlank()) emptyMap()
        else kotlinx.coroutines.withTimeoutOrNull(2_500) { runCatching { meaning.similarities(query, all) }.getOrNull() }.orEmpty()
        val top = MemoryRanker.top(all, query, now, limit, sims)
        if (top.isNotEmpty() && query.isNotBlank()) facts.touch(top.map { it.id }, now)
        return top
    }

    suspend fun all(): List<Fact> = facts.all().map { it.toFact() }

    suspend fun delete(id: Long) = facts.delete(id)

    suspend fun forgetEverything() {
        facts.deleteAll()
        conversations.deleteAll()
    }

    suspend fun addSummary(startedAt: Long, summary: String) {
        if (summary.isNotBlank()) conversations.insert(ConversationEntity(startedAt = startedAt, summary = summary))
    }

    private fun FactEntity.toFact() = Fact(id, text, category, created, lastUsed)
}

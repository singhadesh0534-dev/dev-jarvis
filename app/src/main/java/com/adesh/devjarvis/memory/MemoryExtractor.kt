package com.adesh.devjarvis.memory

import com.adesh.devjarvis.brain.JarvisBrain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Baat-cheet khatam hone ke baad: LLM se durable facts + ek line summary nikaal ke save. */
@Singleton
class MemoryExtractor @Inject constructor(
    private val brain: JarvisBrain,
    private val memory: MemoryRepository,
) {
    private val lock = Mutex()

    suspend fun run(conversationStartedAt: Long) = lock.withLock {
        val exchanges = brain.takeForMemory().filter { (u, j) -> u.length > 2 && j.length > 2 }
        if (exchanges.isEmpty()) return@withLock
        // Quota: a lone short command ("torch on") has nothing worth remembering — don't spend a request on it.
        if (exchanges.size == 1 && exchanges[0].first.length < 45) return@withLock
        val existing = memory.all().map { it.text }
        val raw = brain.complete(MemoryExtraction.systemPrompt(existing), MemoryExtraction.userPrompt(exchanges), json = true)
        val result = MemoryExtraction.parse(raw) ?: return@withLock
        result.facts.forEach { (text, category) -> memory.add(text, category) }
        memory.addSummary(conversationStartedAt, result.summary)
    }
}

package com.adesh.devjarvis.memory

import android.content.Context
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.core.ActivityLog
import com.adesh.devjarvis.core.SessionTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baat-cheet khatam hone ke baad: LLM se durable facts + ek line summary nikaal ke save. Every few days also a careful
 * clean-up of the memory (true duplicates merged, facts a newer one replaced removed; never lessons).
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val brain: JarvisBrain,
    private val memory: MemoryRepository,
    private val activityLog: ActivityLog,
    @ApplicationContext context: Context,
) {
    private val lock = Mutex()
    private val prefs = context.getSharedPreferences("memory_hygiene", Context.MODE_PRIVATE)

    suspend fun run(conversationStartedAt: Long) = lock.withLock {
        extract(conversationStartedAt)
        runCatching { tidyIfDue() }
    }

    private suspend fun extract(conversationStartedAt: Long) {
        val exchanges = brain.takeForMemory().filter { (u, j) -> u.length > 2 && j.length > 2 }
        if (exchanges.isEmpty()) return
        // Quota: a lone short command ("torch on") has nothing worth remembering — don't spend a request on it.
        if (exchanges.size == 1 && exchanges[0].first.length < 45) return
        val existing = memory.all().map { it.text }
        val raw = brain.complete(MemoryExtraction.systemPrompt(existing), MemoryExtraction.userPrompt(exchanges), json = true)
        val result = MemoryExtraction.parse(raw) ?: return
        result.facts.forEach { (text, category) -> memory.add(text, category) }
        memory.addSummary(conversationStartedAt, result.summary)
    }

    private suspend fun tidyIfDue() {
        val now = System.currentTimeMillis()
        val facts = memory.all()
        if (!MemoryHygiene.due(prefs.getLong(K_LAST, 0L), now, facts.size)) return
        prefs.edit().putLong(K_LAST, now).apply() // once per period, even if the brain is busy
        val raw = brain.complete(MemoryHygiene.PROMPT, MemoryHygiene.listing(facts.takeLast(300)), json = true) ?: return
        val plan = MemoryHygiene.parse(raw, facts)
        if (plan.size == 0) return
        val byId = facts.associateBy { it.id }
        plan.merges.forEach { m ->
            val keep = byId[m.ids.first()] ?: return@forEach
            memory.update(keep, m.text)
            m.ids.drop(1).forEach { memory.delete(it) }
            activityLog.add("memory_cleanup", "merged ${m.ids.size}", true, "Merged into: ${m.text}")
        }
        plan.removes.forEach { r ->
            val old = byId[r.id] ?: return@forEach
            memory.delete(r.id)
            activityLog.add("memory_cleanup", "removed", true, "Removed outdated: ${old.text.take(120)} (${r.because})")
        }
        SessionTrace.add("MEMORY tidied: ${plan.merges.size} merged, ${plan.removes.size} outdated removed")
    }

    private companion object {
        const val K_LAST = "last_run"
    }
}

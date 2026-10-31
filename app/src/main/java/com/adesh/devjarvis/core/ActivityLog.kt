package com.adesh.devjarvis.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityEntry(
    val id: Long,
    val timeMillis: Long,
    val tool: String,
    val args: String,
    val ok: Boolean,
    val result: String,
)

/** Har action ka record — "Activity log" screen pe dikhta hai. (Phase 5 mein DB mein jayega.) */
@Singleton
class ActivityLog @Inject constructor() {
    private val _entries = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val entries: StateFlow<List<ActivityEntry>> = _entries.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun add(tool: String, args: String, ok: Boolean, result: String) {
        val e = ActivityEntry(nextId++, System.currentTimeMillis(), tool, args, ok, result)
        _entries.update { (listOf(e) + it).take(MAX) }
    }

    fun clear() = _entries.update { emptyList() }

    private companion object {
        const val MAX = 300
    }
}

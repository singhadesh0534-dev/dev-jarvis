package com.adesh.devjarvis.core

import com.adesh.devjarvis.data.ActivityDao
import com.adesh.devjarvis.data.ActivityEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

/** Har action ka record (Room mein, aakhri 300) — "Activity log" screen pe dikhta hai. */
@Singleton
class ActivityLog @Inject constructor(private val dao: ActivityDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val entries: StateFlow<List<ActivityEntry>> = dao.observeRecent()
        .map { list -> list.map { ActivityEntry(it.id, it.timeMillis, it.tool, it.args, it.ok, it.result) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun add(tool: String, args: String, ok: Boolean, result: String) {
        scope.launch {
            dao.insert(ActivityEntity(timeMillis = System.currentTimeMillis(), tool = tool, args = args, ok = ok, result = result))
            dao.trim()
        }
    }

    fun clear() {
        scope.launch { dao.deleteAll() }
    }
}

package com.adesh.devjarvis.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ek notification jo JARVIS dekh sakta hai (sensitive wale yahan aate hi nahi). */
data class SeenNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    /** Reply action (WhatsApp/Messages etc.), if the app offers one. */
    val reply: ReplyAction?,
)

class ReplyAction(val intent: PendingIntent, val remoteInput: RemoteInput)

/**
 * Haal ke notifications ka chhota in-memory store (kabhi disk pe nahi likhte).
 * Sensitive ones (OTP, banking) are only counted, never stored.
 */
@Singleton
class NotificationStore @Inject constructor() {
    private val items = LinkedHashMap<String, SeenNotification>()
    @Volatile var hiddenSensitiveCount = 0
        private set

    private val _arrivals = MutableSharedFlow<SeenNotification>(extraBufferCapacity = 16)
    /** New (non-sensitive) notifications as they arrive — for spoken alerts. */
    val arrivals: SharedFlow<SeenNotification> = _arrivals.asSharedFlow()

    @Volatile var listenerConnected = false

    @Synchronized
    fun put(n: SeenNotification, isNew: Boolean) {
        items.remove(n.key)
        items[n.key] = n
        while (items.size > MAX) items.remove(items.keys.first())
        if (isNew) _arrivals.tryEmit(n)
    }

    @Synchronized
    fun remove(key: String) {
        items.remove(key)
    }

    fun countSensitive() {
        hiddenSensitiveCount++
    }

    @Synchronized
    fun recent(app: String? = null, limit: Int = 5): List<SeenNotification> {
        val all = items.values.sortedByDescending { it.time }
        val filtered = if (app.isNullOrBlank()) all else all.filter {
            com.adesh.devjarvis.tools.FuzzyMatcher.score(app, it.appLabel) >= 0.75 ||
                com.adesh.devjarvis.tools.FuzzyMatcher.score(app, it.title) >= 0.75
        }
        return filtered.take(limit)
    }

    /** Short lines for the brain's context block. */
    fun topForContext(limit: Int): List<String> = recent(limit = limit).map { "${it.appLabel} — ${it.title}: ${it.text.take(90)}" }

    private companion object {
        const val MAX = 50
    }
}

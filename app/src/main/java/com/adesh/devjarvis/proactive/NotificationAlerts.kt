package com.adesh.devjarvis.proactive

import com.adesh.devjarvis.notifications.NotificationStore
import com.adesh.devjarvis.notifications.SeenNotification
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.FuzzyMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Important notifications (chune hue apps / log) ka chhota spoken summary.
 * Sensitive ones never reach here (NotificationStore drops them). Max one alert per 30 s.
 */
@Singleton
class NotificationAlerts @Inject constructor(
    private val store: NotificationStore,
    private val settings: SettingsRepository,
    private val speaker: ProactiveSpeaker,
) {
    private var lastAlertAt = 0L
    private val spokenKeys = ArrayDeque<String>()

    fun start(scope: CoroutineScope): Job = scope.launch {
        store.arrivals.collect { n -> if (isImportant(n)) alert(n) }
    }

    private fun splitList(csv: String) = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun isImportant(n: SeenNotification): Boolean {
        val s = settings.current
        if (!s.notificationAlerts) return false
        val contacts = splitList(s.importantContacts)
        val apps = splitList(s.importantApps)
        val fromContact = contacts.any { FuzzyMatcher.score(it, n.title) >= 0.85 }
        val fromApp = apps.any { FuzzyMatcher.score(it, n.appLabel) >= 0.85 }
        // A whitelisted person always counts; a whitelisted app only for direct messages (they have a reply action).
        return fromContact || (fromApp && n.reply != null)
    }

    private suspend fun alert(n: SeenNotification) {
        val now = System.currentTimeMillis()
        if (n.key in spokenKeys || now - lastAlertAt < 30_000) return
        lastAlertAt = now
        spokenKeys.addLast(n.key)
        while (spokenKeys.size > 30) spokenKeys.removeFirst()
        val preview = n.text.replace('\n', ' ').take(80)
        speaker.offer(ProactivePolicy.Kind.NOTIFICATION, "Sir, ${n.title} ka ${n.appLabel} pe message: $preview")
    }
}

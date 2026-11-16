package com.adesh.devjarvis.proactive

import com.adesh.devjarvis.notifications.NotificationStore
import com.adesh.devjarvis.notifications.NotificationTriage
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
        if (fromContact || (fromApp && n.reply != null)) return true
        // Also anyone with an urgent message, a missed call, or one person messaging again and again.
        val item = NotificationTriage.Item(n.key, n.appLabel, n.title, n.text, n.time, n.reply != null)
        if (NotificationTriage.level(item, contacts, apps) == NotificationTriage.Level.IMPORTANT) return true
        return n.reply != null && NotificationTriage.isBurst(store.triageItems(), n.title, System.currentTimeMillis())
    }

    private suspend fun alert(n: SeenNotification) {
        val now = System.currentTimeMillis()
        if (n.key in spokenKeys || now - lastAlertAt < 30_000) return
        lastAlertAt = now
        spokenKeys.addLast(n.key)
        while (spokenKeys.size > 30) spokenKeys.removeFirst()
        val preview = n.text.replace('\n', ' ').take(80)
        val urgent = NotificationTriage.isUrgent("${n.title} ${n.text}")
        val burst = n.reply != null && NotificationTriage.isBurst(store.triageItems(), n.title, now)
        val lead = when {
            urgent -> "Boss, zaroori lag raha hai — "
            burst -> "Boss, ${n.title} baar-baar message kar rahe hain — "
            else -> "Sir, "
        }
        speaker.offer(ProactivePolicy.Kind.NOTIFICATION, "$lead${n.title} ka ${n.appLabel} pe message: $preview")
    }
}

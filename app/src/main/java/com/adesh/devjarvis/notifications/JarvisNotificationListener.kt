package com.adesh.devjarvis.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Notifications padhna (sirf phone pe, memory mein). OTP / bank / payment wale turant chhod diye jaate hain —
 * na bole jaate, na dikhte, na LLM ko jaate.
 */
@AndroidEntryPoint
class JarvisNotificationListener : NotificationListenerService() {

    @Inject lateinit var store: NotificationStore

    override fun onListenerConnected() {
        store.listenerConnected = true
        runCatching { activeNotifications?.forEach { handle(it, isNew = false) } }
    }

    override fun onListenerDisconnected() {
        store.listenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, isNew = true)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = store.remove(sbn.key)

    private fun handle(sbn: StatusBarNotification, isNew: Boolean) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        if (n.visibility == Notification.VISIBILITY_SECRET || SensitiveFilter.isSensitive(sbn.packageName, title, text)) {
            store.countSensitive()
            return
        }
        val label = runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(sbn.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(sbn.packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)
        val reply = n.actions?.firstNotNullOfOrNull { a ->
            a.remoteInputs?.firstOrNull { it.allowFreeFormInput }?.let { ReplyAction(a.actionIntent, it) }
        }
        store.put(SeenNotification(sbn.key, sbn.packageName, label, title, text, sbn.postTime, reply), isNew)
    }
}

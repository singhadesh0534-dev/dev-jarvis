package com.adesh.devjarvis.tools

import android.Manifest
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.R
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.core.AppVisibility
import com.adesh.devjarvis.service.ServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Doosre apps / screens kholna. Android background se activity kholne nahi deta jab tak:
 * JARVIS ki screen dikh rahi ho, ya Accessibility service ON ho (ya purane Android pe overlay permission).
 * Warna ek notification aata hai — tap karo aur kaam ho jaata hai.
 */
@Singleton
class ActivityLauncher @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Outcome { LAUNCHED, NEEDS_TAP, NOT_FOUND }

    fun canLaunchFromHere(): Boolean =
        AppVisibility.isForeground ||
            JarvisAccessibilityService.isConnected ||
            (Build.VERSION.SDK_INT < 35 && Settings.canDrawOverlays(context))

    fun launch(intent: Intent, whatForUser: String): Outcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!canLaunchFromHere()) {
            postTapNotification(intent, whatForUser)
            return Outcome.NEEDS_TAP
        }
        return try {
            // From the accessibility service itself when it is running: Android always lets it start activities,
            // even when JARVIS is in the background on Android 14 / 15.
            val svc = JarvisAccessibilityService.get()
            if (svc != null && !AppVisibility.isForeground) svc.startActivity(intent) else context.startActivity(intent)
            Outcome.LAUNCHED
        } catch (e: ActivityNotFoundException) {
            Outcome.NOT_FOUND
        } catch (e: SecurityException) {
            postTapNotification(intent, whatForUser)
            Outcome.NEEDS_TAP
        }
    }

    /** Accessibility is switched on in Settings but Android has not (re)connected it — XOS often does this. */
    fun accessibilityStuck(): Boolean = !JarvisAccessibilityService.isConnected && runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .contains(context.packageName)
    }.getOrDefault(false)

    /** Converts an outcome into a tool result with the right message for the LLM. */
    fun result(outcome: Outcome, done: String, whatForUser: String, endsConversation: Boolean = false): ToolResult = when (outcome) {
        Outcome.LAUNCHED -> ToolResult.ok(done, endsConversation)
        Outcome.NEEDS_TAP -> ToolResult.ok(
            "NOT opened yet: Android only lets JARVIS open apps from the background while JARVIS Accessibility is " +
                "running" + (if (accessibilityStuck()) " (it is switched on but Android disconnected it — Sir should turn " +
                "JARVIS off and on again in Settings > Accessibility)" else " (it is off — JARVIS Setup > Accessibility)") +
                ". A notification '$whatForUser' was posted: say exactly this to Sir in one line — tap the notification " +
                "and it opens. Do not say you have no access.",
        )
        Outcome.NOT_FOUND -> ToolResult.fail("no app on this phone can do that")
    }

    private fun postTapNotification(intent: Intent, whatForUser: String) {
        ServiceController.ensureChannels(context)
        val pi = PendingIntent.getActivity(
            context, whatForUser.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, ServiceController.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS: tap to continue")
            .setContentText(whatForUser)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIF_TAP, n)
        }
    }

    private companion object {
        const val NOTIF_TAP = 3
    }
}

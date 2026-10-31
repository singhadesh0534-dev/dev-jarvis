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
            context.startActivity(intent)
            Outcome.LAUNCHED
        } catch (e: ActivityNotFoundException) {
            Outcome.NOT_FOUND
        }
    }

    /** Converts an outcome into a tool result with the right message for the LLM. */
    fun result(outcome: Outcome, done: String, whatForUser: String, endsConversation: Boolean = false): ToolResult = when (outcome) {
        Outcome.LAUNCHED -> ToolResult.ok(done, endsConversation)
        Outcome.NEEDS_TAP -> ToolResult.ok(
            "Android blocked opening it from the background. A notification '$whatForUser' was posted — " +
                "tell Sir to tap it (or turn on JARVIS Accessibility in Setup so this works hands-free).",
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

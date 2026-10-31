package com.adesh.devjarvis.notifications

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.adesh.devjarvis.tools.FuzzyMatcher
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** read_notifications / reply_to_notification. */
@Singleton
class NotificationTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: NotificationStore,
) {
    private fun notConnected() = ToolResult.fail("notification access is off — Sir can turn it on in JARVIS Setup")

    private val read = object : SimpleTool(
        "read_notifications", "Recent notifications (OTP/bank/payment ones are always hidden). Optionally only from one app or person.",
        params { string("app", "App or sender name, e.g. WhatsApp, Mummy") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (!store.listenerConnected) return notConnected()
            val list = store.recent(args.string("app"), limit = 5)
            val hidden = store.hiddenSensitiveCount
            if (list.isEmpty()) return ToolResult.ok("no notifications" + if (hidden > 0) " (some private ones hidden)" else "")
            return ToolResult.ok(list.joinToString(" | ") { "${it.appLabel}, ${it.title}: ${it.text.take(120)}" })
        }
    }

    private val reply = object : SimpleTool(
        "reply_to_notification", "Reply to the latest message notification from a person/app (WhatsApp, Messages…). The app reads it back and asks Sir to confirm.",
        params {
            string("to", "Sender name as shown in the notification, or the app", required = true)
            string("message", "Reply text", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            if (!store.listenerConnected) return notConnected()
            val to = args.requireString("to")
            val message = args.requireString("message")
            val candidates = store.recent(limit = 50).filter { it.reply != null }
            val target = FuzzyMatcher.rank(to, candidates, threshold = 0.7) { listOf(it.title, it.appLabel) }.firstOrNull()?.item
                ?: return ToolResult.fail("no recent message from '$to' that can be replied to")
            if (!ctx.confirm("Sir, ${target.title} ko ${target.appLabel} pe reply: \"$message\". Bhej doon?")) {
                return ToolResult.ok("Sir said no — not sent")
            }
            val r = target.reply!!
            val intent = Intent()
            val results = Bundle().apply { putCharSequence(r.remoteInput.resultKey, message) }
            RemoteInput.addResultsToIntent(arrayOf(r.remoteInput), intent, results)
            r.intent.send(context, 0, intent)
            return ToolResult.ok("replied to ${target.title} on ${target.appLabel}")
        }
    }

    val tools: List<JarvisTool> = listOf(read, reply)
}

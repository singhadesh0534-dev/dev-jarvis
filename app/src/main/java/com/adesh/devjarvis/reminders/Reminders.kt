package com.adesh.devjarvis.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.R
import com.adesh.devjarvis.data.ReminderDao
import com.adesh.devjarvis.data.ReminderEntity
import com.adesh.devjarvis.proactive.ProactivePolicy
import com.adesh.devjarvis.proactive.ProactiveSpeaker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

data class Reminder(val id: Long, val text: String, val atMillis: Long)

/** JARVIS ke apne reminders: Room mein save, AlarmManager se time pe, phir bol ke / notification se yaad. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderDao,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    suspend fun schedule(text: String, atMillis: Long): Reminder {
        val id = dao.insert(ReminderEntity(text = text, atMillis = atMillis))
        val r = Reminder(id, text, atMillis)
        arm(r)
        return r
    }

    suspend fun pending(): List<Reminder> = dao.pending().map { Reminder(it.id, it.text, it.atMillis) }

    suspend fun between(from: Long, to: Long): List<Reminder> = dao.between(from, to).map { Reminder(it.id, it.text, it.atMillis) }

    suspend fun markDone(id: Long) = dao.markDone(id)

    /** After reboot AlarmManager forgets everything — set future ones again. Also imports Phase-3 reminders. */
    suspend fun rearmAll() {
        importLegacy()
        val now = System.currentTimeMillis()
        pending().forEach { r -> if (r.atMillis > now - 60_000) arm(r) else markDone(r.id) }
    }

    private suspend fun importLegacy() {
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val raw = prefs.getString("list", null) ?: return
        runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).forEach {
                val o = it.jsonObject
                dao.insert(ReminderEntity(text = o["text"]!!.jsonPrimitive.content, atMillis = o["at"]!!.jsonPrimitive.long))
            }
        }
        prefs.edit().remove("list").apply()
    }

    private fun arm(r: Reminder) {
        val pi = PendingIntent.getBroadcast(
            context, r.id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, r.id).putExtra(EXTRA_TEXT, r.text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val am = alarms ?: return
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.atMillis, pi)
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        const val CHANNEL = "jarvis_reminders"

        fun notify(context: Context, id: Int, title: String, text: String) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reminders & briefings", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_jarvis)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
            if (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(id, n)
            }
        }
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var speaker: ProactiveSpeaker

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0)
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                scheduler.markDone(id)
                ReminderScheduler.notify(context, id.toInt(), "Reminder, Sir", text)
                speaker.offer(ProactivePolicy.Kind.REMINDER, "Sir, reminder: $text")
            } finally {
                pending.finish()
            }
        }
    }
}

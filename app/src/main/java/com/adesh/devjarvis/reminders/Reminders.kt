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
import com.adesh.devjarvis.core.JarvisSession
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class Reminder(val id: Long, val text: String, val atMillis: Long)

/**
 * JARVIS ke apne reminders: AlarmManager se time pe notification + (always-on ho to) bol ke yaad dilana.
 * Phase 5 mein ye Room DB mein shift honge.
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(text: String, atMillis: Long): Reminder {
        val r = Reminder(System.currentTimeMillis(), text, atMillis)
        save(all() + r)
        arm(r)
        return r
    }

    fun all(): List<Reminder> = runCatching {
        Json.parseToJsonElement(prefs.getString(KEY, "[]")!!).let { it as JsonArray }.map {
            val o = it.jsonObject
            Reminder(o["id"]!!.jsonPrimitive.long, o["text"]!!.jsonPrimitive.content, o["at"]!!.jsonPrimitive.long)
        }
    }.getOrDefault(emptyList())

    fun remove(id: Long) = save(all().filterNot { it.id == id })

    /** After reboot alarms are gone — set them again (and drop old ones). */
    fun rearmAll() {
        val now = System.currentTimeMillis()
        val future = all().filter { it.atMillis > now - 60_000 }
        save(future)
        future.forEach(::arm)
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

    private fun save(list: List<Reminder>) {
        val arr = JsonArray(list.map { buildJsonObject { put("id", it.id); put("text", it.text); put("at", it.atMillis) } })
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TEXT = "text"
        const val CHANNEL = "jarvis_reminders"
        private const val KEY = "list"
    }
}

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var session: JarvisSession

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0)
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: return
        scheduler.remove(id)

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(ReminderScheduler.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Reminder, Sir")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id.toInt(), n)
        }
        session.announce("Sir, reminder: $text")
    }
}

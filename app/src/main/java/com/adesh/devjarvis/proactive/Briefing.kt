package com.adesh.devjarvis.proactive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.SpeechSanitizer
import com.adesh.devjarvis.core.DeviceStatus
import com.adesh.devjarvis.life.LifeMath
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.reminders.ReminderScheduler
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.tools.CalendarReader
import com.adesh.devjarvis.tools.WeatherClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Subah ka briefing: weather, aaj ke events/reminders, battery, ek witty line. */
@Singleton
class BriefingComposer @Inject constructor(
    private val brain: JarvisBrain,
    private val settings: SettingsRepository,
    private val weather: WeatherClient,
    private val calendar: CalendarReader,
    private val reminders: ReminderScheduler,
    private val device: DeviceStatus,
    private val memory: MemoryRepository,
    private val life: LifeStore,
) {
    /** Raat ka summary: aaj ka kharcha + kal ka plan. */
    suspend fun composeNight(): String {
        val s = settings.current
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val tomorrow = LocalDate.now().plusDays(1)
        val from = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val d = device.snapshot()
        val todayStart = LifeMath.periodStart("today", now, zone)
        val spent = life.get().expenses.filter { it.time in todayStart..now }
        val inputs = BriefingTemplate.NightInputs(
            dateText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
            tomorrowEvents = runCatching { calendar.events(from, to) }.getOrDefault(emptyList()),
            tomorrowReminders = reminders.between(from, to).map { java.time.Instant.ofEpochMilli(it.atMillis).atZone(zone).format(fmt) + " " + it.text },
            expensesToday = if (spent.isEmpty()) null else LifeMath.expenseSummary(spent, todayStart, now),
            batteryPercent = d.batteryPercent,
            charging = d.charging,
        )
        val llm = brain.complete(s.systemPrompt, BriefingTemplate.nightPrompt(inputs), json = false)
            ?.let { SpeechSanitizer.clean(it) }
            ?.takeIf { it.length in 20..900 }
        return llm ?: BriefingTemplate.nightFallback(inputs)
    }

    suspend fun compose(): String {
        val s = settings.current
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val from = System.currentTimeMillis()
        val to = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val d = device.snapshot()
        val inputs = BriefingTemplate.Inputs(
            hour = now.hour,
            dateText = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)),
            weather = s.homeCity.takeIf { it.isNotBlank() }?.let { runCatching { weather.summary(it) }.getOrNull() },
            reminders = reminders.between(from, to).map { java.time.Instant.ofEpochMilli(it.atMillis).atZone(zone).format(fmt) + " " + it.text },
            events = calendar.events(from, to),
            batteryPercent = d.batteryPercent,
            charging = d.charging,
            facts = memory.relevant("", limit = 3).map { it.text },
            news = runCatching { brain.webAnswer("Aaj ki India ki 3 sabse badi khabrein, har ek ek chhoti line mein") }.getOrNull()?.take(400),
        )
        val llm = brain.complete(s.systemPrompt, BriefingTemplate.llmPrompt(inputs), json = false)
            ?.let { SpeechSanitizer.clean(it) }
            ?.takeIf { it.length in 20..900 }
        return llm ?: BriefingTemplate.fallback(inputs)
    }
}

/** Har din set time pe briefing ka alarm (exact). */
@Singleton
class BriefingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun intent(night: Boolean) = PendingIntent.getBroadcast(
        context, if (night) 9003 else 9001,
        Intent(context, BriefingReceiver::class.java).putExtra(EXTRA_NIGHT, night),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** (Re)arm morning briefing and night summary for their next occurrence, or cancel the ones that are off. */
    fun reschedule() {
        val s = settings.current
        arm(night = false, enabled = s.briefingEnabled, minute = s.briefingMinute)
        arm(night = true, enabled = s.nightSummaryEnabled, minute = s.nightSummaryMinute)
    }

    private fun arm(night: Boolean, enabled: Boolean, minute: Int) {
        val am = alarms ?: return
        if (!enabled) {
            am.cancel(intent(night))
            return
        }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(night))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(night))
            }
        }
    }

    /** Compose + speak in the background now (also used by Settings "Play briefing now"). */
    fun runNow(night: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<BriefingWorker>()
            .setInputData(androidx.work.Data.Builder().putBoolean(EXTRA_NIGHT, night).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(if (night) "jarvis_night_summary" else "jarvis_briefing", ExistingWorkPolicy.REPLACE, req)
    }

    companion object {
        const val EXTRA_NIGHT = "night"
    }
}

class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, BriefingWorker.Deps::class.java)
        deps.scheduler().runNow(night = intent.getBooleanExtra(BriefingScheduler.EXTRA_NIGHT, false))
        deps.scheduler().reschedule() // tomorrow
    }
}

class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun composer(): BriefingComposer
        fun speaker(): ProactiveSpeaker
        fun scheduler(): BriefingScheduler
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val night = inputData.getBoolean(BriefingScheduler.EXTRA_NIGHT, false)
        val text = if (night) deps.composer().composeNight() else deps.composer().compose()
        deps.speaker().offer(ProactivePolicy.Kind.BRIEFING, text, notifyTitle = if (night) "Night summary" else "Morning briefing")
        return Result.success()
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        // Expedited work on Android 11 and older needs a notification.
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, com.adesh.devjarvis.service.ServiceController.CHANNEL_ONLINE)
            .setSmallIcon(com.adesh.devjarvis.R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS: preparing your briefing")
            .build()
        com.adesh.devjarvis.service.ServiceController.ensureChannels(applicationContext)
        return androidx.work.ForegroundInfo(9002, n)
    }
}

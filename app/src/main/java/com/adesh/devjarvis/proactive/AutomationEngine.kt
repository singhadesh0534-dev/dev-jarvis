package com.adesh.devjarvis.proactive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.core.ActivityLog
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.life.DiaryLogic
import com.adesh.devjarvis.life.LifeMath
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.life.SuggestionLogic
import com.adesh.devjarvis.life.Trigger
import com.adesh.devjarvis.life.TriggerLogic
import com.adesh.devjarvis.life.Used
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.notifications.NotificationStore
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS ka "khud se" wala hissa, always-on service ke saath chalta hai:
 *  - Triggers: charger / Wi-Fi / earphones / ghadi / kisi ka message -> Sir ka bataya kaam.
 *  - Aadat se sujhaav: jo kaam Sir aksar isi waqt karwate hain, ek baar pooch lena.
 *  - Diary: raat ko din bhar ka saar likh lena.
 * Sab kuch phone ke andar; sirf diary ka saar likhne ke liye Sir ka apna AI key use hota hai.
 */
@Singleton
class AutomationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LifeStore,
    private val session: JarvisSession,
    private val speaker: ProactiveSpeaker,
    private val settings: SettingsRepository,
    private val activity: ActivityLog,
    private val notifications: NotificationStore,
    private val brain: JarvisBrain,
    private val memory: MemoryRepository,
) {
    private var scope: CoroutineScope? = null
    private var jobs: List<Job> = emptyList()
    private var started = false
    private val prefs = context.getSharedPreferences("automation", Context.MODE_PRIVATE)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> fire("charger_on")
                Intent.ACTION_POWER_DISCONNECTED -> fire("charger_off")
                Intent.ACTION_TIME_TICK -> onMinute()
            }
        }
    }

    private val wifi = object : ConnectivityManager.NetworkCallback() {
        private var connected: Boolean? = null
        override fun onAvailable(network: Network) {
            // The first callback only reports the state at start: not an event.
            if (connected == false) fire("wifi_on")
            connected = true
        }

        override fun onLost(network: Network) {
            if (connected == true) fire("wifi_off")
            connected = false
        }

        fun settle() {
            if (connected == null) connected = false
        }
    }

    private val devices = object : AudioDeviceCallback() {
        private var ready = false
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            // Android reports everything already plugged in once, right after registering: skip that.
            if (!ready) { ready = true; return }
            if (added.any { it.isSink && it.type in HEADPHONES }) fire("headphones_on")
        }
    }

    fun start(serviceScope: CoroutineScope) {
        if (started) return
        started = true
        scope = serviceScope
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        runCatching {
            connectivity?.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifi)
        }
        runCatching { audio?.registerAudioDeviceCallback(devices, null) }
        jobs = listOf(
            serviceScope.launch {
                kotlinx.coroutines.delay(3_000)
                wifi.settle() // no Wi-Fi at start: the next connect is a real event
            },
            serviceScope.launch {
                notifications.arrivals.collect { n -> fire("message_from", "${n.title}|${n.appLabel}") }
            },
            serviceScope.launch {
                // (kept as a separate job so one failing collector cannot stop the other)
            },
        )
        SessionTrace.add("AUTO engine on, triggers=${store.get().triggers.size}")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { connectivity?.unregisterNetworkCallback(wifi) }
        runCatching { audio?.unregisterAudioDeviceCallback(devices) }
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        scope = null
    }

    private fun fire(event: String, value: String = "") {
        try {
            fireUnsafe(event, value)
        } catch (t: Throwable) {
            com.adesh.devjarvis.core.ProblemJournal.add("AUTO", "rule check failed: ${t.javaClass.simpleName}: ${t.message?.take(120)}")
        }
    }

    private fun fireUnsafe(event: String, value: String) {
        val now = System.currentTimeMillis()
        val due = TriggerLogic.due(store.get().triggers, event, value, now)
        if (due.isEmpty()) return
        store.update { d -> d.copy(triggers = d.triggers.map { t -> if (due.any { it.id == t.id }) t.copy(lastFired = now) else t }) }
        due.forEach { t ->
            SessionTrace.add("AUTO trigger ${t.event} '${t.param}'")
            scope?.launch(Dispatchers.Main) { runCatching { session.runAutomation(t.action, TriggerLogic.describe(t)) } }
        }
    }

    private fun onMinute() = runCatching { everyMinute() }.let { }

    private fun everyMinute() {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        fire("time", minute.toString())
        if (now.minute % 10 == 0) suggest()
        if (minute == DIARY_MINUTE) scope?.launch { runCatching { writeDiary() } }
    }

    // ---------------------------------------------------------------- habit suggestions

    private fun history(): List<Used> = activity.entries.value.map { Used(it.timeMillis, it.tool, it.args, it.ok) }

    private fun suggest() {
        if (!settings.current.suggestHabits) return
        val today = LocalDate.now().toString()
        val suggested = if (prefs.getString(K_SUGGEST_DAY, "") == today) prefs.getStringSet(K_SUGGESTED, emptySet()).orEmpty() else emptySet()
        if (suggested.size >= MAX_SUGGESTIONS_PER_DAY) return
        val s = SuggestionLogic.find(history(), System.currentTimeMillis(), suggested) ?: return
        prefs.edit().putString(K_SUGGEST_DAY, today).putStringSet(K_SUGGESTED, suggested + s.key).apply()
        val line = SuggestionLogic.phrase(s)
        SessionTrace.add("AUTO suggestion ${s.tool} days=${s.days}")
        // So that a later "haan, kar do" makes sense to the brain.
        brain.noteOffer(line)
        scope?.launch { runCatching { speaker.offer(ProactivePolicy.Kind.NOTIFICATION, line) } }
    }

    // ---------------------------------------------------------------- diary

    /** Write (or rewrite) the entry for today. Returns it, or null when nothing happened. */
    suspend fun writeDiary(): String? {
        if (!settings.current.diaryEnabled) return null
        val now = System.currentTimeMillis()
        val start = LifeMath.periodStart("today", now)
        val actions = history().filter { it.time in start..now }
        val talks = runCatching { memory.recentSummaries.first() }.getOrDefault(emptyList())
            .filter { it.startedAt in start..now }.map { it.summary }.filter { it.isNotBlank() }
        val spent = store.get().expenses.filter { it.time in start..now }
        val spending = if (spent.isEmpty()) null else LifeMath.expenseSummary(spent, start, now)
        val plain = DiaryLogic.plain(actions, talks, spending) ?: return null
        val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
        val written = withContext(Dispatchers.IO) {
            runCatching { brain.complete(DIARY_SYSTEM, DiaryLogic.prompt(dateText, actions, talks, spending), json = false) }.getOrNull()
        }?.trim()?.takeIf { it.length in 20..1200 }
        val entry = written ?: plain
        store.update { it.copy(diary = DiaryLogic.put(it.diary, DiaryLogic.dayKey(now), entry)) }
        SessionTrace.add("AUTO diary written ai=${written != null}")
        return entry
    }

    private companion object {
        const val K_SUGGEST_DAY = "suggest_day"
        const val K_SUGGESTED = "suggested_keys"
        const val MAX_SUGGESTIONS_PER_DAY = 3
        const val DIARY_MINUTE = 23 * 60 + 40
        const val DIARY_SYSTEM = "You keep a short, factual daily diary for the user of a personal assistant. Plain sentences only."
        val HEADPHONES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}

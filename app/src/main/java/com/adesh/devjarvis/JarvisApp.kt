package com.adesh.devjarvis

import android.app.Application
import com.adesh.devjarvis.core.AppVisibility
import com.adesh.devjarvis.core.CrashReporter
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.core.ProblemJournal
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.proactive.BriefingScheduler
import com.adesh.devjarvis.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JarvisApp : Application() {
    @Inject lateinit var briefing: BriefingScheduler
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var calls: com.adesh.devjarvis.calls.CallMonitor
    @Inject lateinit var places: com.adesh.devjarvis.life.PlaceReminders
    @Inject lateinit var focus: com.adesh.devjarvis.voice.AudioFocusHelper
    @Inject lateinit var speechInput: com.adesh.devjarvis.voice.SpeechInput

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        java.io.File(filesDir, "crash").let { dir ->
            SessionTrace.init(dir)
            ProblemJournal.init(dir)
        }
        SessionTrace.add("APP start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${Edition.label}")
        noteLastExit()
        AppVisibility.install(this)
        briefing.reschedule()
        calls.restoreRing() // in case we were killed while the ring was lowered
        places.schedule()
        focus.restoreVolume() // in case we were killed while the voice was boosted
        speechInput.recoverMutes() // ...or while streams were muted for listening
        // One-time import of Phase-3 reminders into the database (+ re-arm).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { reminders.rearmAll() } }
    }

    /** Why the app stopped last time (crash, native crash, killed by the phone to save battery, low memory…). */
    private fun noteLastExit() {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        runCatching {
            val am = getSystemService(android.app.ActivityManager::class.java) ?: return
            val info = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
            val reason = when (info.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "CRASH"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                android.app.ApplicationExitInfo.REASON_ANR -> "ANR (app hung)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (phone ran out of RAM)"
                android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "USER (swiped away / force stop)"
                android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                android.app.ApplicationExitInfo.REASON_SIGNALED -> "KILLED signal ${info.status}"
                android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "KILLED_BY_PHONE (battery / resource limit)"
                android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "UPDATED"
                android.app.ApplicationExitInfo.REASON_OTHER -> "KILLED_BY_SYSTEM"
                else -> "reason ${info.reason}"
            }
            val line = "$reason importance=${info.importance} ${info.description.orEmpty().take(120)}"
            SessionTrace.add("LAST EXIT $line")
            if (info.reason !in setOf(android.app.ApplicationExitInfo.REASON_USER_REQUESTED, android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED, android.app.ApplicationExitInfo.REASON_EXIT_SELF)) {
                ProblemJournal.add("APP_STOPPED", line)
            }
        }
    }
}

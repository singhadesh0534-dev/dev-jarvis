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

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        java.io.File(filesDir, "crash").let { dir ->
            SessionTrace.init(dir)
            ProblemJournal.init(dir)
        }
        SessionTrace.add("APP start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${Edition.label}")
        AppVisibility.install(this)
        briefing.reschedule()
        calls.restoreRing() // in case we were killed while the ring was lowered
        // One-time import of Phase-3 reminders into the database (+ re-arm).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { reminders.rearmAll() } }
    }
}

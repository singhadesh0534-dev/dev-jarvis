package com.adesh.devjarvis

import android.app.Application
import com.adesh.devjarvis.core.AppVisibility
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

    override fun onCreate() {
        super.onCreate()
        AppVisibility.install(this)
        briefing.reschedule()
        // One-time import of Phase-3 reminders into the database (+ re-arm).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { reminders.rearmAll() } }
    }
}

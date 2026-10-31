package com.adesh.devjarvis.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adesh.devjarvis.proactive.BriefingScheduler
import com.adesh.devjarvis.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Phone restart / app update ke baad JARVIS ko wapas laane ki koshish.
 * Android 15+ boot ke waqt mic service allow nahi karta — tab "tap karke online kijiye" notification aata hai.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var briefing: BriefingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" -> {
                // AlarmManager forgets everything on reboot.
                briefing.reschedule()
                if (settings.current.alwaysOn) ServiceController.start(context)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { reminders.rearmAll() } finally { pending.finish() }
                }
            }
        }
    }
}

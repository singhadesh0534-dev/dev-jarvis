package com.adesh.devjarvis.proactive

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.core.JarvisState
import com.adesh.devjarvis.reminders.ReminderScheduler
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.voice.AudioFocusHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Proactive line ka faisla: bolna hai ya sirf notification? ([ProactivePolicy]) */
@Singleton
class ProactiveSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: JarvisSession,
    private val settings: SettingsRepository,
    private val focus: AudioFocusHelper,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun situation(): ProactivePolicy.Situation {
        val s = settings.current
        val ui = session.ui.value
        return ProactivePolicy.Situation(
            minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute },
            quietStartMinute = s.quietStartMinute,
            quietEndMinute = s.quietEndMinute,
            dnd = (notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) > NotificationManager.INTERRUPTION_FILTER_ALL,
            ringerSilent = audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL,
            headphones = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            } == true,
            inCall = focus.inCall(),
            busy = ui.state != JarvisState.IDLE || ui.inConversation,
            alwaysOnRunning = ui.alwaysOn,
        )
    }

    /**
     * Speak [text] if the policy allows; otherwise make sure Sir still sees it.
     * Reminders and Android notifications already have their own notification, so they aren't duplicated.
     */
    suspend fun offer(kind: ProactivePolicy.Kind, text: String, notifyTitle: String = "JARVIS") = withContext(Dispatchers.Main) {
        when (ProactivePolicy.decide(kind, situation())) {
            ProactivePolicy.Decision.SPEAK -> session.announce(text)
            ProactivePolicy.Decision.NOTIFY -> {
                session.note(text)
                if (kind == ProactivePolicy.Kind.BRIEFING || kind == ProactivePolicy.Kind.BATTERY) {
                    ReminderScheduler.notify(context, kind.ordinal + 100, notifyTitle, text)
                }
            }
        }
    }
}

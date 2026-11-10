package com.adesh.devjarvis.calls

import android.Manifest
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.core.ProblemJournal
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.proactive.ProactiveSpeaker
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Incoming call (phone ya WhatsApp): naam bolna, phir "uthao / kaat do" sunna. Call ki notification se
 * chalta hai (Full edition, Notification access). Best-effort: ringtone ke upar sunna mushkil hota hai,
 * isliye poochhte waqt ring ki awaaz thodi der ke liye dheemi ki jaati hai.
 */
@Singleton
class CallMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val session: Lazy<JarvisSession>,
    private val speaker: Lazy<ProactiveSpeaker>,
) {
    private class Ringing(val key: String, val answer: PendingIntent?, val decline: PendingIntent?)

    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = context.getSharedPreferences("call_monitor", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var ringing: Ringing? = null
    private var announcedKey: String? = null

    /** Called by the notification listener. True = it was a call notification (not a message). */
    fun onPosted(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (n.category != Notification.CATEGORY_CALL) return false
        val answer = intent(n, answer = true)
        val decline = intent(n, answer = false)
        if (answer == null || decline == null) { // outgoing / already answered: not a ring any more
            if (ringing?.key == sbn.key) ended()
            return true
        }
        ringing = Ringing(sbn.key, answer, decline)
        if (announcedKey == sbn.key) return true // the dialer re-posts while it rings
        announcedKey = sbn.key
        if (!settings.current.announceCalls) return true
        val sit = speaker.get().situation()
        if (sit.dnd || (sit.ringerSilent && !sit.headphones)) return true // silent means silent
        val caller = IncomingCall.callerForSpeech(n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        SessionTrace.add("RING from ${sbn.packageName}")
        duckRing()
        session.get().promptIncomingCall(caller) { reply -> onReply(sbn.key, reply) }
        return true
    }

    fun onRemoved(key: String) {
        if (ringing?.key == key) ended()
    }

    private fun ended() {
        ringing = null
        announcedKey = null
        restoreRing()
        session.get().cancelIncomingCallPrompt()
    }

    private fun onReply(key: String, reply: IncomingCall.Reply) {
        restoreRing()
        val r = ringing?.takeIf { it.key == key } ?: return
        when (reply) {
            IncomingCall.Reply.ANSWER -> if (!send(r.answer) && !telecom(accept = true)) failed("answer")
            IncomingCall.Reply.REJECT -> {
                if (send(r.decline) || telecom(accept = false)) {
                    main.postDelayed({ session.get().announce("Call kaat diya, Sir.") }, 1500)
                } else {
                    failed("decline")
                }
            }
            IncomingCall.Reply.UNCLEAR -> Unit // let it ring
        }
    }

    private fun failed(what: String) {
        ProblemJournal.add("CALL", "could not $what the ringing call by voice")
        session.get().note("Call ko awaaz se handle nahi kar paya — screen se kijiye, Sir.")
    }

    /** Answer / decline PendingIntent: CallStyle extras first (language independent), then button labels. */
    private fun intent(n: Notification, answer: Boolean): PendingIntent? {
        if (Build.VERSION.SDK_INT >= 31) {
            val key = if (answer) Notification.EXTRA_ANSWER_INTENT else Notification.EXTRA_DECLINE_INTENT
            @Suppress("DEPRECATION")
            (n.extras?.getParcelable(key) as? PendingIntent)?.let { return it }
        }
        val actions = n.actions ?: return null
        val index = IncomingCall.pickAction(actions.map { it.title?.toString().orEmpty() }, answer) ?: return null
        return actions[index].actionIntent
    }

    @Suppress("DEPRECATION")
    private fun send(pi: PendingIntent?): Boolean {
        pi ?: return false
        return runCatching {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pi.send(context, 0, null, null, null, null, options)
        }.isSuccess
    }

    /** Fallback for normal phone calls when Sir allowed "Answer phone calls". */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun telecom(accept: Boolean): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return false
        val tm = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching {
            if (accept) {
                tm.acceptRingingCall()
                true
            } else {
                Build.VERSION.SDK_INT >= 28 && tm.endCall()
            }
        }.getOrDefault(false)
    }

    // ---- ring volume: lower while JARVIS asks and listens, always put back

    private fun duckRing() {
        val am = audio ?: return
        runCatching {
            val current = am.getStreamVolume(AudioManager.STREAM_RING)
            val low = (am.getStreamMaxVolume(AudioManager.STREAM_RING) / 6).coerceAtLeast(1)
            if (current > low) {
                prefs.edit().putInt(K_RESTORE, current).commit()
                am.setStreamVolume(AudioManager.STREAM_RING, low, 0)
            }
        }
    }

    /** Also called at app start, in case JARVIS was killed while the ring was lowered. */
    fun restoreRing() {
        val saved = prefs.getInt(K_RESTORE, -1)
        if (saved < 0) return
        runCatching { audio?.setStreamVolume(AudioManager.STREAM_RING, saved, 0) }
        prefs.edit().remove(K_RESTORE).apply()
    }

    private companion object {
        const val K_RESTORE = "ring_volume_to_restore"
    }
}

package com.adesh.devjarvis.ui.connectors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.adesh.devjarvis.accessibility.JarvisAccessibilityService
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Everything JARVIS is plugged into, checked live: his brains and voice (keys), the phone access that lets him act,
 * the permissions, and the apps he drives through screen control. Each row says what is missing and opens the fix.
 */
@HiltViewModel
class ConnectorsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _items = MutableStateFlow(build())
    val items: StateFlow<List<Connector>> = _items.asStateFlow()

    fun refresh() { _items.value = build() }

    private fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun appIntent(pkg: String): Intent? = context.packageManager.getLaunchIntentForPackage(pkg)


    private fun build(): List<Connector> = buildList {
        val s = settings.current
        val keys = s.geminiApiKey.split(',', '\n', ';').count { it.isNotBlank() }
        val screen = JarvisAccessibilityService.isConnected

        // ---- brains and voice
        add(Connector("gemini", BRAIN, "Gemini", if (keys > 0) "$keys key — main dimaag + Live awaaz" else "Key nahi — JARVIS bina iske nahi sochta", keys > 0, ConnectorAction.JarvisSettings))
        add(Connector("groq", BRAIN, "Groq", if (s.hasGroqKey) "Backup dimaag jude" else "Backup dimaag (free) — Gemini khatam ho to kaam aata hai", s.hasGroqKey, ConnectorAction.JarvisSettings, optional = true))
        add(Connector("extra", BRAIN, "Teesra dimaag", if (s.hasExtraBrain) "Jude" else "Optional — koi bhi OpenAI-jaisa free API", s.hasExtraBrain, ConnectorAction.JarvisSettings, optional = true))
        add(Connector("eleven", BRAIN, "ElevenLabs", if (s.elevenApiKey.isNotBlank()) "Premium awaaz jude" else "Optional — aur asli awaaz", s.elevenApiKey.isNotBlank(), ConnectorAction.JarvisSettings, optional = true))
        add(Connector("web", BRAIN, "Google Search", if (keys > 0) "Taaza khabar, rate, score — Gemini se" else "Gemini key ke saath chalta hai", keys > 0, ConnectorAction.JarvisSettings))

        // ---- phone access (what lets him act, not just talk)
        add(Connector("access", PHONE, "Screen control", if (screen) "Apps chala sakta hai, switch daba sakta hai" else "Band — iske bina switch / apps adhoore", screen, ConnectorAction.Open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))))
        val notif = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        add(Connector("notif", PHONE, "Notifications", if (notif) "Messages padh ke bata sakta hai" else "Band — WhatsApp / SMS alerts nahi sunega", notif, ConnectorAction.Open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))))
        val write = Settings.System.canWrite(context)
        add(Connector("write", PHONE, "System settings", if (write) "Brightness, rotation, timeout badal sakta hai" else "Band — brightness / rotation nahi badlega", write, ConnectorAction.Open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))))
        val dnd = context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        add(Connector("dnd", PHONE, "Do Not Disturb", if (dnd) "Silent / DND khud kar sakta hai" else "Band — silent mode nahi kar payega", dnd, ConnectorAction.Open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))))
        val bg = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != false
        add(Connector("battery", PHONE, "Background", if (bg) "Phone use background mein nahi rokta" else "Battery saver JARVIS ko rok sakta hai", bg, ConnectorAction.Open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))))
        val overlay = Settings.canDrawOverlays(context)
        add(Connector("overlay", PHONE, "Bubble", if (overlay) "Doosre apps ke upar dikh sakta hai" else "Band — floating bubble nahi dikhega", overlay, ConnectorAction.Open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))), optional = true))

        // ---- permissions
        fun perm(id: String, name: String, permission: String, yes: String, no: String, optional: Boolean = false) {
            val ok = has(permission)
            add(Connector(id, PERMISSIONS, name, if (ok) yes else no, ok, ConnectorAction.Permission(permission), optional))
        }
        perm("mic", "Microphone", Manifest.permission.RECORD_AUDIO, "Sun sakta hai", "Band — JARVIS sun nahi sakta")
        perm("camera", "Camera", Manifest.permission.CAMERA, "Live dekh sakta hai, photo le sakta hai", "Band — camera se dekh nahi sakta")
        perm("contacts", "Contacts", Manifest.permission.READ_CONTACTS, "Naam se call / message", "Band — naam se call nahi kar payega")
        perm("phone", "Calls", Manifest.permission.CALL_PHONE, "Call laga sakta hai", "Band — call nahi laga payega")
        perm("calendar", "Calendar", Manifest.permission.READ_CALENDAR, "Meeting yaad dila sakta hai", "Band — meetings nahi dikhenge", optional = true)
        perm("location", "Location", Manifest.permission.ACCESS_FINE_LOCATION, "Mausam, raasta, SOS location", "Band — mausam / SOS location nahi", optional = true)
        perm("sms", "SMS", Manifest.permission.SEND_SMS, "SMS bhej sakta hai (aapki haan ke baad)", "Band — SMS nahi bhej payega", optional = true)
        if (Build.VERSION.SDK_INT >= 33) perm("post", "Alerts", Manifest.permission.POST_NOTIFICATIONS, "Reminders dikha sakta hai", "Band — reminders nahi dikhenge")

        // ---- apps he drives through screen control (no payments, ever)
        APPS.forEach { (pkg, name, what) ->
            val there = installed(pkg)
            val ok = there && screen
            val detail = when {
                !there -> "Phone mein nahi hai"
                !screen -> "Screen control chalu karo, phir: $what"
                else -> what
            }
            add(Connector(pkg, APPS_GROUP, name, detail, ok, appIntent(pkg)?.let { ConnectorAction.Open(it) } ?: ConnectorAction.None, optional = !there))
        }
    }

    private companion object {
        const val BRAIN = "DIMAAG AUR AWAAZ"
        const val PHONE = "PHONE ACCESS"
        const val PERMISSIONS = "PERMISSIONS"
        const val APPS_GROUP = "APPS — SCREEN CONTROL SE"
        val APPS = listOf(
            Triple("com.whatsapp", "WhatsApp", "message, call, status padhna"),
            Triple("com.google.android.youtube", "YouTube", "search, play, gaane"),
            Triple("com.instagram.android", "Instagram", "reels, DM (sirf aapke kehne par)"),
            Triple("org.telegram.messenger", "Telegram", "message bhejna / padhna"),
            Triple("com.google.android.gm", "Gmail", "mail padhna, draft"),
            Triple("com.google.android.apps.maps", "Maps", "raasta, navigation"),
            Triple("com.spotify.music", "Spotify", "gaane chalana"),
            Triple("com.google.android.apps.youtube.music", "YouTube Music", "gaane chalana"),
            Triple("com.android.chrome", "Chrome", "website kholna, padhna"),
        )
    }
}

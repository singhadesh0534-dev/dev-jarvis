package com.adesh.devjarvis.ui.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.wakeword.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StepId {
    MIC, NOTIFICATIONS, WAKE_MODEL, BATTERY, XOS_AUTOSTART, XOS_RECENTS_LOCK,
    CALL_PHONE, CONTACTS, SMS, ACCESSIBILITY, NOTIFICATION_ACCESS, CAMERA, CALENDAR, WRITE_SETTINGS, OVERLAY, ASSISTANT,
}

/** How the step's main button works. */
enum class StepAction { RUNTIME_PERMISSION, OPEN_SETTINGS, DOWNLOAD, MANUAL_CONFIRM }

data class SetupStep(
    val id: StepId,
    val title: String,
    val why: String,
    val instructions: List<String>,
    val action: StepAction,
    val actionLabel: String,
    val granted: Boolean,
    val optional: Boolean,
    /** Runtime permission name for RUNTIME_PERMISSION steps. */
    val permission: String? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: VoskModelManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("setup_confirmations", Context.MODE_PRIVATE)
    private val tick = MutableStateFlow(0)

    val modelState: StateFlow<VoskModelManager.State> = models.state

    val steps: StateFlow<List<SetupStep>> = combine(tick, models.state) { _, _ -> buildSteps() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildSteps())

    /** Call on resume — user may have just granted something in system settings. */
    fun refresh() {
        tick.value++
    }

    fun confirmManual(id: StepId) {
        prefs.edit().putBoolean(id.name, true).apply()
        refresh()
    }

    fun downloadModel() = models.download()

    fun finish() = settings.update { it.copy(setupDone = true) }

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.startsWith(context.packageName + "/") }
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildSteps(): List<SetupStep> = buildList {
        add(
            SetupStep(
                StepId.MIC, "Microphone",
                "Aapki awaaz sunne ke liye — wake word aur baat-cheet dono.",
                listOf("Allow dabao, phir \"While using the app\" / \"Allow\" chuno."),
                StepAction.RUNTIME_PERMISSION, "Allow microphone",
                granted = has(Manifest.permission.RECORD_AUDIO), optional = false,
                permission = Manifest.permission.RECORD_AUDIO,
            )
        )
        if (Build.VERSION.SDK_INT >= 33) {
            add(
                SetupStep(
                    StepId.NOTIFICATIONS, "Notifications",
                    "\"JARVIS online\" wala chhota notification — iske bina Android background service ko jaldi maar deta hai.",
                    listOf("Allow dabao."),
                    StepAction.RUNTIME_PERMISSION, "Allow notifications",
                    granted = has(Manifest.permission.POST_NOTIFICATIONS), optional = false,
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                )
            )
        }
        add(
            SetupStep(
                StepId.WAKE_MODEL, "Wake word model",
                "\"Hello Dev\" offline pehchaanne ke liye ~36 MB ka model. Sirf ek baar download — uske baad bina internet chalta hai.",
                listOf("Wi-Fi pe ho to behtar.", "Download dabao aur 100% tak ruko."),
                StepAction.DOWNLOAD, "Download model",
                granted = models.isInstalled(), optional = false,
            )
        )
        val pm = context.getSystemService(PowerManager::class.java)
        add(
            SetupStep(
                StepId.BATTERY, "Battery optimization off",
                "XOS background apps ko bahut aggressively band karta hai. Ye JARVIS ko bachaata hai.",
                listOf("Allow / \"Don't optimize\" chuno."),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true, optional = false,
            )
        )
        add(
            SetupStep(
                StepId.XOS_AUTOSTART, "Infinix: Auto-start + background",
                "XOS ka apna \"Phone Master\" app ko khud band kar deta hai. Ye do switch zaruri hain.",
                listOf(
                    "Open setting dabao (Phone Master > Auto-start khulega, ya app info).",
                    "JARVIS ke saamne Auto-start ON karo.",
                    "App info > Battery > \"No restrictions\" / \"Allow background activity\" chuno.",
                    "Agar dikhe: Settings > Battery > Power saving / App launch > JARVIS > Manage manually > sab ON.",
                    "Wapas aake \"Ho gaya\" dabao.",
                ),
                StepAction.MANUAL_CONFIRM, "Open setting",
                granted = prefs.getBoolean(StepId.XOS_AUTOSTART.name, false), optional = false,
            )
        )
        add(
            SetupStep(
                StepId.XOS_RECENTS_LOCK, "Infinix: Recents mein lock",
                "Recent apps \"clear all\" karne pe bhi JARVIS band na ho.",
                listOf(
                    "Neeche se upar swipe karke hold karo (ya Recents button) — recent apps khulenge.",
                    "JARVIS ke card ko neeche kheencho (ya card pe long-press).",
                    "Lock 🔒 icon dabao. Card pe chhota lock dikhna chahiye.",
                    "Wapas aake \"Ho gaya\" dabao.",
                ),
                StepAction.MANUAL_CONFIRM, "",
                granted = prefs.getBoolean(StepId.XOS_RECENTS_LOCK.name, false), optional = false,
            )
        )
        add(
            SetupStep(
                StepId.CALL_PHONE, "Phone calls",
                "\"Mummy ko call karo\" — JARVIS seedha call laga sake (har baar pehle aapse poochhega).",
                listOf("Allow dabao."),
                StepAction.RUNTIME_PERMISSION, "Allow calls",
                granted = has(Manifest.permission.CALL_PHONE), optional = false,
                permission = Manifest.permission.CALL_PHONE,
            )
        )
        add(
            SetupStep(
                StepId.CONTACTS, "Contacts",
                "Naam se call/SMS/WhatsApp ke liye (\"Bhai\", \"Papa\"). Contacts phone se bahar nahi jaate — sirf naam match hota hai.",
                listOf("Allow dabao."),
                StepAction.RUNTIME_PERMISSION, "Allow contacts",
                granted = has(Manifest.permission.READ_CONTACTS), optional = false,
                permission = Manifest.permission.READ_CONTACTS,
            )
        )
        add(
            SetupStep(
                StepId.SMS, "Send SMS",
                "\"Papa ko SMS karo ki main late hoon\" — bhejne se pehle JARVIS message padh ke sunata hai aur poochhta hai.",
                listOf("Allow dabao."),
                StepAction.RUNTIME_PERMISSION, "Allow SMS",
                granted = has(Manifest.permission.SEND_SMS), optional = false,
                permission = Manifest.permission.SEND_SMS,
            )
        )
        add(
            SetupStep(
                StepId.ACCESSIBILITY, "Accessibility (JARVIS ke haath)",
                "Screen lock, screenshot, home — aur screen band/lock hone par bhi apps, calls aur alarms khol paana. " +
                    "Iske bina Android background se apps kholne nahi deta.",
                listOf(
                    "Open setting dabao → Installed apps / Downloaded apps → JARVIS → ON → Allow.",
                    "Agar \"Restricted setting\" likha aaye: phone Settings > Apps > JARVIS > upar ⋮ > \"Allow restricted settings\" — phir dobara try.",
                    "JARVIS screen sirf aapke command pe padhta hai (\"screen pe kya hai\", \"WhatsApp pe Rahul ko hi bhejo\"). Password fields kabhi nahi; payment/OTP screens pe ruk jaata hai.",
                ),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = accessibilityEnabled(), optional = false,
            )
        )
        add(
            SetupStep(
                StepId.NOTIFICATION_ACCESS, "Notification access",
                "\"Kiska message aaya?\", reply karna, aur important messages ka spoken alert. OTP / bank / payment " +
                    "notifications JARVIS kabhi nahi padhta, bolta ya kahin bhejta.",
                listOf("Open setting dabao → JARVIS → Allow.", "\"Restricted setting\" aaye to Accessibility wala same tareeka: App info > ⋮ > Allow restricted settings."),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName), optional = false,
            )
        )
        add(
            SetupStep(
                StepId.CAMERA, "Camera",
                "\"Jarvis, ye kya hai?\" — sirf jab aap poochho tab ek photo, Gemini ko dikhane ke liye. Background mein camera kabhi nahi chalta.",
                listOf("Allow dabao."),
                StepAction.RUNTIME_PERMISSION, "Allow camera",
                granted = has(Manifest.permission.CAMERA), optional = false,
                permission = Manifest.permission.CAMERA,
            )
        )
        add(
            SetupStep(
                StepId.CALENDAR, "Calendar (optional)",
                "Morning briefing aur \"aaj kya schedule hai?\" ke liye events padhna. Sirf padhna, kuch badalta nahi.",
                listOf("Allow dabao."),
                StepAction.RUNTIME_PERMISSION, "Allow calendar",
                granted = has(Manifest.permission.READ_CALENDAR), optional = true,
                permission = Manifest.permission.READ_CALENDAR,
            )
        )
        add(
            SetupStep(
                StepId.WRITE_SETTINGS, "Modify system settings (optional)",
                "Sirf brightness badalne ke liye (\"brightness 30 karo\").",
                listOf("JARVIS ke saamne switch ON karo."),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = Settings.System.canWrite(context), optional = true,
            )
        )
        add(
            SetupStep(
                StepId.OVERLAY, "Display over other apps (optional)",
                "Baad mein floating mini-reactor ke liye, aur Android ko JARVIS background se start karne mein madad karta hai.",
                listOf("JARVIS ke saamne switch ON karo."),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = Settings.canDrawOverlays(context), optional = true,
            )
        )
        val assistantHeld = if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            false
        }
        add(
            SetupStep(
                StepId.ASSISTANT, "Default digital assistant (optional)",
                "Home button long-press karne pe Google Assistant ki jagah JARVIS khule.",
                listOf(
                    "Default apps > Digital assistant app > Default digital assistant app.",
                    "JARVIS chuno.",
                    "Wapas aake \"Ho gaya\" dabao (ya Next).",
                ),
                StepAction.OPEN_SETTINGS, "Open setting",
                granted = assistantHeld || prefs.getBoolean(StepId.ASSISTANT.name, false), optional = true,
            )
        )
    }
}

package com.adesh.devjarvis.ui.hud

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.core.HudUiState
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.service.ServiceController
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.update.AppUpdater
import com.adesh.devjarvis.wakeword.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.adesh.devjarvis.brain.Persona

/** HUD ka thin wrapper — asli dimaag [JarvisSession] mein hai (service ke saath shared). */
@HiltViewModel
class HudViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: JarvisSession,
    private val settings: SettingsRepository,
    private val models: VoskModelManager,
    updater: AppUpdater,
    private val documents: com.adesh.devjarvis.vision.DocumentReader,
) : ViewModel() {
    val update: StateFlow<AppUpdater.State> = updater.state

    val ui: StateFlow<HudUiState> = session.ui
    val speechLevel: StateFlow<Float> = session.speechLevel

    /** Who JARVIS is being right now: jarvis / dost / gf. */
    val role: StateFlow<String> = settings.settings.map { Persona.normalize(it.persona) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Persona.normalize(settings.current.persona))

    /** The ROOP chip: the role is changed by voice, so just say how. */
    fun roleHint() = session.note("Roop badalne ke liye boliye: \"dost ban jao\", \"gf ban jao\" ya \"Jarvis ban jao\".")

    /** One-tap report: errors, the flight recorder and the problem journal (never keys or chat text). */
    fun report(): String = listOfNotNull(
        "DEV JARVIS report",
        com.adesh.devjarvis.core.CrashReporter.lastCrash(context),
        com.adesh.devjarvis.core.CrashReporter.recoveredLog(context),
        com.adesh.devjarvis.core.SessionTrace.dump().takeIf { it.isNotBlank() }?.let { "FLIGHT RECORDER (latest events)\n$it" },
        com.adesh.devjarvis.core.ProblemJournal.dump(60).takeIf { it.isNotBlank() }?.let { "PROBLEMS JARVIS NOTICED BY HIMSELF\n$it" },
    ).joinToString("\n\n")

    /** Permissions JARVIS needs to actually do things, that are still off (names for the home alert). */
    fun missingAccess(): List<String> = buildList {
        if (!com.adesh.devjarvis.accessibility.JarvisAccessibilityService.isConnected) add("Accessibility")
        if (!android.provider.Settings.System.canWrite(context)) add("Modify system settings")
        if (!androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) add("Notification access")
    }

    /** Android may stop JARVIS in the background unless he is exempt from battery optimisation. */
    fun backgroundProtected(): Boolean =
        context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != false

    fun refreshDevice() = session.refreshDevice()
    fun onReactorTap() = session.onReactorTap()
    fun onMicPermissionDenied() = session.onMicPermissionDenied()
    fun onTextSubmitted(text: String) = session.onTextSubmitted(text)
    fun answerConfirmation(yes: Boolean) = session.answerConfirmation(yes)
    fun dismissCard() = session.dismissCard()

    /** A photo or PDF picked on the HUD: look at it and tell Sir what it is. */
    fun onFilePicked(uri: android.net.Uri) {
        session.note("File dekh raha hoon, Sir…")
        viewModelScope.launch {
            val answer = runCatching { documents.explain(uri) }.getOrNull()
            session.announce(answer ?: "Sir, ye file padh nahi paya — ya to khul nahi rahi, ya vision abhi jawab nahi de raha. Ek minute mein dobara try kijiye.")
        }
    }

    /** Always-on switch on the HUD. Mic permission is checked by the screen first. */
    fun setAlwaysOn(on: Boolean) {
        settings.update { it.copy(alwaysOn = on) }
        if (on) {
            if (!models.isInstalled()) models.download()
            ServiceController.start(context)
        } else {
            ServiceController.stop(context)
        }
    }
}

package com.adesh.devjarvis.ui.hud

import android.content.Context
import androidx.lifecycle.ViewModel
import com.adesh.devjarvis.core.HudUiState
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.service.ServiceController
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.wakeword.VoskModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** HUD ka thin wrapper — asli dimaag [JarvisSession] mein hai (service ke saath shared). */
@HiltViewModel
class HudViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: JarvisSession,
    private val settings: SettingsRepository,
    private val models: VoskModelManager,
) : ViewModel() {

    val ui: StateFlow<HudUiState> = session.ui
    val speechLevel: StateFlow<Float> = session.speechLevel

    fun refreshDevice() = session.refreshDevice()
    fun onReactorTap() = session.onReactorTap()
    fun onMicPermissionDenied() = session.onMicPermissionDenied()
    fun onTextSubmitted(text: String) = session.onTextSubmitted(text)
    fun answerConfirmation(yes: Boolean) = session.answerConfirmation(yes)

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

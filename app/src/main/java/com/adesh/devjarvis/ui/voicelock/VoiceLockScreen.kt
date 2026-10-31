package com.adesh.devjarvis.ui.voicelock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.wakeword.SpeakerEnrollment
import com.adesh.devjarvis.wakeword.SpeakerModelManager
import com.adesh.devjarvis.wakeword.SpeakerVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceLockUi(
    val enabled: Boolean = false,
    val enrolled: Boolean = false,
    val threshold: Float = SpeakerVerification.DEFAULT_THRESHOLD,
    val modelState: SpeakerModelManager.State = SpeakerModelManager.State.Missing,
    val enrollStatus: String = "",
    val enrolling: Boolean = false,
    val micLevel: Float = 0f,
)

@HiltViewModel
class VoiceLockViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val speakerModels: SpeakerModelManager,
    private val enrollment: SpeakerEnrollment,
) : ViewModel() {

    private val local = MutableStateFlow(VoiceLockUi())
    val ui = combine(local, repo.settings, speakerModels.state) { l, s, model ->
        l.copy(enabled = s.speakerLockEnabled, enrolled = s.speakerEnrolled, threshold = s.speakerThreshold, modelState = model)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceLockUi())

    private var enrollJob: Job? = null

    fun downloadModel() = speakerModels.download()

    fun setEnabled(on: Boolean) {
        if (on && (!speakerModels.isInstalled() || !repo.current.speakerEnrolled)) {
            local.value = local.value.copy(enrollStatus = "Pehle model download + awaaz enroll karo.")
            return
        }
        repo.save(repo.current.copy(speakerLockEnabled = on))
    }

    fun setThreshold(v: Float) = repo.save(repo.current.copy(speakerThreshold = v))

    fun enroll() {
        if (enrollJob?.isActive == true) return
        if (!enrollment.modelsReady()) {
            local.value = local.value.copy(enrollStatus = "Model download hone do, phir enroll karo.")
            return
        }
        enrollJob = viewModelScope.launch {
            local.value = local.value.copy(enrolling = true, enrollStatus = "Taiyaar ho jao…")
            enrollment.enroll().collect { e ->
                when (e) {
                    is SpeakerEnrollment.Event.Listening -> local.value = local.value.copy(enrollStatus = "Boliye \"Hello Dev\" — ${e.index}/${e.total}")
                    is SpeakerEnrollment.Event.Level -> local.value = local.value.copy(micLevel = e.value)
                    is SpeakerEnrollment.Event.Captured -> local.value = local.value.copy(enrollStatus = "Mil gaya ${e.index}/${e.total} ✓", micLevel = 0f)
                    is SpeakerEnrollment.Event.Done -> {
                        repo.save(repo.current.copy(speakerProfile = SpeakerVerification.encode(e.profile), speakerLockEnabled = true))
                        local.value = local.value.copy(enrolling = false, enrollStatus = "Ho gaya, Sir. Ab main sirf aapki awaaz pe jaagunga.", micLevel = 0f)
                    }
                    is SpeakerEnrollment.Event.Failed -> local.value = local.value.copy(enrolling = false, enrollStatus = e.reason, micLevel = 0f)
                }
            }
        }
    }

    fun forgetVoice() = repo.save(repo.current.copy(speakerProfile = "", speakerLockEnabled = false))

    override fun onCleared() { enrollJob?.cancel() }
}

/** "Sirf meri awaaz" setup: model download + apni awaaz enroll + strictness. */
@Composable
fun VoiceLockScreen(viewModel: VoiceLockViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().background(Hud.Background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hud.Cyan) }
            Text("SIRF MERI AWAAZ", style = MaterialTheme.typography.headlineSmall, color = Hud.Cyan)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "JARVIS sirf aapki awaaz pe jaagega, doosron ki awaaz (TV, bagal wale) ignore karega. Ye best-effort hai — " +
                    "bahut shor ya milti-julti awaaz mein 100% pakka nahi. Kabhi aapki awaaz miss ho to reactor pe tap se baat karo.",
                style = MaterialTheme.typography.bodyMedium, color = Hud.Ice,
            )

            // Step 1: model
            Text("1. Speaker model (~13 MB)", style = MaterialTheme.typography.titleMedium, color = Hud.Cyan)
            when (val st = ui.modelState) {
                SpeakerModelManager.State.Ready -> Text("Model ready ✓", style = MaterialTheme.typography.bodyMedium, color = Hud.Green)
                is SpeakerModelManager.State.Downloading -> {
                    LinearProgressIndicator(progress = { st.percent / 100f }, color = Hud.Cyan, trackColor = Hud.Faint, modifier = Modifier.fillMaxWidth())
                    Text("Downloading… ${st.percent}%", style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
                }
                is SpeakerModelManager.State.Failed ->
                    Text("Download fail: ${st.message}", style = MaterialTheme.typography.bodySmall, color = Hud.Amber)
                else -> Unit
            }
            if (ui.modelState !is SpeakerModelManager.State.Ready) {
                OutlinedButton(onClick = viewModel::downloadModel, enabled = ui.modelState !is SpeakerModelManager.State.Downloading) {
                    Text("Download model", color = Hud.Cyan)
                }
            }

            // Step 2: enroll
            Text("2. Apni awaaz enroll karo", style = MaterialTheme.typography.titleMedium, color = Hud.Cyan)
            Text(if (ui.enrolled) "Enrolled ✓ (dobara karke update kar sakte ho)" else "Abhi enroll nahi hui.", style = MaterialTheme.typography.bodyMedium, color = if (ui.enrolled) Hud.Green else Hud.Dim)
            if (ui.enrollStatus.isNotBlank()) Text(ui.enrollStatus, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
            if (ui.enrolling) LinearProgressIndicator(progress = { ui.micLevel }, color = Hud.Cyan, trackColor = Hud.Faint, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::enroll,
                    enabled = ui.modelState is SpeakerModelManager.State.Ready && !ui.enrolling,
                    colors = ButtonDefaults.buttonColors(containerColor = Hud.Cyan, contentColor = Hud.Background),
                ) { Text(if (ui.enrolled) "RE-ENROLL" else "ENROLL", style = MaterialTheme.typography.labelMedium) }
                if (ui.enrolled) OutlinedButton(onClick = viewModel::forgetVoice) { Text("Forget my voice", color = Hud.Amber) }
            }

            // Step 3: enable + strictness
            Text("3. Speaker lock", style = MaterialTheme.typography.titleMedium, color = Hud.Cyan)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sirf meri awaaz pe jaago", style = MaterialTheme.typography.bodyMedium, color = Hud.Ice, modifier = Modifier.weight(1f))
                Switch(
                    checked = ui.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Hud.Background, checkedTrackColor = Hud.Cyan),
                )
            }
            Text("Strictness: ${"%.2f".format(ui.threshold)}", style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
            Slider(
                value = ui.threshold, onValueChange = viewModel::setThreshold, valueRange = 0.2f..0.8f,
                colors = SliderDefaults.colors(thumbColor = Hud.Cyan, activeTrackColor = Hud.Cyan, inactiveTrackColor = Hud.Faint),
            )
            Text("Kam = aapki awaaz kabhi miss na ho (par doosri awaaz bhi jaga sakti). Zyada = sakht (sirf aap, par kabhi aapko bhi miss kare).",
                style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        }
    }
}

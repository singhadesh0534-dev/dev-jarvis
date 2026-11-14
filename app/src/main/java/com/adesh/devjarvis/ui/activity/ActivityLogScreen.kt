package com.adesh.devjarvis.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.ActivityEntry
import com.adesh.devjarvis.core.ActivityLog
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.HudTag
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.StatusDot
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActivityLogViewModel @Inject constructor(private val log: ActivityLog) : ViewModel() {
    val entries = log.entries
    fun clear() = log.clear()
}

/** JARVIS ne kya-kya kiya — har tool call, uske arguments aur result. */
@Composable
fun ActivityLogScreen(viewModel: ActivityLogViewModel, onBack: () -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val failed = entries.count { !it.ok }
    Column(
        Modifier
            .fillMaxSize()
            .hudBackdrop(glowY = 0f)
            .safeDrawingPadding()
    ) {
        ScreenHeader(
            title = "ACTIVITY LOG",
            caption = if (entries.isEmpty()) "JARVIS ke har kaam ka hisaab" else "${entries.size} actions · $failed fail",
            onBack = onBack,
        ) {
            TextButton(onClick = viewModel::clear) { Text("CLEAR", style = MaterialTheme.typography.labelMedium, color = Hud.Dim) }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO ACTIVITY YET", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp), color = Hud.Cyan.copy(alpha = 0.7f))
                    Text(
                        "Abhi tak koi action nahi. Boliye: \"Jarvis, torch on karo\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Hud.Dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(entries, key = { it.id }) { EntryRow(it) }
        }
    }
}

private val timeFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.ENGLISH)

/** People's words for the tools (the raw names stay visible when a row is opened). */
private val FRIENDLY = mapOf(
    "set_toggle" to "Switch", "phone_setting" to "Phone setting", "set_brightness" to "Brightness", "set_volume" to "Volume",
    "toggle_flashlight" to "Torch", "open_app" to "App khola", "close_current_app" to "App band", "set_timer" to "Timer",
    "set_alarm" to "Alarm", "create_reminder" to "Reminder", "make_call" to "Call", "send_sms" to "SMS",
    "send_whatsapp" to "WhatsApp", "play_music" to "Gaana", "media_control" to "Media", "search_in_app" to "Search",
    "web_answer" to "Web", "weather" to "Mausam", "calculate" to "Hisaab", "remember" to "Yaad rakha", "recall" to "Yaad kiya",
    "add_note" to "Note", "read_screen" to "Screen padhi", "look_at_screen" to "Screen dekhi", "look" to "Camera",
    "tap" to "Tap", "type_text" to "Type", "scroll" to "Scroll", "swipe" to "Swipe", "go_back" to "Back", "go_home" to "Home",
    "jarvis_settings" to "JARVIS setting", "set_persona" to "Roop", "use_skill" to "Skill", "write_skill" to "Naya skill",
    "screen_watch" to "Screen share", "live_camera" to "Live camera", "emergency_sos" to "SOS", "device_status" to "Phone status",
    "add_expense" to "Kharcha", "get_schedule" to "Schedule", "add_calendar_event" to "Calendar", "do_task_on_screen" to "Screen kaam",
)

/** One compact row per action: what, result in a line, time. Tap to see the details. */
@Composable
private fun EntryRow(e: ActivityEntry) {
    val color = if (e.ok) Hud.Green else Hud.Amber
    var open by androidx.compose.runtime.saveable.rememberSaveable(e.id) { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(color, HudShapes.Chip, tint = 0.35f, edge = 0.4f)
            .clickable { open = !open }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, size = 4)
            Text(
                FRIENDLY[e.tool] ?: e.tool.replace('_', ' ').replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = Hud.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
            )
            Text(timeFormat.format(Date(e.timeMillis)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Hud.Dim)
            if (!e.ok) HudTag("FAIL", color, Modifier.padding(start = 8.dp))
        }
        Text(
            e.result,
            style = MaterialTheme.typography.bodySmall,
            color = if (e.ok) Hud.Ice.copy(alpha = 0.85f) else Hud.Amber.copy(alpha = 0.9f),
            maxLines = if (open) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
        )
        if (open) {
            Text(
                e.tool + if (e.args.isNotBlank()) "  ·  " + e.args else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Hud.Dim,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { EntryRow(it) }
        }
    }
}

private val timeFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.ENGLISH)

@Composable
private fun EntryRow(e: ActivityEntry) {
    val color = if (e.ok) Hud.Green else Hud.Amber
    Column(
        Modifier
            .fillMaxWidth()
            .glass(color, HudShapes.Card, tint = 0.5f, edge = 0.55f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color, size = 5)
            Text(
                e.tool,
                style = MaterialTheme.typography.labelMedium,
                color = Hud.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp, end = 8.dp),
            )
            HudTag(if (e.ok) "OK" else "FAIL", color)
        }
        Text(
            timeFormat.format(Date(e.timeMillis)),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Hud.Dim,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (e.args.isNotBlank()) {
            Text(
                e.args,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = Hud.Dim,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .glass(Hud.Dim, HudShapes.Chip, tint = 0f, edge = 0.3f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(e.result, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice, modifier = Modifier.padding(top = 8.dp))
    }
}

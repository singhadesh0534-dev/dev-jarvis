package com.adesh.devjarvis.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.core.ActivityEntry
import com.adesh.devjarvis.core.ActivityLog
import com.adesh.devjarvis.ui.theme.Hud
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Hud.Background)
            .safeDrawingPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Hud.Cyan)
            }
            Text("ACTIVITY LOG", style = MaterialTheme.typography.headlineSmall, color = Hud.Cyan, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clear) { Text("CLEAR", color = Hud.Dim) }
        }
        if (entries.isEmpty()) {
            Text(
                "Abhi tak koi action nahi. Boliye: \"Jarvis, torch on karo\".",
                style = MaterialTheme.typography.bodyMedium,
                color = Hud.Dim,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .background(Hud.Panel, RoundedCornerShape(6.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (e.ok) "✓ " else "✗ ") + e.tool, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
            Text(timeFormat.format(Date(e.timeMillis)), style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
        }
        if (e.args.isNotBlank()) Text(e.args, style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
        Text(e.result, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice)
    }
}

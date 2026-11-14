package com.adesh.devjarvis.ui.connectors

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.HudTag
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.StatusDot
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop

/** What a tap on a connector row does. */
sealed interface ConnectorAction {
    data object None : ConnectorAction
    data object JarvisSettings : ConnectorAction
    data class Open(val intent: Intent) : ConnectorAction
    data class Permission(val name: String) : ConnectorAction
}

/** One thing JARVIS is (or is not yet) plugged into. [optional] rows are amber-free when off: nice to have, not needed. */
data class Connector(
    val id: String,
    val group: String,
    val name: String,
    val detail: String,
    val ok: Boolean,
    val action: ConnectorAction,
    val optional: Boolean = false,
)

/** Everything JARVIS is connected to, live, with one tap to connect what is missing. */
@Composable
fun ConnectorsScreen(viewModel: ConnectorsViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    val needed = items.filter { !it.optional }
    val ready = needed.count { it.ok }

    val onTap: (Connector) -> Unit = { c ->
        when (val a = c.action) {
            ConnectorAction.JarvisSettings -> onOpenSettings()
            is ConnectorAction.Open -> runCatching { context.startActivity(a.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            is ConnectorAction.Permission -> if (!c.ok) permission.launch(a.name)
            ConnectorAction.None -> Unit
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .hudBackdrop(glowY = 0f)
            .safeDrawingPadding(),
    ) {
        ScreenHeader("CONNECTORS", "$ready / ${needed.size} zaroori jude · tap karke jodo", onBack)
        ReadyBar(ready, needed.size)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.groupBy { it.group }.forEach { (group, rows) ->
                item(key = "h_$group") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                            color = Hud.Cyan,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${rows.count { it.ok }}/${rows.size}", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
                    }
                }
                items(rows, key = { it.group + it.id }) { c -> ConnectorRow(c) { onTap(c) } }
            }
        }
    }
}

@Composable
private fun ReadyBar(ready: Int, total: Int) {
    val f = if (total == 0) 1f else ready.toFloat() / total
    val color = if (f >= 1f) Hud.Green else if (f >= 0.6f) Hud.Cyan else Hud.Amber
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(3.dp).background(Hud.Faint, HudShapes.Pill)) {
        Box(
            Modifier.fillMaxWidth(f).height(3.dp)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), color)), HudShapes.Pill),
        )
    }
}

@Composable
private fun ConnectorRow(c: Connector, onClick: () -> Unit) {
    val color: Color = when {
        c.ok -> Hud.Green
        c.optional -> Hud.Dim
        else -> Hud.Amber
    }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(if (c.ok || c.optional) Hud.Cyan else Hud.Amber, HudShapes.Card, tint = if (c.ok) 0.6f else 1f, edge = 0.6f)
            .clickable(enabled = c.action != ConnectorAction.None, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleSmall, color = Hud.White, maxLines = 1)
            Text(
                c.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (c.ok || c.optional) Hud.Dim else Hud.Amber,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(8.dp))
        HudTag(
            when {
                c.ok -> "JUDA"
                c.action == ConnectorAction.None -> "—"
                else -> "JODO"
            },
            color,
        )
    }
}

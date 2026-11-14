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
import androidx.compose.runtime.setValue
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

/** A service Sir connected himself (MCP), for the list. */
data class MyConnector(val name: String, val host: String, val tools: Int)

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
    val mine by viewModel.mine.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var adding by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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
        ScreenHeader("CONNECTORS", "$ready / ${needed.size} zaroori jude · tap karke jodo", onBack) {
            Text(
                "+ JODO",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
                color = Hud.Cyan,
                modifier = Modifier.clickable { adding = true }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        ReadyBar(ready, needed.size)
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (busy) Hud.Ice else Hud.Green,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (adding) AddConnectorDialog(busy, onDismiss = { adding = false }) { n, u, t -> viewModel.addConnector(n, u, t) { ok -> if (ok) adding = false } }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "h_mine") {
                Text(
                    "AAPKE CONNECTORS (CLAUDE / CHATGPT JAISE)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                    color = Hud.Cyan,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            if (mine.isEmpty()) {
                item(key = "mine_empty") {
                    Text(
                        "Koi bhi MCP connector URL jodo (Gmail, Calendar, Notion, Zapier, Composio…). Upar + JODO dabao. " +
                            "Phir boliye: \"meri mail dekho\" — JARVIS us service se kaam karega.",
                        style = MaterialTheme.typography.bodySmall, color = Hud.Dim,
                    )
                }
            }
            items(mine, key = { "mine_" + it.name }) { m ->
                Row(
                    Modifier.fillMaxWidth().glass(Hud.Cyan, HudShapes.Card, tint = 0.6f, edge = 0.6f).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(Hud.Green)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, color = Hud.White, maxLines = 1)
                        Text("${m.host} · ${m.tools} kaam", style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 1)
                    }
                    Text(
                        "HATAO",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                        color = Hud.Red,
                        modifier = Modifier.clickable { viewModel.removeConnector(m.name) }.padding(8.dp),
                    )
                }
            }
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

/** Name, MCP server URL and (optional) token — the same three things Claude / ChatGPT ask for a custom connector. */
@Composable
private fun AddConnectorDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var url by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var token by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Panel,
        title = { Text("CONNECTOR JODO", style = MaterialTheme.typography.titleMedium, color = Hud.Ice) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogField("Naam (jaise: Gmail)", name) { name = it }
                DialogField("MCP server URL (https://…)", url) { url = it }
                DialogField("Token / API key (agar chahiye)", token, secret = true) { token = it }
                Text("Token phone mein encrypted rehta hai, kabhi bola ya log nahi hota.", style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
            }
        },
        confirmButton = {
            Text(if (busy) "JUD RAHA…" else "JODO", color = Hud.Cyan, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !busy) { onSave(name, url, token) }.padding(10.dp))
        },
        dismissButton = { Text("CANCEL", color = Hud.Dim, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp)) },
    )
}

@Composable
private fun DialogField(hint: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().glass(Hud.Cyan, HudShapes.Chip, tint = 0.6f, edge = 0.6f).padding(horizontal = 12.dp, vertical = 10.dp)) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Hud.White),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Hud.Cyan),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Hud.Dim)
                inner()
            },
        )
    }
}

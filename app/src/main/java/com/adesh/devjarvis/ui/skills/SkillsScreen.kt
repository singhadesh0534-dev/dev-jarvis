package com.adesh.devjarvis.ui.skills

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adesh.devjarvis.brain.Skills
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.HudTag
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop

/** One skill as the screen shows it. */
data class SkillRow(val id: String, val area: String, val whenToUse: String, val steps: List<String>, val mine: Boolean) {
    val title: String get() = id.replace('_', ' ').replaceFirstChar { it.uppercase() }

    companion object {
        fun of(s: Skills.Skill, mine: Boolean) = SkillRow(
            id = s.id,
            area = if (mine) MINE else s.area,
            whenToUse = s.whenToUse,
            steps = s.playbook.split('\n', '|')
                .map { it.trim().replace(Regex("""^\d+[.)]\s*"""), "") }
                .filter { it.isNotEmpty() },
            mine = mine,
        )

        const val MINE = "mere"
        const val ALL = "sab"
    }
}

/** Every skill JARVIS knows, searchable, by area; the ones he taught himself first. */
@Composable
fun SkillsScreen(viewModel: SkillsViewModel, onBack: () -> Unit, onRun: () -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFile(uri) }
    var query by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf(SkillRow.ALL) }
    var open by remember { mutableStateOf<String?>(null) }

    val areas = remember(rows) { listOf(SkillRow.ALL) + rows.map { it.area }.distinct() }
    val q = query.trim().lowercase()
    val shown = rows.filter { r ->
        (area == SkillRow.ALL || r.area == area) &&
            (q.isEmpty() || r.id.contains(q.replace(' ', '_')) || r.whenToUse.lowercase().contains(q) || r.area.contains(q))
    }
    val mine = rows.count { it.mine }

    Column(
        Modifier
            .fillMaxSize()
            .hudBackdrop(glowY = 0f)
            .safeDrawingPadding(),
    ) {
        ScreenHeader("SKILLS", "${rows.size} kaam aate hain · $mine aapke / khud seekhe", onBack) {
            Action("+ JODO", Hud.Cyan) { adding = true }
            Action("IMPORT", Hud.Cyan) { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/*", "application/json", "application/octet-stream")) }
        }
        if (message.isNotEmpty()) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = Hud.Green,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { viewModel.clearMessage() },
            )
        }
        if (adding) AddSkillDialog(onDismiss = { adding = false }) { n, w, st -> if (viewModel.addManual(n, w, st)) adding = false }

        // Search
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .glass(Hud.Cyan, HudShapes.Pill, tint = 0.7f, edge = 0.6f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Hud.White),
                cursorBrush = SolidColor(Hud.Cyan),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Dhundo: EMI, resume, Wi-Fi, khet, exam…", style = MaterialTheme.typography.bodyLarge, color = Hud.Dim)
                    inner()
                },
            )
        }

        // Areas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(areas, key = { it }) { a ->
                val on = a == area
                val count = if (a == SkillRow.ALL) rows.size else rows.count { it.area == a }
                Text(
                    "${a.uppercase()} $count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = if (on) Hud.Background else if (a == SkillRow.MINE) Hud.Amber else Hud.Ice,
                    modifier = Modifier
                        .background(if (on) Hud.Cyan else Hud.Panel, HudShapes.Pill)
                        .border(1.dp, if (on) Hud.Cyan else Hud.Line, HudShapes.Pill)
                        .clickable { area = a }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        if (shown.isEmpty()) {
            Text(
                "Aisi koi skill nahi mili. JARVIS ko bas boliye — naya kaam wo khud seekh ke yahan jod dega.",
                style = MaterialTheme.typography.bodyMedium,
                color = Hud.Dim,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { (if (it.mine) "m_" else "s_") + it.id }) { r ->
                SkillCard(
                    r,
                    expanded = open == r.id,
                    onToggle = { open = if (open == r.id) null else r.id },
                    onRun = { viewModel.run(r); onRun() },
                    onDelete = { viewModel.delete(r.id) },
                )
            }
        }
    }
}

@Composable
private fun SkillCard(r: SkillRow, expanded: Boolean, onToggle: () -> Unit, onRun: () -> Unit, onDelete: () -> Unit) {
    val accent = if (r.mine) Hud.Amber else Hud.Cyan
    Column(
        Modifier
            .fillMaxWidth()
            .glass(accent, HudShapes.Card, tint = if (expanded) 1f else 0.5f, edge = if (expanded) 1f else 0.5f)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.title, style = MaterialTheme.typography.titleSmall, color = Hud.White, maxLines = 1, modifier = Modifier.weight(1f))
            HudTag(r.area.uppercase(), accent)
        }
        Text(
            r.whenToUse,
            style = MaterialTheme.typography.bodySmall,
            color = Hud.Dim,
            maxLines = if (expanded) 6 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (expanded) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.steps.forEachIndexed { i, step ->
                    Row {
                        Text(
                            "%02d".format(i + 1),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accent,
                            modifier = Modifier.padding(end = 10.dp, top = 2.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodySmall, color = Hud.Ice)
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("IS SE KAAM KARAO", accent, onRun)
                if (r.mine) Action("HATAO", Hud.Red, onDelete)
            }
        }
    }
}

@Composable
private fun Action(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), HudShapes.Pill)
            .border(1.dp, color.copy(alpha = 0.45f), HudShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/** Sir writes his own skill: a name, when JARVIS should use it, and the steps (one per line). */
@Composable
private fun AddSkillDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var whenToUse by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Panel,
        title = { Text("NAYI SKILL", style = MaterialTheme.typography.titleMedium, color = Hud.Ice) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Naam (jaise: Gym plan)", name, single = true) { name = it }
                Field("Kab use kare (jaise: jab main workout ka bolun)", whenToUse, single = true) { whenToUse = it }
                Field("Steps — har line mein ek step", steps, single = false) { steps = it }
            }
        },
        confirmButton = { Action("SAVE", Hud.Cyan) { onSave(name, whenToUse, steps) } },
        dismissButton = { Action("CANCEL", Hud.Dim, onDismiss) },
    )
}

@Composable
private fun Field(hint: String, value: String, single: Boolean, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .glass(Hud.Cyan, HudShapes.Chip, tint = 0.6f, edge = 0.6f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = single,
            minLines = if (single) 1 else 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Hud.White),
            cursorBrush = SolidColor(Hud.Cyan),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Hud.Dim)
                inner()
            },
        )
    }
}

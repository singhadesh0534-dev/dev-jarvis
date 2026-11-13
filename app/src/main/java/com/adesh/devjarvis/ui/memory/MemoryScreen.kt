package com.adesh.devjarvis.ui.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.data.ConversationEntity
import com.adesh.devjarvis.memory.Fact
import com.adesh.devjarvis.memory.MemoryExtraction
import com.adesh.devjarvis.memory.MemoryImporter
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memory: MemoryRepository,
    private val importer: MemoryImporter,
) : ViewModel() {
    /** Import progress / result line; null = nothing to show. */
    val importStatus = MutableStateFlow<String?>(null)
    private var importing = false

    fun importFrom(uri: Uri) = runImport { progress -> importer.importFile(uri, progress) }

    /** Text pasted by Sir (notes, or another assistant's "what I know about you" answer). */
    fun importPasted(text: String) {
        if (text.isNotBlank()) runImport { progress -> importer.importText(text, progress) }
    }

    private fun runImport(work: suspend ((Int, Int) -> Unit) -> MemoryImporter.Outcome) {
        if (importing) return
        importing = true
        importStatus.value = "Padh raha hoon…"
        viewModelScope.launch {
            val outcome = runCatching {
                work { done, total -> importStatus.value = "Yaadein nikaal raha hoon… $done / $total" }
            }.getOrElse { MemoryImporter.Outcome.Failed("Import ruk gaya (${it.javaClass.simpleName}).") }
            importStatus.value = when (outcome) {
                is MemoryImporter.Outcome.Done ->
                    "${outcome.added} nayi baatein yaad kar li." +
                        (if (outcome.withoutAi) " AI nahi mila, isliye lines jaisi thi waisi save ki — galat wali neeche se hata dijiye." else "") +
                        (if (outcome.truncated) " File bahut badi thi — sirf shuru ka hissa padha. \"memories\" wali file chhoti aur behtar hai." else "")
                is MemoryImporter.Outcome.Failed -> outcome.reason
            }
            importing = false
        }
    }

    val facts = memory.allFacts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val summaries = memory.recentSummaries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(text: String, category: String) = viewModelScope.launch {
        if (text.isNotBlank() && !MemoryExtraction.looksSecret(text)) memory.add(text, category)
    }
    fun update(f: Fact, text: String) = viewModelScope.launch { if (text.isNotBlank()) memory.update(f, text) }
    fun delete(f: Fact) = viewModelScope.launch { memory.delete(f.id) }
    fun forgetEverything() = viewModelScope.launch { memory.forgetEverything() }
}

/** Yaaddasht: kya yaad hai, badlo, mitao. Sab sirf phone pe. */
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, onBack: () -> Unit) {
    val facts by viewModel.facts.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Fact?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.importFrom(uri) }

    Column(Modifier.fillMaxSize().hudBackdrop(glowY = 0f).safeDrawingPadding().imePadding()) {
        ScreenHeader(
            title = "MEMORY",
            caption = "${facts.size} yaadein · sab sirf is phone pe",
            onBack = onBack,
        )
        MemoryActions(
            onImport = { picker.launch(arrayOf("*/*")) },
            onPaste = { pasting = true },
            onAdd = { adding = true },
            onWipe = { confirmWipe = true },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "import_hint") {
                Text(
                    importStatus ?: "IMPORT: koi .txt / .json / .zip file chuno (jaise Claude export ki memories-000.zip). PASTE: text seedha chipkao — jaise Claude / ChatGPT se poochho \"mere baare mein jo bhi jaante ho sab list karo\" aur uska jawab yahan daal do. OTP / password / bank wali cheezein kabhi save nahi hoti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (importStatus != null) Hud.Cyan else Hud.Dim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(if (importStatus != null) Hud.Cyan else Hud.Dim, HudShapes.Card, tint = 0.6f, edge = 0.6f)
                        .padding(12.dp),
                )
            }
            if (facts.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("MEMORY BANKS KHAALI", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp), color = Hud.Cyan.copy(alpha = 0.7f))
                        Text(
                            "Abhi kuch yaad nahi. Boliye: \"Jarvis, yaad rakhna meri wife ka birthday 12 March hai\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Hud.Dim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            MemoryExtraction.CATEGORIES.forEach { cat ->
                val inCat = facts.filter { it.category == cat }
                if (inCat.isNotEmpty()) {
                    item(key = "h_$cat") { GroupHeader(cat.uppercase(), inCat.size) }
                    items(inCat, key = { it.id }) { f -> FactRow(f, onEdit = { editing = f }, onDelete = { viewModel.delete(f) }) }
                }
            }
            if (summaries.isNotEmpty()) {
                item(key = "h_conv") { GroupHeader("RECENT CONVERSATIONS", summaries.take(20).size) }
                items(summaries.take(20), key = { "c${it.id}" }) { SummaryRow(it) }
            }
        }
    }

    editing?.let { f ->
        TextDialog("EDIT MEMORY", f.text, onDismiss = { editing = null }) { text -> viewModel.update(f, text); editing = null }
    }
    if (pasting) {
        TextDialog("PASTE MEMORY TEXT", "", onDismiss = { pasting = false }) { text -> viewModel.importPasted(text); pasting = false }
    }
    if (adding) {
        TextDialog("NEW MEMORY", "", onDismiss = { adding = false }) { text -> viewModel.add(text, "other"); adding = false }
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = Hud.Panel,
            shape = HudShapes.Panel,
            title = { Text("Sab bhool jaun, Sir?", color = Hud.Amber) },
            text = { Text("Saari yaadein aur conversation summaries mit jayengi. Ye wapas nahi aayengi.", color = Hud.Ice) },
            confirmButton = { TextButton(onClick = { viewModel.forgetEverything(); confirmWipe = false }) { Text("HAAN, MITA DO", color = Hud.Amber) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("NAHI", color = Hud.Cyan) } },
        )
    }
}

/** IMPORT · PASTE · ADD · WIPE as pills that wrap on small screens. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MemoryActions(onImport: () -> Unit, onPaste: () -> Unit, onAdd: () -> Unit, onWipe: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionPill("IMPORT", Hud.Cyan, onImport)
        ActionPill("PASTE", Hud.Cyan, onPaste)
        ActionPill("ADD", Hud.Cyan, onAdd)
        ActionPill("WIPE", Hud.Amber, onWipe)
    }
}

@Composable
private fun ActionPill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.8.sp),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .glass(color, HudShapes.Pill, tint = 0.8f, edge = 0.8f)
            .clickable(onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(Hud.Cyan, HudShapes.Pill))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Hud.Cyan,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
    }
}

@Composable
private fun FactRow(f: Fact, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(Hud.Cyan, HudShapes.Card, tint = 0.5f, edge = 0.55f)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.text, style = MaterialTheme.typography.bodyMedium, color = Hud.Ice, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Hud.Dim) }
    }
}

private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH)

@Composable
private fun SummaryRow(c: ConversationEntity) {
    Row(Modifier.fillMaxWidth()) {
        // Timeline rail.
        Column(Modifier.padding(top = 6.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(7.dp).background(Hud.Cyan.copy(alpha = 0.8f), HudShapes.Pill))
            Box(Modifier.size(width = 1.dp, height = 36.dp).background(Hud.Line))
        }
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            Text(dateFormat.format(Date(c.startedAt)), style = MaterialTheme.typography.labelSmall, color = Hud.Dim)
            Text(c.summary, style = MaterialTheme.typography.bodySmall, color = Hud.Ice, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.Panel,
        shape = HudShapes.Panel,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = Hud.Cyan) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = HudShapes.Chip,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Hud.Cyan,
                    unfocusedBorderColor = Hud.Line,
                    cursorColor = Hud.Cyan,
                    focusedTextColor = Hud.White,
                    unfocusedTextColor = Hud.Ice,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("SAVE", color = Hud.Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Hud.Dim) } },
    )
}

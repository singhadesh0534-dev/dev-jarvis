package com.adesh.devjarvis.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.adesh.devjarvis.terminal.CodeRunner
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.ScreenHeader
import com.adesh.devjarvis.ui.theme.glass
import com.adesh.devjarvis.ui.theme.hudBackdrop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(val runner: CodeRunner) : ViewModel() {
    /** A command, or — while a program waits for input — the answer to it. */
    fun submit(line: String) {
        if (runner.waiting.value) { runner.answer(line); return }
        if (line.isBlank()) return
        if (line.trim() == "clear") { runner.clear(); return }
        viewModelScope.launch { runner.run(line, interactive = true) }
    }
}

/**
 * JARVIS's own terminal: Python 3 and shell commands in the app's home folder (no Termux). Everything JARVIS runs
 * with run_code shows here too; programs that ask for input get it from the box at the bottom.
 */
@Composable
fun TerminalScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val log by viewModel.runner.log.collectAsStateWithLifecycle()
    val waiting by viewModel.runner.waiting.collectAsStateWithLifecycle()
    val busy by viewModel.runner.busy.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) list.animateScrollToItem(log.size - 1) }
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

    Column(Modifier.fillMaxSize().hudBackdrop(glowY = 0f).safeDrawingPadding().imePadding()) {
        ScreenHeader("TERMINAL", "Python 3 + shell, JARVIS ke andar — Termux ki zaroorat nahi", onBack) {
            TextButton(onClick = { viewModel.submit("clear") }) { Text("CLEAR", color = Hud.Cyan) }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .glass(Hud.Cyan, HudShapes.Card, tint = 0.3f, edge = 0.4f).background(Hud.Abyss.copy(alpha = 0.6f))
                .padding(10.dp),
            state = list,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(log) { _, l ->
                Text(
                    l.text,
                    style = mono,
                    color = when (l.kind) {
                        CodeRunner.TermLine.Kind.COMMAND -> Hud.Cyan
                        CodeRunner.TermLine.Kind.ERROR -> Hud.Red
                        CodeRunner.TermLine.Kind.INFO -> Hud.Dim
                        CodeRunner.TermLine.Kind.OUTPUT -> Hud.White
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp).glass(Hud.Cyan, HudShapes.Chip, tint = 0.6f).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (waiting) "› " else "$ ", style = mono, color = if (waiting) Hud.Amber else Hud.Cyan)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = mono.copy(color = Hud.White),
                cursorBrush = SolidColor(Hud.Cyan),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                keyboardActions = KeyboardActions(onSend = { val t = text; text = ""; scope.launch { viewModel.submit(t) } }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            when { waiting -> "program jawab maang raha hai…"; busy -> "chal raha hai…"; else -> "python calculator.py · ls · python -c \"print(2+2)\"" },
                            style = mono, color = Hud.Dim,
                        )
                    }
                    inner()
                },
            )
            TextButton(onClick = { val t = text; text = ""; viewModel.submit(t) }) { Text("RUN", color = Hud.Cyan) }
        }
    }
}

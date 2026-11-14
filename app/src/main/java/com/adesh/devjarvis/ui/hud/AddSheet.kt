package com.adesh.devjarvis.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adesh.devjarvis.ui.theme.Hud
import com.adesh.devjarvis.ui.theme.HudLineIcon
import com.adesh.devjarvis.ui.theme.HudShapes
import com.adesh.devjarvis.ui.theme.LineIcon
import com.adesh.devjarvis.ui.theme.glass

/** What the "+" sheet needs to show. */
data class AddSheetState(
    val research: Boolean,
    val autoMemory: Boolean,
    val skills: Int,
    val learnedSkills: Int,
)

/**
 * The "+" sheet: give JARVIS a photo, a camera shot or a file, open his skills and connectors, and switch research
 * mode and auto-memory. Slides up over the HUD; a tap outside closes it.
 */
@Composable
fun BoxScope.AddSheet(
    visible: Boolean,
    accent: Color,
    state: AddSheetState,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onSkills: () -> Unit,
    onConnectors: () -> Unit,
    onResearch: (Boolean) -> Unit,
    onMemory: (Boolean) -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .glass(accent, shape, tint = 1.2f)
                .background(Hud.Background.copy(alpha = 0.55f), shape)
                // swallow taps so they don't reach the scrim
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(4.dp).background(Hud.Line, HudShapes.Pill))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "JARVIS KO DO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = 3.sp,
                            brush = Brush.linearGradient(listOf(Hud.White, Hud.Ice, accent)),
                        ),
                    )
                    Text("Photo, file ya kaam — wo dekh ke samjhayega", style = MaterialTheme.typography.bodySmall, color = Hud.Dim)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Band karo", tint = Hud.Dim) }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(LineIcon.CAMERA, "Camera", accent, onCamera)
                Tile(LineIcon.PHOTO, "Photos", accent, onPhotos)
                Tile(LineIcon.FILE, "Files", accent, onFiles)
            }

            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp).glass(accent, HudShapes.Panel, tint = 0.6f, edge = 0.6f),
            ) {
                LinkRow(LineIcon.SKILLS, "Skills", "${state.skills}+ kaam · ${state.learnedSkills} khud seekhe", accent, onSkills)
                Hairline()
                LinkRow(LineIcon.CONNECTORS, "Connectors", "Dimaag, permissions, apps — kya juda hai", accent, onConnectors)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).glass(accent, HudShapes.Panel, tint = 0.6f, edge = 0.6f),
            ) {
                SwitchRow(LineIcon.RESEARCH, "Research", "Har sawaal web pe jaanch ke, detail mein", state.research, accent, onResearch)
                Hairline()
                SwitchRow(LineIcon.MEMORY, "Memory", "Baaton se khud yaad rakhe (\"yaad rakho\" hamesha chalta hai)", state.autoMemory, accent, onMemory)
            }
        }
    }
}

@Composable
private fun RowScope.Tile(icon: LineIcon, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .glass(accent, HudShapes.Panel, tint = 0.8f, edge = 0.7f)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)), HudShapes.Pill),
            contentAlignment = Alignment.Center,
        ) { HudLineIcon(icon, accent, size = 26.dp) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = Hud.White, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LinkRow(icon: LineIcon, title: String, detail: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, accent)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Hud.White)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Hud.Dim)
    }
}

@Composable
private fun SwitchRow(icon: LineIcon, title: String, detail: String, on: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (on) accent else Hud.Dim)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Hud.White)
                if (on) {
                    Text(
                        "ON",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
                        color = accent,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Hud.Dim, maxLines = 2)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            modifier = Modifier.graphicsLayer { scaleX = 0.82f; scaleY = 0.82f },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.Background,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = Hud.Dim,
                uncheckedTrackColor = Hud.Panel,
                uncheckedBorderColor = Hud.Line,
            ),
        )
    }
}

@Composable
private fun RowIcon(icon: LineIcon, color: Color) {
    Box(
        Modifier.size(38.dp).background(color.copy(alpha = 0.10f), HudShapes.Chip),
        contentAlignment = Alignment.Center,
    ) { HudLineIcon(icon, color, size = 22.dp) }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Hud.Line.copy(alpha = 0.6f)))
}

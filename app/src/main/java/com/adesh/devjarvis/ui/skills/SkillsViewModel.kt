package com.adesh.devjarvis.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.adesh.devjarvis.brain.LearnedSkills
import com.adesh.devjarvis.brain.Skills
import com.adesh.devjarvis.core.JarvisSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Every skill JARVIS knows: the ones he was built with and the ones he wrote himself. */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val learned: LearnedSkills,
    private val session: JarvisSession,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val _rows = MutableStateFlow(load())
    val rows: StateFlow<List<SkillRow>> = _rows.asStateFlow()

    /** One line about the last add / import, for the screen. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()
    fun clearMessage() { _message.value = "" }

    /** Sir's own skill from the form. True when it was saved. */
    fun addManual(name: String, whenToUse: String, steps: String): Boolean {
        val skill = com.adesh.devjarvis.brain.SkillImport.manual(name, whenToUse, steps)
        if (skill == null) {
            _message.value = "Naam aur kam se kam ek step likhiye."
            return false
        }
        learned.save(skill)
        _rows.value = load()
        _message.value = "\"${name.trim()}\" jod di — ab JARVIS ise use karega."
        return true
    }

    /** A .zip of skills, or one SKILL.md / .md / .txt / .json file. */
    fun importFile(uri: android.net.Uri) {
        viewModelScope.launch {
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { readSkills(uri) }.getOrDefault(emptyList())
            }
            found.forEach { learned.save(it) }
            _rows.value = load()
            _message.value = if (found.isEmpty()) "Is file mein koi skill nahi mili (SKILL.md, .md, .txt ya .json chahiye)."
            else "${found.size} skill import ho gayi: " + found.take(3).joinToString { it.id.replace('_', ' ') } + if (found.size > 3) "…" else ""
        }
    }

    private fun readSkills(uri: android.net.Uri): List<com.adesh.devjarvis.brain.Skills.Skill> {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val isZip = name.endsWith(".zip", true) || resolver.getType(uri).orEmpty().contains("zip")
        if (!isZip) {
            val text = resolver.openInputStream(uri)?.use { String(it.readBytes().take(MAX_FILE).toByteArray()) } ?: return emptyList()
            return com.adesh.devjarvis.brain.SkillImport.fromFile(name, text)
        }
        val out = mutableListOf<com.adesh.devjarvis.brain.Skills.Skill>()
        resolver.openInputStream(uri)?.use { input ->
            java.util.zip.ZipInputStream(input).use { zip ->
                var entries = 0
                var e = zip.nextEntry
                while (e != null && entries < MAX_ENTRIES) {
                    entries++
                    val n = e.name
                    if (!e.isDirectory && !n.contains("__MACOSX") && !n.substringAfterLast('/').startsWith(".")) {
                        val bytes = zip.readBytes()
                        if (bytes.size <= MAX_FILE) out += com.adesh.devjarvis.brain.SkillImport.fromFile(n, String(bytes))
                    }
                    e = zip.nextEntry
                }
            }
        }
        return out.distinctBy { it.id }.take(com.adesh.devjarvis.brain.SkillWriter.MAX_LEARNED)
    }

    private companion object {
        const val MAX_FILE = 200_000
        const val MAX_ENTRIES = 400
    }

    private fun load(): List<SkillRow> =
        learned.all().map { SkillRow.of(it, mine = true) } + Skills.ALL.map { SkillRow.of(it, mine = false) }

    /** Only a skill he wrote himself can be removed. */
    fun delete(id: String) {
        learned.delete(id)
        _rows.value = load()
    }

    /** Back on the home screen JARVIS starts it and asks for whatever he needs. */
    fun run(row: SkillRow) = session.onTextSubmitted(
        "\"${row.id}\" skill se kaam shuru karo (${row.whenToUse.take(90)}). Jo jaankari chahiye wo mujhse pooch lo.",
    )
}

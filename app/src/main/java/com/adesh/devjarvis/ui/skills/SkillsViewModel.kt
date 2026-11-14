package com.adesh.devjarvis.ui.skills

import androidx.lifecycle.ViewModel
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
) : ViewModel() {
    private val _rows = MutableStateFlow(load())
    val rows: StateFlow<List<SkillRow>> = _rows.asStateFlow()

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

package com.adesh.devjarvis.brain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Skills JARVIS wrote himself, kept on the phone and loaded into [Skills.learned] at start. */
@Singleton
class LearnedSkills @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("learned_skills", Context.MODE_PRIVATE)

    init {
        Skills.learned = SkillWriter.decode(prefs.getString("all", "[]"))
    }

    fun all(): List<Skills.Skill> = Skills.learned

    fun save(skill: Skills.Skill) {
        Skills.learned = SkillWriter.merge(Skills.learned, skill)
        prefs.edit().putString("all", SkillWriter.encode(Skills.learned)).apply()
    }

    fun delete(id: String): Boolean {
        val before = Skills.learned
        Skills.learned = before.filter { it.id != id }
        prefs.edit().putString("all", SkillWriter.encode(Skills.learned)).apply()
        return before.size != Skills.learned.size
    }
}

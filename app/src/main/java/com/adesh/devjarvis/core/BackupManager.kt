package com.adesh.devjarvis.core

import android.content.Context
import android.net.Uri
import com.adesh.devjarvis.brain.JarvisBrain
import com.adesh.devjarvis.brain.LearnedSkills
import com.adesh.devjarvis.brain.SkillWriter
import com.adesh.devjarvis.life.LifeCodec
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.memory.MemoryRepository
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sab kuch ek file mein: yaadein, routines, notes, lists, kharche, diary, triggers, seekhe hue skills, haal ki
 * baat-cheet, roop aur awaaz. Reinstall ke baad wahi file chuno — sab wapas. API keys kabhi file mein nahi jaati.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memory: MemoryRepository,
    private val life: LifeStore,
    private val learned: LearnedSkills,
    private val brain: JarvisBrain,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun export(uri: Uri): String = withContext(Dispatchers.IO) {
        val facts = memory.all()
        val s = settings.current
        val body = buildJsonObject {
            put("app", "dev-jarvis")
            put("version", 1)
            put("saved", System.currentTimeMillis())
            put("facts", buildJsonArray { facts.forEach { f -> add(buildJsonObject { put("text", f.text); put("cat", f.category) }) } })
            put("life", LifeCodec.encode(life.get()))
            put("skills", SkillWriter.encode(learned.all()))
            put("conversation", brain.conversationText())
            put("settings", buildJsonObject {
                put("persona", s.persona); put("gfVoice", s.gfVoice); put("liveVoice", s.liveVoice); put("voiceEngine", s.voiceEngine)
                put("homeCity", s.homeCity); put("emergencyContacts", s.emergencyContacts); put("importantContacts", s.importantContacts)
                put("importantApps", s.importantApps); put("customWakePhrases", s.customWakePhrases); put("speakerProfile", s.speakerProfile)
                put("noiseFilter", s.noiseFilter); put("wakeSensitivity", s.wakeSensitivity.toDouble()); put("systemPrompt", s.systemPrompt)
            })
        }.toString()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(body.toByteArray()) } ?: error("could not write the file")
        "${facts.size} yaadein, ${learned.all().size} seekhe skills aur baaki sab file mein save ho gaya."
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("could not read the file")
        val o = json.parseToJsonElement(text) as JsonObject
        require((o["app"] as? JsonPrimitive)?.contentOrNull == "dev-jarvis") { "ye Dev Jarvis ki backup file nahi hai" }
        var added = 0
        (o["facts"] as? JsonArray).orEmpty().forEach { e ->
            val f = e as? JsonObject ?: return@forEach
            val t = f["text"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            memory.add(t, f["cat"]?.jsonPrimitive?.contentOrNull ?: "other"); added++
        }
        o["life"]?.jsonPrimitive?.contentOrNull?.let { raw -> LifeCodec.decode(raw).let { d -> life.update { d } } }
        o["skills"]?.jsonPrimitive?.contentOrNull?.let { raw -> SkillWriter.decode(raw).forEach(learned::save) }
        o["conversation"]?.jsonPrimitive?.contentOrNull?.let(brain::restoreConversation)
        (o["settings"] as? JsonObject)?.let { st ->
            fun str(k: String) = st[k]?.jsonPrimitive?.contentOrNull
            settings.update { c ->
                c.copy(
                    persona = str("persona") ?: c.persona, gfVoice = str("gfVoice") ?: c.gfVoice, liveVoice = str("liveVoice") ?: c.liveVoice,
                    voiceEngine = str("voiceEngine") ?: c.voiceEngine, homeCity = str("homeCity") ?: c.homeCity,
                    emergencyContacts = str("emergencyContacts") ?: c.emergencyContacts,
                    importantContacts = str("importantContacts") ?: c.importantContacts, importantApps = str("importantApps") ?: c.importantApps,
                    customWakePhrases = str("customWakePhrases") ?: c.customWakePhrases, speakerProfile = str("speakerProfile") ?: c.speakerProfile,
                    noiseFilter = str("noiseFilter")?.toIntOrNull() ?: c.noiseFilter,
                    wakeSensitivity = str("wakeSensitivity")?.toFloatOrNull() ?: c.wakeSensitivity,
                    systemPrompt = str("systemPrompt") ?: c.systemPrompt,
                )
            }
        }
        "$added yaadein, routines, notes, diary, skills aur baat-cheet wapas aa gaye. Ab sirf API keys daal dijiye."
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}

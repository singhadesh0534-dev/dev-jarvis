package com.adesh.devjarvis.tools

import android.content.Context
import com.adesh.devjarvis.brain.GroqMessages
import com.adesh.devjarvis.core.Edition
import com.adesh.devjarvis.memory.MemoryTools
import com.adesh.devjarvis.notifications.NotificationTools
import com.adesh.devjarvis.screen.ScreenTools
import com.adesh.devjarvis.tools.impl.CommunicationTools
import com.adesh.devjarvis.tools.impl.InfoTools
import com.adesh.devjarvis.tools.impl.PhoneTools
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Saare tools ki list + Gemini ko bhejne wala `tools` JSON. */
@Singleton
class ToolRegistry @Inject constructor(
    @ApplicationContext context: Context,
    communication: CommunicationTools,
    phone: PhoneTools,
    info: InfoTools,
    memory: MemoryTools,
    notifications: NotificationTools,
    screen: ScreenTools,
) {
    val tools: List<JarvisTool> = (communication.tools + phone.tools + info.tools + memory.tools + notifications.tools + screen.tools)
        .filter { Edition.isFull || it.name !in Edition.FULL_ONLY_TOOLS }
        .filter { t -> Edition.TOOL_NEEDS_PERMISSION[t.name]?.let { Edition.declares(context, it) } ?: true }
    private val byName = tools.associateBy { it.name }

    val declarations: List<JsonObject> = tools.map { it.declaration() }

    /** Gemini `tools` array: one entry with all function declarations. */
    val geminiTools: JsonArray = buildJsonArray {
        add(buildJsonObject { put("functionDeclarations", JsonArray(declarations)) })
    }

    /** Same tools in OpenAI format for Groq. */
    val groqTools: JsonArray = GroqMessages.toolsFromGemini(declarations)

    suspend fun execute(name: String, args: JsonObject, ctx: ToolContext): ToolResult {
        val tool = byName[name] ?: return ToolResult.fail("unknown tool $name")
        return try {
            tool.execute(ToolArgs(args), ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolArgError) {
            ToolResult.fail("bad arguments: ${e.message}")
        } catch (e: SecurityException) {
            ToolResult.fail("permission missing — ask Sir to allow it in JARVIS Setup")
        } catch (e: Exception) {
            ToolResult.fail("failed: ${e.javaClass.simpleName}")
        }
    }
}

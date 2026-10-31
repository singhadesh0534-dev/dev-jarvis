package com.adesh.devjarvis.memory

import com.adesh.devjarvis.tools.FuzzyMatcher
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import javax.inject.Inject
import javax.inject.Singleton

/** remember / recall / forget. */
@Singleton
class MemoryTools @Inject constructor(private val memory: MemoryRepository) {

    private val remember = object : SimpleTool(
        "remember", "Save a durable fact about Sir when he asks you to remember something (never OTPs, passwords or bank details).",
        params {
            string("fact", "Short third-person sentence, e.g. \"Sir's wife's birthday is 12 March.\"", required = true)
            string("category", "person | preference | routine | project | other", enum = MemoryExtraction.CATEGORIES)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val fact = args.requireString("fact")
            if (MemoryExtraction.looksSecret(fact)) {
                return ToolResult.fail("not stored: looks like a secret (OTP / password / PIN / long number)")
            }
            memory.add(fact, args.string("category") ?: "other")
            return ToolResult.ok("remembered")
        }
    }

    private val recall = object : SimpleTool(
        "recall", "Search what you remember about Sir.",
        params { string("query", "What to look for, e.g. \"wife birthday\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val found = memory.relevant(args.requireString("query"), limit = 5)
            return if (found.isEmpty()) ToolResult.ok("nothing remembered about that")
            else ToolResult.ok(found.joinToString(" | ") { it.text })
        }
    }

    private val forget = object : SimpleTool(
        "forget", "Delete a remembered fact when Sir asks you to forget it. The app asks Sir to confirm.",
        params { string("fact", "What to forget, in Sir's words", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.requireString("fact")
            val all = memory.all()
            val best = FuzzyMatcher.rank(q, all, threshold = 0.5) { listOf(it.text) }.firstOrNull()?.item
                ?: MemoryRanker.top(all, q, System.currentTimeMillis(), 1).firstOrNull()
                ?: return ToolResult.fail("no matching memory")
            if (!ctx.confirm("Sir, ye bhool jaun: \"${best.text}\"?")) return ToolResult.ok("kept it — Sir said no")
            memory.delete(best.id)
            return ToolResult.ok("forgotten: ${best.text}")
        }
    }

    val tools: List<JarvisTool> = listOf(remember, recall, forget)
}

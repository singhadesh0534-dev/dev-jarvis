package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.life.DiaryLogic
import com.adesh.devjarvis.life.LifeMath
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.life.Trigger
import com.adesh.devjarvis.life.TriggerLogic
import com.adesh.devjarvis.proactive.AutomationEngine
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import dagger.Lazy
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Triggers ("jab ... tab ...") and the daily diary. */
@Singleton
class AutomationTools @Inject constructor(
    private val store: LifeStore,
    // Lazy: the engine needs the session, the session needs the tools.
    private val engine: Lazy<AutomationEngine>,
) {
    private val addTrigger = object : SimpleTool(
        "add_trigger",
        "Set up an automatic rule: WHEN something happens, JARVIS does what Sir said, by himself. Use for \"jab charger lage / hate\", " +
            "\"jab Wi-Fi jude / kate\", \"jab earphones lagaun\", \"roz 10 baje\", \"jab Papa ka message aaye\" ... \"tab ye karo\". " +
            "For a one-time reminder use create_reminder instead.",
        params {
            string("event", TriggerLogic.EVENTS.joinToString(" | "), required = true, enum = TriggerLogic.EVENTS)
            string("param", "For event=time: 24-hour HH:mm, e.g. 22:30. For event=message_from: the person's or app's name. Otherwise leave out.")
            string("action", "What to do then, as a full instruction in Sir's words, e.g. \"Wi-Fi band karo aur brightness 20 karo\"", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val event = args.enum("event", TriggerLogic.EVENTS) ?: return ToolResult.fail("unknown event; use one of ${TriggerLogic.EVENTS.joinToString()}")
            val param = TriggerLogic.normalizeParam(event, args.string("param").orEmpty())
                ?: return ToolResult.fail(if (event == "time") "the time must be HH:mm in 24-hour form, e.g. 22:30" else "whose message? give the name")
            val action = args.requireString("action")
            if (store.get().triggers.size >= TriggerLogic.MAX) return ToolResult.fail("${TriggerLogic.MAX} rules already — Sir should delete one first")
            val t = Trigger(System.currentTimeMillis(), event, param, action)
            store.update { it.copy(triggers = it.triggers + t) }
            return ToolResult.ok("rule saved: ${TriggerLogic.describe(t)}. It works while JARVIS always-on is running.")
        }
    }

    private val listTriggers = object : SimpleTool("list_triggers", "List Sir's automatic rules (\"jab ... tab ...\").") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val all = store.get().triggers
            return ToolResult.ok(if (all.isEmpty()) "no rules set" else all.joinToString(" | ") { TriggerLogic.describe(it) })
        }
    }

    private val deleteTrigger = object : SimpleTool(
        "delete_trigger", "Delete an automatic rule, matched by words from it.",
        params { string("text", "Words from the rule, e.g. \"charger\" or \"22:30\"", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = LifeMath.key(args.requireString("text"))
            val hit = store.get().triggers.firstOrNull { q in LifeMath.key(TriggerLogic.describe(it)) || q in LifeMath.key(it.event) }
                ?: return ToolResult.fail("no rule matching '$q'")
            store.update { it.copy(triggers = it.triggers - hit) }
            return ToolResult.ok("deleted: ${TriggerLogic.describe(hit)}")
        }
    }

    private val diary = object : SimpleTool(
        "diary",
        "Sir's daily diary, written by JARVIS each night from what was done and discussed. Use for \"aaj maine kya kiya\", " +
            "\"kal kya hua tha\", \"pichhle mangal maine kya kiya tha\".",
        params { string("day", "today | yesterday | a weekday name | a date as YYYY-MM-DD", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val today = LocalDate.now()
            val day = DiaryLogic.resolve(args.requireString("day"), today)
                ?: return ToolResult.fail("which day? say today, yesterday, a weekday or a date like 2026-11-10")
            if (day == today) {
                val entry = runCatching { engine.get().writeDiary() }.getOrNull()
                return ToolResult.ok(entry ?: "nothing recorded today yet (or the diary is switched off in Settings)")
            }
            val all = store.get().diary
            return ToolResult.ok(
                all[day.toString()] ?: ("no diary entry for $day. " +
                    if (all.isEmpty()) "The diary has only just started." else "Entries exist from ${all.keys.minOrNull()} to ${all.keys.maxOrNull()}."),
            )
        }
    }

    val tools: List<JarvisTool> = listOf(addTrigger, listTriggers, deleteTrigger, diary)
}

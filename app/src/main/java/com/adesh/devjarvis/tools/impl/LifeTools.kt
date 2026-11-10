package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.life.Expense
import com.adesh.devjarvis.life.LifeMath
import com.adesh.devjarvis.life.LifeStore
import com.adesh.devjarvis.life.Note
import com.adesh.devjarvis.tools.FuzzyMatcher
import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Routines / voice shortcuts, notes, lists, expenses. */
@Singleton
class LifeTools @Inject constructor(private val store: LifeStore) {

    private val day = SimpleDateFormat("d MMM", Locale.ENGLISH)

    private val saveRoutine = object : SimpleTool(
        "save_routine",
        "Save or replace a routine / voice shortcut: a phrase Sir says and the steps you then do. Use when Sir says " +
            "\"jab main X bolun to Y karna\" or asks to create/change a routine.",
        params {
            string("name", "The trigger phrase, e.g. \"office mode\"", required = true)
            string("steps", "What to do, as a clear ordered list in plain words, e.g. \"mute ring volume; open Gmail; remind in 60 minutes to stretch\"", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = LifeMath.key(args.requireString("name"))
            if (name.isEmpty()) return ToolResult.fail("routine needs a name")
            store.update { it.copy(routines = it.routines + (name to args.requireString("steps"))) }
            return ToolResult.ok("routine '$name' saved")
        }
    }

    private val deleteRoutine = object : SimpleTool(
        "delete_routine", "Delete a saved routine / shortcut.",
        params { string("name", "Routine name", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = LifeMath.key(args.requireString("name"))
            val key = store.get().routines.keys.let { keys -> keys.firstOrNull { it == q } ?: FuzzyMatcher.rank(q, keys.toList()) { listOf(it) }.firstOrNull()?.item }
                ?: return ToolResult.fail("no routine called '$q'")
            store.update { it.copy(routines = it.routines - key) }
            return ToolResult.ok("routine '$key' deleted")
        }
    }

    private val listRoutines = object : SimpleTool("list_routines", "List Sir's saved routines / shortcuts and their steps.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val r = store.get().routines
            return ToolResult.ok(if (r.isEmpty()) "no routines saved" else r.entries.joinToString(" | ") { "${it.key}: ${it.value}" })
        }
    }

    private val addNote = object : SimpleTool(
        "add_note", "Save a note for Sir (\"note kar lo…\").",
        params { string("text", "The note", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            store.update { it.copy(notes = (it.notes + Note(System.currentTimeMillis(), args.requireString("text"))).takeLast(500)) }
            return ToolResult.ok("noted")
        }
    }

    private val getNotes = object : SimpleTool(
        "get_notes", "Read Sir's notes, newest first. Optional search words.",
        params { string("query", "Words to look for") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.string("query")?.lowercase()
            val notes = store.get().notes.reversed().filter { q == null || q.split(' ').any { w -> w.length > 2 && w in it.text.lowercase() } }.take(8)
            return ToolResult.ok(if (notes.isEmpty()) "no notes found" else notes.joinToString(" | ") { "${day.format(Date(it.time))}: ${it.text}" })
        }
    }

    private val deleteNote = object : SimpleTool(
        "delete_note", "Delete the note that best matches the given words. The app asks Sir to confirm.",
        params { string("text", "Words from the note", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = args.requireString("text")
            val note = FuzzyMatcher.rank(q, store.get().notes, threshold = 0.5) { listOf(it.text) }.firstOrNull()?.item
                ?: store.get().notes.lastOrNull { n -> q.lowercase().split(' ').any { it.length > 2 && it in n.text.lowercase() } }
                ?: return ToolResult.fail("no matching note")
            if (!ctx.confirm("Sir, ye note mita doon: \"${note.text.take(80)}\"?")) return ToolResult.ok("kept the note")
            store.update { it.copy(notes = it.notes - note) }
            return ToolResult.ok("note deleted")
        }
    }

    private fun listKey(args: ToolArgs) = LifeMath.key(args.string("list") ?: "shopping").ifEmpty { "shopping" }

    private val listAdd = object : SimpleTool(
        "list_add", "Add one or more items to a named list (default \"shopping\").",
        params {
            string("items", "Items, comma-separated", required = true)
            string("list", "List name, e.g. shopping, to do, movies")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val key = listKey(args)
            val items = args.requireString("items").split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            val now = store.update { d -> d.copy(lists = d.lists + (key to (d.lists[key].orEmpty() + items).distinct())) }
            return ToolResult.ok("$key list now has ${now.lists[key]?.size} items")
        }
    }

    private val listGet = object : SimpleTool(
        "list_get", "Read a named list (default \"shopping\"), or say which lists exist.",
        params { string("list", "List name; omit to read the shopping list") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val d = store.get()
            val key = listKey(args)
            val items = d.lists[key]
            return when {
                !items.isNullOrEmpty() -> ToolResult.ok("$key: " + items.joinToString(", "))
                d.lists.isEmpty() -> ToolResult.ok("no lists yet")
                else -> ToolResult.ok("'$key' is empty. Lists that exist: " + d.lists.filter { it.value.isNotEmpty() }.keys.joinToString(", "))
            }
        }
    }

    private val listRemove = object : SimpleTool(
        "list_remove", "Remove an item from a list (bought / done), or clear the whole list with item \"all\".",
        params {
            string("item", "Item to remove, or \"all\"", required = true)
            string("list", "List name (default shopping)")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val key = listKey(args)
            val item = args.requireString("item")
            val items = store.get().lists[key].orEmpty()
            if (items.isEmpty()) return ToolResult.fail("'$key' list is empty")
            if (item.equals("all", ignoreCase = true)) {
                if (!ctx.confirm("Sir, poori $key list saaf kar doon?")) return ToolResult.ok("kept the list")
                store.update { it.copy(lists = it.lists - key) }
                return ToolResult.ok("$key list cleared")
            }
            val match = FuzzyMatcher.rank(item, items) { listOf(it) }.firstOrNull()?.item ?: return ToolResult.fail("'$item' is not on the $key list")
            store.update { it.copy(lists = it.lists + (key to items - match)) }
            return ToolResult.ok("removed $match; ${items.size - 1} left")
        }
    }

    private val addExpense = object : SimpleTool(
        "add_expense", "Record money Sir spent (\"200 petrol pe kharch hua\"). Bookkeeping only — this never pays anything.",
        params {
            string("amount", "Amount in rupees, digits only", required = true)
            string("category", "One word: petrol, khana, grocery, travel, shopping, bills, other…")
            string("note", "Optional detail")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val amount = args.requireString("amount").replace(",", "").filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                ?.takeIf { it > 0 } ?: return ToolResult.fail("how much? I need the amount as a number")
            val cat = LifeMath.key(args.string("category") ?: "other").ifEmpty { "other" }
            val d = store.update { it.copy(expenses = (it.expenses + Expense(System.currentTimeMillis(), amount, cat, args.string("note").orEmpty())).takeLast(5000)) }
            val now = System.currentTimeMillis()
            return ToolResult.ok("recorded. Today so far: " + LifeMath.expenseSummary(d.expenses, LifeMath.periodStart("today", now), now))
        }
    }

    private val expenseSummary = object : SimpleTool(
        "expense_summary", "How much Sir spent today, in the last 7 days (week) or this calendar month.",
        params { string("period", "today | week | month", enum = listOf("today", "week", "month")) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val period = args.enum("period", listOf("today", "week", "month")) ?: "today"
            val now = System.currentTimeMillis()
            return ToolResult.ok("$period: " + LifeMath.expenseSummary(store.get().expenses, LifeMath.periodStart(period, now), now))
        }
    }

    val tools: List<JarvisTool> = listOf(
        saveRoutine, deleteRoutine, listRoutines, addNote, getNotes, deleteNote, listAdd, listGet, listRemove, addExpense, expenseSummary,
    )
}

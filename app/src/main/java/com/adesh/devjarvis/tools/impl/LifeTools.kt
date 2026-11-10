package com.adesh.devjarvis.tools.impl

import com.adesh.devjarvis.core.LocationFinder
import com.adesh.devjarvis.life.Expense
import com.adesh.devjarvis.life.Place
import com.adesh.devjarvis.life.PlaceLogic
import com.adesh.devjarvis.life.PlaceReminder
import com.adesh.devjarvis.life.PlaceReminders
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
class LifeTools @Inject constructor(
    private val store: LifeStore,
    private val location: LocationFinder,
    private val placeReminders: PlaceReminders,
) {
    private val noLocation = "location not available — Sir should allow Location for JARVIS (Setup), 'Allow all the time' for place reminders, and keep GPS on"

    private val savePlace = object : SimpleTool(
        "save_place",
        "Save the place where Sir is right now under a name (\"ghar\", \"office\", \"gym\") for place reminders. Use when he says \"ye jagah ghar ke naam se save karo\".",
        params { string("name", "Name for this place", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = LifeMath.key(args.requireString("name"))
            if (name.isEmpty()) return ToolResult.fail("the place needs a name")
            val fix = location.current() ?: return ToolResult.fail(noLocation)
            store.update { d -> d.copy(places = d.places.filterNot { LifeMath.key(it.name) == name } + Place(name, fix.latitude, fix.longitude)) }
            return ToolResult.ok("saved this spot as '$name' (accuracy about ${fix.accuracy.toInt()} m)")
        }
    }

    private val remindAtPlace = object : SimpleTool(
        "remind_at_place",
        "Remind Sir of something when he next ARRIVES at a saved place (\"ghar pahunchun to yaad dilana ...\"). The place must have been saved with save_place.",
        params {
            string("place", "Saved place name, e.g. ghar", required = true)
            string("text", "What to remind him of", required = true)
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val data = store.get()
            val place = PlaceLogic.find(data.places, args.requireString("place"))
                ?: return ToolResult.fail(
                    "no saved place called '${args.requireString("place")}' (saved: ${data.places.joinToString { it.name }.ifEmpty { "none" }}). " +
                        "When Sir is there he can say \"ye jagah ... ke naam se save karo\".",
                )
            if (!location.hasPermission()) return ToolResult.fail(noLocation)
            val fix = location.current(4_000L)
            val alreadyThere = fix != null && PlaceLogic.isInside(place, fix.latitude, fix.longitude)
            store.update { it.copy(placeReminders = it.placeReminders + PlaceReminder(System.currentTimeMillis(), place.name, args.requireString("text"), armed = !alreadyThere)) }
            placeReminders.schedule()
            return ToolResult.ok(
                "will remind at '${place.name}'" + (if (alreadyThere) " the next time Sir comes back there (he is there now)" else "") +
                    ". Checked about every 15 minutes, so it can be a few minutes late; needs Location 'Allow all the time'.",
            )
        }
    }

    private val logHabit = object : SimpleTool(
        "log_habit", "Record that Sir did a habit just now (water, gym, study, meditation, medicine...).",
        params { string("habit", "Habit name, one or two words", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val name = LifeMath.key(args.requireString("habit"))
            if (name.isEmpty()) return ToolResult.fail("which habit?")
            val now = System.currentTimeMillis()
            val d = store.update { it.copy(habits = it.habits + (name to (it.habits[name].orEmpty() + now).takeLast(400))) }
            val today = d.habits[name].orEmpty().count { it >= LifeMath.periodStart("today", now) }
            val week = d.habits[name].orEmpty().count { it >= LifeMath.periodStart("week", now) }
            return ToolResult.ok("$name logged: $today today, $week in the last 7 days")
        }
    }

    private val habitSummary = object : SimpleTool(
        "habit_summary", "How often Sir did his habits today and in the last 7 days.",
        params { string("habit", "One habit, or leave out for all") },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val now = System.currentTimeMillis()
            val only = args.string("habit")?.let { LifeMath.key(it) }
            val rows = store.get().habits.filterKeys { only == null || it == only }.map { (k, v) ->
                "$k: ${v.count { it >= LifeMath.periodStart("today", now) }} today, ${v.count { it >= LifeMath.periodStart("week", now) }} this week"
            }
            return ToolResult.ok(if (rows.isEmpty()) "no habits logged yet" else rows.joinToString("; "))
        }
    }

    private val listPlaceReminders = object : SimpleTool("list_place_reminders", "List saved places and the reminders waiting for them.") {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val d = store.get()
            return ToolResult.ok(
                "places: ${d.places.joinToString { it.name }.ifEmpty { "none" }}; reminders: " +
                    d.placeReminders.joinToString(" | ") { "${it.place}: ${it.text}" }.ifEmpty { "none" },
            )
        }
    }

    private val deletePlaceReminder = object : SimpleTool(
        "delete_place_reminder", "Delete a place reminder (matched by its text or place).",
        params { string("text", "Words from the reminder, or the place name", required = true) },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val q = LifeMath.key(args.requireString("text"))
            val all = store.get().placeReminders
            val hit = all.firstOrNull { q in LifeMath.key(it.text) || LifeMath.key(it.place) == q } ?: return ToolResult.fail("no place reminder matching '$q'")
            store.update { it.copy(placeReminders = it.placeReminders - hit) }
            return ToolResult.ok("deleted: ${hit.place}: ${hit.text}")
        }
    }

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
        savePlace, remindAtPlace, listPlaceReminders, deletePlaceReminder, logHabit, habitSummary,
    )
}

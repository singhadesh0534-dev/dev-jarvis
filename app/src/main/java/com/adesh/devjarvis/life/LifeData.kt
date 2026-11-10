package com.adesh.devjarvis.life

import com.adesh.devjarvis.screen.MacroStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

data class Note(val time: Long, val text: String)
data class Expense(val time: Long, val amount: Double, val category: String, val note: String)
/** A saved place ("ghar", "office") for place reminders. */
data class Place(val name: String, val lat: Double, val lon: Double)
/** [armed] = Sir has been away from the place since he asked, so arriving there now should fire it. */
data class PlaceReminder(val id: Long, val place: String, val text: String, val armed: Boolean = true)

/** Sir ki roz-marra ki cheezein: routines/shortcuts, notes, lists, kharcha, jagahein. Pure data, unit-tested. */
data class LifeData(
    val routines: Map<String, String> = emptyMap(),
    val notes: List<Note> = emptyList(),
    val lists: Map<String, List<String>> = emptyMap(),
    val expenses: List<Expense> = emptyList(),
    val places: List<Place> = emptyList(),
    val placeReminders: List<PlaceReminder> = emptyList(),
    /** Habit name -> times it was done (epoch millis). */
    val habits: Map<String, List<Long>> = emptyMap(),
    /** Tasks Sir taught by doing them once: name -> recorded steps. */
    val macros: Map<String, List<MacroStep>> = emptyMap(),
    /** "Jab X ho tab Y karo". */
    val triggers: List<Trigger> = emptyList(),
    /** ISO date -> what happened that day. */
    val diary: Map<String, String> = emptyMap(),
)

object LifeCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(d: LifeData): String = buildJsonObject {
        put("routines", buildJsonObject { d.routines.forEach { (k, v) -> put(k, v) } })
        put("notes", buildJsonArray { d.notes.forEach { add(buildJsonObject { put("t", it.time); put("text", it.text) }) } })
        put("lists", buildJsonObject { d.lists.forEach { (k, v) -> put(k, JsonArray(v.map { JsonPrimitive(it) })) } })
        put("expenses", buildJsonArray {
            d.expenses.forEach { add(buildJsonObject { put("t", it.time); put("amount", it.amount); put("cat", it.category); put("note", it.note) }) }
        })
        put("triggers", buildJsonArray {
            d.triggers.forEach { add(buildJsonObject { put("id", it.id); put("event", it.event); put("param", it.param); put("action", it.action); put("fired", it.lastFired) }) }
        })
        put("diary", buildJsonObject { d.diary.forEach { (k, v) -> put(k, v) } })
        put("macros", buildJsonObject {
            d.macros.forEach { (name, steps) ->
                put(name, buildJsonArray {
                    steps.forEach { add(buildJsonObject { put("k", it.kind); put("a", it.app); put("l", it.label); put("v", it.viewId); put("t", it.text) }) }
                })
            }
        })
        put("habits", buildJsonObject { d.habits.forEach { (k, v) -> put(k, JsonArray(v.map { JsonPrimitive(it) })) } })
        put("places", buildJsonArray { d.places.forEach { add(buildJsonObject { put("name", it.name); put("lat", it.lat); put("lon", it.lon) }) } })
        put("placeReminders", buildJsonArray {
            d.placeReminders.forEach { add(buildJsonObject { put("id", it.id); put("place", it.place); put("text", it.text); put("armed", it.armed) }) }
        })
    }.toString()

    fun decode(raw: String?): LifeData {
        val root = runCatching { json.parseToJsonElement(raw.orEmpty()).jsonObject }.getOrNull() ?: return LifeData()
        fun obj(k: String) = root[k] as? JsonObject
        fun arr(k: String) = (root[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull ?: 0.0
        fun JsonObject.l(k: String) = this[k]?.jsonPrimitive?.longOrNull ?: 0L
        return LifeData(
            routines = obj("routines")?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty(),
            notes = arr("notes").map { Note(it.l("t"), it.s("text")) },
            lists = obj("lists")?.mapValues { e -> (e.value as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull } }.orEmpty(),
            expenses = arr("expenses").map { Expense(it.l("t"), it.d("amount"), it.s("cat"), it.s("note")) },
            triggers = arr("triggers").map { Trigger(it.l("id"), it.s("event"), it.s("param"), it.s("action"), it.l("fired")) },
            diary = obj("diary")?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty(),
            macros = obj("macros")?.mapValues { e ->
                (e.value as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { MacroStep(it.s("k"), it.s("a"), it.s("l"), it.s("v"), it.s("t")) }
            }.orEmpty(),
            habits = obj("habits")?.mapValues { e -> (e.value as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.longOrNull } }.orEmpty(),
            places = arr("places").map { Place(it.s("name"), it.d("lat"), it.d("lon")) },
            placeReminders = arr("placeReminders").map { PlaceReminder(it.l("id"), it.s("place"), it.s("text"), it["armed"]?.jsonPrimitive?.contentOrNull != "false") },
        )
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}

object LifeMath {
    /** "good night" == "Good Night!" == "goodnight". */
    fun key(name: String): String = name.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("\\s+"), " ")

    fun periodStart(period: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = when (period) {
            "week" -> today.minusDays(6)
            "month" -> today.withDayOfMonth(1)
            else -> today
        }
        return from.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** "total 1,250 (petrol 800, khana 450) over 3 entries". */
    fun expenseSummary(expenses: List<Expense>, from: Long, to: Long): String {
        val inRange = expenses.filter { it.time in from..to }
        if (inRange.isEmpty()) return "no expenses recorded"
        val byCat = inRange.groupBy { it.category.ifBlank { "other" } }.mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        return "total ${fmt(inRange.sumOf { it.amount })} rupees (" + byCat.joinToString(", ") { "${it.key} ${fmt(it.value)}" } +
            ") over ${inRange.size} entr" + if (inRange.size == 1) "y" else "ies"
    }

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(v)

    /** Metres between two points (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}

/** Jagah-wale reminders: "ghar pahunchun to yaad dilana". Pure, unit-tested. */
object PlaceLogic {
    const val RADIUS_M = 200.0

    data class Result(val fire: List<PlaceReminder>, val remaining: List<PlaceReminder>)

    fun find(places: List<Place>, name: String): Place? = LifeMath.key(name).let { k -> places.firstOrNull { LifeMath.key(it.name) == k } }

    fun isInside(place: Place, lat: Double, lon: Double, radius: Double = RADIUS_M): Boolean =
        LifeMath.distanceMeters(place.lat, place.lon, lat, lon) <= radius

    /**
     * Sir is at ([lat], [lon]) now. A reminder fires when he is inside its place and it is armed; one that was
     * created while he was already there arms once he has clearly left (1.5 x the radius), so it fires on his return.
     */
    fun check(places: List<Place>, reminders: List<PlaceReminder>, lat: Double, lon: Double, radius: Double = RADIUS_M): Result {
        val fire = ArrayList<PlaceReminder>()
        val remaining = ArrayList<PlaceReminder>()
        for (r in reminders) {
            val place = find(places, r.place)
            if (place == null) { remaining += r; continue }
            val distance = LifeMath.distanceMeters(place.lat, place.lon, lat, lon)
            when {
                distance <= radius && r.armed -> fire += r
                distance > radius * 1.5 && !r.armed -> remaining += r.copy(armed = true)
                else -> remaining += r
            }
        }
        return Result(fire, remaining)
    }
}

package com.adesh.devjarvis.life

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
data class PlaceReminder(val id: Long, val place: String, val text: String)

/** Sir ki roz-marra ki cheezein: routines/shortcuts, notes, lists, kharcha, jagahein. Pure data, unit-tested. */
data class LifeData(
    val routines: Map<String, String> = emptyMap(),
    val notes: List<Note> = emptyList(),
    val lists: Map<String, List<String>> = emptyMap(),
    val expenses: List<Expense> = emptyList(),
    val places: List<Place> = emptyList(),
    val placeReminders: List<PlaceReminder> = emptyList(),
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
        put("places", buildJsonArray { d.places.forEach { add(buildJsonObject { put("name", it.name); put("lat", it.lat); put("lon", it.lon) }) } })
        put("placeReminders", buildJsonArray {
            d.placeReminders.forEach { add(buildJsonObject { put("id", it.id); put("place", it.place); put("text", it.text) }) }
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
            places = arr("places").map { Place(it.s("name"), it.d("lat"), it.d("lon")) },
            placeReminders = arr("placeReminders").map { PlaceReminder(it.l("id"), it.s("place"), it.s("text")) },
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

package com.adesh.devjarvis.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Supabase (PostgREST) requests for the backup row. Pure, unit-tested. */
object SupabaseRest {
    const val TABLE = "jarvis_backup"
    const val ROW = "dev-jarvis"

    /** The SQL Sir runs once in Supabase > SQL Editor. */
    val SETUP_SQL = """
        create table if not exists $TABLE (id text primary key, data text not null, updated_at timestamptz default now());
        alter table $TABLE enable row level security;
        create policy "jarvis app" on $TABLE for all using (true) with check (true);
    """.trimIndent()

    fun valid(url: String, key: String) = url.startsWith("https://") && url.contains(".supabase.") && key.length > 20

    fun tableUrl(url: String) = url.trimEnd('/') + "/rest/v1/" + TABLE

    fun upsertBody(snapshot: String): String = buildJsonArray {
        add(buildJsonObject { put("id", ROW); put("data", snapshot); put("updated_at", java.time.Instant.now().toString()) })
    }.toString()

    fun selectUrl(url: String) = tableUrl(url) + "?id=eq.$ROW&select=data,updated_at"

    /** The saved snapshot text from a select response, or null. */
    fun snapshotFrom(body: String): String? = runCatching {
        ((Json.parseToJsonElement(body) as JsonArray).firstOrNull() as? JsonObject)?.get("data")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrNull()
}

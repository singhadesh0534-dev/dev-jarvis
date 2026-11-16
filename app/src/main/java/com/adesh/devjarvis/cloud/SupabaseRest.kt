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
    /** Request header that carries the sync code; the row policy matches it against the row id. */
    const val SYNC_HEADER = "x-jarvis-sync"

    /** The SQL Sir runs once in Supabase > SQL Editor. */
    val SETUP_SQL = """
        create table if not exists $TABLE (id text primary key, data text not null, updated_at timestamptz default now());
        alter table $TABLE enable row level security;
        create policy "jarvis sync code" on $TABLE for all to anon, authenticated
          using (length(id) >= 24 and id = coalesce(current_setting('request.headers', true)::json->>'$SYNC_HEADER', ''))
          with check (length(id) >= 24 and id = coalesce(current_setting('request.headers', true)::json->>'$SYNC_HEADER', ''));
        grant select, insert, update on $TABLE to anon, authenticated;
    """.trimIndent()

    /** A new random code (32 chars): nobody can read the backup without it, even with the public anon key. */
    fun newSyncCode(): String {
        val r = java.security.SecureRandom()
        val abc = "abcdefghjkmnpqrstuvwxyz23456789"
        return "jv-" + (1..32).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }

    fun validCode(code: String) = code.trim().length >= 24

    fun valid(url: String, key: String) = url.startsWith("https://") && url.contains(".supabase.") && key.length > 20

    fun tableUrl(url: String) = url.trimEnd('/') + "/rest/v1/" + TABLE

    fun upsertBody(code: String, snapshot: String): String = buildJsonArray {
        add(buildJsonObject { put("id", code.trim()); put("data", snapshot); put("updated_at", java.time.Instant.now().toString()) })
    }.toString()

    fun selectUrl(url: String, code: String) = tableUrl(url) + "?id=eq." + java.net.URLEncoder.encode(code.trim(), "UTF-8") + "&select=data,updated_at"

    /** The saved snapshot text from a select response, or null. */
    fun snapshotFrom(body: String): String? = runCatching {
        ((Json.parseToJsonElement(body) as JsonArray).firstOrNull() as? JsonObject)?.get("data")?.let { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrNull()
}

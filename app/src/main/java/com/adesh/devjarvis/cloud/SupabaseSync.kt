package com.adesh.devjarvis.cloud

import com.adesh.devjarvis.core.BackupManager
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sir's memories, notes, routines, skills and recent conversation kept in his own Supabase project as well, so a lost or
 * new phone gets everything back. API keys and tokens never leave the phone.
 */
@Singleton
class SupabaseSync @Inject constructor(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val backup: BackupManager,
) {
    @Volatile private var lastPush = 0L
    private val json = "application/json".toMediaType()

    val configured: Boolean get() = settings.current.let { SupabaseRest.valid(it.supabaseUrl, it.supabaseKey) && SupabaseRest.validCode(it.supabaseSync) }

    private fun Request.Builder.auth(key: String) = header("apikey", key).header("Authorization", "Bearer $key")
        .header(SupabaseRest.SYNC_HEADER, settings.current.supabaseSync.trim())

    /** Upload now. Returns a line for Sir. */
    suspend fun push(): String = withContext(Dispatchers.IO) {
        val s = settings.current
        if (!configured) return@withContext "Supabase URL aur key Settings mein daaliye."
        val req = Request.Builder().url(SupabaseRest.tableUrl(s.supabaseUrl)).auth(s.supabaseKey)
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(SupabaseRest.upsertBody(s.supabaseSync, backup.snapshot()).toRequestBody(json)).build()
        http.newCall(req).execute().use { r ->
            lastPush = System.currentTimeMillis()
            SessionTrace.add("SUPABASE push HTTP ${r.code}")
            when {
                r.isSuccessful -> "Supabase pe sab save ho gaya."
                r.code == 404 || (r.body?.string().orEmpty().contains("does not exist")) -> "Supabase mein table nahi mili — setup SQL ek baar chala dijiye."
                r.code == 401 || r.code == 403 -> "Supabase key galat hai ya naya Setup SQL nahi chala (HTTP ${r.code})."
                else -> "Supabase pe save nahi hua (HTTP ${r.code})."
            }
        }
    }

    /** Bring everything back from Supabase. */
    suspend fun pull(): String = withContext(Dispatchers.IO) {
        val s = settings.current
        if (!configured) return@withContext "Supabase URL aur key Settings mein daaliye."
        val req = Request.Builder().url(SupabaseRest.selectUrl(s.supabaseUrl, s.supabaseSync)).auth(s.supabaseKey).get().build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext "Supabase se nahi aaya (HTTP ${r.code})."
            r.body?.string().orEmpty()
        }
        val snap = SupabaseRest.snapshotFrom(body) ?: return@withContext "Supabase mein abhi koi backup nahi hai."
        "Supabase se: " + backup.restore(snap)
    }

    /** After a conversation: quietly upload, at most every 10 minutes. */
    suspend fun autoPush() {
        if (!configured || System.currentTimeMillis() - lastPush < 10 * 60_000L) return
        runCatching { push() }.onFailure { SessionTrace.add("SUPABASE push failed ${it.javaClass.simpleName}") }
    }
}

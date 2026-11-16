package com.adesh.devjarvis.memory

import android.content.Context
import com.adesh.devjarvis.core.KeyRing
import com.adesh.devjarvis.core.SessionTrace
import com.adesh.devjarvis.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The meaning index of Sir's memories: one embedding per fact, kept in a small file on the phone (never synced or
 * backed up — it is rebuilt from the facts). Missing or changed facts are embedded on the next search. Any failure
 * (no key, no net, limit) just means plain keyword search for that turn.
 */
@Singleton
class SemanticIndex @Inject constructor(
    @ApplicationContext context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val keys: KeyRing,
) {
    private val file = File(context.filesDir, "memory-vectors.txt")
    private val vectors = ConcurrentHashMap<Long, Embeddings.Entry>()
    private val lock = Mutex()
    @Volatile private var loaded = false
    @Volatile private var pausedUntil = 0L
    private val json = "application/json".toMediaType()

    /** Meaning similarity of each fact to [query] (facts without a vector yet are embedded first, up to 100). */
    suspend fun similarities(query: String, facts: List<Fact>): Map<Long, Double> {
        if (query.isBlank() || facts.isEmpty() || !settings.current.hasGeminiKey || System.currentTimeMillis() < pausedUntil) return emptyMap()
        lock.withLock {
            load()
            val missing = facts.filter { vectors[it.id]?.textKey != Embeddings.textKey(it.text) }.take(100)
            missing.chunked(50).forEach { batch ->
                val vs = embed(batch.map { it.text }, query = false) ?: return@withLock
                batch.zip(vs).forEach { (f, v) -> vectors[f.id] = Embeddings.Entry(f.id, Embeddings.textKey(f.text), v) }
                save(facts.map { it.id }.toSet())
            }
        }
        val q = embed(listOf(query), query = true)?.firstOrNull() ?: return emptyMap()
        return facts.mapNotNull { f ->
            vectors[f.id]?.takeIf { it.textKey == Embeddings.textKey(f.text) }?.let { f.id to Embeddings.cosine(q, it.vector) }
        }.toMap()
    }

    private suspend fun embed(texts: List<String>, query: Boolean): List<FloatArray>? = withContext(Dispatchers.IO) {
        val s = settings.current
        val key = keys.gemini.current(s.geminiApiKey).ifBlank { return@withContext null }
        val req = Request.Builder().url(Embeddings.batchUrl()).header("x-goog-api-key", key)
            .post(Embeddings.batchBody(texts, query).toRequestBody(json)).build()
        runCatching {
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> Embeddings.parseBatch(body)?.takeIf { it.size == texts.size }
                    r.code == 404 || r.code == 400 -> { pause(24 * 3_600_000L, "model unavailable ${r.code}"); null }
                    r.code == 429 -> { pause(2 * 60_000L, "limit"); null }
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun pause(ms: Long, why: String) {
        pausedUntil = System.currentTimeMillis() + ms
        SessionTrace.add("MEMORY meaning search paused: $why")
    }

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching { if (file.exists()) file.readLines().mapNotNull(Embeddings::parseLine).forEach { vectors[it.id] = it } }
    }

    /** Writes the index, dropping vectors of facts that no longer exist. */
    private fun save(liveIds: Set<Long>) {
        vectors.keys.filter { it !in liveIds }.forEach { vectors.remove(it) }
        runCatching { file.writeText(vectors.values.joinToString("\n") { Embeddings.line(it.id, it.textKey, it.vector) }) }
    }
}

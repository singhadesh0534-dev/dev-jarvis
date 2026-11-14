package com.adesh.devjarvis.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Talks to Sir's MCP connectors over HTTP (Streamable HTTP transport). */
@Singleton
class McpClient @Inject constructor(base: OkHttpClient) {
    class McpError(message: String) : Exception(message)

    private val http = base.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
    private val ids = AtomicInteger(1)
    private val sessions = ConcurrentHashMap<String, String>()
    private val JSON = "application/json".toMediaType()

    private fun post(c: UserConnector, body: String, session: String?): Pair<String, String?> {
        val req = Request.Builder().url(c.url)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", McpProtocol.VERSION)
            .apply { if (c.token.isNotBlank()) header("Authorization", "Bearer ${c.token}") }
            .apply { if (session != null) header("Mcp-Session-Id", session) }
            .post(body.toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw McpError("token galat ya expire (HTTP ${r.code})")
            if (r.code == 404 && session != null) throw McpError("session expired")
            if (!r.isSuccessful && r.code != 202) throw McpError("HTTP ${r.code}")
            return (r.body?.string().orEmpty()) to r.header("Mcp-Session-Id")
        }
    }

    private fun session(c: UserConnector): String? {
        sessions[c.url]?.let { return it }
        val id = ids.getAndIncrement()
        val (body, sid) = post(c, McpProtocol.initialize(id), null)
        val resp = McpProtocol.response(body, id) ?: throw McpError("ye MCP server nahi lagta (initialize ka jawab nahi)")
        McpProtocol.error(resp)?.let { throw McpError(it) }
        runCatching { post(c, McpProtocol.notification("notifications/initialized"), sid) }
        val key = sid ?: ""
        sessions[c.url] = key
        return sid
    }

    private suspend fun rpc(c: UserConnector, build: (Int) -> String): JsonObject = withContext(Dispatchers.IO) {
        fun once(): JsonObject {
            val sid = session(c)?.ifBlank { null }
            val id = ids.getAndIncrement()
            val (body, _) = post(c, build(id), sid)
            return McpProtocol.response(body, id) ?: throw McpError("server ka jawab samajh nahi aaya")
        }
        try { once() } catch (e: McpError) {
            if (e.message != "session expired") throw e
            sessions.remove(c.url); once()
        }
    }

    suspend fun listTools(c: UserConnector): List<McpProtocol.Tool> {
        val resp = rpc(c) { McpProtocol.request(it, "tools/list") }
        McpProtocol.error(resp)?.let { throw McpError(it) }
        return McpProtocol.tools(resp)
    }

    suspend fun call(c: UserConnector, tool: String, args: JsonObject): String =
        McpProtocol.resultText(rpc(c) { McpProtocol.callTool(it, tool, args) })
}

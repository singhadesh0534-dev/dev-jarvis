package com.adesh.devjarvis.connectors

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** A service Sir connected: an MCP server URL and, if it needs one, its token. */
data class UserConnector(val name: String, val url: String, val token: String = "", val tools: Int = 0)

/** Sir's connectors, in encrypted storage (tokens are secrets: never logged, never in backups). */
@Singleton
class ConnectorStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context, FILE, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("$FILE-plain", Context.MODE_PRIVATE) }

    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<UserConnector>> = _all.asStateFlow()

    fun find(name: String): UserConnector? {
        val n = name.trim().lowercase()
        return _all.value.firstOrNull { it.name.lowercase() == n } ?: _all.value.firstOrNull { n in it.name.lowercase() || it.name.lowercase() in n }
    }

    fun save(c: UserConnector) = write(_all.value.filter { !it.name.equals(c.name, true) } + c)
    fun remove(name: String) = write(_all.value.filter { !it.name.equals(name, true) })

    private fun write(list: List<UserConnector>) {
        _all.value = list
        prefs.edit().putString(KEY, buildJsonArray {
            list.forEach { c -> add(buildJsonObject { put("name", c.name); put("url", c.url); put("token", c.token); put("tools", c.tools) }) }
        }.toString()).apply()
    }

    private fun load(): List<UserConnector> = runCatching {
        (Json.parseToJsonElement(prefs.getString(KEY, "[]").orEmpty()) as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            UserConnector(s("name"), s("url"), s("token"), s("tools").toIntOrNull() ?: 0).takeIf { it.name.isNotBlank() && it.url.startsWith("https://") }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE = "jarvis_connectors"
        const val KEY = "all"
    }
}

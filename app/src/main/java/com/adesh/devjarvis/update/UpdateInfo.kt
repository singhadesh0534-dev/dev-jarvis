package com.adesh.devjarvis.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** GitHub "jarvis-latest" release ka matlab: kaunsa versionCode, aur har edition ka APK kahan. Pure, unit-tested. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val assets: Map<String, Asset>) {
    data class Asset(val url: String, val sizeBytes: Long)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val codeRegex = Regex("""versionCode=(\d+)""")
        private val nameRegex = Regex("""versionName=([\w.\-]+)""")

        fun parse(releaseJson: String): UpdateInfo? {
            val root = runCatching { json.parseToJsonElement(releaseJson).jsonObject }.getOrNull() ?: return null
            val body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val code = codeRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val name = nameRegex.find(body)?.groupValues?.get(1).orEmpty()
            val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = o["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                n to Asset(url, o["size"]?.jsonPrimitive?.longOrNull ?: 0L)
            }.toMap()
            return UpdateInfo(code, name, assets)
        }

        private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
    }
}

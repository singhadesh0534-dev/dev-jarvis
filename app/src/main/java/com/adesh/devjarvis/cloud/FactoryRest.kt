package com.adesh.devjarvis.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * GitHub REST calls for the App Factory (.github/workflows/app-factory.yml): put an app's HTML on the jarvis-apps
 * branch, follow its build, and find the finished APK. Pure, unit-tested.
 */
object FactoryRest {
    const val BRANCH = "jarvis-apps"
    const val WORKFLOW_NAME = "JARVIS App Factory"
    private const val API = "https://api.github.com/repos/"

    /** Same rule as the workflow: lowercase letters, digits and dashes. */
    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "app" }

    fun refUrl(repo: String, branch: String) = "$API$repo/git/ref/heads/$branch"
    fun createRefUrl(repo: String) = "$API$repo/git/refs"
    fun createRefBody(branch: String, sha: String) = buildJsonObject { put("ref", "refs/heads/$branch"); put("sha", sha) }.toString()
    fun contentUrl(repo: String, slug: String) = "$API$repo/contents/factory-apps/$slug/index.html"
    fun contentGetUrl(repo: String, slug: String) = contentUrl(repo, slug) + "?ref=$BRANCH"
    fun commitMessage(slug: String, name: String) = "build: $slug|${name.replace('\n', ' ').trim().take(40)}"

    fun putBody(slug: String, name: String, html: String, existingSha: String?) = buildJsonObject {
        put("message", commitMessage(slug, name))
        put("content", Base64.getEncoder().encodeToString(html.toByteArray()))
        put("branch", BRANCH)
        if (!existingSha.isNullOrBlank()) put("sha", existingSha)
    }.toString()

    fun runsUrl(repo: String, commitSha: String) = "$API$repo/actions/runs?head_sha=$commitSha&per_page=10"
    fun releaseUrl(repo: String, slug: String) = "$API$repo/releases/tags/app-$slug"
    fun runPage(repo: String, runId: Long) = "https://github.com/$repo/actions/runs/$runId"

    private fun obj(body: String) = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    /** sha of a branch ref ({"object":{"sha":…}}). */
    fun refSha(body: String): String? = (obj(body)?.get("object") as? JsonObject)?.str("sha")

    /** sha of an existing file (needed to replace it). */
    fun fileSha(body: String): String? = obj(body)?.str("sha")

    /** The commit made by a contents PUT. */
    fun commitSha(body: String): String? = (obj(body)?.get("commit") as? JsonObject)?.str("sha")

    data class Run(val id: Long, val status: String, val conclusion: String?)

    /** The factory run for that commit, if GitHub has started it. */
    fun factoryRun(body: String): Run? {
        val runs = obj(body)?.get("workflow_runs") as? JsonArray ?: return null
        return runs.mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("name") == WORKFLOW_NAME }
            ?.let { Run(it.str("id")?.toLongOrNull() ?: 0, it.str("status").orEmpty(), it.str("conclusion")) }
    }

    /** API url of the release asset app.apk (download it with Accept: application/octet-stream). */
    fun apkAssetUrl(body: String): String? {
        val assets = obj(body)?.get("assets") as? JsonArray ?: return null
        return assets.mapNotNull { it as? JsonObject }.firstOrNull { it.str("name") == "app.apk" }?.str("url")
    }

    /** GitHub's page for a new token, filled in with the name, repo and the two permissions JARVIS needs. */
    fun tokenPage(owner: String) = "https://github.com/settings/personal-access-tokens/new" +
        "?name=JARVIS%20App%20Factory&description=JARVIS%20builds%20APKs&target_name=$owner&expires_in=none" +
        "&contents=write&actions=read"

    /** Short Hinglish for GitHub's answers. */
    fun errorText(code: Int): String = when (code) {
        401 -> "GitHub token galat hai ya expire ho gaya — Settings mein naya token daaliye."
        403 -> "GitHub token ko permission nahi hai (Contents: Read & write aur Actions: Read chahiye)."
        404 -> "GitHub pe repo ya branch nahi mila — token mein dev-jarvis repo chuna hai?"
        422 -> "GitHub ne file nahi li (shayad app bahut badi hai)."
        else -> "GitHub se dikkat (HTTP $code)."
    }
}

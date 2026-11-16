package com.adesh.devjarvis.cloud

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * JARVIS accounts (Supabase Auth): Google login in the browser (PKCE), email signup / login, token refresh, and the
 * per-user backup row. Pure, unit-tested. Each user's backup is guarded by row level security on auth.uid().
 */
object AuthRest {
    const val REDIRECT = "devjarvis://login-callback"
    const val TABLE = "jarvis_user_backup"

    data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val email: String)

    /** The SQL for a JARVIS accounts project (already applied to Sir's project; kept here for a new one). */
    val SETUP_SQL = """
        create table if not exists public.$TABLE (
          user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
          data text not null check (length(data) < 3000000),
          updated_at timestamptz not null default now());
        alter table public.$TABLE enable row level security;
        create policy "own backup" on public.$TABLE for all to authenticated
          using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
        grant select, insert, update, delete on public.$TABLE to authenticated;
    """.trimIndent()

    fun configured(url: String, anonKey: String) = url.startsWith("https://") && anonKey.length > 20

    // ---- PKCE (Google login in the browser)
    fun newVerifier(): String {
        val b = ByteArray(48).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    fun challenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    fun googleUrl(base: String, verifier: String): String = base.trimEnd('/') + "/auth/v1/authorize?provider=google" +
        "&redirect_to=" + enc(REDIRECT) + "&code_challenge=" + challenge(verifier) + "&code_challenge_method=s256"

    /** "devjarvis://login-callback?code=…" -> the code (or the error text Supabase sent back). */
    fun callbackCode(uri: String): Result<String> {
        if (!uri.startsWith(REDIRECT)) return Result.failure(IllegalArgumentException("not our link"))
        val q = uri.substringAfter('?', "").substringBefore('#') + "&" + uri.substringAfter('#', "")
        val params = q.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") } }.toMap()
        params["code"]?.takeIf { it.isNotBlank() }?.let { return Result.success(it) }
        return Result.failure(IllegalStateException(params["error_description"] ?: params["error"] ?: "login cancelled"))
    }

    fun pkceTokenUrl(base: String) = base.trimEnd('/') + "/auth/v1/token?grant_type=pkce"
    fun pkceBody(code: String, verifier: String) = buildJsonObject { put("auth_code", code); put("code_verifier", verifier) }.toString()

    /** Public auth settings: which login providers the server has switched on. */
    fun settingsUrl(base: String) = base.trimEnd('/') + "/auth/v1/settings"

    /** true / false from the settings response, null if it can't be read. */
    fun googleEnabled(body: String): Boolean? = runCatching {
        val ext = (Json.parseToJsonElement(body) as JsonObject)["external"] as JsonObject
        (ext["google"] as JsonPrimitive).content.toBooleanStrict()
    }.getOrNull()

    // ---- email + password
    fun signupUrl(base: String) = base.trimEnd('/') + "/auth/v1/signup"
    fun passwordUrl(base: String) = base.trimEnd('/') + "/auth/v1/token?grant_type=password"
    fun refreshUrl(base: String) = base.trimEnd('/') + "/auth/v1/token?grant_type=refresh_token"
    fun logoutUrl(base: String) = base.trimEnd('/') + "/auth/v1/logout"
    fun recoverUrl(base: String) = base.trimEnd('/') + "/auth/v1/recover"
    fun credentialsBody(email: String, password: String) = buildJsonObject { put("email", email.trim()); put("password", password) }.toString()
    fun refreshBody(refresh: String) = buildJsonObject { put("refresh_token", refresh) }.toString()
    fun emailBody(email: String) = buildJsonObject { put("email", email.trim()) }.toString()

    // ---- one-time code (OTP) on email: signup and login
    fun otpUrl(base: String) = base.trimEnd('/') + "/auth/v1/otp"
    fun otpBody(email: String, createUser: Boolean) = buildJsonObject { put("email", email.trim()); put("create_user", createUser) }.toString()
    fun verifyUrl(base: String) = base.trimEnd('/') + "/auth/v1/verify"
    fun verifyBody(email: String, code: String) = buildJsonObject { put("type", "email"); put("email", email.trim()); put("token", code.trim()) }.toString()
    fun userUrl(base: String) = base.trimEnd('/') + "/auth/v1/user"
    fun passwordBody(password: String) = buildJsonObject { put("password", password) }.toString()

    /** A code as typed: digits only, 6 to 10 long. */
    fun cleanCode(raw: String) = raw.filter { it.isDigit() }.take(10)
    fun validCode(code: String) = code.length in 6..10

    fun validEmail(e: String) = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(e.trim())

    /** Login / refresh response -> session. null when there is no session (e.g. signup that must confirm the email first). */
    fun session(body: String, now: Long = System.currentTimeMillis()): Session? = runCatching {
        val o = Json.parseToJsonElement(body) as JsonObject
        fun s(k: String, src: JsonObject = o) = (src[k] as? JsonPrimitive)?.contentOrNull
        val access = s("access_token") ?: return null
        val user = o["user"] as? JsonObject
        val expiresIn = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3600
        Session(access, s("refresh_token").orEmpty(), now + expiresIn * 1000, user?.let { s("id", it) }.orEmpty(), user?.let { s("email", it) }.orEmpty())
    }.getOrNull()

    /** Supabase's error text, made short for Sir. */
    fun errorText(code: Int, body: String): String {
        val raw = runCatching {
            val o = Json.parseToJsonElement(body) as JsonObject
            listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull }
        }.getOrNull().orEmpty()
        val r = raw.lowercase()
        return when {
            "invalid login" in r || "invalid_grant" in r -> "Email ya password galat hai."
            "signups not allowed" in r || "user not found" in r -> "Is email se account nahi hai — SIGNUP kijiye."
            "expired" in r || ("invalid" in r && "token" in r) || "otp" in r && "invalid" in r -> "Code galat hai ya purana ho gaya — naya code mangaiye."
            "not authorized" in r && "email" in r -> "Is email pe abhi code nahi ja sakta — server ka email setup baaki hai."
            "rate limit" in r && "email" in r -> "Abhi bahut code bhej chuke — thodi der baad phir mangaiye."
            "security purposes" in r -> "Thoda ruk kar code dobara mangaiye (kuch second)."
            "already registered" in r || "already exists" in r -> "Is email se account pehle se hai — Login dabaiye."
            "not confirmed" in r -> "Email confirm nahi hua — inbox mein Supabase ka link dabaiye, phir login."
            "password" in r && ("6" in r || "short" in r || "weak" in r) -> "Password kam se kam 6 akshar ka rakhiye."
            "provider is not enabled" in r || "unsupported provider" in r -> "Google login abhi server pe chalu nahi hua."
            "rate limit" in r || code == 429 -> "Bahut baar try hua — thodi der baad."
            raw.isNotBlank() -> raw.take(120)
            else -> "Login nahi hua (HTTP $code)."
        }
    }

    // ---- the user's own backup row
    fun backupUrl(base: String) = base.trimEnd('/') + "/rest/v1/" + TABLE
    fun backupSelectUrl(base: String) = backupUrl(base) + "?select=data,updated_at&limit=1"
    fun backupBody(userId: String, snapshot: String) = buildJsonArray {
        add(buildJsonObject { put("user_id", userId); put("data", snapshot); put("updated_at", java.time.Instant.now().toString()) })
    }.toString()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

package com.adesh.devjarvis.cloud

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.adesh.devjarvis.BuildConfig
import com.adesh.devjarvis.core.BackupManager
import com.adesh.devjarvis.core.SessionTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A JARVIS account: log in with Google or email, and everything (memories, notes, routines, skills, recent talk) is
 * kept in the cloud under that account and comes back on any phone. Each user sees only their own row (RLS).
 * Tokens live in encrypted storage and are never logged. API keys never leave the phone.
 */
@Singleton
class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val backup: BackupManager,
) {
    data class State(
        val email: String = "", val loggedIn: Boolean = false, val busy: Boolean = false, val message: String = "",
        /** A one-time code was emailed here and is waiting to be typed. */
        val codeSentTo: String = "",
        val codeSentAt: Long = 0,
    )

    private val base = BuildConfig.ACCOUNT_URL
    private val anon = BuildConfig.ACCOUNT_ANON_KEY
    val available: Boolean get() = AuthRest.configured(base, anon)

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            context, FILE, MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { context.getSharedPreferences("$FILE-plain", Context.MODE_PRIVATE) }

    private val json = "application/json".toMediaType()
    private val lock = Mutex()
    @Volatile private var lastPush = 0L

    private val _state = MutableStateFlow(State(email = prefs.getString(K_EMAIL, "").orEmpty(), loggedIn = prefs.getString(K_REFRESH, "").orEmpty().isNotEmpty()))
    val state: StateFlow<State> = _state.asStateFlow()

    /** Google login is switched on at the server (the button is shown only then). */
    private val _google = MutableStateFlow(prefs.getBoolean(K_GOOGLE, false))
    val googleOn: StateFlow<Boolean> = _google.asStateFlow()

    suspend fun checkProviders() {
        if (!available) return
        val on = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(req(AuthRest.settingsUrl(base)).get().build()).execute().use { r ->
                    if (r.isSuccessful) AuthRest.googleEnabled(r.body?.string().orEmpty()) else null
                }
            }.getOrNull()
        } ?: return
        _google.value = on
        prefs.edit().putBoolean(K_GOOGLE, on).apply()
    }

    /** The first-run screen was answered (logged in or "baad mein"). */
    var asked: Boolean
        get() = prefs.getBoolean(K_ASKED, false)
        set(v) { prefs.edit().putBoolean(K_ASKED, v).apply() }

    // ---------------- Google (browser, PKCE)
    fun startGoogle() {
        if (!available) { note("Account server abhi set nahi hai."); return }
        val verifier = AuthRest.newVerifier()
        prefs.edit().putString(K_VERIFIER, verifier).apply()
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(AuthRest.googleUrl(base, verifier))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure { note("Browser nahi khula.") }
        _state.value = _state.value.copy(busy = true, message = "Google login browser mein khul raha hai…")
    }

    /** The browser came back to devjarvis://login-callback. */
    suspend fun finishGoogle(uri: String) {
        val code = AuthRest.callbackCode(uri).getOrElse { note("Login nahi hua: ${it.message}"); return }
        val verifier = prefs.getString(K_VERIFIER, "").orEmpty().ifEmpty { note("Login phir se shuru kijiye."); return }
        prefs.edit().remove(K_VERIFIER).apply()
        tokenCall(AuthRest.pkceTokenUrl(base), AuthRest.pkceBody(code, verifier))
    }

    // ---------------- email
    suspend fun signUp(email: String, password: String) {
        if (!checkInput(email, password)) return
        busy("Account bana raha hoon…")
        val (code, body) = post(AuthRest.signupUrl(base), AuthRest.credentialsBody(email, password)) ?: return
        if (code !in 200..299) return note(AuthRest.errorText(code, body))
        val s = AuthRest.session(body)
        if (s != null) return loggedIn(s)
        // No session yet: try logging straight in (works when the server doesn't ask for an email confirmation).
        val (c2, b2) = post(AuthRest.passwordUrl(base), AuthRest.credentialsBody(email, password), quiet = true) ?: (0 to "")
        val s2 = if (c2 in 200..299) AuthRest.session(b2) else null
        if (s2 != null) loggedIn(s2) else note("Account ban gaya. $email pe aaya confirm link dabaiye, phir yahan Login karein.")
    }

    // ---------------- one-time code (OTP) on email
    @Volatile private var pendingPassword: String? = null

    /**
     * Signup (with the password Sir chose) or login: email a one-time code. Signup creates the account only after the
     * code is typed, so every account has a real, owned email.
     */
    suspend fun sendCode(email: String, password: String?, signup: Boolean) {
        if (!available) return note("Account server abhi set nahi hai.")
        if (!AuthRest.validEmail(email)) return note("Sahi email daaliye.")
        if (signup && (password == null || password.length < 6)) return note("Password kam se kam 6 akshar ka rakhiye.")
        busy("Code bhej raha hoon…")
        val (code, body) = post(AuthRest.otpUrl(base), AuthRest.otpBody(email, createUser = signup)) ?: return
        if (code !in 200..299) return note(AuthRest.errorText(code, body))
        pendingPassword = if (signup) password else null
        _state.value = _state.value.copy(busy = false, codeSentTo = email.trim(), codeSentAt = System.currentTimeMillis(),
            message = "${email.trim()} pe code bheja hai. Inbox (ya Spam) dekhiye.")
    }

    suspend fun verifyCode(raw: String) {
        val email = _state.value.codeSentTo.ifEmpty { return note("Pehle code mangaiye.") }
        val code = AuthRest.cleanCode(raw)
        if (!AuthRest.validCode(code)) return note("Email mein aaya poora code daaliye.")
        busy("Code check ho raha hai…")
        val (status, body) = post(AuthRest.verifyUrl(base), AuthRest.verifyBody(email, code)) ?: return
        val s = if (status in 200..299) AuthRest.session(body) else null
        if (s == null) return note(AuthRest.errorText(status, body))
        // Signup: keep the password Sir chose, so he can also log in with it.
        pendingPassword?.let { pw ->
            withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(req(AuthRest.userUrl(base), s.accessToken).put(AuthRest.passwordBody(pw).toRequestBody(json)).build()).execute().close()
                }
            }
        }
        pendingPassword = null
        _state.value = _state.value.copy(codeSentTo = "")
        loggedIn(s)
    }

    /** "Email badlo": back to the form. */
    fun cancelCode() {
        pendingPassword = null
        _state.value = _state.value.copy(codeSentTo = "", busy = false, message = "")
    }

    suspend fun logIn(email: String, password: String) {
        if (!checkInput(email, password)) return
        busy("Login ho raha hai…")
        tokenCall(AuthRest.passwordUrl(base), AuthRest.credentialsBody(email, password))
    }

    suspend fun resetPassword(email: String) {
        if (!AuthRest.validEmail(email)) return note("Sahi email daaliye.")
        busy("Link bhej raha hoon…")
        val (code, body) = post(AuthRest.recoverUrl(base), AuthRest.emailBody(email)) ?: return
        note(if (code in 200..299) "Password badalne ka link $email pe bhej diya." else AuthRest.errorText(code, body))
    }

    suspend fun logOut() {
        val token = prefs.getString(K_ACCESS, "").orEmpty()
        if (token.isNotEmpty()) withContext(Dispatchers.IO) {
            runCatching { http.newCall(req(AuthRest.logoutUrl(base), token).post("{}".toRequestBody(json)).build()).execute().close() }
        }
        prefs.edit().remove(K_ACCESS).remove(K_REFRESH).remove(K_USER).remove(K_EXPIRES).remove(K_EMAIL).apply()
        _state.value = State(message = "Logout ho gaya. Is phone ka data phone pe hi hai.")
    }

    // ---------------- backup
    /** Upload this phone's memories etc. to the account. Returns a line for Sir. */
    suspend fun push(): String = lock.withLock {
        val token = token() ?: return "Pehle login kijiye."
        val user = prefs.getString(K_USER, "").orEmpty()
        withContext(Dispatchers.IO) {
            val r = runCatching {
                http.newCall(req(AuthRest.backupUrl(base), token).header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(AuthRest.backupBody(user, backup.snapshot()).toRequestBody(json)).build()).execute().use { it.code }
            }.getOrElse { return@withContext "Net nahi hai — baad mein save hoga." }
            lastPush = System.currentTimeMillis()
            SessionTrace.add("ACCOUNT push HTTP $r")
            if (r in 200..299) "Account mein sab save ho gaya." else "Account mein save nahi hua (HTTP $r)."
        }
    }

    /** Bring the account's data back onto this phone (memories are merged, not duplicated). */
    suspend fun pull(): String {
        val token = token() ?: return "Pehle login kijiye."
        return withContext(Dispatchers.IO) {
            val body = runCatching {
                http.newCall(req(AuthRest.backupSelectUrl(base), token).get().build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext "Account se data nahi aaya (HTTP ${r.code})."
                    r.body?.string().orEmpty()
                }
            }.getOrElse { return@withContext "Net nahi hai." }
            val snap = SupabaseRest.snapshotFrom(body) ?: return@withContext "Is account mein abhi koi purana data nahi — naya shuru."
            "Wapas aa gaya: " + backup.restore(snap)
        }
    }

    /** After a conversation: quietly save, at most every 10 minutes. */
    suspend fun autoPush() {
        if (!_state.value.loggedIn || System.currentTimeMillis() - lastPush < 10 * 60_000L) return
        runCatching { push() }
    }

    // ---------------- internals
    private suspend fun tokenCall(url: String, body: String) {
        busy("Login ho raha hai…")
        val (code, text) = post(url, body) ?: return
        val s = if (code in 200..299) AuthRest.session(text) else null
        if (s == null) note(AuthRest.errorText(code, text)) else loggedIn(s)
    }

    private suspend fun loggedIn(s: AuthRest.Session) {
        save(s)
        _state.value = State(email = s.email, loggedIn = true, busy = true, message = "Login ho gaya. Aapka data la raha hoon…")
        asked = true
        val restored = pull()
        val saved = push()
        _state.value = State(email = s.email, loggedIn = true, message = "$restored ${if (saved.startsWith("Account mein sab")) "" else saved}".trim())
    }

    private fun save(s: AuthRest.Session) {
        prefs.edit().putString(K_ACCESS, s.accessToken).putString(K_REFRESH, s.refreshToken)
            .putString(K_USER, s.userId).putString(K_EMAIL, s.email).putLong(K_EXPIRES, s.expiresAt).apply()
    }

    /** A fresh access token, refreshed when it is about to expire; null if logged out. */
    private suspend fun token(): String? {
        val refresh = prefs.getString(K_REFRESH, "").orEmpty().ifEmpty { return null }
        if (System.currentTimeMillis() < prefs.getLong(K_EXPIRES, 0) - 60_000) return prefs.getString(K_ACCESS, null)
        val (code, body) = post(AuthRest.refreshUrl(base), AuthRest.refreshBody(refresh), quiet = true) ?: return null
        val s = if (code in 200..299) AuthRest.session(body) else null
        if (s == null) {
            if (code in 400..499) { // refresh token revoked: log out cleanly
                prefs.edit().remove(K_ACCESS).remove(K_REFRESH).apply()
                _state.value = State(email = _state.value.email, message = "Login khatam ho gaya — dobara login kijiye.")
            }
            return null
        }
        save(s.copy(email = s.email.ifEmpty { _state.value.email }, userId = s.userId.ifEmpty { prefs.getString(K_USER, "").orEmpty() }))
        return s.accessToken
    }

    private fun req(url: String, token: String? = null) = Request.Builder().url(url).header("apikey", anon)
        .header("Authorization", "Bearer ${token ?: anon}")

    private suspend fun post(url: String, body: String, quiet: Boolean = false): Pair<Int, String>? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(req(url).post(body.toRequestBody(json)).build()).execute().use { it.code to it.body?.string().orEmpty() }
        }.getOrElse { if (!quiet) note("Net nahi hai — internet check kijiye."); null }
    }

    private fun checkInput(email: String, password: String): Boolean {
        if (!available) { note("Account server abhi set nahi hai."); return false }
        if (!AuthRest.validEmail(email)) { note("Sahi email daaliye."); return false }
        if (password.length < 6) { note("Password kam se kam 6 akshar ka rakhiye."); return false }
        return true
    }

    private fun busy(msg: String) { _state.value = _state.value.copy(busy = true, message = msg) }
    private fun note(msg: String) { _state.value = _state.value.copy(busy = false, message = msg) }

    private companion object {
        const val FILE = "jarvis_account"
        const val K_ACCESS = "access"
        const val K_REFRESH = "refresh"
        const val K_USER = "user"
        const val K_EMAIL = "email"
        const val K_EXPIRES = "expires"
        const val K_VERIFIER = "pkce"
        const val K_ASKED = "asked"
        const val K_GOOGLE = "google_on"
    }
}

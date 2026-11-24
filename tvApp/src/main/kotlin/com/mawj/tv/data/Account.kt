package com.mawj.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mawj.tv.BuildConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom

/** A QR sign-in waiting to be approved on the phone. */
data class QrLogin(val code: String, val url: String, val nonce: String, val intervalSeconds: Int)

/**
 * The Nuvio account, behind Mawj's own sign-in page: email and password, or a QR code approved on
 * the phone. After signing in, the account's addons replace the local list.
 */
object Account {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val base = BuildConfig.SUPA_URL.trimEnd('/')
    private val anon = BuildConfig.SUPA_KEY

    val configured: Boolean get() = base.isNotBlank() && anon.isNotBlank()

    var email by mutableStateOf<String?>(null)
        private set

    /** The sign-in page shows once, on first start; later it opens from the settings. */
    var introSeen: Boolean
        get() = prefs.getBoolean("intro_seen", false)
        set(v) { prefs.edit().putBoolean("intro_seen", v).apply() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mawj_account", Context.MODE_PRIVATE)
        email = prefs.getString("email", null)
    }

    val signedIn: Boolean get() = email != null && prefs.getString("refresh", null) != null

    // ---- sign in ----

    /** Returns null on success, or a message to show. */
    suspend fun signIn(email: String, password: String): String? {
        if (!configured) return "خدمة الحساب غير مهيأة في هذه النسخة."
        val r = post("/auth/v1/token?grant_type=password", buildJsonObject { put("email", email.trim()); put("password", password) })
            ?: return "تعذّر الاتصال. تحقّق من الشبكة."
        if (!r.status.isSuccess()) return "الإيميل أو كلمة السر غير صحيحة."
        return if (saveTokens(r.bodyAsText())) { pullAddons(); null } else "تعذّر تسجيل الدخول."
    }

    suspend fun startQr(): QrLogin? {
        if (!configured) return null
        val nonce = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val r = post("/rest/v1/rpc/start_tv_login_session", buildJsonObject {
            put("p_device_nonce", nonce)
            put("p_redirect_base_url", "https://nuvio.tv/tv-login")
            put("p_device_name", "موج للشاشة")
        }) ?: return null
        if (!r.status.isSuccess()) return null
        val o = runCatching { (json.parseToJsonElement(r.bodyAsText()) as JsonArray).first() as JsonObject }.getOrNull() ?: return null
        return QrLogin(o.str("code") ?: return null, o.str("web_url") ?: return null, nonce, o.str("poll_interval_seconds")?.toIntOrNull() ?: 3)
    }

    /** "pending", "approved" (then signed in), "expired", or null on a network error. */
    suspend fun pollQr(q: QrLogin): String? {
        val r = post("/rest/v1/rpc/poll_tv_login_session", buildJsonObject { put("p_code", q.code); put("p_device_nonce", q.nonce) }) ?: return null
        if (!r.status.isSuccess()) return null
        val status = runCatching { ((json.parseToJsonElement(r.bodyAsText()) as JsonArray).first() as JsonObject).str("status") }.getOrNull() ?: return null
        if (status != "approved") return if (status in listOf("expired", "used", "cancelled")) "expired" else "pending"
        val x = post("/functions/v1/tv-logins-exchange", buildJsonObject { put("code", q.code); put("device_nonce", q.nonce) }) ?: return null
        if (!x.status.isSuccess() || !saveTokens(x.bodyAsText())) return null
        pullAddons()
        return "approved"
    }

    fun signOut() {
        prefs.edit().remove("access").remove("refresh").remove("email").remove("expires").apply()
        email = null
    }

    // ---- account data ----

    /** The account's addons (main profile), in its order. Replaces the local list when found. */
    suspend fun pullAddons(): Int {
        val token = token() ?: return 0
        val r = withContext(Dispatchers.IO) {
            runCatching {
                Addons.http.get("$base/rest/v1/addons?select=url,sort_order&profile_id=eq.1&order=sort_order.asc") {
                    header("apikey", anon); header("Authorization", "Bearer $token")
                }
            }.getOrNull()
        } ?: return 0
        if (!r.status.isSuccess()) return 0
        val urls = runCatching {
            (json.parseToJsonElement(r.bodyAsText()) as JsonArray).mapNotNull { (it as? JsonObject)?.str("url") }
        }.getOrDefault(emptyList()).mapNotNull(Store::normalizeManifestUrl).distinct()
        if (urls.isNotEmpty()) withContext(Dispatchers.Main) { Store.replaceAddons(urls) }
        return urls.size
    }

    // ---- tokens ----

    private fun saveTokens(body: String): Boolean {
        val o = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull() ?: return false
        val access = o.str("access_token") ?: return false
        val refresh = o.str("refresh_token") ?: return false
        val expiresIn = o.str("expires_in")?.toLongOrNull() ?: 3600
        val mail = ((o["user"] as? JsonObject))?.str("email") ?: email ?: "حساب نوفيو"
        prefs.edit().putString("access", access).putString("refresh", refresh).putString("email", mail)
            .putLong("expires", System.currentTimeMillis() + (expiresIn - 60) * 1000).apply()
        email = mail
        return true
    }

    /** A valid access token, refreshed when it has run out. */
    private suspend fun token(): String? {
        val access = prefs.getString("access", null) ?: return null
        if (System.currentTimeMillis() < prefs.getLong("expires", 0)) return access
        val refresh = prefs.getString("refresh", null) ?: return null
        val r = post("/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refresh) }) ?: return null
        if (!r.status.isSuccess()) return null
        return if (saveTokens(r.bodyAsText())) prefs.getString("access", null) else null
    }

    private suspend fun post(path: String, body: JsonObject): HttpResponse? = withContext(Dispatchers.IO) {
        runCatching {
            Addons.http.post("$base$path") {
                header("apikey", anon)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }.getOrNull()
    }
}

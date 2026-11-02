package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Small TMDB helper shared by the describe-search and compare features. */
internal object LuxoTmdbLite {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(endpoint: String, query: Map<String, String> = emptyMap()): JsonObject? {
        val key = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.parseToJsonElement(httpGetText(buildTmdbUrl(endpoint, key, query))) as? JsonObject }.getOrNull()
    }

    fun enc(s: String): String = buildString {
        s.encodeToByteArray().forEach { b ->
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch == '-' || ch == '_' || ch == '.' || ch == '~') append(ch)
            else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }

    fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject
    fun JsonObject.a(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    fun img(path: String?, size: String = "w342"): String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }

    /** Pulls the first `{…}` block out of a companion answer. */
    fun jsonBlock(answer: String): JsonObject? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }
}

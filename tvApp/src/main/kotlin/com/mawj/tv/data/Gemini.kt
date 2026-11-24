package com.mawj.tv.data

import com.mawj.tv.BuildConfig
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Gemini for Mawj's companion tools on the TV (the same models as the tablet). Called only when a
 * tool is used, never in the background. Busy or missing models fall through to the next one.
 */
object Gemini {
    private val key = BuildConfig.GEMINI_KEY.trim()
    val enabled: Boolean get() = key.isNotEmpty()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val models = listOf("gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite")

    /** The answer text, or null. [jpegBase64] adds a picture (the current frame). */
    suspend fun ask(prompt: String, jpegBase64: String? = null): String? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        jpegBase64?.let { add(buildJsonObject { put("inline_data", buildJsonObject { put("mime_type", "image/jpeg"); put("data", it) }) }) }
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
        }.toString()
        for (model in models) {
            val text = runCatching {
                val r = Addons.http.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
                    header("x-goog-api-key", key)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                if (r.status.value !in 200..299) return@runCatching null
                val o = json.parseToJsonElement(r.bodyAsText()) as JsonObject
                val parts = (((o["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("content") as? JsonObject)?.get("parts") as? JsonArray
                parts?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }?.joinToString("")?.trim()
            }.getOrNull()
            if (!text.isNullOrBlank()) return@withContext text
        }
        null
    }

    /** A JSON array of strings from the answer, or null. */
    fun stringArray(answer: String?): List<String>? {
        val a = answer ?: return null
        val inner = a.substringAfter('[', "").substringBeforeLast(']', "")
        if (inner.isEmpty()) return null
        return runCatching { (json.parseToJsonElement("[$inner]") as JsonArray).map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } }.getOrNull()
    }

    fun obj(answer: String?): JsonObject? {
        val a = answer ?: return null
        val start = a.indexOf('{'); val end = a.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(a.substring(start, end + 1)) as JsonObject }.getOrNull()
    }
}

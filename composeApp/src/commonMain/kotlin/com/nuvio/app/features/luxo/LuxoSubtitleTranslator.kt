package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.details.LuxoCompanionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * «ترجمة الترجمة للعربي»: downloads an English (or other) subtitle, translates every line to
 * natural Arabic with Gemini in parallel chunks, keeps the exact timings and returns an SRT file.
 * Finished translations are cached by the source address.
 */
object LuxoSubtitleTranslator {
    private data class Cue(val start: String, val end: String, val text: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, String>()

    private fun normTime(t: String): String {
        // VTT 00:01.234 or 00:00:01.234 → SRT 00:00:01,234
        val clean = t.trim().substringBefore(' ').replace('.', ',')
        val parts = clean.split(':')
        return if (parts.size == 2) "00:$clean" else clean
    }

    private fun parse(raw: String): List<Cue> {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = text.split(Regex("\n\\s*\n"))
        return blocks.mapNotNull { block ->
            val lines = block.trim().lines()
            val timeIndex = lines.indexOfFirst { "-->" in it }
            if (timeIndex < 0) return@mapNotNull null
            val (s, e) = lines[timeIndex].split("-->").map { it.trim() }.let { (it.getOrNull(0) ?: return@mapNotNull null) to (it.getOrNull(1) ?: return@mapNotNull null) }
            val body = lines.drop(timeIndex + 1).joinToString("\n").replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*\\}"), "").trim()
            if (body.isBlank()) return@mapNotNull null
            Cue(normTime(s), normTime(e), body)
        }
    }

    /** [onProgress] gets 0..1. Returns SRT text, or null when it couldn't be translated. */
    suspend fun translate(url: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.Default) {
        cache[url]?.let { return@withContext it }
        val apiKey = LuxoCompanionApi.loadKey() ?: return@withContext null
        val raw = runCatching { httpGetText(url) }.getOrNull() ?: return@withContext null
        val cues = parse(raw)
        if (cues.isEmpty()) return@withContext null
        val chunks = cues.chunked(70)
        var done = 0
        val gate = Semaphore(3)
        val translated = coroutineScope {
            chunks.map { chunk ->
                async {
                    gate.withPermit {
                        val result = translateChunk(apiKey, chunk.map { it.text })
                        done++
                        onProgress(done.toFloat() / chunks.size)
                        result ?: chunk.map { it.text }
                    }
                }
            }.awaitAll().flatten()
        }
        val srt = buildString {
            cues.forEachIndexed { i, c ->
                append(i + 1).append('\n')
                append(c.start).append(" --> ").append(c.end).append('\n')
                append(translated.getOrNull(i) ?: c.text).append("\n\n")
            }
        }
        cache[url] = srt
        srt
    }

    private suspend fun translateChunk(apiKey: String, lines: List<String>): List<String>? {
        val prompt = "ترجم سطور الترجمة التالية لمسلسل/فيلم إلى العربية الفصحى المبسطة بأسلوب الترجمة السينمائية، قصيرة وطبيعية. " +
            "حافظ على نفس عدد السطور ونفس الترتيب تماماً، ولا تدمج سطرين. " +
            "أعد JSON فقط بهذا الشكل: {\"lines\":[\"...\"]}\n\n" +
            lines.mapIndexed { i, l -> "${i + 1}. ${l.replace('\n', ' ')}" }.joinToString("\n")
        repeat(2) {
            val answer = LuxoCompanionApi.ask(apiKey, prompt)
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start >= 0 && end > start) {
                val arr = runCatching { (json.parseToJsonElement(answer.substring(start, end + 1)) as JsonObject)["lines"] as JsonArray }.getOrNull()
                val out = arr?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
                if (out != null && out.size == lines.size) return out
            }
        }
        return null
    }
}

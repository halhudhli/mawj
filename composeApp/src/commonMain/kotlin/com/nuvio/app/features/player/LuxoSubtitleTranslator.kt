package com.nuvio.app.features.player

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.luxoWriteCacheFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull

/**
 * Luxo: when an episode has no Arabic subtitle, Luxo Companion (Gemini) translates an English one
 * and the result is offered as «عربي ✨ ترجمة Luxo».
 */
internal object LuxoSubtitleTranslator {
    private const val KEY_ENABLED = "subtitle_auto_translate"
    private const val BATCH = 90
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = mutableMapOf<String, String>()

    private val _enabled = MutableStateFlow(
        runCatching { LuxoPrefsStorage.load(KEY_ENABLED)?.toBooleanStrictOrNull() }.getOrNull() ?: true,
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        runCatching { LuxoPrefsStorage.save(KEY_ENABLED, value.toString()) }
    }

    fun canTranslate(): Boolean = _enabled.value && LuxoCompanionApi.loadKey() != null

    fun isEnglish(subtitle: AddonSubtitle): Boolean {
        val code = subtitle.language.lowercase()
        return code == "en" || code == "eng" || code.startsWith("en-") || code.startsWith("english")
    }

    /** Translates [source] and returns the Arabic track, or null if anything fails. */
    suspend fun translate(source: AddonSubtitle): AddonSubtitle? {
        val key = LuxoCompanionApi.loadKey() ?: return null
        val uri = cache[source.url] ?: run {
            val body = runCatching { httpGetText(source.url) }.getOrNull() ?: return null
            val cues = PlayerSubtitleCueParser.parse(body, source.url).filter { it.text.isNotBlank() }
            if (cues.isEmpty()) return null
            val translated = translateLines(key, cues.map { it.text }) ?: return null
            val srt = buildSrt(cues, translated)
            val name = "luxo_ar_${source.url.hashCode().toUInt()}.srt"
            luxoWriteCacheFile(name, srt)?.also { cache[source.url] = it } ?: return null
        }
        return AddonSubtitle(
            id = "luxo-translated-${source.id}",
            url = uri,
            language = "ar",
            display = "عربي ✨ ترجمة Luxo",
            addonName = "Luxo · ${source.addonName ?: source.display}",
        )
    }

    private suspend fun translateLines(key: String, lines: List<String>): List<String>? = coroutineScope {
        val permits = Semaphore(3)
        val batches = lines.chunked(BATCH)
        val results = batches.map { batch ->
            async { permits.withPermit { translateBatch(key, batch) } }
        }.awaitAll()
        // If more than a quarter of the batches failed, the result isn't worth showing.
        if (results.count { it == null } * 4 > results.size) return@coroutineScope null
        results.flatMapIndexed { index, translated -> translated ?: batches[index] }
    }

    private suspend fun translateBatch(key: String, batch: List<String>): List<String>? {
        repeat(2) {
            try {
                val input = buildJsonArray { batch.forEach { add(JsonPrimitive(it)) } }.toString()
                val prompt = buildString {
                    append("Translate these movie/TV subtitle lines from English to natural, fluent Arabic ")
                    append("(Modern Standard Arabic that sounds natural to Saudi viewers). Keep names as they are, ")
                    append("keep line breaks inside an item as \\n, keep it short like real subtitles, no explanations. ")
                    append("Return ONLY a JSON array of ${batch.size} strings in the same order.\n")
                    append(input)
                }
                val reply = LuxoCompanionApi.ask(key, prompt)
                val cleaned = reply.substringAfter('[', "").substringBeforeLast(']', "")
                if (cleaned.isEmpty()) return@repeat
                val array = json.parseToJsonElement("[$cleaned]") as? JsonArray ?: return@repeat
                val out = array.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
                if (out.size == batch.size) return out.mapIndexed { i, text -> text.ifBlank { batch[i] } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
        return null
    }

    private fun buildSrt(cues: List<SubtitleSyncCue>, texts: List<String>): String = buildString {
        cues.forEachIndexed { index, cue ->
            append(index + 1).append('\n')
            append(srtTime(cue.startTimeMs)).append(" --> ").append(srtTime(cue.endTimeMs)).append('\n')
            append(texts.getOrNull(index).orEmpty().replace("\\n", "\n").trim()).append("\n\n")
        }
    }

    private fun srtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1000) % 60
        val milli = total % 1000
        fun two(v: Long) = v.toString().padStart(2, '0')
        return "${two(h)}:${two(m)}:${two(s)},${milli.toString().padStart(3, '0')}"
    }
}

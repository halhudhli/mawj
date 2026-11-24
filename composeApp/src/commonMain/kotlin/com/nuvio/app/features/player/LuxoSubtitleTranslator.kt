package com.nuvio.app.features.player

import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoFeaturesRepository
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
 * «ترجمة الترجمة للعربي» — the one Gemini translator (automatic and from the player menu).
 *
 *  1. The file is read by the Mawj encoding rule (so French, Turkish or Russian letters arrive intact).
 *  2. An Arabic file is never sent: Arabic needs no translation.
 *  3. Any format (SRT, VTT, ASS/SSA, TTML) becomes one clean WebVTT first: plain lines, no styling
 *     tags, exact timings. Gemini only ever sees those plain lines.
 *  4. The Arabic result is WebVTT with the same timings, kept on disk for next time.
 */
internal object LuxoSubtitleTranslator {
    private const val KEY_ENABLED = "subtitle_auto_translate"
    private const val BATCH = 80
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val memory = mutableMapOf<String, String>()

    sealed interface Result {
        data class Ready(val subtitle: AddonSubtitle) : Result
        data object AlreadyArabic : Result
        data object NoKey : Result
        data object Unreadable : Result
        data object Failed : Result
    }

    private val _enabled = MutableStateFlow(
        runCatching { LuxoPrefsStorage.load(KEY_ENABLED)?.toBooleanStrictOrNull() }.getOrNull() ?: true,
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        runCatching { LuxoPrefsStorage.save(KEY_ENABLED, value.toString()) }
    }

    fun hasKey(): Boolean = LuxoCompanionApi.loadKey() != null

    /** Automatic translation when an episode has no Arabic subtitle. */
    fun canTranslate(): Boolean = _enabled.value && hasKey() &&
        runCatching { LuxoFeaturesRepository.current().subtitleTranslate }.getOrDefault(true)

    fun isEnglish(subtitle: AddonSubtitle): Boolean {
        val code = subtitle.language.lowercase()
        return code == "en" || code == "eng" || code.startsWith("en-") || code.startsWith("english")
    }

    fun isArabicTagged(subtitle: AddonSubtitle): Boolean {
        val code = subtitle.language.lowercase()
        return code == "ar" || code == "ara" || code.startsWith("ar-") || code.startsWith("arabic") ||
            subtitle.id.startsWith("luxo-translated-")
    }

    /** English first, then any other language: the best source to translate from. */
    fun rankSources(subtitles: List<AddonSubtitle>): List<AddonSubtitle> =
        subtitles.filterNot(::isArabicTagged).sortedBy { if (isEnglish(it)) 0 else 1 }

    /** Kept for the automatic path: the Arabic track, or null. */
    suspend fun translate(source: AddonSubtitle): AddonSubtitle? =
        (translateDetailed(source, headers = emptyMap()) {} as? Result.Ready)?.subtitle

    suspend fun translateDetailed(
        source: AddonSubtitle,
        headers: Map<String, String>,
        onProgress: (Float) -> Unit,
    ): Result {
        val key = LuxoCompanionApi.loadKey() ?: return Result.NoKey
        val cacheName = "mawj_ar_${source.url.hashCode().toUInt().toString(16)}.vtt"
        val vtt = memory[source.url]
            ?: AppFileCache.read("mawj_translated/$cacheName")?.takeIf { it.startsWith("WEBVTT") }
            ?: run {
                val body = runCatching { mawjFetchSubtitleText(source.url, headers, source.language) }.getOrNull()
                    ?: return Result.Unreadable
                if (MawjArabicText.looksArabic(body)) return Result.AlreadyArabic
                val cues = PlayerSubtitleCueParser.parse(body, source.url)
                    .map { it.copy(text = plainText(it.text)) }
                    .filter { it.text.isNotBlank() }
                if (cues.isEmpty()) return Result.Unreadable
                val translated = translateLines(key, cues.map { it.text }, languageName(source), onProgress)
                    ?: return Result.Failed
                toVtt(cues, translated).also { AppFileCache.write("mawj_translated/$cacheName", it) }
            }
        memory[source.url] = vtt
        val uri = luxoWriteCacheFile(cacheName, vtt) ?: return Result.Failed
        return Result.Ready(
            AddonSubtitle(
                id = "luxo-translated-${source.id}",
                url = uri,
                language = "ar",
                display = "عربي ✨ ترجمة موج",
                addonName = "موج · ${source.addonName ?: source.display}",
            ),
        )
    }

    /** ASS override blocks, HTML tags and drawing commands removed: only what is said. */
    private fun plainText(text: String): String = text
        .replace(Regex("""\{[^}]*\}"""), "")
        .replace(Regex("""</?[a-zA-Z][^>]*>"""), "")
        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        .lines().map { it.trim() }.filter { it.isNotBlank() }
        .joinToString("\n")

    private fun languageName(source: AddonSubtitle): String {
        val code = source.language.lowercase()
        return when {
            isEnglish(source) -> "English"
            code.startsWith("fr") -> "French"
            code.startsWith("es") || code.startsWith("spa") -> "Spanish"
            code.startsWith("tr") -> "Turkish"
            code.startsWith("de") || code.startsWith("ger") -> "German"
            code.startsWith("it") -> "Italian"
            code.startsWith("pt") || code.startsWith("por") -> "Portuguese"
            code.startsWith("ru") -> "Russian"
            code.startsWith("ja") || code.startsWith("jpn") -> "Japanese"
            code.startsWith("ko") -> "Korean"
            code.startsWith("zh") || code.startsWith("chi") -> "Chinese"
            else -> "the original language"
        }
    }

    private suspend fun translateLines(
        key: String,
        lines: List<String>,
        from: String,
        onProgress: (Float) -> Unit,
    ): List<String>? = coroutineScope {
        val permits = Semaphore(3)
        val batches = lines.chunked(BATCH)
        var done = 0
        val results = batches.map { batch ->
            async {
                permits.withPermit {
                    translateBatch(key, batch, from).also {
                        done++
                        onProgress(done.toFloat() / batches.size)
                    }
                }
            }
        }.awaitAll()
        // If more than a quarter of the batches failed, the result isn't worth showing.
        if (results.count { it == null } * 4 > results.size) return@coroutineScope null
        results.flatMapIndexed { index, translated -> translated ?: batches[index] }
    }

    private suspend fun translateBatch(key: String, batch: List<String>, from: String): List<String>? {
        repeat(2) {
            try {
                val input = buildJsonArray { batch.forEach { add(JsonPrimitive(it)) } }.toString()
                val prompt = buildString {
                    append("Translate these movie/TV subtitle lines from $from to natural, fluent Arabic ")
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

    /** The clean WebVTT both the source and the Arabic result are written as. */
    private fun toVtt(cues: List<SubtitleSyncCue>, texts: List<String>): String = buildString {
        append("WEBVTT\n\n")
        cues.forEachIndexed { index, cue ->
            append(vttTime(cue.startTimeMs)).append(" --> ").append(vttTime(cue.endTimeMs)).append('\n')
            append(texts.getOrNull(index).orEmpty().replace("\\n", "\n").trim().ifBlank { cue.text }).append("\n\n")
        }
    }

    private fun vttTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1000) % 60
        val milli = total % 1000
        fun two(v: Long) = v.toString().padStart(2, '0')
        return "${two(h)}:${two(m)}:${two(s)}.${milli.toString().padStart(3, '0')}"
    }
}

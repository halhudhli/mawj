package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Arabic for everything Luxo shows: stories/biographies that TMDB has no Arabic for are translated
 * by Gemini, and people's names are written in Arabic letters (how they sound, not translated).
 * Results are cached on the device, so each text is translated only once.
 */
object LuxoTranslate {
    private const val KEY = "translate_cache_v1"
    private const val MAX_ENTRIES = 1500
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val mutex = Mutex()
    private var cache: LinkedHashMap<String, String>? = null

    /** Bumps whenever new translations arrive, so screens can redraw. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun enabled(): Boolean = LuxoFeaturesRepository.current().arabicTranslate

    fun hasArabic(text: String): Boolean = text.any { it in '؀'..'ۿ' }

    /** Latin, CJK, Cyrillic… worth converting (digits/symbols-only texts are skipped). */
    private fun needsArabic(text: String): Boolean = text.isNotBlank() && !hasArabic(text) && text.any { it.isLetter() }

    private fun store(): LinkedHashMap<String, String> {
        cache?.let { return it }
        val loaded = LuxoPrefsStorage.load(KEY)?.let { payload ->
            runCatching { json.decodeFromString(mapSerializer, payload) }.getOrNull()
        }.orEmpty()
        return LinkedHashMap(loaded).also { cache = it }
    }

    private fun persist() {
        val map = store()
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        LuxoPrefsStorage.save(KEY, json.encodeToString(mapSerializer, map))
    }

    private fun textKey(text: String) = "t:${text.length}:${text.hashCode()}"
    private fun nameKey(name: String) = "n:${name.trim().lowercase()}"

    fun cachedText(text: String?): String? = text?.let { store()[textKey(it)] }
    fun cachedName(name: String): String? = store()[nameKey(name)]

    private fun extractJson(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }

    private fun key(): String? = LuxoCompanionApi.loadKey()

    /** Arabic version of a story or biography, or null when it is already Arabic / not possible. */
    suspend fun text(text: String?): String? {
        if (text == null || !enabled() || !needsArabic(text)) return null
        cachedText(text)?.let { return it }
        val apiKey = key() ?: return null
        val prompt = "ترجم النص التالي إلى العربية الفصحى السهلة بأسلوب طبيعي وسلس (ليس حرفياً)، " +
            "بدون إضافة أو حذف أي معلومة، واكتب أسماء الأشخاص والأماكن بالحروف العربية. " +
            "أعد JSON فقط بهذا الشكل: {\"t\":\"الترجمة\"}\n\nالنص:\n" + text.take(3000)
        val answer = LuxoCompanionApi.ask(apiKey, prompt)
        val translated = extractJson(answer)?.get("t")?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotBlank() && hasArabic(it) } ?: return null
        mutex.withLock {
            store()[textKey(text)] = translated
            persist()
        }
        _version.value += 1
        return translated
    }

    /** Arabic spellings for people's names (one Gemini call for the whole list). */
    suspend fun names(names: List<String>): Map<String, String> {
        if (!enabled()) return emptyMap()
        val wanted = names.map { it.trim() }.filter { needsArabic(it) }.distinct()
        val result = mutableMapOf<String, String>()
        val missing = wanted.filter { name -> cachedName(name)?.also { result[name] = it } == null }
        if (missing.isEmpty()) return result
        val apiKey = key() ?: return result
        for (chunk in missing.chunked(40)) {
            val prompt = "اكتب أسماء الأشخاص التالية بالحروف العربية كما تُنطق في لغتها الأصلية (تعريب لا ترجمة)، " +
                "وبالكتابة الشائعة في الإعلام العربي إن وُجدت. حافظ على نفس الترتيب ونفس العدد. " +
                "أعد JSON فقط بهذا الشكل: {\"names\":[\"...\"]}\n\nالأسماء:\n" +
                chunk.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")
            val answer = LuxoCompanionApi.ask(apiKey, prompt)
            val list = (extractJson(answer)?.get("names") as? JsonArray)
                ?.map { it.jsonPrimitive.contentOrNull?.trim().orEmpty() } ?: continue
            if (list.size != chunk.size) continue
            mutex.withLock {
                chunk.zip(list).forEach { (original, arabic) ->
                    if (arabic.isNotBlank() && hasArabic(arabic)) {
                        store()[nameKey(original)] = arabic
                        result[original] = arabic
                    }
                }
                persist()
            }
        }
        _version.value += 1
        return result
    }

    /** Meta with everything already known in Arabic applied (no network). */
    fun applyCached(meta: MetaDetails): MetaDetails {
        if (!enabled()) return meta
        val description = cachedText(meta.description) ?: meta.description
        val cast = meta.cast.map { person -> cachedName(person.name)?.let { person.copy(name = it) } ?: person }
        val directors = meta.director.map { cachedName(it) ?: it }
        val writers = meta.writer.map { cachedName(it) ?: it }
        return if (description == meta.description && cast == meta.cast && directors == meta.director && writers == meta.writer) {
            meta
        } else {
            meta.copy(description = description, cast = cast, director = directors, writer = writers)
        }
    }
}

/**
 * The details page's meta in Arabic: cached translations right away, and missing ones fetched in
 * the background (the page updates when they arrive).
 */
@Composable
fun rememberLuxoArabicMeta(meta: MetaDetails?): MetaDetails? {
    val on = rememberLuxoFeatures().arabicTranslate
    if (meta == null || !on) return meta
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(meta.id, meta.description, meta.cast.size) {
        val needText = meta.description?.let { !LuxoTranslate.hasArabic(it) && LuxoTranslate.cachedText(it) == null } == true
        val people = meta.cast.map { it.name } + meta.director + meta.writer
        val needNames = people.any { !LuxoTranslate.hasArabic(it) && LuxoTranslate.cachedName(it) == null }
        if (needNames) {
            LuxoTranslate.names(people)
            tick++
        }
        if (needText) {
            LuxoTranslate.text(meta.description)
            tick++
        }
    }
    return remember(meta, tick) { LuxoTranslate.applyCached(meta) }
}

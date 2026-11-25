package com.mawj.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Everything the TV keeps: installed addons and where each title stopped. Small and local. */
object Store {
    private lateinit var prefs: SharedPreferences

    val DEFAULT_ADDONS = listOf(
        "https://v3-cinemeta.strem.io/manifest.json",
        "https://opensubtitles-v3.strem.io/manifest.json",
    )

    /** Manifest URLs, in the order the rows show. */
    val addonUrls = mutableStateListOf<String>()

    /** «أكمل المشاهدة», newest first. */
    val progress = mutableStateListOf<Progress>()

    // ---- Mawj feature switches (Settings → ميزات موج) ----

    /** Every switch and its default. All Mawj additions can be turned off. */
    val FEATURE_DEFAULTS: Map<String, Boolean> = mapOf(
        "animatedIcons" to true, "posterRotate" to true, "bestSource" to true, "fillerSkip" to true,
        "directHeavy" to true, "codecGuard" to true, "posterMotion" to true, "animatedCatalog" to true,
        "moodCompass" to true, "tonightPick" to true, "heroHome" to true, "episodeCurve" to true,
        "whyLike" to true, "finishPlan" to true, "parentsGuide" to true, "whereToWatch" to true,
        "geminiExtras" to true, "actorStart" to true, "companyHall" to true, "profileHub" to true,
        "uiSounds" to false, "screensaver" to true, "kidsLimit" to false, "privateMode" to false,
        "seasonalThemes" to true, "timeTint" to false, "top10Rank" to true, "becauseWatched" to true,
        "countdown" to true, "hideCam" to true, "smartSource" to true, "stallGuard" to true,
        "timeLeft" to true, "seriesMemory" to true, "perfMeter" to false, "googleTvRow" to true, "kidsMode" to false,
    )

    private val features = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun feature(key: String): Boolean = features[key] ?: FEATURE_DEFAULTS[key] ?: true

    val animatedIcons: Boolean get() = feature("animatedIcons")
    val posterRotate: Boolean get() = feature("posterRotate")
    val bestSource: Boolean get() = feature("bestSource")
    val fillerSkip: Boolean get() = feature("fillerSkip")

    fun setFeature(key: String, value: Boolean) {
        features[key] = value
        prefs.edit().putBoolean("f:$key", value).apply()
    }

    // ---- bookmarks inside an episode (player → العلامات) ----

    fun bookmarks(videoId: String): List<Long> =
        prefs.getString("bm:$videoId", null)?.split(',')?.mapNotNull { it.toLongOrNull() }?.sorted().orEmpty()

    fun addBookmark(videoId: String, ms: Long) {
        val list = (bookmarks(videoId) + ms).distinctBy { it / 2000 }.sorted().takeLast(30)
        prefs.edit().putString("bm:$videoId", list.joinToString(",")).apply()
    }

    fun removeBookmark(videoId: String, ms: Long) {
        prefs.edit().putString("bm:$videoId", bookmarks(videoId).filter { it != ms }.joinToString(",")).apply()
    }

    // ---- small player settings kept between videos (sound tools, subtitle look) ----

    fun flag(key: String, default: Boolean = false): Boolean = prefs.getBoolean("p:$key", default)
    fun setFlag(key: String, value: Boolean) = prefs.edit().putBoolean("p:$key", value).apply()
    fun text(key: String, default: String): String = prefs.getString("p:$key", default) ?: default
    fun setText(key: String, value: String) = prefs.edit().putString("p:$key", value).apply()
    fun number(key: String, default: Int): Int = prefs.getInt("p:$key", default)
    fun setNumber(key: String, value: Int) = prefs.edit().putInt("p:$key", value).apply()

    // ---- saved lines (player → حفظ العبارة) ----

    fun quotes(videoId: String): List<Pair<Long, String>> =
        prefs.getString("q:$videoId", null)?.split("\u001E")?.mapNotNull { e ->
            val ms = e.substringBefore('\u001F').toLongOrNull() ?: return@mapNotNull null
            ms to e.substringAfter('\u001F')
        }.orEmpty()

    fun addQuote(videoId: String, ms: Long, text: String) {
        val list = (quotes(videoId) + (ms to text.replace('\u001E', ' ').replace('\u001F', ' '))).distinctBy { it.second }.sortedBy { it.first }.takeLast(50)
        prefs.edit().putString("q:$videoId", list.joinToString("\u001E") { "${it.first}\u001F${it.second}" }).apply()
    }

    // ---- player look (picture colours preset, kept for every video) ----

    var colorPreset: String
        get() = prefs.getString("colorPreset", "natural0") ?: "natural0"
        set(v) { prefs.edit().putString("colorPreset", v).apply() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mawj_tv", Context.MODE_PRIVATE)
        FEATURE_DEFAULTS.forEach { (k, d) -> features[k] = prefs.getBoolean("f:$k", d) }
        val saved = prefs.getString("addons", null)
        addonUrls.addAll(saved?.lines()?.map(String::trim)?.filter(String::isNotEmpty) ?: DEFAULT_ADDONS)
        progress.addAll(loadProgress())
    }

    fun addAddon(url: String): Boolean {
        val clean = normalizeManifestUrl(url) ?: return false
        if (clean in addonUrls) return false
        addonUrls.add(clean)
        saveAddons()
        return true
    }

    /** The account's list replaces the local one (same order). */
    fun replaceAddons(urls: List<String>) {
        addonUrls.clear()
        addonUrls.addAll(urls)
        saveAddons()
    }

    fun removeAddon(url: String) {
        addonUrls.remove(url)
        saveAddons()
    }

    fun moveAddon(url: String, up: Boolean) {
        val i = addonUrls.indexOf(url)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in addonUrls.indices) return
        addonUrls[i] = addonUrls[j].also { addonUrls[j] = url }
        saveAddons()
    }

    private fun saveAddons() {
        prefs.edit().putString("addons", addonUrls.joinToString("\n")).apply()
    }

    /** stremio:// links and missing /manifest.json are accepted too. */
    fun normalizeManifestUrl(raw: String): String? {
        var u = raw.trim()
        if (u.isEmpty()) return null
        if (u.startsWith("stremio://")) u = "https://" + u.removePrefix("stremio://")
        if (!u.startsWith("http")) u = "https://$u"
        if (!u.endsWith("/manifest.json")) u = u.trimEnd('/') + "/manifest.json"
        return u
    }

    // ---- the source chosen for a title («متابعة» plays it again) ----

    fun rememberSource(metaId: String, stream: Stream) {
        val o = buildJsonObject {
            put("url", stream.url); put("name", stream.name); put("title", stream.title); put("addon", stream.addonName)
            put("q", stream.quality); stream.bingeGroup?.let { put("bg", it) }
            put("h", JsonObject(stream.headers.mapValues { JsonPrimitive(it.value) }))
        }
        prefs.edit().putString("src:$metaId", o.toString()).apply()
    }

    fun rememberedSource(metaId: String): Stream? = runCatching {
        val o = Json.parseToJsonElement(prefs.getString("src:$metaId", null) ?: return null).jsonObject
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content
        Stream(
            addonName = s("addon") ?: "", url = s("url") ?: return null, name = s("name") ?: "", title = s("title") ?: "",
            headers = (o["h"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.content ?: "" }.orEmpty(),
            quality = s("q")?.toIntOrNull() ?: 0, hdr = false, dolbyVision = false, sizeLabel = null, sizeBytes = 0, arabic = false,
            bingeGroup = s("bg"),
        )
    }.getOrNull()

    // ---- subtitle delay saved for a title ----

    fun subDelay(metaId: String): Double = prefs.getFloat("sd:$metaId", 0f).toDouble()

    fun saveSubDelay(metaId: String, seconds: Double) {
        prefs.edit().putFloat("sd:$metaId", seconds.toFloat()).apply()
    }

    // ---- progress ----

    fun saveProgress(p: Progress) {
        val before = progress.firstOrNull { it.videoId == p.videoId }?.positionMs ?: 0L
        Library.record(p.meta, p.positionMs - before)
        progress.removeAll { it.meta.id == p.meta.id }
        val watched = p.durationMs > 0 && p.positionMs > p.durationMs * 0.92
        if (watched && !isWatched(p.videoId)) Library.finished(p.meta, episode = p.season > 0)
        if (!watched && p.positionMs > 30_000) progress.add(0, p)
        while (progress.size > 30) progress.removeAt(progress.lastIndex)
        prefs.edit().putString("progress", JsonArray(progress.map(::encode)).toString()).apply()
        if (watched) markWatched(p.videoId)
    }

    fun positionFor(videoId: String): Long =
        progress.firstOrNull { it.videoId == videoId }?.positionMs ?: 0L

    fun progressFor(metaId: String): Progress? = progress.firstOrNull { it.meta.id == metaId }

    fun isWatched(videoId: String): Boolean = prefs.getBoolean("w:$videoId", false)

    private fun markWatched(videoId: String) {
        prefs.edit().putBoolean("w:$videoId", true).apply()
    }

    private fun encode(p: Progress): JsonObject = buildJsonObject {
        put("id", p.meta.id); put("type", p.meta.type); put("name", p.meta.name)
        p.meta.poster?.let { put("poster", it) }
        p.meta.background?.let { put("bg", it) }
        p.meta.logo?.let { put("logo", it) }
        put("vid", p.videoId); put("s", p.season); put("e", p.episode)
        p.episodeTitle?.let { put("et", it) }
        put("pos", p.positionMs); put("dur", p.durationMs); put("at", p.updatedAt)
    }

    private fun loadProgress(): List<Progress> = runCatching {
        Json.parseToJsonElement(prefs.getString("progress", "[]") ?: "[]").jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content
            fun l(k: String) = s(k)?.toLongOrNull() ?: 0L
            Progress(
                meta = MetaPreview(
                    id = s("id") ?: return@mapNotNull null, type = s("type") ?: "movie", name = s("name") ?: "",
                    poster = s("poster"), background = s("bg"), logo = s("logo"),
                    year = null, rating = null, description = null, genres = emptyList(),
                ),
                videoId = s("vid") ?: return@mapNotNull null,
                season = l("s").toInt(), episode = l("e").toInt(), episodeTitle = s("et"),
                positionMs = l("pos"), durationMs = l("dur"), updatedAt = l("at"),
            )
        }
    }.getOrDefault(emptyList())
}

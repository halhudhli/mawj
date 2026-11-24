package com.mawj.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
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

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mawj_tv", Context.MODE_PRIVATE)
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

    // ---- progress ----

    fun saveProgress(p: Progress) {
        progress.removeAll { it.meta.id == p.meta.id }
        val watched = p.durationMs > 0 && p.positionMs > p.durationMs * 0.92
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

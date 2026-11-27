package com.mawj.tv.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A part of an episode that can be skipped: the intro, a recap, or the end credits. */
data class SkipPart(val kind: String, val startMs: Long, val endMs: Long) {
    val label: String get() = when (kind) { "recap" -> "تخطّي الملخص"; "outro" -> "تخطّي النهاية"; else -> "تخطّي المقدمة" }
}

/**
 * «تخطّي المقدمة»: the same sources as the tablet. IntroDB for series and films (when its address
 * is set for this build), and AniSkip for anime (its MyAnimeList id found through the ARM list).
 */
object Skip {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun fetch(url: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val r = Addons.http.get(url)
            if (r.status.isSuccess()) json.parseToJsonElement(r.bodyAsText()) else null
        }.getOrNull()
    }

    suspend fun parts(meta: MetaPreview, episode: Episode?): List<SkipPart> {
        val imdb = meta.id.substringBefore(':').takeIf { it.startsWith("tt") } ?: return emptyList()
        introDb(imdb, episode)?.takeIf { it.isNotEmpty() }?.let { return it }
        if (episode == null) return emptyList()
        return aniSkip(imdb, episode).orEmpty()
    }

    private suspend fun introDb(imdb: String, episode: Episode?): List<SkipPart>? {
        val base = com.mawj.tv.BuildConfig.INTRODB_URL.trimEnd('/')
        if (base.isBlank()) return null
        val url = if (episode == null) "$base/segments?imdb_id=$imdb" else "$base/segments?imdb_id=$imdb&season=${episode.season}&episode=${episode.number}"
        val o = fetch(url) as? JsonObject ?: return null
        return listOf("recap", "intro", "outro").mapNotNull { k ->
            val s = o[k] as? JsonObject ?: return@mapNotNull null
            fun ms(msKey: String, secKey: String) = (s[msKey] as? JsonPrimitive)?.longOrNull
                ?: (s[secKey] as? JsonPrimitive)?.doubleOrNull?.let { (it * 1000).toLong() }
            val a = ms("start_ms", "start_sec") ?: return@mapNotNull null
            val b = ms("end_ms", "end_sec") ?: return@mapNotNull null
            if (b > a) SkipPart(k, a, b) else null
        }
    }

    private suspend fun aniSkip(imdb: String, episode: Episode): List<SkipPart>? {
        val arm = fetch("https://arm.haglund.dev/api/v2/imdb?id=$imdb&include=myanimelist") as? JsonArray ?: return null
        val mal = arm.mapNotNull { ((it as? JsonObject)?.get("myanimelist") as? JsonPrimitive)?.intOrNull }
        if (mal.isEmpty()) return null
        // Each season of an anime is its own MyAnimeList entry, in order.
        val id = mal.getOrNull(episode.season - 1) ?: mal.first()
        val o = fetch("https://api.aniskip.com/v2/skip-times/$id/${episode.number}?types=op&types=ed&types=recap&episodeLength=0") as? JsonObject ?: return null
        return (o["results"] as? JsonArray).orEmpty().mapNotNull { r ->
            val j = r as? JsonObject ?: return@mapNotNull null
            val iv = j["interval"] as? JsonObject ?: return@mapNotNull null
            val a = (iv["startTime"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val b = (iv["endTime"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val kind = when ((j["skipType"] as? JsonPrimitive)?.content) { "ed" -> "outro"; "recap" -> "recap"; else -> "intro" }
            SkipPart(kind, (a * 1000).toLong(), (b * 1000).toLong())
        }
    }
}

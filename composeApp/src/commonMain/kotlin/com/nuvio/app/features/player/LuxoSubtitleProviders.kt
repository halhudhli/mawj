package com.nuvio.app.features.player

import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LUXO: built-in subtitle providers (Arabic only), shown next to add-on subtitles.
 *
 * SubDL is queried with `unpack=1`, so every result points to a single subtitle file
 * the player can load directly (no zip handling needed).
 */
internal object LuxoSubtitleProviders {
    /** Keys requested by the owner to be built in. Empty = provider disabled. */
    private const val SUBDL_API_KEY = "Nx0lZs39RqWllRUtjPdpCDfuybiMl8Sr"

    private const val SUBDL_API = "https://api.subdl.com/api/v1/subtitles"
    private const val SUBDL_DL = "https://dl.subdl.com"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(type: String, videoId: String): List<AddonSubtitle> =
        try {
            withTimeoutOrNull(12_000L) { subdl(type, videoId) }.orEmpty()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            emptyList()
        }

    private suspend fun subdl(type: String, videoId: String): List<AddonSubtitle> {
        if (SUBDL_API_KEY.isBlank()) return emptyList()
        val parts = videoId.split(':')
        val imdb = parts.firstOrNull()?.takeIf { it.startsWith("tt") } ?: return emptyList()
        val season = parts.getOrNull(1)?.toIntOrNull()
        val episode = parts.getOrNull(2)?.toIntOrNull()
        val isSeries = type.equals("series", true) || type.equals("tv", true) || season != null

        val query = buildString {
            append(SUBDL_API)
            append("?api_key=").append(SUBDL_API_KEY)
            append("&imdb_id=").append(imdb)
            append("&type=").append(if (isSeries) "tv" else "movie")
            if (isSeries && season != null) append("&season_number=").append(season)
            if (isSeries && episode != null) append("&episode_number=").append(episode)
            append("&languages=AR&subs_per_page=30&unpack=1")
        }
        val root = json.parseToJsonElement(httpGetText(query)).jsonObject
        val subtitles = root["subtitles"] as? JsonArray ?: return emptyList()

        val out = mutableListOf<AddonSubtitle>()
        subtitles.forEachIndexed { index, element ->
            val sub = element as? JsonObject ?: return@forEachIndexed
            val release = sub.str("release_name") ?: sub.str("name") ?: "SubDL"
            val files = (sub["unpack_files"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            files.forEachIndexed { fileIndex, file ->
                val format = file.str("format")?.lowercase()
                if (format != null && format !in setOf("srt", "vtt", "ass", "ssa")) return@forEachIndexed
                if (isSeries && episode != null) {
                    val fileEpisode = file["episode"]?.jsonPrimitive?.intOrNull
                    if (fileEpisode != null && fileEpisode != episode) return@forEachIndexed
                }
                val rawUrl = file.str("url") ?: return@forEachIndexed
                val url = if (rawUrl.startsWith("http")) rawUrl else SUBDL_DL + (if (rawUrl.startsWith("/")) "" else "/") + rawUrl
                out += AddonSubtitle(
                    id = "subdl_${index}_$fileIndex",
                    url = url,
                    language = "ar",
                    display = "العربية • ${file.str("release_name") ?: release} (SubDL)",
                    addonName = "SubDL",
                )
            }
        }
        return out.distinctBy { it.url }
    }

    /** Keeps Arabic subtitles only (owner's choice: no other languages in the list). */
    fun isArabic(subtitle: AddonSubtitle): Boolean {
        val code = subtitle.language.lowercase()
        return code == "ar" || code == "ara" || code.startsWith("ar-") || code.startsWith("arabic")
    }

    private fun JsonObject.str(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
}

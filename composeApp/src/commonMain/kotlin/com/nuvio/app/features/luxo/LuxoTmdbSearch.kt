package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbService
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Luxo: addon search (Cinemeta) only understands English titles. When a search contains Arabic,
 * TMDB is searched as well and its results are mapped back to IMDb ids so streams still resolve.
 */
internal object LuxoTmdbSearch {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    private const val MAX_RESULTS = 15
    private val json = Json { ignoreUnknownKeys = true }

    fun shouldSearch(query: String): Boolean = query.any { it in '؀'..'ۿ' }

    /** [type] is the addon content type: "movie" or "series". */
    suspend fun searchSection(query: String, type: String): HomeCatalogSection {
        val settings = TmdbSettingsRepository.snapshot()
        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() }
            ?: error("TMDB key missing")
        val mediaType = if (type == LuxoContentType.MOVIE) "movie" else "tv"
        val url = buildTmdbUrl(
            endpoint = "search/$mediaType",
            apiKey = apiKey,
            query = mapOf(
                "query" to query,
                "language" to normalizeTmdbLanguage(settings.language),
                "include_adult" to "false",
            ),
        )
        val results = json.decodeFromString<SearchResponse>(httpGetText(url)).results.take(MAX_RESULTS)
        val items = coroutineScope {
            results.map { result ->
                async {
                    val imdbId = runCatching { TmdbService.tmdbToImdb(result.id, mediaType) }.getOrNull()
                    result.toPreview(id = imdbId ?: "tmdb:${result.id}", type = type)
                }
            }.awaitAll()
        }
        require(items.isNotEmpty()) { "No TMDB results" }
        val title = if (type == LuxoContentType.MOVIE) "TMDB · أفلام" else "TMDB · مسلسلات"
        return HomeCatalogSection(
            key = "luxo_tmdb_search_$type",
            title = title,
            subtitle = "TMDB",
            addonName = "TMDB",
            target = CatalogTarget.Addon(
                manifestUrl = "luxo://tmdb",
                contentType = type,
                catalogId = "search",
            ),
            items = items,
            availableItemCount = items.size,
            hasMore = false,
        )
    }

    private fun SearchResult.toPreview(id: String, type: String): MetaPreview {
        val poster = posterPath?.takeIf { it.isNotBlank() }?.let { "${IMAGE_BASE}w342$it" }
        return MetaPreview(
            id = id,
            type = type,
            name = (title ?: name)?.takeIf { it.isNotBlank() } ?: (originalTitle ?: originalName).orEmpty(),
            poster = poster,
            banner = backdropPath?.takeIf { it.isNotBlank() }?.let { "${IMAGE_BASE}w1280$it" },
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() },
            imdbRating = voteAverage?.takeIf { it > 0.0 }?.let { ((it * 10).toInt() / 10.0).toString() },
            rawPosterUrl = poster,
        )
    }

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
    )
}

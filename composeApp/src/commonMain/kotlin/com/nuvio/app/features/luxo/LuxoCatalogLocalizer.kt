package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import com.nuvio.app.features.tmdb.normalizeTmdbLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Luxo: every catalog item from an addon (Cinemeta and friends) is matched on TMDB, so posters,
 * backdrops, titles and descriptions come from TMDB in the app's TMDB language (Arabic by
 * default) instead of the addon. The addon's own artwork stays as the fallback image.
 */
internal object LuxoCatalogLocalizer {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    private const val MAX_CACHE = 4000

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheMutex = Mutex()
    private val cache = LinkedHashMap<String, TmdbCard?>()
    private val requests = Semaphore(8)

    suspend fun localize(items: List<MetaPreview>): List<MetaPreview> {
        if (items.isEmpty()) return items
        val settings = runCatching { TmdbSettingsRepository.snapshot() }.getOrNull() ?: return items
        if (!settings.enabled) return items
        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return items
        val language = normalizeTmdbLanguage(settings.language)
        return runCatching {
            coroutineScope {
                items.map { item ->
                    async { lookup(item, apiKey, language)?.let { item.applyTmdb(it) } ?: item }
                }.awaitAll()
            }
        }.getOrDefault(items)
    }

    private suspend fun lookup(item: MetaPreview, apiKey: String, language: String): TmdbCard? {
        val mediaType = when (item.type.lowercase()) {
            "movie" -> "movie"
            "series", "tv", "show" -> "tv"
            else -> return null
        }
        val rawId = item.id.substringBefore(':').trim()
        val tmdbId = item.id.removePrefix("tmdb:").substringBefore(':').trim().takeIf { item.id.startsWith("tmdb:") && it.all(Char::isDigit) }
        val imdbId = rawId.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (imdbId == null && tmdbId == null) return null

        val key = "$language|$mediaType|${imdbId ?: "tmdb$tmdbId"}"
        cacheMutex.withLock { if (cache.containsKey(key)) return cache[key] }

        val card = requests.withPermit {
            runCatching {
                if (imdbId != null) {
                    val url = buildTmdbUrl(
                        endpoint = "find/$imdbId",
                        apiKey = apiKey,
                        query = mapOf("external_source" to "imdb_id", "language" to language),
                    )
                    val found = json.decodeFromString<FindResponse>(httpGetText(url))
                    if (mediaType == "movie") found.movies.firstOrNull() ?: found.shows.firstOrNull()
                    else found.shows.firstOrNull() ?: found.movies.firstOrNull()
                } else {
                    val url = buildTmdbUrl(
                        endpoint = "$mediaType/$tmdbId",
                        apiKey = apiKey,
                        query = mapOf("language" to language),
                    )
                    json.decodeFromString<TmdbCard>(httpGetText(url))
                }
            }.getOrNull()
        }

        cacheMutex.withLock {
            cache[key] = card
            if (cache.size > MAX_CACHE) cache.remove(cache.keys.first())
        }
        return card
    }

    private fun MetaPreview.applyTmdb(card: TmdbCard): MetaPreview {
        val tmdbPoster = card.posterPath?.takeIf { it.isNotBlank() }?.let { "${IMAGE_BASE}w342$it" }
        val tmdbBackdrop = card.backdropPath?.takeIf { it.isNotBlank() }?.let { "${IMAGE_BASE}w1280$it" }
        val localizedName = (card.title ?: card.name)?.trim()?.takeIf { it.isNotBlank() }
        val localizedOverview = card.overview?.trim()?.takeIf { it.isNotBlank() }
        val rating = card.voteAverage?.takeIf { it > 0.0 }?.let { ((it * 10).toInt() / 10.0).toString() }
        return copy(
            name = localizedName ?: name,
            description = localizedOverview ?: description,
            poster = tmdbPoster ?: poster,
            // The addon's poster becomes the fallback if the TMDB image can't load.
            rawPosterUrl = if (tmdbPoster != null) (rawPosterUrl ?: poster) else rawPosterUrl,
            banner = tmdbBackdrop ?: banner,
            imdbRating = imdbRating?.takeIf { it.isNotBlank() } ?: rating,
        )
    }

    @Serializable
    private data class FindResponse(
        @SerialName("movie_results") val movies: List<TmdbCard> = emptyList(),
        @SerialName("tv_results") val shows: List<TmdbCard> = emptyList(),
    )

    @Serializable
    internal data class TmdbCard(
        val id: Int = 0,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
        val genres: List<Genre> = emptyList(),
    ) {
        val allGenreIds: List<Int> get() = genreIds.ifEmpty { genres.map { it.id } }
    }

    @Serializable
    internal data class Genre(val id: Int = 0)
}

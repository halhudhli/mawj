package com.nuvio.app.features.luxo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What TMDB says about one watched title (cached on the device). */
@Serializable
data class LuxoTitleFacts(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: Int? = null,
    val countries: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val castPhotos: List<String?> = emptyList(),
    val directors: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val backdrop: String? = null,
    val poster: String? = null,
    val runtime: Int? = null,
)

/**
 * Facts for every watched title, used by the «ذوقي» board, «خمّن العمل» and the screensaver.
 * Looked up once per title and kept on the device; the TV looks up fewer titles.
 */
object LuxoTasteFacts {
    private const val KEY = "taste_facts_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), LuxoTitleFacts.serializer())
    private val mutex = Mutex()

    val facts = mutableStateMapOf<String, LuxoTitleFacts>().apply {
        LuxoPrefsStorage.load(KEY)?.let { p -> runCatching { json.decodeFromString(ser, p) }.getOrNull()?.let { putAll(it) } }
    }
    var loading by mutableStateOf(false)
        private set

    private fun save() = LuxoPrefsStorage.save(KEY, json.encodeToString(ser, facts.toMap()))

    fun keyOf(item: WatchedItem): String = "${if (item.type == "movie") "movie" else "tv"}:${item.id.lowercase()}"

    /** Fetches facts for titles not looked up yet (newest first). */
    suspend fun ensure(items: List<WatchedItem>, limit: Int = if (luxoLiteMode()) 60 else 220) = withContext(Dispatchers.Default) {
        if (loading) return@withContext
        mutex.withLock {
            val distinct = items.sortedByDescending { it.markedAtEpochMs }.distinctBy { keyOf(it) }.take(limit)
            val missing = distinct.filter { keyOf(it) !in facts }
            if (missing.isEmpty()) return@withLock
            loading = true
            val gate = Semaphore(if (luxoLiteMode()) 2 else 5)
            coroutineScope {
                missing.map { item ->
                    async { gate.withPermit { runCatching { fetch(item) }.getOrNull()?.let { keyOf(item) to it } } }
                }.awaitAll().filterNotNull().forEach { (k, v) -> facts[k] = v }
            }
            save()
            loading = false
        }
    }

    private suspend fun fetch(item: WatchedItem): LuxoTitleFacts? {
        val type = if (item.type == "movie") "movie" else "tv"
        val tmdbId: Int = when {
            item.id.startsWith("tmdb:") -> item.id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
            item.id.startsWith("tt") -> {
                val found = LuxoTmdbLite.get("find/${item.id.substringBefore(':')}", mapOf("external_source" to "imdb_id")) ?: return null
                (found.a(if (type == "movie") "movie_results" else "tv_results").firstOrNull() ?: found.a("movie_results").firstOrNull() ?: found.a("tv_results").firstOrNull())?.i("id")
            }
            else -> item.id.toIntOrNull()
        } ?: return null
        val o = LuxoTmdbLite.get("$type/$tmdbId", mapOf("language" to "ar", "append_to_response" to "credits")) ?: return null
        val credits = o.o("credits")
        val cast = credits?.a("cast").orEmpty().take(6)
        return LuxoTitleFacts(
            tmdbId = tmdbId,
            mediaType = type,
            title = o.s("title") ?: o.s("name") ?: item.name,
            year = (o.s("release_date") ?: o.s("first_air_date"))?.take(4)?.toIntOrNull(),
            countries = o.a("production_countries").mapNotNull { it.s("iso_3166_1") }.ifEmpty {
                (o["origin_country"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            }.take(3),
            genres = o.a("genres").mapNotNull { it.s("name") }.take(4),
            cast = cast.mapNotNull { it.s("name") },
            castPhotos = cast.map { LuxoTmdbLite.img(it.s("profile_path"), "w185") },
            directors = if (type == "movie") credits?.a("crew").orEmpty().filter { it.s("job") == "Director" }.mapNotNull { it.s("name") }.take(2)
            else o.a("created_by").mapNotNull { it.s("name") }.take(2),
            studios = (o.a("production_companies") + o.a("networks")).mapNotNull { it.s("name") }.distinct().take(3),
            backdrop = o.s("backdrop_path"),
            poster = o.s("poster_path"),
            runtime = o.i("runtime") ?: (o["episode_run_time"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() },
        )
    }
}

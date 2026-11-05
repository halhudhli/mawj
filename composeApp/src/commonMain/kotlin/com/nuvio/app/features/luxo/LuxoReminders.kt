package com.nuvio.app.features.luxo

import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class LuxoMovieReminder(val title: String, val backdrop: String? = null)

/**
 * «نبهني لما ينزل» for movies, and new work from followed actors. A background check (every 12 h on
 * Android) sends a notification when a movie is out digitally or an actor gets a new title.
 */
object LuxoReminders {
    private const val MOVIES_KEY = "movie_reminders_v1"
    private const val TMDB_KEY = "reminder_tmdb_key"
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSer = MapSerializer(String.serializer(), LuxoMovieReminder.serializer())

    val movies = mutableStateMapOf<String, LuxoMovieReminder>().apply {
        LuxoPrefsStorage.load(MOVIES_KEY)?.let { p -> runCatching { json.decodeFromString(mapSer, p) }.getOrNull()?.let { putAll(it) } }
    }

    private fun saveMovies() = LuxoPrefsStorage.save(MOVIES_KEY, json.encodeToString(mapSer, movies.toMap()))

    /** The background worker has no TMDB settings loaded, so the key is kept here too. */
    fun rememberApiKey() {
        TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() }?.let { LuxoPrefsStorage.save(TMDB_KEY, it) }
    }

    fun isMovieSet(tmdbId: String) = movies.containsKey(tmdbId)

    fun toggleMovie(tmdbId: String, title: String, backdrop: String?) {
        if (movies.containsKey(tmdbId)) movies.remove(tmdbId) else movies[tmdbId] = LuxoMovieReminder(title, backdrop)
        saveMovies()
        rememberApiKey()
    }

    private suspend fun tmdb(endpoint: String, query: Map<String, String> = emptyMap()): JsonObject? {
        val key = LuxoPrefsStorage.load(TMDB_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.parseToJsonElement(httpGetText(buildTmdbUrl(endpoint, key, query))) as? JsonObject }.getOrNull()
    }

    private fun today(): String = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate()

    /**
     * Checks everything once. [notify] gets (id, title, body, deepLink, backdrop).
     */
    suspend fun check(notify: suspend (String, String, String, String, String?) -> Unit) {
        val now = today()
        // Movies: out digitally (type 4), on disc (5) or on TV (6).
        movies.entries.toList().forEach { (id, reminder) ->
            val res = tmdb("movie/$id/release_dates") ?: return@forEach
            val dates = (res["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.flatMap { country ->
                (country["release_dates"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            }.filter { (it["type"] as? JsonPrimitive)?.intOrNull in setOf(4, 5, 6) }
                .mapNotNull { (it["release_date"] as? JsonPrimitive)?.contentOrNull?.take(10) }
            val first = dates.minOrNull() ?: return@forEach
            if (first <= now) {
                notify(
                    "luxo-movie-$id",
                    "${reminder.title} نزل 🎬",
                    "صار متوفر للمشاهدة الآن",
                    com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl("movie", "tmdb:$id"),
                    reminder.backdrop,
                )
                movies.remove(id)
                saveMovies()
            }
        }
        // Followed actors: anything new in their credits since the last check.
        LuxoFollowedPeople.ids.toList().forEach { personId ->
            val res = tmdb("person/$personId", mapOf("append_to_response" to "combined_credits", "language" to "ar")) ?: return@forEach
            val name = (res["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val credits = ((res["combined_credits"] as? JsonObject)?.get("cast") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val ids = credits.mapNotNull { c -> (c["id"] as? JsonPrimitive)?.intOrNull?.let { "${(c["media_type"] as? JsonPrimitive)?.contentOrNull}:$it" } }.toSet()
            val seenKey = "followed_seen_$personId"
            val seen = LuxoPrefsStorage.load(seenKey)?.split(",")?.toSet()
            if (seen != null) {
                (ids - seen).take(3).forEach { key ->
                    val c = credits.firstOrNull { "${(it["media_type"] as? JsonPrimitive)?.contentOrNull}:${(it["id"] as? JsonPrimitive)?.intOrNull}" == key } ?: return@forEach
                    val title = (c["title"] as? JsonPrimitive)?.contentOrNull ?: (c["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    val type = if (key.startsWith("tv")) "series" else "movie"
                    notify(
                        "luxo-person-$personId-$key",
                        "عمل جديد لـ $name ✨",
                        title,
                        com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl(type, "tmdb:" + key.substringAfter(":")),
                        (c["backdrop_path"] as? JsonPrimitive)?.contentOrNull?.let { "https://image.tmdb.org/t/p/w780$it" },
                    )
                }
            }
            LuxoPrefsStorage.save(seenKey, ids.joinToString(","))
        }
        // «حالة المسلسلات»: renewed, cancelled, ended, finale next.
        if (LuxoFeaturesRepository.current().seriesStatus) {
            LuxoSeriesStatus.check({ id -> tmdb("tv/$id") }) { id, title, body, link -> notify(id, title, body, link, null) }
        }
    }
}

/** Starts the periodic background check (Android: WorkManager every 12 h). */
expect fun luxoScheduleReminderChecks()

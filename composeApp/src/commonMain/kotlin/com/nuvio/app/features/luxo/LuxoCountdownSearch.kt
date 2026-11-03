package com.nuvio.app.features.luxo

import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A show picked for the countdown straight from TMDB (any country: anime, Turkish, Korean…). */
@Serializable
data class LuxoTmdbShow(
    val key: String,
    val title: String,
    val poster: String? = null,
    val year: String? = null,
    /** yyyy-MM-dd of the next episode, or null when none is announced. */
    val nextDate: String? = null,
    val nextLabel: String? = null,
    val checkedMs: Long = 0L,
)

/**
 * Countdown search across every series on TMDB, so weekly shows that aren't in the world schedule
 * (Detective Conan, Turkish dramas…) can still be picked for the Home strip.
 */
object LuxoTmdbShows {
    private const val KEY = "countdown_tmdb_shows_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = MapSerializer(String.serializer(), LuxoTmdbShow.serializer())

    /** Picked shows, kept on the device and refreshed every few hours. */
    val picked = mutableStateMapOf<String, LuxoTmdbShow>().apply {
        LuxoPrefsStorage.load(KEY)?.let { p -> runCatching { json.decodeFromString(ser, p) }.getOrNull()?.let { putAll(it) } }
    }

    private fun save() = LuxoPrefsStorage.save(KEY, json.encodeToString(ser, picked.toMap()))

    fun remember(show: LuxoTmdbShow) {
        picked[show.key] = show
        save()
    }

    /** Next episode of one show. */
    suspend fun details(id: Int, fallbackTitle: String, fallbackPoster: String?): LuxoTmdbShow? {
        val o = LuxoTmdbLite.get("tv/$id", mapOf("language" to "ar")) ?: return null
        val next = o.o("next_episode_to_air")
        val date = next?.s("air_date")
        val label = next?.let { n -> listOfNotNull(n.i("season_number")?.let { "S$it" }, n.i("episode_number")?.let { "E$it" }).joinToString("") }
        return LuxoTmdbShow(
            key = "tmdb:$id",
            title = o.s("name") ?: fallbackTitle,
            poster = LuxoTmdbLite.img(o.s("poster_path")) ?: fallbackPoster,
            year = o.s("first_air_date")?.take(4),
            nextDate = date,
            nextLabel = label?.ifBlank { null },
            checkedMs = luxoNowMs(),
        )
    }

    /** Every series matching [query] (any language), with its next episode date. */
    suspend fun search(query: String): List<LuxoTmdbShow> = coroutineScope {
        val res = LuxoTmdbLite.get("search/tv", mapOf("query" to LuxoTmdbLite.enc(query.trim()), "language" to "ar")) ?: return@coroutineScope emptyList()
        val hits = res.a("results").filter { it.s("original_language") != "ar" }.take(12)
        val gate = Semaphore(4)
        hits.map { h ->
            async {
                val id = h.i("id") ?: return@async null
                val title = h.s("name") ?: h.s("original_name") ?: return@async null
                val poster = LuxoTmdbLite.img(h.s("poster_path"))
                gate.withPermit { details(id, title, poster) } ?: LuxoTmdbShow("tmdb:$id", title, poster, h.s("first_air_date")?.take(4))
            }
        }.awaitAll().filterNotNull()
    }

    /** Refreshes the picked shows whose date is older than 6 hours (or already passed). */
    suspend fun refresh(keys: List<String>) {
        val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate().take(10)
        keys.filter { it.startsWith("tmdb:") }.forEach { k ->
            val cur = picked[k]
            val stale = cur == null || luxoNowMs() - cur.checkedMs > 6 * 3_600_000L || (cur.nextDate != null && cur.nextDate < today)
            if (!stale) return@forEach
            val id = k.removePrefix("tmdb:").toIntOrNull() ?: return@forEach
            details(id, cur?.title ?: "", cur?.poster)?.let { picked[k] = it }
        }
        save()
    }
}

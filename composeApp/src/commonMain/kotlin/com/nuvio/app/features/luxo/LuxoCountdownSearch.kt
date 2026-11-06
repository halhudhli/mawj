package com.nuvio.app.features.luxo

import androidx.compose.runtime.mutableStateMapOf
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
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
    /** When no date is announced: «قيد الإنتاج»، «مُجدَّد»، «انتهى»… */
    val status: String? = null,
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
        val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate().take(10)
        val next = o.o("next_episode_to_air")
        // A coming season often has its premiere date before its episodes are listed.
        val season = if (next?.s("air_date") == null) {
            o.a("seasons").filter { (it.i("season_number") ?: 0) > 0 }
                .firstOrNull { (it.s("air_date") ?: "") >= today }
        } else null
        val date = next?.s("air_date") ?: season?.s("air_date")
        val label = next?.let { n -> listOfNotNull(n.i("season_number")?.let { "S$it" }, n.i("episode_number")?.let { "E$it" }).joinToString("") }
            ?: season?.i("season_number")?.let { "S$it" }
        val status = when (o.s("status")) {
            "Returning Series" -> "مُجدَّد لموسم جديد · لم يُعلن الموعد بعد"
            "In Production" -> "قيد الإنتاج · لم يُعلن الموعد بعد"
            "Planned" -> "مُعلن عنه · لم يُحدَّد الموعد بعد"
            "Ended" -> "انتهى المسلسل"
            "Canceled" -> "أُلغي المسلسل"
            "Pilot" -> "حلقة تجريبية"
            else -> null
        }
        return LuxoTmdbShow(
            key = "tmdb:$id",
            title = o.s("name") ?: fallbackTitle,
            poster = LuxoTmdbLite.img(o.s("poster_path")) ?: fallbackPoster,
            year = o.s("first_air_date")?.take(4),
            nextDate = date,
            nextLabel = label?.ifBlank { null },
            checkedMs = luxoNowMs(),
            status = if (date == null) status else null,
        )
    }

    /** Every series matching [query] (any language), with its next episode date. */
    suspend fun search(query: String): List<LuxoTmdbShow> = coroutineScope {
        val q = query.trim()
        // An Arabic name often isn't on TMDB (Korean dramas, «The One Piece»…), so its English/original
        // names are asked from Gemini and searched too.
        val names = buildList {
            add(q)
            if (q.any { it in '\u0600'..'\u06FF' }) addAll(alternateNames(q))
        }.distinct()
        val found = names.map { n ->
            async { LuxoTmdbLite.get("search/tv", mapOf("query" to LuxoTmdbLite.enc(n), "language" to "ar"))?.a("results").orEmpty() }
        }.awaitAll().flatten()
        val hits = found.filter { it.s("original_language") != "ar" }.distinctBy { it.i("id") }
            .sortedByDescending { it.d("popularity") ?: 0.0 }.take(16)
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

    private val altCache = mutableMapOf<String, List<String>>()

    private suspend fun alternateNames(q: String): List<String> {
        altCache[q]?.let { return it }
        val key = com.nuvio.app.features.details.LuxoCompanionApi.loadKey() ?: return emptyList()
        val reply = com.nuvio.app.features.details.LuxoCompanionApi.ask(
            key,
            "المستخدم يبحث عن مسلسل أو أنمي بالاسم العربي: «$q». اذكر حتى ثلاثة أسماء محتملة له كما تُكتب في TMDB " +
                "(الاسم الإنجليزي ثم الاسم الأصلي بلغته ثم الاسم البديل)، كل اسم في سطر، بلا ترقيم ولا شرح.",
        )
        val out = reply.lines().map { it.trim().trimStart('-', '•', '*', ' ').trim('"', '«', '»') }
            .filter { it.isNotBlank() && it.length < 80 && !it.startsWith("تعذّر") && !it.startsWith("Gemini") }.take(3)
        altCache[q] = out
        return out
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

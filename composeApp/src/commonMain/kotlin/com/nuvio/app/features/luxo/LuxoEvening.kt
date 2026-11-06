package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Schedules (hour 0–23) or cancels (null) the daily evening check. */
expect fun luxoScheduleEvening(hour: Int?)

/**
 * «اقتراح المساء» and «ذكريات المشاهدة». While the app is open, picks are prepared from the viewer's
 * taste (recommendations for recently watched titles, scored by how often they recur and their rating,
 * skipping anything already watched or suggested); a daily background check then posts one pick at the
 * chosen hour, plus a memory when something was watched on this day in an earlier year.
 */
object LuxoEvening {
    private const val PICKS = "evening_picks"
    private const val MEMORIES = "evening_memories"
    private const val SUGGESTED = "evening_suggested"
    private const val PREPARED = "evening_prepared_day"
    private const val NOTIFIED = "evening_notified_day"
    private const val HOUR = "evening_hour"
    private const val SCHEDULED = "evening_scheduled_hour"

    fun hour(): Int = LuxoPrefsStorage.load(HOUR)?.toIntOrNull() ?: 20
    fun setHour(h: Int) { LuxoPrefsStorage.save(HOUR, h.toString()); reschedule() }

    private fun today(): Pair<String, String> {
        val d = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val md = "${(d.month.ordinal + 1).toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
        return "${d.year}-$md" to md
    }

    fun reschedule(force: Boolean = false) {
        val f = LuxoFeaturesRepository.current()
        val birthdays = f.actorBirthday && com.nuvio.app.features.details.LuxoBirthdays.all().isNotEmpty()
        val want = if (f.eveningPick || f.watchMemories || birthdays) hour() else null
        val key = want?.toString() ?: "off"
        if (!force && LuxoPrefsStorage.load(SCHEDULED) == key) return
        runCatching { luxoScheduleEvening(want) }
        LuxoPrefsStorage.save(SCHEDULED, key)
    }

    /** Runs in the app: refreshes picks and memories once a day. */
    suspend fun prepare() {
        LuxoReminders.rememberApiKey()
        reschedule()
        val (day, _) = today()
        if (LuxoPrefsStorage.load(PREPARED) == day) return
        val items = WatchedRepository.uiState.value.items
        if (items.isEmpty()) return
        // Memories: titles first watched on a past year's same day.
        val memories = items.groupBy { it.id }.mapNotNull { (_, es) ->
            val first = es.minByOrNull { it.markedAtEpochMs } ?: return@mapNotNull null
            val d = kotlin.time.Instant.fromEpochMilliseconds(first.markedAtEpochMs).toLocalDateTime(TimeZone.currentSystemDefault())
            val md = "${(d.month.ordinal + 1).toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
            "$md|${d.year}|${first.type}|${first.id}|${first.name.replace('|', ' ')}"
        }
        LuxoPrefsStorage.save(MEMORIES, memories.joinToString("\n"))
        // Picks from taste.
        if (LuxoFeaturesRepository.current().eveningPick) {
            val recent = items.sortedByDescending { it.markedAtEpochMs }.distinctBy { LuxoTasteFacts.keyOf(it) }.take(8)
            LuxoTasteFacts.ensure(recent, limit = recent.size)
            val seen = LuxoTasteFacts.facts.values.map { "${it.mediaType}:${it.tmdbId}" }.toSet()
            val suggested = LuxoPrefsStorage.load(SUGGESTED).orEmpty().split(',').toSet()
            val score = mutableMapOf<String, Double>()
            val info = mutableMapOf<String, String>()
            recent.forEach { w ->
                val f = LuxoTasteFacts.facts[LuxoTasteFacts.keyOf(w)] ?: return@forEach
                LuxoTmdbLite.get("${f.mediaType}/${f.tmdbId}/recommendations", mapOf("language" to "ar"))?.a("results").orEmpty().forEach { r ->
                    val id = r.i("id") ?: return@forEach
                    val type = r.s("media_type") ?: f.mediaType
                    val k = "$type:$id"
                    if (k in seen || k in suggested || r.s("original_language") == "ar") return@forEach
                    score[k] = (score[k] ?: 0.0) + 2.0 + (r.d("vote_average") ?: 5.0) / 2.0
                    if (k !in info) info[k] = "$type|$id|${(r.s("title") ?: r.s("name") ?: "").replace('|', ' ')}|${LuxoTmdbLite.img(r.s("backdrop_path"), "w780") ?: ""}|${f.title.replace('|', ' ')}"
                }
            }
            val picks = score.entries.sortedByDescending { it.value }.take(7).mapNotNull { info[it.key] }
            if (picks.isNotEmpty()) LuxoPrefsStorage.save(PICKS, picks.joinToString("\n"))
        }
        LuxoPrefsStorage.save(PREPARED, day)
    }

    /** Background side: what to post today (id, title, body, deep link, backdrop). Once per day. */
    fun dueNotifications(): List<List<String?>> {
        val f = LuxoFeaturesRepository.current()
        val (day, md) = today()
        if (LuxoPrefsStorage.load(NOTIFIED) == day) return emptyList()
        val out = mutableListOf<List<String?>>()
        if (f.eveningPick) {
            val picks = LuxoPrefsStorage.load(PICKS).orEmpty().lines().filter { it.isNotBlank() }
            picks.firstOrNull()?.split('|')?.takeIf { it.size >= 5 }?.let { p ->
                val (type, id, title, backdrop, because) = p
                out += listOf(
                    "evening_$day", "اقتراح المساء 🌙", "«$title» — لأنه تمت مشاهدة «$because»",
                    buildMetaDeepLinkUrl(if (type == "movie") "movie" else "series", "tmdb:$id"), backdrop.ifBlank { null },
                )
                LuxoPrefsStorage.save(PICKS, picks.drop(1).joinToString("\n"))
                val suggested = (LuxoPrefsStorage.load(SUGGESTED).orEmpty().split(',').filter { it.isNotBlank() } + "$type:$id").takeLast(300)
                LuxoPrefsStorage.save(SUGGESTED, suggested.joinToString(","))
            }
        }
        if (f.watchMemories) {
            val year = day.substringBefore('-').toIntOrNull() ?: 0
            LuxoPrefsStorage.load(MEMORIES).orEmpty().lines().map { it.split('|') }
                .filter { it.size >= 5 && it[0] == md && (it[1].toIntOrNull() ?: year) < year }
                .maxByOrNull { it[1] }
                ?.let { m ->
                    val years = year - (m[1].toIntOrNull() ?: year)
                    val ago = when (years) { 1 -> "قبل سنة"; 2 -> "قبل سنتين"; in 3..10 -> "قبل $years سنوات"; else -> "قبل $years سنة" }
                    out += listOf("memory_$day", "ذكريات المشاهدة 🎞️", "$ago بالضبط بدأت مشاهدة «${m[4]}»",
                        buildMetaDeepLinkUrl(if (m[2] == "movie") "movie" else "series", m[3]), null)
                }
        }
        if (f.actorBirthday) {
            com.nuvio.app.features.details.LuxoBirthdays.all().filter { it[1] == md }.forEach { b ->
                val pick = b[3].takeIf { it.isNotBlank() }
                out += listOf(
                    "birthday_${b[0]}_$day", "🎂 عيد ميلاد ${b[2]}",
                    if (pick != null) "اليوم عيد ميلاده، ويُقترح للاحتفال: «$pick»" else "اليوم عيد ميلاده",
                    if (b[5].isNotBlank()) buildMetaDeepLinkUrl(if (b[4] == "movie") "movie" else "series", b[5]) else "", null,
                )
            }
        }
        LuxoPrefsStorage.save(NOTIFIED, day)
        return out
    }
}

@Composable
fun LuxoEveningPreparer() {
    val f = rememberLuxoFeatures()
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    LaunchedEffect(f.eveningPick, f.watchMemories, watched.items.size > 0) {
        LuxoEvening.reschedule()
        if ((f.eveningPick || f.watchMemories) && watched.items.isNotEmpty()) runCatching { LuxoEvening.prepare() }
    }
}

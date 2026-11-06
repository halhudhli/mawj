package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.core.deeplink.handleAppUrl
import com.nuvio.app.features.addons.httpPostJson
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import com.nuvio.app.features.ratings.toUserRatingTarget
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private fun openMeta(type: String, id: String) {
    runCatching { handleAppUrl(buildMetaDeepLinkUrl(if (type == "movie") "movie" else "series", id)) }
}

@Composable
private fun Spinner(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(text, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun PickRow(poster: String?, title: String, line: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
            poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

// ================================================================ ⏳ «كم عندي وقت؟»

private data class TimePick(val type: String, val id: String, val title: String, val poster: String?, val minutes: Int, val line: String, val rank: Int)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoTimeBudgetDialog(onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf<Int?>(null) }
    val progress by remember { WatchProgressRepository.ensureLoaded(); WatchProgressRepository.uiState }.collectAsState()
    val library by remember { LibraryRepository.uiState }.collectAsState()
    var movieRuntimes by remember { mutableStateOf<Map<String, Int>?>(null) }
    IDialog("⏳ كم عندي وقت؟", onDismiss) {
        Text("يُختار الوقت المتاح، فتظهر أعمال تنتهي خلاله: ما بدأته، والحلقة التالية من مسلسلاتك، وأفلام من قائمتك.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 45, 60, 90, 120, 150, 180).forEach { m ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (minutes == m) Color(0xFF6F8FBF) else Color.White.copy(alpha = 0.08f))
                        .clickable { minutes = m }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(if (m < 60) "$m دقيقة" else if (m % 60 == 0) "${m / 60} ساعة" else "${m / 60} ساعة و${m % 60} د", color = Color.White, fontSize = 13.sp) }
            }
        }
        val t = minutes ?: return@IDialog
        LaunchedEffect(Unit) {
            if (movieRuntimes != null) return@LaunchedEffect
            val wanted = library.items.filter { it.type == "movie" }.take(30)
            movieRuntimes = wanted.mapNotNull { item ->
                val tmdb = when {
                    item.id.startsWith("tmdb:") -> item.id.removePrefix("tmdb:").toIntOrNull()
                    item.id.startsWith("tt") -> LuxoTmdbLite.get("find/${item.id}", mapOf("external_source" to "imdb_id"))?.a("movie_results")?.firstOrNull()?.i("id")
                    else -> null
                } ?: return@mapNotNull null
                LuxoTmdbLite.get("movie/$tmdb")?.i("runtime")?.takeIf { it > 0 }?.let { item.id to it }
            }.toMap()
        }
        val picks = remember(t, progress.entries, movieRuntimes) {
            val out = mutableListOf<TimePick>()
            val latest = progress.entries.filter { it.parentMetaId !in progress.hiddenContentIds }
                .groupBy { it.parentMetaId }.mapNotNull { (_, es) -> es.maxByOrNull { it.lastUpdatedEpochMs } }
            latest.forEach { e ->
                if (e.durationMs <= 0) return@forEach
                val epMin = (e.durationMs / 60_000L).toInt().coerceAtLeast(1)
                if (!e.isCompleted) {
                    val left = ((e.durationMs - e.lastPositionMs) / 60_000L).toInt().coerceAtLeast(1)
                    if (left <= t) out += TimePick(e.parentMetaType, e.parentMetaId, e.title, e.poster, left,
                        (if (e.seasonNumber != null) "إكمال الحلقة ${e.episodeNumber} · " else "إكمال الفيلم · ") + "متبقٍ $left دقيقة", 0)
                } else if (e.seasonNumber != null && epMin <= t) {
                    val n = (t / epMin).coerceAtMost(8)
                    out += TimePick(e.parentMetaType, e.parentMetaId, e.title, e.poster, epMin * n,
                        "الحلقة التالية" + (if (n > 1) " وما بعدها: $n حلقات تقريباً" else "") + " (نحو $epMin دقيقة للحلقة)", 1)
                }
            }
            val runtimes = movieRuntimes.orEmpty()
            library.items.filter { it.type == "movie" }.forEach { m ->
                val r = runtimes[m.id] ?: return@forEach
                if (r <= t && out.none { it.id == m.id }) out += TimePick("movie", m.id, m.name, m.poster, r, "فيلم من قائمتك · $r دقيقة", 2)
            }
            out.sortedWith(compareBy<TimePick>({ it.rank }, { -it.minutes }))
        }
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (movieRuntimes == null) Spinner("تُحسب مدد الأفلام في قائمتك…")
            if (picks.isEmpty() && movieRuntimes != null) Text("لا يوجد ما ينتهي خلال هذا الوقت. يمكن تجربة مدة أطول.", color = Color.White.copy(alpha = 0.7f))
            picks.forEach { p -> PickRow(p.poster, p.title, p.line) { openMeta(p.type, p.id); onDismiss() } }
        }
    }
}

// ================================================================ ⭐ «تقييم بعد المشاهدة»

object LuxoRateAfter {
    data class Pending(val id: String, val type: String, val title: String, val poster: String?, val releaseInfo: String?)

    var pending by mutableStateOf<Pending?>(null)
    private fun key() = "rate_after_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}"

    /** id → 1..10, kept for «ذوقي» even without a rating provider. */
    fun all(): Map<String, Int> = LuxoPrefsStorage.load(key()).orEmpty().lines().filter { '=' in it }
        .mapNotNull { l -> l.substringAfter('=').toIntOrNull()?.let { l.substringBefore('=') to it } }.toMap()

    private fun save(id: String, rating: Int) {
        val m = all().toMutableMap(); m[id] = rating
        LuxoPrefsStorage.save(key(), m.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    /** Called when playback completes; offers a rating for movies and for a series' last episode. */
    fun onCompleted(item: WatchedItem) {
        if (!LuxoFeaturesRepository.current().rateAfter || LuxoKids.isOn()) return
        if (item.id in all()) return
        val isMovie = item.season == null
        if (!isMovie) {
            val meta = com.nuvio.app.features.details.MetaDetailsRepository.peek("series", item.id) ?: return
            val last = meta.videos.filter { (it.season ?: 0) > 0 && it.episode != null }
                .maxWithOrNull(compareBy({ it.season }, { it.episode })) ?: return
            if (last.season != item.season || last.episode != item.episode) return
        }
        pending = Pending(item.id, if (isMovie) "movie" else "series", item.name, item.poster, item.releaseInfo)
    }

    fun rate(p: Pending, rating: Int) {
        save(p.id, rating)
        runCatching {
            val repo = com.nuvio.app.features.ratings.UserRatingsRepository
            if (repo.hasRatingProvider()) {
                com.nuvio.app.features.tracking.buildTrackingMediaReference(
                    contentType = p.type, parentMetaId = p.id, title = p.title, releaseInfo = p.releaseInfo,
                ).toUserRatingTarget()?.let { repo.rate(it, rating) }
            }
        }
        pending = null
    }
}

@Composable
fun LuxoRateAfterHost() {
    val p = LuxoRateAfter.pending ?: return
    if (LuxoIdle.playerOpen) return
    IDialog("⭐ ما رأيك؟", onDismiss = { LuxoRateAfter.pending = null }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f))) {
                p.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (p.type == "movie") "انتهى الفيلم" else "انتهى المسلسل", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Triple("😍", "رائع", 10), Triple("👍", "جيد", 8), Triple("😐", "عادي", 5), Triple("👎", "لم يعجبني", 2)).forEach { (e, label, r) ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { LuxoRateAfter.rate(p, r) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(e, fontSize = 34.sp)
                    Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
        Text("يُحسّن التقييم اقتراحات «ذوقي»، ويُرسل إلى Trakt إن كان مربوطاً.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
    }
}

// ================================================================ 🌸 «تقويم الأنمي الموسمي» (AniList, no key)

private data class SeasonAnime(val title: String, val english: String?, val cover: String?, val day: Int?, val time: String?, val episode: Int?, val score: Int?, val genres: List<String>)

private val AR_DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

private suspend fun loadSeason(): List<SeasonAnime> {
    val now = kotlin.time.Clock.System.now()
    val local = now.toLocalDateTimeSafe()
    val season = when (local.first) { in 1..3 -> "WINTER"; in 4..6 -> "SPRING"; in 7..9 -> "SUMMER"; else -> "FALL" }
    val query = """
        query { Page(perPage: 50) { media(season: $season, seasonYear: ${local.second}, type: ANIME, format_in: [TV, ONA], sort: POPULARITY_DESC, isAdult: false) {
          title { romaji english } coverImage { large } averageScore genres
          nextAiringEpisode { airingAt episode } } } }
    """.trimIndent()
    val body = "{\"query\":" + Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), JsonPrimitive(query)) + "}"
    val root = runCatching { Json.parseToJsonElement(httpPostJson("https://graphql.anilist.co", body)) as? JsonObject }.getOrNull() ?: return emptyList()
    val media = (root["data"] as? JsonObject)?.o("Page")?.a("media").orEmpty()
    return media.map { m ->
        val next = m.o("nextAiringEpisode")
        val at = (next?.get("airingAt") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val dt = at?.let { kotlin.time.Instant.fromEpochSeconds(it).toDayAndTime() }
        SeasonAnime(
            title = m.o("title")?.s("romaji") ?: "",
            english = m.o("title")?.s("english"),
            cover = m.o("coverImage")?.s("large"),
            day = dt?.first,
            time = dt?.second,
            episode = (next?.get("episode") as? JsonPrimitive)?.intOrNull,
            score = (m["averageScore"] as? JsonPrimitive)?.intOrNull,
            genres = (m["genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
        )
    }.filter { it.title.isNotBlank() }
}

/** (month, year) in the device's zone. */
private fun kotlin.time.Instant.toLocalDateTimeSafe(): Pair<Int, Int> {
    val d = toLocalDateTime(TimeZone.currentSystemDefault())
    return (d.month.ordinal + 1) to d.year
}

/** (0 = Monday … 6 = Sunday, "HH:mm") in the device's zone. */
private fun kotlin.time.Instant.toDayAndTime(): Pair<Int, String> {
    val d = toLocalDateTime(TimeZone.currentSystemDefault())
    return d.dayOfWeek.ordinal to "${d.hour.toString().padStart(2, '0')}:${d.minute.toString().padStart(2, '0')}"
}

@Composable
fun LuxoAnimeSeasonDialog(onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<SeasonAnime>?>(null) }
    var day by remember { mutableStateOf(-1) }
    var opening by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { list = loadSeason() }
    LaunchedEffect(opening) {
        val t = opening ?: return@LaunchedEffect
        val hit = LuxoTmdbLite.get("search/tv", mapOf("query" to t))?.a("results")?.firstOrNull()?.i("id")
        if (hit != null) openMeta("series", "tmdb:$hit")
        opening = null
        if (hit != null) onDismiss()
    }
    IDialog("🌸 تقويم الأنمي الموسمي", onDismiss) {
        val l = list
        if (l == null) { Spinner("يُجلب موسم الأنمي الحالي…"); return@IDialog }
        if (l.isEmpty()) { Text("تعذّر جلب التقويم الآن.", color = Color.White.copy(alpha = 0.7f)); return@IDialog }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((-1..6).toList()) { d ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (day == d) Color(0xFFE08AA8) else Color.White.copy(alpha = 0.08f))
                        .clickable { day = d }.padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(if (d < 0) "الكل" else AR_DAYS[d], color = Color.White, fontSize = 13.sp) }
            }
        }
        val shown = l.filter { day < 0 || it.day == day }.sortedWith(compareBy({ it.day ?: 9 }, { it.time ?: "" }))
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) Text("لا شيء يُعرض في هذا اليوم.", color = Color.White.copy(alpha = 0.6f))
            shown.forEach { a ->
                val line = buildString {
                    if (a.day != null) append(AR_DAYS[a.day]).append(" ").append(a.time ?: "")
                    a.episode?.let { append(" · الحلقة ").append(it) }
                    a.score?.let { append(" · ").append(it).append("٪") }
                }
                PickRow(a.cover, a.english ?: a.title, line.ifBlank { a.genres.take(3).joinToString(" · ") }) { opening = a.english ?: a.title }
            }
            if (opening != null) Spinner("يُفتح العمل…")
        }
        Text("المصدر: AniList · الأوقات بتوقيت جهازك", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
    }
}

// ================================================================ 👨‍👩‍👧 «ماذا نشاهد معاً؟»

private data class TogetherPick(val id: String, val type: String, val name: String, val poster: String?, val count: Int, val who: List<String>)

private val looseJson = Json { ignoreUnknownKeys = true }

private fun libraryOf(profile: Int): List<Triple<String, String, Pair<String, String?>>> = runCatching {
    val payload = com.nuvio.app.features.library.LibraryStorage.loadPayload(profile) ?: return emptyList()
    com.nuvio.app.features.library.LibraryStoragePayloadCodec.decode(payload).items.map { Triple(it.id, it.type, it.name to it.poster) }
}.getOrDefault(emptyList())

private fun watchedIdsOf(profile: Int): Set<String> = runCatching {
    val payload = com.nuvio.app.features.watched.WatchedStorage.loadPayload(profile) ?: return emptySet()
    val root = looseJson.parseToJsonElement(payload) as? JsonObject ?: return emptySet()
    (root["items"] as? JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty()
}.getOrDefault(emptySet())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoWatchTogetherDialog(onDismiss: () -> Unit) {
    val state by remember { com.nuvio.app.features.profiles.ProfileRepository.state }.collectAsState()
    val profiles = state.profiles
    val chosen = remember { mutableStateListOf(com.nuvio.app.features.profiles.ProfileRepository.activeProfileId) }
    IDialog("👨‍👩‍👧 ماذا نشاهد معاً؟", onDismiss) {
        Text("تُختار الملفات الحاضرة، فتظهر الأعمال المحفوظة في قوائمهم ولم يشاهدها أحد منهم، والأكثر اشتراكاً أولاً.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profiles.forEach { p ->
                val on = p.profileIndex in chosen
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color(0xFF6F8FBF) else Color.White.copy(alpha = 0.08f))
                        .clickable { if (on) chosen.remove(p.profileIndex) else chosen.add(p.profileIndex) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text((if (on) "✓ " else "") + p.name.ifBlank { "ملف ${p.profileIndex}" }, color = Color.White, fontSize = 13.sp) }
            }
        }
        if (profiles.size < 2) Text("يلزم ملفان على الأقل في التطبيق.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        val names = profiles.associate { it.profileIndex to it.name.ifBlank { "ملف ${it.profileIndex}" } }
        val picks = remember(chosen.toList()) {
            val sel = chosen.toList()
            if (sel.size < 2) return@remember emptyList<TogetherPick>()
            val watched = sel.flatMap { watchedIdsOf(it) }.toSet()
            val byId = mutableMapOf<String, TogetherPick>()
            sel.forEach { pid ->
                libraryOf(pid).forEach { (id, type, np) ->
                    if (id in watched) return@forEach
                    val cur = byId[id]
                    byId[id] = if (cur == null) TogetherPick(id, type, np.first, np.second, 1, listOf(names[pid].orEmpty()))
                    else cur.copy(count = cur.count + 1, who = cur.who + names[pid].orEmpty())
                }
            }
            byId.values.sortedByDescending { it.count }.take(40)
        }
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chosen.size >= 2 && picks.isEmpty()) Text("لا توجد أعمال محفوظة في قوائم هذه الملفات.", color = Color.White.copy(alpha = 0.7f))
            picks.forEach { p ->
                val line = if (p.count == chosen.size) "في قوائم الجميع ✨" else "في قائمة: " + p.who.joinToString("، ")
                PickRow(p.poster, p.name, line) { openMeta(p.type, p.id); onDismiss() }
            }
        }
    }
}

// ================================================================ 🧠 «اختبار الحلقة»

data class LuxoQuizQuestion(val q: String, val options: List<String>, val answer: Int)

object LuxoQuiz {
    suspend fun make(title: String, season: Int?, episode: Int?): List<LuxoQuizQuestion>? {
        val key = LuxoCompanionApi.loadKey() ?: return null
        val what = if (season != null && episode != null) "الحلقة $episode من الموسم $season من «$title»" else "«$title»"
        val prompt = "اكتب ثلاثة أسئلة اختيار من متعدد عن أحداث $what التي انتهت مشاهدتها للتو، بالعربية الفصحى، " +
            "لكل سؤال ثلاثة خيارات وإجابة واحدة صحيحة، وبلا أي حرق لما بعدها. أعد JSON فقط بهذا الشكل: " +
            "{\"questions\":[{\"q\":\"…\",\"options\":[\"…\",\"…\",\"…\"],\"answer\":0}]}"
        val obj = LuxoTmdbLite.jsonBlock(LuxoCompanionApi.ask(key, prompt, search = true)) ?: return emptyList()
        return obj.a("questions").mapNotNull { q ->
            val text = q.s("q") ?: return@mapNotNull null
            val opts = (q["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val ans = q.i("answer") ?: return@mapNotNull null
            if (opts.size < 2 || ans !in opts.indices) null else LuxoQuizQuestion(text, opts, ans)
        }.take(3)
    }
}

@Composable
fun LuxoQuizDialog(title: String, season: Int?, episode: Int?, onDismiss: () -> Unit) {
    var questions by remember { mutableStateOf<List<LuxoQuizQuestion>?>(null) }
    var noKey by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(Unit) {
        val r = LuxoQuiz.make(title, season, episode)
        if (r == null) noKey = true else questions = r
    }
    IDialog("🧠 اختبار الحلقة", onDismiss) {
        val qs = questions
        when {
            noKey -> Text("يلزم مفتاح Gemini (من «الإعدادات ← المزايا ← مفتاح Gemini»).", color = Color.White.copy(alpha = 0.75f))
            qs == null -> Spinner("تُجهَّز الأسئلة…")
            qs.isEmpty() -> Text("تعذّر تجهيز أسئلة لهذه الحلقة.", color = Color.White.copy(alpha = 0.75f))
            else -> Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                qs.forEachIndexed { qi, q ->
                    Text("${qi + 1}. ${q.q}", color = Color.White, fontWeight = FontWeight.Bold)
                    q.options.forEachIndexed { oi, o ->
                        val picked = answers[qi]
                        val bg = when {
                            picked == null -> Color.White.copy(alpha = 0.06f)
                            oi == q.answer -> Color(0xFF2E7D4F)
                            oi == picked -> Color(0xFF9B2C2C)
                            else -> Color.White.copy(alpha = 0.04f)
                        }
                        Text(
                            o, color = Color.White,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
                                .clickable(enabled = picked == null) { answers[qi] = oi }.padding(12.dp),
                        )
                    }
                }
                if (answers.size == qs.size) {
                    val score = qs.indices.count { answers[it] == qs[it].answer }
                    Text(
                        "النتيجة: $score من ${qs.size} " + when (score) { qs.size -> "🏆 تركيز تام!"; 0 -> "😅 ربما تُعاد الحلقة؟"; else -> "👏" },
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ================================================================ 🏆 «ترتيبي الخاص»

object LuxoTiers {
    val TIERS = listOf("S" to Color(0xFFE5484D), "A" to Color(0xFFF08C3A), "B" to Color(0xFFE6C34A), "C" to Color(0xFF59B36B), "D" to Color(0xFF5B8DEF))
    private fun key() = "tiers_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}"
    val map = mutableStateMapOf<String, String>()
    private var loadedFor = -1

    fun ensure() {
        val pid = com.nuvio.app.features.profiles.ProfileRepository.activeProfileId
        if (loadedFor == pid) return
        loadedFor = pid
        map.clear()
        LuxoPrefsStorage.load(key()).orEmpty().lines().filter { '=' in it }.forEach { map[it.substringBefore('=')] = it.substringAfter('=') }
    }

    fun set(id: String, tier: String?) {
        if (tier == null) map.remove(id) else map[id] = tier
        LuxoPrefsStorage.save(key(), map.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}

@Composable
fun LuxoTierListDialog(onDismiss: () -> Unit) {
    LuxoTiers.ensure()
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    val titles = remember(watched.items) {
        watched.items.sortedByDescending { it.markedAtEpochMs }.distinctBy { it.id }.filter { it.poster != null }.take(150)
    }
    var selected by remember { mutableStateOf<WatchedItem?>(null) }
    IDialog("🏆 ترتيبي الخاص", onDismiss) {
        Text("يُضغط على ملصق ثم على المرتبة المناسبة له.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LuxoTiers.TIERS.forEach { (tier, color) ->
                val inTier = titles.filter { LuxoTiers.map[it.id] == tier }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f))
                        .then(if (selected != null) Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp)) else Modifier)
                        .clickable(enabled = selected != null) { selected?.let { LuxoTiers.set(it.id, tier) }; selected = null }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                        Text(tier, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    LazyRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(inTier, key = { it.id }) { w -> TierPoster(w, selected?.id == w.id) { selected = if (selected?.id == w.id) null else w } }
                    }
                }
            }
            val rest = titles.filter { LuxoTiers.map[it.id] == null }
            if (rest.isNotEmpty()) {
                Text("بلا ترتيب (${rest.size})", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rest, key = { it.id }) { w -> TierPoster(w, selected?.id == w.id) { selected = if (selected?.id == w.id) null else w } }
                }
            }
            selected?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("المحدد: ${s.name}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (LuxoTiers.map[s.id] != null) Text("إزالة من الترتيب", color = Color(0xFFFFB4A9), fontSize = 13.sp,
                        modifier = Modifier.clickable { LuxoTiers.set(s.id, null); selected = null }.padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun TierPoster(w: WatchedItem, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) { AsyncImage(w.poster, w.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

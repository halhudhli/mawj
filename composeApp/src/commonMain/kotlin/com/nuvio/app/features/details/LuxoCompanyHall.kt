package com.nuvio.app.features.details

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.IDialog
import com.nuvio.app.features.luxo.LuxoCredit
import com.nuvio.app.features.luxo.LuxoOverlays
import com.nuvio.app.features.luxo.LuxoTmdbLite
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.luxo.luxoLiteMode
import com.nuvio.app.features.luxo.rememberLuxoWatchedMatcher
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// =====================================================================================
//  «قاعة العرض» — the cinema-hall company / network page.
// =====================================================================================

private val HGold = Color(0xFFEDCB78)
private val HCard = Color(0xFF10131C)
private val HDefaultAccent = Color(0xFF6F8FBF)

private data class HallInfo(
    val name: String,
    val logo: String?,
    val country: String?,
    val hq: String?,
    val parentId: Int?,
    val parentName: String?,
    val description: String?,
)

private data class HallWork(val credit: LuxoCredit, val genres: List<Int>)
private data class HallEra(val decade: Int, val count: Int, val best: LuxoCredit?)
private data class HallPerson(val id: Int, val name: String, val photo: String?, val count: Int)
private data class HallUniverse(val id: Int, val name: String, val poster: String?, val backdrop: String?, val count: Int)
private data class HallStudio(val founded: String?, val founder: String?, val fact: String?, val awards: List<Pair<String, String>>, val sisters: List<String>)

private data class HallData(
    val info: HallInfo?,
    val media: String,
    val catalog: List<HallWork>,
    val movieTotal: Int,
    val tvTotal: Int,
    val genres: Map<Int, String>,
    val top10: List<LuxoCredit>,
    val eras: List<HallEra>,
    val universes: List<HallUniverse>,
    val stars: List<HallPerson>,
    val directors: List<HallPerson>,
    val upcoming: List<LuxoCredit>,
)

private enum class HallSort(val label: String, val key: String) {
    POPULAR("الأشهر", "popularity.desc"), RATING("الأعلى تقييماً", "vote_average.desc"), NEWEST("الأحدث", "date.desc"), OLDEST("الأقدم", "date.asc")
}

/** Mood → TMDB genre ids (movie ids; the TV list shares most of them). */
private val HallMoods = listOf(
    "😂 مضحك" to "35",
    "😱 مرعب" to "27",
    "🔪 مشوّق" to "53|80|9648",
    "💔 مؤثر" to "18|10749",
    "🚀 ملحمي" to "12|14|878|10759|10765",
    "👨‍👩‍👧 عائلي" to "10751|16",
)

private object HallApi {
    private fun today() = CurrentDateProvider.todayIsoDate()

    private fun credit(r: JsonObject, media: String): HallWork? {
        val id = r.i("id") ?: return null
        val c = LuxoCredit(
            id = id,
            mediaType = media,
            title = r.s("title") ?: r.s("name") ?: return null,
            poster = LuxoTmdbLite.img(r.s("poster_path"), "w342"),
            backdrop = LuxoTmdbLite.img(r.s("backdrop_path"), "w780"),
            date = r.s("release_date") ?: r.s("first_air_date"),
            rating = r.d("vote_average") ?: 0.0,
            votes = r.i("vote_count") ?: 0,
            popularity = r.d("popularity") ?: 0.0,
            role = null,
            episodes = null,
            enTitle = r.s("original_title") ?: r.s("original_name"),
        )
        val g = (r["genre_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        return HallWork(c, g)
    }

    suspend fun discover(kind: String, id: Int, media: String, extra: Map<String, String>): Pair<List<HallWork>, Int> {
        val q = buildMap {
            put("language", "ar")
            put(if (kind == "network") "with_networks" else "with_companies", id.toString())
            putAll(extra)
        }
        val o = LuxoTmdbLite.get("discover/$media", q) ?: return emptyList<HallWork>() to 0
        return o.a("results").mapNotNull { credit(it, media) } to (o.i("total_results") ?: 0)
    }

    fun dateField(media: String) = if (media == "movie") "primary_release_date" else "first_air_date"

    suspend fun grid(kind: String, id: Int, media: String, sort: HallSort, floor: Double, genres: String?, decade: Int?, page: Int): Pair<List<LuxoCredit>, Int> {
        val df = dateField(media)
        val extra = buildMap {
            put("page", page.toString())
            put("sort_by", sort.key.replace("date", df))
            if (sort == HallSort.RATING) put("vote_count.gte", "150")
            if (floor > 0) {
                put("vote_average.gte", floor.toString())
                if (sort != HallSort.RATING) put("vote_count.gte", "20")
            }
            if (sort == HallSort.NEWEST) put("$df.lte", today())
            genres?.let { put("with_genres", it) }
            decade?.let { put("$df.gte", "$it-01-01"); put("$df.lte", "${it + 9}-12-31") }
        }
        val (l, t) = discover(kind, id, media, extra)
        return l.map { it.credit } to t
    }

    suspend fun load(kind: String, id: Int): HallData = coroutineScope {
        val infoJob = async { info(kind, id) }
        val movieJob = async { if (kind == "network") emptyList<HallWork>() to 0 else catalog(kind, id, "movie") }
        val tvJob = async { catalog(kind, id, "tv") }
        val genresJob = async { genreNames() }
        val (movies, movieTotal) = movieJob.await()
        val (shows, tvTotal) = tvJob.await()
        val media = if (kind == "network" || tvTotal > movieTotal * 1.4) "tv" else "movie"
        val main = if (media == "movie") movies else shows
        val df = dateField(media)

        val topJob = async {
            discover(kind, id, media, mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "400")).first.map { it.credit }.take(10)
        }
        val erasJob = async {
            (1920..2020 step 10).map { dec ->
                async {
                    val (l, t) = discover(kind, id, media, mapOf("sort_by" to "popularity.desc", "$df.gte" to "$dec-01-01", "$df.lte" to "${dec + 9}-12-31"))
                    HallEra(dec, t, l.firstOrNull { it.credit.backdrop != null || it.credit.poster != null }?.credit)
                }
            }.awaitAll().filter { it.count > 0 }
        }
        val upcomingJob = async {
            discover(kind, id, media, mapOf("sort_by" to "$df.asc", "$df.gte" to tomorrow())).first.map { it.credit }.filter { it.date != null }.take(12)
        }
        val peopleJob = async { people(main.map { it.credit }.take(10), media) }
        val universesJob = async { if (media == "movie") universes(main.map { it.credit }.take(20)) else emptyList() }
        val (stars, directors) = peopleJob.await()
        HallData(
            info = infoJob.await(),
            media = media,
            catalog = movies + shows,
            movieTotal = movieTotal,
            tvTotal = tvTotal,
            genres = genresJob.await(),
            top10 = topJob.await(),
            eras = erasJob.await(),
            universes = universesJob.await(),
            stars = stars,
            directors = directors,
            upcoming = upcomingJob.await(),
        )
    }

    private fun tomorrow(): String {
        val t = today()
        val days = hallDaysFromIso(t) ?: return t
        return hallIsoFromDays(days + 1)
    }

    private suspend fun info(kind: String, id: Int): HallInfo? {
        val o = LuxoTmdbLite.get("$kind/$id") ?: return null
        val parent = o.o("parent_company")
        return HallInfo(
            name = o.s("name") ?: return null,
            logo = LuxoTmdbLite.img(o.s("logo_path"), "w500"),
            country = o.s("origin_country"),
            hq = o.s("headquarters"),
            parentId = parent?.i("id"),
            parentName = parent?.s("name"),
            description = o.s("description"),
        )
    }

    private suspend fun catalog(kind: String, id: Int, media: String): Pair<List<HallWork>, Int> = coroutineScope {
        val first = discover(kind, id, media, mapOf("sort_by" to "popularity.desc", "page" to "1"))
        if (first.second <= 20) return@coroutineScope first
        val more = (2..3).map { p -> async { discover(kind, id, media, mapOf("sort_by" to "popularity.desc", "page" to p.toString())).first } }.awaitAll().flatten()
        (first.first + more).distinctBy { it.credit.id } to first.second
    }

    private suspend fun genreNames(): Map<Int, String> = coroutineScope {
        listOf("movie", "tv").map { m ->
            async { LuxoTmdbLite.get("genre/$m/list", mapOf("language" to "ar"))?.a("genres").orEmpty().mapNotNull { g -> g.i("id")?.let { it to (g.s("name") ?: "") } } }
        }.awaitAll().flatten().toMap()
    }

    private suspend fun people(works: List<LuxoCredit>, media: String): Pair<List<HallPerson>, List<HallPerson>> = coroutineScope {
        val details = works.map { w ->
            async {
                if (media == "movie") LuxoTmdbLite.get("movie/${w.id}/credits") else LuxoTmdbLite.get("tv/${w.id}", mapOf("append_to_response" to "aggregate_credits"))
            }
        }.awaitAll().filterNotNull()
        val stars = mutableMapOf<Int, HallPerson>()
        val makers = mutableMapOf<Int, HallPerson>()
        details.forEach { d ->
            val cast = if (media == "movie") d.a("cast") else d.o("aggregate_credits")?.a("cast").orEmpty()
            cast.take(8).forEach { p ->
                val pid = p.i("id") ?: return@forEach
                val old = stars[pid]
                stars[pid] = HallPerson(pid, p.s("name") ?: return@forEach, LuxoTmdbLite.img(p.s("profile_path"), "w185"), (old?.count ?: 0) + 1)
            }
            val dirs = if (media == "movie") d.a("crew").filter { it.s("job") == "Director" } else d.a("created_by")
            dirs.forEach { p ->
                val pid = p.i("id") ?: return@forEach
                val old = makers[pid]
                makers[pid] = HallPerson(pid, p.s("name") ?: return@forEach, LuxoTmdbLite.img(p.s("profile_path"), "w185"), (old?.count ?: 0) + 1)
            }
        }
        stars.values.sortedByDescending { it.count }.take(14) to makers.values.sortedByDescending { it.count }.take(10)
    }

    private suspend fun universes(works: List<LuxoCredit>): List<HallUniverse> = coroutineScope {
        val cols = works.map { w -> async { LuxoTmdbLite.get("movie/${w.id}", mapOf("language" to "ar"))?.o("belongs_to_collection") } }.awaitAll().filterNotNull()
        cols.groupBy { it.i("id") ?: -1 }.filterKeys { it > 0 }.map { (cid, list) ->
            val c = list.first()
            HallUniverse(cid, c.s("name") ?: "", LuxoTmdbLite.img(c.s("poster_path"), "w342"), LuxoTmdbLite.img(c.s("backdrop_path"), "w780"), list.size)
        }.sortedByDescending { it.count }
    }

    suspend fun collectionParts(id: Int): List<LuxoCredit> =
        LuxoTmdbLite.get("collection/$id", mapOf("language" to "ar"))?.a("parts").orEmpty().mapNotNull { credit(it, "movie")?.credit }.sortedBy { it.date ?: "9999" }

    /** Studio card, awards shelf and sister companies (Gemini, cached per company). */
    suspend fun studio(kind: String, id: Int, name: String): HallStudio? {
        val path = "luxo/hall/studio_${kind}_$id.json"
        val cached = AppFileCache.read(path)
        val text = cached ?: run {
            val key = LuxoCompanionApi.loadKey() ?: return null
            val answer = runCatching {
                LuxoCompanionApi.ask(
                    key,
                    """
                    عن ${if (kind == "network") "الشبكة التلفزيونية" else "شركة الإنتاج"} «$name» أجب بـ JSON فقط بالعربية الفصحى:
                    {"founded":"سنة التأسيس","founder":"المؤسس","fact":"حقيقة لافتة في جملة أو جملتين","awards":[{"title":"اسم الجائزة","note":"العدد أو أبرز عمل فاز بها"}],"sisters":["أسماء الشركات الشقيقة أو التابعة بالإنجليزية"]}
                    أبرز الجوائز حتى 6، والشركات حتى 8. لا تخترع؛ اترك الحقل فارغاً إن لم تتأكد.
                    """.trimIndent(),
                    search = true,
                )
            }.getOrNull() ?: return null
            answer.also { if (LuxoTmdbLite.jsonBlock(it) != null) AppFileCache.write(path, it) }
        }
        val o = LuxoTmdbLite.jsonBlock(text) ?: return null
        return HallStudio(
            founded = o.s("founded"),
            founder = o.s("founder"),
            fact = o.s("fact"),
            awards = o.a("awards").mapNotNull { a -> a.s("title")?.let { it to (a.s("note") ?: "") } },
            sisters = (o["sisters"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } },
        )
    }

    suspend fun findCompany(name: String): Pair<Int, String>? =
        LuxoTmdbLite.get("search/company", mapOf("query" to LuxoTmdbLite.enc(name)))?.a("results")?.firstOrNull()?.let { r -> r.i("id")?.let { it to (r.s("name") ?: name) } }
}

// ------------------------------------------------------------------ dates

internal fun hallDaysFromIso(iso: String?): Long? {
    val p = iso?.take(10)?.split("-") ?: return null
    if (p.size < 3) return null
    var y = p[0].toLongOrNull() ?: return null
    val m = p[1].toLongOrNull() ?: return null
    val d = p[2].toLongOrNull() ?: return null
    if (m <= 2) y -= 1
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (m + 9) % 12
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun hallIsoFromDays(days: Long): String {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

private fun hallEraName(decade: Int): String = when (decade) {
    2020 -> "العشرينات الجديدة"
    2010 -> "العقد الثاني"
    2000 -> "الألفية"
    1990 -> "التسعينات"
    1980 -> "الثمانينات"
    1970 -> "السبعينات"
    1960 -> "الستينات"
    1950 -> "الخمسينات"
    1940 -> "الأربعينات"
    1930 -> "الثلاثينات"
    else -> "العشرينات"
}

// ------------------------------------------------------------------ logo tint

/** Picks the most colourful tone of a logo, so the page can be tinted by it. */
private object HallTint {
    val colors = mutableStateMapOf<String, Color>()

    fun measure(url: String, painter: Painter) {
        if (colors.containsKey(url)) return
        runCatching {
            val w = 40
            val h = 20
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                with(painter) { draw(Size(w.toFloat(), h.toFloat())) }
            }
            val px = IntArray(w * h)
            bitmap.readPixels(px)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            px.forEach { p ->
                val a = (p ushr 24) and 0xFF
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                if (a < 80 || maxOf(pr, pg, pb) - minOf(pr, pg, pb) < 50) return@forEach
                r += pr; g += pg; b += pb; n++
            }
            colors[url] = if (n < 6) HDefaultAccent else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }.onFailure { colors[url] = HDefaultAccent }
    }
}

// ------------------------------------------------------------------ page

private data class HallStop(val kind: String, val id: Int, val name: String)

/** The «قاعة العرض» company page. Sister / parent companies open in place; Back returns. */
@Composable
internal fun LuxoCompanyHall(
    kind: String,
    id: Int,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    val stack = remember(id) { mutableStateListOf(HallStop(kind, id, fallbackName)) }
    val top = stack.last()
    com.nuvio.app.core.ui.PlatformBackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    HallPage(top.kind, top.id, top.name, onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }, onOpenMeta = onOpenMeta) { k, i, n ->
        stack.add(HallStop(k, i, n))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HallPage(
    kind: String,
    id: Int,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
    onOpenCompany: (String, Int, String) -> Unit,
) {
    val lite = luxoLiteMode()
    val isWatched = rememberLuxoWatchedMatcher()
    val scope = rememberCoroutineScope()
    var data by remember(kind, id) { mutableStateOf<HallData?>(null) }
    var studio by remember(kind, id) { mutableStateOf<HallStudio?>(null) }
    var dialog by remember(kind, id) { mutableStateOf<String?>(null) }
    var universe by remember(kind, id) { mutableStateOf<HallUniverse?>(null) }
    var openingPerson by remember { mutableStateOf<Int?>(null) }

    // grid state
    var tab by remember(kind, id) { mutableStateOf<String?>(null) }
    var sort by remember(kind, id) { mutableStateOf(HallSort.POPULAR) }
    var floor by remember(kind, id) { mutableStateOf(0.0) }
    var genre by remember(kind, id) { mutableStateOf<String?>(null) }
    var decade by remember(kind, id) { mutableStateOf<Int?>(null) }
    val works = remember(kind, id) { mutableStateListOf<LuxoCredit>() }
    var total by remember(kind, id) { mutableIntStateOf(0) }
    var page by remember(kind, id) { mutableIntStateOf(1) }
    var loading by remember(kind, id) { mutableStateOf(true) }

    LaunchedEffect(kind, id) {
        val d = HallApi.load(kind, id)
        data = d
        if (tab == null) tab = d.media
        studio = HallApi.studio(kind, id, d.info?.name ?: fallbackName)
    }
    LaunchedEffect(kind, id, tab, sort, floor, genre, decade) {
        val media = tab ?: return@LaunchedEffect
        loading = true
        works.clear()
        page = 1
        val (l, t) = HallApi.grid(kind, id, media, sort, floor, genre, decade, 1)
        works.addAll(l)
        total = t
        loading = false
    }
    LaunchedEffect(openingPerson) {
        val pid = openingPerson ?: return@LaunchedEffect
        val detail = com.nuvio.app.features.tmdb.TmdbMetadataService.fetchPersonDetail(pid)
        openingPerson = null
        if (detail != null) LuxoOverlays.person = detail
    }
    fun loadMore() {
        val media = tab ?: return
        if (loading || works.size >= total) return
        loading = true
        scope.launch {
            val next = page + 1
            val (l, _) = HallApi.grid(kind, id, media, sort, floor, genre, decade, next)
            works.addAll(l.filter { n -> works.none { it.id == n.id } })
            page = next
            loading = false
        }
    }
    fun open(c: LuxoCredit) = onOpenMeta(MetaPreview(id = c.metaId, type = c.metaType, name = c.title, poster = c.poster, banner = c.backdrop, releaseInfo = c.year))

    val info = data?.info
    val name = info?.name ?: fallbackName
    val accent = info?.logo?.let { HallTint.colors[it] } ?: HDefaultAccent
    val bg = MaterialTheme.colorScheme.background

    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val wide = maxWidth >= 840.dp
        val side = if (wide) 32.dp else 16.dp
        // Page tinted by the logo colour.
        Box(Modifier.fillMaxWidth().height(900.dp).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f), bg))))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 138.dp else 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            fun full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.fillMaxWidth()) { content() } }

            full { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(52.dp)) }
            // ---- the hall
            full {
                val backdrops = data?.catalog?.mapNotNull { it.credit.backdrop }?.distinct()?.take(10).orEmpty()
                HallHero(backdrops, accent, lite, if (wide) 360.dp else 270.dp)
            }
            // ---- logo plaque + name + chips
            full {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LogoPlaque(info?.logo, name, accent, lite)
                    Spacer(Modifier.height(12.dp))
                    Text(name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        info?.country?.let { InfoChip("${luxoFlag(it)} $it") }
                        info?.hq?.let { InfoChip("📍 $it") }
                        studio?.founded?.let { InfoChip("🗓 تأسست $it") }
                        data?.movieTotal?.takeIf { it > 0 }?.let { InfoChip("🎬 $it فيلم") }
                        data?.tvTotal?.takeIf { it > 0 }?.let { InfoChip("📺 $it مسلسل") }
                        if (info?.parentId != null && info.parentName != null) {
                            InfoChip("🏛 تابعة لـ ${info.parentName}", accent) { onOpenCompany("company", info.parentId, info.parentName) }
                        }
                    }
                }
            }
            val d = data
            if (d == null) {
                full { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accent) } }
            } else {
                // ---- progress ring
                val catalog = d.catalog.map { it.credit }
                if (catalog.isNotEmpty()) full { ProgressCard(catalog, isWatched, accent, name) { dialog = "progress" } }
                // ---- studio card
                val st = studio
                if (st != null && (st.fact != null || st.founder != null || info?.description != null)) full { StudioCard(st, info?.description, accent) }
                // ---- DNA
                if (d.catalog.isNotEmpty()) full { DnaCard(d, accent) { gid -> genre = gid; decade = null } }
                // ---- eras film strip
                if (d.eras.size > 1) full {
                    Section("🎞 عصور ${name}", "شريط الحقب — يُضغط على الحقبة لتصفية الأعمال")
                    FilmStrip(d.eras, decade) { dec -> decade = if (decade == dec) null else dec }
                }
                // ---- top 10
                if (d.top10.isNotEmpty()) full {
                    Section("🏆 الأيقونات العشر", "الأعلى تقييماً في تاريخها")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(d.top10) { i, c -> TopTenTile(i + 1, c, isWatched(c), accent) { open(c) } }
                    }
                }
                // ---- universes
                if (d.universes.isNotEmpty()) full {
                    Section("🌌 عوالمها", "السلاسل والامتدادات")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(d.universes) { u -> UniverseCard(u) { universe = u } }
                    }
                }
                // ---- stars & directors
                if (d.stars.isNotEmpty()) full {
                    Section("⭐ نجومها", "أكثر من تكرّر في أعمالها الكبرى")
                    PeopleRow(d.stars, accent, openingPerson) { openingPerson = it }
                }
                if (d.directors.isNotEmpty()) full {
                    Section(if (d.media == "tv") "✍️ صُنّاعها" else "🎬 مخرجوها")
                    PeopleRow(d.directors, accent, openingPerson) { openingPerson = it }
                }
                // ---- upcoming
                if (d.upcoming.isNotEmpty()) full {
                    Section("⏳ قريباً", "العدّ التنازلي لأعمالها القادمة")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(d.upcoming) { c -> UpcomingTile(c, accent) { open(c) } }
                    }
                }
                // ---- awards shelf
                if (st != null && st.awards.isNotEmpty()) full {
                    Section("🥇 رفّ الجوائز")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(st.awards) { (t, note) -> AwardTile(t, note) }
                    }
                }
                // ---- sisters
                if (st != null && st.sisters.isNotEmpty()) full {
                    Section("🤝 الشركات الشقيقة")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.sisters.forEach { s ->
                            InfoChip(s, accent) {
                                scope.launch { HallApi.findCompany(s)?.let { (cid, cname) -> onOpenCompany("company", cid, cname) } }
                            }
                        }
                    }
                }
            }
            // ---- filters
            full {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Section("🗂 كل الأعمال")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (kind != "network") HChip("أفلام" + (data?.movieTotal?.let { " · $it" } ?: ""), tab == "movie", accent) { tab = "movie" }
                        HChip("مسلسلات" + (data?.tvTotal?.let { " · $it" } ?: ""), tab == "tv", accent) { tab = "tv" }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(HallSort.entries) { s -> HChip(s.label, sort == s, accent, small = true) { sort = s } }
                        items(listOf(0.0, 6.0, 7.0, 8.0)) { v -> HChip(if (v == 0.0) "كل التقييمات" else "★ +${v.toInt()}", floor == v, accent, small = true) { floor = v } }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { HChip("كل الأمزجة", genre == null, accent, small = true) { genre = null } }
                        items(HallMoods) { (label, g) -> HChip(label, genre == g, accent, small = true) { genre = if (genre == g) null else g } }
                        val gnames = data?.let { dd -> topGenres(dd).map { it.first.toString() to it.second } }.orEmpty()
                        items(gnames) { (g, label) -> HChip(label, genre == g, accent, small = true) { genre = if (genre == g) null else g } }
                    }
                    val decades = data?.eras?.map { it.decade }.orEmpty()
                    if (decades.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { HChip("كل العقود", decade == null, accent, small = true) { decade = null } }
                        items(decades.reversed()) { dec -> HChip("${dec}s", decade == dec, accent, small = true) { decade = if (decade == dec) null else dec } }
                    }
                }
            }
            items(works, key = { "${it.mediaType}:${it.id}" }) { c -> GridTile(c, isWatched(c)) { open(c) } }
            full {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = accent)
                        works.isEmpty() -> Text("لا توجد أعمال بهذا الفلتر", color = Color.White.copy(alpha = 0.6f))
                        works.size < total -> HChip("عرض المزيد (${works.size} من $total)", false, accent) { loadMore() }
                    }
                }
            }
        }
        Box(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp).size(44.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White) }
    }

    val d = data
    if (dialog == "progress" && d != null) HallProgressDialog(d.catalog.map { it.credit }, isWatched, accent, { open(it) }) { dialog = null }
    universe?.let { u -> UniverseDialog(u, isWatched, { open(it) }) { universe = null } }
}

private fun topGenres(d: HallData): List<Pair<Int, String>> {
    val counts = d.catalog.flatMap { it.genres }.groupingBy { it }.eachCount()
    return counts.entries.sortedByDescending { it.value }.mapNotNull { (g, _) -> d.genres[g]?.takeIf { it.isNotBlank() }?.let { g to it } }.take(8)
}

// ------------------------------------------------------------------ hero: curtains, screen, beam, seats

@Composable
private fun HallHero(backdrops: List<String>, accent: Color, lite: Boolean, height: androidx.compose.ui.unit.Dp) {
    val curtain = remember { Animatable(0f) }
    LaunchedEffect(Unit) { curtain.animateTo(1f, tween(if (lite) 600 else 1800, easing = FastOutSlowInEasing)) }
    var index by remember(backdrops) { mutableIntStateOf(0) }
    LaunchedEffect(backdrops) {
        if (backdrops.size < 2) return@LaunchedEffect
        while (true) { delay(if (lite) 9000 else 5200); index = (index + 1) % backdrops.size }
    }
    val inf = rememberInfiniteTransition(label = "hall")
    val flicker by inf.animateFloat(0.10f, 0.19f, infiniteRepeatable(tween(if (lite) 4000 else 1300, easing = LinearEasing), RepeatMode.Reverse), label = "beam")
    val burns by inf.animateFloat(1f, if (lite) 1f else 1.14f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "burns")

    BoxWithConstraints(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp)).background(Color(0xFF07080C))) {
        val w = maxWidth
        val screenW = w * 0.74f
        val screenH = height * 0.58f
        // projector beam
        Canvas(Modifier.fillMaxSize()) {
            val sx = (size.width - screenW.toPx()) / 2
            val sy = size.height * 0.10f
            val path = Path().apply {
                moveTo(size.width / 2, -10f)
                lineTo(sx, sy)
                lineTo(sx + screenW.toPx(), sy)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = flicker + 0.1f), accent.copy(alpha = flicker), Color.Transparent), 0f, sy + 40f))
        }
        // screen
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = height * 0.10f).width(screenW).height(screenH)
                .clip(RoundedCornerShape(10.dp)).background(Color(0xFF15171E))
                .border(2.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
        ) {
            Crossfade(targetState = backdrops.getOrNull(index), animationSpec = tween(1200), label = "screen") { url ->
                if (url != null) {
                    AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = burns; scaleY = burns })
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
        }
        // seats
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height * 0.24f)) {
            val rows = 2
            repeat(rows) { r ->
                val seatW = size.width / (14 + r * 2)
                val y = size.height * (0.35f + r * 0.34f)
                var x = -seatW * 0.3f + (if (r == 1) seatW / 2 else 0f)
                while (x < size.width) {
                    drawRoundRect(
                        color = Color(0xFF0E0F14).copy(alpha = 0.92f + r * 0.08f),
                        topLeft = Offset(x + seatW * 0.08f, y),
                        size = Size(seatW * 0.84f, size.height),
                        cornerRadius = CornerRadius(seatW * 0.35f),
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.10f),
                        topLeft = Offset(x + seatW * 0.08f, y),
                        size = Size(seatW * 0.84f, 3f),
                        cornerRadius = CornerRadius(2f),
                    )
                    x += seatW
                }
            }
        }
        // curtains
        Canvas(Modifier.fillMaxSize()) {
            val open = curtain.value
            val half = size.width / 2
            val cw = half - (half - size.width * 0.12f) * open
            val folds = 7
            listOf(0f, size.width - cw).forEachIndexed { side, x0 ->
                repeat(folds) { f ->
                    val fw = cw / folds
                    val fx = x0 + f * fw
                    drawRect(
                        Brush.horizontalGradient(listOf(Color(0xFF4A0A14), Color(0xFF9A1B2E), Color(0xFF5C0D19)), fx, fx + fw),
                        topLeft = Offset(fx, 0f),
                        size = Size(fw + 1f, size.height),
                    )
                }
                // inner shadow edge
                val edge = if (side == 0) x0 + cw else x0
                drawRect(
                    Brush.horizontalGradient(if (side == 0) listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)) else listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), edge - 14f, edge + 14f),
                    topLeft = Offset(edge - 14f, 0f), size = Size(28f, size.height),
                )
            }
            // valance
            val vh = size.height * 0.08f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3A0710), Color(0xFF7E1626))), size = Size(size.width, vh))
            val scallops = 12
            val sw = size.width / scallops
            repeat(scallops) { s ->
                drawArc(Color(0xFF7E1626), 0f, 180f, true, topLeft = Offset(s * sw, vh - sw * 0.35f), size = Size(sw, sw * 0.7f))
            }
            drawRect(HGold.copy(alpha = 0.55f), topLeft = Offset(0f, vh - 2f), size = Size(size.width, 2f))
        }
    }
}

@Composable
private fun LogoPlaque(logo: String?, name: String, accent: Color, lite: Boolean) {
    val inf = rememberInfiniteTransition(label = "glow")
    val glow by inf.animateFloat(0.25f, if (lite) 0.25f else 0.6f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "g")
    val sheen by inf.animateFloat(-0.4f, if (lite) -0.4f else 1.4f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "s")
    Box(
        Modifier.offset(y = (-34).dp).width(250.dp).height(96.dp)
            .drawBehind {
                drawRoundRect(Brush.radialGradient(listOf(accent.copy(alpha = glow), Color.Transparent), center, size.width * 0.75f), topLeft = Offset(-40f, -40f), size = Size(size.width + 80f, size.height + 80f), cornerRadius = CornerRadius(60f))
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1E27), Color(0xFF0D0F15))))
            .border(1.dp, Brush.linearGradient(listOf(HGold.copy(alpha = 0.7f), accent.copy(alpha = 0.5f))), RoundedCornerShape(24.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            val dark = LuxoLogoTone.dark[logo] == true
            AsyncImage(
                model = logo,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                colorFilter = if (dark) androidx.compose.ui.graphics.ColorFilter.tint(Color.White) else null,
                onState = { s ->
                    if (s is AsyncImagePainter.State.Success) {
                        LuxoLogoTone.measure(logo, s.painter)
                        HallTint.measure(logo, s.painter)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sheen
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), Offset(x - 60f, 0f), Offset(x + 60f, size.height)))
        }
    }
}

// ------------------------------------------------------------------ cards

@Composable
private fun Section(title: String, sub: String? = null) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        sub?.let { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp) }
    }
}

@Composable
private fun InfoChip(text: String, accent: Color? = null, onClick: (() -> Unit)? = null) {
    Text(
        text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(accent?.copy(alpha = 0.22f) ?: Color.White.copy(alpha = 0.07f))
            .border(1.dp, (accent ?: Color.White).copy(alpha = 0.18f), RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun HChip(text: String, selected: Boolean, accent: Color, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = if (small) 13.sp else 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 9.dp),
    )
}

@Composable
private fun ProgressCard(catalog: List<LuxoCredit>, isWatched: (LuxoCredit) -> Boolean, accent: Color, name: String, onClick: () -> Unit) {
    val watched = catalog.count(isWatched)
    val frac = watched.toFloat() / catalog.size
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(HCard).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, style = Stroke(9.dp.toPx()))
                drawArc(Brush.sweepGradient(listOf(accent, HGold, accent)), -90f, 360f * frac, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$watched", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("من ${catalog.size}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text("تقدّمك في أعمال $name", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("من بين أشهر ${catalog.size} عملاً لها — يُضغط لعرض ما شوهد وما لم يُشاهَد", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text("${(frac * 100).toInt()}٪", color = HGold, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StudioCard(st: HallStudio, description: String?, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.20f), HCard)))
            .border(1.dp, HGold.copy(alpha = 0.25f), RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎫 بطاقة الاستوديو", color = HGold, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            st.founded?.let { Fact("التأسيس", it) }
            st.founder?.let { Fact("المؤسس", it) }
        }
        (st.fact ?: description)?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 22.sp) }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DnaCard(d: HallData, accent: Color, onGenre: (String) -> Unit) {
    val counts = d.catalog.flatMap { it.genres }.groupingBy { it }.eachCount()
    val totalTags = counts.values.sum().coerceAtLeast(1)
    val top = topGenres(d).take(6)
    val ratings = d.catalog.map { it.credit }.filter { it.votes >= 50 }
    val avg = if (ratings.isEmpty()) null else ratings.sumOf { it.rating } / ratings.size
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(HCard).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🧬 الحمض النووي للاستوديو", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("ما تصنعه أكثر من غيره", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            avg?.let { Text("★ ${(it * 10).toInt() / 10.0}", color = HGold, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
        top.forEachIndexed { i, (g, label) ->
            val share = (counts[g] ?: 0).toFloat() / totalTags
            Row(Modifier.fillMaxWidth().clickable { onGenre(g.toString()) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f))) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth((share * 2.6f).coerceIn(0.04f, 1f)).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(accent, if (i == 0) HGold else accent.copy(alpha = 0.6f)))),
                    )
                }
                Text("${(share * 100).toInt()}٪", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun FilmStrip(eras: List<HallEra>, selected: Int?, onClick: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B0B0E)),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        items(eras.reversed()) { e ->
            Column(Modifier.width(150.dp).clickable { onClick(e.decade) }) {
                Sprockets()
                Box(
                    Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(4.dp))
                        .border(if (selected == e.decade) 2.dp else 0.dp, if (selected == e.decade) HGold else Color.Transparent, RoundedCornerShape(4.dp)),
                ) {
                    (e.best?.backdrop ?: e.best?.poster)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f }) }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text("${e.decade}s", color = HGold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("${hallEraName(e.decade)} · ${e.count}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                }
                Sprockets()
            }
        }
    }
}

@Composable
private fun Sprockets() {
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val n = 6
        val gap = size.width / n
        repeat(n) { k ->
            drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(k * gap + gap * 0.3f, size.height * 0.28f), size = Size(gap * 0.4f, size.height * 0.44f), cornerRadius = CornerRadius(3f))
        }
    }
}

@Composable
private fun TopTenTile(rank: Int, c: LuxoCredit, watched: Boolean, accent: Color, onClick: () -> Unit) {
    Box(Modifier.width(172.dp).height(190.dp).clickable(onClick = onClick)) {
        Text(
            "$rank",
            style = TextStyle(
                fontSize = 150.sp, fontWeight = FontWeight.Black, color = accent.copy(alpha = 0.9f),
                drawStyle = Stroke(width = 5f),
            ),
            modifier = Modifier.align(Alignment.BottomStart).offset(y = 28.dp),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).width(112.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)),
        ) {
            c.poster?.let { AsyncImage(it, c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text("★ ${(c.rating * 10).toInt() / 10.0}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(HGold, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            if (watched) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0xFF3DDC97)), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun UniverseCard(u: HallUniverse, onClick: () -> Unit) {
    Box(Modifier.width(250.dp).height(140.dp).clip(RoundedCornerShape(18.dp)).background(HCard).clickable(onClick = onClick)) {
        (u.backdrop ?: u.poster)?.let { AsyncImage(it, u.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(u.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("عالم كامل — يُضغط لعرض أجزائه", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PeopleRow(people: List<HallPerson>, accent: Color, opening: Int?, onOpen: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(people) { p ->
            Column(Modifier.width(86.dp).clickable { onOpen(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)).border(2.dp, accent.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    p.photo?.let { AsyncImage(it, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    if (opening == p.id) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
                Text(p.name, color = Color.White, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text("× ${p.count}", color = HGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UpcomingTile(c: LuxoCredit, accent: Color, onClick: () -> Unit) {
    val today = hallDaysFromIso(CurrentDateProvider.todayIsoDate())
    val days = hallDaysFromIso(c.date)?.let { d -> today?.let { d - it } }
    Column(Modifier.width(120.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
            c.poster?.let { AsyncImage(it, c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (days != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (days <= 0) "اليوم" else "$days", color = HGold, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    if (days > 0) Text(if (days == 1L) "يوم متبقٍّ" else "يوماً متبقياً", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
        }
        Text(c.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        c.date?.let { Text(it, color = accent, fontSize = 11.sp) }
    }
}

@Composable
private fun AwardTile(title: String, note: String) {
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2312), HCard)))
            .border(1.dp, HGold.copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("🏆", fontSize = 34.sp)
        Text(title, color = HGold, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (note.isNotBlank()) Text(note, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GridTile(c: LuxoCredit, watched: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
            AsyncImage(model = c.poster, contentDescription = c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (c.rating > 0 && c.votes >= 5) {
                Text("★ ${(c.rating * 10).toInt() / 10.0}", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(if (c.rating >= 7) Color(0xFF3DDC97) else Color(0xFFFFC857), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            if (watched) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0xFF3DDC97)), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(c.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        c.year?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp) }
    }
}

// ------------------------------------------------------------------ dialogs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HallProgressDialog(credits: List<LuxoCredit>, isWatched: (LuxoCredit) -> Boolean, accent: Color, onOpen: (LuxoCredit) -> Unit, onDismiss: () -> Unit) {
    var showWatched by remember { mutableStateOf(true) }
    val watched = credits.filter(isWatched)
    val rest = credits.filterNot(isWatched)
    IDialog("📊 تقدّمك في أعمالها", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HChip("✓ تمت مشاهدته (${watched.size})", showWatched, accent) { showWatched = true }
            HChip("○ لم يُشاهَد بعد (${rest.size})", !showWatched, accent) { showWatched = false }
        }
        val list = if (showWatched) watched else rest
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (list.isEmpty()) Text(if (showWatched) "لم يُشاهَد من أعمالها شيء بعد" else "شوهدت كل أشهر أعمالها 🏆", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { c ->
                    Column(Modifier.width(96.dp).clickable { onOpen(c); onDismiss() }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
                            c.poster?.let { AsyncImage(it, c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Text(c.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UniverseDialog(u: HallUniverse, isWatched: (LuxoCredit) -> Boolean, onOpen: (LuxoCredit) -> Unit, onDismiss: () -> Unit) {
    var parts by remember(u.id) { mutableStateOf<List<LuxoCredit>?>(null) }
    LaunchedEffect(u.id) { parts = HallApi.collectionParts(u.id) }
    IDialog("🌌 ${u.name}", onDismiss) {
        val p = parts
        if (p == null) { CircularProgressIndicator(color = Color.White); return@IDialog }
        val seen = p.count(isWatched)
        Text("${p.size} أجزاء · شوهد منها $seen", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).clickable { onOpen(c); onDismiss() }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${i + 1}", color = HGold, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    Box(Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) { c.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(c.year, if (c.votes >= 20) "★ ${(c.rating * 10).toInt() / 10.0}" else null).joinToString(" · "), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    if (isWatched(c)) Text("✓", color = Color(0xFF3DDC97), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

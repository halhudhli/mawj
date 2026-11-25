@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.CastMember
import com.mawj.tv.data.Company
import com.mawj.tv.data.CompanyPage
import com.mawj.tv.data.Discover
import com.mawj.tv.data.Library
import com.mawj.tv.data.PersonFull
import com.mawj.tv.data.Store
import com.mawj.tv.data.Gemini
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mawj.tv.data.Tmdb
import com.mawj.tv.data.TmdbWork
import com.mawj.tv.data.toPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- on the title page ----------------

/** Cast with photos; a card opens the actor's page. */
@Composable
fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Column(Modifier.padding(top = 28.dp)) {
        T("طاقم العمل", 19.sp, weight = FontWeight.SemiBold)
        LazyRow(Modifier.focusRestorer().padding(top = 10.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(cast, key = { it.id }) { c ->
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).tvFocus(shape = CircleShape, scale = 1.08f, onClick = { Nav.go(Route.Person(c.id, c.name)) }).background(C.Card, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.photo != null) Img(c.photo, 96.dp, 96.dp, Modifier.fillMaxSize())
                        else T(c.name.take(1), 30.sp, color = C.Gold, weight = FontWeight.Bold)
                    }
                    T(c.name, 14.sp, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    if (c.role.isNotBlank()) T(c.role, 12.sp, color = C.Dim, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
    }
}

/** Production companies with their logos; a card opens the studio page. */
@Composable
fun CompanyRow(companies: List<Company>) {
    if (companies.isEmpty()) return
    Column(Modifier.padding(top = 24.dp)) {
        T("الإنتاج", 19.sp, weight = FontWeight.SemiBold)
        LazyRow(Modifier.focusRestorer().padding(top = 10.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(companies, key = { it.id }) { c ->
                Box(
                    Modifier.size(180.dp, 80.dp).tvFocus(shape = RoundedCornerShape(14.dp), onClick = { Nav.go(Route.Company(c.id, c.name)) })
                        .background(C.Raised).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c.logo != null) Logo(c.logo, 150.dp, 50.dp, Modifier.fillMaxSize())
                    else T(c.name, 15.sp, maxLines = 2, align = TextAlign.Center)
                }
            }
        }
    }
}

/** Studio logos are often dark on transparent: drawn as one light colour so they show on the sea. */
@Composable
fun Logo(url: String, w: Dp, h: Dp, modifier: Modifier, tint: Color = C.GoldLight) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).build() },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
        modifier = modifier,
    )
}

// ---------------- actor ----------------

@Composable
fun PersonScreen(id: Int, name: String) {
    var person by remember(id) { mutableStateOf<PersonFull?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var photos by remember { mutableStateOf(false) }
    var facts by remember { mutableStateOf(false) }
    LaunchedEffect(id) { person = Discover.person(id); failed = person == null }
    val p = person
    if (p == null) {
        Box(Modifier.fillMaxSize().background(C.Sea)) { Loading(if (failed) "تعذّر تحميل صفحة $name." else "جارٍ تحميل صفحة $name…") }
        return
    }
    LaunchedEffect(p.id) { p.works.maxByOrNull { it.votes }?.backdrop?.let(Backdrop::show) }
    val movies = p.works.count { it.type == "movie" }
    val shows = p.works.count { it.type == "tv" }
    val watchedNames = remember { Library.titles.values.map { it.meta.name }.toSet() }
    val seen = p.works.count { it.name in watchedNames }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x99070D14), 0.6f to Color(0xE6070D14), 1f to Color(0xF7070D14))))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 40.dp, top = 44.dp, bottom = 50.dp)) {
            item {
                Row {
                    Box(Modifier.size(170.dp, 226.dp).border(3.dp, C.Gold, RoundedCornerShape(18.dp)).padding(3.dp).background(C.Card, RoundedCornerShape(15.dp))) {
                        PhotoSlideshow(p.photos)
                    }
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.width(600.dp)) {
                        T(p.name, 40.sp, weight = FontWeight.Bold, maxLines = 1, shadow = true)
                        p.englishName?.let { T(it, 16.sp, color = C.Muted, num = true) }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tag(p.job)
                            p.age?.let { Tag("$it سنة") }
                            p.place?.substringAfterLast(", ")?.let { Tag(it) }
                            Tag("$movies فيلماً · $shows مسلسلاً")
                            if (seen > 0) Tag("شوهد $seen من أعماله", gold = true)
                        }
                        T(p.bio.ifBlank { "لا تتوفر نبذة." }, 15.sp, color = Color(0xFFCFD7DF), maxLines = 4, lineHeight = 25.sp, modifier = Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val f = Library.followed.containsKey(p.id)
                            Pill(if (f) "✓ متابَع" else "متابعة", primary = true, modifier = restoreFirst()) { Library.toggleFollow(p.id, p.name) }
                            if (p.photos.size > 1) Pill("صوره عبر السنين") { photos = true }
                            Pill("ماراثون الممثل") {
                                val list = "ماراثون ${p.name}"
                                Library.newList(list)
                                p.works.sortedByDescending { it.votes }.take(6).reversed().forEach { w -> Library.toggle(list, w.asPreview()) }
                                Nav.go(Route.Lists)
                            }
                            if (Gemini.enabled && Store.feature("geminiExtras")) GoldChip("✦ حقائق سريعة") { facts = true }
                        }
                    }
                }
            }
            if (Store.feature("actorStart")) item { StartHere(p) }
            item { CareerCurve(p) }
            item { PersonWorks(p) }
        }
        if (photos) SidePanel("صور ${p.name}", { photos = false }, width = 560.dp) {
            Box(restoreFirst().size(1.dp).tvFocus(onClick = {}))
            p.photos.chunked(3).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { Img(it, 160.dp, 240.dp, Modifier.size(160.dp, 240.dp)) }
                }
            }
        }
        if (facts) GeminiPanel("حقائق سريعة", "اذكر 6 حقائق قصيرة وممتعة عن ${p.name}${p.englishName?.let { " ($it)" } ?: ""}، بالعربية الفصحى، كل حقيقة في سطر.") { facts = false }
    }
}

/** A side panel that asks Gemini once when opened, and shows the answer. */
@Composable
fun GeminiPanel(title: String, prompt: String, onClose: () -> Unit) {
    var answer by remember(prompt) { mutableStateOf<String?>(null) }
    LaunchedEffect(prompt) { answer = Gemini.ask(prompt) ?: "تعذّر الوصول الآن." }
    SidePanel(title, onClose, width = 460.dp) {
        Box(restoreFirst().size(1.dp).tvFocus(onClick = {}))
        T(answer ?: "يُجهَّز الجواب…", 14.sp, color = if (answer == null) C.Gold else C.Ink, lineHeight = 24.sp)
    }
}

/** «من أين أبدأ؟»: the most known, the best rated, and a hidden gem. */
@Composable
private fun StartHere(p: PersonFull) {
    val known = p.works.maxByOrNull { it.votes }
    val best = p.works.filter { it.votes >= 300 && it != known }.maxByOrNull { it.rating }
    val gem = p.works.filter { it.rating >= 7 && it.votes in 30..1500 && it != known && it != best }.maxByOrNull { it.rating }
    val picks = listOfNotNull(known?.let { "الأشهر" to it }, best?.let { "الأعلى تقييماً" to it }, gem?.let { "جوهرة مخفية" to it })
    if (picks.isEmpty()) return
    val open = rememberOpener()
    Column(Modifier.padding(top = 26.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("من أين أبدأ؟", 20.sp, weight = FontWeight.Bold)
            T("  ثلاثة اختيارات من أعماله", 13.sp, color = C.Muted)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            picks.forEach { (label, w) ->
                Box {
                    PosterCard(w.asPreview(), onFocused = { Backdrop.show(w.backdrop) }, onClick = { open(w) })
                    T(label, 11.sp, color = C.Sea, weight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(C.Gold, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }
    }
}

/** «منحنى المسيرة»: the average rating of the works of each year. */
@Composable
private fun CareerCurve(p: PersonFull) {
    val byYear = p.works.filter { it.votes >= 40 && it.year != null }.groupBy { it.year!!.toInt() }
        .mapValues { (_, l) -> l.map { it.rating }.average() }.toSortedMap()
    if (byYear.size < 4) return
    val years = byYear.keys.toList()
    val lo = byYear.values.min() - 0.3
    val hi = byYear.values.max() + 0.3
    Glass(Modifier.padding(top = 24.dp).width(760.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("منحنى المسيرة", 19.sp, weight = FontWeight.Bold)
            T("  متوسط التقييم لكل سنة · ${years.first()}–${years.last()}", 12.sp, color = C.Muted)
        }
        Canvas(Modifier.fillMaxWidth().height(100.dp).padding(vertical = 10.dp)) {
            val span = (years.last() - years.first()).coerceAtLeast(1)
            fun pt(y: Int) = Offset(size.width * (1f - (y - years.first()).toFloat() / span), (size.height * (1 - (byYear[y]!! - lo) / (hi - lo))).toFloat())
            val path = Path().apply { years.forEachIndexed { i, y -> val o = pt(y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, C.Gold, style = Stroke(width = 3f))
            years.forEach { drawCircle(C.Gold, 3.5f, pt(it)) }
        }
        val peak = byYear.maxBy { it.value }
        T("أفضل سنة: ${peak.key} · متوسط %.1f".format(peak.value), 12.sp, color = C.Muted)
    }
}

/** «أعماله»: films or series, sorted by fame, date or rating. */
@Composable
private fun PersonWorks(p: PersonFull) {
    var kind by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("known") }
    val list = p.works.filter { kind == "all" || it.type == kind }.let { l ->
        when (sort) {
            "new" -> l.sortedByDescending { it.date ?: "" }
            "top" -> l.filter { it.votes >= 50 }.sortedByDescending { it.rating }
            else -> l.sortedByDescending { it.votes }
        }
    }.take(40)
    Column(Modifier.padding(top = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("أعماله", 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Chip("الكل", kind == "all", onClick = { kind = "all" })
            Chip("أفلام", kind == "movie", onClick = { kind = "movie" })
            Chip("مسلسلات", kind == "tv", onClick = { kind = "tv" })
            Spacer(Modifier.width(14.dp))
            Chip("الأشهر", sort == "known", onClick = { sort = "known" })
            Chip("الأحدث", sort == "new", onClick = { sort = "new" })
            Chip("الأعلى تقييماً", sort == "top", onClick = { sort = "top" })
        }
        WorkPosters(list)
    }
}

/** The actor's photos change every 6 seconds with a slow fade; the face is framed from the top. */
@Composable
private fun PhotoSlideshow(photos: List<String>) {
    if (photos.isEmpty()) return
    val ctx = LocalContext.current
    var index by remember(photos) { mutableIntStateOf(0) }
    LaunchedEffect(photos) {
        if (photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val next = (index + 1) % photos.size
            coil3.SingletonImageLoader.get(ctx).enqueue(ImageRequest.Builder(ctx).data(photos[next]).size(400, 540).build())
            delay(1_000)
            index = next
        }
    }
    Crossfade(targetState = photos[index], animationSpec = tween(1200), label = "actor") { url ->
        AsyncImage(
            model = remember(url) { ImageRequest.Builder(ctx).data(url).size(400, 540).build() },
            contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().background(C.Card, RoundedCornerShape(15.dp)),
        )
    }
}

// ---------------- studio ----------------

@Composable
fun CompanyScreen(id: Int, name: String) {
    var sort by rememberSaveable(id) { mutableStateOf("top") }
    var page by remember(id, sort) { mutableStateOf<CompanyPage?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, sort) { page = Tmdb.company(id, sort); failed = page == null }
    val p = page
    val works = p?.works.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 50.dp)) {
        item { Curtain(p?.company?.logo, p?.company?.name ?: name) }
        item {
            Column(Modifier.padding(start = 48.dp, end = 40.dp, top = 14.dp)) {
                T(p?.company?.name ?: name, 36.sp, weight = FontWeight.Bold)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("شركة إنتاج")
                    p?.country?.let { Tag(it) }
                    if (works.isNotEmpty()) Tag("${works.size}+ عملاً")
                    val names = Library.titles.values.map { it.meta.name }.toSet()
                    val seen = works.count { it.name in names }
                    if (seen > 0) Tag("شوهد $seen من أعمالها", gold = true)
                }
            }
        }
        if (failed) item { T("تعذّر تحميل أعمال الشركة.", 16.sp, color = C.Muted, modifier = Modifier.padding(48.dp)) }
        if (works.isNotEmpty() && Store.feature("companyHall")) item {
            Row(Modifier.padding(start = 48.dp, end = 40.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StudioDna(works, Modifier.width(330.dp))
                Podium(works.filter { it.votes >= 100 }.sortedByDescending { it.rating }.take(3))
                Decades(works, Modifier.width(220.dp))
            }
        }
        item {
            Column(Modifier.padding(start = 48.dp, top = 22.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("كل الأعمال", 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    Chip("الأعلى تقييماً", sort == "top", onClick = { sort = "top" }, modifier = restoreFirst())
                    Chip("الأحدث", sort == "new", onClick = { sort = "new" })
                }
                WorkPosters(works)
            }
        }
    }
}

/** The studio curtain: drawn once, open, still (no repeating motion), with the logo on a plate. */
@Composable
private fun Curtain(logo: String?, name: String) {
    Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF140A0C))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val drape = w * 0.2f
            for (side in 0..1) {
                val x0 = if (side == 0) 0f else w - drape
                var x = x0
                var i = 0
                while (x < x0 + drape) {
                    drawRect(if (i % 3 == 1) Color(0xFFA5182A) else if (i % 3 == 2) Color(0xFF6A0C15) else Color(0xFF7D0F1A), Offset(x, 0f), androidx.compose.ui.geometry.Size(drape / 9, h))
                    x += drape / 9; i++
                }
            }
            drawRect(Color(0xFF8E1220), Offset.Zero, androidx.compose.ui.geometry.Size(w, h * 0.16f))
            drawRect(C.Gold, Offset(0f, h * 0.16f), androidx.compose.ui.geometry.Size(w, 3f))
        }
        Box(
            Modifier.align(Alignment.Center).padding(top = 20.dp).size(260.dp, 92.dp)
                .background(C.Sea, RoundedCornerShape(14.dp)).border(2.dp, C.Gold, RoundedCornerShape(14.dp)).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) Logo(logo, 220.dp, 64.dp, Modifier.fillMaxSize())
            else T(name, 26.sp, color = C.GoldLight, weight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** «الحمض النووي للاستوديو»: the share of each genre in its works. */
@Composable
private fun StudioDna(works: List<com.mawj.tv.data.TmdbWork>, modifier: Modifier) {
    val counts = works.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
    val total = counts.sumOf { it.value }.coerceAtLeast(1)
    Glass(modifier) {
        T("الحمض النووي للاستوديو", 16.sp, weight = FontWeight.Bold)
        counts.forEach { (g, n) ->
            val share = n.toFloat() / total
            Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                T(com.mawj.tv.data.Genres.name(g) ?: "", 12.sp, modifier = Modifier.width(90.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(8.dp).background(Color(0x22FFFFFF), CircleShape)) {
                    Box(Modifier.fillMaxWidth(share).height(8.dp).background(C.Gold, CircleShape))
                }
                T("${(share * 100).toInt()}%", 11.sp, color = C.Muted, num = true, modifier = Modifier.padding(start = 8.dp).width(34.dp))
            }
        }
    }
}

/** «الأيقونات»: its three best rated works on a 2-1-3 podium. */
@Composable
private fun Podium(top: List<com.mawj.tv.data.TmdbWork>) {
    if (top.isEmpty()) return
    val open = rememberOpener()
    Column {
        T("الأيقونات", 16.sp, weight = FontWeight.Bold)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).filter { it < top.size }.forEach { i ->
                val w = top[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(92.dp, 137.dp).tvFocus(shape = RoundedCornerShape(10.dp), onClick = { open(w) }).background(C.Card)) { Img(w.poster, 92.dp, 137.dp, Modifier.fillMaxSize()) }
                    Box(
                        Modifier.padding(top = 6.dp).size(92.dp, listOf(52, 36, 24)[i].dp)
                            .background(if (i == 0) C.Gold else Color(0xFF162231), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { T("${i + 1}", 20.sp, color = if (i == 0) C.Sea else C.GoldLight, weight = FontWeight.Bold, num = true) }
                }
            }
        }
    }
}

/** «العصور»: how many of its works came out in each decade. */
@Composable
private fun Decades(works: List<com.mawj.tv.data.TmdbWork>, modifier: Modifier) {
    val byDecade = works.mapNotNull { it.year?.toIntOrNull()?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount().toSortedMap()
    if (byDecade.isEmpty()) return
    val max = byDecade.values.max()
    Glass(modifier) {
        T("العصور", 16.sp, weight = FontWeight.Bold)
        byDecade.entries.toList().takeLast(6).forEach { (d, n) ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${d}s", 12.sp, num = true, modifier = Modifier.width(52.dp))
                Box(Modifier.weight(1f).height(14.dp).background(Color(0x14FFFFFF), RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(14.dp).background(Color(0x99D6B36A), RoundedCornerShape(4.dp)))
                }
                T("$n", 11.sp, color = C.Muted, num = true, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

// ---------------- shared ----------------

@Composable
private fun WorkPosters(works: List<com.mawj.tv.data.TmdbWork>) {
    if (works.isEmpty()) return
    val open = rememberOpener()
    LazyRow(Modifier.focusRestorer().fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        items(works, key = { it.type + it.tmdbId }) { w ->
            PosterCard(w.asPreview(), onFocused = { Backdrop.show(w.backdrop ?: w.poster) }, onClick = { open(w) })
        }
    }
}

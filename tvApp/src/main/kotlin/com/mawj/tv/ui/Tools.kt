@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Discover
import com.mawj.tv.data.Island
import com.mawj.tv.data.Library
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.Store
import com.mawj.tv.data.TmdbWork
import kotlinx.coroutines.delay
import java.time.LocalDate

// ---------------- «قوائمي» ----------------

@Composable
fun ListsScreen() {
    val names = Library.lists.keys.sortedBy { if (it == Library.LATER) "" else it }
    var current by rememberSaveable { mutableStateOf(Library.LATER) }
    if (current !in Library.lists) current = Library.LATER
    val items = Library.lists[current].orEmpty()
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead("قوائمي", "يُضاف العمل من زر «قائمتي» في صفحته.", Modifier.padding(start = 30.dp))
        LazyRow(Modifier.padding(top = 14.dp).focusRestorer(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(names) { n -> Chip("$n · ${Library.lists[n]?.size ?: 0}", n == current, onClick = { current = n }, modifier = if (n == names.first()) restoreFirst() else Modifier) }
            if (current != Library.LATER) item { Chip("حذف هذه القائمة", false, onClick = { Library.deleteList(current); current = Library.LATER }) }
        }
        if (items.isEmpty()) { Loading("القائمة فارغة."); return@Column }
        PosterGrid(items)
    }
}

@Composable
private fun PosterGrid(items: List<MetaPreview>, works: List<TmdbWork>? = null) {
    val open = rememberOpener()
    LazyVerticalGrid(
        GridCells.Adaptive(PosterSize.w + 6.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (works != null) items(works, key = { it.type + it.tmdbId }) { w ->
            PosterCard(w.asPreview(), onFocused = { Backdrop.show(w.backdrop ?: w.poster) }, onClick = { open(w) })
        } else items(items, key = { it.id }) { m ->
            PosterCard(m, onFocused = { Backdrop.show(m.background ?: m.poster) }, onClick = {
                if (m.id.startsWith("tmdb:")) Nav.go(Route.Tool("open:${m.id}:${m.type}")) else Nav.go(Route.Detail(m))
            })
        }
    }
}

// ---------------- the tools ----------------

@Composable
fun ToolScreen(kind: String) {
    when {
        kind.startsWith("open:") -> OpenTmdb(kind)
        kind == "tonight" -> Tonight(together = false)
        kind == "together" -> Tonight(together = true)
        kind == "time" -> TimeBudget()
        kind == "wheel" -> Wheel()
        kind == "unfinished" -> Unfinished()
        kind == "anime" -> ToolWorks("تقويم الأنمي", "أنمي يُعرض هذه الأيام", "anime-air") {
            Discover.discover("tv", "with_genres=16&with_original_language=ja&air_date.gte=${LocalDate.now().minusDays(7)}&air_date.lte=${LocalDate.now().plusDays(7)}")
        }
        kind == "franchises" -> Franchises()
        kind == "tier" -> Tiers()
        kind == "hidden" -> Hidden()
        kind == "kids" -> SwitchPage("ملف الأطفال", "kidsMode", "يقتصر الاكتشاف (الجزر وأفلام ومسلسلات والمزاج) على ما يناسب الصغار.")
        kind == "private" -> SwitchPage("الوضع الخاص", "privateMode", "لا يُسجَّل شيء في الملف ولا في «متابعة المشاهدة» ما دام مفعّلاً.")
        kind == "second" -> Column(Modifier.fillMaxSize().padding(40.dp)) {
            PageHead("الشاشة الثانية", "يُفتح الرابط في الجوال أو التابلت على نفس الشبكة: بحث، وإضافات، ولقطات.")
            Spacer(Modifier.height(20.dp))
            PhoneLinkCard()
        }
        kind == "wrapped" -> Wrapped()
        else -> Loading("هذه الأداة تُضاف لاحقاً.")
    }
}

/** Opens a saved TMDB title (from a list): its IMDb id is looked up first. */
@Composable
private fun OpenTmdb(kind: String) {
    LaunchedEffect(kind) {
        val parts = kind.removePrefix("open:tmdb:").split(':')
        val id = parts.getOrNull(0)?.toIntOrNull()
        val type = if (parts.getOrNull(1) == "series") "tv" else "movie"
        val w = id?.let { TmdbWork(it, type, "", null, null, null, 0.0) }
        val p = w?.let { com.mawj.tv.data.Tmdb.imdbOf(it) }
        Nav.back()
        if (p != null) Nav.go(Route.Detail(MetaPreview(p, if (type == "tv") "series" else "movie", "", null, null, null, null, null, null, emptyList())))
    }
    Loading("جارٍ الفتح…")
}

/** «ماذا نشاهد الليلة؟» (and «ماذا نشاهد معاً؟» with two moods): mood, time, film or series. */
@Composable
private fun Tonight(together: Boolean) {
    var mood by rememberSaveable { mutableIntStateOf(0) }
    var mood2 by rememberSaveable { mutableIntStateOf(1) }
    var minutes by rememberSaveable { mutableIntStateOf(120) }
    var kind by rememberSaveable { mutableStateOf("movie") }
    var list by remember { mutableStateOf<List<TmdbWork>?>(null) }
    LaunchedEffect(mood, mood2, minutes, kind, together) {
        list = null
        val a = Discover.tonight(mood, minutes, kind)
        list = if (!together) a else {
            val b = Discover.tonight(mood2, minutes, kind)
            val both = a.filter { x -> b.any { it.tmdbId == x.tmdbId } }
            (both + a.zip(b).flatMap { listOf(it.first, it.second) }).distinctBy { it.tmdbId }
        }
    }
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead(if (together) "ماذا نشاهد معاً؟" else "ماذا نشاهد الليلة؟", if (together) "مزاجان، وما يجمعهما أولاً." else "المزاج والوقت المتاح، والباقي على موج.", Modifier.padding(start = 30.dp))
        Row(Modifier.padding(start = 30.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(if (together) "الأول" else "المزاج", 14.sp, color = C.Muted, modifier = Modifier.padding(end = 4.dp))
            Discover.moods.forEachIndexed { i, (n, _) -> Chip(n, i == mood, onClick = { mood = i }, modifier = if (i == 0) restoreFirst() else Modifier) }
        }
        if (together) Row(Modifier.padding(start = 30.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("الثاني", 14.sp, color = C.Muted, modifier = Modifier.padding(end = 4.dp))
            Discover.moods.forEachIndexed { i, (n, _) -> Chip(n, i == mood2, onClick = { mood2 = i }) }
        }
        Row(Modifier.padding(start = 30.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("الوقت", 14.sp, color = C.Muted, modifier = Modifier.padding(end = 4.dp))
            listOf(90, 120, 150, 200).forEach { m -> Chip(if (m == 200) "مفتوح" else "$m د", m == minutes, onClick = { minutes = m }) }
            Spacer(Modifier.width(16.dp))
            Chip("فيلم", kind == "movie", onClick = { kind = "movie" })
            Chip("مسلسل", kind == "tv", onClick = { kind = "tv" })
        }
        val l = list
        if (l == null) Loading() else PosterGrid(emptyList(), l)
    }
}

/** «كم عندي وقت؟»: what fits in the time there is: unfinished titles first, then films that fit. */
@Composable
private fun TimeBudget() {
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    val fits = Store.progress.filter { p -> p.durationMs > 0 && (p.durationMs - p.positionMs) / 60_000 <= minutes }
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead("كم عندي وقت؟", "ما يكفيه الوقت المتاح.", Modifier.padding(start = 30.dp))
        Row(Modifier.padding(start = 30.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120, 180).forEachIndexed { i, m -> Chip("$m دقيقة", m == minutes, onClick = { minutes = m }, modifier = if (i == 0) restoreFirst() else Modifier) }
        }
        if (fits.isNotEmpty()) {
            RowTitle("يكفي لإكماله")
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(fits, key = { it.meta.id }) { p ->
                    WideCard(p.meta.name, p.meta.background ?: p.meta.poster, p.fraction, onFocused = {}, onClick = { Nav.go(Route.Detail(p.meta)) },
                        badge = "متبقٍ ${(p.durationMs - p.positionMs) / 60_000} د", kicker = if (p.season > 0) "S${p.season} E${p.episode}" else null)
                }
            }
        }
        if (minutes >= 90) WorksRow("أفلام تكفيها $minutes دقيقة", "time:$minutes", null, {}) {
            Discover.discover("movie", "with_runtime.lte=$minutes&with_runtime.gte=${minutes - 40}&vote_average.gte=7&vote_count.gte=500")
        } else WorksRow("حلقات قصيرة تكفيها $minutes دقيقة", "time:$minutes", null, {}) {
            Discover.discover("tv", "with_runtime.lte=$minutes&vote_average.gte=7.5&vote_count.gte=200")
        }
    }
}

/** «عجلة الاختيار»: names turn fast, then slow, then stop on one title from the list (or what is trending). */
@Composable
private fun Wheel() {
    val pool = remember {
        Library.lists[Library.LATER].orEmpty().ifEmpty { Store.progress.map { it.meta } }
    }
    val fallback = rememberWorks("trend:movie") { Discover.trending("movie") }
    val list = pool.ifEmpty { fallback.orEmpty().map { it.asPreview() } }
    var at by remember { mutableIntStateOf(0) }
    var spinning by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) {
        if (round == 0 || list.isEmpty()) return@LaunchedEffect
        spinning = true
        var wait = 50L
        repeat(22 + (0..list.size).random()) { at = (at + 1) % list.size; delay(wait); wait = (wait * 1.12).toLong() }
        spinning = false
    }
    val pick = list.getOrNull(at)
    LaunchedEffect(pick) { pick?.background?.let(Backdrop::show) }
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PageHead("عجلة الاختيار", if (pool.isEmpty()) "من الرائج الآن (قائمة «للمشاهدة لاحقاً» فارغة)." else "من قائمة «للمشاهدة لاحقاً».")
        Spacer(Modifier.height(30.dp))
        if (pick != null) {
            Box(Modifier.background(Color(0xCC111A24), RoundedCornerShape(24.dp)).padding(horizontal = 40.dp, vertical = 26.dp)) {
                T(pick.name, 38.sp, weight = FontWeight.Bold, color = if (spinning) C.Muted else C.GoldLight, maxLines = 1)
            }
        }
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill(if (round == 0) "أدر العجلة" else "مرة أخرى", primary = true, modifier = restoreFirst()) { if (!spinning) round++ }
            if (round > 0 && !spinning && pick != null) Pill("افتح «${pick.name}»") {
                if (pick.id.startsWith("tmdb:")) Nav.go(Route.Tool("open:${pick.id}:${pick.type}")) else Nav.go(Route.Detail(pick))
            }
        }
    }
}

/** «ما بدأته ولم يكتمل»: everything in progress, oldest first (the forgotten ones on top). */
@Composable
private fun Unfinished() {
    val list = Store.progress.sortedBy { it.updatedAt }
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead("ما بدأته ولم يكتمل", "${list.size} عملاً · الأقدم أولاً", Modifier.padding(start = 30.dp))
        if (list.isEmpty()) { Loading("لا يوجد ما بدأته ولم يكتمل."); return@Column }
        LazyVerticalGrid(GridCells.Adaptive(260.dp), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(list, key = { it.meta.id }) { p ->
                val days = ((System.currentTimeMillis() - p.updatedAt) / 86_400_000L).coerceAtLeast(0)
                WideCard(p.meta.name, p.meta.background ?: p.meta.poster, p.fraction, onFocused = {}, onClick = { Nav.go(Route.Detail(p.meta)) },
                    badge = if (days > 0) "منذ $days يوماً" else "اليوم", kicker = if (p.season > 0) "S${p.season} E${p.episode}" else null,
                    modifier = if (p == list.first()) restoreFirst() else Modifier)
            }
        }
    }
}

private val COLLECTIONS = listOf(
    "هاري بوتر" to 1241, "سيد الخواتم" to 119, "حرب النجوم" to 10, "المنتقمون" to 86311, "فارس الظلام" to 263, "قصة لعبة" to 10194,
    "المهمة المستحيلة" to 87359, "السرعة والغضب" to 9485, "جون ويك" to 404609, "العراب" to 230, "العودة إلى المستقبل" to 264,
    "ماتريكس" to 2344, "كثيب" to 726871, "قراصنة الكاريبي" to 295, "الحديقة الجوراسية" to 328, "أليان" to 8091,
)

/** «السلاسل»: famous film series in order. */
@Composable
private fun Franchises() {
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead("السلاسل", "الأجزاء بترتيب صدورها.", Modifier.padding(start = 30.dp))
        LazyVerticalGrid(GridCells.Adaptive(240.dp), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(COLLECTIONS) { (name, id) ->
                IconTile("film-advance", name, modifier = if (id == COLLECTIONS.first().second) restoreFirst() else Modifier) {
                    Nav.go(Route.Works(name, Island(name, "movie", "", path = "/collection/$id")))
                }
            }
        }
    }
}

@Composable
private fun ToolWorks(title: String, sub: String, key: String, load: suspend () -> List<TmdbWork>) {
    val list = rememberWorks(key, load)
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead(title, sub, Modifier.padding(start = 30.dp))
        if (list == null) Loading() else if (list.isEmpty()) Loading("لا يوجد شيء الآن.") else PosterGrid(emptyList(), list)
    }
}

/** «ترتيبي الخاص»: each watched title gets a rank (S, A, B, C) with the remote. */
@Composable
private fun Tiers() {
    val all = Library.titles.values.sortedByDescending { it.minutes }.map { it.meta }
    var version by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(top = 30.dp)) {
        PageHead("ترتيبي الخاص", "يُختار الحرف تحت كل عمل: S الأفضل ثم A وB وC.", Modifier.padding(start = 30.dp))
        if (all.isEmpty()) { Loading("ما يُشاهَد يظهر هنا للترتيب."); return@Column }
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            listOf("S", "A", "B", "C", "").forEach { tier ->
                val group = all.filter { Store.text("tier:${it.id}", "") == tier }
                if (group.isEmpty()) return@forEach
                item(key = "t$tier$version") {
                    T(if (tier.isEmpty()) "بلا ترتيب" else "الفئة $tier", 20.sp, weight = FontWeight.Bold, color = C.GoldLight, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(group, key = { it.id }) { m ->
                            Column(Modifier.width(130.dp)) {
                                Box(Modifier.height(190.dp).width(130.dp).background(C.Card, RoundedCornerShape(10.dp))) { Img(m.poster, 130.dp, 190.dp, Modifier.fillMaxSize()) }
                                T(m.name, 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("S", "A", "B", "C").forEach { t -> Chip(t, t == tier, onClick = { Store.setText("tier:${m.id}", if (t == tier) "" else t); version++ }) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hidden() {
    Column(Modifier.fillMaxSize().padding(40.dp)) {
        PageHead("الأعمال المخفية", "لا تظهر في الصفوف ولا في «متابعة المشاهدة». الضغط على العمل يعيد إظهاره.")
        Spacer(Modifier.height(16.dp))
        if (Library.hidden.isEmpty()) T("لا توجد أعمال مخفية. يُخفى العمل من زر «إخفاء العمل» في صفحته.", 15.sp, color = C.Muted)
        Library.hidden.toList().forEachIndexed { i, id ->
            val name = Library.titles[id]?.meta?.name ?: Store.progressFor(id)?.meta?.name ?: id
            Chip("إظهار «$name»", false, onClick = { Library.unhide(id) }, modifier = (if (i == 0) restoreFirst() else Modifier).padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun SwitchPage(title: String, key: String, note: String) {
    Column(Modifier.fillMaxSize().padding(40.dp).width(640.dp)) {
        PageHead(title)
        Spacer(Modifier.height(16.dp))
        Box(restoreFirst()) { SwitchRow(title, note, key) }
    }
}

/** «السنة في موج»: pages turned with the arrows, one fact each. */
@Composable
private fun Wrapped() {
    val year = LocalDate.now().year
    val minutes = Library.days.filterKeys { it.startsWith("$year") }.values.sum()
    val top = Library.titles.values.sortedByDescending { it.minutes }.take(5)
    val days = Library.days.filterKeys { it.startsWith("$year") }.count { it.value > 0 }
    val pages = listOf(
        "السنة في موج" to "$year",
        "شاهدت هذا العام" to "${minutes / 60} ساعة",
        "في" to "$days يوماً",
        "أكثر ما شاهدت" to (top.joinToString("\n") { "${it.meta.name} · ${it.minutes / 60} س" }.ifBlank { "—" }),
        "وقتك المفضل" to (Library.peakHour?.let { "الساعة %d:00".format(it) } ?: "—"),
        "لقبك" to tasteTitle(),
    )
    var page by remember { mutableIntStateOf(0) }
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3B2A6E), Color(0xFF6A3FA0), Color(0xFF1D2A3A))))
            .then(restoreFirst()).tvFocus(shape = RoundedCornerShape(0.dp), scale = 1f, onClick = { page = (page + 1) % pages.size })
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { page = (page + 1).coerceAtMost(pages.lastIndex); true }
                    Key.DirectionRight -> { page = (page - 1).coerceAtLeast(0); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.align(Alignment.TopCenter).padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { i -> Box(Modifier.width(48.dp).height(4.dp).background(if (i <= page) C.GoldLight else Color(0x44FFFFFF), RoundedCornerShape(2.dp))) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(pages[page].first, 22.sp, color = Color(0xFFE6DDF5))
            T(pages[page].second, if (page == 3) 24.sp else 64.sp, weight = FontWeight.Bold, align = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = if (page == 3) 38.sp else 70.sp,
                modifier = Modifier.padding(top = 10.dp))
            T("موج", 16.sp, color = C.GoldLight, modifier = Modifier.padding(top = 30.dp))
        }
        T("الأسهم للتنقل", 12.sp, color = Color(0x99FFFFFF), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

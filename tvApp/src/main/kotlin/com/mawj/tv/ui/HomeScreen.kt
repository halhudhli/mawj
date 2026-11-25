@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.CatalogDef
import com.mawj.tv.data.Discover
import com.mawj.tv.data.Library
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.Store

/** Kept while the app runs, so coming back to the home never reloads or loses the place. */
private object HomeState {
    var rows by mutableStateOf<List<CatalogDef>?>(null)
    val items = mutableStateMapOf<String, List<MetaPreview>>()
    var focused by mutableStateOf<MetaPreview?>(null)
    var lastKey by mutableStateOf<String?>(null)
    /** The row holding the focus: only its posters change (light on a weak TV). */
    var focusedRow by mutableStateOf<String?>(null)
    var mood by mutableStateOf<Int?>(null)
}

private fun CatalogDef.key() = "${addon?.manifestUrl}|$type|$id"

fun catalogTitle(def: CatalogDef): String {
    val name = when (def.name.lowercase()) {
        "popular" -> "الأكثر شعبية"
        "new" -> "الجديد"
        "featured" -> "مختارات"
        "top" -> "الأعلى تقييماً"
        "trending" -> "الرائج"
        else -> def.name
    }
    val type = when (def.type) {
        "movie" -> "أفلام"
        "series" -> "مسلسلات"
        "anime" -> "أنمي"
        "tv" -> "قنوات"
        else -> ""
    }
    return if (type.isEmpty()) name else "$name · $type"
}

@Composable
fun HomeScreen() {
    LaunchedEffect(Store.addonUrls.toList()) { HomeState.rows = Addons.homeRows() }
    val rows = HomeState.rows
    if (HomeState.focused == null) Store.progress.firstOrNull()?.meta?.let { HomeState.focused = it; Backdrop.show(it.background) }
    Column(Modifier.fillMaxSize()) {
        Hero(HomeState.focused, Modifier.fillMaxWidth().height(215.dp).padding(start = 24.dp, end = 32.dp, top = 30.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (Store.progress.isNotEmpty()) item(key = "continue") { ContinueRow() }
            if (Store.feature("moodCompass")) item(key = "mood") { MoodCompass() }
            HomeState.mood?.let { m ->
                item(key = "mood-row-$m") {
                    WorksRow("بوصلة المزاج · ${Discover.moods[m].first}", "mood:$m", HomeState.focusedRow, { HomeState.focusedRow = it },
                        onFocusWork = { w -> HomeState.focused = w.asPreview(); Backdrop.show(w.backdrop ?: w.poster) }) {
                        Discover.discover("movie", Discover.moods[m].second.first) + Discover.discover("tv", Discover.moods[m].second.second)
                    }
                }
            }
            if (Store.feature("top10Rank")) item(key = "top10") {
                WorksRow("الأعلى مشاهدة هذا الأسبوع", "top10", HomeState.focusedRow, { HomeState.focusedRow = it }, rank = true,
                    onFocusWork = { w -> HomeState.focused = w.asPreview(); Backdrop.show(w.backdrop ?: w.poster) }, first = Store.progress.isEmpty()) {
                    (Discover.trending("movie").take(5) + Discover.trending("tv").take(5)).sortedByDescending { it.votes }.take(10)
                }
            }
            val last = Library.titles.values.maxByOrNull { it.last }?.meta
            if (Store.feature("becauseWatched") && last != null) item(key = "because-${last.id}") {
                WorksRow("لأنك شاهدت «${last.name}»", "because:${last.id}", HomeState.focusedRow, { HomeState.focusedRow = it },
                    onFocusWork = { w -> HomeState.focused = w.asPreview(); Backdrop.show(w.backdrop ?: w.poster) }) {
                    val id = Discover.tmdbIdOf(last) ?: return@WorksRow emptyList()
                    Discover.similar(Discover.kindOf(last), id)
                }
            }
            if (rows == null) item { Loading(modifier = Modifier.height(200.dp)) }
            else items(rows, key = { it.key() }) { def -> CatalogRow(def) }
        }
    }
}

@Composable
private fun ContinueRow() {
    RowTitle("متابعة المشاهدة")
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(Store.progress.filter { it.meta.id !in Library.hidden }, key = { _, p -> p.meta.id }) { i, p ->
            val k = "c|${p.meta.id}"
            val left = ((p.durationMs - p.positionMs) / 60_000L).coerceAtLeast(0)
            WideCard(
                p.meta.name, p.meta.background ?: p.meta.poster, p.fraction,
                badge = if (p.durationMs > 0 && Store.feature("timeLeft")) "متبقٍ $left د" else null,
                kicker = if (p.season > 0) "S${p.season} E${p.episode}" else null,
                onFocused = { HomeState.focused = p.meta; HomeState.lastKey = k; HomeState.focusedRow = "continue"; Backdrop.show(p.meta.background) },
                onClick = { Nav.go(Route.Detail(p.meta)) },
                modifier = restoreFocus(k, first = i == 0),
            )
        }
    }
}

/** «بوصلة المزاج»: five moods; choosing one adds a row under it. «ماذا نشاهد الليلة؟» opens the picker. */
@Composable
private fun MoodCompass() {
    Row(Modifier.padding(start = 30.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        T("بوصلة المزاج", 18.sp, weight = FontWeight.Bold, shadow = true, modifier = Modifier.padding(top = 8.dp, end = 6.dp))
        Discover.moods.forEachIndexed { i, (name, _) ->
            Chip(name, HomeState.mood == i, onClick = { HomeState.mood = if (HomeState.mood == i) null else i })
        }
        if (Store.feature("tonightPick")) GoldChip("✦ ماذا نشاهد الليلة؟") { Nav.go(Route.Tool("tonight")) }
    }
}

@Composable
fun GoldChip(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onClick = onClick)
            .background(if (focused) C.GoldLight else Color(0x14F6F1E4))
            .border(1.dp, C.Gold, CircleShape)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) { T(text, 15.sp, color = if (focused) C.Sea else C.GoldLight, maxLines = 1) }
}

@Composable
private fun CatalogRow(def: CatalogDef) {
    val key = def.key()
    val list = HomeState.items[key]?.filter { it.id !in Library.hidden }
    LaunchedEffect(key) { if (HomeState.items[key] == null) HomeState.items[key] = Addons.catalog(def) }
    if (list != null && list.isEmpty()) return
    RowTitle(catalogTitle(def))
    if (list == null) {
        Spacer(Modifier.height(PosterSize.h + 60.dp))
        return
    }
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
            val k = "$key|${m.id}"
            PosterCard(
                m,
                rotate = HomeState.focusedRow == key,
                index = i,
                onFocused = { HomeState.focused = m; HomeState.lastKey = k; HomeState.focusedRow = key; Backdrop.show(m.background ?: m.poster) },
                onClick = { Nav.go(Route.Detail(m)) },
                modifier = restoreFocus(k, first = false),
            )
        }
    }
}

/** Puts the focus back on the card that had it before leaving the page (or the first card). */
@Composable
fun restoreFocus(key: String, first: Boolean): Modifier {
    val wanted = HomeState.lastKey?.let { it == key } ?: first
    if (!wanted) return Modifier
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { r.requestFocus() } }
    return Modifier.focusRequester(r)
}

@Composable
fun RowTitle(text: String) {
    T(text, 20.sp, weight = FontWeight.Bold, shadow = true, modifier = Modifier.padding(start = 30.dp, top = 8.dp))
}

/** The focused title over its clear artwork: kind, name (logo when there is one), facts and story. */
@Composable
fun Hero(m: MetaPreview?, modifier: Modifier) {
    Column(modifier) {
        if (m == null) return@Column
        T(if (m.type == "series") "مسلسل" else "فيلم", 14.sp, color = C.GoldLight, shadow = true)
        if (!m.logo.isNullOrBlank()) {
            Img(m.logo, 340.dp, 90.dp, Modifier.width(340.dp).height(90.dp), ContentScale.Fit)
        } else {
            T(m.name, 46.sp, weight = FontWeight.Bold, maxLines = 1, shadow = true)
        }
        Spacer(Modifier.height(8.dp))
        MetaLine(listOfNotNull(m.rating?.let { "★ $it" }, m.year?.take(4), m.genres.take(3).joinToString("، ").ifBlank { null }))
        Spacer(Modifier.height(8.dp))
        T(m.description.orEmpty(), 16.sp, color = C.Ink, maxLines = 3, lineHeight = 26.sp, shadow = true, modifier = Modifier.width(560.dp))
    }
}

/** «المكتبة»: everything in progress, as a grid. */
@Composable
fun LibraryScreen() {
    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        T("المكتبة", 34.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 30.dp))
        T("الأعمال التي بدأت مشاهدتها والتي شوهدت", 15.sp, color = C.Muted, modifier = Modifier.padding(start = 30.dp, top = 4.dp))
        val all = (Store.progress.map { it.meta } + Library.titles.values.sortedByDescending { it.last }.map { it.meta }).distinctBy { it.id }
        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize()) { Loading("لا توجد أعمال بعد. ما يُشاهَد يظهر هنا.") }
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(PosterSize.w),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(all, key = { it.id }) { m ->
                PosterCard(m, onFocused = { Backdrop.show(m.background) }, onClick = { Nav.go(Route.Detail(m)) })
            }
        }
    }
}

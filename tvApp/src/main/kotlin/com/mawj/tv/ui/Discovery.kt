@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Discover
import com.mawj.tv.data.Island
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.Store
import com.mawj.tv.data.TmdbWork
import kotlinx.coroutines.delay
import java.time.LocalDate

private object BrowseState {
    val focused = androidx.compose.runtime.mutableStateMapOf<String, MetaPreview>()
    val row = androidx.compose.runtime.mutableStateMapOf<String, String>()
    var islandTab by mutableIntStateOf(0)
}

/** «أفلام» and «مسلسلات»: the focused title on top over its clear artwork, TMDB rows under it. */
@Composable
fun BrowseScreen(kind: String) {
    val year = LocalDate.now().year
    val rows: List<Triple<String, String, suspend () -> List<TmdbWork>>> = remember(kind) {
        val k = kind
        val base = listOf(
            Triple("الرائج الآن", "trend:$k", suspend { Discover.trending(k) }),
            Triple("الأعلى تقييماً", "top:$k", suspend { Discover.topRated(k) }),
            Triple("جديد $year", "y$year:$k", suspend {
                Discover.discover(k, if (k == "movie") "primary_release_year=$year" else "first_air_date_year=$year")
            }),
            Triple("من ${year - 1}", "y${year - 1}:$k", suspend {
                Discover.discover(k, if (k == "movie") "primary_release_year=${year - 1}&vote_count.gte=300" else "first_air_date_year=${year - 1}&vote_count.gte=80")
            }),
        )
        val platforms = if (k == "tv") listOf("Netflix" to "with_networks=213", "HBO" to "with_networks=49", "Apple TV+" to "with_networks=2552", "Disney+" to "with_networks=2739")
        else listOf("Netflix" to "with_watch_providers=8&watch_region=US", "Prime Video" to "with_watch_providers=9&watch_region=US", "Disney+" to "with_watch_providers=337&watch_region=US")
        val genres = if (k == "tv") listOf(10759, 35, 80, 18, 10765, 9648, 16) else listOf(28, 35, 53, 878, 27, 16, 10749, 99)
        base + platforms.map { (n, q) -> Triple(n, "pf:$k:$n", suspend { Discover.discover(k, q) }) } +
            genres.map { g -> Triple(com.mawj.tv.data.Genres.name(g) ?: "", "g:$k:$g", suspend { Discover.discover(k, "with_genres=$g") }) }
    }
    Column(Modifier.fillMaxSize()) {
        Hero(BrowseState.focused[kind], Modifier.fillMaxWidth().height(215.dp).padding(start = 24.dp, end = 32.dp, top = 30.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(rows, key = { _, r -> r.second }) { i, (title, key, load) ->
                WorksRow(
                    title, key, BrowseState.row[kind], { BrowseState.row[kind] = it },
                    onFocusWork = { w -> BrowseState.focused[kind] = w.asPreview(); Backdrop.show(w.backdrop ?: w.poster) },
                    first = i == 0, rank = i == 0 && Store.feature("top10Rank"), load = load,
                )
            }
        }
    }
}

// ---------------- «الجزر» ----------------

@Composable
fun IslandsScreen() {
    val tabs = Discover.islands
    val tab = BrowseState.islandTab.coerceIn(0, tabs.lastIndex)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 30.dp, bottom = 50.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                PageHead("الجزر", "الاكتشاف كله في مكان واحد: تبويب، ثم بطاقات.", Modifier.weight(1f))
                if (Store.feature("countdown")) Countdown()
            }
        }
        item {
            LazyRow(Modifier.padding(top = 18.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                itemsIndexed(tabs) { i, t ->
                    Chip(t.title, i == tab, onClick = { BrowseState.islandTab = i }, modifier = if (i == 0) restoreFirst() else Modifier)
                }
            }
        }
        val cards = tabs[tab].cards
        items(cards.chunked(3), key = { r -> tabs[tab].title + r.first().title }) { row ->
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { IslandCard(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One island card. «الكتالوج المتحرك» for a TV: only the focused card moves (its three pictures
 * cross-fade every 3 seconds, a light passes over it); every other card is a still picture.
 */
@Composable
private fun IslandCard(island: Island, modifier: Modifier) {
    val works = rememberWorks("isl:${island.title}:${island.query}") { Discover.of(island) }
    val pics = works.orEmpty().mapNotNull { it.backdrop }.take(3)
    var focused by remember { mutableStateOf(false) }
    var at by remember { mutableIntStateOf(0) }
    val animated = focused && Store.feature("animatedCatalog") && pics.size > 1
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        delay(650)
        while (true) { delay(3_000); at = (at + 1) % pics.size }
    }
    Box(
        modifier.height(118.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(16.dp), scale = 1.04f, onClick = {
                when {
                    island.personId != null -> Nav.go(Route.Person(island.personId, island.title))
                    island.companyId != null -> Nav.go(Route.Company(island.companyId, island.title))
                    else -> Nav.go(Route.Works(island.title, island))
                }
            })
            .background(C.Card),
    ) {
        MotionLayer(animated) {
            Crossfade(pics.getOrNull(at), animationSpec = tween(900), label = "island") { url ->
                Img(url, 300.dp, 120.dp, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x33070D14), 0.55f to Color(0x99070D14), 1f to Color(0xE6070D14))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            T(island.title, 21.sp, weight = FontWeight.Bold, color = if (focused) C.GoldLight else C.Ink, shadow = true, maxLines = 1)
            works?.let { if (it.isNotEmpty()) T("${it.size}+ عملاً", 11.sp, color = C.Muted, num = false) }
        }
    }
}

/** «العد التنازلي»: films coming soon, with the days left under each. */
@Composable
private fun Countdown() {
    var list by remember { mutableStateOf<List<Pair<TmdbWork, Long>>>(emptyList()) }
    LaunchedEffect(Unit) { list = runCatching { Discover.countdown() }.getOrDefault(emptyList()) }
    if (list.isEmpty()) return
    val open = rememberOpener()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        T("⏳ العد التنازلي", 14.sp, color = C.Muted)
        list.take(4).forEach { (w, days) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).tvFocus(shape = CircleShape, scale = 1.1f, onClick = { open(w) }).border(2.dp, C.Gold, CircleShape)) {
                    Img(w.poster, 58.dp, 58.dp, Modifier.fillMaxSize())
                }
                T("بعد $days يوماً", 10.sp, color = C.GoldLight, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A list from an island card (two pages), as a grid of posters. */
@Composable
fun WorksScreen(title: String, island: Island) {
    val works = rememberWorks("works:${island.title}:${island.query}") {
        (Discover.of(island) + Discover.list(island.path, island.query + "&page=2", island.kind)).distinctBy { it.tmdbId }
    }
    val open = rememberOpener()
    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        PageHead(title, works?.let { "${it.size} عملاً" }, Modifier.padding(start = 30.dp))
        if (works == null) { Loading(); return@Column }
        LazyVerticalGrid(
            GridCells.Adaptive(PosterSize.w + 6.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(works, key = { _, w -> w.type + w.tmdbId }) { i, w ->
                PosterCard(w.asPreview(), onFocused = { Backdrop.show(w.backdrop ?: w.poster) }, onClick = { open(w) },
                    modifier = if (i == 0) restoreFirst() else Modifier)
            }
        }
    }
}

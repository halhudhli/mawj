@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.CatalogDef
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
    val focus = HomeState.focused
    Column(Modifier.fillMaxSize()) {
        Hero(focus, Modifier.fillMaxWidth().height(205.dp).padding(start = 24.dp, end = 32.dp, top = 34.dp))
        if (rows == null) {
            Loading()
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (Store.progress.isNotEmpty()) {
                item(key = "continue") {
                    RowTitle("متابعة المشاهدة")
                    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(Store.progress, key = { _, p -> p.meta.id }) { i, p ->
                            val k = "c|${p.meta.id}"
                            val left = ((p.durationMs - p.positionMs) / 60_000L).coerceAtLeast(0)
                            WideCard(
                                p.meta.name, p.meta.background ?: p.meta.poster, p.fraction,
                                badge = if (p.durationMs > 0) "متبقٍ $left د" else null,
                                kicker = if (p.season > 0) "S${p.season} E${p.episode}" else null,
                                onFocused = { HomeState.focused = p.meta; HomeState.lastKey = k; HomeState.focusedRow = "continue"; Backdrop.show(p.meta.background) },
                                onClick = { Nav.go(Route.Detail(p.meta)) },
                                modifier = restoreFocus(k, first = i == 0),
                            )
                        }
                    }
                }
            }
            items(rows, key = { it.key() }) { def -> CatalogRow(def, firstRow = Store.progress.isEmpty() && def == rows.first()) }
        }
    }
}

@Composable
private fun CatalogRow(def: CatalogDef, firstRow: Boolean) {
    val key = def.key()
    val list = HomeState.items[key]
    LaunchedEffect(key) { if (list == null) HomeState.items[key] = Addons.catalog(def) }
    if (list != null && list.isEmpty()) return
    RowTitle(catalogTitle(def))
    if (list == null) {
        Spacer(Modifier.height(285.dp))
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
                modifier = restoreFocus(k, first = firstRow && i == 0),
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
    T(text, 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 30.dp, top = 8.dp))
}

@Composable
private fun Hero(m: MetaPreview?, modifier: Modifier) {
    Column(modifier) {
        if (m == null) return@Column
        if (!m.logo.isNullOrBlank()) {
            Img(m.logo, 340.dp, 96.dp, Modifier.width(340.dp).height(96.dp), ContentScale.Fit)
        } else {
            T(m.name, 42.sp, weight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        MetaLine(listOfNotNull(m.year, m.genres.take(2).joinToString(" · "), m.rating?.let { "★ $it" }))
        Spacer(Modifier.height(10.dp))
        T(m.description.orEmpty(), 16.sp, color = C.Muted, maxLines = 3, lineHeight = 26.sp, modifier = Modifier.width(560.dp))
    }
}

/** «المكتبة»: everything in progress, as a grid. */
@Composable
fun LibraryScreen() {
    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        T("المكتبة", 34.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 30.dp))
        T("الأعمال التي بدأت مشاهدتها", 15.sp, color = C.Muted, modifier = Modifier.padding(start = 30.dp, top = 4.dp))
        if (Store.progress.isEmpty()) {
            Box(Modifier.fillMaxSize()) { Loading("لا توجد أعمال بعد. ما يُشاهَد يظهر هنا.") }
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(Store.progress, key = { it.meta.id }) { p ->
                PosterCard(p.meta, onFocused = { Backdrop.show(p.meta.background) }, onClick = { Nav.go(Route.Detail(p.meta)) })
            }
        }
    }
}

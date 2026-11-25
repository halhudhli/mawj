@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Library
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.TmdbWork
import com.mawj.tv.data.toPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A TMDB title shown like any other (its id is resolved to IMDb only when opened). */
fun TmdbWork.asPreview(): MetaPreview = MetaPreview(
    id = "tmdb:$tmdbId", type = if (type == "tv") "series" else "movie", name = name, poster = poster, background = backdrop,
    logo = null, year = year, rating = if (rating > 0) String.format(java.util.Locale.US, "%.1f", rating) else null,
    description = overview, genres = genres.mapNotNull(com.mawj.tv.data.Genres::name),
)

/** Lists loaded once per run, shared by every page (coming back never reloads). */
object Lists {
    val works = mutableStateMapOf<String, List<TmdbWork>>()
}

@Composable
fun rememberWorks(key: String, load: suspend () -> List<TmdbWork>): List<TmdbWork>? {
    val list = Lists.works[key]
    LaunchedEffect(key) { if (list == null) Lists.works[key] = runCatching { load() }.getOrDefault(emptyList()) }
    return Lists.works[key]?.filter { "tmdb:${it.tmdbId}" !in Library.hidden }
}

/** Opens a TMDB title: its IMDb id is looked up first, so the addons can serve it. */
@Composable
fun rememberOpener(): (TmdbWork) -> Unit {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    return { w ->
        if (!busy) {
            busy = true
            scope.launch {
                w.toPreview()?.let { Nav.go(Route.Detail(it.copy(description = w.overview, genres = w.genres.mapNotNull(com.mawj.tv.data.Genres::name)))) }
                busy = false
            }
        }
    }
}

/** A titled row of TMDB posters. Only the focused row changes its posters; only the focused poster moves. */
@Composable
fun WorksRow(
    title: String,
    key: String,
    focusedRow: String?,
    onRowFocus: (String) -> Unit,
    onFocusWork: (TmdbWork) -> Unit = { Backdrop.show(it.backdrop ?: it.poster) },
    first: Boolean = false,
    rank: Boolean = false,
    load: suspend () -> List<TmdbWork>,
) {
    val list = rememberWorks(key, load)
    if (list != null && list.isEmpty()) return
    val open = rememberOpener()
    RowTitle(title)
    if (list == null) {
        Box(Modifier.height(PosterSize.h + 60.dp))
        return
    }
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(if (rank) 34.dp else 15.dp)) {
        itemsIndexed(list, key = { _, w -> w.type + w.tmdbId }) { i, w ->
            Box {
                PosterCard(
                    w.asPreview(),
                    rotate = focusedRow == key,
                    index = i,
                    onFocused = { onRowFocus(key); onFocusWork(w) },
                    onClick = { open(w) },
                    modifier = if (first && i == 0) restoreFirst() else Modifier,
                )
                if (rank && i < 10) T("${i + 1}", 64.sp, color = Color(0x99F1DDA6), weight = FontWeight.Bold, num = true, shadow = true,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp).offset(x = (-26).dp))
            }
        }
    }
}

@Composable
fun restoreFirst(): Modifier {
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { r.requestFocus() } }
    return Modifier.focusRequester(r)
}

/** A page heading with an optional line under it. */
@Composable
fun PageHead(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        T(title, 34.sp, weight = FontWeight.Bold, shadow = true)
        sub?.let { T(it, 15.sp, color = C.Muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** A see-through card (a little above transparent), as on the tablet. */
@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(Color(0xCC111A24), RoundedCornerShape(18.dp)).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}

/** A panel that opens from the side of the screen over the page; Back closes it. */
@Composable
fun SidePanel(title: String, onClose: () -> Unit, width: Dp = 380.dp, content: @Composable ColumnScope.() -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0x66070D14)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.padding(24.dp).width(width).fillMaxHeight()
                .background(Color(0xD1070D14), RoundedCornerShape(20.dp)).border(1.dp, Color(0x59D6B36A), RoundedCornerShape(20.dp))
                .padding(22.dp),
        ) {
            T(title, 22.sp, color = C.GoldLight, weight = FontWeight.Bold)
            Box(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Column(content = content) }
            }
        }
    }
}

/** A focusable tile with an animated icon and a label (settings, tools). */
@Composable
fun IconTile(icon: String, label: String, sub: String? = null, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(14.dp), scale = 1.03f, onClick = onClick)
            .background(if (selected) C.Gold else if (focused) Color(0x2EF1DDA6) else Color(0xCC111A24))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(icon, if (selected) C.Sea else C.Gold, focused, Modifier.size(22.dp))
        Box(Modifier.width(10.dp))
        Column {
            T(label, 15.sp, weight = FontWeight.SemiBold, color = if (selected) C.Sea else C.Ink, maxLines = 1)
            sub?.let { T(it, 11.sp, color = if (selected) Color(0xFF4A3C1C) else C.Muted, maxLines = 1) }
        }
    }
}

/** A switch line: title, note, and «مفعّل / معطّل». */
@Composable
fun SwitchRow(title: String, note: String, key: String) {
    val on = com.mawj.tv.data.Store.feature(key)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.01f, onClick = { com.mawj.tv.data.Store.setFeature(key, !on) })
            .background(if (focused) Color(0x24F1DDA6) else Color(0x99111A24))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
            T(note, 11.sp, color = C.Dim, maxLines = 2)
        }
        Box(
            Modifier.size(46.dp, 26.dp).background(if (on) C.Gold else Color(0x33FFFFFF), CircleShape).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterStart else Alignment.CenterEnd,
        ) { Box(Modifier.size(20.dp).background(if (on) C.Sea else C.Muted, CircleShape)) }
    }
}

/** A number in a box with a label under it (profile, company). */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xCC111A24), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        T(value, 24.sp, weight = FontWeight.Bold, num = true)
        T(label, 12.sp, color = C.Muted)
    }
}

/** A round photo (or initial) with a name under it. */
@Composable
fun PersonDot(name: String, photo: String?, sub: String? = null, size: Dp = 84.dp, onClick: () -> Unit) {
    Column(Modifier.width(size + 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).tvFocus(shape = CircleShape, scale = 1.08f, onClick = onClick).background(C.Card, CircleShape), contentAlignment = Alignment.Center) {
            if (photo != null) Img(photo, size, size, Modifier.fillMaxSize()) else T(name.take(1), 26.sp, color = C.Gold, weight = FontWeight.Bold)
        }
        T(name, 12.sp, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        sub?.let { T(it, 10.sp, color = C.Dim, maxLines = 1, align = TextAlign.Center) }
    }
}

/** Fills the box with a soft dark scrim over artwork. */
@Composable
fun BoxScope.Scrim(alpha: Float = 0.6f) {
    Box(Modifier.matchParentSize().background(Color(0xFF070D14).copy(alpha = alpha)))
}

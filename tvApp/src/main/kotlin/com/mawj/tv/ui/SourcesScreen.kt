package com.mawj.tv.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Episode
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Store
import com.mawj.tv.data.Stream
import kotlinx.coroutines.delay

private enum class Filter(val label: String) { All("الكل"), UHD("4K"), FHD("1080p"), Arabic("فيها عربي") }

@Composable
fun SourcesScreen(meta: MetaPreview, episode: Episode?) {
    val videoId = episode?.id ?: meta.id
    var streams by remember(videoId) { mutableStateOf<List<Stream>?>(null) }
    LaunchedEffect(videoId) { streams = Addons.streams(meta.type, videoId) }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    val all = streams
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp, 105.dp).background(C.Card, RoundedCornerShape(10.dp))) {
                Img(meta.poster, 70.dp, 105.dp, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(18.dp))
            Column {
                T(meta.name, 32.sp, weight = FontWeight.Bold, maxLines = 1)
                if (episode != null) T("الموسم ${episode.season} · الحلقة ${episode.number} — ${episode.title}", 16.sp, color = C.Muted, maxLines = 1)
            }
        }
        if (all == null) {
            Loading("جارٍ جمع المصادر من الإضافات…")
            return@Column
        }
        if (all.isEmpty()) {
            Loading("لم يُعثر على مصادر. تُضاف إضافات المصادر من الإعدادات.")
            return@Column
        }
        val shown = when (filter) {
            Filter.All -> all
            Filter.UHD -> all.filter { it.quality >= 2160 }
            Filter.FHD -> all.filter { it.quality in 1080 until 2160 }
            Filter.Arabic -> all.filter { it.arabic }
        }
        Row(Modifier.padding(top = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Filter.entries.forEach { f ->
                val count = when (f) {
                    Filter.All -> all.size
                    Filter.UHD -> all.count { it.quality >= 2160 }
                    Filter.FHD -> all.count { it.quality in 1080 until 2160 }
                    Filter.Arabic -> all.count { it.arabic }
                }
                Chip("${f.label} $count", f == filter, onClick = { filter = f })
            }
        }
        val first = remember { FocusRequester() }
        LaunchedEffect(all) { delay(50); runCatching { first.requestFocus() } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(shown, key = { i, s -> "$i|${s.url}" }) { i, s ->
                SourceRow(s, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    Nav.go(Route.Player(PlayRequest(meta, videoId, episode, s, Store.positionFor(videoId))))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: Stream, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .tvFocus(shape = RoundedCornerShape(14.dp), scale = 1.015f, onClick = onClick)
            .background(C.Raised)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T(qualityLabel(s.quality), 19.sp, color = C.GoldLight, weight = FontWeight.Bold, align = TextAlign.Center)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(s.title.lineSequence().firstOrNull().orEmpty().ifBlank { s.name }, 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.dolbyVision) Tag("Dolby Vision", gold = true)
                if (s.hdr) Tag("HDR", gold = true)
                if (s.arabic) Tag("عربي")
                T(s.name, 12.sp, color = C.Dim, maxLines = 1)
            }
        }
        Spacer(Modifier.width(14.dp))
        T(s.sizeLabel.orEmpty(), 15.sp, color = C.Muted, maxLines = 1)
        Spacer(Modifier.width(18.dp))
        T(s.addonName, 13.sp, color = C.Dim, maxLines = 1, modifier = Modifier.width(120.dp))
    }
}

fun qualityLabel(q: Int) = when {
    q >= 2160 -> "4K"
    q >= 1440 -> "1440p"
    q >= 1080 -> "1080p"
    q >= 720 -> "720p"
    q > 0 -> "${q}p"
    else -> "—"
}

// ---------------- search ----------------

/** What the search page shows; the phone link can type into it too. */
object SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<MetaPreview>>(emptyList())
}

private val keys = listOf(
    "ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط",
    "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "ه", "و", "ي", "ة", "ى", "ء", "أ",
    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p",
    "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "1", "2", "3", "4", "5", "6",
)

@Composable
fun SearchScreen() {
    val q = SearchState.query
    LaunchedEffect(q) {
        delay(450)
        SearchState.results = if (q.trim().length >= 2) Addons.search(q) else emptyList()
    }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 32.dp, top = 36.dp)) {
        Column(Modifier.width(430.dp)) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).background(C.Raised, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) { T(q.ifEmpty { "ابحث عن فيلم أو مسلسل" }, 20.sp, color = if (q.isEmpty()) C.Dim else C.Ink, maxLines = 1) }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(GridCells.Fixed(8), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(keys.size) { i ->
                    Key(keys[i], if (i == 0) Modifier.focusRequester(firstKey) else Modifier) { SearchState.query += keys[i] }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(4) }) { Key("مسافة") { SearchState.query += " " } }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Key("حذف") { SearchState.query = q.dropLast(1) } }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Key("مسح") { SearchState.query = "" } }
            }
            T("يمكن الكتابة من الجوال أيضاً عبر «الربط بالجوال» في الإعدادات.", 13.sp, color = C.Dim, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.width(28.dp))
        LazyVerticalGrid(GridCells.Adaptive(132.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(SearchState.results.size) { i ->
                val m = SearchState.results[i]
                PosterCard(m, onFocused = { Backdrop.show(m.background ?: m.poster) }, onClick = { Nav.go(Route.Detail(m)) })
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.08f, onClick = onClick)
            .background(if (focused) C.Ink else Color(0x10F6F1E4)),
        contentAlignment = Alignment.Center,
    ) { T(label, 17.sp, color = if (focused) C.Sea else C.Ink) }
}

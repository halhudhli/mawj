package com.mawj.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
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

private enum class Filter(val label: String) { All("الكل"), UHD("4K"), FHD("1080p"), HD("720p"), Arabic("فيها عربي"), Hdr("HDR · DV") }

private fun Filter.accepts(s: Stream) = when (this) {
    Filter.All -> true
    Filter.UHD -> s.quality >= 2160
    Filter.FHD -> s.quality in 1080 until 2160
    Filter.HD -> s.quality in 1 until 1080
    Filter.Arabic -> s.arabic
    Filter.Hdr -> s.hdr || s.dolbyVision
}

/** «الأنسب»: the highest quality that is ready at once, HDR first, at a sensible size. */
private fun pickBest(list: List<Stream>): Stream? {
    val guard = Store.feature("codecGuard")
    val pool = list.filter { it.source != "CAM" && it.quality > 0 && !(guard && com.mawj.tv.data.Caps.slowReason(it) != null) }
    val ready = pool.filter { it.cached }.ifEmpty { pool }
    return ready.maxWithOrNull(
        compareBy<Stream> { minOf(it.quality, 2160) }
            .thenBy { if (it.dolbyVision || it.hdr) 1 else 0 }
            .thenBy { if (it.sizeBytes in 1..32L * 1_073_741_824L) 1 else 0 }
            .thenBy { it.seeders ?: 0 },
    )
}

private val ORDER = compareByDescending<Stream> { it.quality }
    .thenByDescending { it.dolbyVision || it.hdr }
    .thenByDescending { it.cached }
    .thenByDescending { it.sizeBytes }

@Composable
fun SourcesScreen(meta: MetaPreview, episode: Episode?) {
    val videoId = episode?.id ?: meta.id
    // Each addon loads by itself: its sources show the moment it answers.
    val addons = remember(videoId) { mutableStateListOf<String>() }
    val results = remember(videoId) { mutableStateMapOf<String, List<Stream>>() }
    val failed = remember(videoId) { mutableStateListOf<String>() }
    var asked by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        val list = Addons.streamAddons(meta.type, videoId)
        addons.clear(); addons.addAll(list.map { it.name })
        asked = true
        list.forEach { a ->
            launch {
                val got = Addons.streamsFrom(a, meta.type, videoId)
                if (got == null) failed.add(a.name) else results[a.name] = got
            }
        }
    }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var addonFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val all = results.values.flatten().filter { !(Store.feature("hideCam") && it.source == "CAM") }
    val best = if (Store.bestSource) pickBest(all) else null
    val shown = all.filter { filter.accepts(it) && (addonFilter == null || it.addonName == addonFilter) }
        .sortedWith(ORDER)
        .let { l -> if (best != null && best in l) listOf(best) + (l - best) else l }
    val loading = !asked || addons.any { it !in results && it !in failed }

    LaunchedEffect(meta.id) { Backdrop.show(meta.background ?: meta.poster) }
    Box(Modifier.fillMaxSize()) {
        // The artwork stays clear behind the logo (the right, in Arabic) and darkens under the list.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF7050810), 0.6f to Color(0xD9050810), 0.8f to Color(0x66050810), 1f to Color(0x33050810))))
        Row(Modifier.fillMaxSize()) {
            SourcesSide(meta, episode, addons, results, failed, Modifier.width(250.dp).fillMaxHeight().padding(start = 34.dp, top = 30.dp))
            Spacer(Modifier.width(26.dp))
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 34.dp, top = 30.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("اختيار المصدر", 23.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    T(all.size.toString(), 14.sp, color = C.Dim, num = true, modifier = Modifier.padding(bottom = 3.dp))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Filter.entries.forEach { f ->
                        val n = all.count { f.accepts(it) }
                        if (f == Filter.All || n > 0) CountChip(f.label, n, f == filter) { filter = f }
                    }
                }
                if (addons.size > 1) {
                    Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CountChip("كل الإضافات", null, addonFilter == null, small = true) { addonFilter = null }
                        addons.filter { (results[it]?.size ?: 0) > 0 }.forEach { name ->
                            CountChip(name, results[name]?.size, addonFilter == name, small = true) { addonFilter = name }
                        }
                    }
                }
                if (all.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Loading(if (loading) "جارٍ جمع المصادر من الإضافات…" else "لم يُعثر على مصادر. تُضاف إضافات المصادر من الإعدادات.")
                    }
                } else {
                    val first = remember(videoId) { FocusRequester() }
                    var focusedOnce by remember(videoId) { mutableStateOf(false) }
                    LaunchedEffect(shown.isNotEmpty()) {
                        if (shown.isNotEmpty() && !focusedOnce) { delay(60); runCatching { first.requestFocus() }; focusedOnce = true }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(top = 13.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                    ) {
                        itemsIndexed(shown, key = { _, s -> s.addonName + "|" + s.url }) { i, s ->
                            SourceRow(s, s == best, if (i == 0) Modifier.focusRequester(first) else Modifier) {
                                Store.rememberSource(meta.id, s)
                                Nav.go(Route.Player(PlayRequest(meta, videoId, episode, s, Store.positionFor(videoId))))
                            }
                        }
                    }
                }
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Hint("OK", "تشغيل"); Hint("▲ ▼", "المصادر"); Hint("◀ ▶", "الفلاتر"); Hint("رجوع", "صفحة العمل")
                }
            }
        }
    }
}

@Composable
private fun SourcesSide(
    meta: MetaPreview,
    episode: Episode?,
    addons: List<String>,
    results: Map<String, List<Stream>>,
    failed: List<String>,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(modifier) {
        // The work's logo over its artwork (the artwork is the page background).
        if (!meta.logo.isNullOrBlank()) {
            Img(meta.logo, 216.dp, 96.dp, Modifier.size(216.dp, 96.dp), androidx.compose.ui.layout.ContentScale.Fit)
        } else {
            T(meta.name, 30.sp, weight = FontWeight.Bold, serif = true, maxLines = 2, lineHeight = 34.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (episode != null) {
            T("الموسم ${episode.season} · الحلقة ${episode.number}" + episode.title.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(), 14.sp, color = C.Muted, maxLines = 1)
        }
        listOfNotNull(meta.year?.take(4), meta.rating?.let { "IMDb $it" }).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
            T(it, 11.sp, color = C.Dim, num = true, modifier = Modifier.padding(top = 3.dp))
        }
        Store.rememberedSource(meta.id)?.let { saved ->
            Column(
                Modifier.padding(top = 14.dp).fillMaxWidth()
                    .background(Color(0x1AD6B36A), RoundedCornerShape(10.dp)).border(1.dp, Color(0x73D6B36A), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                T("آخر مصدر مُستخدَم لهذا العمل", 10.sp, color = C.GoldLight)
                T(listOfNotNull(qualityLabel(saved.quality).takeIf { it != "—" }, saved.source, saved.addonName).joinToString(" · "), 13.sp, num = true, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                var focused by remember { mutableStateOf(false) }
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().height(34.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .tvFocus(shape = CircleShape, scale = 1.04f, onClick = { scope.launch { if (!resumeSameSource(meta, episode)) Unit } })
                        .background(Brush.horizontalGradient(listOf(Color(0xFFC9A45A), Color(0xFFECD59A)))),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MIcon("play-circle-start", C.Sea, focused, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    T("المتابعة بنفس المصدر", 13.sp, color = C.Sea, weight = FontWeight.Bold)
                }
            }
        }
        if (addons.isNotEmpty()) {
            T("الإضافات", 10.sp, color = C.Dim, modifier = Modifier.padding(top = 14.dp, bottom = 3.dp))
            addons.forEach { name ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 11.sp, color = C.Muted, num = true, maxLines = 1, modifier = Modifier.weight(1f))
                    when {
                        name in failed -> T("تعذّر", 10.sp, color = C.Red)
                        results[name] != null -> T("✓ ${results[name]!!.size}", 11.sp, color = C.Green, num = true)
                        else -> T("…", 13.sp, color = C.Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int?, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onClick = onClick)
            .background(if (focused) C.Ink else if (selected) C.Gold else Color(0xB3111A24))
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 4.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (focused || selected) C.Sea else C.Muted
        T(label, if (small) 11.sp else 12.sp, color = ink, weight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            T(count.toString(), if (small) 11.sp else 12.sp, color = if (focused || selected) Color(0xFF3B2C10) else C.Dim, num = true, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SourceRow(s: Stream, isBest: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = if (isBest) 6.dp else 0.dp)) {
        Row(
            modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.012f, onClick = onClick)
                .background(if (focused) Color(0xF21C2632) else Color(0xDB111A24))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val label = qualityLabel(s.quality)
                T(label, if (s.quality >= 2160) 23.sp else 19.sp, weight = FontWeight.Bold, num = true,
                    color = if (s.quality >= 2160) C.GoldLight else if (s.quality >= 1080) C.Ink else C.Muted)
                s.hdrLabel?.let { T(it, 9.sp, color = C.Gold, num = true, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(s.releaseLine, 15.sp, weight = FontWeight.SemiBold, num = true, maxLines = 1)
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.audio?.let { SrcTag(it, gold = "Atmos" in it || "TrueHD" in it) }
                    if (s.arabic) SrcTag("عربي", green = true)
                    if (s.source == "CAM") SrcTag("نسخة كام", red = true)
                    if (Store.feature("codecGuard")) com.mawj.tv.data.Caps.slowReason(s)?.let { SrcTag("بطيء على هذه الشاشة · $it", red = true) }
                }
                if (focused) {
                    val file = s.file ?: s.title.lineSequence().lastOrNull { it.isNotBlank() }
                    if (!file.isNullOrBlank()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            T(file, 10.sp, color = C.Dim, num = true, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), align = TextAlign.End)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
                T(s.sizeLabel.orEmpty(), 15.sp, weight = FontWeight.SemiBold, num = true, maxLines = 1)
                when {
                    s.cached -> T("⚡ جاهز فوراً", 10.sp, color = C.Green)
                    s.seeders != null -> T("👤 ${s.seeders}", 10.sp, color = C.Muted, num = true)
                }
                T(s.addonName, 10.sp, color = C.Dim, num = true, maxLines = 1)
            }
        }
        if (isBest) {
            T("الأنسب", 9.sp, color = C.Sea, weight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = (-7).dp).background(C.Gold, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun SrcTag(text: String, gold: Boolean = false, green: Boolean = false, red: Boolean = false) {
    val bg = when { gold -> Color(0x29D6B36A); green -> Color(0x245BBF7A); red -> Color(0x29E0574F); else -> Color(0xFF1D2A39) }
    val fg = when { gold -> C.GoldLight; green -> C.Green; red -> C.Red; else -> Color(0xFFC8D1DA) }
    T(text, 10.sp, color = fg, num = !green && !red, maxLines = 1, modifier = Modifier.background(bg, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
}

@Composable
private fun Hint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(key, 10.sp, num = true, modifier = Modifier.background(Color(0xFF172331), RoundedCornerShape(5.dp)).border(1.dp, Color(0xFF233244), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        Spacer(Modifier.width(5.dp))
        T(label, 11.sp, color = C.Dim)
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
        LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
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

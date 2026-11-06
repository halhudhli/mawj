package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.launch

private val FA1 = Color(0xFF6F8FBF)
private val FA2 = Color(0xFF9DB1CF)
private val FBg = Color(0xFF0B0D14)

private enum class FranchiseFilter(val label: String) { ALL("الكل"), NOT_STARTED("لم تبدأ"), IN_PROGRESS("لم تكتمل"), DONE("مكتملة 🏆") }

/** Everything Luxo opens over the app (series catalog, Trakt doctor, splash studio). */
@Composable
fun LuxoOverlayHost() {
    LaunchedEffect(Unit) { runCatching { luxoScheduleReminderChecks() } }
    LuxoOverlays.franchise?.let { key -> LuxoFranchiseDialog(startKey = key) }
    if (LuxoOverlays.traktDoctor) LuxoTraktDoctorDialog(onDismiss = { LuxoOverlays.traktDoctor = false })
    if (LuxoOverlays.guessGame) LuxoGuessGameDialog(onDismiss = { LuxoOverlays.guessGame = false })
    if (LuxoOverlays.choiceWheel) LuxoChoiceWheelDialog(onDismiss = { LuxoOverlays.choiceWheel = false })
    if (LuxoOverlays.remote) LuxoRemoteDialog(onDismiss = { LuxoOverlays.remote = false })
    if (LuxoOverlays.kids) LuxoKidsDialog(onDismiss = { LuxoOverlays.kids = false })
    if (LuxoOverlays.smartSource) LuxoSmartSourceDialog(onDismiss = { LuxoOverlays.smartSource = false })
    if (LuxoOverlays.youtube) LuxoYoutubeDialog(onDismiss = { LuxoOverlays.youtube = false })
    if (LuxoOverlays.unfinished) LuxoUnfinishedDialog(onDismiss = { LuxoOverlays.unfinished = false })
    if (LuxoOverlays.timeBudget) LuxoTimeBudgetDialog(onDismiss = { LuxoOverlays.timeBudget = false })
    if (LuxoOverlays.animeSeason) LuxoAnimeSeasonDialog(onDismiss = { LuxoOverlays.animeSeason = false })
    if (LuxoOverlays.watchTogether) LuxoWatchTogetherDialog(onDismiss = { LuxoOverlays.watchTogether = false })
    if (LuxoOverlays.tierList) LuxoTierListDialog(onDismiss = { LuxoOverlays.tierList = false })
    if (LuxoOverlays.secondScreen) LuxoSecondScreenDialog(onDismiss = { LuxoOverlays.secondScreen = false })
    LuxoOverlays.person?.let { pd ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { LuxoOverlays.person = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            com.nuvio.app.features.details.LuxoPersonPage(initial = pd, onOpenMeta = { m -> LuxoOverlays.person = null; LuxoOverlays.openMeta(m) })
        }
    }
    LuxoRateAfterHost()
    LuxoDeviceSync()
    LuxoEveningPreparer()
    LuxoRemoteServerHost()
    LuxoSeriesStatusSync()
    LuxoScreensaverHost()
}

@Composable
private fun LuxoFranchiseDialog(startKey: String) {
    Dialog(
        onDismissRequest = { LuxoOverlays.franchise = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var openKey by remember(startKey) { mutableStateOf(startKey.takeIf { it.isNotBlank() }) }
        val startedOnDetail = startKey.isNotBlank()
        Box(Modifier.fillMaxSize().background(FBg)) {
            val key = openKey
            if (key == null) {
                FranchiseHome(onOpen = { openKey = it }, onClose = { LuxoOverlays.franchise = null })
            } else {
                PlatformBackHandler(enabled = !startedOnDetail) { openKey = null }
                FranchiseDetail(key = key, onBack = { if (startedOnDetail) LuxoOverlays.franchise = null else openKey = null })
            }
        }
    }
}

@Composable
private fun FranchiseHome(onOpen: (String) -> Unit, onClose: () -> Unit) {
    val watchedState by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    var data by remember { mutableStateOf<Map<LuxoFranchiseKind, List<LuxoFranchise>>?>(null) }
    var kind by remember { mutableStateOf(LuxoFranchiseKind.MOVIES) }
    var filter by remember { mutableStateOf(FranchiseFilter.ALL) }
    val isWatched = rememberLuxoWatchedMatcher()
    LaunchedEffect(Unit) {
        val ids = watchedState.items.filter { it.type == "movie" && it.id.startsWith("tmdb:") }.mapNotNull { it.id.removePrefix("tmdb:").toIntOrNull() }
        data = LuxoFranchiseRepository.home(ids)
    }
    fun progress(f: LuxoFranchise): Pair<Int, Int> = f.parts.count { isWatched(it.asCredit()) } to f.parts.size
    Column(Modifier.fillMaxSize().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("السلاسل 🎞️", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("الأعمال المكوّنة من أكثر من جزء، بترتيب المشاهدة", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LuxoFranchiseKind.entries) { k ->
                val label = when (k) { LuxoFranchiseKind.MOVIES -> "🎬 أفلام"; LuxoFranchiseKind.SERIES -> "📺 مسلسلات"; LuxoFranchiseKind.ANIME -> "🎌 أنمي" }
                FChip(label + (data?.get(k)?.size?.let { " · $it" } ?: ""), kind == k) { kind = k }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (kind != LuxoFranchiseKind.ANIME) {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FranchiseFilter.entries) { f -> FChip(f.label, filter == f, small = true) { filter = f } }
            }
        }
        val list = data?.get(kind)
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("يتم تجميع السلاسل…", color = Color.White.copy(alpha = 0.6f))
                }
            }
            return@Column
        }
        val shown = list.filter { f ->
            if (kind == LuxoFranchiseKind.ANIME) return@filter true
            val (w, t) = progress(f)
            when (filter) {
                FranchiseFilter.ALL -> true
                FranchiseFilter.NOT_STARTED -> w == 0
                FranchiseFilter.IN_PROGRESS -> w in 1 until t
                FranchiseFilter.DONE -> t > 0 && w == t
            }
        }.sortedByDescending { if (kind == LuxoFranchiseKind.ANIME) 0 else progress(it).first }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = (maxWidth.value / 300f).toInt().coerceIn(1, 4)
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(shown, key = { it.key }) { f ->
                    val (w, t) = if (kind == LuxoFranchiseKind.ANIME) 0 to f.partCount else progress(f)
                    FranchiseCard(f, w, t) { onOpen(f.key) }
                }
            }
        }
    }
}

@Composable
private fun FranchiseCard(f: LuxoFranchise, watched: Int, total: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(model = f.backdrop ?: f.poster, contentDescription = f.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(f.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (f.kind) {
                        LuxoFranchiseKind.MOVIES -> "$total أفلام"
                        LuxoFranchiseKind.SERIES -> "$total أعمال"
                        LuxoFranchiseKind.ANIME -> "+$total أجزاء"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
            if (total > 0 && watched == total) {
                Text("🏆", fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
        }
        if (f.kind != LuxoFranchiseKind.ANIME && total > 0) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(watched.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(FA1, FA2))))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (watched == 0) "لم تبدأ بعد" else "تمت مشاهدة $watched من $total",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun FranchiseDetail(key: String, onBack: () -> Unit) {
    var franchise by remember(key) { mutableStateOf<LuxoFranchise?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    var chrono by remember(key) { mutableStateOf(false) }
    var chronoOrder by remember(key) { mutableStateOf<List<Int>?>(null) }
    var chronoBusy by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isWatched = rememberLuxoWatchedMatcher()
    LaunchedEffect(key) {
        franchise = LuxoFranchiseRepository.load(key)
        failed = franchise == null
    }
    val f = franchise
    fun open(part: LuxoFranchisePart) {
        if (opening) return
        opening = true
        scope.launch {
            LuxoFranchiseRepository.resolveToMeta(part)?.let { LuxoOverlays.openMeta(it) }
            opening = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (f == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (failed) Text("تعذّر تحميل السلسلة", color = Color.White.copy(alpha = 0.7f)) else CircularProgressIndicator(color = Color.White)
            }
        } else {
            val ordered = if (chrono && chronoOrder != null) chronoOrder!!.map { f.parts[it] } else f.parts
            val watchedCount = f.parts.count { isWatched(it.asCredit()) }
            val next = ordered.firstOrNull { !it.optional && !isWatched(it.asCredit()) } ?: ordered.firstOrNull { !isWatched(it.asCredit()) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        AsyncImage(model = f.backdrop ?: f.poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), FBg))))
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text(f.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Text(
                                "${f.parts.size} أجزاء · تمت مشاهدة $watchedCount" + if (watchedCount == f.parts.size && f.parts.isNotEmpty()) " · مكتملة 🏆" else "",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                f.overview?.let { ov ->
                    item { Text(ov, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 20.dp), maxLines = 5, overflow = TextOverflow.Ellipsis) }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        next?.let { n ->
                            Text(
                                (if (watchedCount == 0) "▶ ابدأ السلسلة: " else "▶ أكمل السلسلة: ") + n.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(FA1, FA2)))
                                    .clickable { open(n) }
                                    .padding(horizontal = 18.dp, vertical = 11.dp),
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FChip("ترتيب العرض", !chrono, small = true) { chrono = false }
                        FChip(if (chronoBusy) "يتم ترتيبها…" else "الترتيب الزمني للأحداث", chrono, small = true) {
                            if (chronoOrder != null) {
                                chrono = true
                            } else if (!chronoBusy) {
                                chronoBusy = true
                                scope.launch {
                                    chronoOrder = LuxoFranchiseRepository.chronologicalOrder(f)
                                    chrono = chronoOrder != null
                                    chronoBusy = false
                                }
                            }
                        }
                    }
                }
                itemsIndexed(ordered, key = { _, p -> p.key }) { index, part ->
                    val watched = isWatched(part.asCredit())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (part == next) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.04f))
                            .clickable { open(part) }
                            .padding(10.dp)
                            .alpha(if (part.optional && !watched) 0.65f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                        AsyncImage(model = part.poster, contentDescription = part.title, contentScale = ContentScale.Crop, modifier = Modifier.width(62.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(part.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(part.year, part.note, if (part.rating > 0) "★ ${(part.rating * 10).toInt() / 10.0}" else null).joinToString(" · "), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        if (watched) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF3DDC97)), contentAlignment = Alignment.Center) {
                                Text("✓", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        } else if (part == next) {
                            Text("التالي", color = FA2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp, start = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, null, tint = Color.White)
        }
        if (opening) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }
    }
}

@Composable
internal fun FChip(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = if (small) 13.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(listOf(FA1, FA2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
            )
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.4f else 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 8.dp),
    )
}

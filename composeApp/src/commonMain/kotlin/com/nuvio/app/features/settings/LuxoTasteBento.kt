package com.nuvio.app.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.details.luxoFlag
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.LuxoTasteFacts
import com.nuvio.app.features.luxo.LuxoTitleFacts
import com.nuvio.app.features.luxo.luxoShareImage
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.time.Instant

private val B1 = Color(0xFF6F8FBF)
private val B2 = Color(0xFF9DB1CF)
private val B3 = Color(0xFF5ED6C9)
private val BTile = Color(0xFF17151F)

private data class LuxoBentoStats(
    val titles: Int,
    val movies: Int,
    val series: Int,
    val hours: Int,
    val countries: List<Pair<String, Int>>,
    val decades: List<Pair<Int, Int>>,
    val hourBins: IntArray,
    val actors: List<Triple<String, String?, Int>>,
    val directors: List<Pair<String, Int>>,
    val studios: List<Pair<String, Int>>,
    val genres: List<Pair<String, Int>>,
    val months: List<Pair<String, String?>>,
    val nightShare: Float,
)

private fun localHour(ms: Long): Int = runCatching {
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).hour
}.getOrDefault(0)

private fun localMonth(ms: Long): String = runCatching {
    val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    "${d.year}-${d.month.ordinal + 1}"
}.getOrDefault("")

private val arMonths = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

private fun buildBento(items: List<WatchedItem>, facts: Map<String, LuxoTitleFacts>): LuxoBentoStats {
    val titleItems = items.groupBy { LuxoTasteFacts.keyOf(it) }
    val known = titleItems.mapNotNull { (k, list) -> facts[k]?.let { it to list } }
    val hourBins = IntArray(24)
    items.filter { it.markedAtEpochMs > 0 }.forEach { hourBins[localHour(it.markedAtEpochMs)]++ }
    val totalMarks = hourBins.sum().coerceAtLeast(1)
    val night = (0..4).sumOf { hourBins[it] }.toFloat() / totalMarks
    val hours = known.sumOf { (f, list) ->
        val rt = f.runtime ?: if (f.mediaType == "movie") 110 else 45
        if (f.mediaType == "movie") rt else rt * list.count { it.episode != null }.coerceAtLeast(1)
    } / 60
    fun <T> count(sel: (LuxoTitleFacts) -> List<T>) = known.flatMap { sel(it.first) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val actorPhotos = HashMap<String, String?>()
    known.forEach { (f, _) -> f.cast.forEachIndexed { i, n -> if (actorPhotos[n] == null) actorPhotos[n] = f.castPhotos.getOrNull(i) } }
    // Mood of each of the last 6 months = the most watched genre that month.
    val byMonth = items.filter { it.markedAtEpochMs > 0 }.groupBy { localMonth(it.markedAtEpochMs) }
    val monthKeys = byMonth.keys.filter { it.isNotBlank() }.sortedWith(compareBy({ it.substringBefore('-').toInt() }, { it.substringAfter('-').toInt() })).takeLast(6)
    val months = monthKeys.map { mk ->
        val g = byMonth[mk].orEmpty().mapNotNull { facts[LuxoTasteFacts.keyOf(it)] }.flatMap { it.genres }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        arMonths[(mk.substringAfter('-').toInt() - 1).coerceIn(0, 11)] to g
    }
    return LuxoBentoStats(
        titles = titleItems.size,
        movies = titleItems.keys.count { it.startsWith("movie:") },
        series = titleItems.keys.count { it.startsWith("tv:") },
        hours = hours,
        countries = count { it.countries }.map { it.key to it.value },
        decades = known.mapNotNull { it.first.year?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount().entries.sortedBy { it.key }.map { it.key to it.value },
        hourBins = hourBins,
        actors = count { it.cast.take(4) }.take(8).map { Triple(it.key, actorPhotos[it.key], it.value) },
        directors = count { it.directors }.filter { it.value > 1 }.take(5).map { it.key to it.value },
        studios = count { it.studios }.take(5).map { it.key to it.value },
        genres = count { it.genres }.take(6).map { it.key to it.value },
        months = months,
        nightShare = night,
    )
}

private fun moodOf(genre: String?): String = when {
    genre == null -> "🌫️ هدوء"
    "رعب" in genre -> "👻 ليالي الرعب"
    "جريمة" in genre || "غموض" in genre -> "🕵️ تحقيقات"
    "كوميدي" in genre -> "😄 ضحك"
    "دراما" in genre -> "🎭 دراما"
    "رسوم" in genre || "أنمي" in genre -> "✨ أنمي"
    "خيال" in genre -> "🚀 خيال"
    "حركة" in genre || "مغامرة" in genre -> "💥 أكشن"
    "رومان" in genre -> "💗 رومانسية"
    "وثائقي" in genre -> "📚 وثائقيات"
    "تاريخ" in genre || "حرب" in genre -> "🏛️ تاريخ"
    else -> "🎬 $genre"
}

/** The nickname shown at the top of the board, from the watching pattern. */
private fun nicknameOf(s: LuxoBentoStats): Pair<String, String> {
    val g = s.genres.firstOrNull()?.first.orEmpty()
    val base = when {
        "رعب" in g -> "صيّاد الرعب"
        "جريمة" in g || "غموض" in g -> "المحقق"
        "كوميدي" in g -> "عاشق الضحك"
        "رسوم" in g -> "رحّالة الأنمي"
        "خيال" in g -> "مستكشف العوالم"
        "حركة" in g || "مغامرة" in g -> "مطارد الإثارة"
        "دراما" in g -> "ذوّاق الدراما"
        "وثائقي" in g -> "الباحث"
        else -> "عاشق الشاشة"
    }
    val flavour = when {
        s.nightShare >= 0.25f -> "الليلي 🌙"
        s.countries.size >= 12 -> "العالمي 🌍"
        (s.decades.firstOrNull()?.first ?: 2020) < 1990 -> "الكلاسيكي 🎞️"
        s.series > s.movies * 2 -> "المسلسلاتي 📺"
        s.movies > s.series * 2 -> "السينمائي 🎬"
        else -> "✨"
    }
    return base to flavour
}

/** «ذوقي» as a Bento board: nickname, world map, watch clock, decades, favourite people, taste twin, monthly mood, share. */
@Composable
internal fun LuxoTasteBentoCard(isTablet: Boolean) {
    if (!rememberLuxoFeatures().tasteBento) return
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsStateWithLifecycle()
    LaunchedEffect(watched.items.size) { if (watched.items.isNotEmpty()) LuxoTasteFacts.ensure(watched.items) }
    val facts = LuxoTasteFacts.facts.toMap()
    val stats = remember(watched.items, facts.size) { buildBento(watched.items, facts) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    if (stats.titles == 0) return
    Column(Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 0.dp else 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("لوحة الذوق", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (LuxoTasteFacts.loading) Text("يتم تحليل السجل…", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Box(
                Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(B1, B2)))
                    .clickable { scope.launch { runCatching { layer.toImageBitmap() }.getOrNull()?.let { luxoShareImage(it, "ذوقي على موج") } } }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text("مشاركة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
                .background(Color(0xFF0E0D14), RoundedCornerShape(26.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isTablet) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroTile(stats, Modifier.weight(1.4f).height(210.dp))
                    ClockTile(stats, Modifier.weight(1f).height(210.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MapTile(stats, Modifier.weight(1.4f).height(230.dp))
                    DecadesTile(stats, Modifier.weight(1f).height(230.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActorsTile(stats, Modifier.weight(1.4f))
                    PeopleTile(stats, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoodTile(stats, Modifier.weight(1.4f))
                    TwinTile(stats, facts, Modifier.weight(1f))
                }
            } else {
                HeroTile(stats, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClockTile(stats, Modifier.weight(1f).height(190.dp))
                    DecadesTile(stats, Modifier.weight(1f).height(190.dp))
                }
                MapTile(stats, Modifier.fillMaxWidth().height(210.dp))
                ActorsTile(stats, Modifier.fillMaxWidth())
                PeopleTile(stats, Modifier.fillMaxWidth())
                MoodTile(stats, Modifier.fillMaxWidth())
                TwinTile(stats, facts, Modifier.fillMaxWidth())
            }
            Text("موج", color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.End).padding(end = 6.dp))
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, title: String? = null, brush: Brush? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(BTile))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun HeroTile(s: LuxoBentoStats, modifier: Modifier) {
    val (name, flavour) = nicknameOf(s)
    Tile(modifier, brush = Brush.linearGradient(listOf(B1.copy(alpha = 0.55f), B2.copy(alpha = 0.35f), BTile))) {
        Text("اللقب", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Text("$name $flavour", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat("${s.titles}", "عمل")
            Stat("${s.movies}", "فيلم")
            Stat("${s.series}", "مسلسل")
            Stat("${s.hours}", "ساعة تقريباً")
            Stat("${s.countries.size}", "دولة")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun ClockTile(s: LuxoBentoStats, modifier: Modifier) {
    val peak = s.hourBins.indices.maxByOrNull { s.hourBins[it] } ?: 21
    val label = when (peak) {
        in 5..11 -> "صباحاً"
        in 12..16 -> "ظهراً"
        in 17..20 -> "مساءً"
        else -> "ليلاً"
    }
    Tile(modifier, "ساعة المشاهدة") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val maxV = (s.hourBins.maxOrNull() ?: 1).coerceAtLeast(1)
                val c = Offset(size.width / 2, size.height / 2)
                val r0 = size.minDimension * 0.18f
                val r1 = size.minDimension * 0.48f
                drawCircle(Color.White.copy(alpha = 0.06f), r0, c, style = Stroke(1.dp.toPx()))
                for (h in 0 until 24) {
                    val a = (h / 24.0 * 2 * PI - PI / 2).toFloat()
                    val len = r0 + (r1 - r0) * (s.hourBins[h].toFloat() / maxV).coerceAtLeast(0.04f)
                    drawLine(
                        brush = Brush.linearGradient(listOf(B1, B2)),
                        start = Offset(c.x + cos(a) * r0, c.y + sin(a) * r0),
                        end = Offset(c.x + cos(a) * len, c.y + sin(a) * len),
                        strokeWidth = size.minDimension * 0.045f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text("الذروة", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Text("${peak}:00", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(label, color = B2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (s.nightShare > 0.1f) Text("${(s.nightShare * 100).toInt()}% بعد منتصف الليل", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

// Rough centre of each country (lat, lon) for the dots on the map.
private val centroids = mapOf(
    "US" to (39.0 to -98.0), "CA" to (57.0 to -101.0), "MX" to (23.0 to -102.0), "BR" to (-10.0 to -52.0), "AR" to (-35.0 to -65.0),
    "CL" to (-33.0 to -71.0), "CO" to (4.0 to -73.0), "PE" to (-9.0 to -75.0), "GB" to (54.0 to -2.0), "IE" to (53.0 to -8.0),
    "FR" to (46.0 to 2.0), "ES" to (40.0 to -4.0), "PT" to (39.5 to -8.0), "DE" to (51.0 to 10.0), "IT" to (42.8 to 12.5),
    "NL" to (52.2 to 5.5), "BE" to (50.6 to 4.6), "CH" to (46.8 to 8.2), "AT" to (47.5 to 14.5), "SE" to (62.0 to 15.0),
    "NO" to (61.0 to 9.0), "DK" to (56.0 to 10.0), "FI" to (64.0 to 26.0), "IS" to (65.0 to -18.0), "PL" to (52.0 to 19.0),
    "CZ" to (49.8 to 15.5), "HU" to (47.2 to 19.5), "RO" to (46.0 to 25.0), "GR" to (39.0 to 22.0), "TR" to (39.0 to 35.0),
    "RU" to (60.0 to 90.0), "UA" to (49.0 to 32.0), "IN" to (22.0 to 79.0), "CN" to (35.0 to 103.0), "HK" to (22.3 to 114.2),
    "TW" to (23.7 to 121.0), "JP" to (36.0 to 138.0), "KR" to (36.5 to 127.8), "TH" to (15.0 to 101.0), "PH" to (12.0 to 122.0),
    "ID" to (-2.0 to 118.0), "MY" to (4.0 to 102.0), "SG" to (1.35 to 103.8), "VN" to (16.0 to 107.0), "AU" to (-25.0 to 134.0),
    "NZ" to (-41.0 to 174.0), "ZA" to (-29.0 to 24.0), "NG" to (9.0 to 8.0), "EG" to (26.0 to 30.0), "MA" to (32.0 to -6.0),
    "IL" to (31.0 to 35.0), "IR" to (32.0 to 53.0), "SA" to (24.0 to 45.0), "AE" to (24.0 to 54.0), "PK" to (30.0 to 70.0),
    "LU" to (49.8 to 6.1), "BG" to (42.7 to 25.5), "RS" to (44.0 to 21.0), "HR" to (45.1 to 15.2),
)

@Composable
private fun MapTile(s: LuxoBentoStats, modifier: Modifier) {
    Tile(modifier, "خريطة العالم · ${s.countries.size} دولة") {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val dot = 2.dp.toPx()
                // Faint lat/lon dot grid.
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) { drawCircle(Color.White.copy(alpha = 0.06f), dot * 0.5f, Offset(x, y)); x += dot * 5 }
                    y += dot * 5
                }
                val maxV = (s.countries.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                s.countries.forEach { (code, n) ->
                    val (lat, lon) = centroids[code] ?: return@forEach
                    val px = ((lon + 180) / 360 * size.width).toFloat()
                    val py = ((75 - lat) / 135 * size.height).toFloat()
                    val r = (4.dp.toPx() + 14.dp.toPx() * (n.toFloat() / maxV))
                    drawCircle(B2.copy(alpha = 0.22f), r * 1.6f, Offset(px, py))
                    drawCircle(Brush.radialGradient(listOf(B2, B1), Offset(px, py), r), r, Offset(px, py))
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.countries.take(10).forEach { (code, n) -> Text("${luxoFlag(code)} $n", color = Color.White, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun DecadesTile(s: LuxoBentoStats, modifier: Modifier) {
    val top = s.decades.maxByOrNull { it.second }
    Tile(modifier, "العقود" + (top?.let { " · الأكثر ${it.first}s" } ?: "")) {
        val maxV = (s.decades.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            s.decades.takeLast(8).forEach { (d, n) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("$n", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / maxV * 0.75f).coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(B2, B1))),
                    )
                    Text("${d % 100}".padStart(2, '0') + "s", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ActorsTile(s: LuxoBentoStats, modifier: Modifier) {
    if (s.actors.isEmpty()) return
    Tile(modifier, "الممثلون الأكثر حضوراً") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            s.actors.forEach { (name, photo, n) ->
                Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(2.dp, Brush.linearGradient(listOf(B1, B2)), CircleShape)) {
                        if (photo != null) AsyncImage(photo, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(name, color = Color.White, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    Text("$n أعمال", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PeopleTile(s: LuxoBentoStats, modifier: Modifier) {
    if (s.directors.isEmpty() && s.studios.isEmpty()) return
    Tile(modifier) {
        if (s.directors.isNotEmpty()) {
            Text("المخرجون المفضلون", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            s.directors.take(3).forEach { (n, c) -> RankLine(n, c) }
        }
        if (s.studios.isNotEmpty()) {
            Text("الاستوديوهات والقنوات", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            s.studios.take(3).forEach { (n, c) -> RankLine(n, c) }
        }
    }
}

@Composable
private fun RankLine(name: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", color = B3, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoodTile(s: LuxoBentoStats, modifier: Modifier) {
    if (s.months.isEmpty()) return
    Tile(modifier, "مزاج كل شهر") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.months.forEach { (m, g) ->
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val mood = moodOf(g)
                    Text(mood.substringBefore(' '), fontSize = 26.sp)
                    Text(mood.substringAfter(' '), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                    Text(m, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                }
            }
        }
    }
}

/** «توأم الذوق»: a well-known critic, director or character whose taste is closest. */
@Composable
private fun TwinTile(s: LuxoBentoStats, facts: Map<String, LuxoTitleFacts>, modifier: Modifier) {
    var twin by remember { mutableStateOf(LuxoPrefsStorage.load("taste_twin_v1")) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Tile(modifier, "توأم الذوق", brush = Brush.linearGradient(listOf(B3.copy(alpha = 0.25f), BTile))) {
        when {
            loading -> Text("يتم البحث عن التوأم…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            twin != null -> Text(twin!!, color = Color.White, fontSize = 13.sp, lineHeight = 20.sp)
            else -> Text("يتم اختيار شخصية معروفة يشبه ذوقها هذا السجل", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).clickable(enabled = !loading) {
                loading = true
                scope.launch {
                    val key = LuxoCompanionApi.loadKey()
                    if (key != null) {
                        val sample = facts.values.shuffled().take(40).joinToString("، ") { it.title }
                        val prompt = "هذه عينة من أعمال تمت مشاهدتها: $sample. وأكثر التصنيفات: ${s.genres.joinToString { it.first }}.\n" +
                            "اختر «توأم ذوق» واحداً: مخرج أو ناقد أو شخصية سينمائية معروفة ذوقها الأقرب لهذا السجل. " +
                            "اكتب سطرين فقط بالعربية وبصيغة المجهول: الاسم بخط واضح أولاً ثم السبب. بدون Markdown."
                        val answer = LuxoCompanionApi.ask(key, prompt).replace("**", "").trim()
                        if (answer.isNotBlank()) { twin = answer; LuxoPrefsStorage.save("taste_twin_v1", answer) }
                    } else {
                        twin = "يلزم ضبط مفتاح «رفيق موج»"
                    }
                    loading = false
                }
            }.padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text(if (twin == null) "اكتشاف التوأم" else "↺ توأم آخر", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}


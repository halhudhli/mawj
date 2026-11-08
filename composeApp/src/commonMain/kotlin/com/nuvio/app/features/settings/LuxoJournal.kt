package com.nuvio.app.features.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.luxoAccentColors
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.luxo.LuxoDateStyle
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.LuxoUserData
import com.nuvio.app.features.luxo.luxoShareImage
import com.nuvio.app.features.luxo.luxoStarsLabel
import com.nuvio.app.features.luxo.luxoTodayEpochDay
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ dates

private const val JOURNAL_DAY_MS = 24L * 60 * 60 * 1000
// Local (Riyadh) day boundaries, close enough for a journal.
private fun epochDayOf(ms: Long): Long = (ms + 3 * 3_600_000L) / JOURNAL_DAY_MS

private data class Civil(val year: Int, val month: Int, val day: Int)

private fun civilOf(epochDay: Long): Civil {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
    return Civil(y, m, d)
}

@Composable
private fun monthName(month: Int): String = lx(
    listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")[month - 1],
    listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[month - 1],
)

@Composable
private fun rememberWatchedItems(): List<WatchedItem> {
    val watched by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    return watched.items
}

@Composable
private fun JournalCard(title: String, subtitle: String, emoji: String, isTablet: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 0.dp else 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(accents.first().copy(alpha = 0.18f), tokens.colors.surface)))
            .border(1.dp, accents.first().copy(alpha = 0.2f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(emoji, fontSize = 30.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tokens.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = tokens.colors.textMuted)
        }
    }
}

// ------------------------------------------------------------------ diary

/** «مذكراتي»: everything watched, as a dated journal with your stars and notes. */
@Composable
internal fun LuxoDiaryCard(isTablet: Boolean, onPosterClick: ((MetaPreview) -> Unit)?) {
    if (!rememberLuxoFeatures().diary) return
    val items = rememberWatchedItems()
    var open by remember { mutableStateOf(false) }
    JournalCard(
        title = lx("مذكراتي 📔", "My diary 📔"),
        subtitle = lx("${items.size} مشاهدة مرتبة بالتاريخ مع التقييم والملاحظات", "${items.size} entries by date, with your stars and notes"),
        emoji = "🗓️",
        isTablet = isTablet,
        onClick = { open = true },
    )
    if (open) LuxoDiaryDialog(items, onDismiss = { open = false }, onPosterClick = { item -> open = false; onPosterClick?.invoke(item) })
}

private data class DiaryEntry(val day: Long, val key: String, val sample: WatchedItem, val episodes: List<WatchedItem>)

@Composable
private fun LuxoDiaryDialog(items: List<WatchedItem>, onDismiss: () -> Unit, onPosterClick: (MetaPreview) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val userData by LuxoUserData.state.collectAsStateWithLifecycle()
    // One entry per title per day (several episodes of a show the same day become one line).
    val entries = remember(items) {
        items.filter { it.markedAtEpochMs > 0 }
            .groupBy { epochDayOf(it.markedAtEpochMs) to "${it.type}:${it.id}" }
            .map { (k, list) -> DiaryEntry(k.first, k.second, list.maxBy { it.markedAtEpochMs }, list.filter { it.season != null }) }
            .sortedByDescending { it.sample.markedAtEpochMs }
    }
    val months = entries.groupBy { civilOf(it.day).let { c -> c.year * 100 + c.month } }
    Dialog(onDismissRequest = onDismiss, properties = com.nuvio.app.features.luxo.luxoFullScreenDialogProperties()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(tokens.colors.background)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lx("مذكراتي 📔", "My diary 📔"), fontWeight = FontWeight.Black, fontSize = 26.sp, color = tokens.colors.textPrimary)
                    Text(lx("${entries.size} يوم مشاهدة مسجّل", "${entries.size} watch entries"), fontSize = 12.sp, color = tokens.colors.textMuted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = tokens.colors.textPrimary) }
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(lx("لسا ما فيه مشاهدات مسجلة", "Nothing logged yet"), color = tokens.colors.textMuted)
                }
                return@Column
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                months.forEach { (ym, list) ->
                    item(key = "m$ym") {
                        Row(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Brush.linearGradient(accents)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${monthName(ym % 100)} ${LuxoDateStyle.digits((ym / 100).toString())}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = tokens.colors.textPrimary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(lx("${list.size} مشاهدة", "${list.size} entries"), fontSize = 12.sp, color = tokens.colors.textMuted)
                        }
                    }
                    items(list, key = { "${it.day}:${it.key}" }) { entry ->
                        val item = entry.sample
                        val rating = userData.ratings[LuxoUserData.key(item.type, item.id)]
                        val civil = civilOf(entry.day)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPosterClick(MetaPreview(id = item.id, type = item.type, name = item.name, poster = item.poster)) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Day column like a diary margin
                            Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(LuxoDateStyle.digits(civil.day.toString()), fontWeight = FontWeight.Black, fontSize = 20.sp, color = tokens.colors.textPrimary)
                            }
                            AsyncImage(
                                model = item.poster,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(46.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val line = when {
                                    entry.episodes.size > 1 -> lx("${entry.episodes.size} حلقات", "${entry.episodes.size} episodes")
                                    item.season != null -> lx("الموسم ${item.season} · الحلقة ${item.episode ?: ""}", "S${item.season} · E${item.episode ?: ""}")
                                    else -> lx("فيلم", "Movie")
                                }
                                Text(line, fontSize = 12.sp, color = tokens.colors.textMuted)
                                rating?.note?.takeIf { it.isNotBlank() }?.let {
                                    Text("“$it”", fontSize = 12.sp, color = tokens.colors.textPrimary.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            rating?.let {
                                Text("★ ${luxoStarsLabel(it.score)}", color = Color(0xFFFFD86B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ monthly challenge

internal object LuxoChallenge {
    private const val GOAL_KEY = "challenge_goal"
    private const val UNIT_KEY = "challenge_unit"
    var goal by mutableIntStateOf(LuxoPrefsStorage.load(GOAL_KEY)?.toIntOrNull() ?: 0)
        private set
    var unit by mutableStateOf(LuxoPrefsStorage.load(UNIT_KEY) ?: "movies")
        private set

    fun set(newGoal: Int, newUnit: String) {
        goal = newGoal
        unit = newUnit
        LuxoPrefsStorage.save(GOAL_KEY, newGoal.toString())
        LuxoPrefsStorage.save(UNIT_KEY, newUnit)
    }
}

/** This month's progress toward the goal: (done, goal); goal 0 = no challenge set. */
@Composable
internal fun rememberChallengeProgress(): Pair<Int, Int> {
    val items = rememberWatchedItems()
    val goal = LuxoChallenge.goal
    val unit = LuxoChallenge.unit
    val today = civilOf(luxoTodayEpochDay())
    val done = remember(items, unit, today.month) {
        val thisMonth = items.filter { it.markedAtEpochMs > 0 }.filter {
            val c = civilOf(epochDayOf(it.markedAtEpochMs))
            c.year == today.year && c.month == today.month
        }
        if (unit == "episodes") thisMonth.count { it.season != null }
        else thisMonth.filter { it.season == null }.distinctBy { it.id }.size
    }
    return done to goal
}

@Composable
internal fun LuxoChallengeCard(isTablet: Boolean) {
    if (!rememberLuxoFeatures().challenge) return
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val (done, goal) = rememberChallengeProgress()
    var editing by remember { mutableStateOf(false) }
    val today = civilOf(luxoTodayEpochDay())
    val unitLabel = if (LuxoChallenge.unit == "episodes") lx("حلقة", "episodes") else lx("فيلم", "movies")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 0.dp else 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(tokens.colors.surface)
            .border(1.dp, accents.first().copy(alpha = 0.2f), RoundedCornerShape(22.dp))
            .clickable { editing = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            val fraction = if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
            Canvas(Modifier.size(72.dp)) {
                val stroke = 7.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.1f), -90f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                drawArc(Brush.sweepGradient(accents + accents.first()), -90f, 360f * fraction, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(
                if (goal > 0 && done >= goal) "🏆" else LuxoDateStyle.digits("$done"),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = tokens.colors.textPrimary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(lx("تحدي ${monthName(today.month)} 🎯", "${monthName(today.month)} challenge 🎯"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tokens.colors.textPrimary)
            Text(
                when {
                    goal <= 0 -> lx("حط هدف لهالشهر، مثل 6 أفلام", "Set a goal for this month, like 6 movies")
                    done >= goal -> lx("خلّصت التحدي! $done من $goal $unitLabel 🎉", "Done! $done of $goal $unitLabel 🎉")
                    else -> lx("$done من $goal $unitLabel · باقي ${goal - done}", "$done of $goal $unitLabel · ${goal - done} to go")
                },
                fontSize = 13.sp,
                color = tokens.colors.textMuted,
            )
        }
    }
    if (editing) {
        var newGoal by remember { mutableIntStateOf(if (goal > 0) goal else 6) }
        var newUnit by remember { mutableStateOf(LuxoChallenge.unit) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(lx("هدف هالشهر", "This month's goal")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = newUnit == "movies", onClick = { newUnit = "movies" }, label = { Text(lx("أفلام", "Movies")) })
                        FilterChip(selected = newUnit == "episodes", onClick = { newUnit = "episodes" }, label = { Text(lx("حلقات", "Episodes")) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { if (newGoal > 1) newGoal -= 1 }) { Text("−", fontSize = 24.sp) }
                        Text(LuxoDateStyle.digits("$newGoal"), fontSize = 32.sp, fontWeight = FontWeight.Black)
                        TextButton(onClick = { if (newGoal < 200) newGoal += 1 }) { Text("+", fontSize = 24.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { LuxoChallenge.set(newGoal, newUnit); editing = false }) { Text(lx("حفظ", "Save")) } },
            dismissButton = {
                TextButton(onClick = { LuxoChallenge.set(0, newUnit); editing = false }) { Text(lx("بدون تحدي", "No goal")) }
            },
        )
    }
}

// ------------------------------------------------------------------ year in Luxo

private class WrappedSlide(val colors: List<Color>, val content: @Composable () -> Unit)

@Composable
internal fun LuxoWrappedCard(isTablet: Boolean) {
    if (!rememberLuxoFeatures().wrapped) return
    var open by remember { mutableStateOf(false) }
    val year = civilOf(luxoTodayEpochDay()).year
    JournalCard(
        title = lx("السنة في موج ${LuxoDateStyle.digits(year.toString())} 🎬", "Your $year in Mawj 🎬"),
        subtitle = lx("ملخص السنة على شكل ستوريز، وكل صفحة قابلة للمشاركة", "Your year as stories you can share"),
        emoji = "✨",
        isTablet = isTablet,
        onClick = { open = true },
    )
    if (open) LuxoWrappedDialog(year, onDismiss = { open = false })
}

@Composable
private fun LuxoWrappedDialog(year: Int, onDismiss: () -> Unit) {
    val items = rememberWatchedItems()
    val library by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val userData by LuxoUserData.state.collectAsStateWithLifecycle()
    val stats = rememberLuxoPassStats()
    val yearItems = remember(items, year) {
        items.filter { it.markedAtEpochMs > 0 && civilOf(epochDayOf(it.markedAtEpochMs)).year == year }
    }
    val movies = yearItems.filter { it.season == null }.distinctBy { it.id }
    val episodes = yearItems.filter { it.season != null }
    val shows = episodes.distinctBy { it.id }
    val genres = remember(yearItems, library.items) {
        val ids = yearItems.map { it.id }.toSet()
        library.items.filter { it.id in ids }.flatMap { it.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(3).map { it.key }
    }
    val busiest = remember(yearItems) {
        yearItems.groupingBy { civilOf(epochDayOf(it.markedAtEpochMs)).month }.eachCount().maxByOrNull { it.value }
    }
    val favorite = remember(userData, year) {
        userData.ratings.values.filter { it.ratedAtMs == 0L || civilOf(epochDayOf(it.ratedAtMs)).year == year }.maxByOrNull { it.score }
    }
    val hoursYear = movies.size * 2 + episodes.size * 45 / 60
    val white = Color.White
    val slides = buildList {
        add(WrappedSlide(listOf(Color(0xFF2B1B5C), Color(0xFF7B2E8F))) {
            WrappedBig(lx("السنة في", "Your year in"), "موج", LuxoDateStyle.digits(year.toString()))
        })
        add(WrappedSlide(listOf(Color(0xFF0E4D64), Color(0xFF1D8A99))) {
            WrappedBig(lx("شاهدت", "You watched"), LuxoDateStyle.digits("${movies.size + shows.size}"), lx("عمل · ${movies.size} فيلم و ${shows.size} مسلسل", "titles · ${movies.size} movies, ${shows.size} shows"))
        })
        add(WrappedSlide(listOf(Color(0xFF5C1B2B), Color(0xFFB8432E))) {
            WrappedBig(lx("قضيت تقريباً", "You spent about"), LuxoDateStyle.digits("$hoursYear"), lx("ساعة مشاهدة · ${episodes.size} حلقة", "hours · ${episodes.size} episodes"))
        })
        if (genres.isNotEmpty()) add(WrappedSlide(listOf(Color(0xFF1B3D2B), Color(0xFF2A9D8F))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lx("الذوق هالسنة", "Your top genres"), color = white.copy(alpha = 0.8f), fontSize = 20.sp)
                genres.forEachIndexed { i, g ->
                    Text("${i + 1}. $g", color = white, fontSize = if (i == 0) 40.sp else 28.sp, fontWeight = FontWeight.Black)
                }
            }
        })
        favorite?.let { fav ->
            add(WrappedSlide(listOf(Color(0xFF3B2414), Color(0xFFB8860B))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(lx("الأعلى تقييماً", "Your top rated"), color = white.copy(alpha = 0.8f), fontSize = 20.sp)
                    AsyncImage(fav.title.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)))
                    Text(fav.title.name, color = white, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text("★ ${luxoStarsLabel(fav.score)}/5", color = Color(0xFFFFD86B), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            })
        }
        busiest?.let { (month, count) ->
            add(WrappedSlide(listOf(Color(0xFF14283F), Color(0xFF4F6BFF))) {
                WrappedBig(lx("أكثر شهر شاهدت فيه", "Your busiest month"), monthName(month), lx("$count مشاهدة", "$count entries"))
            })
        }
        add(WrappedSlide(listOf(Color(0xFF1B1035), Color(0xFFFF5FD2))) {
            WrappedBig(
                lx("وأطول سلسلة أيام", "And your best streak"),
                LuxoDateStyle.digits("${stats.streakDays}"),
                if (stats.nightShare > 0.25f) lx("يوماً بعد يوم… وأغلبها ليلاً 🌙", "days in a row… mostly at night 🌙") else lx("يوماً بعد يوم 🔥", "days in a row 🔥"),
            )
        })
    }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(5500, easing = LinearEasing))
        if (index < slides.lastIndex) index += 1
    }
    Dialog(onDismissRequest = onDismiss, properties = com.nuvio.app.features.luxo.luxoFullScreenDialogProperties()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 520.dp)
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(Brush.linearGradient(slides[index].colors))
                    .pointerInput(slides.size, isRtl) {
                        detectTapGestures { pos ->
                            val forward = if (isRtl) pos.x < size.width / 2 else pos.x > size.width / 2
                            index = if (forward) (index + 1).coerceAtMost(slides.lastIndex) else (index - 1).coerceAtLeast(0)
                        }
                    }
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                slides[index].content()
                Text("MAWJ", color = white.copy(alpha = 0.7f), fontWeight = FontWeight.Black, letterSpacing = 6.sp, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    slides.indices.forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(white.copy(alpha = 0.3f)),
                        ) {
                            val fill = when {
                                i < index -> 1f
                                i == index -> progress.value
                                else -> 0f
                            }
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(white))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { layer.toImageBitmap() }.getOrNull()?.let { luxoShareImage(it, "Mawj $year") }
                        }
                    }) { Icon(Icons.Rounded.Share, null, tint = white) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = white) }
                }
            }
        }
    }
}

@Composable
private fun WrappedBig(top: String, big: String, bottom: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(top, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(big, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 76.sp)
        Text(bottom, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Suppress("unused")
private suspend fun journalPause() = delay(0)

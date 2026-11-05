package com.nuvio.app.features.luxo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.luxoAccentColors
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryUpcomingEpisode
import com.nuvio.app.features.library.libraryUpcomingEpisodesFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ------------------------------------------------------------------ categories

/** Built-in sections; users can add their own next to them. */
internal object LuxoCountdownSections {
    const val ALL = "all"
    const val KDRAMA = "kdrama"
    const val FOREIGN = "foreign"
    const val ANIME = "anime"
}

@Serializable
internal data class LuxoCountdownSection(val id: String, val name: String, val emoji: String)

@Serializable
internal data class LuxoCountdownState(
    val custom: List<LuxoCountdownSection> = emptyList(),
    /** Manual placement: series key -> section id. */
    val overrides: Map<String, String> = emptyMap(),
    /** Automatic placement worked out once from the title's country/genres. */
    val detected: Map<String, String> = emptyMap(),
)

internal object LuxoCountdownStore {
    private const val KEY = "countdown_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LuxoCountdownState> = _state.asStateFlow()

    private fun load(): LuxoCountdownState = runCatching {
        LuxoPrefsStorage.load(KEY)?.let { json.decodeFromString(LuxoCountdownState.serializer(), it) }
    }.getOrNull() ?: LuxoCountdownState()

    private fun update(transform: (LuxoCountdownState) -> LuxoCountdownState) {
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(KEY, json.encodeToString(LuxoCountdownState.serializer(), next))
    }

    fun addSection(name: String, emoji: String) = update {
        it.copy(custom = it.custom + LuxoCountdownSection("c${it.custom.size}_${name.hashCode()}", name.trim(), emoji.ifBlank { "📺" }))
    }

    fun deleteSection(id: String) = update { s ->
        s.copy(custom = s.custom.filterNot { it.id == id }, overrides = s.overrides.filterValues { it != id })
    }

    fun place(seriesKey: String, sectionId: String?) = update { s ->
        s.copy(overrides = if (sectionId == null) s.overrides - seriesKey else s.overrides + (seriesKey to sectionId))
    }

    fun rememberDetected(seriesKey: String, sectionId: String) = update { it.copy(detected = it.detected + (seriesKey to sectionId)) }

    fun sectionOf(seriesKey: String): String? = _state.value.overrides[seriesKey] ?: _state.value.detected[seriesKey]
}

private fun LibraryItem.seriesKey() = "$type:$id"

private fun LibraryItem.quickSection(): String? {
    val genresText = genres.joinToString(" ").lowercase()
    return when {
        mediaCategory.equals("anime", ignoreCase = true) || "anime" in genresText || "أنمي" in genresText -> LuxoCountdownSections.ANIME
        else -> null
    }
}

/** Looks up the country/language once per series (cached) and files it under a section. */
private suspend fun detectSection(item: LibraryItem): String {
    item.quickSection()?.let { return it }
    val meta = runCatching { MetaDetailsRepository.fetch(item.type, item.id) }.getOrNull()
    val text = listOfNotNull(meta?.country, meta?.language).joinToString(" ").lowercase()
    val genres = (meta?.genres.orEmpty() + item.genres).joinToString(" ").lowercase()
    return when {
        "anime" in genres || ("japan" in text && "animation" in genres) -> LuxoCountdownSections.ANIME
        "korea" in text || Regex("""\bko\b|\bkr\b|كوري""").containsMatchIn(text) -> LuxoCountdownSections.KDRAMA
        else -> LuxoCountdownSections.FOREIGN
    }
}

@Composable
private fun sectionLabel(id: String, custom: List<LuxoCountdownSection>): String = when (id) {
    LuxoCountdownSections.ALL -> lx("الكل", "All")
    LuxoCountdownSections.KDRAMA -> lx("🇰🇷 كيدراما", "🇰🇷 K-drama")
    LuxoCountdownSections.FOREIGN -> lx("🌍 أجنبي", "🌍 International")
    LuxoCountdownSections.ANIME -> lx("🌸 أنمي", "🌸 Anime")
    else -> custom.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.name}" } ?: id
}

// ------------------------------------------------------------------ helpers

@Composable
private fun daysLabel(days: Long): String = when {
    days <= 0L -> lx("اليوم 🔥", "Today 🔥")
    days == 1L -> lx("بكرة", "Tomorrow")
    days == 2L -> lx("بعد يومين", "In 2 days")
    days in 3L..10L -> lx("بعد $days أيام", "In $days days")
    else -> lx("بعد $days يوم", "In $days days")
}

internal fun LibraryItem.toPreview(): MetaPreview =
    MetaPreview(id = id, type = type, name = name, poster = poster, banner = banner, logo = logo, genres = genres)

@Composable
private fun CountdownRing(days: Long, modifier: Modifier, stroke: Float = 5f) {
    val accents = luxoAccentColors()
    val fraction = (1f - days.coerceIn(0L, 30L) / 30f).coerceIn(0.04f, 1f)
    Canvas(modifier) {
        val inset = stroke.dp.toPx() / 2
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke.dp.toPx()))
        drawArc(
            Brush.sweepGradient(accents + accents.first()),
            -90f, 360f * fraction, false, Offset(inset, inset), arcSize,
            style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun rememberUpcoming(classify: Boolean = true): List<LibraryUpcomingEpisode> {
    val range = LuxoCountdownRange.days
    val episodes by remember(range) { libraryUpcomingEpisodesFlow(days = LuxoCountdownRange.effective()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Classify new series in the background, once.
    LaunchedEffect(episodes.map { it.item.seriesKey() }.distinct(), classify) {
        if (!classify) return@LaunchedEffect
        episodes.map { it.item }.distinctBy { it.seriesKey() }
            .filter { LuxoCountdownStore.sectionOf(it.seriesKey()) == null }
            .take(30)
            .forEach { item ->
                LuxoCountdownStore.rememberDetected(item.seriesKey(), detectSection(item))
                kotlinx.coroutines.delay(150L)
            }
    }
    return episodes
}

// ------------------------------------------------------------------ home strip

/** One show in the home strip, from the library (followed) or from the world schedule. */
private data class StripEntry(
    val key: String,
    val title: String,
    val poster: String?,
    val airMs: Long?,
    val epochDay: Long,
    val preview: MetaPreview?,
    val followed: Boolean,
)

internal object LuxoHomeStripPicks {
    private const val KEY = "countdown_home_picks"
    /** Empty = automatic (followed shows, nearest first). */
    var picks by androidx.compose.runtime.mutableStateOf(load())
        private set

    private fun load(): List<String> =
        LuxoPrefsStorage.load(KEY)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun toggle(key: String) {
        picks = if (key in picks) picks - key else picks + key
        LuxoPrefsStorage.save(KEY, picks.joinToString("\n"))
    }

    fun clear() {
        picks = emptyList()
        LuxoPrefsStorage.save(KEY, "")
    }
}

@Composable
private fun rememberStripEntries(): List<StripEntry> {
    // TV: no background classification on Home (it froze weak boxes). Tablet: as before.
    val episodes = rememberUpcoming(classify = !luxoIsTv())
    val world by LuxoWorldCountdown.items.collectAsStateWithLifecycle()
    val today = remember { luxoTodayEpochDay() }
    val tmdbPicked = LuxoTmdbShows.picked.toMap()
    return remember(episodes, world, tmdbPicked) {
        val followed = episodes.distinctBy { it.item.seriesKey() }.map { ep ->
            StripEntry(
                key = "lib:" + ep.item.seriesKey(),
                title = ep.item.name,
                poster = ep.item.poster,
                airMs = null,
                epochDay = luxoEpochDay(ep.dateIso),
                preview = ep.item.toPreview(),
                followed = true,
            )
        }
        val followedNames = followed.map { it.title.lowercase() }.toSet()
        val others = world.orEmpty().filter { it.title.lowercase() !in followedNames }.map { air ->
            StripEntry(
                key = air.key,
                title = air.title,
                poster = air.poster,
                airMs = air.airMs,
                epochDay = (air.airMs + 3 * 3_600_000L) / 86_400_000L,
                preview = air.imdb?.let { MetaPreview(id = it, type = "series", name = air.title, poster = air.poster) },
                followed = false,
            )
        }
        val knownNames = (followed + others).map { it.title.lowercase() }.toSet()
        // Shows picked from the TMDB search (anime, Turkish, Korean… anything with an announced episode).
        val searched = tmdbPicked.values.filter { it.nextDate != null && it.title.lowercase() !in knownNames }.map { sh ->
            StripEntry(
                key = sh.key,
                title = sh.title,
                poster = sh.poster,
                airMs = null,
                epochDay = luxoEpochDay(sh.nextDate!!),
                preview = MetaPreview(id = sh.key, type = "series", name = sh.title, poster = sh.poster),
                followed = false,
            )
        }
        (followed + others + searched).sortedBy { it.airMs ?: (it.epochDay * 86_400_000L) }.filter { it.epochDay >= today }
    }
}

/** Home: the chosen shows as countdown rings; "All" opens the full page, ✎ picks the shows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxoCountdownStrip(onOpen: (MetaPreview) -> Unit, modifier: Modifier = Modifier) {
    if (!rememberLuxoFeatures().countdown) return
    val picks = LuxoHomeStripPicks.picks
    // TV: the world schedule only loads on Home when a world show was picked. Tablet: always, as before.
    LaunchedEffect(picks.any { !it.startsWith("lib:") }) {
        if (!luxoIsTv() || picks.any { !it.startsWith("lib:") }) LuxoWorldCountdown.refresh()
    }
    LaunchedEffect(picks) { runCatching { LuxoTmdbShows.refresh(picks) } }
    val entries = rememberStripEntries()
    var pageOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val today = remember { luxoTodayEpochDay() }
    val tokens = MaterialTheme.nuvio
    val shown = if (picks.isEmpty()) entries.filter { it.followed }.take(12) else entries.filter { it.key in picks }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lx("العد التنازلي ⏳", "Countdown ⏳"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                lx("✎ اختيار", "✎ Choose"),
                color = tokens.colors.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { picking = true }.padding(6.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                lx("الكل ‹", "All ›"),
                color = tokens.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { pageOpen = true }.padding(6.dp),
            )
        }
        if (shown.isEmpty()) {
            Text(
                lx("يتم اختيار المسلسلات من «✎ اختيار»", "Pick the shows to show here with “✎ Choose”"),
                color = tokens.colors.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { picking = true }.padding(vertical = 8.dp),
            )
        } else {
            // A lazy row scrolls and flings smoothly inside the Home list (the plain scrolling row didn't).
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shown, key = { it.key }) { entry ->
                    val days = entry.epochDay - today
                    Column(
                        Modifier
                            .width(104.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { entry.preview?.let(onOpen) },
                                onLongClick = { picking = true },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            CountdownRing(days, Modifier.size(96.dp), stroke = 4f)
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(luxoHighResImage(entry.poster))
                                    // Ask for a sharp copy even though it's drawn small.
                                    .size(480, 720)
                                    .build(),
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                                modifier = Modifier.size(82.dp).clip(CircleShape),
                            )
                        }
                        Text(daysLabel(days), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 1)
                        Text(entry.title, fontSize = 11.sp, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (pageOpen) {
        LuxoCountdownPage(
            onDismiss = { pageOpen = false },
            onOpen = { item ->
                pageOpen = false
                onOpen(item)
            },
        )
    }
    if (picking) LuxoStripPicker(entries, onDismiss = { picking = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuxoStripPicker(entries: List<StripEntry>, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var query by remember { mutableStateOf("") }
    val picks = LuxoHomeStripPicks.picks
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lx("مسلسلات الشريط في الرئيسية", "Shows on the Home strip"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (picks.isEmpty()) lx("تلقائي الآن: المتابَعات الأقرب موعداً", "Automatic: your followed shows") else lx("${picks.size} مختار", "${picks.size} chosen"),
                        fontSize = 12.sp,
                        color = tokens.colors.textMuted,
                    )
                }
                if (picks.isNotEmpty()) {
                    TextButton(onClick = { LuxoHomeStripPicks.clear() }) { Text(lx("رجّعه تلقائي", "Reset")) }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(lx("ابحث عن مسلسل…", "Search a show…")) },
                modifier = Modifier.fillMaxWidth(),
            )
            val filtered = entries.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            var found by remember { mutableStateOf<List<LuxoTmdbShow>>(emptyList()) }
            var searching by remember { mutableStateOf(false) }
            LaunchedEffect(query) {
                val q = query.trim()
                if (q.length < 2) { found = emptyList(); return@LaunchedEffect }
                kotlinx.coroutines.delay(450)
                searching = true
                val shownNames = filtered.map { it.title.lowercase() }.toSet()
                found = runCatching { LuxoTmdbShows.search(q) }.getOrDefault(emptyList()).filter { it.title.lowercase() !in shownNames }
                searching = false
            }
            LazyColumn(Modifier.fillMaxWidth().height(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (query.isNotBlank() && (searching || found.isNotEmpty())) {
                    item(key = "tmdb_header") {
                        Text(
                            if (searching) lx("يتم البحث في كل المسلسلات…", "Searching every show…") else lx("كل المسلسلات", "Every show"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.colors.textMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(found, key = { "f_" + it.key }) { sh ->
                        val on = sh.key in picks
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) tokens.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    LuxoTmdbShows.remember(sh)
                                    LuxoHomeStripPicks.toggle(sh.key)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = sh.poster,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(sh.title + (sh.year?.let { " ($it)" } ?: ""), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    sh.nextDate?.let { d -> listOfNotNull(sh.nextLabel, daysLabel(luxoEpochDay(d) - luxoTodayEpochDay())).joinToString(" · ") }
                                        ?: lx("لا توجد حلقة قادمة معلنة", "No upcoming episode announced"),
                                    fontSize = 12.sp,
                                    color = tokens.colors.textMuted,
                                )
                            }
                            Text(if (on) "✓" else "＋", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (on) tokens.colors.accent else tokens.colors.textMuted)
                        }
                    }
                }
                items(filtered, key = { it.key }) { entry ->
                    val on = entry.key in picks
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) tokens.colors.accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { LuxoHomeStripPicks.toggle(entry.key) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = luxoHighResImage(entry.poster),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (entry.followed) lx("⭐ متابَع · ", "⭐ Following · ") else "") + daysLabel(entry.epochDay - luxoTodayEpochDay()),
                                fontSize = 12.sp,
                                color = tokens.colors.textMuted,
                            )
                        }
                        Text(if (on) "✓" else "＋", fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (on) tokens.colors.accent else tokens.colors.textMuted)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ full page

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun LuxoCountdownPage(onDismiss: () -> Unit, onOpen: (MetaPreview) -> Unit) {
    val episodes = rememberUpcoming()
    val store by LuxoCountdownStore.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(LuxoCountdownSections.ALL) }
    var worldMode by remember { mutableStateOf(true) }
    var moving by remember { mutableStateOf<LibraryItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LuxoCountdownSection?>(null) }
    val today = remember { luxoTodayEpochDay() }
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val sectionIds = listOf(LuxoCountdownSections.ALL, LuxoCountdownSections.KDRAMA, LuxoCountdownSections.FOREIGN, LuxoCountdownSections.ANIME) +
        store.custom.map { it.id }
    fun sectionFor(item: LibraryItem): String =
        store.overrides[item.seriesKey()] ?: store.detected[item.seriesKey()] ?: item.quickSection() ?: LuxoCountdownSections.FOREIGN
    val visible = episodes.filter { selected == LuxoCountdownSections.ALL || sectionFor(it.item) == selected }
        .sortedBy { it.dateIso }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {

        com.nuvio.app.features.luxo.LuxoIdleBusy()
        Box(
            Modifier
                .fillMaxSize()
                .background(tokens.colors.background)
                .background(
                    Brush.verticalGradient(listOf(accents.first().copy(alpha = 0.10f), Color.Transparent, Color.Transparent)),
                )
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lx("العد التنازلي ⏳", "Countdown ⏳"), fontWeight = FontWeight.Black, fontSize = 26.sp, color = tokens.colors.textPrimary)
                        Text(
                            lx("${episodes.size} حلقة قادمة خلال ${LuxoCountdownRange.label(LuxoCountdownRange.days)} · الضغط المطوّل على أي عمل ينقله لقسم", "${episodes.size} upcoming episodes · long-press a show to move it"),
                            fontSize = 12.sp,
                            color = tokens.colors.textMuted,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = tokens.colors.textPrimary) }
                }
                // World (all airing series) vs. the series in the library.
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(tokens.colors.surface)
                        .padding(4.dp),
                ) {
                    listOf(true to lx("🌐 كل المسلسلات", "🌐 Everything airing"), false to lx("⭐ متابعاتي", "⭐ Following")).forEach { (mode, label) ->
                        Text(
                            label,
                            color = if (worldMode == mode) Color.White else tokens.colors.textMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (worldMode == mode) Brush.horizontalGradient(accents) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                                .clickable { worldMode = mode }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // How far ahead: one week up to six months.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lx("المدة", "Range"), color = tokens.colors.textMuted, fontSize = 12.sp)
                    LuxoCountdownRange.options.forEach { d ->
                        val on = LuxoCountdownRange.days == d
                        val capped = luxoLiteMode() && d > 60
                        Text(
                            LuxoCountdownRange.label(d) + if (capped) " · 60" else "",
                            color = if (on) Color.White else tokens.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) Brush.horizontalGradient(accents) else Brush.horizontalGradient(listOf(tokens.colors.surface, tokens.colors.surface)))
                                .clickable { LuxoCountdownRange.set(d) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (worldMode) {
                    LuxoWorldCountdownContent(onOpen = onOpen, modifier = Modifier.fillMaxWidth().weight(1f))
                    return@Column
                }
                // Section chips
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sectionIds.forEach { id ->
                        val isOn = id == selected
                        val count = if (id == LuxoCountdownSections.ALL) episodes.size else episodes.count { sectionFor(it.item) == id }
                        val custom = store.custom.firstOrNull { it.id == id }
                        Text(
                            text = "${sectionLabel(id, store.custom)}  $count",
                            color = if (isOn) Color.White else tokens.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isOn) Brush.horizontalGradient(accents) else Brush.horizontalGradient(listOf(tokens.colors.surface, tokens.colors.surface)))
                                .border(1.dp, accents.first().copy(alpha = if (isOn) 0f else 0.25f), RoundedCornerShape(50))
                                .combinedClickable(
                                    onClick = { selected = id },
                                    onLongClick = { if (custom != null) deleting = custom },
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    Text(
                        lx("＋ قسم", "＋ Section"),
                        color = tokens.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, tokens.colors.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { adding = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        if (episodes.isEmpty()) {
                            LuxoSkeletonList(modifier = Modifier.padding(16.dp))
                        } else {
                            Text(
                                lx("ما فيه حلقات جاية في هالقسم", "No upcoming episodes in this section"),
                                color = tokens.colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visible, key = { it.key }) { ep ->
                            val days = luxoEpochDay(ep.dateIso) - today
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 900.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(tokens.colors.surface)
                                    .border(1.dp, accents.first().copy(alpha = if (days <= 1) 0.45f else 0.12f), RoundedCornerShape(22.dp))
                                    .combinedClickable(
                                        onClick = { onOpen(ep.item.toPreview()) },
                                        onLongClick = { moving = ep.item },
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AsyncImage(
                                    model = luxoHighResImage(ep.imageUrl ?: ep.item.banner ?: ep.item.poster, maxBackdrop1080 = true),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(ep.item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val se = listOfNotNull(
                                        ep.seasonNumber?.let { lx("الموسم $it", "Season $it") },
                                        ep.episodeNumber?.let { lx("الحلقة $it", "Episode $it") },
                                    ).joinToString(" · ")
                                    if (se.isNotBlank()) Text(se, fontSize = 13.sp, color = tokens.colors.textMuted)
                                    ep.subtitle?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, fontSize = 12.sp, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(
                                        sectionLabel(sectionFor(ep.item), store.custom),
                                        fontSize = 11.sp,
                                        color = tokens.colors.accent,
                                    )
                                }
                                if (rememberLuxoFeatures().calendarAdd) {
                                    Text(
                                        "📅",
                                        fontSize = 22.sp,
                                        modifier = Modifier.clip(CircleShape).clickable {
                                            luxoAddToCalendar(
                                                ep.item.name + listOfNotNull(ep.seasonNumber?.let { " · الموسم $it" }, ep.episodeNumber?.let { " الحلقة $it" }).joinToString(""),
                                                ep.dateIso,
                                                "موعد حلقة جديدة · موج",
                                            )
                                        }.padding(8.dp),
                                    )
                                }
                                Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                                    CountdownRing(days, Modifier.size(70.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            if (days <= 0) "🔥" else LuxoDateStyle.digits(days.toString()),
                                            fontWeight = FontWeight.Black,
                                            fontSize = if (days <= 0) 20.sp else 18.sp,
                                            color = tokens.colors.textPrimary,
                                        )
                                        Text(
                                            when {
                                                days <= 0 -> lx("اليوم", "today")
                                                days == 1L -> lx("بكرة", "tmrw")
                                                else -> lx("يوم", "days")
                                            },
                                            fontSize = 10.sp,
                                            color = tokens.colors.textMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    moving?.let { item ->
        ModalBottomSheet(onDismissRequest = { moving = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lx("نقل «${item.name}» إلى…", "Move \"${item.name}\" to…"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                (sectionIds - LuxoCountdownSections.ALL).forEach { id ->
                    Text(
                        sectionLabel(id, store.custom),
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            LuxoCountdownStore.place(item.seriesKey(), id)
                            moving = null
                        }.padding(12.dp),
                    )
                }
                Text(
                    lx("↺ تلقائي (حسب بلد العمل)", "↺ Automatic"),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        LuxoCountdownStore.place(item.seriesKey(), null)
                        moving = null
                    }.padding(12.dp),
                )
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("📺") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(lx("قسم جديد", "New section")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text(lx("الاسم (مثلاً: صيني، تركي، وثائقي)", "Name")) }, singleLine = true)
                    OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(2) }, label = { Text(lx("إيموجي", "Emoji")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    LuxoCountdownStore.addSection(name, emoji)
                    adding = false
                }) { Text(lx("إضافة", "Add")) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(lx("إلغاء", "Cancel")) } },
        )
    }

    deleting?.let { section ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(lx("حذف قسم «${section.name}»؟", "Delete \"${section.name}\"?")) },
            text = { Text(lx("تعود الأعمال التي فيه إلى أقسامها التلقائية.", "Its shows go back to their automatic sections.")) },
            confirmButton = {
                TextButton(onClick = {
                    LuxoCountdownStore.deleteSection(section.id)
                    if (selected == section.id) selected = LuxoCountdownSections.ALL
                    deleting = null
                }) { Text(lx("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(lx("إلغاء", "Cancel")) } },
        )
    }
}

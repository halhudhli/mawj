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
private fun rememberUpcoming(): List<LibraryUpcomingEpisode> {
    val episodes by remember { libraryUpcomingEpisodesFlow(days = 90) }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Classify new series in the background, once.
    LaunchedEffect(episodes.map { it.item.seriesKey() }.distinct()) {
        episodes.map { it.item }.distinctBy { it.seriesKey() }
            .filter { LuxoCountdownStore.sectionOf(it.seriesKey()) == null }
            .take(30)
            .forEach { item -> LuxoCountdownStore.rememberDetected(item.seriesKey(), detectSection(item)) }
    }
    return episodes
}

// ------------------------------------------------------------------ home strip

/** Home: the next episodes as countdown rings; "All" opens the full page. */
@Composable
internal fun LuxoCountdownStrip(onOpen: (MetaPreview) -> Unit, modifier: Modifier = Modifier) {
    if (!rememberLuxoFeatures().countdown) return
    val episodes = rememberUpcoming()
    var pageOpen by remember { mutableStateOf(false) }
    if (episodes.isEmpty()) {
        // Nothing followed yet: still offer the world countdown.
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(luxoAccentColors().map { it.copy(alpha = 0.22f) }))
                .clickable { pageOpen = true }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⏳", fontSize = 26.sp)
            Column(Modifier.weight(1f)) {
                Text(lx("العد التنازلي للحلقات", "Episode countdown"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(lx("الحلقات الجاية حول العالم بعداد مباشر", "Upcoming episodes worldwide, live"), fontSize = 12.sp, color = MaterialTheme.nuvio.colors.textMuted)
            }
        }
        if (pageOpen) LuxoCountdownPage(onDismiss = { pageOpen = false }, onOpen = { item -> pageOpen = false; onOpen(item) })
        return
    }
    val today = remember { luxoTodayEpochDay() }
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lx("العد التنازلي ⏳", "Countdown ⏳"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                lx("الكل ‹", "All ›"),
                color = tokens.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { pageOpen = true }.padding(6.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            episodes.distinctBy { it.item.seriesKey() }.take(10).forEach { ep ->
                val days = luxoEpochDay(ep.dateIso) - today
                Column(
                    Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(ep.item.toPreview()) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        CountdownRing(days, Modifier.size(84.dp), stroke = 4f)
                        AsyncImage(
                            model = ep.item.poster,
                            contentDescription = ep.item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(70.dp).clip(CircleShape),
                        )
                    }
                    Text(daysLabel(days), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 1)
                    Text(ep.item.name, fontSize = 11.sp, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            lx("${episodes.size} حلقة جاية خلال 3 شهور · اضغط مطولاً على أي عمل لنقله لقسم", "${episodes.size} upcoming episodes · long-press a show to move it"),
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
            text = { Text(lx("الأعمال اللي فيه ترجع لأقسامها التلقائية.", "Its shows go back to their automatic sections.")) },
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

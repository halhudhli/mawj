package com.nuvio.app.features.luxo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberMawjStageOn
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview

/** One mood of «بوصلة المزاج» and the genres (English and Arabic names) that carry it. */
private enum class MawjMood(val label: String, val genres: List<String>) {
    Calm("هادئ", listOf("drama", "romance", "family", "documentary", "دراما", "رومانس", "عائلي", "وثائقي")),
    Thrilling("مشوّق", listOf("thriller", "action", "mystery", "crime", "إثارة", "أكشن", "حركة", "غموض", "جريمة")),
    Funny("يضحك", listOf("comedy", "animation", "كوميدي", "رسوم")),
    Moving("مؤثر", listOf("drama", "romance", "war", "دراما", "رومانس", "حرب")),
    Thoughtful("يدعو للتفكير", listOf("science fiction", "sci-fi", "mystery", "history", "documentary", "خيال علمي", "غموض", "تاريخ", "وثائقي")),
}

/**
 * «بوصلة المزاج»: a row of moods under the hero. Choosing one gathers, from the rows already on the
 * home page, the titles whose genres fit that mood, in one calm row. Choosing it again closes it.
 */
@Composable
fun MawjMoodCompass(
    sections: List<HomeCatalogSection>,
    onOpen: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberMawjStageOn()) return
    val tokens = MaterialTheme.nuvio
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val mood = MawjMood.entries.firstOrNull { it.name == picked }
    val matches = remember(sections, mood) {
        if (mood == null) emptyList() else sections.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { item -> item.genres.any { g -> mood.genres.any { k -> g.contains(k, ignoreCase = true) } } }
            .distinctBy { it.type + ":" + it.id }
            .take(24)
            .toList()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "بوصلة المزاج",
                color = tokens.colors.textPrimary,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
            MawjMood.entries.forEach { m ->
                val on = m == mood
                Text(
                    m.label,
                    color = if (on) Color(0xFF070D14) else Color(0xFFC3CCD6),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (on) Modifier.background(tokens.colors.accent)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
                        )
                        .clickable { picked = if (on) null else m.name }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
        AnimatedVisibility(visible = mood != null) {
            if (matches.isEmpty()) {
                Text(
                    "لا توجد أعمال بهذا المزاج في الصفوف الحالية.",
                    color = Color(0xFF8796A8),
                    fontSize = 13.sp,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(matches, key = { it.type + ":" + it.id }) { item ->
                        NuvioPosterCard(
                            title = item.name,
                            imageUrl = item.poster,
                            detailLine = item.releaseInfo,
                            onClick = { onOpen(item) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A collection on the new home: its title in serif and its folders as quiet text chips, instead of a
 * row of large artwork tiles. Tapping a chip opens that folder as before.
 */
@Composable
fun MawjCollectionChips(
    collection: com.nuvio.app.features.collection.Collection,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (collection.folders.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            collection.title,
            color = tokens.colors.textPrimary,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            collection.folders.forEach { folder ->
                Text(
                    listOfNotNull(folder.coverEmoji?.takeIf { it.isNotBlank() }, folder.title).joinToString(" "),
                    color = Color(0xFFE6EBF0),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D1620))
                        .border(1.dp, tokens.colors.accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        .clickable { onFolderClick?.invoke(collection.id, folder.id) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * «الجزر»: every home collection in one section. The collections are tabs; the chosen one shows its
 * folders as cards (their cover art faint behind the name), instead of a separate row per collection.
 */
@Composable
fun MawjIslands(
    collections: List<com.nuvio.app.features.collection.Collection>,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (collections.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    var chosen by rememberSaveable { mutableStateOf(collections.first().id) }
    val lite = rememberLuxoLite()
    val current = collections.firstOrNull { it.id == chosen } ?: collections.first()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val chart = rememberLuxoFeatures().islandsChart
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الجزر", color = tokens.colors.textPrimary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.weight(1f))
            if (chart) MawjCompassRose(44.dp, tokens.colors.accent)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            collections.forEach { c ->
                val on = c.id == current.id
                Text(
                    c.title,
                    color = if (on) Color(0xFF070D14) else Color(0xFFC3CCD6),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (on) Modifier.background(tokens.colors.accent)
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
                        )
                        .clickable { chosen = c.id }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        val routeColor = tokens.colors.accent.copy(alpha = 0.35f)
        LazyRow(
            modifier = if (chart) Modifier.drawBehind { drawSeaRoutes(routeColor) } else Modifier,
            horizontalArrangement = Arrangement.spacedBy(if (chart) 22.dp else 14.dp),
        ) {
            items(current.folders, key = { current.id + ":" + it.id }) { folder ->
                // On the chart every card is an island of its own shape (the same shape each time for a folder).
                val shape = if (chart) remember(folder.id) { MawjIslandShape(folder.id.hashCode()) } else RoundedCornerShape(18.dp)
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .width(if (chart) 214.dp else 200.dp)
                        .height(if (chart) 124.dp else 104.dp)
                        .clip(shape)
                        .background(Color(0xFF0D1620))
                        .border(if (chart) 1.5.dp else 1.dp, tokens.colors.accent.copy(alpha = if (chart) 0.55f else 0.2f), shape)
                        .clickable { onFolderClick?.invoke(current.id, folder.id) },
                ) {
                    if (!folder.coverImageUrl.isNullOrBlank() && lite) {
                        // TV light mode: an animated (GIF) cover is shown as a still picture.
                        com.nuvio.app.core.ui.NuvioAsyncImage(
                            imageUrl = folder.coverImageUrl!!,
                            contentDescription = "",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.matchParentSize().alpha(0.45f),
                            animateIfPossible = false,
                        )
                    } else if (!folder.coverImageUrl.isNullOrBlank()) {
                        coil3.compose.AsyncImage(
                            model = folder.coverImageUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                            alpha = 0.45f,
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.matchParentSize().background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6070D14))),
                        ),
                    )
                    Text(
                        listOfNotNull(folder.coverEmoji?.takeIf { it.isNotBlank() }, folder.title).joinToString(" "),
                        color = Color(0xFFF6F1E4),
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        maxLines = 2,
                        modifier = Modifier.align(if (chart) Alignment.Center else Alignment.BottomStart).padding(14.dp),
                    )
                }
            }
        }
    }
}

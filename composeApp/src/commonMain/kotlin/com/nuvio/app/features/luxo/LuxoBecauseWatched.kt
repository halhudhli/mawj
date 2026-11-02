package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedRepository

private data class BecauseRow(val sourceTitle: String, val items: List<MetaPreview>)

private object LuxoBecauseCache {
    var key: String? = null
    var rows: List<BecauseRow> = emptyList()
}

/** «لأنه تمت مشاهدة …»: TMDB recommendations for the last titles watched, without what was already seen. */
@Composable
internal fun LuxoBecauseWatchedRows(onOpen: (MetaPreview) -> Unit, modifier: Modifier = Modifier) {
    if (!rememberLuxoFeatures().becauseWatched) return
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    val lite = luxoLiteMode()
    val recent = remember(watched.items) {
        watched.items.sortedByDescending { it.markedAtEpochMs }.distinctBy { LuxoTasteFacts.keyOf(it) }.take(if (lite) 1 else 2)
    }
    val stamp = recent.joinToString { LuxoTasteFacts.keyOf(it) }
    var rows by remember { mutableStateOf(if (LuxoBecauseCache.key == stamp) LuxoBecauseCache.rows else emptyList()) }
    LaunchedEffect(stamp) {
        if (recent.isEmpty() || LuxoBecauseCache.key == stamp) return@LaunchedEffect
        LuxoTasteFacts.ensure(recent, limit = recent.size)
        val seenTmdb = LuxoTasteFacts.facts.values.map { "${it.mediaType}:${it.tmdbId}" }.toSet()
        val result = recent.mapNotNull { item ->
            val f = LuxoTasteFacts.facts[LuxoTasteFacts.keyOf(item)] ?: return@mapNotNull null
            val res = LuxoTmdbLite.get("${f.mediaType}/${f.tmdbId}/recommendations", mapOf("language" to "ar")) ?: return@mapNotNull null
            val picks = res.a("results")
                .filter { it.s("original_language") != "ar" && it.s("poster_path") != null }
                .filter { "${it.s("media_type") ?: f.mediaType}:${it.i("id")}" !in seenTmdb }
                .take(if (lite) 10 else 16)
                .mapNotNull { r ->
                    val id = r.i("id") ?: return@mapNotNull null
                    val type = r.s("media_type") ?: f.mediaType
                    MetaPreview(
                        id = "tmdb:$id",
                        type = if (type == "movie") "movie" else "series",
                        name = r.s("title") ?: r.s("name") ?: return@mapNotNull null,
                        poster = LuxoTmdbLite.img(r.s("poster_path")),
                    )
                }
            if (picks.isEmpty()) null else BecauseRow(f.title, picks)
        }
        LuxoBecauseCache.key = stamp
        LuxoBecauseCache.rows = result
        rows = result
    }
    if (rows.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "لأنه تمت مشاهدة «${row.sourceTitle}»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(row.items, key = { it.id }) { p ->
                        Column(Modifier.width(if (lite) 104.dp else 118.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(p) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
                                AsyncImage(p.poster, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Text(p.name, color = tokens.colors.textPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

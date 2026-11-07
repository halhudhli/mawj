package com.nuvio.app.features.luxo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.TmdbCollectionSourceType
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import kotlinx.coroutines.delay

/**
 * Animated catalog cards (the «animatedCatalog» switch).
 *  - Platform / network / studio cards cycle backdrops of their own shows (Ken Burns), the cover
 *    returning between them, with a soft glow pulse and a sheen.
 *  - Every other card gets a slow Ken Burns and a light sweep; mood cards breathe.
 * Only composed (visible) cards animate; on TV the motion is reduced.
 */
private data class FolderQuery(val media: String, val params: Map<String, String>) {
    val key: String get() = media + params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
}

private object FolderBackdrops {
    val cache = mutableStateMapOf<String, List<String>>()

    suspend fun load(q: FolderQuery): List<String> {
        cache[q.key]?.let { return it }
        val o = LuxoTmdbLite.get("discover/${q.media}", q.params + mapOf("sort_by" to "popularity.desc", "language" to "en"))
        val list = o?.a("results").orEmpty().mapNotNull { LuxoTmdbLite.img(it.s("backdrop_path"), "w780") }.take(6)
        cache[q.key] = list
        return list
    }
}

private fun folderQuery(folder: CollectionFolder): FolderQuery? {
    folder.resolvedSources.forEach { src ->
        if (!src.isTmdb) return@forEach
        val id = src.tmdbId
        when (src.tmdbSourceType?.uppercase()) {
            TmdbCollectionSourceType.NETWORK.name -> if (id != null) return FolderQuery("tv", mapOf("with_networks" to id.toString()))
            TmdbCollectionSourceType.COMPANY.name -> if (id != null) return FolderQuery(if (src.mediaType == "tv") "tv" else "movie", mapOf("with_companies" to id.toString()))
        }
        val f = src.filters ?: return@forEach
        val media = if (src.mediaType == "tv" || src.mediaType == "series") "tv" else "movie"
        f.withNetworks?.takeIf { it.isNotBlank() }?.let { return FolderQuery("tv", mapOf("with_networks" to it)) }
        f.withWatchProviders?.takeIf { it.isNotBlank() }?.let {
            return FolderQuery(media, mapOf("with_watch_providers" to it, "watch_region" to (f.watchRegion ?: "US"), "with_watch_monetization_types" to "flatrate"))
        }
        f.withCompanies?.takeIf { it.isNotBlank() }?.let { return FolderQuery(media, mapOf("with_companies" to it)) }
    }
    return null
}

private fun isMoodTitle(t: String): Boolean =
    listOf("مزاج", "مود", "mood", "vibe").any { t.contains(it, ignoreCase = true) }

@Composable
fun LuxoFolderMotion(
    folder: CollectionFolder,
    sectionTitle: String,
    content: @Composable (Modifier) -> Unit,
) {
    val features = rememberLuxoFeatures()
    // On a TV the cards stay still: cycling pictures on every visible card is too heavy for TV boxes.
    if (!features.animatedCatalog || luxoIsTv()) {
        content(Modifier.fillMaxSize())
        return
    }
    val lite = luxoLiteMode()
    val active = LocalScreenActive.current
    val query = remember(folder.id, folder.sources, folder.catalogSources) { folderQuery(folder) }
    var backs by remember(folder.id) { mutableStateOf(query?.let { FolderBackdrops.cache[it.key] }.orEmpty()) }
    LaunchedEffect(query) { if (query != null && backs.isEmpty()) backs = runCatching { FolderBackdrops.load(query) }.getOrDefault(emptyList()) }
    val mood = isMoodTitle(sectionTitle) || isMoodTitle(folder.title)

    val inf = rememberInfiniteTransition(label = "folder")
    val burns by inf.animateFloat(1f, if (lite) 1f else 1.09f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "burns")
    val sweep by inf.animateFloat(-0.6f, if (lite) -0.6f else 1.6f, infiniteRepeatable(tween(6800, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    val breathe by inf.animateFloat(0.965f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val glow by inf.animateFloat(0f, if (lite) 0f else 0.16f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "glow")

    var slot by remember(folder.id) { mutableIntStateOf(0) }
    LaunchedEffect(backs, active, lite) {
        if (backs.isEmpty() || !active) return@LaunchedEffect
        while (true) {
            delay(((if (slot % 2 == 0) 3800L else 3000L) * (if (lite) 2 else 1)))
            slot++
        }
    }
    val showing = if (backs.isNotEmpty() && slot % 2 == 1) backs[(slot / 2) % backs.size] else null

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            if (mood && !lite) { scaleX = breathe; scaleY = breathe }
        },
    ) {
        Crossfade(targetState = showing, animationSpec = tween(if (lite) 400 else 900), label = "folderSlide") { url ->
            if (url == null) {
                content(Modifier.fillMaxSize().graphicsLayer { scaleX = burns; scaleY = burns })
            } else {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = burns; scaleY = burns })
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.75f)))))
                    val logo = folder.titleLogoUrl?.takeIf { it.isNotBlank() }
                    if (logo != null) {
                        AsyncImage(logo, folder.title, contentScale = ContentScale.Fit, modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.7f).fillMaxHeight(0.35f))
                    } else {
                        Text(
                            folder.title,
                            style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontWeight = FontWeight.Black)),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                        )
                    }
                }
            }
        }
        if (query != null && glow > 0f) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent))))
        }
        if (!lite) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (query != null) 0.20f else 0.13f), Color.Transparent),
                        start = Offset(x - size.width * 0.25f, 0f),
                        end = Offset(x + size.width * 0.25f, size.height),
                    ),
                )
            }
        }
    }
}

/** «اكتشف» headers shimmer softly. */
@Composable
fun luxoShimmerTitleStyle(title: String, base: TextStyle, color: Color): TextStyle {
    val features = rememberLuxoFeatures()
    if (!features.animatedCatalog || luxoIsTv() || !(title.contains("اكتشف") || title.contains("Discover", ignoreCase = true))) return base
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(-300f, 900f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "x")
    return base.merge(
        TextStyle(
            brush = Brush.linearGradient(
                listOf(color, Color(0xFFEDCB78), Color.White, color),
                start = Offset(x, 0f),
                end = Offset(x + 260f, 40f),
            ),
        ),
    )
}

package com.nuvio.app.features.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.features.details.components.DetailSectionTitle
import com.nuvio.app.features.luxo.LuxoAudioPreview
import com.nuvio.app.features.luxo.LuxoImage
import com.nuvio.app.features.luxo.LuxoMediaBundle
import com.nuvio.app.features.luxo.LuxoSoundtrack
import com.nuvio.app.features.luxo.LuxoTmdb
import com.nuvio.app.features.luxo.luxoVideoTypeArabic
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.luxo.urlEncode

private enum class LuxoMediaTab(val label: String) {
    BACKDROPS("الخلفيات"),
    POSTERS("الملصقات"),
    LOGOS("الشعارات"),
    VIDEOS("فيديوهات"),
    SOUNDTRACK("الموسيقى 🎵"),
}

private val Accent1 = Color(0xFF8C7BFF)
private val Accent2 = Color(0xFFFF7EB6)

/**
 * «الوسائط»: everything TMDB has for the title (backdrops, posters, logos, every video kind) and the
 * official soundtrack with previews — under the existing trailers.
 */
@Composable
internal fun LuxoMediaHub(
    meta: MetaDetails,
    horizontalScrollPadding: Dp,
    onTrailerClick: (MetaTrailer) -> Unit,
) {
    if (!rememberLuxoFeatures().mediaHub) return
    var bundle by remember(meta.id) { mutableStateOf<LuxoMediaBundle?>(null) }
    LaunchedEffect(meta.id) {
        bundle = runCatching { LuxoTmdb.media(meta.id, meta.type, meta.imdbId) }.getOrNull()
    }
    val data = bundle ?: return
    val tabs = buildList {
        if (data.backdrops.isNotEmpty()) add(LuxoMediaTab.BACKDROPS to data.backdrops.size)
        if (data.posters.isNotEmpty()) add(LuxoMediaTab.POSTERS to data.posters.size)
        if (data.logos.isNotEmpty()) add(LuxoMediaTab.LOGOS to data.logos.size)
        if (data.videos.isNotEmpty()) add(LuxoMediaTab.VIDEOS to data.videos.size)
        add(LuxoMediaTab.SOUNDTRACK to -1)
    }
    var selected by remember(meta.id) { mutableStateOf(tabs.first().first) }
    var viewer by remember { mutableStateOf<Pair<List<LuxoImage>, Int>?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionTitle(title = "الوسائط")
        LazyRow(
            modifier = Modifier.nuvioHorizontalScrollBleed(horizontalScrollPadding).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = horizontalScrollPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = { it.first.name }) { (tab, count) ->
                val on = tab == selected
                Text(
                    text = if (count >= 0) "${tab.label} · $count" else tab.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (on) Brush.horizontalGradient(listOf(Accent1, Accent2))
                            else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
                        )
                        .border(1.dp, Color.White.copy(alpha = if (on) 0.4f else 0.12f), RoundedCornerShape(50))
                        .clickable { selected = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedContent(targetState = selected, label = "luxo_media_tab") { tab ->
            when (tab) {
                LuxoMediaTab.BACKDROPS -> ImageRow(data.backdrops, 16f / 9f, 260.dp, "w780", horizontalScrollPadding) { viewer = data.backdrops to it }
                LuxoMediaTab.POSTERS -> ImageRow(data.posters, 2f / 3f, 130.dp, "w342", horizontalScrollPadding) { viewer = data.posters to it }
                LuxoMediaTab.LOGOS -> ImageRow(data.logos, 16f / 9f, 220.dp, "w300", horizontalScrollPadding, fit = true) { viewer = data.logos to it }
                LuxoMediaTab.VIDEOS -> VideoRows(data.videos, horizontalScrollPadding, onTrailerClick)
                LuxoMediaTab.SOUNDTRACK -> SoundtrackPanel(meta, data, horizontalScrollPadding)
            }
        }
    }

    viewer?.let { (images, start) ->
        LuxoImageViewer(images = images, start = start, onDismiss = { viewer = null })
    }
}

@Composable
private fun ImageRow(
    images: List<LuxoImage>,
    ratio: Float,
    width: Dp,
    size: String,
    padding: Dp,
    fit: Boolean = false,
    onOpen: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.nuvioHorizontalScrollBleed(padding).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = padding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(images, key = { _, it -> it.path }) { index, image ->
            Box(
                Modifier
                    .width(width)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (fit) Brush.linearGradient(listOf(Color(0xFF2A2F3A), Color(0xFF151821))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                    .clickable { onOpen(index) },
            ) {
                AsyncImage(
                    model = image.url(size),
                    contentDescription = null,
                    contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (fit) 14.dp else 0.dp),
                )
                image.language?.let { lang ->
                    Text(
                        lang.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRows(videos: List<MetaTrailer>, padding: Dp, onTrailerClick: (MetaTrailer) -> Unit) {
    val groups = remember(videos) { videos.groupBy { it.type } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        groups.forEach { (type, list) ->
            Text(
                "${luxoVideoTypeArabic(type)} · ${list.size}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(
                modifier = Modifier.nuvioHorizontalScrollBleed(padding).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = padding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { video ->
                    Column(Modifier.width(240.dp).clickable { onTrailerClick(video) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
                            AsyncImage(
                                model = "https://img.youtube.com/vi/${video.key}/hqdefault.jpg",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(video.name, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        video.publishedAt?.take(4)?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundtrackPanel(meta: MetaDetails, data: LuxoMediaBundle, padding: Dp) {
    val uri = LocalUriHandler.current
    val title = data.englishTitle ?: meta.name
    var state by remember(meta.id) { mutableStateOf<Pair<Boolean, LuxoSoundtrack?>>(false to null) }
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(meta.id) {
        val result = runCatching {
            LuxoTmdb.soundtrack(title, data.year, meta.type != "movie", data.originalTitle, data.originalLanguage)
        }.getOrNull()
        state = true to result
    }
    DisposableEffect(Unit) { onDispose { LuxoAudioPreview.stop() } }
    // «تشغيل الكل»: previews play one after another.
    var playAll by remember { mutableStateOf(false) }
    fun playFrom(tracks: List<com.nuvio.app.features.luxo.LuxoTrack>, start: Int) {
        val next = tracks.drop(start).firstOrNull { it.previewUrl != null } ?: run {
            playing = null
            playAll = false
            return
        }
        val url = next.previewUrl!!
        playing = url
        LuxoAudioPreview.play(url) {
            if (playing != url) return@play
            if (playAll) playFrom(tracks, tracks.indexOf(next) + 1) else playing = null
        }
    }
    val (loaded, album) = state
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("ندوّر على الموسيقى التصويرية…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            album == null -> Text(
                "ما لقينا ألبوم موسيقى رسمي لهالعمل، تقدر تدوّر عليه من هنا:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = album.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.albumTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                        Text("${album.tracks.size} مقطوعة · اضغط ▶ تسمع ٣٠ ثانية", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (playAll) "⏹ إيقاف" else "▶ تشغيل الكل",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(listOf(Accent1, Accent2)))
                                .clickable {
                                    if (playAll) {
                                        playAll = false
                                        playing = null
                                        LuxoAudioPreview.stop()
                                    } else {
                                        playAll = true
                                        playFrom(album.tracks.take(30), 0)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(vertical = 6.dp),
                ) {
                    album.tracks.take(30).forEachIndexed { index, track ->
                        val isPlaying = playing == track.previewUrl && track.previewUrl != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = track.previewUrl != null) {
                                    val url = track.previewUrl ?: return@clickable
                                    if (isPlaying) {
                                        LuxoAudioPreview.stop()
                                        playing = null
                                        playAll = false
                                    } else if (playAll) {
                                        playFrom(album.tracks.take(30), index)
                                    } else {
                                        playing = url
                                        LuxoAudioPreview.play(url) { if (playing == url) playing = null }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPlaying) Brush.linearGradient(listOf(Accent1, Accent2))
                                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (track.previewUrl == null) Text("${index + 1}", color = Color.White, fontSize = 12.sp)
                                else Icon(if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (track.artist.isNotBlank() && track.artist != album.artist) {
                                    Text(track.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            Text(
                                "${track.durationSec / 60}:${(track.durationSec % 60).toString().padStart(2, '0')}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        val query = urlEncode("$title soundtrack")
        LazyRow(
            modifier = Modifier.nuvioHorizontalScrollBleed(padding).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            album?.let { a -> item { LinkChip("الألبوم كامل · Deezer") { runCatching { uri.openUri(a.link) } } } }
            item { LinkChip("Spotify") { runCatching { uri.openUri("https://open.spotify.com/search/$query") } } }
            item { LinkChip("YouTube Music") { runCatching { uri.openUri("https://music.youtube.com/search?q=$query") } } }
            item { LinkChip("Apple Music") { runCatching { uri.openUri("https://music.apple.com/search?term=$query") } } }
        }
    }
}

@Composable
private fun LinkChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

/** Full-screen swipeable viewer in the original resolution. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxoImageViewer(images: List<LuxoImage>, start: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val image = images[page]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = image.url("w780"),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AsyncImage(
                        model = image.url("original"),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 28.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                images.getOrNull(pager.currentPage)?.let { img ->
                    if (img.width > 0) Text(
                        "${img.width}×${img.height}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White)
                }
            }
        }
    }
}

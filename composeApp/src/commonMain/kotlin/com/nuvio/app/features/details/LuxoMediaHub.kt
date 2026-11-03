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
import kotlinx.coroutines.launch
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
    TRAILERS("الإعلانات"),
    BACKDROPS("الخلفيات"),
    POSTERS("الملصقات"),
    LOGOS("الشعارات"),
    VIDEOS("فيديوهات"),
    SOUNDTRACK("الموسيقى 🎵"),
}

private val Accent1 = Color(0xFF6F8FBF)
private val Accent2 = Color(0xFF9DB1CF)

/**
 * «الوسائط»: everything TMDB has for the title (backdrops, posters, logos, every video kind) and the
 * official soundtrack with previews — under the existing trailers.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LuxoMediaHub(
    meta: MetaDetails,
    horizontalScrollPadding: Dp,
    onTrailerClick: (MetaTrailer) -> Unit,
    showHeader: Boolean = true,
) {
    if (!rememberLuxoFeatures().mediaHub) return
    var bundle by remember(meta.id) { mutableStateOf<LuxoMediaBundle?>(null) }
    LaunchedEffect(meta.id) {
        bundle = runCatching { LuxoTmdb.media(meta.id, meta.type, meta.imdbId) }.getOrNull()
    }
    val data = bundle ?: LuxoMediaBundle()
    if (bundle == null && meta.trailers.isEmpty()) return
    // Trailers already shown under «الإعلانات» aren't repeated under «فيديوهات».
    val trailerKeys = remember(meta.trailers) { meta.trailers.map { it.key }.toSet() }
    val otherVideos = remember(data.videos, trailerKeys) { data.videos.filter { it.key !in trailerKeys } }
    val tabs = buildList {
        if (meta.trailers.isNotEmpty()) add(LuxoMediaTab.TRAILERS to meta.trailers.size)
        if (data.backdrops.isNotEmpty()) add(LuxoMediaTab.BACKDROPS to data.backdrops.size)
        if (data.posters.isNotEmpty()) add(LuxoMediaTab.POSTERS to data.posters.size)
        if (data.logos.isNotEmpty()) add(LuxoMediaTab.LOGOS to data.logos.size)
        if (otherVideos.isNotEmpty()) add(LuxoMediaTab.VIDEOS to otherVideos.size)
        if (bundle != null) add(LuxoMediaTab.SOUNDTRACK to -1)
    }
    if (tabs.isEmpty()) return
    var selectedState by remember(meta.id) { mutableStateOf(tabs.first().first) }
    val selected = if (tabs.any { it.first == selectedState }) selectedState else tabs.first().first
    var viewer by remember { mutableStateOf<Pair<List<LuxoImage>, Int>?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showHeader) DetailSectionTitle(title = "الوسائط")
        LuxoFranchiseBanner(data)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LuxoReleaseReminderChip(meta, data)
            com.nuvio.app.features.luxo.LuxoTimeLeftChip(meta.id, data.tmdbId, data.mediaType)
            com.nuvio.app.features.luxo.LuxoCharacterGuideChip(meta.id, meta.name, data.tmdbId, data.mediaType, data.englishTitle, data.year)
            com.nuvio.app.features.luxo.LuxoCompareChip(meta.name, data.tmdbId, data.mediaType)
        }
        LuxoBookmarksBlock(meta)
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
                        .clickable { selectedState = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedContent(targetState = selected, label = "luxo_media_tab") { tab ->
            when (tab) {
                LuxoMediaTab.TRAILERS -> com.nuvio.app.features.details.components.DetailTrailersSection(
                    trailers = meta.trailers,
                    onTrailerClick = onTrailerClick,
                    showHeader = false,
                    horizontalScrollPadding = horizontalScrollPadding,
                )
                LuxoMediaTab.BACKDROPS -> ImageRow(data.backdrops, 16f / 9f, 260.dp, "w780", horizontalScrollPadding) { viewer = data.backdrops to it }
                LuxoMediaTab.POSTERS -> ImageRow(data.posters, 2f / 3f, 130.dp, "w342", horizontalScrollPadding) { viewer = data.posters to it }
                LuxoMediaTab.LOGOS -> ImageRow(data.logos, 16f / 9f, 220.dp, "w300", horizontalScrollPadding, fit = true) { viewer = data.logos to it }
                LuxoMediaTab.VIDEOS -> VideoRows(otherVideos, horizontalScrollPadding, onTrailerClick)
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
    var loaded by remember(meta.id) { mutableStateOf(false) }
    var result by remember(meta.id) { mutableStateOf(com.nuvio.app.features.luxo.LuxoSoundtrack()) }
    var playing by remember { mutableStateOf<String?>(null) }
    var playAll by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    videoUrl?.let { v -> com.nuvio.app.features.luxo.LuxoVideoDialog(v) { videoUrl = null } }
    LaunchedEffect(meta.id) {
        result = runCatching {
            com.nuvio.app.features.luxo.LuxoSoundtrackFinder.find(
                title = title,
                originalTitle = data.originalTitle,
                language = data.originalLanguage,
                year = data.year,
                isSeries = meta.type != "movie",
                isAnime = (data.isAnime || meta.type == "anime") && com.nuvio.app.features.luxo.LuxoFeaturesRepository.current().animeThemes,
                composers = data.composers,
            )
        }.getOrDefault(com.nuvio.app.features.luxo.LuxoSoundtrack())
        loaded = true
    }
    DisposableEffect(Unit) { onDispose { LuxoAudioPreview.stop() } }
    val all = result.allTracks
    fun playFrom(index: Int) {
        val next = all.drop(index).firstOrNull { it.previewUrl != null } ?: run {
            playing = null
            playAll = false
            return
        }
        val url = next.previewUrl!!
        playing = url
        LuxoAudioPreview.play(url) {
            if (playing != url) return@play
            if (playAll) playFrom(all.indexOf(next) + 1) else playing = null
        }
    }
    fun toggle(track: com.nuvio.app.features.luxo.LuxoTrack) {
        val url = track.previewUrl ?: return
        if (playing == url) {
            LuxoAudioPreview.stop()
            playing = null
            playAll = false
        } else if (playAll) {
            playFrom(all.indexOf(track))
        } else {
            playing = url
            LuxoAudioPreview.play(url) { if (playing == url) playing = null }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("يتم البحث في Deezer و Apple Music و MusicBrainz و AnimeThemes…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            result.isEmpty -> Text(
                "لم يتم العثور على موسيقى تصويرية رسمية مؤكدة لهذا العمل. يمكن البحث عنها من هنا:",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${all.size} مقطوعة بدون تكرار · من ${(result.albums.map { it.source } + (if (result.themes.isNotEmpty()) listOf("AnimeThemes") else emptyList())).distinct().joinToString(" و ")}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
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
                                    playFrom(0)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
                if (result.themes.isNotEmpty()) {
                    TrackGroup(
                        header = "أغاني البداية والنهاية 🎌",
                        sub = "AnimeThemes",
                        cover = null,
                        tracks = result.themes,
                        playing = playing,
                        onToggle = ::toggle,
                        onVideo = { url ->
                            LuxoAudioPreview.stop()
                            playing = null
                            playAll = false
                            videoUrl = url
                        },
                    )
                }
                result.albums.forEach { album ->
                    TrackGroup(
                        header = album.title,
                        sub = listOfNotNull(album.artist.takeIf { it.isNotBlank() }, album.year, album.source).joinToString(" · "),
                        cover = album.cover,
                        tracks = album.tracks,
                        playing = playing,
                        onToggle = ::toggle,
                        onHeaderClick = { runCatching { uri.openUri(album.link) } },
                    )
                }
            }
        }
        val query = urlEncode("${data.originalTitle?.takeIf { data.originalLanguage != "en" } ?: title} soundtrack")
        LazyRow(
            modifier = Modifier.nuvioHorizontalScrollBleed(padding).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { LinkChip("Spotify") { runCatching { uri.openUri("https://open.spotify.com/search/$query") } } }
            item { LinkChip("YouTube Music") { runCatching { uri.openUri("https://music.youtube.com/search?q=$query") } } }
            item { LinkChip("Apple Music") { runCatching { uri.openUri("https://music.apple.com/search?term=$query") } } }
            item { LinkChip("Deezer") { runCatching { uri.openUri("https://www.deezer.com/search/$query") } } }
        }
    }
}

@Composable
private fun TrackGroup(
    header: String,
    sub: String,
    cover: String?,
    tracks: List<com.nuvio.app.features.luxo.LuxoTrack>,
    playing: String?,
    onToggle: (com.nuvio.app.features.luxo.LuxoTrack) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
    onVideo: ((String) -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(header, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        tracks.forEach { track ->
            val isPlaying = track.previewUrl != null && playing == track.previewUrl
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = track.previewUrl != null) { onToggle(track) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
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
                    if (track.previewUrl == null) Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    else Icon(if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artist.isNotBlank()) Text(track.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (track.videoUrl != null && onVideo != null) {
                    Text(
                        "🎬",
                        fontSize = 18.sp,
                        modifier = Modifier.clip(CircleShape).clickable { onVideo(track.videoUrl) }.padding(8.dp),
                    )
                } else if (track.durationSec > 0) {
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
                // Save the original-resolution file to the gallery.
                if (com.nuvio.app.features.luxo.rememberLuxoFeatures().saveImages) {
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    var saveState by remember { mutableStateOf<Boolean?>(null) }
                    Text(
                        when (saveState) { true -> "✅ تم الحفظ"; false -> "تعذّر الحفظ"; null -> "⬇ حفظ" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                val img = images.getOrNull(pager.currentPage) ?: return@clickable
                                scope.launch { saveState = com.nuvio.app.features.luxo.luxoSaveImageFromUrl(img.url("original"), "موج") }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                    LaunchedEffect(pager.currentPage) { saveState = null }
                    Spacer(Modifier.width(8.dp))
                }
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


/** «هذا الجزء ٣ من ٥ في سلسلة X» → opens the franchise page. */
@Composable
private fun LuxoFranchiseBanner(data: LuxoMediaBundle) {
    if (!rememberLuxoFeatures().franchises) return
    val key = data.collectionId?.let { "collection:$it" }
        ?: data.tmdbId?.toIntOrNull()?.let { id -> data.mediaType?.let { com.nuvio.app.features.luxo.LuxoFranchiseRepository.groupForTmdb(it, id) } }
        ?: return
    var info by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        val f = com.nuvio.app.features.luxo.LuxoFranchiseRepository.load(key) ?: return@LaunchedEffect
        val index = f.parts.indexOfFirst { it.tmdbId?.toString() == data.tmdbId }
        info = if (index >= 0) "هذا الجزء ${index + 1} من ${f.parts.size} في «${f.title}»" else "جزء من «${f.title}»"
    }
    val text = info ?: data.collectionName?.let { "جزء من «$it»" } ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Accent1.copy(alpha = 0.25f), Accent2.copy(alpha = 0.15f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable { com.nuvio.app.features.luxo.LuxoOverlays.franchise = key }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎞️", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("عرض السلسلة ←", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


/** «⏰ نبهني لما ينزل»: for movies not out yet, or only in cinemas (released in the last 4 months). */
@Composable
private fun LuxoReleaseReminderChip(meta: MetaDetails, data: LuxoMediaBundle) {
    if (!rememberLuxoFeatures().releaseReminders || data.mediaType != "movie") return
    val id = data.tmdbId ?: return
    val date = data.releaseDate ?: return
    val today = remember { com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate() }
    val recentLimit = remember(today) {
        val y = today.take(4).toIntOrNull() ?: return@remember today
        val m = today.drop(5).take(2).toIntOrNull() ?: 1
        val back = m - 4
        val yy = if (back <= 0) y - 1 else y
        val mm = if (back <= 0) back + 12 else back
        "$yy-${mm.toString().padStart(2, '0')}-01"
    }
    if (date < recentLimit) return
    val set = com.nuvio.app.features.luxo.LuxoReminders.movies.containsKey(id)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (set) Brush.horizontalGradient(listOf(Accent1, Accent2)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable { com.nuvio.app.features.luxo.LuxoReminders.toggleMovie(id, meta.name, data.backdrops.firstOrNull()?.url("w780")) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (set) "⏰ سيتم التنبيه عند نزوله رقمياً" else if (date > today) "⏰ نبهني لما ينزل" else "⏰ نبهني لما ينزل رقمياً",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Saved moments of this title (from the player), with their notes. */
@Composable
private fun LuxoBookmarksBlock(meta: MetaDetails) {
    if (!rememberLuxoFeatures().bookmarks) return
    val marks = com.nuvio.app.features.luxo.LuxoBookmarks.all.filter { it.title == meta.name }
    if (marks.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔖 العلامات المحفوظة · ${marks.size}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        marks.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.positionMs })).forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(m.season?.let { s -> m.episode?.let { "S$s E$it" } }, com.nuvio.app.features.luxo.luxoFormatMs(m.positionMs)).joinToString(" · "),
                    color = Accent2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(120.dp),
                )
                Text(m.note.ifBlank { "—" }, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("✕", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.clickable { com.nuvio.app.features.luxo.LuxoBookmarks.remove(m) }.padding(6.dp))
            }
        }
        Text("الرجوع لأي علامة يتم من قائمة المشغل ← «العلامات»", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}

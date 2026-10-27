package com.nuvio.app.features.luxo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Remembers which titles were already judged so the queue keeps moving forward. */
private object LuxoDiscoverySeen {
    private const val KEY = "discovery_seen_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: MutableList<String>? = null

    private fun all(): MutableList<String> = cache ?: run {
        val loaded = runCatching {
            LuxoPrefsStorage.load(KEY)?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }
        }.getOrNull().orEmpty().toMutableList()
        cache = loaded
        loaded
    }

    fun contains(key: String) = key in all()

    fun add(key: String) {
        val list = all()
        list.remove(key)
        list.add(key)
        while (list.size > 800) list.removeAt(0)
        LuxoPrefsStorage.save(KEY, json.encodeToString(ListSerializer(String.serializer()), list))
    }

    fun clear() {
        cache = mutableListOf()
        LuxoPrefsStorage.save(KEY, "[]")
    }
}

private const val DISCOVERY_LIST_NAME = "من طابور الاستكشاف"

private fun saveLiked(item: MetaPreview) {
    val list = LuxoUserData.state.value.lists.firstOrNull { it.name == DISCOVERY_LIST_NAME }
        ?: LuxoUserData.createList(DISCOVERY_LIST_NAME, "🧭")
    if (!LuxoUserData.isInList(list.id, item.type, item.id)) {
        LuxoUserData.toggleInList(
            list.id,
            LuxoSavedTitle(id = item.id, type = item.type, name = item.name, poster = item.poster, banner = item.banner),
        )
    }
}

private fun discoveryCandidates(items: List<MetaPreview>): List<MetaPreview> {
    val rated = LuxoUserData.state.value.ratings.keys
    return items
        .filter { !it.poster.isNullOrBlank() }
        .distinctBy { "${it.type}:${it.id}" }
        .filterNot { LuxoDiscoverySeen.contains("${it.type}:${it.id}") || LuxoUserData.key(it.type, it.id) in rated }
}

/** Instant: what the home screen already loaded. */
private fun quickDiscoveryQueue(): List<MetaPreview> =
    discoveryCandidates(HomeRepository.uiState.value.sections.flatMap { it.items }).shuffled().take(40)

/** Slower: smart collections from the network, appended once they arrive. */
private suspend fun extraDiscoveryItems(): List<MetaPreview> =
    discoveryCandidates(
        listOf(LuxoContentType.MOVIE, LuxoContentType.SERIES)
            .flatMap { type -> runCatching { LuxoSmartCollections.load(type) }.getOrDefault(emptyList()) }
            .flatMap { it.items },
    ).shuffled().take(30)

/** Entry card shown on Home and in the section tabs. */
@Composable
internal fun LuxoDiscoveryCard(
    onOpen: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberLuxoFeatures().discoveryQueue) return
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF2BD1C4).copy(alpha = 0.30f), Color(0xFF4F7BFF).copy(alpha = 0.22f), Color(0xFFB57BFF).copy(alpha = 0.16f)),
                ),
            )
            .clickable { open = true }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🧭", style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(lx("طابور الاستكشاف", "Discovery queue"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                lx("اسحب يمين للي يعجبك ويسار للي ما يعجبك", "Swipe right to keep, left to skip"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) {
        LuxoDiscoveryQueueDialog(
            onDismiss = { open = false },
            onOpen = { item ->
                open = false
                onOpen(item)
            },
        )
    }
}

@Composable
internal fun LuxoDiscoveryQueueDialog(onDismiss: () -> Unit, onOpen: (MetaPreview) -> Unit) {
    var queue by remember { mutableStateOf<List<MetaPreview>?>(null) }
    var kept by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val hapticsOn = rememberLuxoFeatures().haptics
    LaunchedEffect(round) {
        val quick = quickDiscoveryQueue()
        if (quick.isNotEmpty()) queue = quick
        val extra = extraDiscoveryItems()
        val current = queue.orEmpty()
        queue = (current + extra).distinctBy { "${it.type}:${it.id}" }
    }

    fun decide(item: MetaPreview, like: Boolean) {
        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        LuxoDiscoverySeen.add("${item.type}:${item.id}")
        if (like) {
            saveLiked(item)
            kept += 1
        }
        queue = queue?.drop(1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B0F1A), Color(0xFF120B1F))))
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            queue?.firstOrNull()?.let { top ->
                AsyncImage(
                    model = luxoHighResImage(top.banner ?: top.poster, maxBackdrop1080 = true),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.35f },
                )
            }
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lx("طابور الاستكشاف 🧭", "Discovery queue 🧭"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            lx("انحفظ $kept في «$DISCOVERY_LIST_NAME»", "$kept saved to \"$DISCOVERY_LIST_NAME\""),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth().widthIn(max = 460.dp), contentAlignment = Alignment.Center) {
                    val items = queue
                    when {
                        items == null -> LuxoSkeletonBox(
                            Modifier.fillMaxHeight().aspectRatio(2f / 3f, matchHeightConstraintsFirst = true),
                            corner = 28.dp,
                        )
                        items.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 42.sp)
                            Text(
                                lx("خلص الطابور! ارجع بعدين لأعمال جديدة.", "Queue finished! Come back later for more."),
                                color = Color.White,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                lx("ابدأ من جديد", "Start over"),
                                color = Color(0xFF7BF7FF),
                                modifier = Modifier.clickable {
                                    LuxoDiscoverySeen.clear()
                                    queue = null
                                    round += 1
                                }.padding(8.dp),
                            )
                        }
                        else -> {
                            items.take(3).reversed().forEachIndexed { reversedIndex, item ->
                                val depth = minOf(items.size, 3) - 1 - reversedIndex
                                key("${item.type}:${item.id}") {
                                    LuxoSwipeCard(
                                        item = item,
                                        depth = depth,
                                        onDecide = { like -> decide(item, like) },
                                    )
                                }
                            }
                        }
                    }
                }
                val top = queue?.firstOrNull()
                Row(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LuxoRoundButton(Icons.Rounded.Close, Color(0xFFFF6B6B), 62) { top?.let { decide(it, false) } }
                    LuxoRoundButton(Icons.Rounded.Info, Color(0xFF8C9EFF), 48) { top?.let(onOpen) }
                    LuxoRoundButton(Icons.Rounded.Favorite, Color(0xFF4CE0A0), 62) { top?.let { decide(it, true) } }
                }
            }
        }
    }
}

@Composable
private fun LuxoRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(1.5.dp, tint.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.45f).dp))
    }
}

@Composable
private fun LuxoSwipeCard(item: MetaPreview, depth: Int, onDecide: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val threshold = with(density) { 110.dp.toPx() }
    val flyOut = with(density) { 600.dp.toPx() }
    val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .padding(top = (depth * 10).dp)
            // A real poster shape (2:3), never stretched to the screen width.
            .fillMaxHeight()
            .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                val scale = 1f - depth * 0.05f
                scaleX = scale
                scaleY = scale
                translationX = offsetX.value
                translationY = offsetY.value
                // Physical right is always "keep", in Arabic and English alike.
                rotationZ = offsetX.value / 28f
            }
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .then(
                if (depth == 0) {
                    Modifier.pointerInputDrag(
                        onDrag = { dx, dy ->
                            scope.launch { offsetX.snapTo(offsetX.value + dx) }
                            scope.launch { offsetY.snapTo(offsetY.value + dy) }
                        },
                        onEnd = {
                            val x = offsetX.value
                            if (abs(x) > threshold) {
                                val like = x > 0
                                scope.launch {
                                    offsetX.animateTo(if (like) flyOut else -flyOut, tween(220))
                                    onDecide(like)
                                }
                            } else {
                                scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 0.6f)) }
                                scope.launch { offsetY.animateTo(0f, spring(dampingRatio = 0.6f)) }
                            }
                        },
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = luxoHighResImage(item.poster),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color(0xFF1A1F2B)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))),
        )
        if (depth == 0 && abs(progress) > 0.15f) {
            val like = progress > 0
            Text(
                text = if (like) lx("يعجبني ❤️", "KEEP ❤️") else lx("مو لي ✕", "SKIP ✕"),
                color = if (like) Color(0xFF4CE0A0) else Color(0xFFFF6B6B),
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .graphicsLayer { alpha = abs(progress) }
                    .border(3.dp, if (like) Color(0xFF4CE0A0) else Color(0xFFFF6B6B), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(item.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.releaseInfo?.takeIf { it.isNotBlank() },
                item.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" },
                item.genres.take(3).joinToString(" · ").takeIf { it.isNotBlank() },
            ).joinToString("   ")
            if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Modifier.pointerInputDrag(onDrag: (Float, Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = onEnd,
            onDragCancel = onEnd,
        ) { change, amount ->
            change.consume()
            onDrag(amount.x, amount.y)
        }
    }

package com.nuvio.app.features.luxo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.luxoLocalHour
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The bar looks Luxo offers (the original Nuvio bar and Luxo Dock stay available). */
enum class LuxoBarStyle(val id: String, val ar: String) {
    ORIGINAL("original", "الأصلي (نوفيو)"),
    DOCK("dock", "Luxo Dock"),
    ISLAND("island", "الجزيرة 🏝️"),
    CINEMA("cinema", "السينمائي 🎬"),
    SPLIT("split", "المنقسم ✂️"),
    NEON("neon", "النيون ✨"),
    DISCS("discs", "الأقراص 💿"),
    GREETING("greeting", "التحية 👋"),
    FAN("fan", "المروحة 🪭"),
    CURTAIN("curtain", "الستارة 🎭"),
    MAGNET("magnet", "المغناطيس 🧲"),
    LIQUID("liquid", "السائل 💧"),
    COMMAND("command", "لوحة الأوامر ⌘"),
    GESTURE("gesture", "السحب بإصبعين ✌️"),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: DOCK
    }
}

private val BA1 = Color(0xFF8C7BFF)
private val BA2 = Color(0xFFFF7EB6)

private val FloatingNavigationItem.isProfile: Boolean get() = content != null
private val FloatingNavigationItem.isSearch: Boolean get() = id == LuxoNavTabIds.SEARCH

@Composable
private fun ItemIcon(item: FloatingNavigationItem, tint: Color, size: Dp = 24.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            item.content != null -> item.content.invoke(item.onClick, tint)
            item.icon != null -> Icon(item.icon, item.label, Modifier.size(size), tint = tint)
            item.drawable != null -> Icon(painterResource(item.drawable), item.label, Modifier.size(size), tint = tint)
        }
        LuxoNavBadge(item.badge, Modifier.align(Alignment.TopEnd))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.navClick(item: FloatingNavigationItem): Modifier = combinedClickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = item.onClick,
    onLongClick = item.onLongClick,
)

@Composable
private fun glass(alpha: Float): Brush {
    val t = MaterialTheme.nuvio
    return Brush.verticalGradient(listOf(t.colors.surface.copy(alpha = alpha), t.colors.background.copy(alpha = alpha)))
}

/**
 * Draws the chosen bar style. [align] is the box alignment for the bar (top/bottom + start/center/end),
 * [hideProgress] 0..1 slides it away while scrolling.
 */
@Composable
internal fun BoxScope.LuxoStyledBar(
    style: LuxoBarStyle,
    items: List<FloatingNavigationItem>,
    onTop: Boolean,
    hideProgress: Float,
    contentPadding: PaddingValues,
) {
    val nav = rememberLuxoNavSettings()
    val scale = when (nav.barSize) { 0 -> 0.86f; 2 -> 1.16f; else -> 1f }
    val horizontal = when (nav.barPosition) {
        "start" -> Alignment.Start
        "end" -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val vertical = if (onTop) Alignment.Top else Alignment.Bottom
    val full = nav.barPosition == "full"
    val labels = nav.barLabels
    val alpha = nav.barOpacity.coerceIn(0.35f, 1f)
    val slide = Modifier.graphicsLayer {
        translationY = (if (onTop) -1f else 1f) * 170.dp.toPx() * hideProgress
        this.alpha = 1f - hideProgress
    }
    val place = Modifier
        .align(alignOf(horizontal, vertical))
        .padding(contentPadding)
        .padding(horizontal = 14.dp)
        .then(slide)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    when (style) {
        LuxoBarStyle.ISLAND -> IslandBar(items, labels, alpha, hideProgress, place)
        LuxoBarStyle.CINEMA -> CinemaBar(items, alpha, Modifier.align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth().then(slide), onTop)
        LuxoBarStyle.SPLIT -> SplitBar(items, labels, alpha, if (full) place.fillMaxWidth() else place)
        LuxoBarStyle.NEON -> NeonBar(items, place)
        LuxoBarStyle.DISCS -> DiscsBar(items, labels, place)
        LuxoBarStyle.GREETING -> GreetingBar(items, labels, alpha, if (full) place.fillMaxWidth() else place)
        LuxoBarStyle.FAN -> FanBar(items, onTop, nav.barPosition)
        LuxoBarStyle.CURTAIN -> CurtainBar(items, onTop)
        LuxoBarStyle.MAGNET -> MagnetBar(items, alpha, place)
        LuxoBarStyle.LIQUID -> LiquidBar(items, labels, alpha, if (full) place.fillMaxWidth() else place)
        LuxoBarStyle.COMMAND -> CommandBar(items, alpha, place)
        LuxoBarStyle.GESTURE -> GestureDots(items, place)
        LuxoBarStyle.ORIGINAL, LuxoBarStyle.DOCK -> Unit
    }
}

private fun alignOf(h: Alignment.Horizontal, v: Alignment.Vertical): Alignment = when {
    v == Alignment.Top && h == Alignment.Start -> Alignment.TopStart
    v == Alignment.Top && h == Alignment.End -> Alignment.TopEnd
    v == Alignment.Top -> Alignment.TopCenter
    h == Alignment.Start -> Alignment.BottomStart
    h == Alignment.End -> Alignment.BottomEnd
    else -> Alignment.BottomCenter
}

private fun showLabel(mode: String, selected: Boolean) = when (mode) {
    "always" -> true
    "none" -> false
    else -> selected
}

// ── 1. Island: icons only; the open tab stretches with its name; shrinks to a dot while scrolling.
@Composable
private fun IslandBar(items: List<FloatingNavigationItem>, labels: String, alpha: Float, hide: Float, modifier: Modifier) {
    val muted = MaterialTheme.nuvio.colors.textMuted
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.82f * alpha + 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = 380f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val sel = item.selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Brush.horizontalGradient(listOf(BA1, BA2)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .navClick(item)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemIcon(item, if (sel) Color.White else muted, 22.dp)
                AnimatedVisibility(
                    visible = showLabel(labels, sel) && !item.isProfile,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Text("  " + item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

// ── 2. Cinema: full width, Luxo wordmark, text tabs with a gliding glowing underline, avatar on the far side.
@Composable
private fun CinemaBar(items: List<FloatingNavigationItem>, alpha: Float, modifier: Modifier, onTop: Boolean) {
    val positions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val selectedIndex = items.indexOfFirst { it.selected && !it.isProfile }
    val target = positions[selectedIndex]
    val x by animateFloatAsState(target?.first ?: 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "cin_x")
    val w by animateFloatAsState(target?.second ?: 0f, spring(dampingRatio = 0.8f, stiffness = 300f), label = "cin_w")
    val density = LocalDensity.current
    val top = if (onTop) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    Row(
        modifier
            .background(
                Brush.verticalGradient(
                    if (onTop) listOf(Color.Black.copy(alpha = 0.85f * alpha), Color.Transparent)
                    else listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f * alpha)),
                ),
            )
            .padding(top = top + 10.dp, bottom = 14.dp, start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Luxo",
            style = TextStyle(brush = Brush.horizontalGradient(listOf(BA1, BA2))),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(28.dp))
        Box(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (item.isProfile) return@forEachIndexed
                    Text(
                        item.label,
                        color = if (item.selected) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .onGloballyPositioned { c -> positions[i] = c.positionInParent().x to c.size.width.toFloat() }
                            .navClick(item)
                            .padding(vertical = 6.dp),
                    )
                }
            }
            if (target != null) {
                Box(
                    Modifier
                        .offset { IntOffset(x.roundToInt(), with(density) { 34.dp.roundToPx() }) }
                        .width(with(density) { w.toDp() })
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(BA1, BA2))),
                )
            }
        }
        items.firstOrNull { it.isProfile }?.let { p ->
            Box(Modifier.size(38.dp).clip(CircleShape).navClick(p), contentAlignment = Alignment.Center) { ItemIcon(p, Color.White, 32.dp) }
        }
    }
}

// ── 3. Split: tabs in one capsule, search and profile as their own floating circles.
@Composable
private fun SplitBar(items: List<FloatingNavigationItem>, labels: String, alpha: Float, modifier: Modifier) {
    val muted = MaterialTheme.nuvio.colors.textMuted
    val main = items.filter { !it.isProfile && !it.isSearch }
    val side = items.filter { it.isProfile || it.isSearch }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(glass(alpha))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            main.forEach { item ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (item.selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .navClick(item)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(item, if (item.selected) Color.White else muted, 22.dp)
                    if (showLabel(labels, item.selected)) {
                        Text(" " + item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        side.forEach { item ->
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (item.selected) Brush.linearGradient(listOf(BA1, BA2)) else glass(alpha))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .navClick(item),
                contentAlignment = Alignment.Center,
            ) { ItemIcon(item, Color.White, if (item.isProfile) 34.dp else 22.dp) }
        }
    }
}

// ── 4. Neon: big bold words without a background; the open one glows in the accent gradient.
@Composable
private fun NeonBar(items: List<FloatingNavigationItem>, modifier: Modifier) {
    Row(modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            val s by animateFloatAsState(if (item.selected) 1.12f else 1f, label = "neon")
            if (item.isProfile) {
                Box(Modifier.size(36.dp).clip(CircleShape).navClick(item)) { ItemIcon(item, Color.White, 34.dp) }
            } else {
                Text(
                    item.label,
                    style = if (item.selected) TextStyle(brush = Brush.horizontalGradient(listOf(BA1, BA2))) else TextStyle(color = Color.White.copy(alpha = 0.5f)),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }.navClick(item),
                )
            }
        }
    }
}

// ── 5. Discs: separate round buttons; the open one grows and fills with colour.
@Composable
private fun DiscsBar(items: List<FloatingNavigationItem>, labels: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        items.forEach { item ->
            val size by animateDpAsState(if (item.selected) 60.dp else 50.dp, spring(dampingRatio = 0.6f), label = "disc")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(if (item.selected) Brush.linearGradient(listOf(BA1, BA2)) else Brush.linearGradient(listOf(Color(0xE61C1E26), Color(0xE6111218))))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .navClick(item),
                    contentAlignment = Alignment.Center,
                ) { ItemIcon(item, Color.White, if (item.isProfile) size - 10.dp else 22.dp) }
                if (showLabel(labels, item.selected) && !item.isProfile) {
                    Text(item.label, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ── 6. Greeting: a time-of-day greeting and the clock next to the tabs.
@Composable
private fun GreetingBar(items: List<FloatingNavigationItem>, labels: String, alpha: Float, modifier: Modifier) {
    var hour by remember { mutableIntStateOf(luxoLocalHour()) }
    var clock by remember { mutableStateOf(luxoClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = luxoClockText()
            hour = luxoLocalHour()
            delay(20_000L)
        }
    }
    val greeting = when (hour) {
        in 5..11 -> "صباح الخير ☀️"
        in 12..16 -> "نهارك سعيد 🌤️"
        in 17..20 -> "مساء الخير 🌇"
        else -> "مساء الخير 🌙"
    }
    val muted = MaterialTheme.nuvio.colors.textMuted
    Row(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(glass(alpha))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 14.dp)) {
            Text(greeting, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(clock, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.12f)))
        Spacer(Modifier.width(8.dp))
        items.forEach { item ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (item.selected) Brush.horizontalGradient(listOf(BA1, BA2)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .navClick(item)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemIcon(item, if (item.selected) Color.White else muted, 22.dp)
                if (showLabel(labels, item.selected) && !item.isProfile) Text(" " + item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

internal fun luxoClockText(): String = runCatching {
    val t = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrDefault("")

// ── 7. Fan: one floating Luxo orb; tapping it opens the tabs in an arc around it.
@Composable
private fun BoxScope.FanBar(items: List<FloatingNavigationItem>, onTop: Boolean, position: String) {
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 300f), label = "fan")
    val start = position == "start"
    val align = when {
        onTop && start -> Alignment.TopStart
        onTop -> Alignment.TopEnd
        start -> Alignment.BottomStart
        else -> Alignment.BottomEnd
    }
    if (progress > 0.01f) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * progress)).pointerInput(Unit) { detectTapGestures { open = false } })
    }
    val density = LocalDensity.current
    val pad = if (onTop) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp else 28.dp
    Box(Modifier.align(align).padding(top = if (onTop) pad else 0.dp, bottom = if (onTop) 0.dp else pad, start = 24.dp, end = 24.dp)) {
        val radius = with(density) { 150.dp.toPx() }
        items.forEachIndexed { i, item ->
            // Quarter circle opening away from the corner.
            val angle = kotlin.math.PI / 2 * i / (items.size - 1).coerceAtLeast(1)
            val dx = (cos(angle) * radius * progress).toFloat() * (if (start) 1f else -1f)
            val dy = (sin(angle) * radius * progress).toFloat() * (if (onTop) 1f else -1f)
            if (progress > 0.02f) {
                Box(
                    Modifier
                        .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                        .graphicsLayer { alpha = progress; scaleX = 0.6f + 0.4f * progress; scaleY = 0.6f + 0.4f * progress }
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(if (item.selected) Brush.linearGradient(listOf(BA1, BA2)) else Brush.linearGradient(listOf(Color(0xF21C1E26), Color(0xF2111218))))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable {
                            open = false
                            item.onClick()
                        },
                    contentAlignment = Alignment.Center,
                ) { ItemIcon(item, Color.White, if (item.isProfile) 40.dp else 22.dp) }
            }
        }
        Box(
            Modifier
                .size(58.dp)
                .graphicsLayer { rotationZ = 135f * progress }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BA1, BA2)))
                .clickable { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (progress > 0.5f) "+" else "L", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ── 8. Curtain: hidden; a thin handle shows the open tab; pull it (or tap) for big cards.
@Composable
private fun BoxScope.CurtainBar(items: List<FloatingNavigationItem>, onTop: Boolean) {
    var open by remember { mutableStateOf(false) }
    val current = items.firstOrNull { it.selected }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        Modifier
            .align(if (onTop) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(top = if (onTop) top + 4.dp else 0.dp, bottom = if (onTop) 0.dp else 10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectDragGestures { _, drag -> if (abs(drag.y) > 8f) open = true }
            }
            .clickable { open = true }
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.6f)))
            Spacer(Modifier.width(10.dp))
            Text(current?.label ?: "Luxo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xF2101218))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(30.dp))
                    .padding(20.dp),
            ) {
                Text("إلى أين؟", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(columns = GridCells.Adaptive(130.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items) { item ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (item.selected) Brush.linearGradient(listOf(BA1, BA2)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))))
                                .clickable {
                                    open = false
                                    item.onClick()
                                }
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ItemIcon(item, Color.White, if (item.isProfile) 44.dp else 32.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(item.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ── 9. Magnet: small icons that swell under the finger (like the Mac dock) and show their names.
@Composable
private fun MagnetBar(items: List<FloatingNavigationItem>, alpha: Float, modifier: Modifier) {
    var touchX by remember { mutableFloatStateOf(-1f) }
    val centers = remember { mutableStateMapOf<Int, Float>() }
    val density = LocalDensity.current
    val reach = with(density) { 110.dp.toPx() }
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(glass(alpha))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp))
            .pointerInput(items.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        touchX = if (change.pressed) change.position.x else -1f
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { i, item ->
            val center = centers[i]
            val near = if (touchX < 0f || center == null) 0f else (1f - abs(touchX - center) / reach).coerceIn(0f, 1f)
            val grow by animateFloatAsState(1f + 0.7f * near, spring(stiffness = 600f), label = "mag")
            Column(
                Modifier.onGloballyPositioned { c -> centers[i] = c.positionInParent().x + c.size.width / 2f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (near > 0.75f && !item.isProfile) {
                    Text(item.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Box(
                    Modifier
                        .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (item.selected) Brush.linearGradient(listOf(BA1, BA2)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))))
                        .navClick(item),
                    contentAlignment = Alignment.Center,
                ) { ItemIcon(item, Color.White, if (item.isProfile) 34.dp else 20.dp) }
                Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (item.selected) Color.White else Color.Transparent))
            }
        }
    }
}

// ── 10. Liquid: the indicator is a drop that stretches and pours from tab to tab.
@Composable
private fun LiquidBar(items: List<FloatingNavigationItem>, labels: String, alpha: Float, modifier: Modifier) {
    val muted = MaterialTheme.nuvio.colors.textMuted
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val sel = items.indexOfFirst { it.selected }
    val target = bounds[sel]
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        val goingRight = t.first > left.value
        // The leading edge runs ahead, the trailing edge follows: a stretching drop.
        kotlinx.coroutines.coroutineScope {
            launch { (if (goingRight) right else left).animateTo(if (goingRight) t.first + t.second else t.first, spring(dampingRatio = 0.7f, stiffness = 420f)) }
            launch {
                delay(70L)
                (if (goingRight) left else right).animateTo(if (goingRight) t.first else t.first + t.second, spring(dampingRatio = 0.6f, stiffness = 220f))
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(glass(alpha))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(6.dp),
    ) {
        Box(
            Modifier.matchParentSize().drawBehind {
                if (right.value > left.value) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(BA1, BA2), startX = left.value, endX = right.value),
                        topLeft = Offset(left.value, 0f),
                        size = androidx.compose.ui.geometry.Size(right.value - left.value, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, item ->
                Row(
                    Modifier
                        .onGloballyPositioned { c -> bounds[i] = c.positionInParent().x to c.size.width.toFloat() }
                        .clip(RoundedCornerShape(50))
                        .navClick(item)
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ItemIcon(item, if (item.selected) Color.White else muted, 22.dp)
                    if (showLabel(labels, item.selected) && !item.isProfile) Text(" " + item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

// ── 11. Command palette: a small pill with the current section; tap for a palette of every place.
@Composable
private fun CommandBar(items: List<FloatingNavigationItem>, alpha: Float, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val current = items.firstOrNull { it.selected }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(glass(alpha))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌘", color = BA2, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Text(current?.label ?: "Luxo", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text("▾", color = Color.White.copy(alpha = 0.6f))
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF2101218))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp))
                    .padding(14.dp),
            ) {
                items.firstOrNull { it.isSearch }?.let { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                open = false
                                s.onLongClick?.invoke() ?: s.onClick()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemIcon(s, Color.White, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("بحث عن فيلم أو مسلسل أو ممثل…", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                items.filter { !it.isSearch }.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (item.selected) Brush.horizontalGradient(listOf(BA1.copy(alpha = 0.5f), BA2.copy(alpha = 0.3f))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable {
                                open = false
                                item.onClick()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ItemIcon(item, Color.White, if (item.isProfile) 30.dp else 22.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (item.selected) Text("●", color = BA2, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// ── 12. Two-finger swipe: no bar, just dots; swiping with two fingers changes the tab.
@Composable
private fun GestureDots(items: List<FloatingNavigationItem>, modifier: Modifier) {
    Row(modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (item.selected) 22.dp else 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (item.selected) Brush.horizontalGradient(listOf(BA1, BA2)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.4f))))
                    .clickable(onClick = item.onClick),
            )
        }
    }
}

/** For the two-finger style: a horizontal two-finger swipe moves to the next / previous tab. */
internal fun Modifier.luxoTwoFingerSwipe(enabled: Boolean, items: List<FloatingNavigationItem>, rtl: Boolean): Modifier {
    if (!enabled) return this
    return pointerInput(items.map { it.id to it.selected }) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (down.changes.count { it.pressed } < 2) continue
                val startX = down.changes.map { it.position.x }.average()
                var fired = false
                while (!fired) {
                    val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val pressed = ev.changes.filter { it.pressed }
                    if (pressed.size < 2) break
                    val dx = pressed.map { it.position.x }.average() - startX
                    if (abs(dx) > 140) {
                        fired = true
                        val idx = items.indexOfFirst { it.selected }
                        val forward = (dx < 0) != rtl
                        val next = items.getOrNull(if (forward) idx + 1 else idx - 1)
                        next?.onClick?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
internal fun Modifier.luxoTwoFingerSwipeIf(enabled: Boolean, items: List<FloatingNavigationItem>): Modifier =
    luxoTwoFingerSwipe(enabled, items, androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)

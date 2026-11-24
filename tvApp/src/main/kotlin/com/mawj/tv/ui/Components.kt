package com.mawj.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mawj.tv.data.MetaPreview

object C {
    val Sea = Color(0xFF070D14)
    val Raised = Color(0xFF0D1620)
    val Card = Color(0xFF121C26)
    val Line = Color(0x1AEEF2F6)
    val Gold = Color(0xFFD6B36A)
    val GoldLight = Color(0xFFF1DDA6)
    val Ink = Color(0xFFF6F1E4)
    val Muted = Color(0xFFA9B6C4)
    val Dim = Color(0xFF6F7E8E)
    val Green = Color(0xFF5BBF7A)
    val Red = Color(0xFFE0574F)
}

@Composable
fun T(
    text: String,
    size: TextUnit = 16.sp,
    color: Color = C.Ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start,
    lineHeight: TextUnit = TextUnit.Unspecified,
    /** Numbers and Latin labels (years, sizes, times): Barlow, as on the tablet. */
    num: Boolean = false,
    serif: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, textAlign = align, lineHeight = lineHeight,
            fontFamily = when { num -> Fonts.num; serif -> Fonts.serif; else -> Fonts.ui },
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The one focus look of the whole app: a gold ring and a small lift on the focused item only.
 * No shadows, no blur: cheap to draw on a weak TV.
 */
@Composable
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    scale: Float = 1.06f,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, tween(140), label = "focus")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onFocused()
        }
        .border(if (focused) 3.dp else 0.dp, if (focused) C.GoldLight else Color.Transparent, shape)
        .clip(shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Loads an image at the size it is drawn, never bigger (memory is short on TVs). */
@Composable
fun Img(url: String?, w: Dp, h: Dp, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    if (url.isNullOrBlank()) return
    val ctx = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pxW = with(density) { w.roundToPx() }
    val pxH = with(density) { h.roundToPx() }
    AsyncImage(
        model = remember(url, pxW, pxH) { ImageRequest.Builder(ctx).data(url).size(pxW, pxH).build() },
        contentDescription = null,
        contentScale = scale,
        modifier = modifier,
    )
}

/**
 * A poster as on the tablet: 2:3, rounded, the rating at the top corner, the title and year under it.
 * With [rotate] (the focused row) a title with several posters changes them slowly, Arabic first.
 */
@Composable
fun PosterCard(
    meta: MetaPreview,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    w: Dp = 150.dp,
    h: Dp = 223.dp,
    rotate: Boolean = false,
    index: Int = 0,
    showTitle: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(w)) {
        Box(
            Modifier
                .size(w, h)
                .onFocusChanged { focused = it.isFocused }
                .tvFocus(shape = RoundedCornerShape(10.dp), onFocused = onFocused, onClick = onClick)
                .background(C.Card),
        ) {
            PosterArt(meta, w, h, rotate && com.mawj.tv.data.Store.posterRotate, index)
            if (meta.poster.isNullOrBlank()) {
                T(meta.name, 15.sp, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(10.dp), align = TextAlign.Center)
            }
            val rating = meta.rating?.toDoubleOrNull()?.takeIf { it > 0 }
            if (rating != null) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xB80A0C10), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("★", 10.sp, color = Color(0xFFF5C542))
                    Spacer(Modifier.width(3.dp))
                    T(String.format(java.util.Locale.US, "%.1f", rating), 11.sp, weight = FontWeight.Bold, num = true)
                }
            }
        }
        if (showTitle) {
            T(meta.name, 13.sp, color = if (focused) C.GoldLight else C.Ink, maxLines = 1, modifier = Modifier.padding(top = if (focused) 12.dp else 8.dp))
            meta.year?.takeIf { it.isNotBlank() }?.let { T(it.take(4), 11.sp, color = C.Dim, num = true, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/** The poster picture; in the focused row it cross-fades slowly through the title's posters (Arabic first). */
@Composable
private fun PosterArt(meta: MetaPreview, w: Dp, h: Dp, rotate: Boolean, index: Int) {
    var urls by remember(meta.id) { mutableStateOf(listOfNotNull(meta.poster)) }
    var at by remember(meta.id) { mutableStateOf(0) }
    LaunchedEffect(meta.id, rotate) {
        if (!rotate) return@LaunchedEffect
        if (urls.size <= 1) {
            val more = com.mawj.tv.data.Tmdb.posters(meta)
            if (more.isNotEmpty()) {
                urls = (more.arabic + listOfNotNull(meta.poster) + more.others).distinct().take(4)
                at = 0
            }
        }
        // Slow, and staggered along the row so the cards never change together.
        kotlinx.coroutines.delay(1_800L * (index % 5))
        while (urls.size > 1) {
            kotlinx.coroutines.delay(9_000L)
            at = (at + 1) % urls.size
        }
    }
    androidx.compose.animation.Crossfade(urls.getOrNull(at) ?: meta.poster, animationSpec = tween(1600), label = "poster") { url ->
        Img(url, w, h, Modifier.fillMaxSize())
    }
}

@Composable
fun WideCard(
    title: String,
    image: String?,
    fraction: Float,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    w: Dp = 260.dp,
    h: Dp = 146.dp,
    /** «متبقٍ 22 د» at the top corner. */
    badge: String? = null,
    /** Season and episode above the title. */
    kicker: String? = null,
) {
    Box(modifier.size(w, h).tvFocus(shape = RoundedCornerShape(11.dp), onFocused = onFocused, onClick = onClick).background(C.Card)) {
        Img(image, w, h, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6070D14))))
        if (badge != null) {
            T(badge, 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).background(Color(0xBF0A0C10), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 11.dp, bottom = 13.dp)) {
            if (kicker != null) T(kicker, 10.sp, weight = FontWeight.Bold, num = true)
            T(title, 15.sp, weight = FontWeight.Bold, maxLines = 1)
        }
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 11.dp, vertical = 5.dp).fillMaxWidth().height(3.dp).background(Color(0x2EFFFFFF), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(C.Gold, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> if (primary) C.GoldLight else C.Ink
        primary -> C.Gold
        else -> Color(0x1AF6F1E4)
    }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onFocused = onFocused, onClick = onClick)
            .background(bg)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(text, 17.sp, color = if (focused || primary) C.Sea else C.Ink, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onClick = onClick)
            .background(if (focused) C.Ink else if (selected) Color(0x33D6B36A) else Color(0x14F6F1E4))
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        T(text, 15.sp, color = if (focused) C.Sea else if (selected) C.GoldLight else C.Muted, maxLines = 1)
    }
}

@Composable
fun Loading(text: String = "جارٍ التحميل…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(text, 18.sp, color = C.Muted) }
}

@Composable
fun Tag(text: String, gold: Boolean = false) {
    Box(
        Modifier.border(1.dp, if (gold) Color(0x8CD6B36A) else C.Line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
    ) { T(text, 12.sp, color = if (gold) C.GoldLight else C.Muted, maxLines = 1) }
}

@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.filter { it.isNotBlank() }.forEach { T(it, 15.sp, color = C.Muted, maxLines = 1) }
    }
}

@Composable
fun SideGap() = Box(Modifier.width(96.dp).fillMaxHeight())

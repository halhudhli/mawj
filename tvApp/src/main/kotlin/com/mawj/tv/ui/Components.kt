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
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align, lineHeight = lineHeight),
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

@Composable
fun PosterCard(
    meta: MetaPreview,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    w: Dp = 132.dp,
    h: Dp = 198.dp,
) {
    Box(
        modifier
            .size(w, h)
            .tvFocus(onFocused = onFocused, onClick = onClick)
            .background(C.Card),
    ) {
        Img(meta.poster, w, h, Modifier.fillMaxSize())
        if (meta.poster.isNullOrBlank()) {
            T(meta.name, 15.sp, weight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(10.dp), align = TextAlign.Center)
        }
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
) {
    Box(modifier.size(w, h).tvFocus(onFocused = onFocused, onClick = onClick).background(C.Card)) {
        Img(image, w, h, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6070D14))))
        T(title, 15.sp, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 14.dp))
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(4.dp).background(C.Gold))
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

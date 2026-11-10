package com.nuvio.app.core.ui

import com.nuvio.app.core.ui.background.appGlassBackdrop
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlinx.datetime.toLocalDateTime

/** Whether Luxo's own look is on (it can be switched back to the original look). */
@Composable
fun luxoDesignOn(): Boolean = rememberLuxoFeatures().luxoDesign

/**
 * How much glow to draw: the user's choice (none / soft / strong), softened further when the
 * accent is very light (a white accent glows as a harsh white blob otherwise).
 */
@Composable
fun luxoGlowAmount(): Float {
    val level = rememberLuxoFeatures().glowLevel
    val base = when (level) {
        0 -> 0f
        2 -> 1f
        else -> 0.45f
    }
    val accent = MaterialTheme.nuvio.colors.accent
    val luminance = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
    return if (luminance > 0.75f) base * 0.35f else base
}

/** True when the accent is near white, so content on it should be dark. */
@Composable
fun luxoAccentIsLight(): Boolean {
    val accent = MaterialTheme.nuvio.colors.accent
    return 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue > 0.75f
}

/** Two-or-more colours of the current theme accent, for gradients. */
@Composable
fun luxoAccentColors(): List<Color> {
    val palette = MaterialTheme.themePalette
    val accent = MaterialTheme.nuvio.colors.accent
    return if (palette.accentGradient.size >= 2) palette.accentGradient else listOf(accent, accent.copy(alpha = 0.55f))
}

/**
 * The Luxo glass card: a deep surface lit by the theme accent from one corner, a gradient rim,
 * and a soft top highlight.
 */
@Composable
fun Modifier.luxoGlass(shape: Shape, glowScale: Float = 1f): Modifier {
    val glow = glowScale * luxoGlowAmount()
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .clip(shape)
        // Glass UI: the blurred app background shows through the (now translucent) card.
        .appGlassBackdrop(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    lerpColor(tokens.colors.surface, accents.first(), 0.07f * glow),
                    tokens.colors.surface,
                    lerpColor(tokens.colors.surface, Color.Black, 0.18f),
                ),
            ),
        )
        .drawBehind {
            // Accent light from the reading-start top corner.
            val corner = if (isRtl) Offset(size.width, 0f) else Offset.Zero
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accents.first().copy(alpha = 0.16f * glow), Color.Transparent),
                    center = corner,
                    radius = size.maxDimension * 0.75f,
                ),
                radius = size.maxDimension * 0.75f,
                center = corner,
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accents.first().copy(alpha = 0.10f + 0.30f * glow),
                    Color.White.copy(alpha = 0.06f),
                    accents.last().copy(alpha = 0.06f + 0.16f * glow),
                ),
            ),
            shape,
        )
}

fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Luxo switch: a glowing gradient track and a springy thumb. RTL-aware. */
@Composable
fun LuxoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else -1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "luxo_switch",
    )
    val glow by animateFloatAsState(if (checked) 1f else 0f, tween(260), label = "luxo_switch_glow")
    val lightAccent = luxoAccentIsLight()
    val thumbColor by animateColorAsState(
        when {
            checked && lightAccent -> tokens.colors.background
            checked -> Color.White
            else -> tokens.colors.textMuted
        },
        label = "luxo_thumb",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(tokens.colors.borderDefault.copy(alpha = 0.9f))
            .background(Brush.horizontalGradient(accents.map { it.copy(alpha = glow) }))
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = com.nuvio.app.features.luxo.luxoTvIndicationOrNull(),
                    ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
        contentAlignment = BiasAlignment(horizontalBias = position, verticalBias = 0f),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    val squash = 1f + (1f - kotlin.math.abs(position)) * 0.25f
                    scaleX = squash
                }
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** A fade-and-rise entrance for sections as they appear. */
@Composable
fun Modifier.luxoEnter(enabled: Boolean = true, delayMs: Int = 0): Modifier {
    if (!enabled) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 420, delayMillis = delayMs))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** The small glowing accent bar used under Luxo titles. */
@Composable
fun LuxoAccentBar(modifier: Modifier = Modifier, width: Dp = 34.dp) {
    val accents = luxoAccentColors()
    Box(
        modifier
            .width(width)
            .height(4.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(accents)),
    )
}

/** A soft aurora glow pinned to the top of a screen (drawn behind content, doesn't scroll). */
@Composable
fun Modifier.luxoAurora(enabled: Boolean): Modifier {
    if (!enabled) return this
    val baseAccents = luxoAccentColors()
    val glow = luxoGlowAmount()
    if (glow <= 0f) return this
    // Luxo: warmer light around sunset, cooler and calmer at night.
    val timeTintOn = rememberLuxoFeatures().timeTint
    val hour = remember { luxoLocalHour() }
    val accents = if (!timeTintOn) baseAccents else when (hour) {
        in 16..18 -> listOf(lerpColor(baseAccents.first(), Color(0xFFFF9A5C), 0.55f), lerpColor(baseAccents.last(), Color(0xFFFFD27B), 0.5f))
        in 19..23, in 0..4 -> listOf(lerpColor(baseAccents.first(), Color(0xFF4F6BFF), 0.5f), lerpColor(baseAccents.last(), Color(0xFF7B5CFF), 0.45f))
        else -> baseAccents
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val startX = if (isRtl) size.width * 0.85f else size.width * 0.15f
        val endX = if (isRtl) size.width * 0.2f else size.width * 0.8f
        val r1 = size.width * 0.75f
        drawCircle(
            Brush.radialGradient(listOf(accents.first().copy(alpha = 0.16f * glow), Color.Transparent), center = Offset(startX, -r1 * 0.25f), radius = r1),
            radius = r1,
            center = Offset(startX, -r1 * 0.25f),
        )
        val r2 = size.width * 0.55f
        drawCircle(
            Brush.radialGradient(listOf(accents.last().copy(alpha = 0.10f * glow), Color.Transparent), center = Offset(endX, r2 * 0.1f), radius = r2),
            radius = r2,
            center = Offset(endX, r2 * 0.1f),
        )
    }
}

@Suppress("unused")
private val LuxoCenter = Alignment.Center


fun luxoLocalHour(): Int = runCatching {
    kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).hour
}.getOrDefault(12)

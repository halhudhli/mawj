package com.nuvio.app.core.ui

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

/** Whether Luxo's own look is on (it can be switched back to the original look). */
@Composable
fun luxoDesignOn(): Boolean = rememberLuxoFeatures().luxoDesign

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
fun Modifier.luxoGlass(shape: Shape, glow: Float = 1f): Modifier {
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .clip(shape)
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
                    accents.first().copy(alpha = 0.42f * glow),
                    Color.White.copy(alpha = 0.06f),
                    accents.last().copy(alpha = 0.22f * glow),
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
    val thumbColor by animateColorAsState(if (checked) Color.White else tokens.colors.textMuted, label = "luxo_thumb")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .drawBehind {
                if (glow > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accents.first().copy(alpha = 0.35f * glow), Color.Transparent),
                            radius = size.width * 0.9f,
                        ),
                        radius = size.width * 0.9f,
                    )
                }
            }
            .clip(shape)
            .background(tokens.colors.borderDefault.copy(alpha = 0.9f))
            .background(Brush.horizontalGradient(accents.map { it.copy(alpha = glow) }))
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
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
                .shadow(4.dp, CircleShape)
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
            .shadow(6.dp, RoundedCornerShape(50), ambientColor = accents.first(), spotColor = accents.first())
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(accents)),
    )
}

/** A soft aurora glow pinned to the top of a screen (drawn behind content, doesn't scroll). */
@Composable
fun Modifier.luxoAurora(enabled: Boolean): Modifier {
    if (!enabled) return this
    val accents = luxoAccentColors()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val startX = if (isRtl) size.width * 0.85f else size.width * 0.15f
        val endX = if (isRtl) size.width * 0.2f else size.width * 0.8f
        val r1 = size.width * 0.75f
        drawCircle(
            Brush.radialGradient(listOf(accents.first().copy(alpha = 0.16f), Color.Transparent), center = Offset(startX, -r1 * 0.25f), radius = r1),
            radius = r1,
            center = Offset(startX, -r1 * 0.25f),
        )
        val r2 = size.width * 0.55f
        drawCircle(
            Brush.radialGradient(listOf(accents.last().copy(alpha = 0.10f), Color.Transparent), center = Offset(endX, r2 * 0.1f), radius = r2),
            radius = r2,
            center = Offset(endX, r2 * 0.1f),
        )
    }
}

@Suppress("unused")
private val LuxoCenter = Alignment.Center

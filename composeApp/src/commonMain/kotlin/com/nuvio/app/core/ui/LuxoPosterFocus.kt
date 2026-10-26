package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.luxo.LuxoPosterFocusStyle
import com.nuvio.app.features.luxo.rememberLuxoLookSettings

/**
 * Luxo's pressed / focused poster effect (Settings → Appearance → Luxo → poster effect).
 * Goes on a wrapper around the poster so the zoom and shadow aren't clipped by the card.
 */
@Composable
internal fun Modifier.luxoPosterFocusEffect(
    shape: Shape,
    interactionSource: InteractionSource,
    imageUrl: String?,
): Modifier {
    val style = rememberLuxoLookSettings().posterFocusStyle
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = pressed || focused || hovered
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "luxo_poster_focus",
    )

    return when (style) {
        LuxoPosterFocusStyle.CLEAN -> this
            .graphicsLayer {
                val scale = 1f + 0.07f * progress
                scaleX = scale
                scaleY = scale
                shadowElevation = (2f + 22f * progress) * density
                this.shape = shape
                clip = false
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
            .border(1.dp, Color.White.copy(alpha = 0.08f + 0.14f * progress), shape)

        LuxoPosterFocusStyle.AMBIENT -> {
            val tint = (rememberArtworkTint(imageUrl, enabled = active) as? ArtworkTint.Tinted)?.value
            val glow = tint ?: Color.White
            this.graphicsLayer {
                val scale = 1f + 0.04f * progress
                scaleX = scale
                scaleY = scale
                shadowElevation = 28f * progress * density
                this.shape = shape
                clip = false
                ambientShadowColor = glow
                spotShadowColor = glow
                alpha = 1f
            }
                .drawBehind {
                    if (progress <= 0f) return@drawBehind
                    val radius = size.maxDimension * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = 0.20f * progress), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
        }

        LuxoPosterFocusStyle.SPOTLIGHT -> this
            .graphicsLayer {
                translationY = -8f * progress * density
                shadowElevation = 12f * progress * density
                this.shape = shape
                clip = false
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
            .drawBehind {
                if (progress <= 0f) return@drawBehind
                val lineWidth = size.width * 0.56f
                val lineHeight = 3.dp.toPx()
                val top = size.height + 6.dp.toPx()
                val left = (size.width - lineWidth) / 2f
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * progress), Color.Transparent),
                        startX = left,
                        endX = left + lineWidth,
                    ),
                    topLeft = Offset(left, top),
                    size = Size(lineWidth, lineHeight),
                    cornerRadius = CornerRadius(lineHeight, lineHeight),
                )
            }
    }
}

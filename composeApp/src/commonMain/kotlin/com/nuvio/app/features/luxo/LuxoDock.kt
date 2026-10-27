package com.nuvio.app.features.luxo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.luxoAccentColors
import com.nuvio.app.core.ui.nuvio
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch

/**
 * «Luxo Dock»: a floating glass capsule. The open tab grows into a glowing gradient pill with its
 * name; switching tabs bursts a ring of light and bounces the icon. A slow shimmer runs along the
 * rim. Long press, badges and the profile tab all work as in the classic bar.
 */
@Composable
internal fun LuxoDockBar(
    items: List<FloatingNavigationItem>,
    hideProgress: Float,
    onTop: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val glow = com.nuvio.app.core.ui.luxoGlowAmount()
    val showLabels = rememberLuxoNavSettings().showLabels
    val shimmer = rememberInfiniteTransition(label = "dock_shimmer")
    val sweep by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "dock_sweep",
    )
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .graphicsLayer {
                translationY = (if (onTop) -1f else 1f) * 170.dp.toPx() * hideProgress
                alpha = 1f - hideProgress
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .widthIn(max = 560.dp)
                .height(64.dp)
                .drawBehind {
                    // Accent pool of light under the dock.
                    drawOval(
                        brush = Brush.radialGradient(
                            listOf(accents.first().copy(alpha = 0.22f * glow), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * (if (onTop) 0.1f else 0.9f)),
                            radius = size.width * 0.6f,
                        ),
                        topLeft = Offset(-size.width * 0.1f, -size.height * 0.4f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 1.8f),
                    )
                }
                .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(tokens.colors.surface.copy(alpha = 0.94f), tokens.colors.background.copy(alpha = 0.96f)),
                    ),
                )
                .drawBehind {
                    // A light that travels along the rim.
                    val x = size.width * (sweep * 1.4f - 0.2f)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, accents.last().copy(alpha = 0.12f + 0.3f * glow), Color.Transparent),
                            startX = x - size.width * 0.18f,
                            endX = x + size.width * 0.18f,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), accents.first().copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))), shape)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                LuxoDockItem(item = item, showLabel = showLabels, accents = accents, glow = glow)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxoDockItem(item: FloatingNavigationItem, showLabel: Boolean, accents: List<Color>, glow: Float) {
    val onPill = if (com.nuvio.app.core.ui.luxoAccentIsLight()) Color(0xFF111111) else Color.White
    val tokens = MaterialTheme.nuvio
    val selected = item.selected
    val bounce = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            burst.snapTo(0f)
            bounce.snapTo(0.7f)
            launch { burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
        }
    }
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "dock_pill")
    val pillShape = RoundedCornerShape(24.dp)
    Box(contentAlignment = Alignment.Center) {
        // Ring of light when a tab opens.
        Box(
            Modifier
                .size(46.dp)
                .drawBehind {
                    if (burst.value in 0.01f..0.99f) {
                        drawCircle(
                            color = accents.first().copy(alpha = (1f - burst.value) * (0.25f + 0.45f * glow)),
                            radius = size.minDimension / 2f * (0.6f + burst.value * 1.1f),
                            style = Stroke(width = (1f - burst.value) * 4.dp.toPx()),
                        )
                    }
                },
        )
        Row(
            modifier = Modifier
                .height(46.dp)
                .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 420f))
                .then(
                    if (selected && glow > 0.5f) {
                        Modifier.shadow(8.dp, pillShape, ambientColor = accents.first(), spotColor = accents.first())
                    } else {
                        Modifier
                    },
                )
                .clip(pillShape)
                .background(Brush.horizontalGradient(accents.map { it.copy(alpha = pillAlpha) }))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = item.onClick,
                    onLongClick = item.onLongClick,
                )
                .padding(horizontal = if (selected && showLabel && item.content == null) 14.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (selected) onPill else tokens.colors.textMuted
                when {
                    item.content != null -> item.content.invoke(item.onClick, if (selected) onPill else null)
                    item.icon != null -> Icon(item.icon, item.label, Modifier.size(24.dp), tint = tint)
                    item.drawable != null -> Icon(painterResource(item.drawable), item.label, Modifier.size(24.dp), tint = tint)
                }
                LuxoNavBadge(item.badge, Modifier.align(Alignment.TopEnd))
            }
            AnimatedVisibility(
                visible = selected && showLabel && item.content == null,
                enter = fadeIn(tween(220)) + expandHorizontally(tween(260, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(200)),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        color = onPill,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private val DockCircle = CircleShape

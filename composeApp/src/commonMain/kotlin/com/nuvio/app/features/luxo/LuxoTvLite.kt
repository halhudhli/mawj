package com.nuvio.app.features.luxo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/*
 * Luxo on TV — the ideas Nuvio TV uses to stay smooth on weak boxes, applied to Luxo:
 *  • Light mode (tvLite): fewer moving lights, smaller pictures, no autoplaying trailer on Home,
 *    no shimmer animation, lighter image decoding. Only ever active on a TV.
 *  • Remote navigation (tvRemote): every button and card shows a clear focus frame and grows a
 *    little. The frame is animated in the draw phase only, so moving with the remote never
 *    recomposes the screen. Lists glide to keep the focused item comfortably on screen.
 */

/** Light mode is on (only on a TV). Safe to call anywhere, also outside Compose. */
fun luxoLiteMode(): Boolean = luxoIsTv() && LuxoFeaturesRepository.current().tvLite

@Composable
fun rememberLuxoLite(): Boolean = luxoIsTv() && rememberLuxoFeatures().tvLite

@Composable
fun rememberLuxoTvRemote(): Boolean = luxoIsTv() && rememberLuxoFeatures().tvRemote

/** Wraps the whole app on a TV: focus frames on every clickable and smooth focus scrolling. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LuxoTvProviders(content: @Composable () -> Unit) {
    if (!rememberLuxoTvRemote()) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalIndication provides LuxoTvIndication,
        LocalRippleConfiguration provides LuxoTvRipple,
        LocalBringIntoViewSpec provides LuxoTvBringIntoViewSpec,
        content = content,
    )
}

/** Material buttons keep their own ripple; on TV its focus layer is made clearly visible. */
@OptIn(ExperimentalMaterial3Api::class)
private val LuxoTvRipple = RippleConfiguration(
    color = Color.Unspecified,
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.30f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.26f,
    ),
)

/**
 * Keeps the focused item inside a comfortable band of the screen and glides there with a soft
 * spring. Symmetric, so it behaves the same for rows and columns and in Arabic or English.
 */
@OptIn(ExperimentalFoundationApi::class)
private val LuxoTvBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = 0.95f,
        stiffness = Spring.StiffnessMediumLow,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        val margin = containerSize * 0.12f
        val trailing = offset + size
        return when {
            size >= containerSize - margin * 2f -> offset - margin.coerceAtMost(offset.coerceAtLeast(0f))
            offset < margin -> offset - margin
            trailing > containerSize - margin -> trailing - (containerSize - margin)
            else -> 0f
        }
    }
}

/** Focus frame + slight growth for anything clickable, drawn without recomposition. */
object LuxoTvIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        LuxoTvIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x1A7C0
}

private class LuxoTvIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private val focus = Animatable(0f)
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
                val target = if (focusCount > 0) 1f else 0f
                if (focus.targetValue != target) {
                    launch { focus.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = focus.value
        if (p <= 0.001f && !pressed) {
            drawContent()
            return
        }
        // Big surfaces (a full-width hero, a wide banner) only get the frame: growing them would
        // overflow the screen and redraw a huge layer on every focus move.
        val big = size.width > 520.dp.toPx() || size.height > 420.dp.toPx()
        val grow = if (big) 1f else 1f + 0.05f * p - (if (pressed) 0.02f else 0f)
        scale(grow) {
            this@draw.drawContent()
            if (pressed) drawRect(Color.Black.copy(alpha = 0.14f))
            val stroke = 3.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = p.coerceIn(0f, 1f)),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** For clickables that hide their press effect on phones: on a TV they still show the focus frame. */
@Composable
fun luxoTvIndicationOrNull(): androidx.compose.foundation.Indication? =
    if (rememberLuxoTvRemote()) LuxoTvIndication else null

/**
 * TV only: gives this element the remote's focus when the screen opens (e.g. «تشغيل» on a details
 * page), retrying briefly while the screen is still loading. Does nothing on phones and tablets.
 */
@Composable
fun Modifier.luxoTvAutoFocus(key: Any? = Unit, enabled: Boolean = true): Modifier {
    if (!rememberLuxoTvRemote()) return this
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(12) {
            delay(150)
            if (focused) return@LaunchedEffect
            runCatching { requester.requestFocus() }
        }
    }
    return this.onFocusChanged { focused = it.isFocused || it.hasFocus }.focusRequester(requester)
}

package com.nuvio.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlin.math.PI
import kotlin.math.sin

/** True while «واجهة موج الجديدة» is on (the stage, the gold wave). */
@Composable
fun rememberMawjStageOn(): Boolean = rememberLuxoFeatures().mawjStage

/**
 * Mawj's signature: a thin gold wave drifting slowly sideways (one full pass every 14 s).
 * Drawn only in the draw phase, so it never recomposes the screen.
 */
@Composable
fun MawjWave(modifier: Modifier = Modifier, color: Color = MaterialTheme.nuvio.colors.accent, alpha: Float = 0.55f) {
    val transition = rememberInfiniteTransition(label = "mawj_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "mawj_wave_phase",
    )
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val waves = 8f
        val amplitude = size.height * 0.32f
        val mid = size.height / 2f
        val path = Path()
        val steps = 160
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = mid + amplitude * sin(((i.toFloat() / steps) * waves + phase * 2f) * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 1.2.dp.toPx()))
    }
}

/**
 * The «stage» at the top of a page: the subject's picture, melting into the night sea at the
 * bottom and on the reading-start side, with the gold wave along its lower edge. [content] sits at
 * the bottom-start, over the darkest part.
 */
@Composable
fun MawjStage(
    imageUrl: String?,
    height: Dp,
    modifier: Modifier = Modifier,
    fallback: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.fillMaxWidth().height(height)) {
        Box(
            Modifier.fillMaxSize().background(
                fallback ?: Brush.linearGradient(listOf(Color(0xFF1B2C3F), Color(0xFF3B2A24), Color(0xFF14202D))),
            ),
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = 0.25f),
                    0.45f to background.copy(alpha = 0.35f),
                    0.82f to background.copy(alpha = 0.92f),
                    1f to background,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    if (rtl) listOf(Color.Transparent, background.copy(alpha = 0.85f)) else listOf(background.copy(alpha = 0.85f), Color.Transparent),
                ),
            ),
        )
        MawjWave(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        content()
    }
}

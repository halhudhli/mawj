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
import androidx.compose.ui.draw.drawBehind
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

/**
 * The slow drift behind the sea and the gold waves. On a TV in light mode it stands still: the same
 * picture, drawn once, instead of redrawing a large part of the screen every frame on a weak box.
 */
@Composable
internal fun mawjDrift(label: String, durationMs: Int, still: Float = 0.3f): androidx.compose.runtime.State<Float> {
    if (com.nuvio.app.features.luxo.rememberLuxoLite()) {
        return androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(still) }
    }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = label + "_t",
    )
}

/** True while «واجهة موج الجديدة» is on (the stage, the gold wave). */
@Composable
fun rememberMawjStageOn(): Boolean = rememberLuxoFeatures().mawjStage

/**
 * Mawj's signature: a thin gold wave drifting slowly sideways (one full pass every 14 s).
 * Drawn only in the draw phase, so it never recomposes the screen.
 */
@Composable
fun MawjWave(modifier: Modifier = Modifier, color: Color = MaterialTheme.nuvio.colors.accent, alpha: Float = 0.55f) {
    val phase by mawjDrift("mawj_wave", 14_000)
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
 * The lower edge of a stage: the picture ends on a wave, the page below is filled with [fill] (the
 * page background), and a thin gold line drifts slowly along the crest. Nothing is drawn over the
 * content, so text and posters never sit on the line.
 */
@Composable
fun MawjWaveEdge(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.background,
    color: Color = MaterialTheme.nuvio.colors.accent,
    height: Dp = 44.dp,
) {
    val phase by mawjDrift("mawj_edge", 16_000)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val waves = 2.5f
        val amp = size.height * 0.28f
        val mid = size.height * 0.45f
        val steps = 120
        val crest = Path()
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = mid + amp * sin(((i.toFloat() / steps) * waves * 2f + phase * 2f) * PI.toFloat())
            if (i == 0) crest.moveTo(x, y) else crest.lineTo(x, y)
        }
        val body = Path().apply {
            addPath(crest)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(body, fill)
        drawPath(crest, color.copy(alpha = 0.8f), style = Stroke(width = 1.4.dp.toPx()))
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
            val lite = com.nuvio.app.features.luxo.rememberLuxoLite()
            val picture = androidx.compose.runtime.remember(imageUrl, lite) {
                if (lite) com.nuvio.app.features.luxo.luxoHighResImage(imageUrl, maxBackdrop1080 = true) else imageUrl
            }
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
        content()
        MawjWaveEdge(Modifier.align(Alignment.BottomCenter), fill = background)
    }
}


/**
 * Loading look: a soft light passing over the sea colour, in the exact shape of what is coming, so the
 * page does not jump when pictures arrive.
 */
@Composable
fun Modifier.mawjShimmer(): Modifier {
    // TV light mode: a plain placeholder in the sea colour, no moving light (dozens of them run at
    // once while a home screen loads).
    if (com.nuvio.app.features.luxo.rememberLuxoLite()) return this.background(Color(0xFF13202C))
    val transition = rememberInfiniteTransition(label = "mawj_shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "mawj_shimmer_x",
    )
    val dark = Color(0xFF0F1A25)
    val light = Color(0xFF1D2C3B)
    return this.drawBehind {
        drawRect(dark)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(dark, light, dark),
                start = androidx.compose.ui.geometry.Offset(w * x - w * 0.5f, 0f),
                end = androidx.compose.ui.geometry.Offset(w * x + w * 0.5f, size.height),
            ),
        )
    }
}

/**
 * A real-looking sea: several wave layers stacked from the horizon down, each filled with a gradient
 * that is light at its crest and deepens below, drifting at different speeds. It belongs to the
 * picture above it, so it scrolls away with the page. The last layer ends in [fill] (the page colour).
 */
@Composable
fun MawjSea(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.background,
    gold: Color = MaterialTheme.nuvio.colors.accent,
) {
    val t by mawjDrift("mawj_sea", 24_000)
    val grain = com.nuvio.app.features.luxo.rememberLuxoFeatures().filmGrain
    Canvas(modifier.fillMaxWidth().then(if (grain) Modifier.then(com.nuvio.app.features.luxo.Touches.grain) else Modifier)) {
        val layers = 5
        val tau = (2 * PI).toFloat()
        for (i in 0 until layers) {
            val k = i / (layers - 1f)                     // 0 = far (horizon), 1 = near (bottom)
            val base = size.height * (0.12f + 0.2f * i)
            val amp = size.height * (0.025f + 0.035f * k)
            val waves = 3.5f - 1.6f * k
            val speed = (if (i % 2 == 0) 1f else -1f) * (1f + i)
            val crestTop = base - amp
            val path = Path()
            val steps = 90
            for (s in 0..steps) {
                val x = size.width * s / steps
                val y = base + amp * sin((s.toFloat() / steps) * waves * tau + t * tau * speed) +
                    amp * 0.35f * sin((s.toFloat() / steps) * waves * 2.3f * tau - t * tau * speed * 1.7f)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val crest = Path().apply { addPath(path) }
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            val light = mawjMix(Color(0xFF2E5470), Color(0xFF16293A), k)
            drawPath(
                path,
                Brush.verticalGradient(
                    0f to light.copy(alpha = 0.55f + 0.45f * k),
                    1f to fill,
                    startY = crestTop,
                    endY = size.height,
                ),
            )
            drawPath(crest, gold.copy(alpha = 0.12f + 0.4f * k), style = Stroke(width = (0.8f + 0.8f * k).dp.toPx()))
        }
    }
}

private fun mawjMix(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f,
    a.green + (b.green - a.green) * f,
    a.blue + (b.blue - a.blue) * f,
    1f,
)

package com.nuvio.app.features.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.luxo.luxoLiteMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mawj_swash
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.sin

private val MwChrome = listOf(Color(0xFFFFFFFF), Color(0xFFD9DEE6), Color(0xFF8A919D), Color(0xFFC9CED6), Color(0xFF6E7480))

/** 0..1 inside [a, b], clamped. */
private fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

/**
 * «موج» launch scene: a drop falls, rings open, silver water rises and fills the empty letters of
 * «مَوج», the word is mirrored on the water, the play button floats up like a buoy, MAWJ gathers
 * underneath, and a big wave crosses the screen into the app. The whole story follows the chosen
 * splash duration.
 */
@Composable
internal fun MawjSplashScene(settings: SplashSettings, replayKey: Int) {
    var elapsed by remember(replayKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(replayKey) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
    }
    val lite = remember { luxoLiteMode() }
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val totalS = settings.durationMs / 1000f
    // Text is measured once per size, not every frame.
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }
    val pathCache = remember { mutableMapOf<String, Path>() }
    Canvas(Modifier.fillMaxSize()) {
        // The preview in settings loops; on launch the app moves on when the time is up.
        val loop = totalS + 0.8f
        val p = ((elapsed % loop) / totalS).coerceIn(0f, 1f)
        val t = elapsed * settings.speed
        val w = size.width
        val h = size.height
        val horizon = h * 0.64f
        val cx = w / 2f

        val wordSize = (minOf(w * 0.30f, h * 0.30f) * settings.logoScale).coerceAtLeast(40f)
        val word = cache.getOrPut("w${wordSize.toInt()}_${swash.hashCode()}") {
            measurer.measure(
                "مَوج",
                TextStyle(fontFamily = swash, fontWeight = FontWeight.Black, fontSize = (wordSize / density).sp),
                constraints = Constraints(maxWidth = w.toInt().coerceAtLeast(1)),
            )
        }
        // The real outline of the letters, placed so their lowest point stays clearly above the water.
        val glyphs = pathCache.getOrPut("p${wordSize.toInt()}_${swash.hashCode()}") { word.getPathForRange(0, word.layoutInput.text.length) }
        val ink = glyphs.getBounds()
        val origin = Offset(cx - (ink.left + ink.width / 2f), horizon - h * 0.07f - ink.bottom)
        val wordPath = Path().apply { addPath(glyphs, origin) }
        val inkTop = origin.y + ink.top

        // Horizon line.
        val hz = seg(p, 0.08f, 0.11f)
        if (hz > 0f) {
            val half = w / 2f * hz
            drawLine(Color(0xFF9AA1AD).copy(alpha = 0.45f), Offset(cx - half, horizon), Offset(cx + half, horizon), strokeWidth = 1.2f)
        }

        // Empty letters (the glass).
        val vessel = (seg(p, 0.10f, 0.14f) - seg(p, 0.50f, 0.58f)).coerceIn(0f, 1f)
        if (vessel > 0f) drawPath(wordPath, Color(0xFF4A505B).copy(alpha = vessel), style = Stroke(width = 1.6f))

        // Water level: from the horizon to just over the top of the word.
        val rise = easeInOut(seg(p, 0.13f, 0.46f))
        val level = horizon + (inkTop - h * 0.04f - horizon) * rise
        val drift = t * 140f

        // Water line outside the letters while it rises.
        val outside = (seg(p, 0.12f, 0.16f) - seg(p, 0.40f, 0.48f)).coerceIn(0f, 1f)
        if (outside > 0f && rise > 0f) drawPath(wavePath(w, level, drift, closed = false, h = h), Color(0xFFC9CED6).copy(alpha = 0.6f * outside), style = Stroke(1.4f))

        // Filled letters + reflection.
        val shine = seg(p, 0.47f, 0.58f)
        if (rise > 0f) {
            drawFilledWord(wordPath, ink.width, inkTop, ink.height, origin.x + ink.left, level, drift, w, h, shine, 1f)
            val refl = seg(p, 0.44f, 0.56f) * 0.22f
            if (refl > 0f && !lite) {
                val wobble = sin(t * 2.6f) * 4f
                translate(left = wobble) {
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(cx, horizon)) {
                        drawFilledWord(wordPath, ink.width, inkTop, ink.height, origin.x + ink.left, level, drift, w, h, 0f, refl)
                    }
                }
                // Fade the reflection into the background.
                drawRect(
                    Brush.verticalGradient(listOf(Color(0x00050608), Color(0xFF050608)), startY = horizon, endY = horizon + h * 0.24f),
                    topLeft = Offset(0f, horizon + 1f),
                    size = Size(w, h - horizon),
                )
            }
        }

        // Drop, spray and rings.
        val fall = seg(p, 0f, 0.09f)
        if (p < 0.095f) drawCircle(Color(0xFFE9ECF1), radius = h * 0.009f, center = Offset(cx, -20f + (horizon + 20f) * fall * fall))
        val spray = seg(p, 0.09f, 0.19f)
        if (spray in 0.001f..0.999f && !lite) {
            val dirs = listOf(-0.6f to 1.0f, 0.55f to 1.2f, -0.15f to 1.5f, 1.1f to 0.7f, -1.15f to 0.6f)
            dirs.forEachIndexed { i, (dx, up) ->
                val x = cx + dx * h * 0.07f * spray
                val y = horizon - up * h * 0.09f * (4f * spray * (1f - spray))
                drawCircle(Color(0xFFE9ECF1).copy(alpha = 1f - spray), radius = h * (0.0035f + 0.001f * (i % 3)), center = Offset(x, y))
            }
        }
        for (k in 0..1) {
            val r = seg(p, 0.09f + k * 0.03f, 0.28f + k * 0.03f)
            if (r in 0.001f..0.999f) {
                val rx = h * 0.012f + w * 0.42f * easeOut(r)
                drawOval(
                    Color(0xFFC9CED6).copy(alpha = 0.8f * (1f - r)),
                    topLeft = Offset(cx - rx, horizon - rx * 0.12f),
                    size = Size(rx * 2f, rx * 0.24f),
                    style = Stroke(1f),
                )
            }
        }

        // The play button floats up like a buoy.
        val buoy = seg(p, 0.56f, 0.86f)
        if (buoy > 0f) {
            val s = h * 0.026f
            val bob = sin(buoy * PI.toFloat() * 4f) * s * 0.35f * (1f - buoy)
            val y = horizon - s * 0.55f + (1f - easeOut(seg(p, 0.56f, 0.62f))) * s * 1.6f + bob
            val tri = Path().apply {
                moveTo(cx - s * 0.55f, y - s)
                lineTo(cx + s * 0.85f, y)
                lineTo(cx - s * 0.55f, y + s)
                close()
            }
            drawPath(tri, Color(0xFFE9ECF1).copy(alpha = seg(p, 0.56f, 0.62f)))
        }

        // MAWJ gathers letter by letter, then the line under it.
        val latin = seg(p, 0.60f, 0.70f)
        if (latin > 0f) {
            val spacing = 1.4f - 0.85f * easeOut(latin)
            val l = measurer.measure(
                "MAWJ",
                TextStyle(fontSize = (h * 0.03f / density).sp, letterSpacing = spacing.em, color = Color(0xFFC9CED6)),
            )
            drawText(l, topLeft = Offset(cx - l.size.width / 2f + h * 0.03f * spacing / 2f, horizon + h * 0.10f), alpha = latin)
        }
        val tag = seg(p, 0.66f, 0.74f)
        if (tag > 0f) {
            val l = cache.getOrPut("t${h.toInt()}") {
                measurer.measure("عالمٌ من الحكايات، بلا حدود", TextStyle(fontSize = (h * 0.022f / density).sp, color = Color(0xFF9AA1AD)))
            }
            drawText(l, topLeft = Offset(cx - l.size.width / 2f, horizon + h * 0.16f + (1f - tag) * h * 0.012f), alpha = tag)
        }

        // A big wave crosses the screen into the app.
        val exit = easeInOut(seg(p, 0.88f, 1f))
        if (exit > 0f) {
            val edge = w * 1.05f - exit * w * 1.3f
            val path = Path().apply {
                moveTo(edge, 0f)
                var y = 0f
                while (y <= h) {
                    lineTo(edge + sin(y / h * PI.toFloat() * 3f + t * 3f) * w * 0.03f, y)
                    y += h / 30f
                }
                lineTo(w + 10f, h)
                lineTo(w + 10f, 0f)
                close()
            }
            drawPath(path, Brush.horizontalGradient(listOf(Color(0xFFE9ECF1), Color(0xFF5C6370), Color(0xFF0F1116)), startX = edge - w * 0.03f, endX = edge + w * 0.3f))
        }
    }
}

private fun wavePath(w: Float, level: Float, drift: Float, closed: Boolean, h: Float): Path {
    val period = h * 0.16f
    val amp = h * 0.012f
    return Path().apply {
        var x = -period
        moveTo(x, level + sin((x + drift) / period * 2f * PI.toFloat()) * amp)
        while (x <= w + period) {
            x += period / 12f
            lineTo(x, level + sin((x + drift) / period * 2f * PI.toFloat()) * amp)
        }
        if (closed) {
            lineTo(w + period, h * 2f)
            lineTo(-period, h * 2f)
            close()
        }
    }
}

/** Water inside the letters only: everything is clipped to the letters' outline (works on every GPU). */
private fun DrawScope.drawFilledWord(
    wordPath: Path,
    inkWidth: Float,
    inkTop: Float,
    inkHeight: Float,
    inkLeft: Float,
    level: Float,
    drift: Float,
    w: Float,
    h: Float,
    shine: Float,
    alpha: Float,
) {
    clipPath(wordPath) {
        drawPath(
            wavePath(w, level, drift, closed = true, h = h),
            Brush.verticalGradient(MwChrome, startY = level - h * 0.02f, endY = level + h * 0.42f),
            alpha = alpha,
        )
        drawPath(wavePath(w, level + h * 0.035f, -drift * 0.7f, closed = false, h = h), Color.White.copy(alpha = 0.5f * alpha), style = Stroke(2f))
        if (shine in 0.001f..0.999f) {
            val x = inkLeft - inkWidth * 0.3f + inkWidth * 1.6f * shine
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.8f * alpha), Color.Transparent), startX = x - h * 0.06f, endX = x + h * 0.06f),
                topLeft = Offset(x - h * 0.06f, inkTop),
                size = Size(h * 0.12f, inkHeight),
            )
        }
    }
}

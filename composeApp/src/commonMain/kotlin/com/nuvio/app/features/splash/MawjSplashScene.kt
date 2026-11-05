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

/** Water colours per finish: (foam, light, deep, mid, bottom). */
internal fun mawjPalette(v: SplashLogoVariant): List<Color> = when (v) {
    SplashLogoVariant.GOLD -> listOf(Color(0xFFFFF6DC), Color(0xFFF2D58C), Color(0xFF9C6F22), Color(0xFFE8C26A), Color(0xFF7A5418))
    SplashLogoVariant.ROSE_GOLD -> listOf(Color(0xFFFFEEE8), Color(0xFFEFC1B6), Color(0xFF9A5A63), Color(0xFFE3A79C), Color(0xFF7A4249))
    SplashLogoVariant.COPPER -> listOf(Color(0xFFFFEADB), Color(0xFFF0B98F), Color(0xFF8A4320), Color(0xFFDB915E), Color(0xFF6A3216))
    SplashLogoVariant.EMERALD -> listOf(Color(0xFFEFFFF7), Color(0xFFB9F0D5), Color(0xFF1E7350), Color(0xFF7FD9AE), Color(0xFF15543A))
    SplashLogoVariant.ARCTIC_BLUE -> listOf(Color(0xFFF5FAFF), Color(0xFFD9EBFF), Color(0xFF4F7FB3), Color(0xFFA9CDF2), Color(0xFF365C86))
    SplashLogoVariant.GRAPHITE -> listOf(Color(0xFFE4E7EC), Color(0xFFAEB4BE), Color(0xFF3A3F49), Color(0xFF8A919D), Color(0xFF2A2E36))
    else -> listOf(Color(0xFFFFFFFF), Color(0xFFD9DEE6), Color(0xFF8A919D), Color(0xFFC9CED6), Color(0xFF6E7480))
}

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
        // Everything is drawn as real (shaped) text: some devices can't turn Arabic letters into
        // outlines, which showed a plain box. The baseline sits so the tails of «و» and «ج» stay
        // clearly above the water line.
        val baseline = horizon - h * 0.07f - wordSize * 0.42f
        val wordTop = Offset(cx - word.size.width / 2f, baseline - word.firstBaseline)
        val inkTop = baseline - wordSize * 0.80f
        val boxTop = wordTop.y
        val boxH = word.size.height.toFloat().coerceAtLeast(1f)

        // Horizon line.
        val hz = seg(p, 0.08f, 0.11f)
        if (hz > 0f) {
            val half = w / 2f * hz
            drawLine(Color(0xFF9AA1AD).copy(alpha = 0.45f), Offset(cx - half, horizon), Offset(cx + half, horizon), strokeWidth = 1.2f)
        }

        // Empty letters (the glass).
        val vessel = (seg(p, 0.10f, 0.14f) - seg(p, 0.50f, 0.58f)).coerceIn(0f, 1f)
        if (vessel > 0f) drawText(word, color = Color(0xFF4A505B), topLeft = wordTop, alpha = vessel, drawStyle = Stroke(width = 1.6f))

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
            drawWaterWord(word, wordTop, boxTop, boxH, level, shine, 1f, mawjPalette(settings.logoVariant))
            val refl = seg(p, 0.44f, 0.56f) * 0.22f
            if (refl > 0f && !lite && settings.mawjReflection) {
                val wobble = sin(t * 2.6f) * 4f
                translate(left = wobble) {
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset(cx, horizon)) {
                        drawWaterWord(word, wordTop, boxTop, boxH, level, 0f, refl, mawjPalette(settings.logoVariant))
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
        val latin = if (settings.mawjTagline) seg(p, 0.60f, 0.70f) else 0f
        if (latin > 0f) {
            val spacing = 1.4f - 0.85f * easeOut(latin)
            val l = measurer.measure(
                "MAWJ",
                TextStyle(fontSize = (h * 0.03f / density).sp, letterSpacing = spacing.em, color = Color(0xFFC9CED6)),
            )
            drawText(l, topLeft = Offset(cx - l.size.width / 2f + h * 0.03f * spacing / 2f, horizon + h * 0.10f), alpha = latin)
        }
        val tag = if (settings.mawjTagline) seg(p, 0.66f, 0.74f) else 0f
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

/**
 * Water inside the letters: the word is painted with a gradient that is empty above the water
 * level, a bright line of foam at the level, and silver below it. Works on every device.
 */
private fun DrawScope.drawWaterWord(word: TextLayoutResult, topLeft: Offset, boxTop: Float, boxH: Float, level: Float, shine: Float, alpha: Float, pal: List<Color>) {
    val f = ((level - boxTop) / boxH).coerceIn(0f, 1f)
    if (f >= 1f) return
    fun at(x: Float) = (f + (1f - f) * x).coerceIn(0f, 1f)
    val foam = minOf(f + 0.012f, 1f)
    val water = Brush.verticalGradient(
        0f to Color.Transparent,
        f to Color.Transparent,
        f to pal[0],
        foam to pal[1],
        at(0.35f) to pal[2],
        at(0.65f) to pal[3],
        1f to pal[4],
        startY = boxTop,
        endY = boxTop + boxH,
    )
    drawText(word, brush = water, topLeft = topLeft, alpha = alpha)
    if (shine in 0.001f..0.999f) {
        val wW = word.size.width.toFloat()
        val x = topLeft.x - wW * 0.3f + wW * 1.6f * shine
        drawText(
            word,
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), startX = x - boxH * 0.12f, endX = x + boxH * 0.12f),
            topLeft = topLeft,
            alpha = alpha,
        )
    }
}

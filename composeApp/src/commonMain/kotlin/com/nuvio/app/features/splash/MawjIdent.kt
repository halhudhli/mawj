package com.nuvio.app.features.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.luxo.luxoLiteMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mawj_swash
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.sin

/** Total length of the «موج» ident. */
const val MAWJ_IDENT_MS = 4300L

private val IdBg = Color(0xFF040506)
private val IdSilver = listOf(Color(0xFF7C8FB0), Color(0xFFE9ECF1), Color(0xFFFFFFFF), Color(0xFFA9C4E8), Color(0xFF7C8FB0))

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun seg(p: Float, a: Float, b: Float) = clamp01((p - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1f - t
    return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
}

// The mark in its 200×160 box: the top edges of the two waves and the play triangle.
private val Wave1 = listOf(
    listOf(Offset(8f, 104f), Offset(46f, 74f), Offset(88f, 72f), Offset(118f, 94f)),
    listOf(Offset(118f, 94f), Offset(146f, 114f), Offset(172f, 110f), Offset(192f, 88f)),
)
private val Wave2 = listOf(
    listOf(Offset(24f, 130f), Offset(58f, 112f), Offset(94f, 115f), Offset(120f, 130f)),
    listOf(Offset(120f, 130f), Offset(144f, 144f), Offset(168f, 141f), Offset(186f, 128f)),
)
private val Triangle = listOf(Offset(74f, 4f), Offset(130f, 36f), Offset(74f, 68f), Offset(74f, 4f))

private fun sampleCurve(parts: List<List<Offset>>, n: Int): List<Offset> = List(n) { i ->
    val t = i / (n - 1f) * parts.size
    val k = t.toInt().coerceAtMost(parts.size - 1)
    val c = parts[k]
    cubic(c[0], c[1], c[2], c[3], (t - k).coerceIn(0f, 1f))
}

private fun samplePolyline(pts: List<Offset>, n: Int): List<Offset> {
    val lens = pts.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lens.sum()
    return List(n) { i ->
        var d = i / (n - 1f) * total
        var k = 0
        while (k < lens.size - 1 && d > lens[k]) { d -= lens[k]; k++ }
        val f = if (lens[k] > 0f) (d / lens[k]).coerceIn(0f, 1f) else 0f
        pts[k] + (pts[k + 1] - pts[k]) * f
    }
}

/**
 * «موج» ident, in the spirit of the big streaming idents: three ribbons of light sweep across the
 * dark, twist together and lock into the mark (two waves and the play button), a flash of light
 * runs through it, «مَوج» rises underneath with a glint, and the whole frame rushes forward into
 * the app.
 */
@Composable
fun MawjIdent(modifier: Modifier = Modifier, replayKey: Int = 0) {
    var elapsed by remember(replayKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(replayKey) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
    }
    val lite = remember { luxoLiteMode() }
    val n = if (lite) 40 else 72
    val targets = remember(n) {
        listOf(sampleCurve(Wave1, n), sampleCurve(Wave2, n), samplePolyline(Triangle, n))
    }
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }

    Canvas(modifier.fillMaxSize().background(IdBg)) {
        val total = MAWJ_IDENT_MS / 1000f
        val p = clamp01(elapsed / total)
        val t = elapsed
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val markW = minOf(w, h) * 0.34f
        val unit = markW / 200f
        val markOrigin = Offset(cx - markW / 2f, cy - h * 0.20f - 80f * unit)
        fun mark(o: Offset) = markOrigin + o * unit

        // Exit: the whole frame rushes forward.
        val exit = easeInOut(seg(p, 0.86f, 1f))
        scale(1f + exit * 0.9f, Offset(cx, cy - h * 0.06f)) {
            val fade = 1f - exit

            // 1) Ribbons of light that twist into the mark.
            val morph = easeInOut(seg(p, 0.16f, 0.56f))
            val ribbonsAlpha = (seg(p, 0f, 0.08f) - seg(p, 0.58f, 0.68f)).coerceIn(0f, 1f) * fade
            if (ribbonsAlpha > 0f) {
                val amps = listOf(0.20f, 0.13f, 0.26f)
                val bases = listOf(-0.04f, 0.07f, 0.0f)
                val freqs = listOf(1.6f, 2.3f, 1.1f)
                val speeds = listOf(2.4f, -3.0f, 1.7f)
                for (r in 0..2) {
                    val target = targets[r]
                    val path = Path()
                    for (i in 0 until n) {
                        val u = i / (n - 1f)
                        val sx = -0.15f * w + 1.3f * w * u
                        val sy = cy + bases[r] * h + amps[r] * h * (1f - morph * 0.6f) *
                            sin(u * freqs[r] * 2f * PI.toFloat() + t * speeds[r] + r)
                        val tp = mark(target[i])
                        // Ribbons slide in from the sides before they curl.
                        val enter = easeOut(seg(p, 0f, 0.18f))
                        val slide = (1f - enter) * w * (if (r == 1) 1f else -1f)
                        val x = lerp(sx + slide, tp.x, morph)
                        val y = lerp(sy, tp.y, morph)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val width = lerp(h * 0.010f, unit * 5f, morph)
                    val brush = Brush.horizontalGradient(IdSilver, startX = 0f, endX = w)
                    if (!lite) {
                        drawPath(path, brush, alpha = 0.06f * ribbonsAlpha, style = Stroke(width * 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(path, brush, alpha = 0.16f * ribbonsAlpha, style = Stroke(width * 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    drawPath(path, brush, alpha = ribbonsAlpha, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            // 2) The solid mark takes over, with a flash running through it.
            val solid = seg(p, 0.52f, 0.62f) * fade
            if (solid > 0f) {
                drawMark(::mark, unit, solid)
                val flash = seg(p, 0.55f, 0.70f)
                if (flash in 0.001f..0.999f) {
                    val x = markOrigin.x - markW * 0.3f + markW * 1.6f * flash
                    val band = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * solid), Color.Transparent),
                        start = Offset(x - markW * 0.15f, markOrigin.y),
                        end = Offset(x + markW * 0.15f, markOrigin.y + markW * 0.8f),
                    )
                    drawMark(::mark, unit, 1f, override = band)
                }
            }

            // 3) «مَوج» rises underneath, then a glint crosses it.
            val wordIn = easeOut(seg(p, 0.62f, 0.78f))
            if (wordIn > 0f) {
                val wordPx = minOf(w * 0.22f, h * 0.20f)
                val word = cache.getOrPut("w${wordPx.toInt()}_${swash.hashCode()}") {
                    measurer.measure("مَوج", TextStyle(fontFamily = swash, fontWeight = FontWeight.Black, fontSize = (wordPx / density).sp))
                }
                val top = cy + h * 0.02f + (1f - wordIn) * h * 0.04f
                val left = cx - word.size.width / 2f
                val chrome = Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFD9DEE6), Color(0xFF8A919D), Color(0xFFEEF1F5)),
                    startY = top + word.size.height * 0.2f,
                    endY = top + word.size.height * 0.85f,
                )
                drawText(word, brush = chrome, topLeft = Offset(left, top), alpha = wordIn * fade)
                val glint = seg(p, 0.74f, 0.88f)
                if (glint in 0.001f..0.999f) {
                    val gx = left - word.size.width * 0.3f + word.size.width * 1.6f * glint
                    drawText(
                        word,
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), startX = gx - wordPx * 0.18f, endX = gx + wordPx * 0.18f),
                        topLeft = Offset(left, top),
                        alpha = 0.85f * fade,
                    )
                }
                val latin = seg(p, 0.70f, 0.82f)
                if (latin > 0f) {
                    val spacing = lerp(1.2f, 0.6f, easeOut(latin))
                    val l = measurer.measure("MAWJ", TextStyle(fontSize = (h * 0.026f / density).sp, letterSpacing = spacing.em, color = Color(0xFFC9CED6)))
                    drawText(l, topLeft = Offset(cx - l.size.width / 2f + h * 0.026f * spacing / 2f, top + word.size.height * 0.92f), alpha = latin * fade)
                }
            }
        }
    }
}

private fun DrawScope.drawMark(mark: (Offset) -> Offset, unit: Float, alpha: Float, override: Brush? = null) {
    fun shape(build: Path.() -> Unit) = Path().apply(build)
    val wave1 = shape {
        val s = mark(Offset(8f, 104f)); moveTo(s.x, s.y)
        fun c(a: Offset, b: Offset, d: Offset) { val x = mark(a); val y = mark(b); val z = mark(d); cubicTo(x.x, x.y, y.x, y.y, z.x, z.y) }
        c(Offset(46f, 74f), Offset(88f, 72f), Offset(118f, 94f))
        c(Offset(146f, 114f), Offset(172f, 110f), Offset(192f, 88f))
        c(Offset(178f, 124f), Offset(142f, 134f), Offset(110f, 116f))
        c(Offset(84f, 101f), Offset(50f, 96f), Offset(8f, 104f))
        close()
    }
    val wave2 = shape {
        val s = mark(Offset(24f, 130f)); moveTo(s.x, s.y)
        fun c(a: Offset, b: Offset, d: Offset) { val x = mark(a); val y = mark(b); val z = mark(d); cubicTo(x.x, x.y, y.x, y.y, z.x, z.y) }
        c(Offset(58f, 112f), Offset(94f, 115f), Offset(120f, 130f))
        c(Offset(144f, 144f), Offset(168f, 141f), Offset(186f, 128f))
        c(Offset(172f, 152f), Offset(142f, 156f), Offset(116f, 143f))
        c(Offset(92f, 131f), Offset(62f, 125f), Offset(24f, 130f))
        close()
    }
    val tri = shape {
        val a = mark(Offset(74f, 4f)); val b = mark(Offset(130f, 36f)); val c = mark(Offset(74f, 68f))
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
    }
    val top = mark(Offset(0f, 0f)).y
    val bottom = mark(Offset(0f, 160f)).y
    val chrome = override ?: Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC9CED6), Color(0xFF7C838F), Color(0xFFE9ECF1)), startY = top, endY = bottom)
    val waveBrush = override ?: Brush.horizontalGradient(listOf(Color(0xFF6E7480), Color(0xFFF4F6F9), Color(0xFF8A919D)), startX = mark(Offset(8f, 0f)).x, endX = mark(Offset(192f, 0f)).x)
    drawPath(tri, chrome, alpha = alpha)
    drawPath(wave1, waveBrush, alpha = alpha)
    drawPath(wave2, waveBrush, alpha = alpha * 0.8f)
}

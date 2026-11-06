package com.nuvio.app.features.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.luxoLiteMode
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mawj_swash
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * «موج» launch idents, each in the spirit of a big studio/streaming opener but carrying Mawj's own
 * mark and name:
 * - [MawjIdentStyle.Strands] «شرائط الضوء» — the dive into a curtain of coloured light strands.
 * - [MawjIdentStyle.Golden] «الإطار الذهبي» — the classic golden studio emblem with a film-strip ring.
 * - [MawjIdentStyle.Arc] «قوس الضوء» — a luminous arc sweeping over the mark in a starry night.
 */
enum class MawjIdentStyle(val title: String, val inspired: String, val durationMs: Long) {
    Strands("شرائط الضوء", "مستوحاة من افتتاحية نتفليكس: يُقترب من الشعار حتى يتفتّت إلى ستارة من خيوط الضوء الملوّنة، ثم تلتئم ويعود «مَوج».", 4200L),
    Golden("الإطار الذهبي", "مستوحاة من افتتاحية MGM الكلاسيكية: إطار ذهبي بشريط سينمائي وأشعة تدور، والشعار ذهبي في المنتصف تحت لافتة «MAWJ PICTURES».", 5000L),
    Arc("قوس الضوء", "مستوحاة من افتتاحية ديزني+: ليل مرصّع بالنجوم، وقوس من الضوء يرتسم فوق الشعار ويترك خلفه شرراً، ثم يتوهّج الاسم.", 4400L),
}

/** Which ident plays at launch ("random" picks one each time; "off" skips the opener). */
object MawjIdentPrefs {
    private const val KEY = "mawj_ident_style"
    const val RANDOM = "random"
    const val OFF = "off"

    var choice by mutableStateOf(LuxoPrefsStorage.load(KEY) ?: MawjIdentStyle.Strands.name)
        private set

    fun set(value: String) { choice = value; LuxoPrefsStorage.save(KEY, value) }

    /** The ident for this launch (fixed once picked, so the hold and the animation agree). */
    val forThisLaunch: MawjIdentStyle? by lazy {
        when (choice) {
            OFF -> null
            RANDOM -> MawjIdentStyle.entries.random()
            else -> MawjIdentStyle.entries.firstOrNull { it.name == choice } ?: MawjIdentStyle.Strands
        }
    }
}

/** A tap on the opener asks the gate to move on. */
object SplashSkip {
    var requested by mutableStateOf(false)
}

// ------------------------------------------------------------------ helpers

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun seg(p: Float, a: Float, b: Float) = clamp01((p - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeIn(x: Float) = x * x * x
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private val TAU = (2.0 * PI).toFloat()

private val Chrome = listOf(Color(0xFFFFFFFF), Color(0xFFC9CED6), Color(0xFF7C838F), Color(0xFFE9ECF1))
private val Gold = listOf(Color(0xFFFFF4C9), Color(0xFFE9C46A), Color(0xFFA67A2B), Color(0xFFF6DD97), Color(0xFF8C6420))
private val IceBlue = listOf(Color(0xFFFFFFFF), Color(0xFFCFE6FF), Color(0xFF7FB2F0), Color(0xFFE6F2FF))

/** The «موج» mark (two waves under a play triangle) in a 200×160 box, filled with [brush]. */
private fun DrawScope.drawMawjMark(origin: Offset, unit: Float, alpha: Float, brush: Brush? = null) {
    if (alpha <= 0f) return
    fun m(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
    fun Path.c(ax: Float, ay: Float, bx: Float, by: Float, dx: Float, dy: Float) {
        val a = m(ax, ay); val b = m(bx, by); val d = m(dx, dy); cubicTo(a.x, a.y, b.x, b.y, d.x, d.y)
    }
    val wave1 = Path().apply {
        val s = m(8f, 104f); moveTo(s.x, s.y)
        c(46f, 74f, 88f, 72f, 118f, 94f); c(146f, 114f, 172f, 110f, 192f, 88f)
        c(178f, 124f, 142f, 134f, 110f, 116f); c(84f, 101f, 50f, 96f, 8f, 104f); close()
    }
    val wave2 = Path().apply {
        val s = m(24f, 130f); moveTo(s.x, s.y)
        c(58f, 112f, 94f, 115f, 120f, 130f); c(144f, 144f, 168f, 141f, 186f, 128f)
        c(172f, 152f, 142f, 156f, 116f, 143f); c(92f, 131f, 62f, 125f, 24f, 130f); close()
    }
    val tri = Path().apply {
        val a = m(74f, 4f); val b = m(130f, 36f); val d = m(74f, 68f)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close()
    }
    val fill = brush ?: Brush.verticalGradient(Chrome, startY = m(0f, 0f).y, endY = m(0f, 160f).y)
    drawPath(tri, fill, alpha = alpha)
    drawPath(wave1, fill, alpha = alpha)
    drawPath(wave2, fill, alpha = alpha * 0.82f)
}

/** «مَوج» (and «MAWJ» under it), centred on [cx] with its top at [top]. Returns the word's height. */
private fun DrawScope.drawMawjWord(
    measurer: TextMeasurer,
    cache: MutableMap<String, TextLayoutResult>,
    family: FontFamily,
    cx: Float,
    top: Float,
    px: Float,
    brush: Brush,
    alpha: Float,
    latinAlpha: Float = alpha,
    latinColor: Color = Color(0xFFC9CED6),
    latinSpacing: Float = 0.6f,
    glint: Float = -1f,
): Float {
    if (alpha <= 0f) return 0f
    val word = cache.getOrPut("w${px.toInt()}_${family.hashCode()}") {
        measurer.measure("مَوج", TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = (px / density).sp))
    }
    val left = cx - word.size.width / 2f
    drawText(word, brush = brush, topLeft = Offset(left, top), alpha = alpha)
    if (glint in 0.001f..0.999f) {
        val gx = left - word.size.width * 0.3f + word.size.width * 1.6f * glint
        drawText(
            word,
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), startX = gx - px * 0.2f, endX = gx + px * 0.2f),
            topLeft = Offset(left, top),
            alpha = 0.85f * alpha,
        )
    }
    if (latinAlpha > 0f) {
        val size = px * 0.13f
        val l = measurer.measure("MAWJ", TextStyle(fontSize = (size / density).sp, letterSpacing = latinSpacing.em, color = latinColor, fontWeight = FontWeight.Medium))
        drawText(l, topLeft = Offset(cx - l.size.width / 2f + size * latinSpacing / 2f, top + word.size.height * 0.9f), alpha = latinAlpha)
    }
    return word.size.height.toFloat()
}

@Composable
private fun rememberElapsed(replayKey: Int): () -> Float {
    var elapsed by remember(replayKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(replayKey) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
    }
    return { elapsed }
}

// ------------------------------------------------------------------ entry

@Composable
fun MawjIdent(style: MawjIdentStyle, modifier: Modifier = Modifier, replayKey: Int = 0) {
    when (style) {
        MawjIdentStyle.Strands -> StrandsIdent(modifier, replayKey)
        MawjIdentStyle.Golden -> GoldenIdent(modifier, replayKey)
        MawjIdentStyle.Arc -> ArcIdent(modifier, replayKey)
    }
}

// ------------------------------------------------------------------ 1) «شرائط الضوء»

private data class Strand(val x: Float, val width: Float, val color: Color, val phase: Float, val speed: Float, val depth: Float)

private val StrandPalette = listOf(
    Color(0xFF3D7BFF), Color(0xFF55D6FF), Color(0xFF8B5CFF), Color(0xFFFF4D8D),
    Color(0xFFFFB547), Color(0xFF6EF0C2), Color(0xFFE9ECF1), Color(0xFF2A44C9),
)

@Composable
private fun StrandsIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val strands = remember {
        val r = Random(7)
        List(if (lite) 40 else 96) {
            Strand(
                x = (r.nextFloat() * 2f - 1f).let { it * abs(it) * 0.6f + it * 0.4f },
                width = 0.002f + r.nextFloat() * r.nextFloat() * 0.018f,
                color = StrandPalette[r.nextInt(StrandPalette.size)],
                phase = r.nextFloat() * TAU,
                speed = 2f + r.nextFloat() * 6f,
                depth = 0.4f + r.nextFloat() * 0.6f,
            )
        }
    }
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }
    Canvas(modifier.fillMaxSize().background(Color.Black)) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Strands.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        val markW = minOf(w, h) * 0.30f
        val unit = markW / 200f

        // a) The mark glows in, then the camera dives into it.
        val markIn = easeOut(seg(p, 0.02f, 0.18f))
        val dive = easeIn(seg(p, 0.22f, 0.46f))
        val markAlpha = markIn * (1f - seg(p, 0.36f, 0.46f))
        if (markAlpha > 0f) {
            val s = lerp(0.92f, 1.06f, seg(p, 0f, 0.24f)) * (1f + dive * 9f)
            scale(s, Offset(cx, cy)) {
                drawMawjMark(Offset(cx - markW / 2f, cy - 80f * unit), unit, markAlpha)
            }
        }

        // b) A curtain of light strands opens out of it and streams past.
        val curtain = (seg(p, 0.30f, 0.42f) - seg(p, 0.70f, 0.80f)).coerceIn(0f, 1f)
        if (curtain > 0f) {
            val spread = lerp(0.05f, 1.25f, easeOut(seg(p, 0.30f, 0.62f))) * lerp(1f, 0.02f, easeInOut(seg(p, 0.66f, 0.80f)))
            strands.forEach { s ->
                val flicker = 0.65f + 0.35f * sin(t * s.speed + s.phase)
                val x = cx + s.x * spread * w * (0.6f + s.depth * 0.6f)
                val sw = maxOf(1f, s.width * w * (0.6f + spread * 0.8f) * s.depth)
                val a = curtain * flicker * s.depth
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, s.color.copy(alpha = 0.9f * a), Color.White.copy(alpha = 0.55f * a), s.color.copy(alpha = 0.9f * a), Color.Transparent),
                    ),
                    topLeft = Offset(x - sw / 2f, 0f), size = Size(sw, h), blendMode = BlendMode.Plus,
                )
                if (!lite) drawRect(s.color.copy(alpha = 0.10f * a), topLeft = Offset(x - sw * 3f, 0f), size = Size(sw * 6f, h), blendMode = BlendMode.Plus)
            }
            // The bright seam where the strands gather.
            val seam = seg(p, 0.64f, 0.80f) * (1f - seg(p, 0.80f, 0.86f))
            if (seam > 0f) drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = seam), Color.Transparent), startX = cx - w * 0.08f, endX = cx + w * 0.08f),
                topLeft = Offset(cx - w * 0.08f, 0f), size = Size(w * 0.16f, h), blendMode = BlendMode.Plus,
            )
        }

        // c) The mark and the name settle out of the seam.
        val settle = easeOut(seg(p, 0.76f, 0.90f))
        val out = seg(p, 0.94f, 1f)
        if (settle > 0f) {
            val a = settle * (1f - out)
            val small = markW * 0.62f
            val u2 = small / 200f
            val top = cy - h * 0.16f - 80f * u2
            drawMawjMark(Offset(cx - small / 2f, top), u2, a)
            val px = minOf(w * 0.17f, h * 0.16f)
            drawMawjWord(
                measurer, cache, swash, cx, cy - h * 0.02f + (1f - settle) * h * 0.03f, px,
                Brush.verticalGradient(Chrome, startY = cy, endY = cy + px), a,
                latinAlpha = a * seg(p, 0.82f, 0.92f), latinSpacing = lerp(1.2f, 0.6f, settle),
                glint = seg(p, 0.84f, 0.98f),
            )
        }
    }
}

// ------------------------------------------------------------------ 2) «الإطار الذهبي»

@Composable
private fun GoldenIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }
    Canvas(modifier.fillMaxSize().background(Color(0xFF050302))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Golden.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val cy = h * 0.42f
        val fadeOut = 1f - seg(p, 0.92f, 1f)
        val push = lerp(1f, 1.07f, easeInOut(p))
        // Warm stage light.
        drawRect(Brush.radialGradient(listOf(Color(0xFF3A2410).copy(alpha = 0.9f * fadeOut), Color.Transparent), center = Offset(cx, cy), radius = maxOf(w, h) * 0.55f))

        scale(push, Offset(cx, cy)) {
            val r = minOf(w, h) * 0.26f
            val goldBrush = Brush.verticalGradient(Gold, startY = cy - r * 1.2f, endY = cy + r * 1.2f)

            // a) Rays turning slowly behind the emblem.
            val rays = easeOut(seg(p, 0.10f, 0.40f)) * fadeOut
            if (rays > 0f) {
                rotate(t * 6f, Offset(cx, cy)) {
                    val count = if (lite) 16 else 28
                    for (i in 0 until count) {
                        val ang = i * TAU / count
                        val path = Path().apply {
                            moveTo(cx + cos(ang - 0.035f) * r * 1.12f, cy + sin(ang - 0.035f) * r * 1.12f)
                            lineTo(cx + cos(ang) * r * 1.75f, cy + sin(ang) * r * 1.75f)
                            lineTo(cx + cos(ang + 0.035f) * r * 1.12f, cy + sin(ang + 0.035f) * r * 1.12f)
                            close()
                        }
                        drawPath(path, Brush.radialGradient(listOf(Color(0xFFF6DD97).copy(alpha = 0.55f * rays), Color.Transparent), center = Offset(cx, cy), radius = r * 1.75f))
                    }
                }
            }

            // b) The double ring with a film strip between, drawn in a sweep.
            val ring = easeInOut(seg(p, 0.02f, 0.30f))
            if (ring > 0f) {
                val a = fadeOut
                val sweep = 360f * ring
                listOf(r, r * 0.80f).forEach { rr ->
                    drawArc(goldBrush, -90f, sweep, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), alpha = a, style = Stroke(r * 0.035f, cap = StrokeCap.Round))
                }
                // Film band and sprocket holes.
                val band = r * 0.90f
                drawArc(Color(0xFF1A1208), -90f, sweep, false, Offset(cx - band, cy - band), Size(band * 2, band * 2), alpha = 0.9f * a, style = Stroke(r * 0.14f))
                val holes = if (lite) 24 else 36
                for (i in 0 until holes) {
                    val ang = -PI.toFloat() / 2f + i * TAU / holes
                    if (i.toFloat() / holes > ring) break
                    val hx = cx + cos(ang) * band; val hy = cy + sin(ang) * band
                    rotate(ang * 180f / PI.toFloat() + 90f, Offset(hx, hy)) {
                        drawRoundRect(goldBrush, Offset(hx - r * 0.022f, hy - r * 0.035f), Size(r * 0.044f, r * 0.07f), CornerRadius(r * 0.012f), alpha = 0.85f * a)
                    }
                }
                // Inner velvet.
                drawCircle(Brush.radialGradient(listOf(Color(0xFF2B1A0C), Color(0xFF0C0704)), center = Offset(cx, cy), radius = r * 0.78f), radius = r * 0.78f, center = Offset(cx, cy), alpha = ring * a)
            }

            // c) The gold mark inside, with a slow light sweep.
            val markIn = easeOut(seg(p, 0.28f, 0.50f)) * fadeOut
            if (markIn > 0f) {
                val markW = r * 1.05f
                val unit = markW / 200f
                val origin = Offset(cx - markW / 2f, cy - 82f * unit + (1f - markIn) * r * 0.08f)
                drawMawjMark(origin, unit, markIn, Brush.verticalGradient(Gold, startY = origin.y, endY = origin.y + 160f * unit))
                val sweepP = seg(p, 0.52f, 0.74f)
                if (sweepP in 0.001f..0.999f) {
                    val x = origin.x - markW * 0.3f + markW * 1.6f * sweepP
                    drawMawjMark(origin, unit, 0.9f * markIn, Brush.linearGradient(
                        listOf(Color.Transparent, Color.White, Color.Transparent),
                        start = Offset(x - markW * 0.14f, origin.y), end = Offset(x + markW * 0.14f, origin.y + markW * 0.8f),
                    ))
                }
            }

            // d) The banner above: «MAWJ PICTURES».
            val banner = easeOut(seg(p, 0.40f, 0.58f)) * fadeOut
            if (banner > 0f) {
                val bw = r * 1.5f; val bh = r * 0.22f
                val by = cy - r - bh * 0.4f
                val bannerPath = Path().apply {
                    moveTo(cx - bw / 2f - bh * 0.5f, by); lineTo(cx + bw / 2f + bh * 0.5f, by)
                    lineTo(cx + bw / 2f, by + bh / 2f); lineTo(cx + bw / 2f + bh * 0.5f, by + bh)
                    lineTo(cx - bw / 2f - bh * 0.5f, by + bh); lineTo(cx - bw / 2f, by + bh / 2f); close()
                }
                drawPath(bannerPath, Brush.verticalGradient(listOf(Color(0xFF7A1F1F), Color(0xFF4A0F12)), startY = by, endY = by + bh), alpha = banner)
                drawPath(bannerPath, goldBrush, alpha = banner, style = Stroke(r * 0.012f))
                val lt = cache.getOrPut("mp${bh.toInt()}") {
                    measurer.measure("MAWJ  PICTURES", TextStyle(fontSize = (bh * 0.46f / density).sp, letterSpacing = 0.32.em, fontWeight = FontWeight.Bold, color = Color(0xFFF6DD97)))
                }
                drawText(lt, topLeft = Offset(cx - lt.size.width / 2f, by + bh / 2f - lt.size.height / 2f), alpha = banner)
            }
        }

        // e) The name under the emblem, then a motto.
        val nameIn = easeOut(seg(p, 0.56f, 0.74f)) * fadeOut
        if (nameIn > 0f) {
            val px = minOf(w * 0.14f, h * 0.13f)
            val top = h * 0.42f + minOf(w, h) * 0.26f * push + h * 0.03f + (1f - nameIn) * h * 0.02f
            val hgt = drawMawjWord(
                measurer, cache, swash, cx, top, px,
                Brush.verticalGradient(Gold, startY = top + px * 0.2f, endY = top + px), nameIn,
                latinAlpha = 0f, glint = seg(p, 0.74f, 0.90f),
            )
            val motto = seg(p, 0.70f, 0.84f) * fadeOut
            if (motto > 0f) {
                val m = cache.getOrPut("motto${px.toInt()}") {
                    measurer.measure("حكاياتٌ تُروى على الشاشة", TextStyle(fontSize = (px * 0.15f / density).sp, color = Color(0xFFE9C46A), letterSpacing = 0.05.em))
                }
                drawText(m, topLeft = Offset(cx - m.size.width / 2f, top + hgt * 0.95f), alpha = motto)
            }
        }
    }
}

// ------------------------------------------------------------------ 3) «قوس الضوء»

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
private data class Spark(val u: Float, val dx: Float, val dy: Float, val life: Float)

@Composable
private fun ArcIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val stars = remember { val r = Random(3); List(if (lite) 50 else 140) { Star(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 1.4f, r.nextFloat() * TAU) } }
    val sparks = remember { val r = Random(11); List(if (lite) 30 else 80) { Spark(r.nextFloat(), r.nextFloat() * 2f - 1f, r.nextFloat() * 2f - 0.3f, 0.3f + r.nextFloat() * 0.7f) } }
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }
    Canvas(modifier.fillMaxSize().background(Color(0xFF03060F))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Arc.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val cy = h * 0.44f
        val fadeOut = 1f - seg(p, 0.92f, 1f)
        // Night sky.
        drawRect(Brush.radialGradient(listOf(Color(0xFF14306B).copy(alpha = fadeOut), Color(0xFF060C22).copy(alpha = fadeOut), Color.Transparent), center = Offset(cx, h * 0.62f), radius = maxOf(w, h) * 0.75f))
        val starA = seg(p, 0f, 0.2f) * fadeOut
        stars.forEach { s ->
            val tw = 0.45f + 0.55f * sin(t * 2.2f + s.phase)
            drawCircle(Color.White.copy(alpha = 0.7f * tw * starA), radius = s.r * density * 0.8f, center = Offset(s.x * w, s.y * h))
        }

        val markW = minOf(w, h) * 0.30f
        val unit = markW / 200f
        val origin = Offset(cx - markW / 2f, cy - 80f * unit)

        // a) The mark, softly lit.
        val markIn = easeOut(seg(p, 0.06f, 0.26f)) * fadeOut
        val flash = seg(p, 0.56f, 0.64f) * (1f - seg(p, 0.64f, 0.80f))
        if (flash > 0f) drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE0FF).copy(alpha = 0.55f * flash), Color.Transparent), center = Offset(cx, cy), radius = markW * 1.3f), radius = markW * 1.3f, center = Offset(cx, cy))
        drawMawjMark(origin, unit, markIn, Brush.verticalGradient(IceBlue, startY = origin.y, endY = origin.y + 160f * unit))

        // b) The arc of light draws over the mark from left to right, sparks trailing its head.
        val draw = easeInOut(seg(p, 0.20f, 0.60f))
        if (draw > 0f) {
            val rx = markW * 1.05f; val ry = markW * 0.72f
            val acy = cy + markW * 0.10f
            val start = 200f; val full = 140f   // degrees: from lower-left over the top to lower-right
            val sweep = full * draw
            val glowA = fadeOut * (0.7f + 0.3f * flash)
            val rect = Offset(cx - rx, acy - ry) to Size(rx * 2, ry * 2)
            val arcBrush = Brush.sweepGradient(listOf(Color(0xFF4F8DFF), Color(0xFFBFE6FF), Color.White, Color(0xFF7FB2F0), Color(0xFF4F8DFF)), center = Offset(cx, acy))
            if (!lite) drawArc(arcBrush, start, sweep, false, rect.first, rect.second, alpha = 0.18f * glowA, style = Stroke(markW * 0.09f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawArc(arcBrush, start, sweep, false, rect.first, rect.second, alpha = 0.35f * glowA, style = Stroke(markW * 0.035f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawArc(Color.White, start, sweep, false, rect.first, rect.second, alpha = 0.95f * glowA, style = Stroke(markW * 0.010f, cap = StrokeCap.Round))
            // Head of the arc.
            val headAng = (start + sweep) * PI.toFloat() / 180f
            val head = Offset(cx + cos(headAng) * rx, acy + sin(headAng) * ry)
            if (draw < 1f) {
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9CCBFF).copy(alpha = 0.5f), Color.Transparent), center = head, radius = markW * 0.16f), radius = markW * 0.16f, center = head, blendMode = BlendMode.Plus)
            }
            // Sparks shed along the drawn part.
            sparks.forEach { s ->
                if (s.u > draw) return@forEach
                val age = (draw - s.u) / s.life
                if (age > 1f) return@forEach
                val ang = (start + full * s.u) * PI.toFloat() / 180f
                val base = Offset(cx + cos(ang) * rx, acy + sin(ang) * ry)
                val pos = base + Offset(s.dx * markW * 0.12f * age, (s.dy * 0.12f + 0.25f * age) * markW * age)
                drawCircle(Color(0xFFE6F2FF).copy(alpha = (1f - age) * 0.9f * fadeOut), radius = (1.6f - age) * density, center = pos, blendMode = BlendMode.Plus)
            }
        }

        // c) The name glows in under it.
        val nameIn = easeOut(seg(p, 0.60f, 0.78f)) * fadeOut
        if (nameIn > 0f) {
            val px = minOf(w * 0.16f, h * 0.15f)
            val top = cy + markW * 0.62f + (1f - nameIn) * h * 0.025f
            drawMawjWord(
                measurer, cache, swash, cx, top, px,
                Brush.verticalGradient(IceBlue, startY = top + px * 0.2f, endY = top + px), nameIn,
                latinAlpha = nameIn * seg(p, 0.70f, 0.84f), latinColor = Color(0xFFBFD8F5),
                latinSpacing = lerp(1.1f, 0.6f, seg(p, 0.70f, 0.88f)), glint = seg(p, 0.76f, 0.92f),
            )
        }
    }
}

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
import androidx.compose.ui.graphics.drawscope.clipPath
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
 * «موج» launch openers. All carry the moon-over-the-sea mark (a golden moon with the play button
 * carved in it, over moving waves), each staged in the spirit of a big studio/streaming opener.
 */
enum class MawjIdentStyle(val title: String, val inspired: String, val durationMs: Long) {
    Moonrise("طلوع القمر", "يطلع القمر الذهبي من خلف البحر في ليل مرصّع بالنجوم، وينعكس ضوؤه على الموج، ثم يُنحت فيه زر التشغيل ويظهر «مَوج».", 5000L),
    Strands("شرائط الضوء", "مستوحاة من افتتاحية نتفليكس: يتوهّج القمر ثم ينفجر منه ستار من خيوط الضوء الذهبية والفيروزية، وتلتئم الخيوط ليعود الشعار.", 4400L),
    Searchlights("الكشّافات", "مستوحاة من افتتاحية فوكس للقرن العشرين: كشّافات ضوء تتمايل فوق البحر في ليل ضبابي، ثم تلتقي كلها على القمر فيتوهّج ويظهر الاسم.", 4600L),
    Pulse("النبض", "مستوحاة من افتتاحية HBO Max: نبضات ضوء بنفسجية وزرقاء تنتشر في الظلام، وخط وهج سينمائي يكشف الشعار.", 4200L),
    Glass("الزجاج", "مستوحاة من افتتاحية Apple TV: لوح زجاجي يدور في الضوء ويلمع، وفي داخله القمر والموج، وانعكاسه تحته.", 4400L),
    Curtains("الستائر الملوّنة", "مستوحاة من Hulu وPrime Video: ستائر بألوان البحر والقمر تنسحب تباعاً، ثم ترتسم موجة تحت الاسم.", 4000L),
}

/** Which opener plays at launch ("random" picks one each time; "off" skips the opener). */
object MawjIdentPrefs {
    private const val KEY = "mawj_ident_style"
    const val RANDOM = "random"
    const val OFF = "off"

    var choice by mutableStateOf(LuxoPrefsStorage.load(KEY) ?: MawjIdentStyle.Moonrise.name)
        private set

    fun set(value: String) { choice = value; LuxoPrefsStorage.save(KEY, value) }

    /** The opener for this launch (fixed once picked, so the hold and the animation agree). */
    val forThisLaunch: MawjIdentStyle? by lazy {
        when (choice) {
            OFF -> null
            RANDOM -> MawjIdentStyle.entries.random()
            else -> MawjIdentStyle.entries.firstOrNull { it.name == choice } ?: MawjIdentStyle.Moonrise
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
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private val TAU = (2.0 * PI).toFloat()

// The sea, close to real night water.
private val SeaNight = Color(0xFF071A2E)
private val SeaDeep = Color(0xFF0B2D4A)
private val SeaMid = Color(0xFF145B7A)
private val SeaLight = Color(0xFF2E9AB0)
private val SeaFoam = Color(0xFFD8F3F5)
// The moon.
private val MoonLight = Color(0xFFFFF6DC)
private val MoonMid = Color(0xFFEDCB78)
private val MoonDark = Color(0xFFC9963A)
private val GoldText = listOf(Color(0xFFFFF6DC), Color(0xFFEDCB78), Color(0xFFC9963A))

/** A sine wave band across [x0]..[x1], closed down to [bottom]. */
private fun waveBand(x0: Float, x1: Float, base: Float, amp: Float, length: Float, phase: Float, bottom: Float, steps: Int): Path = Path().apply {
    for (i in 0..steps) {
        val x = x0 + (x1 - x0) * i / steps
        val y = base + amp * sin(x / length * TAU + phase) + amp * 0.35f * sin(x / length * TAU * 2.3f - phase * 1.4f)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    lineTo(x1, bottom); lineTo(x0, bottom); close()
}

private fun waveLine(x0: Float, x1: Float, base: Float, amp: Float, length: Float, phase: Float, steps: Int): Path = Path().apply {
    for (i in 0..steps) {
        val x = x0 + (x1 - x0) * i / steps
        val y = base + amp * sin(x / length * TAU + phase) + amp * 0.35f * sin(x / length * TAU * 2.3f - phase * 1.4f)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/**
 * The mark: a golden moon of radius [r] at [c] with the play button carved in it and three moving
 * wave bands inside its lower part. [carve] fades the play button in, [sea] the waves.
 */
private fun DrawScope.drawMoonMark(c: Offset, r: Float, alpha: Float, t: Float, carve: Float = 1f, sea: Float = 1f, glow: Float = 1f, lite: Boolean = false) {
    if (alpha <= 0f || r <= 0f) return
    val u = r / 92f
    if (glow > 0f) drawCircle(
        Brush.radialGradient(listOf(MoonMid.copy(alpha = 0.32f * alpha * glow), Color.Transparent), center = c, radius = r * 1.7f),
        radius = r * 1.7f, center = c,
    )
    drawCircle(
        Brush.radialGradient(listOf(MoonLight, MoonMid, MoonDark), center = c + Offset(-0.25f * r, -0.35f * r), radius = r * 1.7f),
        radius = r, center = c, alpha = alpha,
    )
    if (carve > 0f) {
        val tri = Path().apply {
            moveTo(c.x - 29f * u, c.y - 48f * u); lineTo(c.x + 31f * u, c.y - 13f * u); lineTo(c.x - 29f * u, c.y + 21f * u); close()
        }
        drawPath(tri, SeaNight, alpha = alpha * carve)
    }
    if (sea > 0f) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        val steps = if (lite) 24 else 48
        val x0 = c.x - r; val x1 = c.x + r; val bottom = c.y + r
        val len = r * 1.6f
        clipPath(disc) {
            drawPath(waveBand(x0, x1, c.y + 40f * u, 6f * u, len, t * 0.9f, bottom, steps),
                Brush.verticalGradient(listOf(SeaDeep, SeaNight), startY = c.y + 34f * u, endY = bottom), alpha = alpha * sea)
            drawPath(waveBand(x0, x1, c.y + 60f * u, 5f * u, len * 0.8f, t * 1.4f + 1f, bottom, steps),
                Brush.horizontalGradient(listOf(SeaMid, SeaLight, SeaMid), startX = x0, endX = x1), alpha = alpha * sea)
            drawPath(waveLine(x0, x1, c.y + 60f * u, 5f * u, len * 0.8f, t * 1.4f + 1f, steps),
                SeaFoam, alpha = 0.85f * alpha * sea, style = Stroke(2.4f * u))
            drawPath(waveBand(x0, x1, c.y + 80f * u, 4f * u, len * 0.7f, t * 2f + 2f, bottom, steps), SeaNight, alpha = alpha * sea)
        }
    }
}

/** «مَوج» with «MAWJ» under it, centred on [cx], top at [top]. Returns the bottom of the block. */
private fun DrawScope.drawWordBlock(
    measurer: TextMeasurer,
    cache: MutableMap<String, TextLayoutResult>,
    family: FontFamily,
    cx: Float,
    top: Float,
    px: Float,
    alpha: Float,
    colors: List<Color> = GoldText,
    latinAlpha: Float = alpha,
    latinColor: Color = Color(0xFFE9C46A),
    latinSpacing: Float = 0.6f,
    glint: Float = -1f,
): Float {
    val word = cache.getOrPut("w${px.toInt()}_${family.hashCode()}") {
        measurer.measure("مَوج", TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = (px / density).sp))
    }
    val bottomOfWord = top + word.size.height * 1.0f
    if (alpha > 0f) {
        val left = cx - word.size.width / 2f
        drawText(word, brush = Brush.verticalGradient(colors, startY = top + word.size.height * 0.2f, endY = top + word.size.height * 0.85f), topLeft = Offset(left, top), alpha = alpha)
        if (glint in 0.001f..0.999f) {
            val gx = left - word.size.width * 0.3f + word.size.width * 1.6f * glint
            drawText(
                word,
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), startX = gx - px * 0.2f, endX = gx + px * 0.2f),
                topLeft = Offset(left, top), alpha = 0.8f * alpha,
            )
        }
    }
    if (latinAlpha <= 0f) return bottomOfWord
    val size = px * 0.13f
    val key = "l${size.toInt()}_${(latinSpacing * 20).toInt()}"
    val l = cache.getOrPut(key) {
        measurer.measure("MAWJ", TextStyle(fontSize = (size / density).sp, letterSpacing = latinSpacing.em, fontWeight = FontWeight.SemiBold, color = latinColor))
    }
    drawText(l, topLeft = Offset(cx - l.size.width / 2f + size * latinSpacing / 2f, bottomOfWord + size * 0.25f), alpha = latinAlpha)
    return bottomOfWord + size * 0.25f + l.size.height
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

private class IdentText(val measurer: TextMeasurer, val family: FontFamily, val cache: MutableMap<String, TextLayoutResult>)

@Composable
private fun rememberIdentText(): IdentText {
    val measurer = rememberTextMeasurer()
    val swash = FontFamily(Font(Res.font.mawj_swash))
    val cache = remember { mutableMapOf<String, TextLayoutResult>() }
    return IdentText(measurer, swash, cache)
}

/** Mark above the word, the pair centred on the screen. */
private fun DrawScope.drawLockup(text: IdentText, t: Float, markAlpha: Float, wordAlpha: Float, latinAlpha: Float, glint: Float, rise: Float, lite: Boolean, carve: Float = 1f, scaleK: Float = 1f) {
    val w = size.width; val h = size.height
    val r = minOf(w, h) * 0.13f * scaleK
    val px = minOf(w * 0.15f, h * 0.15f) * scaleK
    val blockH = r * 2f + r * 0.35f + px * 1.75f
    val top = h / 2f - blockH / 2f
    val c = Offset(w / 2f, top + r)
    drawMoonMark(c, r, markAlpha, t, carve = carve, lite = lite)
    drawWordBlock(text.measurer, text.cache, text.family, w / 2f, c.y + r * 1.15f + (1f - rise) * h * 0.03f, px, wordAlpha, latinAlpha = latinAlpha, glint = glint)
}

// ------------------------------------------------------------------ entry

@Composable
fun MawjIdent(style: MawjIdentStyle, modifier: Modifier = Modifier, replayKey: Int = 0) {
    when (style) {
        MawjIdentStyle.Moonrise -> MoonriseIdent(modifier, replayKey)
        MawjIdentStyle.Strands -> StrandsIdent(modifier, replayKey)
        MawjIdentStyle.Searchlights -> SearchlightsIdent(modifier, replayKey)
        MawjIdentStyle.Pulse -> PulseIdent(modifier, replayKey)
        MawjIdentStyle.Glass -> GlassIdent(modifier, replayKey)
        MawjIdentStyle.Curtains -> CurtainsIdent(modifier, replayKey)
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

private fun DrawScope.drawStars(stars: List<Star>, t: Float, alpha: Float, maxY: Float = 1f) {
    if (alpha <= 0f) return
    stars.forEach { s ->
        if (s.y > maxY) return@forEach
        val tw = 0.45f + 0.55f * sin(t * 2.2f + s.phase)
        drawCircle(Color.White.copy(alpha = 0.75f * tw * alpha), radius = s.r * density * 0.8f, center = Offset(s.x * size.width, s.y * size.height))
    }
}

// ------------------------------------------------------------------ 1) «طلوع القمر»

@Composable
private fun MoonriseIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    val stars = remember { val r = Random(5); List(if (lite) 50 else 130) { Star(r.nextFloat(), r.nextFloat() * 0.62f, 0.4f + r.nextFloat() * 1.3f, r.nextFloat() * TAU) } }
    Canvas(modifier.fillMaxSize().background(Color(0xFF02050C))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Moonrise.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val out = 1f - seg(p, 0.93f, 1f)
        val horizon = h * 0.64f
        // Scene: sky, stars, the sea; it gives way to the lockup.
        val scene = (1f - seg(p, 0.60f, 0.74f)) * out
        if (scene > 0f) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF040A18), Color(0xFF0E2446), Color(0xFF1B3B62)), startY = 0f, endY = horizon), size = Size(w, horizon), alpha = scene)
            drawStars(stars, t, seg(p, 0f, 0.2f) * scene)
            // Moon rising from behind the sea.
            val rise = easeOut(seg(p, 0.05f, 0.55f))
            val r = minOf(w, h) * 0.13f
            // It ends exactly where the lockup puts the mark.
            val px = minOf(w * 0.15f, h * 0.15f)
            val finalY = h / 2f - (r * 2f + r * 0.35f + px * 1.75f) / 2f + r
            val c = Offset(cx, lerp(horizon + r * 1.1f, finalY, rise))
            clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, horizon + 1f)) }) {
                drawMoonMark(c, r, scene, t, carve = 0f, sea = 0f, glow = 1.4f)
            }
            // Its reflection on the water, broken by the waves.
            val steps = if (lite) 40 else 90
            drawRect(Brush.verticalGradient(listOf(SeaDeep, SeaNight), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon), alpha = scene)
            val refl = rise * scene
            // A shimmering column of broken light strokes, like moonlight on real water.
            val bands = if (lite) 12 else 26
            for (i in 0 until bands) {
                val y = horizon + (h - horizon) * (i + 0.5f) / bands
                val k = 1f - i / bands.toFloat()
                val half = r * (0.35f + 0.9f * (1f - k)) * (0.6f + 0.4f * sin(t * 3.1f + i * 1.7f))
                val shift = r * 0.12f * sin(t * 1.9f + i * 2.3f)
                drawLine(MoonLight.copy(alpha = 0.6f * k * refl), Offset(cx - half + shift, y), Offset(cx + half * 0.55f + shift, y), strokeWidth = (h - horizon) / bands * 0.22f, cap = StrokeCap.Round)
                drawLine(MoonMid.copy(alpha = 0.45f * k * refl), Offset(cx + half * 0.7f + shift, y), Offset(cx + half + shift, y), strokeWidth = (h - horizon) / bands * 0.22f, cap = StrokeCap.Round)
            }
            for (layer in 0..2) {
                val base = horizon + (h - horizon) * (0.12f + layer * 0.3f)
                drawPath(waveLine(0f, w, base, h * 0.006f * (layer + 1), w * (0.35f - layer * 0.06f), t * (0.8f + layer * 0.5f) + layer, steps),
                    SeaLight, alpha = 0.35f * scene, style = Stroke(1.6f * density))
            }
        }
        // The lockup: the moon with the carved play button and its own sea, and the name.
        val lock = seg(p, 0.60f, 0.74f) * out
        if (lock > 0f) {
            drawStars(stars, t, 0.6f * lock)
            drawLockup(
                text, t, markAlpha = lock, wordAlpha = easeOut(seg(p, 0.66f, 0.80f)) * out, latinAlpha = seg(p, 0.74f, 0.86f) * out,
                glint = seg(p, 0.78f, 0.94f), rise = easeOut(seg(p, 0.66f, 0.80f)), lite = lite, carve = easeOut(seg(p, 0.62f, 0.74f)),
            )
        }
    }
}

// ------------------------------------------------------------------ 2) «شرائط الضوء»

private data class Strand(val x: Float, val width: Float, val color: Color, val phase: Float, val speed: Float, val depth: Float)

private val StrandPalette = listOf(
    Color(0xFFEDCB78), Color(0xFFFFF6DC), Color(0xFF2E9AB0), Color(0xFF55D6E6),
    Color(0xFF145B7A), Color(0xFF3D7BFF), Color(0xFFC9963A), Color(0xFFD8F3F5),
)

@Composable
private fun StrandsIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    val strands = remember {
        val r = Random(7)
        List(if (lite) 40 else 96) {
            Strand(
                x = (r.nextFloat() * 2f - 1f).let { it * abs(it) * 0.6f + it * 0.4f },
                width = 0.002f + r.nextFloat() * r.nextFloat() * 0.016f,
                color = StrandPalette[r.nextInt(StrandPalette.size)],
                phase = r.nextFloat() * TAU, speed = 2f + r.nextFloat() * 6f, depth = 0.4f + r.nextFloat() * 0.6f,
            )
        }
    }
    Canvas(modifier.fillMaxSize().background(Color.Black)) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Strands.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        val out = 1f - seg(p, 0.94f, 1f)
        // a) The moon glows in, swells and dissolves into light (no giant zoom).
        val intro = easeOut(seg(p, 0.02f, 0.18f)) * (1f - seg(p, 0.30f, 0.40f))
        if (intro > 0f) {
            val r = minOf(w, h) * 0.13f * lerp(1f, 1.25f, seg(p, 0.18f, 0.40f))
            drawMoonMark(Offset(cx, cy), r, intro, t, lite = lite, glow = 1f + seg(p, 0.2f, 0.4f) * 2f)
        }
        // b) A curtain of light strands bursts out and streams past, then gathers into a seam.
        val curtain = (seg(p, 0.28f, 0.40f) - seg(p, 0.70f, 0.80f)).coerceIn(0f, 1f)
        if (curtain > 0f) {
            val spread = lerp(0.04f, 1.2f, easeOut(seg(p, 0.28f, 0.60f))) * lerp(1f, 0.02f, easeInOut(seg(p, 0.64f, 0.80f)))
            strands.forEach { s ->
                val flicker = 0.65f + 0.35f * sin(t * s.speed + s.phase)
                val x = cx + s.x * spread * w * (0.6f + s.depth * 0.6f)
                val sw = maxOf(1f, s.width * w * (0.6f + spread * 0.8f) * s.depth)
                val a = curtain * flicker * s.depth
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, s.color.copy(alpha = 0.9f * a), Color.White.copy(alpha = 0.5f * a), s.color.copy(alpha = 0.9f * a), Color.Transparent)),
                    topLeft = Offset(x - sw / 2f, 0f), size = Size(sw, h), blendMode = BlendMode.Plus,
                )
                if (!lite) drawRect(s.color.copy(alpha = 0.10f * a), topLeft = Offset(x - sw * 3f, 0f), size = Size(sw * 6f, h), blendMode = BlendMode.Plus)
            }
            val seam = seg(p, 0.64f, 0.78f) * (1f - seg(p, 0.78f, 0.84f))
            if (seam > 0f) drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, MoonLight.copy(alpha = seam), Color.Transparent), startX = cx - w * 0.06f, endX = cx + w * 0.06f),
                topLeft = Offset(cx - w * 0.06f, 0f), size = Size(w * 0.12f, h), blendMode = BlendMode.Plus,
            )
        }
        // c) The lockup settles once the seam has gone.
        val settle = easeOut(seg(p, 0.80f, 0.92f))
        if (settle > 0f) {
            drawLockup(text, t, markAlpha = settle * out, wordAlpha = settle * out, latinAlpha = seg(p, 0.84f, 0.94f) * out, glint = seg(p, 0.84f, 0.99f), rise = settle, lite = lite)
        }
    }
}

// ------------------------------------------------------------------ 3) «الكشّافات»

@Composable
private fun SearchlightsIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    val stars = remember { val r = Random(9); List(if (lite) 40 else 110) { Star(r.nextFloat(), r.nextFloat() * 0.6f, 0.4f + r.nextFloat() * 1.2f, r.nextFloat() * TAU) } }
    Canvas(modifier.fillMaxSize().background(Color(0xFF02050C))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Searchlights.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val out = 1f - seg(p, 0.93f, 1f)
        val horizon = h * 0.80f
        // Night sky, a little fog and the sea.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF030814), Color(0xFF0B1C36), Color(0xFF16304F)), startY = 0f, endY = horizon), size = Size(w, horizon), alpha = out)
        drawStars(stars, t, seg(p, 0f, 0.2f) * out * 0.8f)
        // Where the mark sits (same as the lockup).
        val r = minOf(w, h) * 0.13f
        val px = minOf(w * 0.15f, h * 0.15f)
        val c = Offset(cx, h / 2f - (r * 2f + r * 0.35f + px * 1.75f) / 2f + r)
        // Searchlights from the shore sway, then all swing onto the moon.
        val meet = easeInOut(seg(p, 0.42f, 0.62f))
        val beams = if (lite) 4 else 6
        val beamA = seg(p, 0.04f, 0.18f) * (1f - 0.65f * seg(p, 0.70f, 0.86f)) * out
        for (i in 0 until beams) {
            val side = if (i % 2 == 0) -1f else 1f
            val k = i / 2
            val base = Offset(cx + side * w * (0.12f + 0.16f * k), horizon)
            val sway = sin(t * (0.9f + 0.25f * k) + i * 1.3f) * 0.42f
            val free = -PI.toFloat() / 2f + side * (0.18f + 0.12f * k) + sway
            val toMark = kotlin.math.atan2(c.y - base.y, c.x - base.x)
            val ang = lerp(free, toMark, meet)
            val len = h * 1.4f
            val spread = lerp(0.065f, 0.035f, meet)
            val tip1 = base + Offset(cos(ang - spread) * len, sin(ang - spread) * len)
            val tip2 = base + Offset(cos(ang + spread) * len, sin(ang + spread) * len)
            val beam = Path().apply { moveTo(base.x, base.y); lineTo(tip1.x, tip1.y); lineTo(tip2.x, tip2.y); close() }
            drawPath(beam, Brush.radialGradient(listOf(Color(0xFFFFF3D6).copy(alpha = 0.55f * beamA), Color(0xFFBFD8F5).copy(alpha = 0.18f * beamA), Color.Transparent), center = base, radius = len), blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFF6DC).copy(alpha = 0.9f * beamA), radius = 3.5f * density, center = base)
        }
        // Fog bank over the horizon.
        if (!lite) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF8FA9C8).copy(alpha = 0.10f * out), Color.Transparent), startY = horizon - h * 0.12f, endY = horizon + h * 0.04f), topLeft = Offset(0f, horizon - h * 0.12f), size = Size(w, h * 0.16f))
        drawRect(Brush.verticalGradient(listOf(SeaDeep, SeaNight), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon), alpha = out)
        for (layer in 0..2) {
            val base = horizon + (h - horizon) * (0.18f + layer * 0.3f)
            drawPath(waveLine(0f, w, base, h * 0.005f * (layer + 1), w * (0.32f - layer * 0.05f), t * (0.9f + layer * 0.5f) + layer, if (lite) 40 else 90),
                SeaLight, alpha = 0.35f * out, style = Stroke(1.5f * density))
        }
        // Where the beams meet, the moon lights up and the name follows.
        val lit = easeOut(seg(p, 0.56f, 0.70f))
        if (lit > 0f) {
            val flash = seg(p, 0.58f, 0.64f) * (1f - seg(p, 0.64f, 0.82f))
            if (flash > 0f) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6DC).copy(alpha = 0.5f * flash), Color.Transparent), center = c, radius = r * 3f), radius = r * 3f, center = c, blendMode = BlendMode.Plus)
            drawMoonMark(c, r * lerp(0.85f, 1f, lit), lit * out, t, lite = lite)
            drawWordBlock(text.measurer, text.cache, text.family, cx, c.y + r * 1.15f + (1f - easeOut(seg(p, 0.66f, 0.80f))) * h * 0.025f, px,
                easeOut(seg(p, 0.66f, 0.80f)) * out, latinAlpha = seg(p, 0.74f, 0.86f) * out, glint = seg(p, 0.78f, 0.94f))
        }
    }
}

// ------------------------------------------------------------------ 4) «النبض»

@Composable
private fun PulseIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    Canvas(modifier.fillMaxSize().background(Color.Black)) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Pulse.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f; val cy = h / 2f
        val out = 1f - seg(p, 0.93f, 1f)
        val haze = (seg(p, 0f, 0.25f)) * out
        // A violet-blue haze that breathes.
        val breath = 0.75f + 0.25f * sin(t * 3.2f)
        drawRect(Brush.radialGradient(listOf(Color(0xFF5B2BD6).copy(alpha = 0.55f * haze * breath), Color(0xFF1A2A8A).copy(alpha = 0.35f * haze), Color.Transparent), center = Offset(cx, cy), radius = maxOf(w, h) * 0.6f))
        // Sonar rings.
        val rings = if (lite) 3 else 5
        for (i in 0 until rings) {
            val k = ((t * 0.55f + i / rings.toFloat()) % 1f)
            val a = (1f - k) * seg(p, 0.05f, 0.25f) * (1f - seg(p, 0.62f, 0.75f)) * out
            if (a > 0f) drawCircle(Color(0xFFB8A6FF).copy(alpha = 0.45f * a), radius = minOf(w, h) * (0.08f + 0.6f * k), center = Offset(cx, cy), style = Stroke((2.5f - 1.5f * k) * density))
        }
        // The anamorphic flare that reveals the mark.
        val flare = seg(p, 0.34f, 0.50f) * (1f - seg(p, 0.56f, 0.72f))
        if (flare > 0f) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF8FB8FF).copy(alpha = 0.9f * flare), Color.White.copy(alpha = flare), Color(0xFF8FB8FF).copy(alpha = 0.9f * flare), Color.Transparent)),
                topLeft = Offset(0f, cy - h * 0.006f), size = Size(w, h * 0.012f), blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = flare), Color(0xFF7C6CFF).copy(alpha = 0.4f * flare), Color.Transparent), center = Offset(cx, cy), radius = h * 0.18f), radius = h * 0.18f, center = Offset(cx, cy), blendMode = BlendMode.Plus)
        }
        val reveal = easeOut(seg(p, 0.44f, 0.62f))
        if (reveal > 0f) {
            drawLockup(text, t, markAlpha = reveal * out, wordAlpha = easeOut(seg(p, 0.56f, 0.72f)) * out, latinAlpha = seg(p, 0.62f, 0.80f) * out,
                glint = seg(p, 0.74f, 0.92f), rise = easeOut(seg(p, 0.56f, 0.72f)), lite = lite, scaleK = lerp(0.9f, 1f, reveal))
        }
    }
}

// ------------------------------------------------------------------ 5) «الزجاج»

@Composable
private fun GlassIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    Canvas(modifier.fillMaxSize().background(Color(0xFF05070D))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Glass.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val out = 1f - seg(p, 0.93f, 1f)
        drawRect(Brush.radialGradient(listOf(Color(0xFF16213A).copy(alpha = out), Color.Transparent), center = Offset(cx, h * 0.4f), radius = maxOf(w, h) * 0.6f))
        val tile = minOf(w, h) * 0.34f
        val px = minOf(w * 0.13f, h * 0.13f)
        val blockH = tile + tile * 0.12f + px * 1.75f
        val top = h / 2f - blockH / 2f
        val c = Offset(cx, top + tile / 2f)
        // The glass tile turns towards the viewer.
        val turn = easeOut(seg(p, 0.04f, 0.50f))
        val angle = (1f - turn) * 80f * PI.toFloat() / 180f
        val sx = cos(angle).coerceAtLeast(0.04f)
        val enter = seg(p, 0.02f, 0.14f) * out
        fun drawTile(alpha: Float) {
            scale(sx, 1f, c) {
                val tl = Offset(c.x - tile / 2f, c.y - tile / 2f)
                drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.10f)), start = tl, end = tl + Offset(tile, tile)),
                    topLeft = tl, size = Size(tile, tile), cornerRadius = CornerRadius(tile * 0.24f), alpha = alpha)
                drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = tl, size = Size(tile, tile), cornerRadius = CornerRadius(tile * 0.24f), alpha = alpha, style = Stroke(1.4f * density))
                drawMoonMark(c, tile * 0.30f, alpha, t, lite = lite, glow = 0.6f)
                // Specular sweep.
                val sweep = seg(p, 0.40f, 0.70f)
                if (sweep in 0.001f..0.999f) {
                    val x = tl.x - tile * 0.4f + tile * 1.8f * sweep
                    clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + tile, tl.y + tile, CornerRadius(tile * 0.24f))) }) {
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f * alpha), Color.Transparent), start = Offset(x - tile * 0.2f, tl.y), end = Offset(x + tile * 0.2f, tl.y + tile)),
                            topLeft = tl, size = Size(tile, tile))
                    }
                }
            }
        }
        if (enter > 0f) {
            drawTile(enter)
            // Reflection on the floor.
            if (!lite) {
                val floor = c.y + tile / 2f + tile * 0.04f
                clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, floor, w, floor + tile * 0.45f)) }) {
                    scale(1f, -1f, Offset(cx, floor)) { drawTile(enter * 0.16f) }
                }
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF05070D)), startY = floor, endY = floor + tile * 0.45f), topLeft = Offset(0f, floor), size = Size(w, tile * 0.45f))
            }
        }
        val nameIn = easeOut(seg(p, 0.58f, 0.76f)) * out
        if (nameIn > 0f) {
            drawWordBlock(text.measurer, text.cache, text.family, cx, c.y + tile / 2f + tile * 0.14f + (1f - nameIn) * h * 0.02f, px, nameIn,
                colors = listOf(Color.White, Color(0xFFDDE3EC), Color(0xFFA9B3C2)), latinAlpha = seg(p, 0.68f, 0.84f) * out, latinColor = Color(0xFFC9CED6), glint = seg(p, 0.76f, 0.94f))
        }
    }
}

// ------------------------------------------------------------------ 6) «الستائر الملوّنة»

private val CurtainColors = listOf(SeaNight, SeaMid, SeaLight, MoonMid, SeaDeep, MoonDark, SeaMid)

@Composable
private fun CurtainsIdent(modifier: Modifier, replayKey: Int) {
    val time = rememberElapsed(replayKey)
    val lite = remember { luxoLiteMode() }
    val text = rememberIdentText()
    Canvas(modifier.fillMaxSize().background(Color(0xFF04060B))) {
        val t = time()
        val p = clamp01(t / (MawjIdentStyle.Curtains.durationMs / 1000f))
        val w = size.width; val h = size.height; val cx = w / 2f
        val out = 1f - seg(p, 0.93f, 1f)
        // The lockup underneath, revealed as the curtains lift.
        val r = minOf(w, h) * 0.13f
        val px = minOf(w * 0.15f, h * 0.15f)
        val blockH = r * 2f + r * 0.35f + px * 1.75f
        val c = Offset(cx, h / 2f - blockH / 2f + r)
        drawMoonMark(c, r, out * seg(p, 0.30f, 0.45f).coerceAtLeast(0.001f), t, lite = lite)
        val bottom = drawWordBlock(text.measurer, text.cache, text.family, cx, c.y + r * 1.15f, px, easeOut(seg(p, 0.42f, 0.60f)) * out,
            latinAlpha = 0f, glint = seg(p, 0.70f, 0.88f))
        // A wave drawn under the name, left to right.
        val draw = easeInOut(seg(p, 0.56f, 0.80f))
        if (draw > 0f) {
            val x0 = cx - px * 1.1f; val x1 = x0 + px * 2.2f * draw
            drawPath(waveLine(x0, x1, bottom + px * 0.18f, px * 0.05f, px * 0.9f, 0f, 40), Brush.horizontalGradient(listOf(SeaLight, MoonMid), startX = cx - px * 1.1f, endX = cx + px * 1.1f),
                alpha = out, style = Stroke(px * 0.045f, cap = StrokeCap.Round))
        }
        // Curtains in the sea and moon colours lift one after the other.
        val n = CurtainColors.size
        val pw = w / n
        for (i in 0 until n) {
            val k = easeInOut(seg(p, 0.06f + i * 0.045f, 0.34f + i * 0.045f))
            if (k >= 1f) continue
            val y = -h * k
            drawRect(CurtainColors[i], topLeft = Offset(i * pw - 1f, y), size = Size(pw + 2f, h))
            if (!lite) drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)), topLeft = Offset(i * pw - 1f, y), size = Size(pw + 2f, h * 0.3f))
        }
    }
}

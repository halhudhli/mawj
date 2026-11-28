package com.nuvio.app.features.luxo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.mawj_swash
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Mawj's moon gold and the dark of its unlit side. */
val MawjMoonLit = Color(0xFFF1DDA6)
val MawjMoonDark = Color(0xFF26323F)

// ---------------- «التقييم بأطوار القمر» ----------------

/**
 * The rating as the moon: a thin crescent for a weak rating that fills to a full moon at 9 and above.
 * [rating] is out of 10.
 */
@Composable
fun MawjMoonRating(rating: Double, size: Dp, modifier: Modifier = Modifier, lit: Color = MawjMoonLit, dark: Color = MawjMoonDark) {
    Canvas(modifier.size(size)) { drawMoonPhase(((rating - 5.0) / 4.0).toFloat().coerceIn(0.08f, 1f), lit, dark) }
}

/** Draws the moon lit by [fill] (0 = new moon, 1 = full moon), lit from the right as in Arabic reading. */
fun DrawScope.drawMoonPhase(fill: Float, lit: Color, dark: Color) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(dark, r, c)
    if (fill >= 0.999f) { drawCircle(lit, r, c); return }
    val disc = Path().apply { addOval(Rect(c, r)) }
    // The shadow: a disc of the same size slid to the left; the further it slides, the fuller the moon.
    val shift = 2f * r * fill
    val shadow = Path().apply { addOval(Rect(Offset(c.x - shift, c.y), r)) }
    val litPart = Path().apply { op(disc, shadow, PathOperation.Difference) }
    drawPath(litPart, lit)
}

// ---------------- «التحميل بأطوار القمر» ----------------

/** One frame of the moon loader: [t] runs 0..1 from new moon to full and back. */
fun DrawScope.drawMoonLoader(t: Float, lit: Color, dark: Color) {
    val k = if (t < 0.5f) t * 2f else (1f - t) * 2f
    val r = size.minDimension * 0.36f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(lit.copy(alpha = 0.10f), r * 1.35f, c)
    drawCircle(dark, r, c)
    val disc = Path().apply { addOval(Rect(c, r)) }
    val shadow = Path().apply { addOval(Rect(Offset(c.x - 2f * r * k.coerceIn(0.06f, 1f), c.y), r)) }
    drawPath(Path().apply { op(disc, shadow, PathOperation.Difference) }, lit)
}

// ---------------- «خط الأفق» ----------------

/** A thin gold horizon that section titles stand on; it fades towards the end of the line. */
@Composable
fun MawjHorizonLine(color: Color, modifier: Modifier = Modifier) {
    val run by rememberInfiniteTransition(label = "horizon").animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "run")
    Box(
        modifier.height(1.5.dp).drawWithCache {
            val brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f)))
            onDrawBehind {
                drawRect(brush)
                // A short glint travels along the horizon, like moonlight on the water.
                val x = size.width * run
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent), startX = x - 60f, endX = x + 60f), Offset(x - 60f, 0f), Size(120f, size.height))
            }
        },
    )
}

// ---------------- «ختم موج» ----------------

/** The gold seal on a finished title: a round stamp with the crescent, tilted like a pressed seal. */
@Composable
fun MawjFinishedStamp(size: Dp, gold: Color, ink: Color, modifier: Modifier = Modifier) {
    val press = remember { Animatable(1.5f) }
    LaunchedEffect(Unit) { press.animateTo(1f, tween(420)) }
    Canvas(modifier.size(size)) {
        val k = press.value
        scale(k, k) { rotate(-14f - (k - 1f) * 30f) {
            val r = this.size.minDimension / 2f
            val c = center
            drawCircle(gold, r, c)
            drawCircle(ink.copy(alpha = 0.35f), r * 0.84f, c, style = Stroke(width = r * 0.06f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.12f))))
            // The crescent in the middle.
            val moon = Path().apply { addOval(Rect(c, r * 0.46f)) }
            val cut = Path().apply { addOval(Rect(Offset(c.x - r * 0.2f, c.y - r * 0.06f), r * 0.4f)) }
            drawPath(Path().apply { op(moon, cut, PathOperation.Difference) }, ink)
        } }
    }
}

// ---------------- «ملمس الفيلم» ----------------

/** A small tile of film grain, made once; drawn repeated over the background at a very low alpha. */
private val grainTile: ImageBitmap by lazy {
    val n = 160
    val bmp = ImageBitmap(n, n)
    val canvas = GCanvas(bmp)
    val paint = Paint()
    val rnd = Random(7)
    repeat(n * n / 3) {
        val v = rnd.nextFloat()
        paint.color = if (v > 0.5f) Color.White.copy(alpha = rnd.nextFloat() * 0.9f) else Color.Black.copy(alpha = rnd.nextFloat() * 0.9f)
        canvas.drawRect(Rect(Offset(rnd.nextInt(n).toFloat(), rnd.nextInt(n).toFloat()), Size(1f, 1f)), paint)
    }
    bmp
}

/** Still film grain over everything behind it (no motion, so it costs nothing after the first frame). */
fun Modifier.mawjFilmGrain(alpha: Float = 0.06f, frame: () -> Float = { 0f }): Modifier = drawWithCache {
    val brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        // The grain jumps to a new place a few times a second, like film running through a projector.
        val step = (frame() * 240f).toInt()
        val dx = ((step * 37) % 160).toFloat()
        val dy = ((step * 61) % 160).toFloat()
        translate(-dx, -dy) { drawRect(brush, topLeft = Offset.Zero, size = Size(size.width + 160f, size.height + 160f), alpha = alpha) }
    }
}

// ---------------- «تذكرة» ----------------

/** A cinema ticket: rounded, with a half-circle bite on both long sides at [tearAt] (0..1 across the width). */
class MawjTicketShape(private val tearAt: Float = 0.7f, private val bite: Dp = 9.dp, private val corner: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val b = with(density) { bite.toPx() }
        val cr = with(density) { corner.toPx() }
        val x = size.width * (if (layoutDirection == LayoutDirection.Rtl) 1f - tearAt else tearAt)
        val body = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, size), androidx.compose.ui.geometry.CornerRadius(cr)))
        }
        val bites = Path().apply {
            addOval(Rect(Offset(x, 0f), b))
            addOval(Rect(Offset(x, size.height), b))
        }
        return Outline.Generic(Path().apply { op(body, bites, PathOperation.Difference) })
    }
}

/** The dotted tear line of the ticket. */
fun DrawScope.drawTicketTear(tearAt: Float, color: Color, rtl: Boolean) {
    val x = size.width * (if (rtl) 1f - tearAt else tearAt)
    drawLine(color, Offset(x, 12f), Offset(x, size.height - 12f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f)), cap = StrokeCap.Round)
}

// ---------------- «الجزر» كخريطة بحرية ----------------

/** An island outline: a soft irregular blob, different for every [seed], fitted to the card. */
class MawjIslandShape(private val seed: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rnd = Random(seed)
        val n = 9
        val c = Offset(size.width / 2f, size.height / 2f)
        val pts = (0 until n).map { i ->
            val a = i / n.toFloat() * 2f * PI.toFloat()
            val k = 0.86f + rnd.nextFloat() * 0.14f
            Offset(c.x + cos(a) * size.width / 2f * k, c.y + sin(a) * size.height / 2f * k)
        }
        val p = Path()
        // A closed smooth curve through the points (mid-point quadratic smoothing).
        val mid = { a: Offset, b: Offset -> Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f) }
        p.moveTo(mid(pts.last(), pts.first()).x, mid(pts.last(), pts.first()).y)
        pts.forEachIndexed { i, pt ->
            val next = pts[(i + 1) % n]
            val m = mid(pt, next)
            p.quadraticTo(pt.x, pt.y, m.x, m.y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** A chart's compass rose, in gold lines. */
@Composable
fun MawjCompassRose(size: Dp, color: Color, modifier: Modifier = Modifier) {
    val sway by rememberInfiniteTransition(label = "compass").animateFloat(-9f, 9f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "sway")
    Canvas(modifier.size(size)) { rotate(sway) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(color.copy(alpha = 0.5f), r * 0.92f, c, style = Stroke(1.5f))
        drawCircle(color.copy(alpha = 0.3f), r * 0.62f, c, style = Stroke(1f))
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f - PI.toFloat() / 2f
            val long = i % 2 == 0
            val tip = Offset(c.x + cos(a) * r * (if (long) 0.98f else 0.6f), c.y + sin(a) * r * (if (long) 0.98f else 0.6f))
            val side = r * 0.12f
            val l = Offset(c.x + cos(a - PI.toFloat() / 2f) * side, c.y + sin(a - PI.toFloat() / 2f) * side)
            val rr = Offset(c.x + cos(a + PI.toFloat() / 2f) * side, c.y + sin(a + PI.toFloat() / 2f) * side)
            val p = Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(c.x, c.y); close() }
            val q = Path().apply { moveTo(tip.x, tip.y); lineTo(rr.x, rr.y); lineTo(c.x, c.y); close() }
            drawPath(p, color.copy(alpha = if (long) 0.95f else 0.6f))
            drawPath(q, color.copy(alpha = if (long) 0.45f else 0.3f))
        }
        drawCircle(color, r * 0.07f, c)
    } }
}

/** Dotted sea routes between the islands, behind the cards. */
fun DrawScope.drawSeaRoutes(color: Color, phase: Float = 0f) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(5f, 9f), phase * 14f)
    val y = size.height * 0.5f
    val p = Path().apply {
        moveTo(0f, y)
        cubicTo(size.width * 0.25f, y - size.height * 0.35f, size.width * 0.5f, y + size.height * 0.35f, size.width * 0.75f, y)
        cubicTo(size.width * 0.85f, y - size.height * 0.2f, size.width * 0.95f, y, size.width, y - size.height * 0.1f)
    }
    drawPath(p, color, style = Stroke(1.5f, pathEffect = dash))
}

// ---------------- «رسومات الصفحات الفارغة» ----------------

enum class MawjScene { Boat, Lighthouse, Net }

/** A small still drawing for empty and offline states: a boat on the waves, a lighthouse, an empty net. */
@Composable
fun MawjSceneArt(scene: MawjScene, gold: Color, modifier: Modifier = Modifier.fillMaxWidth().height(120.dp)) {
    val tr = rememberInfiniteTransition(label = "scene")
    val wave by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "wave")
    val rock by tr.animateFloat(-1f, 1f, infiniteRepeatable(tween(2_200), RepeatMode.Reverse), label = "rock")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val line = Stroke(width = 2.2f, cap = StrokeCap.Round)
        // The sea line under every scene.
        val shift = wave * 30f
        val sea = Path().apply {
            moveTo(cx - 120f, h * 0.82f)
            var x = cx - 120f
            while (x < cx + 120f) {
                val up = if (((x - cx + 120f + shift) / 30f).toInt() % 2 == 0) -8f else -4f
                quadraticTo(x + 15f, h * 0.82f + up, x + 30f, h * 0.82f); x += 30f
            }
        }
        drawPath(sea, gold.copy(alpha = 0.55f), style = line)
        when (scene) {
            MawjScene.Boat -> translate(0f, rock * 3f) { rotate(rock * 4f, Offset(cx, h * 0.75f)) {
                val hull = Path().apply { moveTo(cx - 46f, h * 0.7f); lineTo(cx + 46f, h * 0.7f); lineTo(cx + 30f, h * 0.8f); lineTo(cx - 30f, h * 0.8f); close() }
                drawPath(hull, gold.copy(alpha = 0.85f))
                drawLine(gold, Offset(cx, h * 0.7f), Offset(cx, h * 0.22f), strokeWidth = 2.4f)
                val sail = Path().apply { moveTo(cx + 3f, h * 0.24f); lineTo(cx + 40f, h * 0.64f); lineTo(cx + 3f, h * 0.64f); close() }
                drawPath(sail, gold.copy(alpha = 0.35f))
                drawPath(sail, gold, style = line)
            } }
            MawjScene.Lighthouse -> {
                val tower = Path().apply { moveTo(cx - 20f, h * 0.8f); lineTo(cx - 12f, h * 0.3f); lineTo(cx + 12f, h * 0.3f); lineTo(cx + 20f, h * 0.8f); close() }
                drawPath(tower, gold.copy(alpha = 0.3f))
                drawPath(tower, gold, style = line)
                drawRect(gold, Offset(cx - 14f, h * 0.2f), Size(28f, h * 0.1f))
                drawLine(gold.copy(alpha = 0.6f), Offset(cx - 16f, h * 0.55f), Offset(cx + 16f, h * 0.55f), strokeWidth = 2f)
                // The light, reaching out to both sides.
                // The light turns: one side brightens as the other fades.
                val a = (rock + 1f) / 2f
                val beam = Path().apply { moveTo(cx, h * 0.25f); lineTo(cx + 110f, h * 0.12f); lineTo(cx + 110f, h * 0.38f); close() }
                drawPath(beam, Brush.horizontalGradient(listOf(gold.copy(alpha = 0.5f * a), Color.Transparent), startX = cx, endX = cx + 110f))
                val beam2 = Path().apply { moveTo(cx, h * 0.25f); lineTo(cx - 110f, h * 0.12f); lineTo(cx - 110f, h * 0.38f); close() }
                drawPath(beam2, Brush.horizontalGradient(listOf(Color.Transparent, gold.copy(alpha = 0.5f * (1f - a))), startX = cx - 110f, endX = cx))
            }
            MawjScene.Net -> rotate(rock * 4f, Offset(cx, h * 0.25f)) {
                val net = Path().apply { moveTo(cx - 50f, h * 0.25f); quadraticTo(cx, h * 0.95f, cx + 50f, h * 0.25f) }
                drawPath(net, gold, style = line)
                for (i in 1..4) {
                    val t = i / 5f
                    drawLine(gold.copy(alpha = 0.45f), Offset(cx - 50f + 100f * t, h * 0.25f), Offset(cx, h * 0.62f), strokeWidth = 1.4f)
                }
                for (j in 1..3) {
                    val y = h * (0.3f + j * 0.09f)
                    val half = 50f * (1f - j * 0.27f)
                    drawLine(gold.copy(alpha = 0.45f), Offset(cx - half, y), Offset(cx + half, y), strokeWidth = 1.4f)
                }
                drawLine(gold, Offset(cx - 60f, h * 0.25f), Offset(cx + 60f, h * 0.25f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            }
        }
    }
}

// ---------------- خط الشعار ----------------

/** The logo's font (Thmanyah Serif Display Swash) for Arabic title names. */
@Composable
fun rememberMawjSwash(): FontFamily = FontFamily(Font(Res.font.mawj_swash))

fun String.hasArabic(): Boolean = any { it in '؀'..'ۿ' }

/** The touches as members, for files outside this package (`with(Touches) { … }`). */
object Touches {
    fun DrawScope.moonFrame(t: Float, lit: Color) = drawMoonLoader(t, lit, Color.White.copy(alpha = 0.12f))
    val grain: Modifier get() = Modifier.mawjFilmGrain()
    fun grainOn(m: Modifier, frame: () -> Float): Modifier = m.mawjFilmGrain(frame = frame)
    fun String.hasArabic(): Boolean = any { it in '؀'..'ۿ' }
}

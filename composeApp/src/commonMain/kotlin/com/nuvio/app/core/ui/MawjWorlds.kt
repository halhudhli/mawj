package com.nuvio.app.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * «عوالم موج»: each Mawj theme is a world — a palette and a living background of its own. The
 * background lies behind every page (like the sea); its motion is transform and colour only, drawn in
 * the draw phase, so pages never recompose for it.
 */
enum class MawjWorld(val theme: AppTheme) {
    Sea(AppTheme.MAWJ_SEA),
    Dawn(AppTheme.MAWJ_DAWN),
    Dunes(AppTheme.MAWJ_DUNES),
    Aurora(AppTheme.MAWJ_AURORA),
    Reef(AppTheme.MAWJ_REEF),
    Galaxy(AppTheme.MAWJ_GALAXY),
    Sadu(AppTheme.MAWJ_SADU),
}

/** Whether the page shown now is pale («الفجر»), so the system bars can switch to dark icons. */
object MawjPageTone {
    val light = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/**
 * The theme's gold and moon colours for the touches that are drawn by hand (moon ratings, the seal,
 * gold labels and lines). Backed by state, so a draw lambda that reads them redraws on a theme change.
 */
object MawjTint {
    var gold by androidx.compose.runtime.mutableStateOf(Color(0xFFD6B36A))
    var moon by androidx.compose.runtime.mutableStateOf(Color(0xFFF1DDA6))
    var moonDark by androidx.compose.runtime.mutableStateOf(Color(0xFF26323F))
    /** True on a pale page («الفجر»). */
    var pale by androidx.compose.runtime.mutableStateOf(false)

    /** [night] on the dark themes, [day] on a pale page. */
    fun ink(night: Color, day: Color): Color = if (pale) day else night

    fun apply(theme: AppTheme, accent: Color, light: Boolean) {
        gold = accent
        if (pale != light) pale = light
        val (lit, dark) = when (theme) {
            AppTheme.MAWJ_DAWN -> Color(0xFF9A7429) to Color(0xFFD9C9AE)
            AppTheme.MAWJ_DUNES -> Color(0xFFF2D39A) to Color(0xFF4A2F24)
            AppTheme.MAWJ_AURORA -> Color(0xFFA8F7DC) to Color(0xFF16323A)
            AppTheme.MAWJ_REEF -> Color(0xFFFFC2B4) to Color(0xFF0F4250)
            AppTheme.MAWJ_GALAXY -> Color(0xFFE9DCFF) to Color(0xFF231A46)
            AppTheme.MAWJ_SADU -> Color(0xFFF1E6D0) to Color(0xFF4A1C22)
            else -> Color(0xFFF1DDA6) to Color(0xFF26323F)
        }
        if (moon != lit) moon = lit
        if (moonDark != dark) moonDark = dark
    }
}

fun AppTheme.mawjWorld(): MawjWorld? = MawjWorld.entries.firstOrNull { it.theme == this }

/** One line for each world, shown under its live card in the theme picker. */
internal fun MawjWorld.blurb(): String = when (this) {
    MawjWorld.Sea -> com.nuvio.app.features.luxo.lx("بحر الليل وذهب القمر، والموج يتحرك ببطء", "The night sea and the moon's gold")
    MawjWorld.Dawn -> com.nuvio.app.features.luxo.lx("فاتح: رمل عاجي وبحر فضي، وذهب أعمق للقراءة في النهار", "Light: ivory sand and a silver sea")
    MawjWorld.Dunes -> com.nuvio.app.features.luxo.lx("ليل الصحراء: نجوم تومض وهلال، وكثبان نحاسية تنزلق", "A desert night, a crescent, copper dunes")
    MawjWorld.Aurora -> com.nuvio.app.features.luxo.lx("شرائط ضوء خضراء وبنفسجية تتمايل فوق الجبال", "Green and violet ribbons over the mountains")
    MawjWorld.Reef -> com.nuvio.app.features.luxo.lx("تحت الماء: أشعة تتمايل، وفقاعات تصعد، ومرجان وردي", "Under water: rays, bubbles, coral")
    MawjWorld.Galaxy -> com.nuvio.app.features.luxo.lx("سديم بنفسجي يدور ببطء، ونجوم تومض، وشهاب يعبر كل حين", "A slow violet nebula and a shooting star")
    MawjWorld.Sadu -> com.nuvio.app.features.luxo.lx("نسيج السدو: عنابي وأسود وذهبي، وشريط النقش ينساب كأنه يُنسج", "The Sadu weave: maroon, black and gold")
}

/**
 * «الثيم حسب الوقت»: «الفجر» from 6:00 to 17:59, the chosen theme at night («بحر موج» when the
 * chosen one is «الفجر» itself). Checked once a minute.
 */
@Composable
fun rememberMawjTimedTheme(selected: AppTheme): AppTheme {
    val features = rememberLuxoFeatures()
    if (!features.themeByTime) return selected
    var hour by remember { mutableIntStateOf(mawjHourNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = mawjHourNow()
        }
    }
    return if (hour in 6..17) AppTheme.MAWJ_DAWN else if (selected == AppTheme.MAWJ_DAWN) AppTheme.MAWJ_SEA else selected
}

private fun mawjHourNow(): Int = runCatching {
    kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
}.getOrDefault(20)

/** True when this world's background moves (its own switch in the theme picker, and not in light mode). */
@Composable
fun rememberMawjWorldMoving(world: MawjWorld): Boolean =
    world.theme.name !in rememberLuxoFeatures().worldStill

/**
 * A world's whole background, filling [modifier]. [moving] false draws the same picture once and
 * holds it. Used behind the pages and, small, on the live cards of the theme picker.
 */
@Composable
fun MawjWorldScene(world: MawjWorld, modifier: Modifier = Modifier, moving: Boolean = true, grain: Boolean = false) {
    val t by if (moving) mawjDrift("mawj_world_" + world.name, 36_000)
    else remember { mutableFloatStateOf(0.3f) }
    val grainMod = if (grain) com.nuvio.app.features.luxo.Touches.grainOn(Modifier) { t } else Modifier
    Canvas(modifier.fillMaxSize().then(grainMod)) {
        when (world) {
            MawjWorld.Sea -> sea(t)
            MawjWorld.Dawn -> dawn(t)
            MawjWorld.Dunes -> dunes(t)
            MawjWorld.Aurora -> aurora(t)
            MawjWorld.Reef -> reef(t)
            MawjWorld.Galaxy -> galaxy(t)
            MawjWorld.Sadu -> sadu(t)
        }
    }
}

private const val TAU = (2 * PI).toFloat()

/** Design units: the scenes were drawn on a 1800 × 1200 board. */
private fun DrawScope.ux(v: Float) = size.width * v / 1800f
private fun DrawScope.uy(v: Float) = size.height * v / 1200f
private fun DrawScope.ur(v: Float) = max(0.6f, min(size.width / 1800f, size.height / 1200f) * v * 1.6f)

/** A soft wave across the width from [baseY] down to the bottom; returns (body, crest). */
private fun DrawScope.waveBand(baseY: Float, amp: Float, waves: Float, phase: Float): Pair<Path, Path> {
    val crest = Path()
    val steps = 80
    for (s in 0..steps) {
        val f = s.toFloat() / steps
        val x = size.width * f
        val y = baseY + amp * sin(f * waves * TAU + phase) + amp * 0.3f * sin(f * waves * 2.2f * TAU - phase * 1.6f)
        if (s == 0) crest.moveTo(x, y) else crest.lineTo(x, y)
    }
    val body = Path().apply {
        addPath(crest)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    return body to crest
}

private fun DrawScope.stars(count: Int, maxY: Float, color: Color, t: Float, seedX: Int = 397, seedY: Int = 151) {
    for (i in 0 until count) {
        val x = ux(((i * seedX) % 1800).toFloat())
        val y = uy(((i * seedY) % maxY.toInt()).toFloat())
        val r = ur(1f + (i % 3) * 0.6f)
        val base = 0.3f + (i % 5) * 0.12f
        val speed = 2 + (i % 5)
        val tw = 0.55f + 0.45f * sin(t * TAU * speed + i * 1.7f)
        drawCircle(color.copy(alpha = (base * tw).coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

private fun DrawScope.sky(vararg stops: Pair<Float, Color>) {
    drawRect(Brush.verticalGradient(*stops))
}

// ───────────────────────────── the worlds ─────────────────────────────

private fun DrawScope.sea(t: Float) {
    sky(0f to Color(0xFF0A1724), 1f to Color(0xFF05090F))
    val moon = Offset(ux(1010f), uy(110f))
    drawCircle(Color(0xFFF1DDA6).copy(alpha = 0.05f), ur(110f), moon)
    drawCircle(Color(0xFFF1DDA6).copy(alpha = 0.8f), ur(40f), moon)
    val layers = listOf(Triple(700f, Color(0xFF12263A), 0.8f), Triple(860f, Color(0xFF0F2031), 0.9f), Triple(1010f, Color(0xFF0B1826), 1f))
    layers.forEachIndexed { i, (y, c, a) ->
        val (body, crest) = waveBand(uy(y), uy(28f - i * 4f), 2.2f - i * 0.4f, t * TAU * (if (i % 2 == 0) 1f else -1f) * (1 + i))
        drawPath(body, c.copy(alpha = a))
        if (i == 0) drawPath(crest, Color(0xFFD6B36A).copy(alpha = 0.3f), style = Stroke(uy(2f)))
    }
}

private fun DrawScope.dawn(t: Float) {
    sky(0f to Color(0xFFF6EFE3), 0.6f to Color(0xFFEFE2CD), 1f to Color(0xFFE6D3B5))
    val sun = Offset(ux(1010f), uy(120f))
    val breathe = 1f + 0.06f * sin(t * TAU * 2)
    drawCircle(Color(0xFFE9B96A).copy(alpha = 0.10f), ur(150f) * breathe, sun)
    drawCircle(Color(0xFFE9B96A).copy(alpha = 0.5f), ur(50f), sun)
    val layers = listOf(Triple(760f, Color(0xFFC9D6DD), 0.55f), Triple(900f, Color(0xFFA9BDC8), 0.45f), Triple(1040f, Color(0xFF8FA6B3), 0.4f))
    layers.forEachIndexed { i, (y, c, a) ->
        val (body, crest) = waveBand(uy(y), uy(22f - i * 3f), 2.2f - i * 0.4f, t * TAU * (if (i % 2 == 0) 1f else -1f) * (1 + i))
        drawPath(body, c.copy(alpha = a))
        if (i == 0) drawPath(crest, Color(0xFF9A7429).copy(alpha = 0.35f), style = Stroke(uy(2f)))
    }
}

private fun DrawScope.dunes(t: Float) {
    sky(0f to Color(0xFF0F0B14), 0.55f to Color(0xFF2A1A1C), 1f to Color(0xFF3D2416))
    stars(70, 520f, Color(0xFFF4E3C4), t)
    // The crescent: a disc with a second disc cut out of it.
    val c = Offset(ux(1010f), uy(115f))
    val r = ur(45f)
    val moon = Path().apply {
        op(
            Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) },
            Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.45f, c.y - r * 0.25f), r * 0.85f)) },
            PathOperation.Difference,
        )
    }
    drawCircle(Color(0xFFF2D39A).copy(alpha = 0.06f), r * 2.6f, c)
    drawPath(moon, Color(0xFFF2D39A))
    val layers = listOf(Triple(760f, Color(0xFF5A3420), 0.85f), Triple(900f, Color(0xFF7A4626), 1f), Triple(1050f, Color(0xFF9A5C30), 1f))
    layers.forEachIndexed { i, (y, col, a) ->
        val (body, crest) = waveBand(uy(y), uy(55f - i * 10f), 1.1f + i * 0.15f, t * TAU * (if (i % 2 == 0) 1f else -1f))
        drawPath(body, col.copy(alpha = a))
        if (i == 1) drawPath(crest, Color(0xFFE0A060).copy(alpha = 0.35f), style = Stroke(uy(2f)))
    }
}

private fun DrawScope.aurora(t: Float) {
    sky(0f to Color(0xFF03121A), 1f to Color(0xFF061F26))
    stars(40, 700f, Color(0xFFDFFCF2), t, seedY = 263)
    val ribbon = Brush.horizontalGradient(
        0f to Color(0x002BE3A8), 0.4f to Color(0xFF2BE3A8), 0.7f to Color(0xFF7B6CF0), 1f to Color(0x007B6CF0),
    )
    listOf(330f to 1f, 520f to 0.7f).forEachIndexed { i, (y, a) ->
        val path = Path()
        val steps = 70
        val sway = sin(t * TAU * (1 + i) + i) * uy(30f)
        for (s in 0..steps) {
            val f = s.toFloat() / steps
            val x = size.width * f
            val yy = uy(y) + sway + uy(90f) * sin(f * 1.6f * TAU + t * TAU * (if (i == 0) 1 else -1)) * (0.6f + 0.4f * f)
            if (s == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        // A soft curtain: wide faint strokes under a narrower bright one.
        listOf(uy(150f) to 0.10f, uy(90f) to 0.16f, uy(40f) to 0.24f, uy(14f) to 0.30f).forEach { (w, al) ->
            drawPath(path, ribbon, alpha = al * a, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
    val m = Path().apply {
        val pts = listOf(-50f to 930f, 120f to 860f, 260f to 940f, 420f to 820f, 600f to 920f, 760f to 850f, 930f to 940f,
            1120f to 830f, 1300f to 920f, 1480f to 860f, 1650f to 940f, 1850f to 880f)
        moveTo(ux(-50f), size.height)
        pts.forEach { (x, y) -> lineTo(ux(x), uy(y)) }
        lineTo(ux(1850f), size.height)
        close()
    }
    drawPath(m, Color(0xFF020B10))
}

private fun DrawScope.reef(t: Float) {
    sky(0f to Color(0xFF0B4552), 0.5f to Color(0xFF062C37), 1f to Color(0xFF03161D))
    val ray = Brush.verticalGradient(0f to Color(0x38BFF3FF), 1f to Color(0x00BFF3FF), endY = uy(900f))
    listOf(200f, 520f, 880f, 1250f, 1560f).forEachIndexed { i, x ->
        val shift = sin(t * TAU * (1 + i % 2) + i) * ux(60f)
        val p = Path().apply {
            moveTo(ux(x), 0f)
            lineTo(ux(x + 70f), 0f)
            lineTo(ux(x + 260f) + shift, uy(900f))
            lineTo(ux(x + 120f) + shift, uy(900f))
            close()
        }
        drawPath(p, ray)
    }
    // Bubbles rise and wobble, looping seamlessly.
    listOf(Triple(300f, 8f, 0f), Triple(330f, 5f, 0.4f), Triple(1460f, 9f, 0.2f), Triple(1490f, 5f, 0.7f),
        Triple(1000f, 6f, 0.5f), Triple(760f, 4f, 0.85f), Triple(1200f, 5f, 0.3f), Triple(560f, 7f, 0.65f)).forEachIndexed { i, (x, r, off) ->
        val f = ((off - t * (1 + i % 3)) % 1f + 1f) % 1f
        val y = uy(1150f) - f * uy(1000f)
        val wob = sin(f * TAU * 3 + i) * ux(12f)
        val a = 0.45f * min(1f, f * 6f) * min(1f, (1f - f) * 3f)
        drawCircle(Color(0xFFBFF3FF).copy(alpha = a), ur(r), Offset(ux(x) + wob, y), style = Stroke(ur(1.2f)))
    }
    val coral = Color(0xFFFF8A73).copy(alpha = 0.35f)
    val sw = sin(t * TAU * 2) * ux(10f)
    listOf(120f, 1500f).forEach { bx ->
        val p = Path().apply {
            moveTo(ux(bx), size.height)
            quadraticTo(ux(bx + 20f), uy(1080f), ux(bx - 20f) + sw, uy(1020f))
            quadraticTo(ux(bx + 40f), uy(1050f), ux(bx + 50f) + sw, uy(980f))
            quadraticTo(ux(bx + 70f), uy(1070f), ux(bx + 110f) + sw, uy(1040f))
            quadraticTo(ux(bx + 80f), uy(1120f), ux(bx + 120f), size.height)
            close()
        }
        drawPath(p, coral)
    }
    val bed = Path().apply {
        moveTo(0f, uy(1150f))
        cubicTo(ux(300f), uy(1120f), ux(600f), uy(1160f), ux(900f), uy(1130f))
        cubicTo(ux(1200f), uy(1110f), ux(1500f), uy(1110f), size.width, uy(1150f))
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(bed, Color(0xFF02121A))
}

private fun DrawScope.galaxy(t: Float) {
    drawRect(Brush.linearGradient(listOf(Color(0xFF0B0820), Color(0xFF05040F)), start = Offset.Zero, end = Offset(size.width, size.height)))
    val pivot = Offset(ux(900f), uy(560f))
    val turn = 10f * sin(t * TAU)
    withTransform({ rotate(turn, pivot) }) {
        nebula(Offset(ux(900f), uy(560f)), ux(820f), uy(300f), Color(0xFFB06CFF), 0.5f)
        nebula(Offset(ux(1180f), uy(420f)), ux(420f), uy(220f), Color(0xFFFF7AC8), 0.38f)
        nebula(Offset(ux(560f), uy(700f)), ux(380f), uy(180f), Color(0xFFFF7AC8), 0.26f)
    }
    stars(140, 1200f, Color(0xFFF3EAFF), t, seedY = 263)
    // A shooting star crosses once in each pass.
    val f = (t - 0.62f) / 0.07f
    if (f in 0f..1f) {
        val start = Offset(ux(1500f), uy(120f))
        val head = Offset(start.x - ux(700f) * f, start.y + uy(300f) * f)
        val tail = Offset(head.x + ux(260f), head.y - uy(110f))
        val fade = sin(f * PI.toFloat())
        drawLine(
            Brush.linearGradient(listOf(Color(0x00FFFFFF), Color.White.copy(alpha = 0.9f * fade)), start = tail, end = head),
            tail, head, strokeWidth = ur(2f), cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.nebula(c: Offset, rx: Float, ry: Float, color: Color, a: Float) {
    withTransform({ scale(1f, ry / rx, c) }) {
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = a),
                0.5f to color.copy(alpha = a * 0.4f),
                1f to color.copy(alpha = 0f),
                center = c,
                radius = rx,
            ),
            rx,
            c,
        )
    }
}

private fun DrawScope.sadu(t: Float) {
    sky(0f to Color(0xFF1C0A0D), 1f to Color(0xFF2C0F13))
    // Gold diamonds glint one after another.
    var i = 0
    var x = 80f
    while (x < 1800f) {
        for (y in listOf(120f, 420f, 720f)) {
            val g = max(0f, sin(t * TAU * 2 - i * 0.55f)).pow(6)
            val c = Offset(ux(x), uy(y) + uy(32f))
            val p = Path().apply {
                moveTo(c.x, c.y - uy(32f)); lineTo(c.x + ux(40f), c.y); lineTo(c.x, c.y + uy(32f)); lineTo(c.x - ux(40f), c.y); close()
            }
            drawPath(p, Color(0xFFE3B04B).copy(alpha = 0.05f + 0.22f * g))
            i++
        }
        x += 220f
    }
    // The woven band at the foot, flowing as if on the loom.
    val top = uy(1020f)
    val triH = uy(60f)
    val tile = ux(60f)
    val shift = (t * 6f % 1f) * tile
    drawRect(Color(0xFF7A1A22), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, triH))
    var tx = -tile + shift
    while (tx < size.width + tile) {
        val big = Path().apply { moveTo(tx, top + triH); lineTo(tx + tile / 2, top + triH * 0.3f); lineTo(tx + tile, top + triH); close() }
        drawPath(big, Color(0xFF1A0B0C))
        val small = Path().apply { moveTo(tx + tile / 4, top + triH); lineTo(tx + tile / 2, top + triH * 0.65f); lineTo(tx + tile * 3 / 4, top + triH); close() }
        drawPath(small, Color(0xFFE3B04B))
        tx += tile
    }
    val dTop = top + triH
    val dH = uy(40f)
    val dTile = ux(48f)
    drawRect(Color(0xFFF1E6D0), Offset(0f, dTop), androidx.compose.ui.geometry.Size(size.width, dH))
    var dx = -dTile - shift * 0.8f % dTile
    while (dx < size.width + dTile) {
        val cx = dx + dTile / 2
        val cy = dTop + dH / 2
        val outer = Path().apply { moveTo(cx, dTop + dH * 0.1f); lineTo(cx + dTile * 0.42f, cy); lineTo(cx, dTop + dH * 0.9f); lineTo(cx - dTile * 0.42f, cy); close() }
        drawPath(outer, Color(0xFF1A0B0C))
        val inner = Path().apply { moveTo(cx, dTop + dH * 0.32f); lineTo(cx + dTile * 0.2f, cy); lineTo(cx, dTop + dH * 0.68f); lineTo(cx - dTile * 0.2f, cy); close() }
        drawPath(inner, Color(0xFFB8282F))
        dx += dTile
    }
    val foot = dTop + dH
    drawRect(Color(0xFF7A1A22), Offset(0f, foot), androidx.compose.ui.geometry.Size(size.width, max(0f, size.height - foot)))
    drawRect(Color(0xFFE3B04B), Offset(0f, foot + uy(4f)), androidx.compose.ui.geometry.Size(size.width, uy(6f)))
    // Keep the woven foot calm under the content: a light veil of the page colour.
    drawRect(Color(0xFF1C0A0D).copy(alpha = 0.25f), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - top))
}

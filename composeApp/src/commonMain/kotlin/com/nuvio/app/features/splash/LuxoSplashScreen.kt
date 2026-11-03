package com.nuvio.app.features.splash

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import nuvio.composeapp.generated.resources.luxo_barlow_bold
import nuvio.composeapp.generated.resources.luxo_bebas
import nuvio.composeapp.generated.resources.luxo_cinzel
import nuvio.composeapp.generated.resources.luxo_poppins_bold
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark
import nuvio.composeapp.generated.resources.app_logo_wordmark_arctic_blue
import nuvio.composeapp.generated.resources.app_logo_wordmark_copper
import nuvio.composeapp.generated.resources.app_logo_wordmark_emerald
import nuvio.composeapp.generated.resources.app_logo_wordmark_gold
import nuvio.composeapp.generated.resources.app_logo_wordmark_graphite
import nuvio.composeapp.generated.resources.app_logo_wordmark_original
import nuvio.composeapp.generated.resources.app_logo_wordmark_rose_gold
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal fun rgbColor(rgb: Int, alpha: Float = 1f): Color =
    Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = alpha.coerceIn(0f, 1f),
    )

internal fun SplashLogoVariant.resource(): DrawableResource = when (this) {
    SplashLogoVariant.SILVER -> Res.drawable.app_logo_wordmark
    SplashLogoVariant.ORIGINAL -> Res.drawable.app_logo_wordmark_original
    SplashLogoVariant.GOLD -> Res.drawable.app_logo_wordmark_gold
    SplashLogoVariant.ROSE_GOLD -> Res.drawable.app_logo_wordmark_rose_gold
    SplashLogoVariant.COPPER -> Res.drawable.app_logo_wordmark_copper
    SplashLogoVariant.EMERALD -> Res.drawable.app_logo_wordmark_emerald
    SplashLogoVariant.ARCTIC_BLUE -> Res.drawable.app_logo_wordmark_arctic_blue
    SplashLogoVariant.GRAPHITE -> Res.drawable.app_logo_wordmark_graphite
}

/**
 * Luxo launch screen. Everything (background, colours, logo, motion) comes from [settings], so the
 * same composable drives both the real launch intro and the live preview in Settings.
 *
 * @param replayKey change it to replay the logo entrance animation (used by the preview).
 */
@Composable
fun LuxoSplashScreen(
    settings: SplashSettings,
    modifier: Modifier = Modifier,
    replayKey: Int = 0,
) {
    val background = rgbColor(settings.backgroundColor)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!settings.enabled) return@Box

        SplashAnimatedBackground(settings)
        SplashLogo(settings, replayKey)
    }
}

@Composable
private fun SplashAnimatedBackground(settings: SplashSettings) {
    // One frame clock for the whole background; only the Canvas re-draws each frame.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }

    val accent = settings.accentColor
    val stars = remember(settings.density) { buildStars((110 * settings.density).roundToInt().coerceAtLeast(10)) }
    val particles = remember(settings.density) { buildParticles((42 * settings.density).roundToInt().coerceAtLeast(6)) }
    val waveCount = (4 * settings.density).roundToInt().coerceIn(2, 9)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = time * settings.speed
        when (settings.style) {
            SplashBackgroundStyle.STARS -> drawStars(stars, t, accent)
            SplashBackgroundStyle.WAVES -> drawWaves(waveCount, t, accent)
            SplashBackgroundStyle.PARTICLES -> drawParticles(particles, t, accent)
            SplashBackgroundStyle.GRADIENT -> drawAurora(t, accent, settings.backgroundColor)
            SplashBackgroundStyle.SOLID -> Unit
            SplashBackgroundStyle.AURORA_RIBBONS -> drawRibbons(t, accent)
            SplashBackgroundStyle.FILM -> drawFilmStrip(t, accent)
            SplashBackgroundStyle.CURTAIN -> drawCurtain(t, accent)
            SplashBackgroundStyle.NEON -> drawNeonRings(t, accent)
            SplashBackgroundStyle.PROJECTOR -> drawProjector(t, accent, particles)
            SplashBackgroundStyle.SPOTLIGHTS -> drawSpotlights(t, accent)
            SplashBackgroundStyle.FILM_LEADER -> drawFilmLeader(t, accent)
            SplashBackgroundStyle.RAIN -> drawRain(stars, t, accent)
            SplashBackgroundStyle.BOKEH -> drawBokeh(particles, t, accent)
            SplashBackgroundStyle.WARP -> drawWarp(stars, t, accent)
            SplashBackgroundStyle.OLD_FILM -> drawOldFilm(t, accent)
        }
        if (settings.glow) {
            val radius = size.minDimension * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(rgbColor(accent, 0.22f), rgbColor(accent, 0.06f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

@Composable
private fun SplashLogo(settings: SplashSettings, replayKey: Int) {
    val alpha = remember(replayKey, settings.logoAnimation) { Animatable(if (settings.logoAnimation == SplashLogoAnimation.NONE) 1f else 0f) }
    val scale = remember(replayKey, settings.logoAnimation) {
        Animatable(
            when (settings.logoAnimation) {
                SplashLogoAnimation.ZOOM -> 0.55f
                SplashLogoAnimation.SPIN -> 0.2f
                else -> 1f
            },
        )
    }
    // 1 → 0: how far the logo still is from its place (slide / drop / flip / spin).
    val travel = remember(replayKey, settings.logoAnimation) {
        Animatable(if (settings.logoAnimation in setOf(SplashLogoAnimation.SLIDE_UP, SplashLogoAnimation.DROP, SplashLogoAnimation.FLIP, SplashLogoAnimation.SPIN)) 1f else 0f)
    }

    LaunchedEffect(replayKey, settings.logoAnimation) {
        when (settings.logoAnimation) {
            SplashLogoAnimation.NONE -> Unit
            SplashLogoAnimation.FADE -> alpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            SplashLogoAnimation.ZOOM -> {
                launch { alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 120f))
            }
            SplashLogoAnimation.SLIDE_UP -> {
                launch { alpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
                travel.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
            }
            SplashLogoAnimation.DROP -> {
                launch { alpha.animateTo(1f, tween(250)) }
                travel.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 140f))
            }
            SplashLogoAnimation.FLIP -> {
                launch { alpha.animateTo(1f, tween(400)) }
                travel.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
            }
            SplashLogoAnimation.SPIN -> {
                launch { alpha.animateTo(1f, tween(400)) }
                launch { scale.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
                travel.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
            }
            SplashLogoAnimation.FLICKER -> {
                // Like an old projector catching the frame.
                listOf(0.7f, 0.1f, 0.9f, 0.3f, 1f, 0.6f, 1f).forEach { a ->
                    alpha.animateTo(a, tween(90))
                }
            }
            SplashLogoAnimation.PULSE -> {
                alpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                while (true) {
                    scale.animateTo(1.06f, tween(700, easing = FastOutSlowInEasing))
                    scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    // Luxo: colours that flow inside the letters (like Samt's waves).
    var flow by remember { mutableFloatStateOf(0f) }
    if (settings.flowingLetters) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now -> flow = (now - start) / 1_000_000_000f * settings.speed }
        }
    }
    val flowColors = listOf(rgbColor(settings.accentColor), Color(0xFF8C7BFF), Color(0xFFFF7EB6), rgbColor(settings.accentColor))
    val animatedModifier = Modifier.graphicsLayer {
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
        val tr = travel.value
        when (settings.logoAnimation) {
            SplashLogoAnimation.SLIDE_UP -> translationY = 140.dp.toPx() * tr
            SplashLogoAnimation.DROP -> translationY = -size.height * 4f * tr
            SplashLogoAnimation.FLIP -> {
                rotationY = 180f * tr
                cameraDistance = 14f * density
            }
            SplashLogoAnimation.SPIN -> rotationZ = -360f * tr
            else -> Unit
        }
        if (settings.flowingLetters) compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.then(
        if (settings.flowingLetters) {
            Modifier.drawWithContent {
                drawContent()
                val shift = size.width * (flow * 0.35f % 2f)
                drawRect(
                    brush = Brush.linearGradient(
                        flowColors,
                        start = Offset(-size.width + shift, 0f),
                        end = Offset(shift, size.height),
                        tileMode = androidx.compose.ui.graphics.TileMode.Mirror,
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                )
            }
        } else {
            Modifier
        },
    )
    val fontResource = settings.logoFont.fontResource()
    if (fontResource == null) {
        Image(
            painter = painterResource(settings.logoVariant.resource()),
            contentDescription = "Luxo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width((230 * settings.logoScale).dp)
                .then(animatedModifier),
        )
    } else {
        Text(
            text = "LUXO",
            fontFamily = FontFamily(Font(fontResource)),
            fontSize = (54 * settings.logoScale * settings.logoFont.sizeFactor()).sp,
            letterSpacing = (10 * settings.logoScale).sp,
            maxLines = 1,
            style = TextStyle(
                color = rgbColor(settings.logoTextColor),
                shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 6f), blurRadius = 24f),
            ),
            modifier = animatedModifier,
        )
    }
}

/** Font used to write "LUXO"; null means the image wordmark. */
internal fun SplashLogoFont.fontResource(): FontResource? = when (this) {
    SplashLogoFont.DEFAULT -> null
    SplashLogoFont.CINZEL -> Res.font.luxo_cinzel
    SplashLogoFont.BEBAS -> Res.font.luxo_bebas
    SplashLogoFont.BARLOW -> Res.font.luxo_barlow_bold
    SplashLogoFont.POPPINS -> Res.font.luxo_poppins_bold
}

/** Bebas is a tall condensed face; give it a little more size so it reads the same. */
private fun SplashLogoFont.sizeFactor(): Float = if (this == SplashLogoFont.BEBAS) 1.25f else 1f

// ---------------------------------------------------------------- drawing

private class Star(val x: Float, val y: Float, val radius: Float, val phase: Float, val twinkle: Float, val depth: Float)
private class Particle(val x: Float, val y: Float, val radius: Float, val rise: Float, val sway: Float, val phase: Float)

private fun buildStars(count: Int): List<Star> {
    val random = Random(7)
    return List(count) {
        Star(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 0.6f + random.nextFloat() * 1.8f,
            phase = random.nextFloat() * 2f * PI.toFloat(),
            twinkle = 0.6f + random.nextFloat() * 2.2f,
            depth = 0.2f + random.nextFloat(),
        )
    }
}

private fun buildParticles(count: Int): List<Particle> {
    val random = Random(11)
    return List(count) {
        Particle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = 1.5f + random.nextFloat() * 5f,
            rise = 0.03f + random.nextFloat() * 0.07f,
            sway = 0.01f + random.nextFloat() * 0.03f,
            phase = random.nextFloat() * 2f * PI.toFloat(),
        )
    }
}

private fun wrap(value: Float): Float = value - kotlin.math.floor(value)

private fun DrawScope.drawStars(stars: List<Star>, t: Float, accent: Int) {
    val pxPerDp = 1.dp.toPx()
    stars.forEach { star ->
        val twinkle = 0.5f + 0.5f * sin(t * star.twinkle + star.phase)
        val y = wrap(star.y + t * 0.006f * star.depth)
        drawCircle(
            color = rgbColor(accent, 0.18f + 0.82f * twinkle),
            radius = star.radius * pxPerDp * (0.75f + 0.35f * twinkle),
            center = Offset(star.x * size.width, y * size.height),
        )
    }
}

private fun DrawScope.drawWaves(count: Int, t: Float, accent: Int) {
    val width = size.width
    val midY = size.height / 2f
    val segments = 72
    repeat(count) { index ->
        val fraction = if (count == 1) 0f else index / (count - 1f)
        val amplitude = size.height * (0.035f + 0.05f * fraction)
        val frequency = 1.2f + fraction * 1.1f
        val phase = t * (0.9f + 0.35f * index) + index * 1.3f
        val offsetY = (fraction - 0.5f) * size.height * 0.16f
        val path = Path()
        for (step in 0..segments) {
            val px = width * step / segments
            val angle = (step.toFloat() / segments) * 2f * PI.toFloat() * frequency + phase
            val py = midY + offsetY + sin(angle) * amplitude * (0.6f + 0.4f * sin(t * 0.5f + index))
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, rgbColor(accent, 0.85f), Color.Transparent),
        )
        drawPath(path, brush = brush, alpha = 0.18f, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, brush = brush, alpha = 0.75f, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawParticles(particles: List<Particle>, t: Float, accent: Int) {
    particles.forEach { p ->
        val life = wrap(p.y + t * p.rise)
        val y = size.height * (1.1f - life * 1.2f)
        val x = size.width * (p.x + sin(t * 0.8f + p.phase) * p.sway)
        val fade = sin(life * PI.toFloat())
        val radius = p.radius * 2.2f * 1.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(rgbColor(accent, 0.55f * fade), Color.Transparent),
                center = Offset(x, y),
                radius = radius,
            ),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawAurora(t: Float, accent: Int, background: Int) {
    val blobs = listOf(0f, 2.1f, 4.2f)
    blobs.forEachIndexed { index, phase ->
        val angle = t * (0.25f + index * 0.08f) + phase
        val c = Offset(
            x = size.width * (0.5f + 0.32f * cos(angle)),
            y = size.height * (0.5f + 0.26f * sin(angle * 1.3f)),
        )
        val radius = size.maxDimension * (0.45f + 0.1f * sin(t * 0.4f + phase))
        val tint = if (index == 1) blend(accent, background, 0.45f) else accent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(rgbColor(tint, 0.30f), Color.Transparent),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
    }
}

private fun blend(a: Int, b: Int, amount: Float): Int {
    fun ch(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + (cb - ca) * amount).roundToInt().coerceIn(0, 255)
    }
    return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}


// ---------------------------------------------------------------- Luxo extra styles

private fun DrawScope.drawRibbons(t: Float, accent: Int) {
    val colors = listOf(rgbColor(accent, 0.35f), Color(0x558C7BFF), Color(0x55FF7EB6))
    colors.forEachIndexed { i, c ->
        val path = androidx.compose.ui.graphics.Path()
        val base = size.height * (0.35f + i * 0.12f)
        path.moveTo(0f, base)
        val steps = 48
        for (k in 0..steps) {
            val x = size.width * k / steps
            val y = base + sin(k / 6f + t * (0.6f + i * 0.2f)) * size.height * 0.06f
            path.lineTo(x, y)
        }
        for (k in steps downTo 0) {
            val x = size.width * k / steps
            val y = base + size.height * 0.12f + sin(k / 5f + t * (0.5f + i * 0.25f) + 1f) * size.height * 0.05f
            path.lineTo(x, y)
        }
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c, Color.Transparent), startY = base - size.height * 0.08f, endY = base + size.height * 0.2f))
    }
}

private fun DrawScope.drawFilmStrip(t: Float, accent: Int) {
    val h = size.height * 0.26f
    val top = (size.height - h) / 2f
    drawRect(Color(0xFF0A0A0A), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h))
    val hole = h * 0.09f
    val gap = hole * 2.2f
    val shift = (t * 120f) % gap
    var x = -gap + shift
    while (x < size.width + gap) {
        listOf(top + hole * 0.6f, top + h - hole * 1.6f).forEach { y ->
            drawRoundRect(rgbColor(accent, 0.55f), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(hole * 1.2f, hole), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f))
        }
        x += gap
    }
    // Frame separators sliding by.
    val frame = h * 1.3f
    var fx = -frame + (t * 120f) % frame
    while (fx < size.width + frame) {
        drawLine(rgbColor(accent, 0.25f), Offset(fx, top + hole * 2.4f), Offset(fx, top + h - hole * 2.4f), strokeWidth = 3f)
        fx += frame
    }
}

private fun DrawScope.drawCurtain(t: Float, accent: Int) {
    // Opens over ~1.6 s, then gently sways.
    val open = (t / 1.6f).coerceIn(0f, 1f)
    val eased = 1f - (1f - open) * (1f - open)
    val half = size.width / 2f
    val w = half * (1f - eased * 0.82f)
    val sway = sin(t * 1.3f) * 6f * eased
    val curtain = listOf(Color(0xFF5A0F1F), Color(0xFF8E1B33), Color(0xFF5A0F1F))
    for (side in 0..1) {
        val left = if (side == 0) 0f else size.width - w
        drawRect(Brush.horizontalGradient(curtain, startX = left, endX = left + w), topLeft = Offset(left + if (side == 0) sway else -sway, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
        var fold = left
        while (fold < left + w) {
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(fold, 0f), Offset(fold, size.height), strokeWidth = 2f)
            fold += 28f
        }
    }
    drawRect(rgbColor(accent, 0.18f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.06f))
}

private fun DrawScope.drawNeonRings(t: Float, accent: Int) {
    val c = center
    for (i in 0 until 4) {
        val phase = (t * 0.45f + i * 0.25f) % 1f
        val radius = size.minDimension * (0.12f + phase * 0.55f)
        drawCircle(
            color = (if (i % 2 == 0) Color(0xFFFF7EB6) else rgbColor(accent)).copy(alpha = (1f - phase) * 0.55f),
            radius = radius,
            center = c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f * (1f - phase) + 1.5f),
        )
    }
}


// ---------------------------------------------------------------- Luxo: more backgrounds

private fun DrawScope.drawProjector(t: Float, accent: Int, dust: List<Particle>) {
    // A beam from the top corner with dust floating inside it.
    val src = Offset(size.width * 0.5f, -size.height * 0.08f)
    val flicker = 0.85f + 0.15f * sin(t * 23f) * sin(t * 7f)
    val beam = androidx.compose.ui.graphics.Path().apply {
        moveTo(src.x - 10f, src.y)
        lineTo(src.x + 10f, src.y)
        lineTo(size.width * 0.95f, size.height)
        lineTo(size.width * 0.05f, size.height)
        close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(rgbColor(accent, 0.30f * flicker), rgbColor(accent, 0.04f))))
    dust.forEach { p ->
        val x = size.width * (0.2f + 0.6f * wrap(p.x + t * 0.01f * (p.rise * 15f)))
        val y = size.height * wrap(p.y + t * 0.02f * (p.rise * 15f))
        drawCircle(rgbColor(accent, 0.35f * flicker), radius = p.radius * 0.6f, center = Offset(x, y))
    }
}

private fun DrawScope.drawSpotlights(t: Float, accent: Int) {
    // Premiere searchlights sweeping the sky.
    for (i in 0..1) {
        val baseX = size.width * (if (i == 0) 0.25f else 0.75f)
        val angle = (sin(t * 0.7f + i * 2.1f) * 0.45f).toDouble()
        val len = size.height * 1.4f
        val tipX = baseX + (kotlin.math.sin(angle) * len).toFloat()
        val tipY = size.height - (kotlin.math.cos(angle) * len).toFloat()
        val spread = size.width * 0.12f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(baseX - 8f, size.height)
            lineTo(baseX + 8f, size.height)
            lineTo(tipX + spread, tipY)
            lineTo(tipX - spread, tipY)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, rgbColor(accent, 0.28f)), startY = tipY, endY = size.height))
    }
}

private fun DrawScope.drawFilmLeader(t: Float, accent: Int) {
    // The old "3 · 2 · 1" film leader: crosshair, rings and a sweeping hand.
    val c = center
    val r = size.minDimension * 0.34f
    drawLine(rgbColor(accent, 0.25f), Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = 2f)
    drawLine(rgbColor(accent, 0.25f), Offset(c.x, 0f), Offset(c.x, size.height), strokeWidth = 2f)
    drawCircle(rgbColor(accent, 0.35f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
    drawCircle(rgbColor(accent, 0.25f), r * 1.18f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    val sweep = (t % 1f) * 360f
    drawArc(rgbColor(accent, 0.16f), startAngle = -90f, sweepAngle = sweep, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
}

private fun DrawScope.drawRain(drops: List<Star>, t: Float, accent: Int) {
    drops.forEach { d ->
        val x = size.width * d.x + sin(t) * 6f
        val y = size.height * wrap(d.y + t * (0.5f + d.depth * 0.2f))
        val len = 18f + d.depth * 10f
        drawLine(rgbColor(accent, 0.18f + d.depth * 0.08f), Offset(x, y), Offset(x - len * 0.18f, y + len), strokeWidth = 1.4f)
    }
}

private fun DrawScope.drawBokeh(lights: List<Particle>, t: Float, accent: Int) {
    lights.forEachIndexed { i, p ->
        val x = size.width * wrap(p.x + sin(t * 0.2f + i) * 0.03f)
        val y = size.height * wrap(p.y - t * 0.01f * (p.rise * 15f))
        val r = size.minDimension * (0.03f + p.radius * 0.012f)
        val col = if (i % 3 == 0) Color(0xFFFF7EB6) else if (i % 3 == 1) Color(0xFF8C7BFF) else rgbColor(accent)
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.32f), col.copy(alpha = 0.08f), Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
    }
}

private fun DrawScope.drawWarp(stars: List<Star>, t: Float, accent: Int) {
    // Stars rushing out from the middle.
    val c = center
    val maxR = size.maxDimension * 0.75f
    stars.forEach { s ->
        val angle = s.x * 6.2832f
        val phase = wrap(s.y + t * 0.35f)
        val d1 = maxR * phase * phase
        val d2 = maxR * (phase * phase + 0.04f * phase)
        val p1 = Offset(c.x + kotlin.math.cos(angle) * d1, c.y + kotlin.math.sin(angle) * d1)
        val p2 = Offset(c.x + kotlin.math.cos(angle) * d2, c.y + kotlin.math.sin(angle) * d2)
        drawLine(rgbColor(accent, 0.15f + 0.7f * phase), p1, p2, strokeWidth = 1f + 2.5f * phase)
    }
}

private fun DrawScope.drawOldFilm(t: Float, accent: Int) {
    // Sepia flicker, grain and scratches of an old reel.
    val flicker = 0.06f + 0.05f * kotlin.math.abs(sin(t * 31f))
    drawRect(Color(0xFF3B2A18).copy(alpha = 0.55f))
    drawRect(Color.White.copy(alpha = flicker))
    val frame = (t * 24f).toInt()
    val rnd = kotlin.random.Random(frame)
    repeat(140) {
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = rnd.nextFloat() * 1.8f + 0.4f, center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
    repeat(2) {
        if (rnd.nextFloat() > 0.4f) {
            val x = rnd.nextFloat() * size.width
            drawLine(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Offset(x + rnd.nextFloat() * 6f, size.height), strokeWidth = 1.2f)
        }
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), center = center, radius = size.maxDimension * 0.7f))
    drawRect(rgbColor(accent, 0.05f))
}

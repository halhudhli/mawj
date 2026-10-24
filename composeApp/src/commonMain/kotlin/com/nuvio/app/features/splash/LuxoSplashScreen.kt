package com.nuvio.app.features.splash

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
        Animatable(if (settings.logoAnimation == SplashLogoAnimation.ZOOM) 0.55f else 1f)
    }

    LaunchedEffect(replayKey, settings.logoAnimation) {
        when (settings.logoAnimation) {
            SplashLogoAnimation.NONE -> Unit
            SplashLogoAnimation.FADE -> alpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            SplashLogoAnimation.ZOOM -> {
                launch { alpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 120f))
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

    Image(
        painter = painterResource(settings.logoVariant.resource()),
        contentDescription = "Luxo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width((230 * settings.logoScale).dp)
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

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

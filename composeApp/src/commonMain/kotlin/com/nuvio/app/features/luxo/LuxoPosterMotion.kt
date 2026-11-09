package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.nuvio.app.core.ui.LocalScreenActive
import kotlin.math.PI
import kotlin.math.cos

/**
 * «حركة البوسترات»: each poster drifts in and out slowly (9 s, up to 9 % closer) and a soft sheen
 * crosses it every 7 s. Every card starts at its own point so a row never moves in step.
 *  - Only composed cards animate, i.e. the ones on screen (lazy rows don't compose the rest).
 *  - The clock stops while anything is scrolled and resumes where it was.
 *  - Only the draw phase is invalidated each frame; nothing recomposes.
 */
object LuxoMotionScroll {
    @kotlin.concurrent.Volatile
    private var lastScrollMs = 0L

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            lastScrollMs = luxoNowMs()
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            lastScrollMs = luxoNowMs()
            return Velocity.Zero
        }
    }

    fun scrolling(): Boolean = luxoNowMs() - lastScrollMs < 220L
}

private const val DRIFT_SECONDS = 9f
private const val DRIFT_ZOOM = 0.09f
private const val SHEEN_EVERY_SECONDS = 7f
private const val SHEEN_PASS_SECONDS = 1.6f
private const val SHEEN_ALPHA = 0.18f

@Composable
fun Modifier.luxoPosterMotion(seed: Int): Modifier {
    val features = rememberLuxoFeatures()
    // Tablet first: Android phones and tablets only for now (not the TV, not the iPhone yet).
    if (!features.posterMotion || !luxoIsAndroidPlatform() || luxoIsTv()) return this
    val active = LocalScreenActive.current
    val start = remember(seed) { ((seed and 0x7fffffff) % 9000) / 1000f }
    val clock = remember(seed) { mutableFloatStateOf(start) }
    LaunchedEffect(active, seed) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L && !LuxoMotionScroll.scrolling()) {
                    clock.floatValue += (now - last).coerceAtMost(100L) / 1000f
                }
                last = now
            }
        }
    }
    val sheenOffset = remember(seed) { (((seed ushr 8) and 0x7fffffff) % 7000) / 1000f }
    return this
        .graphicsLayer {
            val phase = (clock.floatValue / DRIFT_SECONDS) % 2f
            val k = if (phase < 1f) phase else 2f - phase
            val eased = (1f - cos(PI.toFloat() * k)) / 2f
            val scale = 1f + DRIFT_ZOOM * eased
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            val pass = ((clock.floatValue + sheenOffset) % SHEEN_EVERY_SECONDS) / SHEEN_PASS_SECONDS
            if (pass <= 1f) {
                val w = size.width
                val centerX = w * (-0.5f + pass * 2f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = SHEEN_ALPHA), Color.Transparent),
                        start = Offset(centerX - w * 0.35f, 0f),
                        end = Offset(centerX + w * 0.35f, size.height),
                    ),
                )
            }
        }
}

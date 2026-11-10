package com.nuvio.app.features.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.delay

/** Copies the current video frame (scaled to [width]×[height]); null when the platform can't. */
internal expect suspend fun luxoCaptureVideoFrame(width: Int, height: Int): ImageBitmap?

/** Saves a full-size frame to the gallery (Pictures/Luxo). Returns true on success. */
internal expect suspend fun luxoSaveVideoScreenshot(name: String): Boolean

private data class LuxoEdgeColors(val start: Color, val end: Color, val top: Color, val bottom: Color)

private fun ImageBitmap.edgeColors(): LuxoEdgeColors {
    val pixels = toPixelMap()
    fun average(xs: IntRange, ys: IntRange): Color {
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        for (x in xs) for (y in ys) {
            val c = pixels[x, y]
            r += c.red; g += c.green; b += c.blue; n++
        }
        if (n == 0) return Color.Black
        return Color(r / n, g / n, b / n)
    }
    val w = width
    val h = height
    val band = (w / 6).coerceAtLeast(1)
    return LuxoEdgeColors(
        start = average(0 until band, 0 until h),
        end = average(w - band until w, 0 until h),
        top = average(0 until w, 0 until (h / 6).coerceAtLeast(1)),
        bottom = average(0 until w, h - (h / 6).coerceAtLeast(1) until h),
    )
}

/**
 * A soft glow at the screen edges in the colours of the scene (like Ambilight TVs).
 * Samples a tiny frame every couple of seconds, so it costs very little.
 */
@Composable
internal fun LuxoAmbientGlow(isPlaying: Boolean, modifier: Modifier = Modifier) {
    var colors by remember { mutableStateOf(LuxoEdgeColors(Color.Black, Color.Black, Color.Black, Color.Black)) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            luxoCaptureVideoFrame(48, 27)?.let { colors = it.edgeColors() }
            delay(1_800L)
        }
    }
    val spec = tween<Color>(1_400)
    val start by animateColorAsState(colors.start, spec, label = "glow_start")
    val end by animateColorAsState(colors.end, spec, label = "glow_end")
    val top by animateColorAsState(colors.top, spec, label = "glow_top")
    val bottom by animateColorAsState(colors.bottom, spec, label = "glow_bottom")
    Canvas(modifier) {
        val edge = size.minDimension * 0.16f
        drawRect(Brush.horizontalGradient(listOf(start.copy(alpha = 0.55f), Color.Transparent), startX = 0f, endX = edge))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, end.copy(alpha = 0.55f)), startX = size.width - edge, endX = size.width))
        drawRect(Brush.verticalGradient(listOf(top.copy(alpha = 0.4f), Color.Transparent), startY = 0f, endY = edge))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, bottom.copy(alpha = 0.4f)), startY = size.height - edge, endY = size.height))
        drawCircle(Color.Transparent, radius = 0f, center = Offset.Zero)
    }
}

/** The current frame as JPEG bytes (for questions about the scene); null when the platform can't. */
internal expect suspend fun luxoCaptureVideoJpeg(width: Int, height: Int): ByteArray?


/** Layout size of the video view in pixels (before any zoom), or null when the platform can't tell. */
internal expect suspend fun luxoVideoViewSize(): androidx.compose.ui.unit.IntSize?

package com.nuvio.app.features.player.seekpreview

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.features.player.formatPlaybackTime
import kotlin.math.roundToInt

private val PreviewWidth = 160.dp
private val PreviewShape = RoundedCornerShape(10.dp)
private const val DefaultAspectRatio = 16f / 9f

@Composable
internal fun SeekPreviewOverlay(
    controller: SeekPreviewController?,
    active: Boolean,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    chapters: List<com.nuvio.app.features.player.PlayerChapter> = emptyList(),
) {
    if (controller == null || durationMs <= 0L) return
    // Where the scrub started, for the «+2:14» difference.
    var startMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(positionMs) }
    androidx.compose.runtime.LaunchedEffect(active) { if (active) startMs = positionMs }
    val visible = active && !controller.isUnavailable

    LaunchedEffect(controller, visible, positionMs, durationMs) {
        if (visible) controller.request(positionMs, durationMs)
    }
    LaunchedEffect(controller, active) {
        if (!active) controller.endScrub()
    }
    LaunchedEffect(controller) {
        controller.warmUp(positionMs, durationMs)
    }

    Layout(
        modifier = modifier,
        content = {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(120),
                ),
                exit = fadeOut(tween(160)) + scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(160),
                ),
            ) {
                if (com.nuvio.app.features.luxo.rememberLuxoFeatures().filmReel) {
                    LuxoFilmReelBubble(controller = controller, positionMs = positionMs, durationMs = durationMs, chapters = chapters, startMs = startMs)
                } else {
                    SeekPreviewBubble(controller = controller, positionMs = positionMs)
                }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            if (placeable == null) return@layout
            val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val x = (fraction * width - placeable.width / 2f).roundToInt()
                .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
            placeable.place(x, -placeable.height - gap.roundToPx())
        }
    }
}

@Composable
private fun SeekPreviewBubble(controller: SeekPreviewController, positionMs: Long) {
    val frame = controller.frame
    val aspectRatio = frame?.bitmap
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
        ?.coerceIn(1f, 2.4f)
        ?: DefaultAspectRatio

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = PreviewWidth, height = PreviewWidth / aspectRatio)
                .clip(PreviewShape)
                .background(Color.Black)
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), PreviewShape),
            contentAlignment = Alignment.Center,
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.8f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatPlaybackTime(positionMs),
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}


/**
 * Luxo «بكرة الفيلم»: five frames like a strip of film — the centre one big and sharp, the
 * neighbours smaller and fading, tilted away — with the time, the difference from where the scrub
 * started and the chapter name.
 */
@Composable
private fun LuxoFilmReelBubble(
    controller: SeekPreviewController,
    positionMs: Long,
    durationMs: Long,
    chapters: List<com.nuvio.app.features.player.PlayerChapter>,
    startMs: Long,
) {
    val frame = controller.frame
    val step = (durationMs / 60L).coerceIn(5_000L, 30_000L)
    val offsets = listOf(-2, -1, 0, 1, 2)
    val chapter = chapters.lastOrNull { it.startMs <= positionMs }?.title?.takeIf { it.isNotBlank() }
    val delta = positionMs - startMs
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatPlaybackTime(positionMs),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            if (kotlin.math.abs(delta) >= 1_000L) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = (if (delta > 0) "+" else "−") + formatPlaybackTime(kotlin.math.abs(delta)),
                    color = if (delta > 0) Color(0xFF3DDC97) else Color(0xFFFF8A80),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        chapter?.let {
            Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            offsets.forEach { o ->
                val w = when (kotlin.math.abs(o)) { 0 -> 176.dp; 1 -> 104.dp; else -> 70.dp }
                val a = when (kotlin.math.abs(o)) { 0 -> 1f; 1 -> 0.65f; else -> 0.38f }
                val bitmap = if (o == 0) frame?.bitmap else controller.cachedNear((positionMs + o * step).coerceIn(0L, durationMs), step / 2)
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .graphicsLayer {
                            alpha = a
                            rotationY = -o * 14f
                            cameraDistance = 12f * density
                        }
                        .size(width = w, height = w * 9f / 16f)
                        .clip(RoundedCornerShape(if (o == 0) 10.dp else 6.dp))
                        .background(Color(0xFF16181F))
                        .then(if (o == 0) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    } else if (o == 0) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

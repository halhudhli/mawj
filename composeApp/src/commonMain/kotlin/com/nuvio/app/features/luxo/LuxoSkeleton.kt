package com.nuvio.app.features.luxo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.watchprogress.CurrentDateProvider

/** A soft light that sweeps across placeholder shapes while content loads. */
@Composable
fun Modifier.luxoShimmer(): Modifier {
    if (rememberLuxoLite()) return this.background(Color.White.copy(alpha = 0.07f))
    val transition = rememberInfiniteTransition(label = "luxo_shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "luxo_shimmer_x",
    )
    return this
        .background(Color.White.copy(alpha = 0.06f))
        .drawWithContent {
            drawContent()
            val center = size.width * x
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                    start = Offset(center - size.width * 0.4f, 0f),
                    end = Offset(center + size.width * 0.4f, size.height),
                ),
            )
        }
}

@Composable
fun LuxoSkeletonBox(modifier: Modifier, corner: Dp = 16.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).luxoShimmer())
}

/** A row of poster-shaped placeholders. */
@Composable
fun LuxoSkeletonPosterRow(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LuxoSkeletonBox(Modifier.width(140.dp).height(18.dp), corner = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(count) { LuxoSkeletonBox(Modifier.width(110.dp).aspectRatio(2f / 3f)) }
        }
    }
}

/** List-card placeholders (thumbnail + two lines). */
@Composable
fun LuxoSkeletonList(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LuxoSkeletonBox(Modifier.width(140.dp).aspectRatio(16f / 9f))
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LuxoSkeletonBox(Modifier.fillMaxWidth(0.7f).height(16.dp), corner = 6.dp)
                    LuxoSkeletonBox(Modifier.fillMaxWidth(0.45f).height(12.dp), corner = 6.dp)
                }
            }
        }
    }
}

/** Days since 1970 for an ISO date (yyyy-MM-dd…). */
internal fun luxoEpochDay(iso: String): Long {
    val parts = iso.take(10).split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return 0
    val (y, m, d) = parts
    val a = (14 - m) / 12
    val yy = y + 4800 - a
    val mm = m + 12 * a - 3
    val jdn = d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    return jdn - 2440588L
}

internal fun luxoTodayEpochDay(): Long = luxoEpochDay(CurrentDateProvider.todayIsoDate())

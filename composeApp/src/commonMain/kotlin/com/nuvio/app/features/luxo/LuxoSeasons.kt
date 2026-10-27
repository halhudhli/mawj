package com.nuvio.app.features.luxo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlin.math.PI
import kotlin.math.sin

/** Occasions that dress the app up for a few days. */
enum class LuxoSeason(
    val greetingAr: String,
    val greetingEn: String,
    val subtitleAr: String,
    val subtitleEn: String,
    val colors: List<Color>,
) {
    RAMADAN("رمضان كريم 🌙", "Ramadan Kareem 🌙", "سهرات رمضان صارت أحلى", "Ramadan nights just got better", listOf(Color(0xFF2B1B5C), Color(0xFF6B3FA0), Color(0xFFE8B04A))),
    EID_FITR("عيدكم مبارك 🎉", "Eid Mubarak 🎉", "كل عام وأنتم بخير", "Wishing you a joyful Eid", listOf(Color(0xFF0E4D64), Color(0xFF1D8A99), Color(0xFFF4D35E))),
    EID_ADHA("عيد أضحى مبارك 🐑", "Eid al-Adha Mubarak 🐑", "تقبّل الله منا ومنكم", "May it be a blessed Eid", listOf(Color(0xFF3D2C1F), Color(0xFF8C5A2B), Color(0xFFF2C14E))),
    NATIONAL_DAY("اليوم الوطني 🇸🇦", "Saudi National Day 🇸🇦", "دام عزك يا وطن", "Happy National Day", listOf(Color(0xFF00402A), Color(0xFF006C35), Color(0xFF3FBF7F))),
    FOUNDING_DAY("يوم التأسيس 🏰", "Founding Day 🏰", "يوم بدينا", "The day it all began", listOf(Color(0xFF3B2414), Color(0xFF7A4B24), Color(0xFFD9A55B))),
    ;

    companion object {
        fun current(isoToday: String = CurrentDateProvider.todayIsoDate()): LuxoSeason? {
            val parts = isoToday.take(10).split('-').mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return null
            val (year, month, day) = parts
            if (month == 9 && day in 20..24) return NATIONAL_DAY
            if (month == 2 && day in 21..23) return FOUNDING_DAY
            val (_, hMonth, hDay) = LuxoDateStyle.gregorianToHijri(year, month, day)
            return when {
                hMonth == 9 -> RAMADAN
                hMonth == 10 && hDay <= 3 -> EID_FITR
                hMonth == 12 && hDay in 9..13 -> EID_ADHA
                else -> null
            }
        }
    }
}

private const val SEASON_DISMISSED_KEY = "season_dismissed_day"

/** A decorated greeting on Home while an occasion is on. Can be closed for the day. */
@Composable
internal fun LuxoSeasonBanner(modifier: Modifier = Modifier) {
    if (!rememberLuxoFeatures().seasonalThemes) return
    val today = remember { CurrentDateProvider.todayIsoDate().take(10) }
    val season = remember(today) { LuxoSeason.current(today) } ?: return
    var dismissed by remember { mutableStateOf(LuxoPrefsStorage.load(SEASON_DISMISSED_KEY) == today) }
    if (dismissed) return
    val motion = rememberInfiniteTransition(label = "season")
    val swing by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "season_swing",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(season.colors, start = Offset.Zero, end = Offset(1400f, 400f))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (season) {
                LuxoSeason.RAMADAN -> drawRamadan(swing)
                LuxoSeason.EID_FITR, LuxoSeason.EID_ADHA -> drawSparkles(swing)
                LuxoSeason.NATIONAL_DAY, LuxoSeason.FOUNDING_DAY -> drawWaves(swing)
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp)) {
            Text(lx(season.greetingAr, season.greetingEn), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(lx(season.subtitleAr, season.subtitleEn), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
        IconButton(
            onClick = {
                LuxoPrefsStorage.save(SEASON_DISMISSED_KEY, today)
                dismissed = true
            },
            modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

private fun DrawScope.drawRamadan(t: Float) {
    // Crescent
    val moonCenter = Offset(size.width * 0.86f, size.height * 0.42f)
    drawCircle(Color(0xFFFFE6A6), radius = 22.dp.toPx(), center = moonCenter)
    drawCircle(Color(0xFF5A3690), radius = 19.dp.toPx(), center = moonCenter + Offset(9.dp.toPx(), -5.dp.toPx()))
    // Hanging lanterns that sway
    listOf(0.58f to 0.0f, 0.68f to 0.33f, 0.76f to 0.66f).forEach { (x, phase) ->
        val angle = sin((t + phase) * 2f * PI.toFloat()) * 9f
        val top = Offset(size.width * x, 0f)
        rotate(angle, pivot = top) {
            val ropeEnd = top + Offset(0f, size.height * (0.28f + phase * 0.12f))
            drawLine(Color(0xFFE8B04A).copy(alpha = 0.8f), top, ropeEnd, strokeWidth = 1.5.dp.toPx())
            val w = 16.dp.toPx()
            val h = 24.dp.toPx()
            val body = Path().apply {
                moveTo(ropeEnd.x, ropeEnd.y)
                lineTo(ropeEnd.x + w / 2, ropeEnd.y + h * 0.3f)
                lineTo(ropeEnd.x + w / 2, ropeEnd.y + h * 0.8f)
                lineTo(ropeEnd.x, ropeEnd.y + h)
                lineTo(ropeEnd.x - w / 2, ropeEnd.y + h * 0.8f)
                lineTo(ropeEnd.x - w / 2, ropeEnd.y + h * 0.3f)
                close()
            }
            drawPath(body, Color(0xFFE8B04A))
            drawCircle(Color(0xFFFFF4C2).copy(alpha = 0.9f), radius = 4.dp.toPx(), center = ropeEnd + Offset(0f, h * 0.55f))
        }
    }
}

private fun DrawScope.drawSparkles(t: Float) {
    val points = listOf(0.55f to 0.25f, 0.65f to 0.7f, 0.74f to 0.35f, 0.82f to 0.62f, 0.9f to 0.2f, 0.95f to 0.8f, 0.6f to 0.5f)
    points.forEachIndexed { index, (x, y) ->
        val pulse = (sin((t + index / 7f) * 2f * PI.toFloat()) + 1f) / 2f
        val r = (2f + pulse * 4f).dp.toPx()
        val c = Offset(size.width * x, size.height * y)
        drawCircle(Color.White.copy(alpha = 0.35f + pulse * 0.5f), radius = r, center = c)
        drawLine(Color.White.copy(alpha = pulse * 0.6f), c - Offset(r * 2, 0f), c + Offset(r * 2, 0f), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = pulse * 0.6f), c - Offset(0f, r * 2), c + Offset(0f, r * 2), strokeWidth = 1f)
    }
}

private fun DrawScope.drawWaves(t: Float) {
    for (band in 0 until 3) {
        val path = Path()
        val baseY = size.height * (0.55f + band * 0.14f)
        path.moveTo(size.width * 0.45f, size.height)
        var x = size.width * 0.45f
        while (x <= size.width) {
            val y = baseY + sin((x / size.width * 4f + t * 2f + band) * PI.toFloat()) * 6.dp.toPx()
            path.lineTo(x, y)
            x += 8f
        }
        path.lineTo(size.width, size.height)
        path.close()
        drawPath(path, Color.White.copy(alpha = 0.07f + band * 0.04f))
    }
    drawRect(Color.Transparent, size = Size(0f, 0f))
}

package com.nuvio.app.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.luxo.LuxoUserData
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressRepository

private class LuxoBadge(val emoji: String, val title: String, val hint: String, val unlocked: Boolean)

@Composable
private fun luxoBadges(stats: LuxoPassStats): List<LuxoBadge> {
    val userData by LuxoUserData.state.collectAsStateWithLifecycle()
    val discovered = userData.lists.firstOrNull { it.name == "من طابور الاستكشاف" }?.items?.size ?: 0
    val total = stats.movies + stats.episodes
    val (challengeDone, challengeGoal) = rememberChallengeProgress()
    return listOf(
        LuxoBadge("🎯", lx("بطل الشهر", "Monthly champ"), lx("خلّص تحدي الشهر", "Finish the monthly challenge"), challengeGoal > 0 && challengeDone >= challengeGoal),
        LuxoBadge("🎟️", lx("أول تذكرة", "First ticket"), lx("أول عمل", "First title"), total >= 1),
        LuxoBadge("🎬", lx("عشرة أفلام", "Ten films"), lx("10 أفلام", "10 movies"), stats.movies >= 10),
        LuxoBadge("🍿", lx("سينمائي", "Cinephile"), lx("50 فيلم", "50 movies"), stats.movies >= 50),
        LuxoBadge("🏆", lx("أسطورة السينما", "Film legend"), lx("100 فيلم", "100 movies"), stats.movies >= 100),
        LuxoBadge("📺", lx("حلقة ورا حلقة", "One more episode"), lx("100 حلقة", "100 episodes"), stats.episodes >= 100),
        LuxoBadge("🛋️", lx("ماراثون", "Marathoner"), lx("500 حلقة", "500 episodes"), stats.episodes >= 500),
        LuxoBadge("⏳", lx("مئة ساعة", "100 hours"), lx("100 ساعة تقريباً", "About 100 hours"), stats.hours >= 100),
        LuxoBadge("🔥", lx("أسبوع متواصل", "Week streak"), lx("7 أيام ورا بعض", "7 days in a row"), stats.streakDays >= 7),
        LuxoBadge("🌋", lx("شهر متواصل", "Month streak"), lx("30 يوم ورا بعض", "30 days in a row"), stats.streakDays >= 30),
        LuxoBadge("🌙", lx("سهران", "Night owl"), lx("مشاهدة بعد منتصف الليل", "Watching after midnight"), stats.nightShare >= 0.25f && total >= 10),
        LuxoBadge("⭐", lx("ناقد", "Critic"), lx("10 تقييمات", "10 ratings"), stats.ratings >= 10),
        LuxoBadge("🗂️", lx("منظّم", "Curator"), lx("3 قوائم", "3 lists"), stats.lists >= 3),
        LuxoBadge("🧭", lx("مستكشف", "Explorer"), lx("10 من طابور الاستكشاف", "10 from the discovery queue"), discovered >= 10),
    )
}

/** Badges unlocked by watching; locked ones show faded with their goal. */
@Composable
internal fun LuxoAchievementsCard(isTablet: Boolean) {
    if (!rememberLuxoFeatures().achievements) return
    val stats = rememberLuxoPassStats()
    val badges = luxoBadges(stats)
    val tokens = MaterialTheme.nuvio
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 0.dp else 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(tokens.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lx("الأوسمة 🏅", "Badges 🏅"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${badges.count { it.unlocked }}/${badges.size}", color = tokens.colors.textMuted, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            badges.sortedByDescending { it.unlocked }.forEach { badge ->
                Column(
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (badge.unlocked) tokens.colors.accent.copy(alpha = 0.14f) else tokens.colors.background.copy(alpha = 0.5f))
                        .border(1.dp, if (badge.unlocked) tokens.colors.accent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                        .padding(10.dp)
                        .alpha(if (badge.unlocked) 1f else 0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (badge.unlocked) badge.emoji else "🔒", fontSize = 28.sp)
                    Text(badge.title, color = tokens.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                    Text(badge.hint, color = tokens.colors.textMuted, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

private const val HEATMAP_DAY_MS = 24L * 60 * 60 * 1000

/** A GitHub-style map of the last 26 weeks: darker squares mean more watching that day. */
@Composable
internal fun LuxoWatchHeatmapCard(isTablet: Boolean) {
    if (!rememberLuxoFeatures().heatmap) return
    val watched by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val progress by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val today = remember { isoToEpochDay(CurrentDateProvider.todayIsoDate()) }
    val counts = remember(watched.items, progress.entries) {
        (watched.items.map { it.markedAtEpochMs } + progress.entries.map { it.lastUpdatedEpochMs })
            .filter { it > 0 }
            .groupingBy { it / HEATMAP_DAY_MS }
            .eachCount()
    }
    val weeks = 26
    val maxCount = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val activeDays = counts.keys.count { it > today - weeks * 7 }
    val tokens = MaterialTheme.nuvio
    val accent = tokens.colors.accent
    val empty = Color.White.copy(alpha = 0.06f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 0.dp else 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(tokens.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lx("خريطة المشاهدة", "Watch map"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(lx("$activeDays يوم مشاهدة", "$activeDays active days"), color = tokens.colors.textMuted, style = MaterialTheme.typography.labelLarge)
        }
        Canvas(Modifier.fillMaxWidth().height(if (isTablet) 150.dp else 112.dp)) {
            val gap = 3.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            val startDay = today - (weeks * 7 - 1)
            for (i in 0 until weeks * 7) {
                val day = startDay + i
                val col = i / 7
                val row = i % 7
                val count = counts[day] ?: 0
                val level = if (count == 0) 0f else 0.25f + 0.75f * (count.toFloat() / maxCount)
                // Oldest week on the start side, matching reading direction.
                val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    size.width - (col + 1) * (cell + gap) + gap
                } else {
                    col * (cell + gap)
                }
                drawRoundRect(
                    color = if (count == 0) empty else accent.copy(alpha = level),
                    topLeft = Offset(x, row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.25f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lx("أقل", "Less"), color = tokens.colors.textMuted, fontSize = 10.sp)
            listOf(0f, 0.3f, 0.55f, 0.8f, 1f).forEach { a ->
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(if (a == 0f) empty else accent.copy(alpha = a)))
            }
            Text(lx("أكثر", "More"), color = tokens.colors.textMuted, fontSize = 10.sp)
        }
    }
}

private fun isoToEpochDay(iso: String): Long {
    val parts = iso.take(10).split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return 0
    val (y, m, d) = parts
    val a = (14 - m) / 12
    val yy = y + 4800 - a
    val mm = m + 12 * a - 3
    val jdn = d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    return jdn - 2440588L
}

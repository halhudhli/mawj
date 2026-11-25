@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Account
import com.mawj.tv.data.Library
import com.mawj.tv.data.Store
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

/** Membership by hours watched. */
private val TIERS = listOf("برونزية" to 0, "فضية" to 50, "ذهبية" to 200, "ماسية" to 500)

/** English genre names from the addons, in Arabic for the profile. */
private val GENRE_AR = mapOf(
    "Drama" to "الدراما", "Comedy" to "الكوميديا", "Action" to "الأكشن", "Thriller" to "الإثارة", "Crime" to "الجريمة",
    "Horror" to "الرعب", "Romance" to "الرومانسية", "Animation" to "الرسوم المتحركة", "Sci-Fi" to "الخيال العلمي",
    "Science Fiction" to "الخيال العلمي", "Mystery" to "الغموض", "Adventure" to "المغامرة", "Fantasy" to "الفانتازيا",
    "Documentary" to "الوثائقيات", "Family" to "العائلي", "History" to "التاريخ", "War" to "الحرب",
)

fun tasteTitle(): String {
    val genre = Library.titles.values.flatMap { w -> w.meta.genres.map { it to w.minutes } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.maxByOrNull { it.value }?.key
    val g = genre?.let { GENRE_AR[it] ?: it } ?: "الأعمال"
    val time = when (Library.peakHour) { null -> ""; in 0..4, 22, 23 -> " الليلي"; in 5..11 -> " الصباحي"; in 12..17 -> " النهاري"; else -> " المسائي" }
    return "ذوّاق $g$time"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen() {
    val hours = Library.totalMinutes / 60
    val tier = TIERS.last { hours >= it.second }
    val next = TIERS.firstOrNull { it.second > hours }
    val series = Library.titles.values.count { it.meta.type == "series" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 30.dp, bottom = 50.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageHead("الملف", Account.email?.let { "حساب Nuvio مرتبط" } ?: "على هذه الشاشة", Modifier.weight(1f))
                Pill("الإعدادات", modifier = restoreFirst()) { Nav.tab(Route.Settings) }
                Pill("السنة في موج") { Nav.go(Route.Tool("wrapped")) }
                if (Store.feature("privateMode")) Tag("الوضع الخاص مفعّل", gold = true)
            }
        }
        item {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    T("إجمالي المشاهدة", 13.sp, color = C.Muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        T("$hours", 46.sp, weight = FontWeight.Bold, num = true)
                        T(" ساعة", 22.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    T(if (next != null) "بقي ${next.second - hours} ساعة للوصول إلى العضوية ${next.first}" else "أعلى عضوية: الماسية", 12.sp, color = C.Muted)
                    val frac = if (next == null) 1f else (hours - tier.second).toFloat() / (next.second - tier.second)
                    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(6.dp).background(Color(0x22FFFFFF), CircleShape)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(6.dp).background(Brush.horizontalGradient(listOf(Color(0xFF5FB3A6), C.GoldLight)), CircleShape))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIERS.forEach { (n, _) ->
                            T(n, 12.sp, color = if (n == tier.first) C.GoldLight else C.Muted,
                                modifier = Modifier.weight(1f).background(if (n == tier.first) Color(0xFF6E5524) else Color(0xCC111A24), RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
                                align = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("${Library.finishedEpisodes}", "حلقة", Modifier.weight(1f))
                        StatBox("${Library.finishedMovies}", "فيلماً", Modifier.weight(1f))
                        StatBox("$series", "مسلسلاً", Modifier.weight(1f))
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("${Store.progress.size}", "قيد المشاهدة", Modifier.weight(1f))
                        StatBox("${Library.lists.values.sumOf { it.size }}", "محفوظ", Modifier.weight(1f))
                        StatBox("${Library.streak}", "يوماً متتالياً", Modifier.weight(1f))
                    }
                }
                MemberCard(tier.first, hours, series)
            }
        }
        item {
            T("لوحة الذوق", 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PeakClock(Modifier.weight(1f))
                Column(
                    Modifier.weight(1.5f).height(150.dp).background(Brush.linearGradient(listOf(Color(0xFF4D3C1C), Color(0xFF2A2416))), RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    T("اللقب", 12.sp, color = C.Muted)
                    T(tasteTitle() + " ☾", 24.sp, weight = FontWeight.Bold, color = C.GoldLight, maxLines = 1)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        listOf("${Library.titles.size}" to "عمل", "${Library.finishedMovies}" to "فيلم", "$series" to "مسلسل", "${Library.lastDays(30).sumOf { it.second } / 60}" to "ساعة هذا الشهر").forEach { (v, l) ->
                            Column { T(v, 20.sp, weight = FontWeight.Bold, num = true); T(l, 11.sp, color = C.Muted) }
                        }
                    }
                }
                DecadeBars(Modifier.weight(1f))
            }
        }
        item { Heatmap() }
        item { MonthGoal() }
        item { Badges() }
    }
}

/** The membership card: still (no tilt, no moving shine), with the sea at its foot. */
@Composable
private fun MemberCard(tier: String, hours: Int, series: Int) {
    Box(
        Modifier.size(360.dp, 210.dp).background(Brush.linearGradient(listOf(Color(0xFF3C3A2E), Color(0xFF1D2A3A), Color(0xFF2C4A6A))), RoundedCornerShape(22.dp))
            .border(1.dp, Color(0x59F1DDA6), RoundedCornerShape(22.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.72f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y); cubicTo(size.width * 0.25f, y - 30f, size.width * 0.5f, y + 30f, size.width * 0.75f, y)
                cubicTo(size.width * 0.88f, y - 15f, size.width, y + 5f, size.width, y); lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFF2C5D8A), Color(0xFF16324D)), startY = y - 30f, endY = size.height))
        }
        Column(Modifier.padding(18.dp)) {
            T(tier, 13.sp, color = C.GoldLight)
            Box(Modifier.padding(top = 10.dp).size(46.dp).background(Brush.radialGradient(listOf(Color(0xFF7A6AA8), Color(0xFF2B2340))), CircleShape), contentAlignment = Alignment.Center) {
                T(Account.email?.firstOrNull()?.uppercase() ?: "م", 20.sp, weight = FontWeight.Bold)
            }
            T("ملفي", 22.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            T(tasteTitle(), 12.sp, color = Color(0xFFD8DDE2))
        }
        T("%04d · %04d · %04d · %04d".format(hours, series, Library.finishedMovies, Library.finishedEpisodes), 15.sp, num = true, color = Color(0xFFE8EEF4),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

/** When the watching happens: a ray for every hour, longer for more minutes; the peak hour in the middle. */
@Composable
private fun PeakClock(modifier: Modifier) {
    val max = Library.hours.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(modifier.height(150.dp).background(Color(0xCC111A24), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(110.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            for (h in 0 until 24) {
                val a = h / 24.0 * 2 * Math.PI - Math.PI / 2
                val len = size.width * 0.12f + size.width * 0.3f * Library.hours[h] / max
                val r0 = size.width * 0.16f
                drawLine(C.Gold.copy(alpha = 0.35f + 0.65f * Library.hours[h] / max), Offset(c.x + (cos(a) * r0).toFloat(), c.y + (sin(a) * r0).toFloat()),
                    Offset(c.x + (cos(a) * (r0 + len)).toFloat(), c.y + (sin(a) * (r0 + len)).toFloat()), strokeWidth = 5f, cap = StrokeCap.Round)
            }
            drawCircle(C.Sea, size.width * 0.14f, c)
        }
        Column(Modifier.padding(start = 12.dp)) {
            T("ذروة المشاهدة", 12.sp, color = C.Muted)
            val h = Library.peakHour
            T(h?.let { "%d:00".format(if (it % 12 == 0) 12 else it % 12) } ?: "—", 26.sp, weight = FontWeight.Bold, num = true)
            T(h?.let { if (it < 5 || it >= 18) "ليلاً" else if (it < 12) "صباحاً" else "ظهراً" } ?: "", 12.sp, color = C.Muted)
        }
    }
}

/** How many of the titles watched came from each decade. */
@Composable
private fun DecadeBars(modifier: Modifier) {
    val counts = Library.titles.values.mapNotNull { it.meta.year?.take(4)?.toIntOrNull()?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount()
    val decades = listOf(1980, 1990, 2000, 2010, 2020)
    val max = (decades.maxOfOrNull { counts[it] ?: 0 } ?: 0).coerceAtLeast(1)
    Row(modifier.height(150.dp).background(Color(0xCC111A24), RoundedCornerShape(18.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        decades.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(20.dp).height((6 + 90 * (counts[d] ?: 0) / max).dp).background(Brush.verticalGradient(listOf(C.GoldLight, Color(0xFF8E7340))), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                T("${d % 100}s", 10.sp, color = C.Muted, num = true, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** «خريطة المشاهدة»: the last 15 weeks, a square per day, brighter for more watching. */
@Composable
private fun Heatmap() {
    val days = Library.lastDays(105)
    val max = days.maxOf { it.second }.coerceAtLeast(1)
    Glass(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("خريطة المشاهدة", 17.sp, weight = FontWeight.Bold)
            T("  آخر 15 أسبوعاً · ${days.count { it.second > 0 }} يوم مشاهدة", 12.sp, color = C.Muted)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.chunked(7).forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { (_, m) ->
                        Box(Modifier.size(13.dp).background(if (m == 0) Color(0x1AFFFFFF) else C.Gold.copy(alpha = 0.25f + 0.75f * m / max), RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
    }
}

/** «هدف الشهر»: hours this month against a goal set with the remote. */
@Composable
private fun MonthGoal() {
    val goal = Store.number("goalHours", 20)
    val now = LocalDate.now()
    val month = Library.days.filterKeys { it.startsWith("%04d-%02d".format(now.year, now.monthValue)) }.values.sum() / 60
    Glass(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T("هدف هذا الشهر", 17.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Chip("−", false, onClick = { Store.setNumber("goalHours", (goal - 5).coerceAtLeast(5)) })
            T("$goal ساعة", 16.sp, num = true)
            Chip("＋", false, onClick = { Store.setNumber("goalHours", goal + 5) })
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).background(Color(0x22FFFFFF), CircleShape)) {
            Box(Modifier.fillMaxWidth((month.toFloat() / goal).coerceIn(0.01f, 1f)).height(8.dp).background(C.Gold, CircleShape))
        }
        T(if (month >= goal) "تحقق الهدف هذا الشهر." else "$month من $goal ساعة", 12.sp, color = C.Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

/** «الأوسمة»: earned ones in gold, the rest dimmed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges() {
    val h = Library.totalMinutes / 60
    val genres = Library.titles.values.flatMap { it.meta.genres }.distinct().size
    val list = listOf(
        "أول مشاهدة" to (Library.titles.isNotEmpty()),
        "10 ساعات" to (h >= 10), "100 ساعة" to (h >= 100), "500 ساعة" to (h >= 500),
        "ليلي" to (Library.peakHour?.let { it < 4 } == true),
        "أسبوع متواصل" to (Library.streak >= 7),
        "10 أفلام" to (Library.finishedMovies >= 10), "50 حلقة" to (Library.finishedEpisodes >= 50),
        "متعدد الأذواق" to (genres >= 6), "جامع القوائم" to (Library.lists.values.sumOf { it.size } >= 20),
        "متابع للنجوم" to (Library.followed.size >= 3),
    )
    Glass(Modifier.padding(top = 16.dp)) {
        T("الأوسمة · ${list.count { it.second }} من ${list.size}", 17.sp, weight = FontWeight.Bold)
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { (name, got) -> Tag(if (got) "★ $name" else name, gold = got) }
        }
    }
}

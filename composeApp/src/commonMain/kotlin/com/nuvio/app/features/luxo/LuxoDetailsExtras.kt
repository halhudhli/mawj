package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val X1 = Color(0xFF8C7BFF)
private val X2 = Color(0xFFFF7EB6)

@Composable
private fun XChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun XDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(max = 720.dp).padding(16.dp)
                .clip(RoundedCornerShape(26.dp)).background(Color(0xFF14121C)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp)) { Text("✕", color = Color.White) }
            }
            content()
        }
    }
}

/** Where the series stands: the furthest watched (season, episode) for this title, or null. */
@Composable
private fun rememberWatchedEpisodes(metaId: String): List<Pair<Int, Int>> {
    val state by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    return remember(state.items, metaId) {
        state.items.filter { it.id == metaId && (it.season ?: 0) > 0 && it.episode != null }
            .map { it.season!! to it.episode!! }.distinct()
    }
}

// ---- «⏳ الوقت المتبقي وخطة الإنهاء» ----

private data class TimeLeft(val left: Int, val total: Int, val minutes: Int)

@Composable
fun LuxoTimeLeftChip(metaId: String, tmdbId: String?, mediaType: String?) {
    if (!rememberLuxoFeatures().timeLeft || tmdbId == null || mediaType != "tv") return
    val watchedEps = rememberWatchedEpisodes(metaId)
    var info by remember { mutableStateOf<TimeLeft?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(tmdbId, watchedEps.size) {
        val o = LuxoTmdbLite.get("tv/$tmdbId") ?: return@LaunchedEffect
        val seasons = o.a("seasons").filter { (it.i("season_number") ?: 0) > 0 }
        val last = o.o("last_episode_to_air")
        val lastS = last?.i("season_number")
        val lastE = last?.i("episode_number")
        val aired = if (lastS != null && lastE != null) {
            seasons.filter { (it.i("season_number") ?: 0) < lastS }.sumOf { it.i("episode_count") ?: 0 } + lastE
        } else {
            seasons.sumOf { it.i("episode_count") ?: 0 }
        }
        val runtime = (o["episode_run_time"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
            ?: last?.i("runtime") ?: 45
        val left = (aired - watchedEps.size).coerceAtLeast(0)
        info = TimeLeft(left, aired, left * runtime)
    }
    val t = info ?: return
    if (t.total == 0) return
    val label = if (t.left == 0) "✔️ تمت مشاهدة كل الحلقات المعروضة" else "⏳ المتبقي ${t.left} حلقة · ${hm(t.minutes)}"
    XChip(label) { if (t.left > 0) open = true }
    if (open) {
        XDialog("⏳ خطة الإنهاء", { open = false }) {
            var perDay by remember { mutableIntStateOf(2) }
            Text("تمت مشاهدة ${t.total - t.left} من ${t.total} حلقة · المتبقي ${hm(t.minutes)}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f))) {
                Box(
                    Modifier.fillMaxWidth(((t.total - t.left).toFloat() / t.total).coerceIn(0.02f, 1f)).padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(X1, X2))),
                )
            }
            Text("عدد الحلقات يومياً", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 5, 8).forEach { n ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (perDay == n) Brush.horizontalGradient(listOf(X1, X2)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
                            .clickable { perDay = n }.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("$n", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            val days = (t.left + perDay - 1) / perDay
            val finish = runCatching {
                LocalDate.parse(com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate().take(10)).plus(DatePeriod(days = days))
            }.getOrNull()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(X1.copy(alpha = 0.22f), X2.copy(alpha = 0.16f)))).padding(16.dp),
            ) {
                Text("يُنتهى منه خلال $days يوم", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                finish?.let { Text("بتاريخ ${it.dayOfMonth}/${it.month.ordinal + 1}/${it.year}", color = Color.White.copy(alpha = 0.8f)) }
                Text("بمعدل ${hm(perDay * (t.minutes / t.left.coerceAtLeast(1)))} يومياً", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
    }
}

private fun hm(minutes: Int): String = when {
    minutes < 60 -> "$minutes د"
    minutes % 60 == 0 -> "${minutes / 60} س"
    else -> "${minutes / 60} س ${minutes % 60} د"
}

// ---- «👥 دليل الشخصيات بدون حرق» ----

private data class GuideCharacter(val name: String, val actor: String, val photo: String?, val about: String)

private val guideCache = mutableMapOf<String, List<GuideCharacter>>()

@Composable
fun LuxoCharacterGuideChip(metaId: String, title: String, tmdbId: String?, mediaType: String?, enTitle: String?, year: String?) {
    if (!rememberLuxoFeatures().characterGuide || tmdbId == null) return
    var open by remember { mutableStateOf(false) }
    XChip("👥 دليل الشخصيات") { open = true }
    if (!open) return
    val watchedEps = rememberWatchedEpisodes(metaId)
    val point = watchedEps.maxWithOrNull(compareBy({ it.first }, { it.second }))
    val cacheKey = "$tmdbId:${point?.first}:${point?.second}"
    var list by remember(cacheKey) { mutableStateOf(guideCache[cacheKey]) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cacheKey) {
        if (list != null) return@LaunchedEffect
        val isTv = mediaType == "tv"
        val credits = LuxoTmdbLite.get(if (isTv) "tv/$tmdbId/aggregate_credits" else "movie/$tmdbId/credits") ?: run { error = "تعذّر تحميل طاقم العمل"; return@LaunchedEffect }
        val cast = credits.a("cast").take(16).mapNotNull { c ->
            val character = if (isTv) c.a("roles").firstOrNull()?.s("character") else c.s("character")
            val actor = c.s("name") ?: return@mapNotNull null
            if (character.isNullOrBlank()) null else Triple(character, actor, LuxoTmdbLite.img(c.s("profile_path"), "w185"))
        }
        if (cast.isEmpty()) { error = "لا تتوفر شخصيات لهذا العمل"; return@LaunchedEffect }
        val key = LuxoCompanionApi.loadKey()
        if (key == null) {
            list = cast.map { GuideCharacter(it.first, it.second, it.third, "") }
            return@LaunchedEffect
        }
        val limit = when {
            !isTv -> "اكتفِ بما يُعرف عن الشخصية في أول ربع ساعة من الفيلم فقط."
            point == null -> "لم تبدأ المشاهدة بعد: اكتفِ بما يُعرف في الحلقة الأولى فقط."
            else -> "تمت المشاهدة حتى الموسم ${point.first} الحلقة ${point.second}: لا تذكر أي شيء يحدث بعد هذه النقطة."
        }
        val prompt = "دليل شخصيات بدون حرق لعمل «${enTitle ?: title}» (${year ?: ""}). $limit\n" +
            "لكل شخصية من القائمة: وصف من سطر واحد بالعربية (من هي وعلاقتها بالبقية) بدون أي تطورات أو مصائر أو مفاجآت. " +
            "إن لم تكن الشخصية قد ظهرت حتى تلك النقطة فاكتب \"\".\n" +
            cast.mapIndexed { i, c -> "${i + 1}. ${c.first} (${c.second})" }.joinToString("\n") +
            "\nأعد JSON فقط: {\"about\":[\"...\"]} بنفس الترتيب والعدد."
        val obj = LuxoTmdbLite.jsonBlock(LuxoCompanionApi.ask(key, prompt))
        val about = (obj?.get("about") as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        val result = cast.mapIndexedNotNull { i, c ->
            val text = about?.getOrNull(i)
            if (about != null && text.isNullOrBlank()) null else GuideCharacter(c.first, c.second, c.third, text.orEmpty())
        }
        guideCache[cacheKey] = result
        list = result
    }
    XDialog("👥 دليل الشخصيات", { open = false }) {
        Text(
            when {
                mediaType != "tv" -> "بدون حرق: ما يُعرف عن الشخصيات في بداية الفيلم"
                point == null -> "بدون حرق: حتى الحلقة الأولى"
                else -> "بدون حرق: حتى الموسم ${point.first} · الحلقة ${point.second}"
            },
            color = X2,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        val l = list
        when {
            error != null -> Text(error!!, color = Color(0xFFFFB4A9))
            l == null -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = X1) }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(l) { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                            if (c.photo != null) AsyncImage(c.photo, c.actor, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("يؤديها ${c.actor}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                            if (c.about.isNotBlank()) Text(c.about, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
        }
    }
}


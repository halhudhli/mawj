package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

// ---------------------------------------------------------------- platform hooks

/** Google TV «أكمل المشاهدة» row (Watch Next). Entries: id, title, subtitle, poster, deep link, positionMs, durationMs, isEpisode, lastMs. */
data class LuxoWatchNextEntry(
    val id: String, val title: String, val subtitle: String?, val poster: String?, val deepLink: String,
    val positionMs: Long, val durationMs: Long, val isEpisode: Boolean, val lastMs: Long,
)

expect fun luxoPublishWatchNext(entries: List<LuxoWatchNextEntry>)

/** Asks the paired TV what it is playing; returns its JSON or null. */
expect suspend fun luxoRemoteQuery(host: String, port: Int, code: String): String?

/** Sets [jpeg] as the device wallpaper. */
expect suspend fun luxoSetWallpaper(jpeg: ByteArray): Boolean

// ---------------------------------------------------------------- now playing (TV side of the second screen)

object LuxoNowPlaying {
    @kotlin.concurrent.Volatile var json: String = "{}"

    fun update(metaId: String, metaType: String, title: String, season: Int?, episode: Int?, positionMs: Long, durationMs: Long, playing: Boolean) {
        json = buildJsonObject {
            put("metaId", metaId); put("metaType", metaType); put("title", title)
            season?.let { put("season", it) }; episode?.let { put("episode", it) }
            put("pos", positionMs); put("dur", durationMs); put("playing", playing)
        }.toString()
    }

    fun clear() { json = "{}" }
}

// ---------------------------------------------------------------- hosts

/** Keeps the widget's next-episode line and the Google TV row up to date. */
@Composable
fun LuxoDeviceSync() {
    val f = rememberLuxoFeatures()
    // Google TV Watch Next.
    if (luxoIsTv()) {
        val progress by remember { WatchProgressRepository.ensureLoaded(); WatchProgressRepository.uiState }.collectAsState()
        LaunchedEffect(f.googleTvRow, progress.entries) {
            if (!f.googleTvRow) { runCatching { luxoPublishWatchNext(emptyList()) }; return@LaunchedEffect }
            delay(4_000L)
            val entries = progress.entries.filter { !it.isCompleted && it.durationMs > 0 && it.parentMetaId !in progress.hiddenContentIds }
                .groupBy { it.parentMetaId }.mapNotNull { (_, es) -> es.maxByOrNull { it.lastUpdatedEpochMs } }
                .sortedByDescending { it.lastUpdatedEpochMs }.take(8)
                .map { e ->
                    LuxoWatchNextEntry(
                        id = e.parentMetaId, title = e.title,
                        subtitle = if (e.seasonNumber != null) "الموسم ${e.seasonNumber} · الحلقة ${e.episodeNumber}" else null,
                        poster = e.background ?: e.poster,
                        deepLink = buildMetaDeepLinkUrl(if (e.parentMetaType == "movie") "movie" else "series", e.parentMetaId),
                        positionMs = e.lastPositionMs, durationMs = e.durationMs, isEpisode = e.seasonNumber != null, lastMs = e.lastUpdatedEpochMs,
                    )
                }
            runCatching { luxoPublishWatchNext(entries) }
        }
    }
    // Widget: nearest upcoming episode.
    if (!luxoIsTv()) {
        val upcoming by remember { com.nuvio.app.features.library.libraryUpcomingEpisodesFlow(days = 30) }.collectAsState(initial = emptyList())
        LaunchedEffect(f.widgetNext, upcoming) {
            if (!f.widgetNext) { luxoPublishWidgetNext(null); return@LaunchedEffect }
            val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val next = upcoming.mapNotNull { u -> runCatching { LocalDate.parse(u.dateIso.take(10)) }.getOrNull()?.let { u to it } }
                .filter { it.second >= today }.minByOrNull { it.second }
            val line = next?.let { (u, d) ->
                val days = (d.toEpochDays() - today.toEpochDays()).toLong()
                val whenText = when (days) { 0L -> "اليوم"; 1L -> "غداً"; 2L -> "بعد يومين"; in 3L..10L -> "بعد $days أيام"; else -> "بعد $days يوماً" }
                "⏳ ${u.item.name}" + (u.episodeNumber?.let { " · الحلقة $it" } ?: "") + " — $whenText"
            }
            luxoPublishWidgetNext(line)
        }
    }
}

// ---------------------------------------------------------------- 📱 «الشاشة الثانية» (tablet)

private data class Remote(val title: String, val metaId: String, val metaType: String, val season: Int?, val episode: Int?, val pos: Long, val dur: Long, val playing: Boolean)

private fun parseRemote(s: String): Remote? = runCatching {
    val o = Json.parseToJsonElement(s) as? JsonObject ?: return null
    fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    fun int(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
    fun lng(k: String) = (o[k] as? JsonPrimitive)?.longOrNull ?: 0L
    val id = str("metaId") ?: return null
    Remote(str("title").orEmpty(), id, str("metaType") ?: "series", int("season"), int("episode"), lng("pos"), lng("dur"), str("playing") == "true")
}.getOrNull()

private fun fmtTime(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return (if (h > 0) "$h:" + m.toString().padStart(2, '0') else m.toString()) + ":" + sec.toString().padStart(2, '0')
}

@Composable
fun LuxoSecondScreenDialog(onDismiss: () -> Unit) {
    val target = LuxoRemote.target?.split('|')
    var now by remember { mutableStateOf<Remote?>(null) }
    var reached by remember { mutableStateOf<Boolean?>(null) }
    var cast by remember { mutableStateOf<List<String>>(emptyList()) }
    var facts by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(target) {
        if (target == null || target.size < 3) return@LaunchedEffect
        while (true) {
            val r = luxoRemoteQuery(target[0], target[1].toIntOrNull() ?: LuxoRemoteNet.port, target[2])
            reached = r != null
            now = r?.let(::parseRemote)
            delay(5_000L)
        }
    }
    val key = now?.let { "${it.metaId}:${it.season}:${it.episode}" }
    LaunchedEffect(key) {
        val n = now ?: return@LaunchedEffect
        cast = emptyList(); facts = null
        cast = runCatching { LuxoWhoInScene.castLines(n.metaId, n.metaType, n.season, n.episode) }.getOrDefault(emptyList()).take(14)
        val k = LuxoCompanionApi.loadKey()
        facts = if (k == null) "" else LuxoCompanionApi.ask(
            k,
            "اذكر خمس معلومات قصيرة ممتعة من خلف كواليس «${n.title}»" +
                (if (n.season != null) " (الموسم ${n.season}، الحلقة ${n.episode})" else "") +
                "، كل معلومة في سطر يبدأ بـ «• »، بلا أي حرق لما بعد هذه الحلقة. بالعربية الفصحى وبلا Markdown.",
            search = true,
        )
    }
    IDialog("📱 الشاشة الثانية", onDismiss) {
        if (target == null || target.size < 3) {
            Text("يُربط التابلت بالشاشة أولاً من قاعة «التحكم بالشاشة»، ثم تُفتح هذه الصفحة أثناء المشاهدة على التلفزيون.", color = Color.White.copy(alpha = 0.75f))
            return@IDialog
        }
        Text("متصل بـ: " + target.getOrElse(3) { target[0] }, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        val n = now
        when {
            reached == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text("يُتصل بالشاشة…", color = Color.White.copy(alpha = 0.7f))
            }
            reached == false -> Text("تعذّر الوصول إلى الشاشة. يُتأكد من أن موج مفتوح على التلفزيون وأن الجهازين على الشبكة نفسها.", color = Color.White.copy(alpha = 0.75f))
            n == null -> Text("لا يُعرض شيء على الشاشة الآن.", color = Color.White.copy(alpha = 0.75f))
            else -> Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(n.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                if (n.season != null) Text("الموسم ${n.season} · الحلقة ${n.episode}", color = Color.White.copy(alpha = 0.7f))
                if (n.dur > 0) {
                    LinearProgressIndicator(progress = { (n.pos.toFloat() / n.dur).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${fmtTime(n.pos)} / ${fmtTime(n.dur)}" + if (!n.playing) " · متوقف مؤقتاً" else "", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                if (cast.isNotEmpty()) {
                    Text("👥 في الحلقة", color = Color.White, fontWeight = FontWeight.Bold)
                    cast.forEach { Text("• $it", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) }
                }
                Text("💡 هل تعلم؟", color = Color.White, fontWeight = FontWeight.Bold)
                when (val fa = facts) {
                    null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    "" -> Text("يلزم مفتاح Gemini لعرض المعلومات.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    else -> Text(fa, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

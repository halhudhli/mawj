package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ================================================================ «خريطة تقييم الحلقات»

private data class EpCell(val season: Int, val episode: Int, val name: String, val rating: Double?)

private fun ratingColor(r: Double?): Color = when {
    r == null || r <= 0.0 -> Color.White.copy(alpha = 0.08f)
    r >= 9.0 -> Color(0xFF1B7F4B)
    r >= 8.0 -> Color(0xFF2FA86A)
    r >= 7.0 -> Color(0xFFC9A227)
    r >= 6.0 -> Color(0xFFD9772B)
    else -> Color(0xFFC0392B)
}

/** A chip on series pages that opens a grid of every episode coloured by its rating. */
@Composable
fun LuxoEpisodeMapChip(title: String, tmdbId: String?, mediaType: String?) {
    if (!rememberLuxoFeatures().episodeMap || tmdbId == null || mediaType != "tv") return
    var open by remember { mutableStateOf(false) }
    IChip("🟩 خريطة تقييم الحلقات") { open = true }
    if (open) LuxoEpisodeMapDialog(title, tmdbId) { open = false }
}

@Composable
private fun LuxoEpisodeMapDialog(title: String, tmdbId: String, onDismiss: () -> Unit) {
    var seasons by remember { mutableStateOf<List<List<EpCell>>?>(null) }
    var picked by remember { mutableStateOf<EpCell?>(null) }
    LaunchedEffect(tmdbId) {
        val show = LuxoTmdbLite.get("tv/$tmdbId", mapOf("language" to "ar"))
        val numbers = show?.a("seasons").orEmpty().mapNotNull { it.i("season_number") }.filter { it > 0 }.sorted().take(40)
        seasons = numbers.mapNotNull { n ->
            val season = LuxoTmdbLite.get("tv/$tmdbId/season/$n", mapOf("language" to "ar")) ?: return@mapNotNull null
            season.a("episodes").mapNotNull { e ->
                val ep = e.i("episode_number") ?: return@mapNotNull null
                val votes = e.i("vote_count") ?: 0
                EpCell(n, ep, e.s("name").orEmpty(), e.d("vote_average")?.takeIf { votes > 0 })
            }.takeIf { it.isNotEmpty() }
        }
    }
    IDialog("🟩 خريطة تقييم الحلقات", onDismiss) {
        Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        val data = seasons
        when {
            data == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            data.isEmpty() -> Text("لا تتوفر تقييمات الحلقات لهذا العمل", color = Color.White.copy(alpha = 0.7f))
            else -> {
                val all = data.flatten().mapNotNull { it.rating }
                if (all.isNotEmpty()) {
                    val best = data.flatten().filter { it.rating != null }.maxBy { it.rating!! }
                    Text(
                        "متوسط الحلقات ${fmt1(all.average())} · الأعلى: م${best.season} ح${best.episode} (${fmt1(best.rating!!)})",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                    )
                }
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    data.forEach { season ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("م${season.first().season}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(34.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                season.forEach { cell ->
                                    Box(
                                        Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(ratingColor(cell.rating))
                                            .then(if (picked == cell) Modifier.border(2.dp, Color.White, RoundedCornerShape(7.dp)) else Modifier)
                                            .clickable { picked = cell },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(cell.rating?.let { fmt1(it) } ?: "–", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                picked?.let { c ->
                    Text(
                        "الموسم ${c.season} · الحلقة ${c.episode}" + (if (c.name.isNotBlank()) " — ${c.name}" else "") + (c.rating?.let { " · ★ ${fmt1(it)}" } ?: ""),
                        color = Color.White, fontSize = 13.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("9+" to 9.5, "8" to 8.5, "7" to 7.5, "6" to 6.5, "أقل" to 5.0).forEach { (label, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(ratingColor(v)))
                            Text(" $label", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun fmt1(v: Double): String {
    val t = kotlin.math.round(v * 10).toInt()
    return "${t / 10}.${t % 10}"
}

// ================================================================ «ما بدأته ولم يكتمل»

object LuxoUnfinished {
    private fun key() = "unfinished_hidden_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}"
    var version by mutableStateOf(0)
        private set

    fun hidden(): Set<String> { version; return LuxoPrefsStorage.load(key()).orEmpty().split('\n').filter { it.isNotBlank() }.toSet() }
    fun hide(id: String) { LuxoPrefsStorage.save(key(), (hidden() + id).joinToString("\n")); version++ }

    const val STALE_DAYS = 21
}

@Composable
fun LuxoUnfinishedDialog(onDismiss: () -> Unit) {
    val state by remember { WatchProgressRepository.ensureLoaded(); WatchProgressRepository.uiState }.collectAsState()
    val hidden = LuxoUnfinished.hidden()
    val now = luxoNowMs()
    val list: List<WatchProgressEntry> = remember(state.entries, hidden) {
        state.entries
            .filter { !it.isCompleted && it.parentMetaId !in hidden && it.parentMetaId !in state.hiddenContentIds }
            .groupBy { it.parentMetaId }
            .mapNotNull { (_, es) -> es.maxByOrNull { it.lastUpdatedEpochMs } }
            .filter { now - it.lastUpdatedEpochMs > LuxoUnfinished.STALE_DAYS * 86_400_000L }
            .sortedByDescending { it.lastUpdatedEpochMs }
    }
    IDialog("⏸️ ما بدأته ولم يكتمل", onDismiss) {
        Text("أعمال لم تُفتح منذ أكثر من ثلاثة أسابيع. يمكن إكمالها أو إخفاؤها من هذه القائمة.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        if (list.isEmpty()) {
            Text("لا توجد أعمال متوقفة 👏", color = Color.White, modifier = Modifier.padding(vertical = 16.dp))
            return@IDialog
        }
        LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.parentMetaId }) { e ->
                val days = ((now - e.lastUpdatedEpochMs) / 86_400_000L).toInt()
                val pct = if (e.durationMs > 0) (e.lastPositionMs * 100 / e.durationMs).toInt().coerceIn(0, 100) else 0
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(52.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        (e.poster ?: e.background)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(e.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val where = if (e.seasonNumber != null && e.episodeNumber != null) "الموسم ${e.seasonNumber} · الحلقة ${e.episodeNumber}" else "فيلم"
                        Text("$where · $pct٪ · منذ $days يوماً", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IButton("إكمال") {
                            val type = if (e.parentMetaType == "movie") "movie" else "series"
                            com.nuvio.app.core.deeplink.handleAppUrl(com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl(type, e.parentMetaId))
                            onDismiss()
                        }
                        Text("إخفاء", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.clickable { LuxoUnfinished.hide(e.parentMetaId) }.padding(4.dp))
                    }
                }
            }
        }
    }
}

// ================================================================ حد يومي لملف الأطفال

object LuxoKidsTime {
    private fun pid() = com.nuvio.app.features.profiles.ProfileRepository.activeProfileId
    private fun limitKey() = "kids_limit_min_${pid()}"
    private fun today(): String = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    private fun usedKey() = "kids_used_${pid()}_${today()}"
    private fun extraKey() = "kids_extra_${pid()}_${today()}"

    var version by mutableStateOf(0)
        private set

    /** Minutes allowed per day; 0 = no limit. */
    fun limitMinutes(): Int { version; return LuxoPrefsStorage.load(limitKey())?.toIntOrNull() ?: 0 }
    fun setLimit(minutes: Int) { LuxoPrefsStorage.save(limitKey(), minutes.toString()); version++ }

    fun usedSeconds(): Int { version; return LuxoPrefsStorage.load(usedKey())?.toIntOrNull() ?: 0 }
    fun addSeconds(s: Int) { LuxoPrefsStorage.save(usedKey(), (usedSeconds() + s).toString()); version++ }
    private fun extraMinutes(): Int = LuxoPrefsStorage.load(extraKey())?.toIntOrNull() ?: 0
    fun grantExtra(minutes: Int) { LuxoPrefsStorage.save(extraKey(), (extraMinutes() + minutes).toString()); version++ }

    fun active(): Boolean = LuxoFeaturesRepository.current().kidsTimeLimit && LuxoKids.isOn() && limitMinutes() > 0
    fun exhausted(): Boolean = active() && usedSeconds() >= (limitMinutes() + extraMinutes()) * 60
}

/** Inside the kids dialog: pick the daily limit (the PIN is needed to change it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoKidsTimeLimitRow(pin: String, onWrongPin: () -> Unit) {
    if (!rememberLuxoFeatures().kidsTimeLimit) return
    val current = LuxoKidsTime.limitMinutes()
    val used = LuxoKidsTime.usedSeconds() / 60
    Text("⏱️ الحد اليومي للمشاهدة" + (if (current > 0) " · استُخدم اليوم $used من $current دقيقة" else ""), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "بلا حد", 30 to "30 دقيقة", 60 to "ساعة", 90 to "ساعة ونصف", 120 to "ساعتان", 180 to "3 ساعات").forEach { (m, label) ->
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (m == current) Color(0xFF6F8FBF) else Color.White.copy(alpha = 0.08f))
                    .clickable { if (LuxoKids.checkPin(pin)) LuxoKidsTime.setLimit(m) else onWrongPin() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(label, color = Color.White, fontSize = 13.sp) }
        }
    }
    Text("يُكتب الرمز أعلاه ثم يُختار الحد", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
}

/** Shown over the player when today's time is used up; the PIN grants 30 more minutes. */
@Composable
fun LuxoKidsTimeUpDialog(onExtended: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    // Back closes the notice (to leave the player); playing again brings it straight back.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF208090D)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🌙", fontSize = 52.sp)
                Text("انتهى وقت المشاهدة لهذا اليوم", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("يُستأنف غداً، أو تُضاف نصف ساعة بإدخال الرمز", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); wrong = false }, singleLine = true, placeholder = { Text("الرمز") })
                if (wrong) Text("الرمز غير صحيح", color = Color(0xFFFFB4A9), fontSize = 12.sp)
                IButton("إضافة 30 دقيقة") {
                    if (LuxoKids.checkPin(pin)) { LuxoKidsTime.grantExtra(30); onExtended() } else wrong = true
                }
            }
        }
    }
}

// ================================================================ «من في المشهد؟»

internal object LuxoWhoInScene {
    /** Cast for the title (and the episode's guest stars) as "Actor — Character" lines. */
    suspend fun castLines(metaId: String, metaType: String, season: Int?, episode: Int?): List<String> {
        val tv = metaType != "movie"
        val tmdb = when {
            metaId.startsWith("tmdb:") -> metaId.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
            metaId.startsWith("tt") -> LuxoTmdbLite.get("find/${metaId.substringBefore(':')}", mapOf("external_source" to "imdb_id"))
                ?.a(if (tv) "tv_results" else "movie_results")?.firstOrNull()?.i("id")
            else -> null
        } ?: return emptyList()
        val people = mutableListOf<Pair<String, String>>()
        if (tv && season != null && episode != null) {
            LuxoTmdbLite.get("tv/$tmdb/season/$season/episode/$episode/credits")?.let { c ->
                (c.a("cast") + c.a("guest_stars")).forEach { p -> p.s("name")?.let { people += it to p.s("character").orEmpty() } }
            }
        }
        if (people.isEmpty()) {
            val c = LuxoTmdbLite.get(if (tv) "tv/$tmdb/aggregate_credits" else "movie/$tmdb/credits")
            c?.a("cast").orEmpty().take(40).forEach { p ->
                val ch = p.s("character") ?: p.a("roles").firstOrNull()?.s("character").orEmpty()
                p.s("name")?.let { people += it to ch }
            }
        }
        return people.distinctBy { it.first }.take(45).map { (n, ch) -> if (ch.isBlank()) n else "$n — $ch" }
    }

    fun prompt(title: String, season: Int?, episode: Int?, cast: List<String>): String = buildString {
        append("هذه لقطة من «").append(title).append("»")
        if (season != null && episode != null) append(" (الموسم ").append(season).append("، الحلقة ").append(episode).append(")")
        append(".\n")
        if (cast.isNotEmpty()) append("قائمة الممثلين في العمل:\n").append(cast.joinToString("\n")).append("\n")
        append(
            "حدّد الأشخاص الظاهرين في اللقطة من القائمة فقط، واذكر لكل واحد: اسم الممثل، واسم الشخصية، وسطراً قصيراً عنها بدون حرق للأحداث. " +
                "صِف موقع كل شخص في الصورة (يمين، يسار، في المنتصف). إن لم يكن التعرّف مؤكداً فاذكر ذلك صراحة. " +
                "اكتب بالعربية الفصحى، بلا تنسيق Markdown.",
        )
    }
}

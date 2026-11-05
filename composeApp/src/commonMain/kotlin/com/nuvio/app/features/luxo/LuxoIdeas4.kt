package com.nuvio.app.features.luxo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val I1 = Color(0xFF6F8FBF)
private val I2 = Color(0xFF9DB1CF)
private val IBg = Color(0xFF14161C)
private val ijson = Json { ignoreUnknownKeys = true }

// ================================================================ shared UI bits

@Composable
internal fun IChip(text: String, onClick: () -> Unit) {
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
internal fun IButton(text: String, primary: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Brush.horizontalGradient(listOf(I1, I2)) else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun IDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LuxoIdleBusy()
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = 760.dp).padding(16.dp)
                .clip(RoundedCornerShape(26.dp)).background(IBg).padding(18.dp),
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

// ================================================================ 1) «سابقًا في…»

private val recapCache = mutableMapOf<String, String>()

/**
 * Shown when an episode starts after a break of three days or more: a small card offers a short,
 * spoiler-free recap of the previous episode.
 */
@Composable
internal fun LuxoPreviouslyOn(metaId: String, title: String, season: Int?, episode: Int?, pause: () -> Unit) {
    if (!rememberLuxoFeatures().previouslyOn || season == null || episode == null || metaId.isBlank()) return
    if (season <= 1 && episode <= 1) return
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    val lastMs = remember(watched.items, metaId) { watched.items.filter { it.id == metaId }.maxOfOrNull { it.markedAtEpochMs } ?: 0L }
    val gapDays = if (lastMs > 0) (luxoNowMs() - lastMs) / 86_400_000L else 0L
    var offer by remember(metaId, season, episode) { mutableStateOf(gapDays >= 3) }
    var open by remember { mutableStateOf(false) }
    var text by remember(metaId, season, episode) { mutableStateOf<String?>(null) }
    LaunchedEffect(offer) { if (offer) { delay(12_000); offer = false } }
    if (offer) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopEnd) {
            Column(
                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("مضى $gapDays يومًا على آخر مشاهدة", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text("سابقًا في «$title»", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IButton("عرض الملخص") { offer = false; open = true; pause() }
                    IButton("تخطٍّ", primary = false) { offer = false }
                }
            }
        }
    }
    if (open) {
        val key = "$metaId:$season:$episode"
        LaunchedEffect(key) {
            text = recapCache[key] ?: run {
                val apiKey = LuxoCompanionApi.loadKey() ?: return@run "يلزم ضبط مفتاح «رفيق موج»."
                val prev = if (episode > 1) "الموسم $season الحلقة ${episode - 1}" else "الحلقة الأخيرة من الموسم ${season - 1}"
                val answer = LuxoCompanionApi.ask(
                    apiKey,
                    "اكتب ملخص «سابقًا في…» لمسلسل «$title»: لخّص $prev فقط في 4 إلى 6 نقاط قصيرة بالعربية الفصحى وبصيغة المجهول، " +
                        "تذكّر بأهم ما حدث فيها. ممنوع ذكر أي حدث من الحلقة القادمة أو ما بعدها. بدون تنسيق Markdown.",
                    search = true,
                ).replace("**", "")
                answer.also { recapCache[key] = it }
            }
        }
        IDialog("سابقًا في «$title»", { open = false }) {
            val t = text
            if (t == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = I1) }
            else Text(t, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 24.sp)
            IButton("متابعة المشاهدة") { open = false }
        }
    }
}

// ================================================================ 2) «أين قيلت هذه العبارة؟»

@Composable
fun LuxoQuoteFinderChip(metaId: String, title: String, enTitle: String?, year: String?, mediaType: String?) {
    if (!rememberLuxoFeatures().quoteFinder) return
    var open by remember { mutableStateOf(false) }
    IChip("🔎 أين قيلت هذه العبارة؟") { open = true }
    if (!open) return
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val voice = rememberLuxoVoiceInput { query = it }
    IDialog("🔎 أين قيلت هذه العبارة؟", { open = false }) {
        Text("تُكتب العبارة كما تُتذكّر، ولو بالإنجليزية أو بمعناها التقريبي", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("مثال: I am the one who knocks") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IButton(if (loading) "جارٍ البحث…" else "بحث") {
                if (query.isBlank() || loading) return@IButton
                loading = true
                result = null
                scope.launch {
                    val key = LuxoCompanionApi.loadKey()
                    result = if (key == null) "يلزم ضبط مفتاح «رفيق موج»." else LuxoCompanionApi.ask(
                        key,
                        "في ${if (mediaType == "movie") "فيلم" else "مسلسل"} «${enTitle ?: title}» (${year ?: ""}): أين قيلت هذه العبارة أو ما يشبهها: «$query»؟ " +
                            "أجب بالعربية الفصحى وباختصار: ${if (mediaType == "movie") "الدقيقة التقريبية" else "الموسم والحلقة والدقيقة التقريبية"}، ومن قالها، وسياقها في سطر دون كشف لما بعدها. " +
                            "إن لم تكن متأكدًا فاذكر ذلك صراحة. بدون تنسيق Markdown.",
                        search = true,
                    ).replace("**", "")
                    loading = false
                }
            }
            if (voice != null) IButton("🎙️ بالصوت", primary = false, onClick = voice)
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = I1) }
        result?.let {
            Text(it, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 24.sp)
            Text("قد لا يكون التحديد دقيقًا دائمًا.", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        }
    }
}

// ================================================================ 3) العبارات المحفوظة

@Serializable
data class LuxoQuote(val title: String, val text: String, val season: Int? = null, val episode: Int? = null, val positionMs: Long = 0L, val createdMs: Long = 0L)

object LuxoQuotes {
    private const val KEY = "quotes_v1"
    val all = mutableStateListOf<LuxoQuote>().apply {
        LuxoPrefsStorage.load(KEY)?.let { p -> runCatching { ijson.decodeFromString(ListSerializer(LuxoQuote.serializer()), p) }.getOrNull()?.let { addAll(it) } }
    }

    private fun save() = LuxoPrefsStorage.save(KEY, ijson.encodeToString(ListSerializer(LuxoQuote.serializer()), all.toList()))

    fun add(q: LuxoQuote) { all.add(q); save() }
    fun remove(q: LuxoQuote) { all.remove(q); save() }
}

@Composable
fun LuxoQuotesBlock(title: String) {
    if (!rememberLuxoFeatures().quotes) return
    val list = LuxoQuotes.all.filter { it.title == title }
    if (list.isEmpty()) return
    var sharing by remember { mutableStateOf<LuxoQuote?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("💬 العبارات المحفوظة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        list.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.positionMs })).forEach { q ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("«${q.text}»", color = Color.White, fontSize = 14.sp)
                    val where = listOfNotNull(q.season?.let { "الموسم $it" }, q.episode?.let { "الحلقة $it" }, luxoFormatMs(q.positionMs)).joinToString(" · ")
                    Text(where, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                Text("مشاركة", color = I2, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { sharing = q }.padding(6.dp))
                Text("حذف", color = Color(0xFFFFB4A9), fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { LuxoQuotes.remove(q) }.padding(6.dp))
            }
        }
    }
    sharing?.let { q -> LuxoQuoteShareDialog(q) { sharing = null } }
}

@Composable
private fun LuxoQuoteShareDialog(q: LuxoQuote, onDismiss: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    IDialog("مشاركة العبارة", onDismiss) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(4f / 5f)
                .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B1E26), Color(0xFF08090D))))
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("❝", color = I2, fontSize = 54.sp)
            Text(q.text, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 36.sp)
            Text(q.title + (q.season?.let { " · م$it" } ?: "") + (q.episode?.let { " ح$it" } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp))
            Text("موج", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 26.dp))
        }
        IButton("مشاركة") {
            scope.launch { runCatching { layer.toImageBitmap() }.getOrNull()?.let { luxoShareImage(it, q.title) }; onDismiss() }
        }
    }
}

// ================================================================ 5) المصدر الذكي

@Serializable
data class LuxoSmartRules(
    val enabled: Boolean = false,
    /** 0 = any, 720, 1080, 2160. */
    val minQuality: Int = 1080,
    /** 0 = no limit. */
    val maxSizeGb: Int = 0,
    val preferWords: String = "",
    val avoidWords: String = "CAM, TS, HDCAM, TELESYNC",
    val preferArabicSubs: Boolean = true,
)

/** «المصدر الذكي»: ranks the matching streams before the auto-play picks the first one. */
object LuxoSmartSource {
    private const val KEY = "smart_source_v1"
    var rules by mutableStateOf(LuxoPrefsStorage.load(KEY)?.let { runCatching { ijson.decodeFromString(LuxoSmartRules.serializer(), it) }.getOrNull() } ?: LuxoSmartRules())
        private set

    fun update(r: LuxoSmartRules) {
        rules = r
        LuxoPrefsStorage.save(KEY, ijson.encodeToString(LuxoSmartRules.serializer(), r))
        if (r.enabled) {
            val ps = com.nuvio.app.features.player.PlayerSettingsRepository
            ps.ensureLoaded()
            if (ps.uiState.value.streamAutoPlayMode == com.nuvio.app.features.streams.StreamAutoPlayMode.MANUAL) {
                ps.setStreamAutoPlayMode(com.nuvio.app.features.streams.StreamAutoPlayMode.FIRST_STREAM)
            }
        }
    }

    private fun text(s: StreamItem) = listOfNotNull(s.name, s.title, s.description, s.streamLabel, s.streamSubtitle).joinToString(" ")

    private fun quality(t: String): Int = when {
        Regex("2160|4k|uhd", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 2160
        Regex("1080", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 1080
        Regex("720", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 720
        Regex("480|sd\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 480
        else -> 0
    }

    private fun sizeGb(t: String): Double? {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(GB|GiB|MB|MiB)", RegexOption.IGNORE_CASE).find(t) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (m.groupValues[2].startsWith("M", ignoreCase = true)) v / 1024.0 else v
    }

    private fun words(s: String) = s.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }

    /** Same list, best first; streams breaking a hard rule move to the end. */
    fun rank(streams: List<StreamItem>): List<StreamItem> {
        val r = rules
        if (!r.enabled || !LuxoFeaturesRepository.current().smartSource || streams.size < 2) return streams
        val avoid = words(r.avoidWords)
        val prefer = words(r.preferWords)
        return streams.withIndex().sortedByDescending { (index, s) ->
            val t = text(s)
            val q = quality(t)
            val size = sizeGb(t)
            var score = 0.0
            if (avoid.any { t.contains(it, ignoreCase = true) }) score -= 10_000
            if (r.minQuality > 0 && q in 1 until r.minQuality) score -= 3_000
            if (r.maxSizeGb > 0 && size != null && size > r.maxSizeGb) score -= 2_000
            score += q / 10.0
            if (prefer.any { t.contains(it, ignoreCase = true) }) score += 400
            if (r.preferArabicSubs && Regex("arabic|\\bara\\b|عربي|multi.?sub", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 250
            score - index * 0.01
        }.map { it.value }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoSmartSourceDialog(onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(LuxoSmartSource.rules) }
    IDialog("🎯 المصدر الذكي", onDismiss) {
        Text("يُرتَّب كل مصدر حسب هذه القواعد، ثم يبدأ التشغيل بأفضلها مباشرة دون قائمة المصادر.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تفعيل المصدر الذكي", color = Color.White, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = r.enabled, onCheckedChange = { r = r.copy(enabled = it) })
        }
        Text("أدنى جودة", color = Color.White, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "أي جودة", 720 to "720p", 1080 to "1080p", 2160 to "4K").forEach { (v, l) ->
                androidx.compose.material3.FilterChip(selected = r.minQuality == v, onClick = { r = r.copy(minQuality = v) }, label = { Text(l) })
            }
        }
        Text("أقصى حجم", color = Color.White, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "دون حد", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB", 20 to "20 GB").forEach { (v, l) ->
                androidx.compose.material3.FilterChip(selected = r.maxSizeGb == v, onClick = { r = r.copy(maxSizeGb = v) }, label = { Text(l) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تفضيل المصادر ذات الترجمة العربية", color = Color.White, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = r.preferArabicSubs, onCheckedChange = { r = r.copy(preferArabicSubs = it) })
        }
        OutlinedTextField(value = r.preferWords, onValueChange = { r = r.copy(preferWords = it) }, label = { Text("كلمات مفضّلة (مفصولة بفواصل)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = r.avoidWords, onValueChange = { r = r.copy(avoidWords = it) }, label = { Text("كلمات مستبعدة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        IButton("حفظ") { LuxoSmartSource.update(r); onDismiss() }
    }
}

// ================================================================ 8) حالة المسلسلات

@Serializable
data class LuxoSeriesState(val tmdbId: Int, val title: String, val status: String? = null, val seasons: Int = 0, val finaleNotified: String? = null)

object LuxoSeriesStatus {
    private const val KEY = "series_status_v1"
    private val ser = MapSerializer(String.serializer(), LuxoSeriesState.serializer())
    private val map: MutableMap<String, LuxoSeriesState> =
        (LuxoPrefsStorage.load(KEY)?.let { runCatching { ijson.decodeFromString(ser, it) }.getOrNull() } ?: emptyMap()).toMutableMap()

    private fun save() = LuxoPrefsStorage.save(KEY, ijson.encodeToString(ser, map.toMap()))

    /** Called in the app: keeps the list of library series (resolved to TMDB) for the background check. */
    suspend fun sync(series: List<Pair<String, String>>) {
        LuxoReminders.rememberApiKey()
        val keep = series.map { it.first }.toSet()
        map.keys.retainAll(keep)
        series.filter { it.first !in map }.take(40).forEach { (id, name) ->
            val tmdb = when {
                id.startsWith("tmdb:") -> id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
                id.startsWith("tt") -> LuxoTmdbLite.get("find/${id.substringBefore(':')}", mapOf("external_source" to "imdb_id"))?.a("tv_results")?.firstOrNull()?.i("id")
                else -> null
            } ?: return@forEach
            val o = LuxoTmdbLite.get("tv/$tmdb") ?: return@forEach
            map[id] = LuxoSeriesState(tmdb, name, o.s("status"), o.i("number_of_seasons") ?: 0)
        }
        save()
    }

    /** Background: (id, title, body, deepLink). */
    suspend fun check(fetch: suspend (Int) -> JsonObject?, notify: suspend (String, String, String, String) -> Unit) {
        val today = com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate().take(10)
        map.entries.toList().forEach { (key, st) ->
            val o = fetch(st.tmdbId) ?: return@forEach
            val status = o.s("status")
            val seasons = o.i("number_of_seasons") ?: st.seasons
            val link = com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl("series", key)
            var next = st.copy(status = status, seasons = seasons)
            if (st.status != null && status != st.status) {
                when (status) {
                    "Canceled" -> notify("luxo-status-$key-c", "أُلغي «${st.title}» 📣", "أُعلن إلغاء المسلسل ولن تُنتج مواسم جديدة", link)
                    "Ended" -> notify("luxo-status-$key-e", "انتهى «${st.title}» 📣", "عُرضت الحلقة الأخيرة من المسلسل", link)
                    "Returning Series" -> notify("luxo-status-$key-r", "«${st.title}» مستمر 📣", "أُعلن استمرار المسلسل", link)
                }
            }
            if (st.seasons in 1 until seasons) {
                notify("luxo-status-$key-s$seasons", "موسم جديد من «${st.title}» 🎉", "أُعلن الموسم $seasons", link)
            }
            val ne = o.o("next_episode_to_air")
            val date = ne?.s("air_date")
            if (ne?.s("episode_type") == "finale" && date != null && date >= today && st.finaleNotified != date) {
                notify("luxo-status-$key-f$date", "الحلقة القادمة ختام الموسم 🏁", "«${st.title}»: الموسم ${ne.i("season_number") ?: ""} · بتاريخ $date", link)
                next = next.copy(finaleNotified = date)
            }
            map[key] = next
        }
        save()
    }
}

// ================================================================ 10) البحث بصورة (dialog in LuxoDescribeSearch.kt)

// ================================================================ 11) عجلة الاختيار

private data class WheelItem(val preview: MetaPreview, val year: String?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoChoiceWheelDialog(onDismiss: () -> Unit) {
    val picks = remember { mutableStateListOf<WheelItem>() }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<WheelItem>>(emptyList()) }
    val angle = remember { Animatable(0f) }
    var winner by remember { mutableStateOf<WheelItem?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.isBlank()) { hits = emptyList(); return@LaunchedEffect }
        delay(400)
        hits = LuxoTmdbLite.get("search/multi", mapOf("query" to LuxoTmdbLite.enc(query), "language" to "ar"))?.a("results").orEmpty()
            .filter { it.s("media_type") in setOf("movie", "tv") && it.s("original_language") != "ar" }.take(8).mapNotNull { r ->
                val id = r.i("id") ?: return@mapNotNull null
                WheelItem(
                    MetaPreview(id = "tmdb:$id", type = if (r.s("media_type") == "movie") "movie" else "series", name = r.s("title") ?: r.s("name") ?: return@mapNotNull null, poster = LuxoTmdbLite.img(r.s("poster_path"))),
                    (r.s("release_date") ?: r.s("first_air_date"))?.take(4),
                )
            }
    }
    val colors = listOf(Color(0xFF3B4A63), Color(0xFF55657F), Color(0xFF2E3A4E), Color(0xFF6F8FBF), Color(0xFF46546B), Color(0xFF8A9BB5), Color(0xFF394559), Color(0xFF5E7393))
    IDialog("🎡 عجلة الاختيار", onDismiss) {
        Text("يُضاف عمل لكل فرد (حتى ثمانية)، ثم تدور العجلة وتختار.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        if (picks.size >= 2) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp).aspectRatio(1f).rotate(angle.value)) {
                    val n = picks.size
                    val sweep = 360f / n
                    for (k in 0 until n) {
                        drawArc(colors[k % colors.size], startAngle = -90f + k * sweep, sweepAngle = sweep, useCenter = true, size = Size(size.width, size.height))
                    }
                    for (k in 0 until n) {
                        val a = ((-90f + k * sweep) * PI / 180f).toFloat()
                        drawLine(Color(0xFF08090D), center, Offset(center.x + cos(a) * size.width / 2, center.y + sin(a) * size.height / 2), strokeWidth = 3f)
                    }
                    drawCircle(Color(0xFF08090D), radius = size.minDimension * 0.08f)
                }
                // Pointer at the top.
                Canvas(Modifier.size(28.dp).align(Alignment.TopCenter)) {
                    val p = Path().apply { moveTo(size.width / 2, size.height); lineTo(0f, 0f); lineTo(size.width, 0f); close() }
                    drawPath(p, Color.White)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            picks.forEachIndexed { k, it ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(colors[k % colors.size]).clickable { picks.remove(it); winner = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("${it.preview.name} ✕", color = Color.White, fontSize = 13.sp, maxLines = 1) }
            }
        }
        winner?.let { w ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f))) {
                    if (w.preview.poster != null) AsyncImage(w.preview.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("وقع الاختيار على", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Text(w.preview.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                IButton("فتح") { onDismiss(); LuxoOverlays.openMeta(w.preview) }
            }
        }
        if (picks.size >= 2) {
            IButton("أدر العجلة") {
                scope.launch {
                    winner = null
                    val n = picks.size
                    val pick = (0 until n).random()
                    val sweep = 360f / n
                    // The pointer is at -90°; land the middle of [pick] under it.
                    val target = angle.value - (angle.value % 360f) + 360f * 6 - (pick * sweep + sweep / 2f)
                    angle.animateTo(target, tween(4200, easing = FastOutSlowInEasing))
                    winner = picks.getOrNull(pick)
                }
            }
        }
        if (picks.size < 8) {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("إضافة عمل…") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(hits, key = { it.preview.id }) { h ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (picks.none { it.preview.id == h.preview.id }) picks.add(h); query = "" }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(34.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f))) {
                            if (h.preview.poster != null) AsyncImage(h.preview.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Text(h.preview.name + (h.year?.let { " ($it)" } ?: ""), color = Color.White, modifier = Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ================================================================ 12) ملف الأطفال

object LuxoKids {
    private fun key() = "kids_mode_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}"
    var version by mutableStateOf(0)
        private set
    private val unlocked = mutableStateMapOf<String, Boolean>()

    fun isOn(): Boolean { version; return LuxoFeaturesRepository.current().kidsProfile && LuxoPrefsStorage.load(key())?.isNotBlank() == true }
    fun enable(pin: String) { LuxoPrefsStorage.save(key(), pin); version++ }
    fun disable(pin: String): Boolean {
        if (LuxoPrefsStorage.load(key()) != pin) return false
        LuxoPrefsStorage.save(key(), ""); version++; return true
    }
    fun checkPin(pin: String) = LuxoPrefsStorage.load(key()) == pin
    fun isUnlocked(id: String) = unlocked[id] == true
    fun unlock(id: String) { unlocked[id] = true }

    private val ok = setOf("G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "U", "0", "6", "7", "+6", "G-", "TP", "AL")
    private val cache = mutableMapOf<String, Boolean>()

    /** true = allowed for kids, false = not, null = unknown (treated as not allowed). */
    suspend fun allowed(tmdbId: String, mediaType: String): Boolean {
        cache["$mediaType:$tmdbId"]?.let { return it }
        val certs = if (mediaType == "movie") {
            LuxoTmdbLite.get("movie/$tmdbId/release_dates")?.a("results").orEmpty().filter { it.s("iso_3166_1") in setOf("US", "GB", "SA") }
                .flatMap { c -> c.a("release_dates").mapNotNull { it.s("certification") } }
        } else {
            LuxoTmdbLite.get("tv/$tmdbId/content_ratings")?.a("results").orEmpty().filter { it.s("iso_3166_1") in setOf("US", "GB", "SA") }.mapNotNull { it.s("rating") }
        }.filter { it.isNotBlank() }
        val result = certs.isNotEmpty() && certs.all { it.uppercase() in ok }
        cache["$mediaType:$tmdbId"] = result
        return result
    }
}

/** Covers the details page when the kids profile is on and the title is not kid-rated. */
@Composable
fun LuxoKidsGate(metaId: String, tmdbId: String?, mediaType: String?, loading: Boolean = false) {
    val on = LuxoKids.isOn()
    if (!on || LuxoKids.isUnlocked(metaId)) return
    var allowed by remember(metaId, tmdbId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(metaId, tmdbId, loading) {
        if (loading) return@LaunchedEffect
        allowed = if (tmdbId == null) false else runCatching { LuxoKids.allowed(tmdbId, if (mediaType == "movie") "movie" else "tv") }.getOrNull()
    }
    if (allowed == true) return
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xF208090D)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🧸", fontSize = 48.sp)
            Text(if (allowed == null) "يتم التحقق من التصنيف العمري…" else "هذا العمل غير متاح في ملف الأطفال", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text("يُفتح بإدخال رمز ملف الأطفال", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); wrong = false }, singleLine = true, placeholder = { Text("الرمز") })
            if (wrong) Text("الرمز غير صحيح", color = Color(0xFFFFB4A9), fontSize = 12.sp)
            IButton("فتح") { if (LuxoKids.checkPin(pin)) LuxoKids.unlock(metaId) else wrong = true }
        }
    }
}

@Composable
fun LuxoKidsDialog(onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val on = LuxoKids.isOn()
    IDialog("🧸 ملف الأطفال", onDismiss) {
        Text(
            if (on) "ملف الأطفال مفعّل لهذا الملف. يُوقف بإدخال الرمز." else
                "عند التفعيل لا تُفتح في هذا الملف إلا الأعمال المصنّفة للأطفال (مثل G وPG وTV-Y وTV-G)، ويُطلب الرمز لفتح غيرها.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 20.sp,
        )
        OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null }, singleLine = true, placeholder = { Text(if (on) "الرمز" else "رمز جديد (4 أرقام أو أكثر)") })
        error?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
        if (on) LuxoKidsTimeLimitRow(pin) { error = "الرمز غير صحيح" }
        IButton(if (on) "إيقاف ملف الأطفال" else "تفعيل") {
            if (on) { if (LuxoKids.disable(pin)) onDismiss() else error = "الرمز غير صحيح" }
            else { if (pin.length < 4) error = "يلزم رمز من 4 أرقام على الأقل" else { LuxoKids.enable(pin); onDismiss() } }
        }
    }
}

// ================================================================ 6/7) التحكم بالشاشة و«أكمل على الشاشة»

object LuxoRemote {
    private const val CODE_KEY = "remote_code_v1"
    private const val TARGET_KEY = "remote_target_v1"

    /** TV: its pairing code (made once). */
    val code: String by lazy {
        LuxoPrefsStorage.load(CODE_KEY)?.takeIf { it.length == 4 } ?: (1000..9999).random().toString().also { LuxoPrefsStorage.save(CODE_KEY, it) }
    }

    /** Controller: last TV used, "host|port|code|name". */
    var target by mutableStateOf(LuxoPrefsStorage.load(TARGET_KEY))
        private set

    fun saveTarget(host: String, port: Int, code: String, name: String) {
        target = "$host|$port|$code|$name"
        LuxoPrefsStorage.save(TARGET_KEY, target!!)
    }

    suspend fun send(payload: JsonObject): Boolean {
        val t = target?.split('|') ?: return false
        if (t.size < 3) return false
        return LuxoRemoteNet.send(t[0], t[1].toIntOrNull() ?: LuxoRemoteNet.port, t[2], payload.toString())
    }

    var searchQuery by mutableStateOf<String?>(null)
}

/** TV side: runs the listener while the app is open. */
@Composable
internal fun LuxoRemoteServerHost() {
    val features = rememberLuxoFeatures()
    if (!features.remote || !luxoIsTv()) return
    DisposableEffect(Unit) {
        LuxoRemoteNet.startServer(LuxoRemote.code, "موج · الشاشة") { raw ->
            val o = runCatching { ijson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return@startServer
            val c = com.nuvio.app.features.player.LuxoPlayerBridge.remoteController
            when ((o["t"] as? JsonPrimitive)?.contentOrNull) {
                "pp" -> c?.let { if (com.nuvio.app.features.player.LuxoPlayerBridge.remoteIsPlaying) it.pause() else it.play() }
                "seek" -> (o["d"] as? JsonPrimitive)?.longOrNull?.let { d -> c?.seekBy(d) }
                "open" -> (o["url"] as? JsonPrimitive)?.contentOrNull?.let { com.nuvio.app.core.deeplink.handleAppUrl(it) }
                "search" -> (o["q"] as? JsonPrimitive)?.contentOrNull?.let { LuxoRemote.searchQuery = it }
            }
        }
        onDispose { LuxoRemoteNet.stopServer() }
    }
    LuxoRemote.searchQuery?.let { q -> LuxoRemoteSearchDialog(q) { LuxoRemote.searchQuery = null } }
}

@Composable
private fun LuxoRemoteSearchDialog(query: String, onDismiss: () -> Unit) {
    var hits by remember(query) { mutableStateOf<List<MetaPreview>?>(null) }
    LaunchedEffect(query) {
        hits = LuxoTmdbLite.get("search/multi", mapOf("query" to LuxoTmdbLite.enc(query), "language" to "ar"))?.a("results").orEmpty()
            .filter { it.s("media_type") in setOf("movie", "tv") }.take(12).mapNotNull { r ->
                val id = r.i("id") ?: return@mapNotNull null
                MetaPreview(id = "tmdb:$id", type = if (r.s("media_type") == "movie") "movie" else "series", name = r.s("title") ?: r.s("name") ?: return@mapNotNull null, poster = LuxoTmdbLite.img(r.s("poster_path")))
            }
    }
    IDialog("📱 بحث من الجوال: «$query»", onDismiss) {
        val list = hits
        if (list == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = I1) }
        else LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).clickable { onDismiss(); LuxoOverlays.openMeta(p) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        if (p.poster != null) AsyncImage(p.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

/** TV: shows its address and code. Tablet/phone: finds the TV and controls it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoRemoteDialog(onDismiss: () -> Unit) {
    if (luxoIsTv()) {
        IDialog("📱 التحكم بالشاشة", onDismiss) {
            Text("يُفتح «التحكم بالشاشة» من الجوال أو التابلت على الشبكة نفسها، ثم يُختار هذا الجهاز ويُدخل الرمز:", color = Color.White.copy(alpha = 0.75f))
            Text(LuxoRemote.code, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Text("عنوان الجهاز: ${LuxoRemoteNet.localAddress() ?: "غير متصل بالشبكة"}", color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<LuxoRemoteDevice>?>(null) }
    var picking by remember { mutableStateOf<LuxoRemoteDevice?>(null) }
    var code by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    fun cmd(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch { status = if (LuxoRemote.send(buildJsonObject(build))) null else "تعذّر الوصول إلى الشاشة" }
    }
    IDialog("📱 التحكم بالشاشة", onDismiss) {
        val t = LuxoRemote.target?.split('|')
        if (t != null && t.size >= 4 && picking == null) {
            Text("متصل بـ ${t[3]} (${t[0]})", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IButton("⏪ 60", primary = false) { cmd { put("t", "seek"); put("d", -60_000L) } }
                IButton("⏪ 10", primary = false) { cmd { put("t", "seek"); put("d", -10_000L) } }
                IButton("⏯") { cmd { put("t", "pp") } }
                IButton("10 ⏩", primary = false) { cmd { put("t", "seek"); put("d", 10_000L) } }
                IButton("60 ⏩", primary = false) { cmd { put("t", "seek"); put("d", 60_000L) } }
            }
            OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true, placeholder = { Text("بحث على الشاشة…") }, modifier = Modifier.fillMaxWidth())
            IButton("إرسال البحث") { if (search.isNotBlank()) cmd { put("t", "search"); put("q", search) } }
            status?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
            Text("تغيير الشاشة", color = I2, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { devices = null; picking = LuxoRemoteDevice("", "", 0) }.padding(6.dp))
            return@IDialog
        }
        LaunchedEffect(Unit) { devices = LuxoRemoteNet.discover(3500) }
        val p = picking?.takeIf { it.host.isNotBlank() }
        if (p == null) {
            Text("يتم البحث عن شاشات تعمل عليها «موج» في الشبكة نفسها…", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            val list = devices
            if (list == null) CircularProgressIndicator(color = I1)
            else if (list.isEmpty()) Text("لم يُعثر على شاشة. يمكن إدخال عنوانها الظاهر في «التحكم بالشاشة» عليها:", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            list?.forEach { d -> IChip("📺 ${d.name} · ${d.host}") { picking = d } }
            OutlinedTextField(value = manualHost, onValueChange = { manualHost = it }, singleLine = true, placeholder = { Text("عنوان الشاشة مثل 192.168.1.20") }, modifier = Modifier.fillMaxWidth())
            if (manualHost.isNotBlank()) IButton("متابعة") { picking = LuxoRemoteDevice("الشاشة", manualHost.trim(), LuxoRemoteNet.port) }
        } else {
            Text("يُدخل الرمز الظاهر على الشاشة في «التحكم بالشاشة»", color = Color.White.copy(alpha = 0.7f))
            OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(4) }, singleLine = true, placeholder = { Text("الرمز") })
            IButton("اتصال") {
                scope.launch {
                    val ok = LuxoRemoteNet.send(p.host, p.port, code, buildJsonObject { put("t", "ping") }.toString())
                    if (ok) { LuxoRemote.saveTarget(p.host, p.port, code, p.name); picking = null; status = null } else status = "الرمز غير صحيح أو الشاشة غير متاحة"
                }
            }
            status?.let { Text(it, color = Color(0xFFFFB4A9), fontSize = 12.sp) }
        }
    }
}


/** Keeps the library's series list for the background status check (once per app start). */
@Composable
internal fun LuxoSeriesStatusSync() {
    if (!rememberLuxoFeatures().seriesStatus) return
    val library by remember {
        com.nuvio.app.features.library.LibraryRepository.ensureLoaded()
        com.nuvio.app.features.library.LibraryRepository.uiState
    }.collectAsState()
    val series = remember(library.items) { library.items.filter { it.type == "series" || it.type == "tv" }.map { it.id to it.name } }
    LaunchedEffect(series.size) {
        if (series.isEmpty()) return@LaunchedEffect
        delay(20_000)
        runCatching { LuxoSeriesStatus.sync(series) }
    }
}

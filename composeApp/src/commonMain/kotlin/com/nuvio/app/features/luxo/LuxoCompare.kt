package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.details.luxoFlag
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.d
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val CAccent1 = Color(0xFF8C7BFF)
private val CAccent2 = Color(0xFFFF7EB6)

data class LuxoCompareSide(
    val tmdbId: String,
    val mediaType: String,
    val title: String,
    val enTitle: String?,
    val poster: String?,
    val year: String?,
    val rating: Double?,
    val votes: Int?,
    val runtime: String?,
    val seasons: String?,
    val genres: String?,
    val country: String?,
    val maker: String?,
    val cast: String?,
    val money: String?,
    val status: String?,
)

internal data class LuxoCompareHit(val id: String, val type: String, val title: String, val year: String?, val poster: String?)

/** «⚖️ قارن عملين»: two titles side by side, plus a short verdict from «رفيق Luxo». */
object LuxoCompareData {
    private fun money(v: Int?): String? = v?.takeIf { it > 0 }?.let { "$" + if (it >= 1_000_000) "${it / 1_000_000}M" else "${it / 1000}K" }

    suspend fun load(tmdbId: String, mediaType: String): LuxoCompareSide? {
        val t = if (mediaType == "movie") "movie" else "tv"
        val o = LuxoTmdbLite.get("$t/$tmdbId", mapOf("language" to "ar", "append_to_response" to "credits")) ?: return null
        val en = if (t == "movie") o.s("original_title") else o.s("original_name")
        val credits = o.o("credits")
        val maker = if (t == "movie") {
            credits?.a("crew")?.filter { it.s("job") == "Director" }?.mapNotNull { it.s("name") }?.take(2)?.joinToString("، ")
        } else {
            o.a("created_by").mapNotNull { it.s("name") }.take(2).joinToString("، ").ifBlank { null }
        }
        val runtime = if (t == "movie") o.i("runtime")?.takeIf { it > 0 }?.let { "${it / 60}س ${it % 60}د" }
        else (o["episode_run_time"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.let { "$it د للحلقة" }
        return LuxoCompareSide(
            tmdbId = tmdbId,
            mediaType = t,
            title = o.s("title") ?: o.s("name") ?: en ?: "",
            enTitle = en,
            poster = LuxoTmdbLite.img(o.s("poster_path")),
            year = (o.s("release_date") ?: o.s("first_air_date"))?.take(4),
            rating = o.d("vote_average")?.takeIf { it > 0 },
            votes = o.i("vote_count"),
            runtime = runtime,
            seasons = if (t == "tv") listOfNotNull(o.i("number_of_seasons")?.let { "$it مواسم" }, o.i("number_of_episodes")?.let { "$it حلقة" }).joinToString(" · ").ifBlank { null } else null,
            genres = o.a("genres").mapNotNull { it.s("name") }.take(3).joinToString("، ").ifBlank { null },
            country = (o.a("production_countries").mapNotNull { it.s("iso_3166_1") }.ifEmpty { (o["origin_country"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty() })
                .take(2).joinToString(" ") { luxoFlag(it) + " " + it }.ifBlank { null },
            maker = maker,
            cast = credits?.a("cast")?.mapNotNull { it.s("name") }?.take(3)?.joinToString("، ")?.ifBlank { null },
            money = if (t == "movie") listOfNotNull(money(o.i("budget"))?.let { "ميزانية $it" }, money(o.i("revenue"))?.let { "إيرادات $it" }).joinToString(" · ").ifBlank { null } else null,
            status = o.s("status"),
        )
    }

    internal suspend fun search(q: String): List<LuxoCompareHit> {
        val res = LuxoTmdbLite.get("search/multi", mapOf("query" to LuxoTmdbLite.enc(q), "language" to "ar")) ?: return emptyList()
        return res.a("results").filter { it.s("media_type") in setOf("movie", "tv") }.take(12).mapNotNull { r ->
            val id = r.i("id") ?: return@mapNotNull null
            LuxoCompareHit(id.toString(), r.s("media_type")!!, r.s("title") ?: r.s("name") ?: return@mapNotNull null, (r.s("release_date") ?: r.s("first_air_date"))?.take(4), LuxoTmdbLite.img(r.s("poster_path"), "w185"))
        }
    }

    private val verdictCache = mutableMapOf<String, String>()

    suspend fun verdict(a: LuxoCompareSide, b: LuxoCompareSide): String? {
        val k = "${a.tmdbId}|${b.tmdbId}"
        verdictCache[k]?.let { return it }
        val key = LuxoCompanionApi.loadKey() ?: return null
        val prompt = "قارن باختصار بين «${a.enTitle ?: a.title}» (${a.year}) و«${b.enTitle ?: b.title}» (${b.year}). " +
            "في 4 أسطر قصيرة بالعربية وبصيغة المجهول (بدون مخاطبة): نقاط التشابه، أبرز فرق، ولمن يُنصح بكل واحد. بدون حرق للأحداث وبدون تنسيق Markdown."
        val answer = LuxoCompanionApi.ask(key, prompt).trim().replace("**", "")
        if (answer.isNotBlank()) verdictCache[k] = answer
        return answer.ifBlank { null }
    }
}

@Composable
fun LuxoCompareChip(title: String, tmdbId: String?, mediaType: String?) {
    if (!rememberLuxoFeatures().compareTitles || tmdbId == null) return
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text("⚖️ قارن مع عمل آخر", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    if (open) LuxoCompareDialog(title, tmdbId, if (mediaType == "movie") "movie" else "tv") { open = false }
}

@Composable
fun LuxoCompareDialog(title: String, tmdbId: String, mediaType: String, onDismiss: () -> Unit) {
    var left by remember { mutableStateOf<LuxoCompareSide?>(null) }
    var right by remember { mutableStateOf<LuxoCompareSide?>(null) }
    var picking by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<LuxoCompareHit>>(emptyList()) }
    var loadingRight by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<String?>(null) }
    var verdictLoading by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(tmdbId) { left = LuxoCompareData.load(tmdbId, mediaType) }
    LaunchedEffect(query) {
        if (query.isBlank()) { hits = emptyList(); return@LaunchedEffect }
        delay(400)
        hits = LuxoCompareData.search(query).filter { it.id != tmdbId }
    }
    LaunchedEffect(left, right) {
        val a = left
        val b = right
        verdict = null
        if (a != null && b != null) {
            verdictLoading = true
            verdict = LuxoCompareData.verdict(a, b)
            verdictLoading = false
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF14121C))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚖️ مقارنة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            }
            if (picking) {
                Text("يتم اختيار العمل الثاني للمقارنة مع «$title»", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("اسم العمل…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (loadingRight) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CAccent1) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits, key = { it.type + it.id }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).clickable {
                                loadingRight = true
                                picking = false
                                right = null
                                scope.launch { right = LuxoCompareData.load(h.id, h.type); loadingRight = false }
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))) {
                                if (h.poster != null) AsyncImage(h.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(h.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(h.year, if (h.type == "movie") "فيلم" else "مسلسل").joinToString(" · "), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                val a = left
                val b = right
                if (!loadingRight && (a == null || b == null)) {
                    Text("تعذّر تحميل بيانات المقارنة", color = Color(0xFFFFB4A9), fontSize = 13.sp)
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { picking = true }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text("↺ اختيار عمل آخر", color = Color.White, fontSize = 13.sp)
                    }
                } else if (a == null || b == null) {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CAccent1) }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PosterHead(a, Modifier.weight(1f))
                                PosterHead(b, Modifier.weight(1f))
                            }
                        }
                        val rows = listOf<Triple<String, String?, String?>>(
                            Triple("السنة", a.year, b.year),
                            Triple("التقييم", a.rating?.let { "★ " + (kotlin.math.round(it * 10) / 10.0) }, b.rating?.let { "★ " + (kotlin.math.round(it * 10) / 10.0) }),
                            Triple("عدد المقيّمين", a.votes?.toString(), b.votes?.toString()),
                            Triple("المدة", a.runtime, b.runtime),
                            Triple("المواسم", a.seasons, b.seasons),
                            Triple("التصنيف", a.genres, b.genres),
                            Triple("البلد", a.country, b.country),
                            Triple(if (a.mediaType == "movie") "الإخراج" else "الإنشاء", a.maker, b.maker),
                            Triple("البطولة", a.cast, b.cast),
                            Triple("الأرقام", a.money, b.money),
                            Triple("الحالة", a.status, b.status),
                        ).filter { it.second != null || it.third != null }
                        items(rows) { (label, x, y) ->
                            val better = when (label) {
                                "التقييم" -> compareValues(a.rating, b.rating)
                                "عدد المقيّمين" -> compareValues(a.votes, b.votes)
                                else -> 0
                            }
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(10.dp)) {
                                Text(label, color = CAccent2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Cell(x, better > 0, Modifier.weight(1f))
                                    Cell(y, better < 0, Modifier.weight(1f))
                                }
                            }
                        }
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(CAccent1.copy(alpha = 0.18f), CAccent2.copy(alpha = 0.14f))))
                                    .padding(14.dp),
                            ) {
                                Text("✨ رأي رفيق Luxo", color = Color.White, fontWeight = FontWeight.Bold)
                                when {
                                    verdictLoading -> Text("جارٍ التحليل…", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                                    verdict != null -> Text(verdict!!, color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 20.sp)
                                    else -> Text("لم يتوفر رأي الرفيق (يلزم ضبط مفتاحه)", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                                }
                            }
                        }
                        item {
                            Box(
                                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                                    .clickable { picking = true }.padding(horizontal = 16.dp, vertical = 9.dp),
                            ) { Text("↺ تغيير العمل الثاني", color = Color.White, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterHead(s: LuxoCompareSide, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.7f).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (s.poster != null) AsyncImage(s.poster, s.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Cell(text: String?, winner: Boolean, modifier: Modifier) {
    Text(
        (text ?: "—") + if (winner) " 🏆" else "",
        color = if (winner) Color(0xFFFFD27A) else Color.White.copy(alpha = 0.9f),
        fontSize = 13.sp,
        fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

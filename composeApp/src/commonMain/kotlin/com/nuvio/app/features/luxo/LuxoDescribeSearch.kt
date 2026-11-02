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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private val DAccent1 = Color(0xFF8C7BFF)
private val DAccent2 = Color(0xFFFF7EB6)

data class LuxoDescribeHit(val preview: MetaPreview, val year: String?, val why: String)

/** «🔮 البحث بالوصف»: «مسلسل كوري عن محامية عندها توحد» → the matching titles. */
object LuxoDescribeSearch {
    private val cache = mutableMapOf<String, List<LuxoDescribeHit>>()

    suspend fun find(description: String): Result<List<LuxoDescribeHit>> = withContext(Dispatchers.Default) {
        val q = description.trim()
        cache[q]?.let { return@withContext Result.success(it) }
        val key = LuxoCompanionApi.loadKey() ?: return@withContext Result.failure(IllegalStateException("لم يتم ضبط مفتاح «رفيق Luxo»"))
        val prompt = "يبحث مستخدم عن فيلم أو مسلسل أو أنمي بهذا الوصف: «$q».\n" +
            "اقترح حتى 12 عملاً حقيقياً هي الأقرب للوصف، الأكثر تطابقاً أولاً. استبعد الأعمال العربية. " +
            "لكل عمل: العنوان الإنجليزي الرسمي كما في TMDB، سنة الإصدار، النوع movie أو tv، وسبب قصير جداً بالعربية بصيغة المجهول (مثل: «تُروى فيه قصة…»).\n" +
            "أعد JSON فقط: {\"items\":[{\"title\":\"\",\"year\":2010,\"type\":\"tv\",\"why\":\"\"}]}"
        val answer = LuxoCompanionApi.ask(key, prompt)
        val obj = LuxoTmdbLite.jsonBlock(answer) ?: return@withContext Result.failure(IllegalStateException(answer.take(160)))
        val items = (obj["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val hits = coroutineScope {
            items.take(12).map { item ->
                async {
                    val title = (item["title"] as? JsonPrimitive)?.contentOrNull ?: return@async null
                    val year = (item["year"] as? JsonPrimitive)?.contentOrNull?.take(4)
                    val type = if ((item["type"] as? JsonPrimitive)?.contentOrNull == "movie") "movie" else "tv"
                    val why = (item["why"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    resolve(title, year, type)?.let { LuxoDescribeHit(it.first, it.second ?: year, why) }
                }
            }.awaitAll().filterNotNull().distinctBy { it.preview.id }
        }
        if (hits.isNotEmpty()) cache[q] = hits
        Result.success(hits)
    }

    /** TMDB lookup by title (+ year when it helps), trying the other type when nothing matches. */
    private suspend fun resolve(title: String, year: String?, type: String): Pair<MetaPreview, String?>? {
        for (t in listOf(type, if (type == "movie") "tv" else "movie")) {
            val yearKey = if (t == "movie") "year" else "first_air_date_year"
            val attempts = listOfNotNull(year?.let { mapOf(yearKey to it) }, emptyMap())
            for (extra in attempts) {
                val res = LuxoTmdbLite.get("search/$t", mapOf("query" to LuxoTmdbLite.enc(title), "language" to "ar") + extra) ?: continue
                val hit = res.a("results").firstOrNull() ?: continue
                val id = hit.i("id") ?: continue
                val date = hit.s("release_date") ?: hit.s("first_air_date")
                return MetaPreview(
                    id = "tmdb:$id",
                    type = if (t == "movie") "movie" else "series",
                    name = hit.s("title") ?: hit.s("name") ?: title,
                    poster = LuxoTmdbLite.img(hit.s("poster_path")),
                ) to date?.take(4)
            }
        }
        return null
    }
}

/** The two chips under the search box: description search and voice search. */
@Composable
fun LuxoSearchExtrasRow(onVoiceText: (String) -> Unit, onOpen: (MetaPreview) -> Unit, modifier: Modifier = Modifier) {
    val features = rememberLuxoFeatures()
    if (!features.describeSearch) return
    var open by remember { mutableStateOf(false) }
    val voice = rememberLuxoVoiceInput(onVoiceText)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExtraChip("🔮 البحث بالوصف") { open = true }
        if (voice != null) ExtraChip("🎙️ البحث بالصوت", voice)
    }
    if (open) LuxoDescribeSearchDialog(onOpen = { open = false; onOpen(it) }, onDismiss = { open = false })
}

@Composable
private fun ExtraChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Brush.horizontalGradient(listOf(DAccent1.copy(alpha = 0.6f), DAccent2.copy(alpha = 0.6f))), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun LuxoDescribeSearchDialog(onOpen: (MetaPreview) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<LuxoDescribeHit>?>(null) }
    val scope = rememberCoroutineScope()
    fun run() {
        if (text.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            LuxoDescribeSearch.find(text)
                .onSuccess { hits = it; if (it.isEmpty()) error = "لم يُعثر على أعمال مطابقة، يمكن تجربة وصف آخر" }
                .onFailure { error = it.message ?: "تعذّر البحث" }
            loading = false
        }
    }
    val voice = rememberLuxoVoiceInput { text = it; run() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF14121C))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔮 البحث بالوصف", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("يُكتب وصف القصة أو مشهد يُتذكّر، ويتم إيجاد العمل", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("مثال: مسلسل كوري عن محامية عبقرية تحب الحيتان") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                trailingIcon = if (voice != null) {
                    { IconButton(onClick = voice) { Icon(Icons.Rounded.Mic, "بالصوت", tint = DAccent2) } }
                } else {
                    null
                },
            )
            Box(
                Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(DAccent1, DAccent2)))
                    .clickable { run() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) { Text(if (loading) "جارٍ البحث…" else "ابحث", color = Color.White, fontWeight = FontWeight.Bold) }
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DAccent1) }
                error != null -> Text(error!!, color = Color(0xFFFFB4A9), fontSize = 13.sp)
            }
            val list = hits
            if (!loading && !list.isNullOrEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(118.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                ) {
                    items(list, key = { it.preview.id }) { hit -> HitCard(hit) { onOpen(hit.preview) } }
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: LuxoDescribeHit, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (hit.preview.poster != null) {
                AsyncImage(model = hit.preview.poster, contentDescription = hit.preview.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(hit.preview.name, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(8.dp), fontSize = 12.sp)
            }
        }
        Text(hit.preview.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        val sub = listOfNotNull(hit.year, if (hit.preview.type == "movie") "فيلم" else "مسلسل").joinToString(" · ")
        Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        if (hit.why.isNotBlank()) Text(hit.why, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}


@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.home.HomeRepository
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private enum class TonightMood(val label: String, val genres: List<String>, val promptLabel: String) {
    LIGHT("خفيف ومضحك", listOf("comedy", "animation", "family"), "شيء خفيف ومضحك"),
    ACTION("أكشن وحماس", listOf("action", "adventure", "thriller"), "أكشن وحماس"),
    SCARY("رعب وتشويق", listOf("horror", "thriller", "mystery"), "رعب وتشويق"),
    ROMANCE("رومانسي", listOf("romance", "drama"), "رومانسي"),
    DEEP("دراما عميقة", listOf("drama", "history", "war", "crime"), "دراما عميقة ومؤثرة"),
    FAMILY("عائلي", listOf("family", "animation", "adventure"), "مناسب للعائلة"),
}

private enum class TonightTime(val label: String, val type: String?) {
    MOVIE("فيلم الليلة", "movie"),
    SERIES("مسلسل أبدأه", "series"),
    ANY("ما يهم", null),
}

private data class TonightPick(val item: MetaPreview, val reason: String?)

/** A card that opens «وش أشوف الليلة؟». */
@Composable
internal fun LuxoTonightCard(
    onOpen: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF7B5CFF).copy(alpha = 0.35f), Color(0xFFFF4F7B).copy(alpha = 0.25f), Color(0xFFFFC46B).copy(alpha = 0.18f)),
                ),
            )
            .clickable { open = true }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("✨", style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(lx("وش أشوف الليلة؟", "What should I watch tonight?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            Text(
                lx("اختر مزاجك ونقترح لك من الكتالوج", "Pick a mood and get picks from your catalog"),
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
            )
        }
    }
    if (open) {
        LuxoTonightSheet(
            onDismiss = { open = false },
            onOpen = { item ->
                open = false
                onOpen(item)
            },
        )
    }
}

@Composable
internal fun LuxoTonightSheet(onDismiss: () -> Unit, onOpen: (MetaPreview) -> Unit) {
    var mood by remember { mutableStateOf(TonightMood.LIGHT) }
    var time by remember { mutableStateOf(TonightTime.ANY) }
    var busy by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<List<TonightPick>?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("وش أشوف الليلة؟ ✨", style = MaterialTheme.typography.titleLarge)
            Text("المزاج", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TonightMood.entries.forEach { option ->
                    FilterChip(selected = mood == option, onClick = { mood = option }, label = { Text(option.label) })
                }
            }
            Text("وش تبي؟", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TonightTime.entries.forEach { option ->
                    FilterChip(selected = time == option, onClick = { time = option }, label = { Text(option.label) })
                }
            }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    picks = null
                    scope.launch {
                        picks = pickTonight(mood, time)
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("اقترح لي") }

            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            picks?.let { list ->
                if (list.isEmpty()) {
                    Text(
                        "ما لقيت شي مناسب في الكتالوج الحالي. جرّب مزاج ثاني.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                list.forEach { pick ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { onOpen(pick.item) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = pick.item.poster ?: pick.item.rawPosterUrl,
                            contentDescription = pick.item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(64.dp).size(width = 64.dp, height = 96.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pick.item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            val meta = listOfNotNull(pick.item.releaseInfo, pick.item.imdbRating?.let { "★ $it" }).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            pick.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

private val pickJson = Json { ignoreUnknownKeys = true; isLenient = true }

private suspend fun pickTonight(mood: TonightMood, time: TonightTime): List<TonightPick> {
    val smart = listOf(LuxoContentType.MOVIE, LuxoContentType.SERIES)
        .flatMap { type -> runCatching { LuxoSmartCollections.load(type) }.getOrDefault(emptyList()) }
    val candidates = (HomeRepository.uiState.value.sections + smart)
        .flatMap { it.items }
        .distinctBy { "${it.type}:${it.id}" }
        .filter { time.type == null || it.type == time.type }
        .take(90)
    if (candidates.isEmpty()) return emptyList()

    val key = LuxoCompanionApi.loadKey()
    if (key != null) {
        val list = candidates.joinToString("\n") { item ->
            "${item.id}|${item.name}|${item.type}|${item.releaseInfo.orEmpty()}|${item.genres.joinToString("/")}|${item.imdbRating.orEmpty()}"
        }
        val prompt = """
            أنت «رفيق Luxo». المشاهد يريد الليلة: ${mood.promptLabel}${time.type?.let { if (it == "movie") "، فيلم" else "، مسلسل" } ?: ""}.
            اختر 5 أعمال فقط من القائمة التالية (المعرّف|الاسم|النوع|السنة|التصنيفات|التقييم) تناسب طلبه.
            أجب بصيغة JSON فقط بدون أي نص آخر: [{"id":"...","reason":"سبب قصير بالعربية بدون حرق"}]
            القائمة:
            $list
        """.trimIndent()
        val answer = LuxoCompanionApi.ask(key, prompt)
        val parsed = runCatching {
            val start = answer.indexOf('[')
            val end = answer.lastIndexOf(']')
            pickJson.parseToJsonElement(answer.substring(start, end + 1)).jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val item = candidates.firstOrNull { it.id == id } ?: return@mapNotNull null
                TonightPick(item, obj["reason"]?.jsonPrimitive?.content)
            }
        }.getOrNull()
        if (!parsed.isNullOrEmpty()) return parsed.take(5)
    }

    // Without the companion key (or if it fails): match by genre, best rated first.
    return candidates
        .map { item ->
            val genres = item.genres.map { it.lowercase() }
            val score = mood.genres.count { wanted -> genres.any { it.contains(wanted) } }
            item to score
        }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<MetaPreview, Int>> { it.second }.thenByDescending { it.first.imdbRating?.toDoubleOrNull() ?: 0.0 })
        .shuffled(kotlin.random.Random(mood.ordinal * 31 + time.ordinal))
        .sortedByDescending { it.second }
        .take(5)
        .map { (item, _) -> TonightPick(item, null) }
}

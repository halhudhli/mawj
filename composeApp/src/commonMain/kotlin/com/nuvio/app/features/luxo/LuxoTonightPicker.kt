@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.nuvio.app.features.luxo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.sp
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
private fun TonightChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.linearGradient(LuxoAssistantColors.take(2))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))),
            )
            .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
internal fun LuxoTonightSheet(onDismiss: () -> Unit, onOpen: (MetaPreview) -> Unit) {
    var mood by remember { mutableStateOf(TonightMood.LIGHT) }
    var time by remember { mutableStateOf(TonightTime.ANY) }
    var busy by remember { mutableStateOf(false) }
    var picks by remember { mutableStateOf<List<TonightPick>?>(null) }
    var wish by remember { mutableStateOf("") }
    var lastWish by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun run(freeText: String?) {
        if (busy) return
        busy = true
        picks = null
        lastWish = freeText
        scope.launch {
            picks = pickTonight(mood, time, freeText)
            busy = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LuxoAssistantSheetColor,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LuxoAssistantHeader(
                title = "وش أشوف الليلة؟",
                subtitle = if (busy) "يدوّر لك على شي يناسبك…" else "اختر مزاجك أو اكتب اللي في بالك",
                thinking = busy,
            )
            Text("المزاج", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TonightMood.entries.forEach { option -> TonightChip(option.label, mood == option) { mood = option } }
            }
            Text("وش تبي؟", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TonightTime.entries.forEach { option -> TonightChip(option.label, time == option) { time = option } }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(LuxoAssistantColors))
                    .clickable(enabled = !busy) { run(null) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("اقترح لي ✨", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }

            LuxoAskBar(
                value = wish,
                onValueChange = { wish = it },
                placeholder = "أو اكتب: «أبغى فيلم خفيف عن السفر»",
                enabled = !busy,
                onSend = {
                    val w = wish.trim()
                    wish = ""
                    run(w)
                },
            )
            lastWish?.let { LuxoChatBubble(it, fromUser = true) }

            if (busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LuxoTypingDots() }
            }
            picks?.let { list ->
                if (list.isEmpty()) {
                    LuxoChatBubble("ما لقيت شي يناسب هالطلب في كتالوجك الحالي. جرّب مزاج ثاني أو اكتب طلبك بشكل مختلف.", fromUser = false)
                }
                list.forEach { pick ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .clickable { onOpen(pick.item) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = luxoHighResImage(pick.item.poster ?: pick.item.rawPosterUrl),
                            contentDescription = pick.item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 70.dp, height = 105.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pick.item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val meta = listOfNotNull(pick.item.releaseInfo, pick.item.imdbRating?.let { "★ $it" }).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            pick.reason?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

private val pickJson = Json { ignoreUnknownKeys = true; isLenient = true }

private suspend fun pickTonight(mood: TonightMood, time: TonightTime, freeText: String? = null): List<TonightPick> {
    // Speed: the home rows are already loaded; only fetch smart collections when they're thin.
    val home = HomeRepository.uiState.value.sections
    val smart = if (home.sumOf { it.items.size } >= 60) emptyList() else listOf(LuxoContentType.MOVIE, LuxoContentType.SERIES)
        .flatMap { type -> runCatching { LuxoSmartCollections.load(type) }.getOrDefault(emptyList()) }
    val candidates = (home + smart)
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
            أنت «رفيق Luxo». ${if (!freeText.isNullOrBlank()) "طلب المشاهد بكلماته: «$freeText»" else "المشاهد يريد الليلة: ${mood.promptLabel}${time.type?.let { if (it == "movie") "، فيلم" else "، مسلسل" } ?: ""}"}.
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

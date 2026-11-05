package com.nuvio.app.features.details

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import com.nuvio.app.features.updater.LuxoGeminiConfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.addons.httpPostJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * LUXO: «رفيق Luxo» — a Gemini-powered companion on the details page.
 * Summaries without spoilers, cast bios in Arabic, and free questions about the title.
 * The Gemini API key (from Google AI Studio) is entered once inside the sheet and stored on the device.
 */
@Composable
internal fun LuxoCompanion(meta: MetaDetails, modifier: Modifier = Modifier) {
    LuxoCompanionPanel(
        subjectKey = meta.id,
        subjectTitle = meta.name,
        context = LuxoCompanionApi.context(meta),
        quickActions = buildList {
            luxoLastWatchedEpisode(meta.id)?.let { (season, episode) ->
                add("ملخص ما سبق" to LuxoCompanionApi.recapPrompt(season, episode))
            }
            add("ملخص بدون حرق" to LuxoCompanionApi.SUMMARY)
            add("عن الممثلين" to LuxoCompanionApi.CAST)
            add("لماذا يُشاهَد؟" to LuxoCompanionApi.WHY)
            add("أعمال تشبهه" to LuxoCompanionApi.SIMILAR)
            if (com.nuvio.app.features.luxo.rememberLuxoFeatures().endingExplained) add(ENDING_LABEL to LuxoCompanionApi.ENDING)
        },
        questionLabel = "اسأل عن العمل (بدون حرق)",
        modifier = modifier,
    )
}

/** Quick actions that reveal the plot; the sheet asks before sending them. */
internal const val ENDING_LABEL = "شرح النهاية ⚠️"

/** «رفيق Luxo» on an actor's page: ask about the person, their career and best work. */
@Composable
internal fun LuxoPersonCompanion(person: PersonDetail, modifier: Modifier = Modifier) {
    LuxoCompanionPanel(
        subjectKey = "person:${person.tmdbId}",
        subjectTitle = person.name,
        context = LuxoCompanionApi.personContext(person),
        quickActions = listOf(
            "نبذة عنه" to LuxoCompanionApi.PERSON_BIO,
            "أشهر أعماله" to LuxoCompanionApi.PERSON_BEST,
            "من وين أبدأ؟" to LuxoCompanionApi.PERSON_START,
            "معلومات طريفة" to LuxoCompanionApi.PERSON_TRIVIA,
        ),
        questionLabel = "اسأل عن ${person.name}",
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuxoCompanionPanel(
    subjectKey: String,
    subjectTitle: String,
    context: String,
    quickActions: List<Pair<String, String>>,
    questionLabel: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    ExtendedFloatingActionButton(
        onClick = { open = true },
        modifier = modifier.navigationBarsPadding().padding(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        text = { Text("رفيق موج") },
        icon = { Text("✨") },
    )
    if (!open) return
    LuxoCompanionSheet(
        subjectKey = subjectKey,
        subjectTitle = subjectTitle,
        context = context,
        quickActions = quickActions,
        questionLabel = questionLabel,
        onDismiss = { open = false },
    )
}

/** The companion sheet itself; [onDismiss] closes it. Used by the floating button and the player. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxoCompanionSheet(
    subjectKey: String,
    subjectTitle: String,
    context: String,
    quickActions: List<Pair<String, String>>,
    questionLabel: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(LuxoCompanionApi.loadKey()) }
    var keyDraft by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    // The conversation: (fromUser, text). Kept per title while the app runs.
    val messages = remember(subjectKey) { LuxoCompanionApi.conversation(subjectKey) }
    var busy by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // A quick action that reveals the plot waits here until it is confirmed.
    var spoilerAsk by remember { mutableStateOf<Pair<String, String>?>(null) }
    // When the question was spoken, the answer is read aloud.
    var speakReplies by remember { mutableStateOf(false) }
    val voiceOn = com.nuvio.app.features.luxo.rememberLuxoFeatures().companionVoice
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { com.nuvio.app.features.luxo.luxoStopSpeaking() } }

    fun send(label: String, prompt: String, freeForm: Boolean) {
        val k = key ?: return
        if (busy) return
        messages.add(true to label)
        busy = true
        scope.launch {
            listState.animateScrollToItem(messages.size)
            // Recent turns give follow-up questions their context.
            val history = messages.dropLast(1).takeLast(6).joinToString("\n") { (user, text) ->
                (if (user) "المشاهد: " else "رفيق موج: ") + text.take(600)
            }
            val fullPrompt = buildString {
                append(context)
                if (history.isNotBlank()) append("\n\nالمحادثة السابقة:\n").append(history)
                append("\n\n").append(prompt)
            }
            // Web search only for free questions (it's slower); quick actions answer from the model directly.
            val reply = LuxoCompanionApi.ask(k, fullPrompt, search = freeForm)
            messages.add(false to reply)
            if (speakReplies && voiceOn) com.nuvio.app.features.luxo.luxoSpeak(reply)
            busy = false
            listState.animateScrollToItem(messages.size)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.nuvio.app.features.luxo.LuxoAssistantSheetColor,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.nuvio.app.features.luxo.LuxoAssistantHeader(
                title = "رفيق موج",
                subtitle = if (busy) "يفكّر…" else subjectTitle,
                thinking = busy,
            )

            if (key == null) {
                Text(
                    "يحتاج مفتاح Gemini مرة وحدة: افتح aistudio.google.com ← Get API key ← انسخ المفتاح والصقه هنا. المفتاح ينحفظ على هذا الجهاز فقط.",
                    color = Color.White.copy(alpha = 0.8f),
                )
                com.nuvio.app.features.luxo.LuxoAskBar(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    placeholder = "الصق مفتاح Gemini",
                    enabled = true,
                    onSend = {
                        val k = keyDraft.trim()
                        if (k.isNotEmpty()) {
                            LuxoCompanionApi.saveKey(k)
                            key = k
                        }
                    },
                )
                return@Column
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            "أهلاً! يمكن اختيار سؤال جاهز أو كتابة سؤال تحت 👇",
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(messages.size) { index ->
                    val (fromUser, text) = messages[index]
                    com.nuvio.app.features.luxo.LuxoChatBubble(text, fromUser)
                }
                if (busy) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            com.nuvio.app.features.luxo.LuxoTypingDots()
                        }
                    }
                }
            }

            com.nuvio.app.features.luxo.LuxoSuggestionChips(
                quickActions.map { (label, prompt) ->
                    label to { if (label == ENDING_LABEL) spoilerAsk = label to prompt else { speakReplies = false; send(label, prompt, freeForm = false) } }
                },
                enabled = !busy,
            )
            spoilerAsk?.let { (label, prompt) ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { spoilerAsk = null },
                    title = { Text("تنبيه: حرق للأحداث") },
                    text = { Text("يكشف شرح النهاية أحداث العمل كاملة. هل يُعرض الشرح؟") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { spoilerAsk = null; speakReplies = false; send(label, prompt, freeForm = false) }) { Text("عرض الشرح") }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { spoilerAsk = null }) { Text("إلغاء") } },
                )
            }
            val voice = com.nuvio.app.features.luxo.rememberLuxoVoiceInput { spoken ->
                val q = spoken.trim()
                if (q.isNotEmpty()) {
                    speakReplies = true
                    send(q, "سؤال المشاهد: $q", freeForm = true)
                }
            }
            if (voiceOn && voice != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🎙️ سؤال بالصوت",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(enabled = !busy) { com.nuvio.app.features.luxo.luxoStopSpeaking(); voice() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    if (speakReplies) {
                        Text(
                            "⏹ إيقاف القراءة",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { com.nuvio.app.features.luxo.luxoStopSpeaking(); speakReplies = false }.padding(6.dp),
                        )
                    }
                }
            }
            com.nuvio.app.features.luxo.LuxoAskBar(
                value = question,
                onValueChange = { question = it },
                placeholder = questionLabel,
                enabled = !busy,
                onSend = {
                    val q = question.trim()
                    question = ""
                    speakReplies = false
                    send(q, "سؤال المشاهد: $q", freeForm = true)
                },
            )
            Text(
                "تغيير المفتاح",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable {
                        LuxoCompanionApi.saveKey("")
                        key = null
                    },
            )
        }
    }
}

internal object LuxoCompanionApi {
    private const val KEY_PATH = "luxo/gemini_api_key"
    private val json = Json { ignoreUnknownKeys = true }

    const val SUMMARY = "اكتب ملخصاً قصيراً وممتعاً للقصة بدون أي حرق للأحداث، في 4 إلى 6 أسطر."
    const val ENDING = "اشرح نهاية هذا العمل بوضوح: ماذا حدث في الختام، ومعناه، وما الرموز أو الأسئلة المفتوحة التي تركها، وأبرز تفسيرات المشاهدين إن وُجدت. هذا الطلب يقبل الحرق صراحةً. إن كان العمل مسلسلاً مستمراً فاشرح نهاية آخر موسم معروض."
    const val CAST = "عرّف بأبرز الممثلين وصنّاع العمل: نبذة قصيرة عن كل واحد وأشهر أعماله، بدون حرق."
    const val WHY = "لماذا قد يستمتع المشاهد بهذا العمل؟ اذكر نقاط قوته وأجواءه ولمن يناسب، بدون حرق."
    const val SIMILAR = "اقترح 5 أعمال مشابهة مع سطر واحد لكل عمل يوضح وجه الشبه."

    const val PERSON_BIO = "اكتب نبذة مختصرة وممتعة عن هذا الشخص: بدايته ومسيرته وأبرز محطاته، في 6 إلى 8 أسطر."
    const val PERSON_BEST = "ما أشهر وأفضل أعماله؟ اذكر 5 إلى 8 أعمال مع سنة كل عمل وسطر عن دوره فيه."
    const val PERSON_START = "لو أبغى أبدأ أتابع أعماله، بأي عمل أبدأ؟ رتّب لي 3 إلى 5 أعمال مع السبب باختصار."
    const val PERSON_TRIVIA = "اذكر 5 معلومات طريفة أو غير معروفة عنه، مؤكدة وغير مختلقة."

    fun personContext(person: PersonDetail): String = buildString {
        append("أنت «رفيق موج»، مساعد عربي ودود داخل تطبيق مشاهدة. اكتب بالعربية الفصحى السهلة، باختصار ووضوح، ولا تختلق معلومات؛ إذا لم تكن متأكداً فقل ذلك.\n")
        append("الشخص: ").append(person.name)
        person.knownFor?.let { append("\nمعروف بـ: ").append(it) }
        person.birthday?.let { append("\nتاريخ الميلاد: ").append(it) }
        person.deathday?.let { append("\nتاريخ الوفاة: ").append(it) }
        person.placeOfBirth?.let { append("\nمكان الميلاد: ").append(it) }
        val credits = (person.movieCredits + person.tvCredits).take(20).joinToString("، ") { credit ->
            credit.releaseInfo?.takeIf { it.isNotBlank() }?.let { "${credit.name} ($it)" } ?: credit.name
        }
        if (credits.isNotBlank()) append("\nمن أعماله: ").append(credits)
        person.biography?.let { append("\nالسيرة من TMDB: ").append(it.take(1500)) }
    }

    /** Recap of everything up to (and including) the episode the viewer reached — nothing after it. */
    fun recapPrompt(season: Int, episode: Int): String =
        "المشاهد توقف عند الموسم $season الحلقة $episode. لخّص له أحداث المسلسل من البداية حتى نهاية هذه الحلقة فقط " +
            "بنقاط مرتبة، ليتذكر القصة قبل أن يكمل. ممنوع ذكر أي حدث يقع بعد هذه الحلقة."

    fun loadKey(): String? = AppFileCache.read(KEY_PATH)?.trim()?.takeIf { it.isNotEmpty() }
        ?: LuxoGeminiConfig.KEY.trim().takeIf { it.isNotEmpty() }
    fun saveKey(value: String) = AppFileCache.write(KEY_PATH, value)

    fun context(meta: MetaDetails): String = buildString {
        append("أنت «رفيق موج»، مساعد عربي ودود داخل تطبيق مشاهدة. اكتب بالعربية الفصحى السهلة، باختصار ووضوح، ولا تحرق أي أحداث مهمة أبداً.\n")
        append("العمل: ").append(meta.name)
        meta.releaseInfo?.let { append(" (").append(it).append(")") }
        append("\nالنوع: ").append(meta.type)
        if (meta.genres.isNotEmpty()) append("\nالتصنيفات: ").append(meta.genres.joinToString("، "))
        if (meta.cast.isNotEmpty()) append("\nالممثلون: ").append(meta.cast.take(10).joinToString("، ") { it.name })
        meta.description?.let { append("\nالوصف الرسمي: ").append(it.take(1200)) }
    }

    /** Models to try, best first; filled from the key's own model list the first time. */
    private var modelChain: List<String>? = null
    /** Luxo's chosen model: fast, stable, supports Google Search grounding. */
    private const val PINNED_MODEL = "gemini-3.5-flash-lite"
    private val fallbackModels = listOf(PINNED_MODEL, "gemini-flash-lite-latest", "gemini-flash-latest", "gemini-2.5-flash-lite")

    private val conversations = mutableMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<Pair<Boolean, String>>>()

    /** The conversation for a title, kept while the app runs so reopening continues it. */
    fun conversation(subjectKey: String): androidx.compose.runtime.snapshots.SnapshotStateList<Pair<Boolean, String>> =
        conversations.getOrPut(subjectKey) { androidx.compose.runtime.mutableStateListOf() }

    // Speed: go straight to the pinned model; the network model list is only a fallback.
    private suspend fun models(key: String): List<String> = fallbackModels.ifEmpty { listedModels(key) }

    private suspend fun listedModels(key: String): List<String> {
        modelChain?.let { return it }
        val listed = runCatching {
            val response = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                com.nuvio.app.features.addons.httpRequestRaw(
                    method = "GET",
                    url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                    headers = mapOf("x-goog-api-key" to key.trim()),
                    body = "",
                )
            } ?: return@runCatching emptyList()
            val arr = json.parseToJsonElement(response.body).jsonObject["models"] as? JsonArray ?: return@runCatching emptyList()
            arr.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@mapNotNull null
                val methods = (obj["supportedGenerationMethods"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                name.takeIf { "generateContent" in methods && "flash" in it && "image" !in it && "tts" !in it && "audio" !in it && "live" !in it }
            }
        }.getOrDefault(emptyList())
        // Prefer the moving "-latest" aliases, then stable names, then previews; flash before flash-lite.
        val ranked = listed.sortedWith(
            compareBy<String>(
                { if (it.endsWith("-latest")) 0 else 1 },
                { if ("preview" in it || "exp" in it) 1 else 0 },
                { if ("lite" in it) 1 else 0 },
            ).thenByDescending { it },
        )
        // The pinned model always goes first, then the others only as a safety net.
        val chain = (listOf(PINNED_MODEL) + fallbackModels.filter { listed.isEmpty() || it in listed } + ranked).distinct().take(5)
        if (listed.isNotEmpty()) modelChain = chain
        return chain.ifEmpty { fallbackModels }
    }

    /**
     * Asks Gemini. Busy/overloaded models (503/429) or missing ones (404) fall through to the next
     * model, so "high demand" on one model doesn't leave the companion silent. With [search] the
     * answer can use Google Search for fresh facts.
     */
    suspend fun ask(key: String, prompt: String, search: Boolean = false, image: Pair<String, String>? = null): String =
        try {
            var lastError: String? = null
            for (model in models(key)) {
                val attempts = if (model == PINNED_MODEL) listOf(true, true, false) else listOf(true, false)
                val searchModes = if (search) attempts.map { true } + false else attempts.map { false }
                var tries = 0
                var searchRefused = false
                for (searchWanted in searchModes) {
                    val withSearch = searchWanted && !searchRefused
                    tries += 1
                    val body = buildJsonObject {
                        put("contents", buildJsonArray {
                            add(buildJsonObject {
                                put("parts", buildJsonArray {
                                    // [image] = (mime type, base64 data) for questions about a picture.
                                    image?.let { (mime, data) ->
                                        add(buildJsonObject { put("inline_data", buildJsonObject { put("mime_type", mime); put("data", data) }) })
                                    }
                                    add(buildJsonObject { put("text", prompt) })
                                })
                            })
                        })
                        if (withSearch) {
                            put("tools", buildJsonArray { add(buildJsonObject { put("google_search", buildJsonObject { }) }) })
                        }
                    }.toString()
                    // New AI Studio keys ("AQ." auth keys) only work in the x-goog-api-key header.
                    val response = kotlinx.coroutines.withTimeoutOrNull(60_000L) {
                        com.nuvio.app.features.addons.httpRequestRaw(
                            method = "POST",
                            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                            headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key.trim()),
                            body = body,
                        )
                    }
                    if (response == null) {
                        lastError = "تأخّر الرفيق في الرد"
                        break
                    }
                    val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
                    val error = (root?.get("error") as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                    if (response.status in 200..299 && error == null) {
                        val candidates = root?.get("candidates") as? JsonArray
                        val parts = (candidates?.firstOrNull() as? JsonObject)
                            ?.get("content")?.jsonObject?.get("parts") as? JsonArray
                        val text = parts?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                            ?.joinToString("")?.trim()?.takeIf { it.isNotEmpty() }
                        if (text != null) return text
                        lastError = "ما وصل رد"
                        break
                    }
                    lastError = error ?: "رمز ${response.status}"
                    when (response.status) {
                        // The search tool isn't allowed on this model/key: try the same model without it.
                        400 -> if (withSearch) {
                            searchRefused = true
                            continue
                        } else {
                            break
                        }
                        // Busy: wait a moment and retry the same model, then move on.
                        429, 500, 502, 503, 504 -> {
                            if (tries < searchModes.size) {
                                kotlinx.coroutines.delay(1_500L * tries)
                                continue
                            }
                            break
                        }
                        // Unknown model on this key: next model.
                        404 -> break
                        // Bad key or permission: no point trying other models.
                        else -> return "تعذّر رد الرفيق: $lastError"
                    }
                }
                kotlinx.coroutines.delay(400L)
            }
            "Gemini مشغول الآن على كل النماذج المتاحة (${lastError ?: "بدون تفاصيل"}). تُرجى المحاولة بعد دقيقة."
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            "تعذّر الاتصال بـ Gemini. يُرجى التأكد من الإنترنت ومن صحة المفتاح."
        }
}

/** Furthest episode (season, episode) the viewer reached for a series, from progress and watched history. */
internal fun luxoLastWatchedEpisode(metaId: String): Pair<Int, Int>? {
    val fromProgress = com.nuvio.app.features.watchprogress.WatchProgressRepository.uiState.value.entries
        .filter { it.parentMetaId == metaId }
        .mapNotNull { entry -> entry.seasonNumber?.let { s -> entry.episodeNumber?.let { e -> s to e } } }
    val fromWatched = com.nuvio.app.features.watched.WatchedRepository.uiState.value.items
        .filter { it.id == metaId }
        .mapNotNull { item -> item.season?.let { s -> item.episode?.let { e -> s to e } } }
    return (fromProgress + fromWatched)
        .filter { (season, _) -> season > 0 }
        .maxWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
}

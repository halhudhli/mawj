package com.nuvio.app.features.details

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxoCompanion(meta: MetaDetails, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ExtendedFloatingActionButton(
        onClick = { open = true },
        modifier = modifier.navigationBarsPadding().padding(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        text = { Text("رفيق Luxo") },
        icon = { Text("✨") },
    )
    if (!open) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(LuxoCompanionApi.loadKey()) }
    var keyDraft by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember(meta.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun ask(prompt: String) {
        val k = key ?: return
        busy = true
        answer = null
        scope.launch {
            answer = LuxoCompanionApi.ask(k, LuxoCompanionApi.context(meta) + "\n\n" + prompt)
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("رفيق Luxo ✨", style = MaterialTheme.typography.titleLarge)
            Text(meta.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (key == null) {
                Text(
                    "يحتاج مفتاح Gemini مرة وحدة: افتح aistudio.google.com ← Get API key ← انسخ المفتاح والصقه هنا. المفتاح ينحفظ على هذا الجهاز فقط.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("مفتاح Gemini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val k = keyDraft.trim()
                        if (k.isNotEmpty()) {
                            LuxoCompanionApi.saveKey(k)
                            key = k
                        }
                    },
                ) { Text("حفظ") }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { ask(LuxoCompanionApi.SUMMARY) }, label = { Text("ملخص بدون حرق") })
                AssistChip(onClick = { ask(LuxoCompanionApi.CAST) }, label = { Text("عن الممثلين") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { ask(LuxoCompanionApi.WHY) }, label = { Text("ليش أشاهده؟") })
                AssistChip(onClick = { ask(LuxoCompanionApi.SIMILAR) }, label = { Text("أعمال تشبهه") })
            }

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("اسأل عن العمل (بدون حرق)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = question.isNotBlank() && !busy,
                    onClick = { ask("سؤال المشاهد: ${question.trim()}") },
                ) { Text("اسأل") }
                TextButton(onClick = {
                    LuxoCompanionApi.saveKey("")
                    key = null
                }) { Text("تغيير المفتاح") }
            }

            if (busy) {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) { CircularProgressIndicator() }
            }
            answer?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                )
            }
        }
    }
}

internal object LuxoCompanionApi {
    private const val KEY_PATH = "luxo/gemini_api_key"
    private const val MODEL = "gemini-flash-latest"
    private val json = Json { ignoreUnknownKeys = true }

    const val SUMMARY = "اكتب ملخصاً قصيراً وممتعاً للقصة بدون أي حرق للأحداث، في 4 إلى 6 أسطر."
    const val CAST = "عرّف بأبرز الممثلين وصنّاع العمل: نبذة قصيرة عن كل واحد وأشهر أعماله، بدون حرق."
    const val WHY = "لماذا قد يستمتع المشاهد بهذا العمل؟ اذكر نقاط قوته وأجواءه ولمن يناسب، بدون حرق."
    const val SIMILAR = "اقترح 5 أعمال مشابهة مع سطر واحد لكل عمل يوضح وجه الشبه."

    fun loadKey(): String? = AppFileCache.read(KEY_PATH)?.trim()?.takeIf { it.isNotEmpty() }
    fun saveKey(value: String) = AppFileCache.write(KEY_PATH, value)

    fun context(meta: MetaDetails): String = buildString {
        append("أنت «رفيق Luxo»، مساعد عربي ودود داخل تطبيق مشاهدة. اكتب بالعربية الفصحى السهلة، باختصار ووضوح، ولا تحرق أي أحداث مهمة أبداً.\n")
        append("العمل: ").append(meta.name)
        meta.releaseInfo?.let { append(" (").append(it).append(")") }
        append("\nالنوع: ").append(meta.type)
        if (meta.genres.isNotEmpty()) append("\nالتصنيفات: ").append(meta.genres.joinToString("، "))
        if (meta.cast.isNotEmpty()) append("\nالممثلون: ").append(meta.cast.take(10).joinToString("، ") { it.name })
        meta.description?.let { append("\nالوصف الرسمي: ").append(it.take(1200)) }
    }

    suspend fun ask(key: String, prompt: String): String =
        try {
            val body = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                    })
                })
            }.toString()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$key"
            val root = json.parseToJsonElement(httpPostJson(url, body)).jsonObject
            val error = (root["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            if (error != null) {
                "ما قدر الرفيق يرد: $error"
            } else {
                val candidates = root["candidates"] as? JsonArray
                val parts = (candidates?.firstOrNull() as? JsonObject)
                    ?.get("content")?.jsonObject?.get("parts") as? JsonArray
                parts?.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "ما وصل رد، جرّب مرة ثانية."
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            "تعذّر الاتصال بـ Gemini. تأكد من الإنترنت ومن صحة المفتاح."
        }
}

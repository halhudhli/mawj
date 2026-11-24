package com.mawj.tv.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.MawjTv
import com.mawj.tv.data.Gemini
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.ui.C
import com.mawj.tv.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.ByteArrayOutputStream

/** What the companion knows: the work, where the viewer is, and the last subtitle lines heard. */
internal fun companionContext(req: PlayRequest, posMs: Long, durMs: Long, lines: List<String>): String = buildString {
    append("أنت «رفيق موج»، مساعد عربي ودود داخل مشغّل الفيديو. اكتب بالعربية الفصحى السهلة وباختصار ووضوح. ")
    append("ممنوع ذكر أي حدث بعد اللحظة التي وصل إليها المشاهد.\n")
    append("العمل: ").append(req.meta.name)
    req.meta.year?.let { append(" (").append(it).append(")") }
    req.episode?.let { append("\nالموسم ${it.season} الحلقة ${it.number}"); if (it.title.isNotBlank()) append(" «${it.title}»") }
    append("\nوصل المشاهد إلى الدقيقة ${posMs / 60_000} من ${durMs / 60_000}.")
    req.meta.description?.let { append("\nالوصف: ").append(it.take(800)) }
    if (lines.isNotEmpty()) append("\nآخر سطور الترجمة قبل هذه اللحظة:\n").append(lines.takeLast(20).joinToString("\n"))
}

/** «رفيق موج»: ready questions picked with the remote; the answer shows under them. */
@Composable
internal fun CompanionPanel(req: PlayRequest, posMs: Long, durMs: Long, lines: List<String>) {
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { first.requestFocus() } }
    val questions = listOf(
        "ماذا حدث حتى الآن؟" to "لخّص ما حدث حتى هذه اللحظة بنقاط قصيرة مرتبة، دون أي حرق لما بعدها.",
        "اشرح المشهد الحالي" to "اشرح ما يجري في المشهد الحالي اعتماداً على آخر سطور الترجمة، وما المقصود منه.",
        "من الشخصيات الرئيسية؟" to "عرّف بالشخصيات الرئيسية التي ظهرت حتى الآن بسطر لكل شخصية، دون حرق.",
        "معلومات عن العمل" to "اذكر معلومات ممتعة عن هذا العمل: الإنتاج والإخراج وما يميّزه، دون حرق للأحداث.",
    )
    PanelBox("رفيق موج", width = 430) {
        questions.forEachIndexed { i, (label, ask) ->
            ChoiceRow(label, false, if (i == 0) first else null) {
                if (busy) return@ChoiceRow
                busy = true; answer = null
                scope.launch {
                    answer = Gemini.ask(companionContext(req, posMs, durMs, lines) + "\n\nالمطلوب: " + ask) ?: "تعذّر الوصول إلى الرفيق الآن."
                    busy = false
                }
            }
        }
        if (busy) T("يفكّر الرفيق…", 13.sp, color = C.Gold)
        answer?.let {
            T(it, 14.sp, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().background(Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(12.dp))
        }
    }
}

/** The current frame as a small JPEG in base64 (for Gemini), or null. */
private suspend fun frameBase64(v: MpvView): String? = withContext(Dispatchers.IO) {
    val f = File(MawjTv.app.cacheDir, "frame.jpg").apply { delete() }
    if (!v.screenshot(f.absolutePath)) return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
    val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
    bmp.recycle()
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** «من في المشهد؟»: the frame and the cast list go to Gemini; it names who is on screen. */
@Composable
internal fun WhoPanel(req: PlayRequest, v: MpvView) {
    var answer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val img = frameBase64(v)
        if (img == null) { answer = "تعذّر التقاط المشهد من هذا المصدر."; return@LaunchedEffect }
        val cast = runCatching { com.mawj.tv.data.Tmdb.credits(req.meta.id, req.meta.type).first }.getOrDefault(emptyList())
            .take(20).joinToString("\n") { "${it.name} — ${it.role}" }
        answer = Gemini.ask(
            "هذه لقطة من «${req.meta.name}». " + (if (cast.isNotBlank()) "طاقم العمل:\n$cast\n" else "") +
                "من يظهر في اللقطة؟ اكتب بالعربية قائمة قصيرة: اسم الممثل — اسم الشخصية، ثم سطراً واحداً عمّا يجري. " +
                "إن لم تكن متأكداً فقل ذلك، ولا تخمّن أسماء خارج الطاقم.",
            img,
        ) ?: "تعذّر الوصول إلى الرفيق الآن."
    }
    PanelBox("من في المشهد؟", width = 430) {
        T(answer ?: "يُلتقط المشهد ويُحلَّل…", 14.sp, color = if (answer == null) C.Gold else C.Ink, lineHeight = 22.sp)
    }
}

private data class QuizQ(val q: String, val options: List<String>, val answer: Int)

/** «اختبار الحلقة»: four quick questions on what has been seen so far. */
@Composable
internal fun QuizPanel(req: PlayRequest, posMs: Long, durMs: Long, lines: List<String>) {
    var questions by remember { mutableStateOf<List<QuizQ>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var at by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val o = Gemini.obj(
            Gemini.ask(
                companionContext(req, posMs, durMs, lines) +
                    "\n\nاكتب 4 أسئلة اختيار من متعدد عمّا شوهد حتى الآن فقط (لا شيء بعده)، لكل سؤال 3 خيارات وخيار صحيح واحد. " +
                    "أعد JSON فقط بهذا الشكل: {\"questions\":[{\"q\":\"…\",\"options\":[\"…\",\"…\",\"…\"],\"answer\":0}]}",
            ),
        )
        questions = (o?.get("questions") as? JsonArray)?.mapNotNull { e ->
            val qo = e as? JsonObject ?: return@mapNotNull null
            val opts = (qo["options"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val ans = (qo["answer"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val q = (qo["q"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (opts.size < 2 || ans !in opts.indices) null else QuizQ(q, opts, ans)
        }?.takeIf { it.isNotEmpty() }
        failed = questions == null
    }
    LaunchedEffect(questions, at) { if (questions != null) { delay(60); runCatching { first.requestFocus() } } }
    LaunchedEffect(picked) {
        if (picked != null) { delay(1_300); picked = null; at++ }
    }
    PanelBox("اختبار الحلقة", width = 430) {
        val qs = questions
        when {
            failed -> T("تعذّر تجهيز الأسئلة الآن.", 14.sp, color = C.Muted)
            qs == null -> T("تُجهَّز الأسئلة…", 14.sp, color = C.Gold)
            at >= qs.size -> {
                T("النتيجة: $score من ${qs.size}", 20.sp, weight = FontWeight.Bold, color = C.GoldLight)
                T(if (score == qs.size) "متابعة دقيقة!" else "يُعاد الاختبار في وقت لاحق من الحلقة.", 13.sp, color = C.Muted)
            }
            else -> {
                val q = qs[at]
                T("السؤال ${at + 1} من ${qs.size}", 11.sp, color = C.Dim)
                T(q.q, 16.sp, weight = FontWeight.SemiBold, lineHeight = 24.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    q.options.forEachIndexed { i, opt ->
                        val mark = when {
                            picked == null -> ""
                            i == q.answer -> "  ✓"
                            i == picked -> "  ✗"
                            else -> ""
                        }
                        ChoiceRow(opt + mark, picked != null && i == q.answer, if (i == 0) first else null) {
                            if (picked != null) return@ChoiceRow
                            picked = i
                            if (i == q.answer) score++
                        }
                    }
                }
            }
        }
    }
}

/** «العبارات»: lines saved in this video; choosing one jumps back to it. */
@Composable
internal fun QuotesPanel(list: List<Pair<Long, String>>, onJump: (Long) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { first.requestFocus() } }
    PanelBox("العبارات", width = 430) {
        if (list.isEmpty()) T("لا توجد عبارات محفوظة. تُحفظ من «حفظ العبارة» والسطر ظاهر على الشاشة.", 13.sp, color = C.Muted, lineHeight = 20.sp)
        list.forEachIndexed { i, (ms, text) ->
            ChoiceRow(text.replace('\n', ' '), false, if (i == 0) first else null, sub = time(ms)) { onJump(ms) }
        }
    }
}

/** «لقطة شاشة»: saved in the app; the phone page («الربط بالجوال») shows and downloads them. */
internal suspend fun saveShot(v: MpvView, title: String, posMs: Long): Boolean = withContext(Dispatchers.IO) {
    val dir = File(MawjTv.app.filesDir, "shots").apply { mkdirs() }
    dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(40)?.forEach { it.delete() }
    val safe = title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "mawj" }
    v.screenshot(File(dir, "$safe-${posMs / 1000}.jpg").absolutePath)
}

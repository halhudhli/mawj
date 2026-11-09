package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.luxo.LuxoFeaturesRepository
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * «تحسين الصورة» and «تحسين الصوت» in the player menu.
 *  Picture: refresh-rate matching (both engines), smooth motion, upscaling and deband (libmpv).
 *  Sound: headphone surround and levelling (both engines), Bluetooth delay per headset (libmpv).
 * Upscale levels: 0 none, 1 sharp scaler (live action), 2 Anime4K light, 3 Anime4K strong.
 */
@Serializable
data class LuxoAvSettings(
    val matchRefresh: Boolean = true,
    val smoothMotion: Boolean = false,
    val upscale: Int = 0,
    val deband: Boolean = false,
    val spatial: Boolean = false,
    val levelling: Boolean = false,
    /** Audio advance in ms per output device name ("" = the device speaker). */
    val audioDelays: Map<String, Int> = emptyMap(),
)

object LuxoAvRepository {
    private const val KEY = "av_enhance_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(LuxoAvSettings())
    val state: StateFlow<LuxoAvSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        LuxoPrefsStorage.load(KEY)?.let { payload ->
            runCatching { json.decodeFromString(LuxoAvSettings.serializer(), payload) }.onSuccess { _state.value = it }
        }
        refreshRuntimeFlags()
    }

    fun current(): LuxoAvSettings {
        ensureLoaded()
        return _state.value
    }

    fun update(transform: (LuxoAvSettings) -> LuxoAvSettings) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(KEY, json.encodeToString(LuxoAvSettings.serializer(), next))
        refreshRuntimeFlags()
    }

    /** What the engines should apply, with the feature switches taken into account. */
    fun effective(settings: LuxoAvSettings = current()): LuxoAvSettings {
        val features = LuxoFeaturesRepository.current()
        var out = settings
        if (!features.picturePack) out = out.copy(matchRefresh = false, smoothMotion = false, upscale = 0, deband = false)
        if (!features.soundPack) out = out.copy(spatial = false, levelling = false, audioDelays = emptyMap())
        return out
    }

    /** True when a picture option that only libmpv can do is on (the next video then starts with libmpv). */
    fun wantsLibmpv(): Boolean = effective().let { it.smoothMotion || it.upscale > 0 || it.deband }

    fun delayFor(output: String?): Int = effective().audioDelays[output.orEmpty()] ?: 0

    fun refreshRuntimeFlags() {
        val e = effective(_state.value)
        LuxoAvRuntime.spatialOn = e.spatial
        LuxoAvRuntime.levellingOn = e.levelling
    }
}

/** Live values shared between the engines and the panels. */
object LuxoAvRuntime {
    /** Frame rate of the video now playing (0 = unknown / nothing playing). */
    var videoFps by mutableFloatStateOf(0f)
    /** True while the libmpv engine is playing. */
    var engineIsMpv by mutableStateOf(false)
    /** Headphones the sound plays on (null = device speaker). */
    var outputName by mutableStateOf<String?>(null)

    @kotlin.concurrent.Volatile
    var spatialOn: Boolean = false

    @kotlin.concurrent.Volatile
    var levellingOn: Boolean = false
}

private val panelAccent = Color(0xFFB59CFF)

@Composable
private fun AvHeader(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Text(
            "إغلاق",
            color = Color.White,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AvToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) Brush.linearGradient(listOf(Color(0xFF7B5CFF), Color(0xFFFF6FB5)))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.5f else 0.12f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AvNote(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
}

private fun fpsLabel(fps: Float): String {
    if (fps <= 0f) return "سرعة لقطات الفيديو: غير معروفة بعد"
    val rounded = (fps * 1000f).roundToInt() / 1000f
    return "سرعة لقطات الفيديو: $rounded لقطة في الثانية"
}

private val upscaleChoices = listOf(
    "بدون" to "الصورة كما هي",
    "حدّة طبيعية" to "تكبير أوضح للأفلام والمسلسلات الحية، خفيف على الجهاز",
    "أنمي · خفيف" to "Anime4K: خطوط أوضح وألوان أنظف للأنمي، مناسب لأغلب الأجهزة",
    "أنمي · قوي" to "Anime4K بجودة أعلى، يحتاج جهازاً قوياً وقد يسبب تقطيعاً في الأجهزة المتوسطة",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LuxoPicturePanel(visible: Boolean, onDismiss: () -> Unit) {
    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        val av by remember { LuxoAvRepository.ensureLoaded(); LuxoAvRepository.state }.collectAsState()
        val isMpv = LuxoAvRuntime.engineIsMpv
        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AvHeader(
                "تحسين الصورة ✨",
                if (isMpv) "المحرك الحالي: mpv · التغيير يظهر مباشرة" else "المحرك الحالي: ExoPlayer",
                onDismiss,
            )
            AvToggle(
                "مطابقة سرعة الشاشة مع الفيلم",
                "تُضبط سرعة تحديث الشاشة على مضاعف سرعة لقطات الفيلم لإزالة الرجفة. يعمل مع المحركين.\n${fpsLabel(LuxoAvRuntime.videoFps)}",
                av.matchRefresh,
            ) { v -> LuxoAvRepository.update { it.copy(matchRefresh = v) } }

            if (!isMpv) {
                AvNote("الخيارات التالية تعمل بمحرك mpv. عند تفعيل أيٍّ منها يبدأ الفيديو التالي بمحرك mpv تلقائياً، ويمكن إعادة فتح الفيديو الحالي لتطبيقها الآن.")
            }

            AvToggle(
                "نعومة الحركة",
                "تُمزج اللقطات على سرعة الشاشة فتصبح المشاهد المتحركة أنعم وأقل تقطيعاً. أقرب خيار لتقنية MEMC.",
                av.smoothMotion,
            ) { v -> LuxoAvRepository.update { it.copy(smoothMotion = v) } }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تحسين الدقة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upscaleChoices.forEachIndexed { index, (label, _) ->
                        AvChip(label, av.upscale == index) { LuxoAvRepository.update { it.copy(upscale = index) } }
                    }
                }
                Text(upscaleChoices[av.upscale.coerceIn(0, upscaleChoices.lastIndex)].second, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            AvToggle(
                "إزالة التكتل",
                "تُنعَّم التدرجات المتكتلة في السماء والظلال، وتفيد المصادر منخفضة الجودة.",
                av.deband,
            ) { v -> LuxoAvRepository.update { it.copy(deband = v) } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun LuxoSoundPanel(visible: Boolean, onDismiss: () -> Unit) {
    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        val av by remember { LuxoAvRepository.ensureLoaded(); LuxoAvRepository.state }.collectAsState()
        val isMpv = LuxoAvRuntime.engineIsMpv
        val output = LuxoAvRuntime.outputName
        val outputKey = output.orEmpty()
        val delay = av.audioDelays[outputKey] ?: 0
        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AvHeader("تحسين الصوت 🎧", if (isMpv) "المحرك الحالي: mpv" else "المحرك الحالي: ExoPlayer", onDismiss)
            AvToggle(
                "صوت محيطي للسماعات",
                if (isMpv) "يوسّع مساحة الصوت في السماعات فيبدو كأنه صادر من حولك."
                else "يوسّع مساحة الصوت في السماعات، ويستخدم الصوت المكاني في النظام إن كان الجهاز يدعمه. يُطبَّق الصوت المكاني من الفيديو التالي.",
                av.spatial,
            ) { v -> LuxoAvRepository.update { it.copy(spatial = v) } }
            AvToggle(
                "توحيد مستوى الصوت",
                "يثبّت مستوى الصوت بين المصادر والحلقات، فلا حاجة لتغيير الصوت مع كل ملف.",
                av.levelling,
            ) { v -> LuxoAvRepository.update { it.copy(levelling = v) } }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تعويض تأخير الصوت", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("الجهاز الحالي: ${output ?: "سماعة الجهاز"} · يُحفظ لكل سماعة على حدة", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Text(
                        "تصفير",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { LuxoAvRepository.update { it.copy(audioDelays = it.audioDelays - outputKey) } }
                            .padding(6.dp),
                    )
                }
                Text(
                    when {
                        delay > 0 -> "تقديم الصوت $delay مللي ثانية (للكلام المتأخر عن حركة الشفاه)"
                        delay < 0 -> "تأخير الصوت ${-delay} مللي ثانية"
                        else -> "بدون تعديل"
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
                Slider(
                    value = delay.toFloat(),
                    onValueChange = { raw ->
                        val v = ((raw / 10f).roundToInt() * 10)
                        LuxoAvRepository.update { s -> s.copy(audioDelays = if (v == 0) s.audioDelays - outputKey else s.audioDelays + (outputKey to v)) }
                    },
                    valueRange = -300f..500f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = panelAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isMpv) {
                    AvNote("يعوّض محرك ExoPlayer تأخير سماعات البلوتوث تلقائياً في أغلب الأجهزة. هذا الضبط يُطبَّق عند التشغيل بمحرك mpv.")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

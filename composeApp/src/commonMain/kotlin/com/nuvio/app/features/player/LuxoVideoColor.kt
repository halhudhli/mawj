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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Luxo picture colours for screens without HDR: ready looks plus free manual tuning on top.
 * Values are −50…+50. The engines (ExoPlayer colour matrix, libmpv video properties) read
 * [LuxoVideoColorRepository.effective] live, so a change shows on the running video at once.
 */
enum class LuxoColorPreset(val ar: String, val en: String, val hint: String) {
    ORIGINAL("الأصلي", "Original", "بدون أي تعديل"),
    SMART("ذكي ✨", "Smart ✨", "يكتشف الفيديو: لو HDR يرجّع له الإضاءة والألوان، وغيره لمسة طبيعية"),
    NATURAL("طبيعي", "Natural", "أوضح قليلاً بألوان حقيقية"),
    VIVID("زاهي", "Vivid", "ألوان قوية وتباين عالي"),
    CINEMA("سينما", "Cinema", "دافي وعميق مثل صالة السينما"),
    BRIGHT("ساطع", "Bright", "للغرف المضيئة والنهار"),
    HDR_RESCUE("إنقاذ HDR", "HDR rescue", "لأفلام HDR التي تظهر باهتة وخافتة على الشاشات العادية"),
    NIGHT("ليلي", "Night", "أدفأ وأريح للعين ليلاً"),
    ANIME("أنمي", "Anime", "ألوان مشبعة وخطوط واضحة للأنمي"),
}

@Serializable
data class LuxoVideoColor(
    val preset: String = LuxoColorPreset.ORIGINAL.name,
    /** Manual tuning added on top of the preset. */
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
    val warmth: Int = 0,
    /** Remember the look for every video (off = only for this session). */
    val remember: Boolean = true,
) {
    val presetValue: LuxoColorPreset
        get() = LuxoColorPreset.entries.firstOrNull { it.name == preset } ?: LuxoColorPreset.ORIGINAL
}

/** Final numbers sent to the engines, −50…+50 each. */
data class LuxoColorValues(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val gamma: Int = 0,
    val warmth: Int = 0,
) {
    val isNeutral: Boolean
        get() = brightness == 0 && contrast == 0 && saturation == 0 && gamma == 0 && warmth == 0

    operator fun plus(o: LuxoColorValues) = LuxoColorValues(
        (brightness + o.brightness).coerceIn(-50, 50),
        (contrast + o.contrast).coerceIn(-50, 50),
        (saturation + o.saturation).coerceIn(-50, 50),
        (gamma + o.gamma).coerceIn(-50, 50),
        (warmth + o.warmth).coerceIn(-50, 50),
    )

    /**
     * A 4×4 colour matrix (column-major, works on RGB in 0…1 with a 4th column of offsets) for
     * GPU colour effects. Gamma has no linear form, so here it lifts the mid-tones a little.
     */
    fun rgbMatrix(): FloatArray {
        val b = brightness / 100f * 0.5f + gamma / 100f * 0.12f
        val c = 1f + contrast / 100f
        val s = 1f + saturation / 100f
        val w = warmth / 100f * 0.18f
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f
        val sat = arrayOf(
            floatArrayOf(lr * (1 - s) + s, lg * (1 - s), lb * (1 - s)),
            floatArrayOf(lr * (1 - s), lg * (1 - s) + s, lb * (1 - s)),
            floatArrayOf(lr * (1 - s), lg * (1 - s), lb * (1 - s) + s),
        )
        val warm = floatArrayOf(1f + w, 1f + w * 0.15f, 1f - w)
        val offset = 0.5f * (1f - c) + b
        val m = FloatArray(16)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                m[col * 4 + row] = c * warm[row] * sat[row][col]
            }
            m[3 * 4 + row] = offset
        }
        m[3 * 4 + 3] = 1f
        return m
    }
}

private fun LuxoColorPreset.values(isHdr: Boolean): LuxoColorValues = when (this) {
    LuxoColorPreset.ORIGINAL -> LuxoColorValues()
    LuxoColorPreset.SMART -> if (isHdr) LuxoColorPreset.HDR_RESCUE.values(true) else LuxoColorValues(2, 5, 8, 2, 0)
    LuxoColorPreset.NATURAL -> LuxoColorValues(3, 6, 8, 2, 0)
    LuxoColorPreset.VIVID -> LuxoColorValues(3, 12, 26, 0, 2)
    LuxoColorPreset.CINEMA -> LuxoColorValues(-2, 12, -4, -2, 10)
    LuxoColorPreset.BRIGHT -> LuxoColorValues(12, 6, 6, 10, 0)
    LuxoColorPreset.HDR_RESCUE -> LuxoColorValues(10, 16, 22, 12, 2)
    LuxoColorPreset.NIGHT -> LuxoColorValues(-6, -4, -6, 0, 18)
    LuxoColorPreset.ANIME -> LuxoColorValues(4, 10, 30, 2, 0)
}

object LuxoVideoColorRepository {
    private const val KEY = "video_color_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(LuxoVideoColor())
    val state: StateFlow<LuxoVideoColor> = _state.asStateFlow()
    private var loaded = false

    /** Set by the engines: true while the current video is HDR (PQ / HLG). */
    val videoIsHdr = MutableStateFlow(false)

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        LuxoPrefsStorage.load(KEY)?.let { payload ->
            runCatching { json.decodeFromString(LuxoVideoColor.serializer(), payload) }.onSuccess { _state.value = it }
        }
    }

    fun current(): LuxoVideoColor {
        ensureLoaded()
        return _state.value
    }

    fun update(transform: (LuxoVideoColor) -> LuxoVideoColor) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        if (next.remember) LuxoPrefsStorage.save(KEY, json.encodeToString(LuxoVideoColor.serializer(), next))
        else LuxoPrefsStorage.save(KEY, json.encodeToString(LuxoVideoColor.serializer(), LuxoVideoColor(remember = false)))
    }

    /** What the engines should apply right now. Neutral when the feature is switched off. */
    fun effective(): LuxoColorValues {
        if (!com.nuvio.app.features.luxo.LuxoFeaturesRepository.current().videoColors) return LuxoColorValues()
        val c = current()
        return c.presetValue.values(videoIsHdr.value) + LuxoColorValues(c.brightness, c.contrast, c.saturation, c.gamma, c.warmth)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LuxoVideoColorPanel(visible: Boolean, onDismiss: () -> Unit) {
    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        val color by remember { LuxoVideoColorRepository.ensureLoaded(); LuxoVideoColorRepository.state }.collectAsState()
        val isHdr by LuxoVideoColorRepository.videoIsHdr.collectAsState()
        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ألوان الصورة 🎨", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (isHdr) "الفيديو HDR · يُنصح بـ «ذكي» أو «إنقاذ HDR»" else "التغيير يظهر على الفيديو مباشرة",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
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

            Text("تعديلات جاهزة", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LuxoColorPreset.entries.forEach { preset ->
                    val selected = color.presetValue == preset
                    Box(
                        Modifier
                            .background(
                                if (selected) Brush.linearGradient(listOf(Color(0xFF7B5CFF), Color(0xFFFF6FB5)))
                                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
                                RoundedCornerShape(14.dp),
                            )
                            .border(1.dp, Color.White.copy(alpha = if (selected) 0.5f else 0.12f), RoundedCornerShape(14.dp))
                            .clickable { LuxoVideoColorRepository.update { it.copy(preset = preset.name) } }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(preset.ar, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Text(color.presetValue.hint, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تعديل يدوي فوق الجاهز", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "تصفير",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { LuxoVideoColorRepository.update { it.copy(brightness = 0, contrast = 0, saturation = 0, gamma = 0, warmth = 0) } }
                        .padding(6.dp),
                )
            }
            LuxoColorSlider("☀️ الإضاءة", color.brightness) { v -> LuxoVideoColorRepository.update { it.copy(brightness = v) } }
            LuxoColorSlider("◐ التباين", color.contrast) { v -> LuxoVideoColorRepository.update { it.copy(contrast = v) } }
            LuxoColorSlider("🎨 تشبع الألوان", color.saturation) { v -> LuxoVideoColorRepository.update { it.copy(saturation = v) } }
            LuxoColorSlider("🌗 الظلال (جاما)", color.gamma) { v -> LuxoVideoColorRepository.update { it.copy(gamma = v) } }
            LuxoColorSlider("🔥 الدفء", color.warmth) { v -> LuxoVideoColorRepository.update { it.copy(warmth = v) } }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("احفظ الشكل لكل الفيديوهات", color = Color.White, fontSize = 14.sp)
                    Text("عند الإيقاف يرجع الأصلي في المرة الجاية", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Switch(checked = color.remember, onCheckedChange = { v -> LuxoVideoColorRepository.update { it.copy(remember = v) } })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LuxoColorSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (value > 0) "+$value" else "$value", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = -50f..50f,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFB59CFF),
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

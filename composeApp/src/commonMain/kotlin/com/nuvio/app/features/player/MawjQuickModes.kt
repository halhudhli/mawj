package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.rememberMawjStageOn
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.serialization.json.Json

/** «أوضاع بلمسة»: each mode sets picture and sound together; tapping it again puts back what was there. */
private enum class MawjQuickMode(val label: String, val hint: String, val apply: (LuxoAvSettings) -> LuxoAvSettings) {
    Evening("سهرة", "صورة أنقى وتدرّج ألوان ناعم", { it.copy(upscale = 1, deband = true, smoothMotion = false, levelling = false) }),
    Headphones("سماعة", "صوت مكاني حول الرأس", { it.copy(spatial = true, levelling = false) }),
    Travel("سفر", "أخف على البطارية", { it.copy(upscale = 0, deband = false, smoothMotion = false) }),
    QuietNight("هدوء الليل", "موازنة الصوت وخفض الانفجارات", { it.copy(levelling = true, spatial = false) }),
}

private const val ModeKey = "mawj_quick_mode"
private const val BeforeKey = "mawj_quick_mode_before"
private val modeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Composable
fun MawjQuickModes(modifier: Modifier = Modifier) {
    if (!rememberMawjStageOn()) return
    val tokens = MaterialTheme.nuvio
    var active by remember { mutableStateOf(LuxoPrefsStorage.load(ModeKey)) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("أوضاع بلمسة", color = tokens.colors.textPrimary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(
            "كل وضع يضبط الصورة والصوت دفعة واحدة، ولمسة ثانية تعيد ما كان.",
            color = Color(0xFF8796A8),
            fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MawjQuickMode.entries.forEach { mode ->
                val on = active == mode.name
                Column(
                    Modifier
                        .weight(1f)
                        .height(104.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (on) Modifier.background(tokens.colors.accent)
                            else Modifier.background(Color(0xFF0D1620)).border(1.dp, tokens.colors.accent.copy(alpha = 0.18f), RoundedCornerShape(20.dp)),
                        )
                        .clickable {
                            LuxoAvRepository.ensureLoaded()
                            if (on) {
                                // Put back the settings from before the first mode.
                                LuxoPrefsStorage.load(BeforeKey)
                                    ?.let { runCatching { modeJson.decodeFromString(LuxoAvSettings.serializer(), it) }.getOrNull() }
                                    ?.let { before -> LuxoAvRepository.update { before.copy(audioDelays = it.audioDelays) } }
                                LuxoPrefsStorage.save(ModeKey, "")
                                active = null
                            } else {
                                if (active.isNullOrBlank()) {
                                    LuxoPrefsStorage.save(BeforeKey, modeJson.encodeToString(LuxoAvSettings.serializer(), LuxoAvRepository.current()))
                                }
                                LuxoAvRepository.update(mode.apply)
                                LuxoPrefsStorage.save(ModeKey, mode.name)
                                active = mode.name
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(mode.label, color = if (on) tokens.colors.background else tokens.colors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(mode.hint, color = if (on) tokens.colors.background.copy(alpha = 0.8f) else Color(0xFFA9B6C4), fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

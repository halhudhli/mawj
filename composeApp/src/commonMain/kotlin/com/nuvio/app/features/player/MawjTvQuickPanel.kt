package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.features.player.skip.AutoSkipSegmentType

private val QGold: androidx.compose.ui.graphics.Color get() = com.nuvio.app.core.ui.MawjTint.gold
private val QGoldLight = Color(0xFFF1DDA6)
private val QInk = Color(0xFFF6F1E4)
private val QMuted = Color(0xFFA9B6C4)
private val QCard = Color(0xFF121C26)
private val QOnGold = Color(0xFF070D14)

/**
 * TV: the quick panel opened by holding OK on the video. It opens the player's own panels (so the
 * player keeps its look) and holds the two choices that save reaching for the tablet.
 */
@Composable
internal fun MawjTvQuickPanel(
    onDismiss: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onTiming: () -> Unit,
    onSpeed: (() -> Unit)?,
) {
    val settings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    val arabicAuto = settings.preferredSubtitleLanguage == "ar"
    val skipAuto = AutoSkipSegmentType.INTRO in settings.autoSkipSegmentTypes
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.padding(bottom = 48.dp).widthIn(max = 980.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xF2070D14))
                    .border(1.dp, Color(0x47D6B36A), RoundedCornerShape(24.dp)).padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("لوحة سريعة", color = QInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuickTile(Icons.Rounded.ClosedCaption, "الترجمة", Color(0xFFE0574F), Modifier.focusRequester(first)) { onDismiss(); onSubtitles() }
                    QuickTile(Icons.Rounded.Audiotrack, "الصوت", Color(0xFF5BBF7A)) { onDismiss(); onAudio() }
                    QuickTile(Icons.Rounded.Timer, "توقيت الترجمة", Color(0xFFE3C34D)) { onDismiss(); onTiming() }
                    if (onSpeed != null) QuickTile(Icons.Rounded.Speed, "السرعة", Color(0xFF4F8FE0)) { onDismiss(); onSpeed() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuickToggle("ترجمة عربية تلقائية", arabicAuto) {
                        PlayerSettingsRepository.setPreferredSubtitleLanguage(if (arabicAuto) SubtitleLanguageOption.NONE else "ar")
                    }
                    QuickToggle("تخطي المقدمة تلقائياً", skipAuto) {
                        PlayerSettingsRepository.setAutoSkipSegmentTypeEnabled(AutoSkipSegmentType.INTRO, !skipAuto)
                        PlayerSettingsRepository.setAutoSkipSegmentTypeEnabled(AutoSkipSegmentType.RECAP, !skipAuto)
                    }
                }
                Text("في المشغل: الأحمر للترجمة · الأخضر للصوت · الأصفر للتوقيت · الأزرق للسرعة", color = QMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, dot: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.width(170.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0x24D6B36A) else QCard)
            .border(if (focused) 3.dp else 1.dp, if (focused) QGoldLight else Color(0x1AEEF2F6), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = QInk, modifier = Modifier.size(34.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Text(label, color = QInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickToggle(label: String, on: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.onFocusChanged { focused = it.isFocused }.clip(CircleShape)
            .background(if (on) QGold else Color.Transparent)
            .border(if (focused) 3.dp else 1.dp, if (focused) QGoldLight else Color(0x47D6B36A), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (on) "✓" else "○", color = if (on) QOnGold else QMuted, fontSize = 15.sp)
        Text(label, color = if (on) QOnGold else QInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

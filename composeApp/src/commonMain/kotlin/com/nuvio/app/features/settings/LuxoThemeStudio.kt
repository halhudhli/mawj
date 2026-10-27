package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.CustomThemeColors
import com.nuvio.app.core.ui.formatHexColor
import com.nuvio.app.core.ui.parseHexColor
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures

private class LuxoPalette(val nameAr: String, val nameEn: String, val colors: CustomThemeColors)

private val LuxoPalettes = listOf(
    LuxoPalette("ليل الرياض", "Riyadh night", CustomThemeColors(0x7B5CFF, 0xB75AFF, 0xFF7BD1)),
    LuxoPalette("غروب العلا", "AlUla sunset", CustomThemeColors(0xFF6B3D, 0xFF9A5C, 0xFFD27B)),
    LuxoPalette("بحر جدة", "Jeddah sea", CustomThemeColors(0x00B4D8, 0x48CAE4, 0x90E0EF)),
    LuxoPalette("نخيل", "Palms", CustomThemeColors(0x2A9D8F, 0x52B788, 0xB7E4C7)),
    LuxoPalette("نيون", "Neon", CustomThemeColors(0xFF2E97, 0x7B2EFF, 0x2EF2FF)),
    LuxoPalette("قهوة", "Coffee", CustomThemeColors(0x8B5E3C, 0xC08552, 0xE8C39E)),
    LuxoPalette("ذهب", "Gold", CustomThemeColors(0xB8860B, 0xE6B422, 0xFFE08A)),
)

private const val CODE_PREFIX = "LUXO-THEME:"

/** Theme studio: Luxo palettes, three editable colours, and a code to share a look. */
@Composable
internal fun LuxoThemeStudio(isTablet: Boolean) {
    if (!rememberLuxoFeatures().themeStudio) return
    val current by ThemeSettingsRepository.customThemePreference.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var draft by remember(current) { mutableStateOf(current.colors.map(::formatHexColor)) }
    var message by remember { mutableStateOf<String?>(null) }
    val msgApplied = lx("تم تطبيق الثيم ✨", "Theme applied ✨")
    val msgCopied = lx("انسخ الكود وأرسله لأي أحد يستخدم Luxo", "Code copied — share it with any Luxo user")
    val msgBad = lx("الكود غير صحيح", "That code isn't valid")

    fun apply(colors: CustomThemeColors) {
        ThemeSettingsRepository.setCustomTheme(colors)
        message = msgApplied
    }

    LuxoBlock(isTablet) {
        LuxoLabel(lx("استوديو الثيمات 🎨", "Theme studio 🎨"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LuxoPalettes.forEach { palette ->
                Column(
                    Modifier
                        .width(86.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { apply(palette.colors) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(palette.colors.colors.map { Color(it or 0xFF000000.toInt()) })),
                    )
                    Text(lx(palette.nameAr, palette.nameEn), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(
            lx("أو ركّب ألوانك:", "Or mix your own:"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            draft.forEachIndexed { index, hex ->
                val parsed = parseHexColor(hex)
                OutlinedTextField(
                    value = hex,
                    onValueChange = { value -> draft = draft.toMutableList().also { it[index] = value.take(7) } },
                    singleLine = true,
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(parsed?.let { Color(it or 0xFF000000.toInt()) } ?: Color.Gray)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                val parsed = draft.map(::parseHexColor)
                if (parsed.all { it != null }) {
                    apply(CustomThemeColors(parsed[0]!!, parsed[1]!!, parsed[2]!!))
                } else {
                    message = msgBad
                }
            }) { Text(lx("تطبيق", "Apply")) }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(CODE_PREFIX + current.encode()))
                message = msgCopied
            }) { Text(lx("نسخ الكود", "Copy code")) }
            TextButton(onClick = {
                val text = clipboard.getText()?.text.orEmpty().trim()
                val body = text.removePrefix(CODE_PREFIX)
                val parts = body.split(",").map { parseHexColor(it.trim()) }
                if (text.startsWith(CODE_PREFIX) && parts.size == 3 && parts.all { it != null }) {
                    apply(CustomThemeColors(parts[0]!!, parts[1]!!, parts[2]!!))
                } else {
                    message = msgBad
                }
            }) { Text(lx("لصق كود", "Paste code")) }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

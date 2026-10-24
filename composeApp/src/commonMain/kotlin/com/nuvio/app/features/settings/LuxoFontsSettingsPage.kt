package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.luxo.LuxoBundledFont
import com.nuvio.app.features.luxo.LuxoFontChoice
import com.nuvio.app.features.luxo.LuxoFontSettingsRepository
import com.nuvio.app.features.luxo.luxoFontFamilyFor
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.player.SubtitleFont
import com.nuvio.app.features.player.SubtitleFontImportResult
import com.nuvio.app.features.player.SubtitleFontRepository
import com.nuvio.app.features.player.rememberSubtitleFontImporter

internal fun LazyListScope.luxoFontsSettingsContent(isTablet: Boolean) {
    item(key = "luxo_fonts_settings") {
        LuxoFontsSettingsBody(isTablet = isTablet)
    }
}

private const val ARABIC_SAMPLE = "شاهد أفلامك ومسلسلاتك بخط يعجبك"
private const val ENGLISH_SAMPLE = "Watch your movies and shows in style"

@Composable
private fun LuxoFontsSettingsBody(isTablet: Boolean) {
    val settings by remember {
        LuxoFontSettingsRepository.ensureLoaded()
        LuxoFontSettingsRepository.state
    }.collectAsStateWithLifecycle()
    val importedFonts by remember {
        SubtitleFontRepository.ensureLoaded()
        SubtitleFontRepository.fonts
    }.collectAsStateWithLifecycle()
    // Bundled fonts are copied into the same folder for subtitles; don't list them twice here.
    val bundledFiles = remember { LuxoBundledFont.entries.map { it.resourceFile }.toSet() }
    val customFonts = importedFonts.filter { it.fileName !in bundledFiles }

    var message by remember { mutableStateOf<String?>(null) }
    val importedText = lx("تمت إضافة الخط", "Font added")
    val notAFontText = lx("الملف هذا مو خط (ttf أو otf)", "That file isn't a font (ttf or otf)")
    val tooLargeText = lx("الخط كبير مرة", "That font is too large")
    val failedText = lx("ما قدرت أضيف الخط، جرّب مرة ثانية", "Couldn't add the font, try again")
    val launchImport = rememberSubtitleFontImporter(
        onPicked = { name, bytes ->
            message = when (val result = SubtitleFontRepository.import(name, bytes)) {
                is SubtitleFontImportResult.Imported -> "$importedText: ${result.font.familyName}"
                SubtitleFontImportResult.NotAFont -> notAFontText
                SubtitleFontImportResult.TooLarge -> tooLargeText
                SubtitleFontImportResult.Failed -> failedText
            }
        },
        onError = { message = failedText },
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = lx(
                "الخط العربي يُستخدم لما تكون لغة التطبيق عربي، والإنجليزي لباقي اللغات. أي خط تضيفه يطلع هنا وفي خطوط الترجمة بعد.",
                "The Arabic font is used when the app language is Arabic, the English font for every other language. Fonts you add also appear in the subtitle fonts.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSection(title = lx("الخط العربي", "Arabic font"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                FontChoiceList(
                    isTablet = isTablet,
                    sample = ARABIC_SAMPLE,
                    selected = settings.arabicFont,
                    bundled = LuxoBundledFont.entries.filter { it.isArabic },
                    custom = customFonts,
                    onSelect = { choice -> LuxoFontSettingsRepository.update { it.copy(arabicFont = choice) } },
                )
            }
        }

        SettingsSection(title = lx("الخط الإنجليزي", "English font"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                FontChoiceList(
                    isTablet = isTablet,
                    sample = ENGLISH_SAMPLE,
                    selected = settings.englishFont,
                    bundled = LuxoBundledFont.entries.filter { !it.isArabic },
                    custom = customFonts,
                    onSelect = { choice -> LuxoFontSettingsRepository.update { it.copy(englishFont = choice) } },
                )
            }
        }

        SettingsSection(title = lx("خطوطك", "Your fonts"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                LuxoBlock(isTablet) {
                    Text(
                        text = lx(
                            "أضف أي خط ttf أو otf من جوالك.",
                            "Add any ttf or otf font from your phone.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = launchImport, modifier = Modifier.fillMaxWidth()) {
                        Text(lx("إضافة خط من الجوال", "Add a font from your phone"))
                    }
                    message?.let {
                        Text(text = it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                customFonts.forEach { font ->
                    SettingsGroupDivider(isTablet = isTablet)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = font.familyName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { SubtitleFontRepository.delete(font) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = lx("حذف", "Delete"))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FontChoiceList(
    isTablet: Boolean,
    sample: String,
    selected: String,
    bundled: List<LuxoBundledFont>,
    custom: List<SubtitleFont>,
    onSelect: (String) -> Unit,
) {
    FontChoiceRow(
        isTablet = isTablet,
        title = lx("خط التطبيق الأصلي", "App default"),
        sample = sample,
        family = null,
        selected = selected == LuxoFontChoice.DEFAULT,
        onClick = { onSelect(LuxoFontChoice.DEFAULT) },
    )
    bundled.forEach { font ->
        SettingsGroupDivider(isTablet = isTablet)
        FontChoiceRow(
            isTablet = isTablet,
            title = font.displayName,
            sample = sample,
            family = luxoFontFamilyFor(font.id),
            selected = selected == font.id,
            onClick = { onSelect(font.id) },
        )
    }
    custom.forEach { font ->
        val choice = LuxoFontChoice.custom(font.fileName)
        SettingsGroupDivider(isTablet = isTablet)
        FontChoiceRow(
            isTablet = isTablet,
            title = font.familyName,
            sample = sample,
            family = luxoFontFamilyFor(choice),
            selected = selected == choice,
            onClick = { onSelect(choice) },
        )
    }
}

@Composable
private fun FontChoiceRow(
    isTablet: Boolean,
    title: String,
    sample: String,
    family: FontFamily?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sample,
                style = if (family != null) TextStyle(fontFamily = family, fontSize = 17.sp) else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

import com.nuvio.app.features.splash.SplashLogoFont
@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.nuvio.app.features.settings

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.splash.LuxoSplashScreen
import com.nuvio.app.features.splash.SplashBackgroundStyle
import com.nuvio.app.features.splash.SplashLogoAnimation
import com.nuvio.app.features.splash.SplashLogoVariant
import com.nuvio.app.features.splash.SplashSettings
import com.nuvio.app.features.splash.SplashSettingsRepository
import com.nuvio.app.features.splash.rgbColor
import kotlin.math.roundToInt

internal fun LazyListScope.luxoSplashSettingsContent(isTablet: Boolean) {
    item(key = "luxo_splash_settings") {
        LuxoSplashSettingsBody(isTablet = isTablet)
    }
}

private val backgroundSwatches = listOf(
    0x0D0D0D, 0x000000, 0x0B1020, 0x101828, 0x1A1033, 0x0E1F1A, 0x2A0E14, 0x1C1C22,
)

private val accentSwatches = listOf(
    0xC9CDD4, 0xFFFFFF, 0xE7C27D, 0xF2A7B5, 0xD98E5F, 0x4FD1A5, 0x6EC8FF, 0xB78CFF,
)

@Composable
private fun LuxoSplashSettingsBody(isTablet: Boolean) {
    val settings by remember {
        SplashSettingsRepository.ensureLoaded()
        SplashSettingsRepository.state
    }.collectAsStateWithLifecycle()
    var replayKey by remember { mutableIntStateOf(0) }
    val update: ((SplashSettings) -> SplashSettings) -> Unit = { SplashSettingsRepository.update(it) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ------------------------------------------------ live preview
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTablet) 300.dp else 230.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
            ) {
                LuxoSplashScreen(
                    settings = settings.copy(enabled = true),
                    replayKey = replayKey,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lx("معاينة مباشرة", "Live preview"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { replayKey++ }) {
                    Text(lx("إعادة تشغيل الحركة", "Replay animation"))
                }
            }
        }

        // ------------------------------------------------ general
        SettingsSection(title = lx("عام", "General"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = lx("إظهار شاشة الدخول", "Show launch screen"),
                    description = lx(
                        "لو أوقفتها يفتح التطبيق على طول بدون الشاشة المتحركة",
                        "When off, the app opens straight away without the animated screen",
                    ),
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = lx("توهّج خلف الشعار", "Glow behind logo"),
                    checked = settings.glow,
                    isTablet = isTablet,
                    onCheckedChange = { checked -> update { it.copy(glow = checked) } },
                )
            }
        }

        // ------------------------------------------------ background
        SettingsSection(title = lx("الخلفية", "Background"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                LuxoBlock(isTablet) {
                    LuxoLabel(lx("نوع الحركة", "Animation style"))
                    LuxoChips(
                        options = SplashBackgroundStyle.entries,
                        selected = settings.style,
                        label = { it.label() },
                        onSelect = { style -> update { it.copy(style = style) } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoColorChooser(
                        title = lx("لون الخلفية", "Background colour"),
                        color = settings.backgroundColor,
                        swatches = backgroundSwatches,
                        pickerIndex = 0,
                        onColor = { color -> update { it.copy(backgroundColor = color) } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoColorChooser(
                        title = lx("لون النجوم / الموجات / الجزيئات", "Stars / waves / particles colour"),
                        color = settings.accentColor,
                        swatches = accentSwatches,
                        pickerIndex = 1,
                        onColor = { color -> update { it.copy(accentColor = color) } },
                    )
                }
            }
        }

        // ------------------------------------------------ logo
        SettingsSection(title = lx("الشعار", "Logo"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                LuxoBlock(isTablet) {
                    LuxoLabel(lx("لون الشعار", "Logo finish"))
                    LuxoChips(
                        options = SplashLogoVariant.entries,
                        selected = settings.logoVariant,
                        label = { it.label() },
                        onSelect = { variant -> update { it.copy(logoVariant = variant) } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
        LuxoBlock(isTablet) {
            LuxoLabel(lx("خط الشعار", "Logo font"))
            LuxoChips(
                options = SplashLogoFont.entries,
                selected = settings.logoFont,
                label = { it.label() },
                onSelect = { font -> update { it.copy(logoFont = font) } },
            )
        }
        SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoLabel(lx("حركة ظهور الشعار", "Logo entrance"))
                    LuxoChips(
                        options = SplashLogoAnimation.entries,
                        selected = settings.logoAnimation,
                        label = { it.label() },
                        onSelect = { animation ->
                            update { it.copy(logoAnimation = animation) }
                            replayKey++
                        },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoSlider(
                        title = lx("حجم الشعار", "Logo size"),
                        valueLabel = "${(settings.logoScale * 100).roundToInt()}%",
                        value = settings.logoScale,
                        range = 0.5f..1.8f,
                        onChange = { value -> update { it.copy(logoScale = value) } },
                    )
                }
            }
        }

        // ------------------------------------------------ motion & timing
        SettingsSection(title = lx("الحركة والمدة", "Motion & timing"), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                LuxoBlock(isTablet) {
                    LuxoSlider(
                        title = lx("سرعة الحركة", "Animation speed"),
                        valueLabel = "×${(settings.speed * 10).roundToInt() / 10f}",
                        value = settings.speed,
                        range = 0.25f..3f,
                        onChange = { value -> update { it.copy(speed = value) } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoSlider(
                        title = lx("الكثافة (عدد النجوم والجزيئات)", "Density (stars & particles)"),
                        valueLabel = "${(settings.density * 100).roundToInt()}%",
                        value = settings.density,
                        range = 0.2f..2.5f,
                        onChange = { value -> update { it.copy(density = value) } },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                LuxoBlock(isTablet) {
                    LuxoSlider(
                        title = lx("مدة ظهور الشاشة", "How long it stays"),
                        valueLabel = lx(
                            "${(settings.durationMs / 100) / 10f} ث",
                            "${(settings.durationMs / 100) / 10f}s",
                        ),
                        value = settings.durationMs.toFloat(),
                        range = SplashSettings.MIN_DURATION_MS.toFloat()..SplashSettings.MAX_DURATION_MS.toFloat(),
                        onChange = { value -> update { it.copy(durationMs = ((value / 100f).roundToInt() * 100)) } },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                SplashSettingsRepository.reset()
                replayKey++
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(lx("استرجاع الإعدادات الافتراضية", "Restore defaults"))
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------- labels

@Composable
private fun SplashBackgroundStyle.label(): String = when (this) {
    SplashBackgroundStyle.STARS -> lx("نجوم", "Stars")
    SplashBackgroundStyle.WAVES -> lx("موجات ضوئية", "Light waves")
    SplashBackgroundStyle.PARTICLES -> lx("جزيئات", "Particles")
    SplashBackgroundStyle.GRADIENT -> lx("تدرّج متحرك", "Moving gradient")
    SplashBackgroundStyle.SOLID -> lx("لون سادة", "Solid colour")
}

@Composable
private fun SplashLogoAnimation.label(): String = when (this) {
    SplashLogoAnimation.FADE -> lx("تلاشي", "Fade")
    SplashLogoAnimation.ZOOM -> lx("تكبير", "Zoom")
    SplashLogoAnimation.PULSE -> lx("نبض", "Pulse")
    SplashLogoAnimation.NONE -> lx("بدون", "None")
}

@Composable
private fun SplashLogoVariant.label(): String = when (this) {
    SplashLogoVariant.SILVER -> lx("فضي", "Silver")
    SplashLogoVariant.ORIGINAL -> lx("الأصلي", "Original")
    SplashLogoVariant.GOLD -> lx("ذهبي", "Gold")
    SplashLogoVariant.ROSE_GOLD -> lx("ذهبي وردي", "Rose gold")
    SplashLogoVariant.COPPER -> lx("نحاسي", "Copper")
    SplashLogoVariant.EMERALD -> lx("زمردي", "Emerald")
    SplashLogoVariant.ARCTIC_BLUE -> lx("أزرق ثلجي", "Arctic blue")
    SplashLogoVariant.GRAPHITE -> lx("جرافيت", "Graphite")
}

private fun SplashLogoFont.label(): String = when (this) {
    SplashLogoFont.DEFAULT -> lx("افتراضي", "Default")
    SplashLogoFont.CINZEL -> lx("سينزل (Cinzel)", "Cinzel")
}

// ---------------------------------------------------------------- shared Luxo settings widgets

@Composable
internal fun LuxoBlock(isTablet: Boolean, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
internal fun LuxoLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun <T> LuxoChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
internal fun LuxoSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuxoLabel(title)
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LuxoColorChooser(
    title: String,
    color: Int,
    swatches: List<Int>,
    pickerIndex: Int,
    onColor: (Int) -> Unit,
) {
    var showCustom by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuxoLabel(title)
        TextButton(onClick = { showCustom = !showCustom }) {
            Text(if (showCustom) lx("إخفاء", "Hide") else lx("لون مخصص", "Custom"))
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatches.forEach { swatch ->
            val isSelected = swatch == color
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(rgbColor(swatch))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                        shape = CircleShape,
                    )
                    .clickable { onColor(swatch) },
            )
        }
    }
    if (showCustom) {
        ThemeColorPicker(
            color = color,
            colorIndex = pickerIndex,
            onColorChanged = onColor,
        )
    }
}

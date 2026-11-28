package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.MawjWorld
import com.nuvio.app.core.ui.MawjWorldScene
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.blurb
import com.nuvio.app.core.ui.labelRes
import com.nuvio.app.core.ui.mawjWorld
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.luxo.LuxoFeaturesRepository
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import org.jetbrains.compose.resources.stringResource

/**
 * «عوالم موج» in Settings → Appearance: a live card for each world (its own moving background and a
 * hint of its page colours), then «الثيم حسب الوقت» and the selected world's own motion switch.
 */
@Composable
internal fun MawjWorldPicker(
    isTablet: Boolean,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val features = rememberLuxoFeatures()
    val lite = com.nuvio.app.features.luxo.rememberLuxoLite()
    val gap = if (isTablet) 14.dp else 10.dp
    Column(
        Modifier.fillMaxWidth().padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(
            lx("عوالم موج", "Mawj worlds"),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            color = tokens.colors.textPrimary,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth > 900.dp -> 4
                maxWidth > 560.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                MawjWorld.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { world ->
                            WorldCard(
                                world = world,
                                selected = world.theme == selectedTheme,
                                moving = !lite && world.theme.name !in features.worldStill,
                                onClick = { onThemeSelected(world.theme) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        WorldSwitch(
            title = lx("تلقائي حسب الوقت", "By time of day"),
            subtitle = lx("«الفجر» من السادسة صباحاً إلى السادسة مساءً، والثيم المختار في الليل", "Dawn by day, the chosen theme by night"),
            checked = features.themeByTime,
            onChange = { v -> LuxoFeaturesRepository.update { it.copy(themeByTime = v) } },
        )
        val world = selectedTheme.mawjWorld()
        if (world != null) {
            val name = stringResource(world.theme.labelRes)
            WorldSwitch(
                title = lx("حركة خلفية «$name»", "Motion of «$name»"),
                subtitle = lx("عند الإيقاف تبقى الصورة نفسها ثابتة، وفي وضع الخفة تثبت دائماً", "Off: the same picture, standing still"),
                checked = world.theme.name !in features.worldStill,
                onChange = { v ->
                    LuxoFeaturesRepository.update {
                        it.copy(worldStill = if (v) it.worldStill - world.theme.name else it.worldStill + world.theme.name)
                    }
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            lx("ألوان أخرى", "Other colours"),
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textMuted,
        )
    }
}

@Composable
private fun WorldCard(world: MawjWorld, selected: Boolean, moving: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tokens = MaterialTheme.nuvio
    val palette = ThemeColors.getColorPalette(world.theme)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) tokens.colors.accent else tokens.colors.borderSubtle, shape)
            .background(tokens.colors.surfaceCard)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            MawjWorldScene(world, Modifier.fillMaxSize(), moving = moving)
            // A hint of the world's pages: a gold pill and three small posters in its card colour.
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(3) {
                    Box(Modifier.width(18.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(palette.backgroundCard.copy(alpha = 0.92f)))
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width(30.dp).height(10.dp).clip(CircleShape).background(palette.secondary))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(palette.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = palette.onSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                stringResource(world.theme.labelRes),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
            )
            Text(
                world.blurb(),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = tokens.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorldSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.colors.surfaceCard)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = tokens.colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = tokens.colors.accent, checkedThumbColor = tokens.colors.onAccent),
        )
    }
}

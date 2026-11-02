@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.luxo.LuxoNavPlacement
import com.nuvio.app.features.luxo.LuxoNavSettingsRepository
import com.nuvio.app.features.luxo.LuxoNavTabIds
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoNavSettings

/** Luxo navigation extras: placement, side rail, hide on scroll, labels, badges, shortcuts, order. */
@Composable
internal fun LuxoNavExtrasControls(isTablet: Boolean) {
    val nav = rememberLuxoNavSettings()
    LuxoBlock(isTablet) {
        LuxoLabel(lx("مكان شريط التنقل", "Navigation placement"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                LuxoNavPlacement.BAR to lx("علوي / سفلي", "Top / bottom"),
                LuxoNavPlacement.SIDE to lx("جانبي", "Side"),
                LuxoNavPlacement.AUTO to lx("تلقائي", "Auto"),
            ).forEach { (placement, label) ->
                FilterChip(
                    selected = nav.placementValue == placement,
                    onClick = { LuxoNavSettingsRepository.update { it.copy(placement = placement.id) } },
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = when (nav.placementValue) {
                LuxoNavPlacement.BAR -> lx(
                    "الشريط العائم فوق أو تحت حسب «موضع الشريط» في قسم العرض.",
                    "The floating bar at the top or bottom, as set in the Display section.",
                )
                LuxoNavPlacement.SIDE -> lx(
                    "شريط جانبي: يمين بالعربي ويسار بالإنجليزي. يفتح بالسهم أو بالسحب ويظهر الأسماء.",
                    "A side rail: right in Arabic, left in English. Open it with the arrow or a swipe.",
                )
                LuxoNavPlacement.AUTO -> lx(
                    "شريط عادي على الجوال، وجانبي لما تكون الشاشة عريضة بالعرض.",
                    "A bar on phones, a side rail on wide landscape screens.",
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SettingsGroupDivider(isTablet = isTablet)
    LuxoBlock(isTablet) {
        LuxoLabel(lx("شكل الشريط", "Bar style"))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.nuvio.app.features.luxo.LuxoBarStyle.entries.forEach { st ->
                FilterChip(
                    selected = nav.barStyle == st.id,
                    onClick = { LuxoNavSettingsRepository.update { it.copy(barStyle = st.id) } },
                    label = { Text(st.ar) },
                )
            }
        }
        Text(
            text = when (com.nuvio.app.features.luxo.LuxoBarStyle.of(nav.barStyle)) {
                com.nuvio.app.features.luxo.LuxoBarStyle.ORIGINAL -> "شريط نوفيو الأصلي بدون تعديلات Luxo."
                com.nuvio.app.features.luxo.LuxoBarStyle.DOCK -> "الكبسولة الزجاجية والقسم المفتوح ينصبغ."
                com.nuvio.app.features.luxo.LuxoBarStyle.ISLAND -> "كبسولة صغيرة بالأيقونات، القسم المفتوح يتمدد باسمه، وتنكمش مع النزول."
                com.nuvio.app.features.luxo.LuxoBarStyle.CINEMA -> "شريط بعرض الشاشة: شعار Luxo، أقسام نصية، وخط متوهج ينزلق تحت القسم المفتوح."
                com.nuvio.app.features.luxo.LuxoBarStyle.SPLIT -> "الأقسام في كبسولة، والبحث والبروفايل في دوائر عائمة لحالها."
                com.nuvio.app.features.luxo.LuxoBarStyle.NEON -> "كلمات كبيرة بدون خلفية، والقسم المفتوح يتوهج بتدرج."
                com.nuvio.app.features.luxo.LuxoBarStyle.DISCS -> "دوائر منفصلة، والمفتوحة تكبر وتتلون."
                com.nuvio.app.features.luxo.LuxoBarStyle.GREETING -> "تحية حسب الوقت والساعة بجانب الأقسام."
                com.nuvio.app.features.luxo.LuxoBarStyle.FAN -> "كرة Luxo عائمة في الزاوية، والضغط عليها يفتح الأقسام على شكل قوس."
                com.nuvio.app.features.luxo.LuxoBarStyle.CURTAIN -> "الشريط مخفي، والسحب أو الضغط على المقبض يفتح الأقسام كبطاقات كبيرة."
                com.nuvio.app.features.luxo.LuxoBarStyle.MAGNET -> "أيقونات صغيرة تكبر تحت الإصبع وتظهر أسماؤها."
                com.nuvio.app.features.luxo.LuxoBarStyle.LIQUID -> "المؤشر قطرة تتمدد وتنسكب من قسم لقسم."
                com.nuvio.app.features.luxo.LuxoBarStyle.COMMAND -> "كبسولة باسم القسم، والضغط عليها يفتح لوحة فيها البحث وكل الأقسام."
                com.nuvio.app.features.luxo.LuxoBarStyle.GESTURE -> "بدون شريط: السحب بإصبعين يمين ويسار ينقل بين الأقسام، ونقاط صغيرة توضح المكان."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LuxoLabel(lx("مكان الشريط", "Position"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("start" to "بداية", "center" to "وسط", "end" to "نهاية", "full" to "عرض الشاشة").forEach { (id, label) ->
                FilterChip(selected = nav.barPosition == id, onClick = { LuxoNavSettingsRepository.update { it.copy(barPosition = id) } }, label = { Text(label) })
            }
        }
        LuxoLabel(lx("أسماء الأقسام", "Labels"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("always" to "دايم ظاهرة", "selected" to "للمفتوح فقط", "none" to "أيقونات فقط").forEach { (id, label) ->
                FilterChip(selected = nav.barLabels == id, onClick = { LuxoNavSettingsRepository.update { it.copy(barLabels = id) } }, label = { Text(label) })
            }
        }
        LuxoLabel(lx("الحجم", "Size"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "صغير", 1 to "عادي", 2 to "كبير").forEach { (v, label) ->
                FilterChip(selected = nav.barSize == v, onClick = { LuxoNavSettingsRepository.update { it.copy(barSize = v) } }, label = { Text(label) })
            }
        }
        LuxoLabel(lx("الشفافية", "Transparency"))
        androidx.compose.material3.Slider(
            value = nav.barOpacity,
            onValueChange = { v -> LuxoNavSettingsRepository.update { it.copy(barOpacity = v) } },
            valueRange = 0.35f..1f,
        )
        FilterChip(
            selected = false,
            onClick = {
                LuxoNavSettingsRepository.update {
                    it.copy(barStyle = "dock", barPosition = "center", barLabels = "selected", barSize = 1, barOpacity = 0.94f)
                }
            },
            label = { Text(lx("↺ رجّع الشكل السابق", "↺ Restore previous look")) },
        )
    }
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("إخفاء الشريط أثناء التمرير", "Hide the bar while scrolling"),
        description = lx("يختفي عند النزول في الصفحة ويرجع عند الطلوع", "Hides when scrolling down, returns when scrolling up"),
        checked = nav.hideOnScroll,
        isTablet = isTablet,
        onCheckedChange = { v -> LuxoNavSettingsRepository.update { it.copy(hideOnScroll = v) } },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("أسماء الأقسام تحت الأيقونات", "Labels under icons"),
        description = lx("إيقافها يخلي الشريط أيقونات فقط", "Off shows icons only"),
        checked = nav.showLabels,
        isTablet = isTablet,
        onCheckedChange = { v -> LuxoNavSettingsRepository.update { it.copy(showLabels = v) } },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("شارة حلقات اليوم", "Today's episodes badge"),
        description = lx("رقم على «المكتبة» بعدد الحلقات اللي تنزل اليوم من الأعمال المتابعة", "A count on Library for followed episodes airing today"),
        checked = nav.badges,
        isTablet = isTablet,
        onCheckedChange = { v -> LuxoNavSettingsRepository.update { it.copy(badges = v) } },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = lx("اختصارات الضغط المطوّل", "Long-press shortcuts"),
        description = lx(
            "الرئيسية: «وش أشوف الليلة؟» · بحث: يفتح الكيبورد · المكتبة: التنزيلات. والضغط على القسم المفتوح يرجع لأول الصفحة",
            "Home: tonight's pick · Search: opens the keyboard · Library: downloads. Tapping the open tab scrolls to top",
        ),
        checked = nav.quickActions,
        isTablet = isTablet,
        onCheckedChange = { v -> LuxoNavSettingsRepository.update { it.copy(quickActions = v) } },
    )
    SettingsGroupDivider(isTablet = isTablet)
    LuxoBlock(isTablet) {
        LuxoLabel(lx("ترتيب أزرار الشريط", "Order of the tabs"))
        val order = nav.resolvedOrder()
        order.forEachIndexed { index, id ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${LuxoNavTabIds.label(id)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { LuxoNavSettingsRepository.move(id, -1) }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(onClick = { LuxoNavSettingsRepository.move(id, 1) }, enabled = index < order.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        }
        Text(
            lx("استعادة الترتيب الأصلي", "Reset order"),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { LuxoNavSettingsRepository.update { it.copy(order = LuxoNavTabIds.DEFAULT_ORDER) } }
                .padding(vertical = 6.dp),
        )
    }
}

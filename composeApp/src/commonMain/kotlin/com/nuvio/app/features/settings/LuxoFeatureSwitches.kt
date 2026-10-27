package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.luxo.LuxoFeatures
import com.nuvio.app.features.luxo.LuxoFeaturesRepository
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures

private class LuxoFeatureSwitch(
    val title: String,
    val description: String,
    val read: (LuxoFeatures) -> Boolean,
    val write: (LuxoFeatures, Boolean) -> LuxoFeatures,
)

/** One switch per Luxo extra, so each can be turned off on its own. */
@Composable
internal fun LuxoFeatureSwitches(isTablet: Boolean) {
    val features = rememberLuxoFeatures()
    val switches = listOf(
        LuxoFeatureSwitch(
            lx("تصميم Luxo الكامل ✨", "Full Luxo design ✨"),
            lx("كروت زجاجية وعناوين متدرجة ومفاتيح متوهجة في كل الصفحات. إيقافه يرجع شكل نوفيو", "Glass cards, gradient titles and glowing switches everywhere. Off returns the original look"),
            { it.luxoDesign }, { f, v -> f.copy(luxoDesign = v) },
        ),
        LuxoFeatureSwitch(
            lx("شريط Luxo Dock 🚀", "Luxo Dock bar 🚀"),
            lx("الشريط العائم الجديد: القسم المفتوح يتمدد بتدرج متوهج وحلقة ضوء عند التنقل", "The new floating bar: the open tab grows into a glowing pill with a ring of light"),
            { it.luxoDock }, { f, v -> f.copy(luxoDock = v) },
        ),
        LuxoFeatureSwitch(
            lx("صفحة الملف الشخصي بتصميم Luxo 🎟️", "Luxo profile page 🎟️"),
            lx("بطاقة Luxo Pass والقاعات بدل قائمة الإعدادات العادية", "The Luxo Pass and halls instead of the plain settings list"),
            { it.profileHub }, { f, v -> f.copy(profileHub = v) },
        ),
        LuxoFeatureSwitch(
            lx("لمعة البطاقة مع ميلان الجهاز", "Pass shine follows device tilt"),
            lx("اللمعة الهولوغرامية تتحرك لما يميل الجهاز", "The holographic shine moves as the device tilts"),
            { it.passTilt }, { f, v -> f.copy(passTilt = v) },
        ),
        LuxoFeatureSwitch(
            lx("قسم الأنمي 🌸", "Anime tab 🌸"),
            lx("تبويب مستقل لأقسام الأنمي من الإضافات مثل Anime Kitsu", "A separate tab for anime catalogs from addons like Anime Kitsu"),
            { it.animeSection }, { f, v -> f.copy(animeSection = v) },
        ),
        LuxoFeatureSwitch(
            lx("طابور الاستكشاف 🧭", "Discovery queue 🧭"),
            lx("كروت تسحبها يمين للي يعجبك ويسار للي ما يعجبك، واللي يعجبك ينحفظ في قائمة", "Swipe right to keep, left to skip; kept titles go to a list"),
            { it.discoveryQueue }, { f, v -> f.copy(discoveryQueue = v) },
        ),
        LuxoFeatureSwitch(
            lx("الاهتزاز الخفيف", "Haptics"),
            lx("لمسة اهتزاز عند السحب والتقييم والإضافة للقوائم", "A light tap when swiping, rating and saving"),
            { it.haptics }, { f, v -> f.copy(haptics = v) },
        ),
        LuxoFeatureSwitch(
            lx("ثيمات المواسم 🌙", "Seasonal themes 🌙"),
            lx("زينة وتهنئة في رمضان والعيدين واليوم الوطني ويوم التأسيس", "Decorations for Ramadan, both Eids, National Day and Founding Day"),
            { it.seasonalThemes }, { f, v -> f.copy(seasonalThemes = v) },
        ),
        LuxoFeatureSwitch(
            lx("الأوسمة 🏅", "Badges 🏅"),
            lx("إنجازات تنفتح مع المشاهدة في صفحة «ذوقي»", "Achievements that unlock as you watch, in My taste"),
            { it.achievements }, { f, v -> f.copy(achievements = v) },
        ),
        LuxoFeatureSwitch(
            lx("خريطة المشاهدة", "Watch map"),
            lx("مربعات آخر 6 شهور، كل ما زادت المشاهدة صار المربع أغمق", "The last 6 months as squares, darker on busier days"),
            { it.heatmap }, { f, v -> f.copy(heatmap = v) },
        ),
        LuxoFeatureSwitch(
            lx("بطاقة المشاركة", "Share card"),
            lx("زر في صفحة العمل يصنع صورة أنيقة فيها البوستر والتقييم للستوري", "A button on a title page that makes a story-ready image"),
            { it.shareCard }, { f, v -> f.copy(shareCard = v) },
        ),
        LuxoFeatureSwitch(
            lx("ترتيب المصادر حسب الجودة", "Rank sources by quality"),
            lx("داخل كل إضافة: الدقة الأعلى والجاهز على خدمة التخزين (⚡) والنسخ النظيفة أولاً", "Within each addon: higher resolution, cached (⚡) and clean releases first"),
            { it.streamRanking }, { f, v -> f.copy(streamRanking = v) },
        ),
        LuxoFeatureSwitch(
            lx("إخفاء نسخ الكام", "Hide cam copies"),
            lx("يشيل نسخ CAM و TS المصوّرة من السينما", "Removes CAM and telesync copies"),
            { it.hideCamReleases }, { f, v -> f.copy(hideCamReleases = v) },
        ),
        LuxoFeatureSwitch(
            lx("لقطة شاشة في المشغل 📸", "Player screenshot 📸"),
            lx("زر في قائمة المشغل يحفظ المشهد في الصور", "A player menu item that saves the frame to your photos"),
            { it.playerScreenshot }, { f, v -> f.copy(playerScreenshot = v) },
        ),
        LuxoFeatureSwitch(
            lx("تكرار مقطع (A-B)", "A-B loop"),
            lx("حدد بداية ونهاية مقطع ويعيده تلقائياً", "Mark a start and end and it repeats"),
            { it.playerAbLoop }, { f, v -> f.copy(playerAbLoop = v) },
        ),
        LuxoFeatureSwitch(
            lx("إضاءة حول الفيديو", "Ambient glow"),
            lx("وهج خفيف على أطراف الشاشة بألوان المشهد", "A soft glow at the edges in the scene's colours"),
            { it.ambientGlow }, { f, v -> f.copy(ambientGlow = v) },
        ),
        LuxoFeatureSwitch(
            lx("استوديو الثيمات", "Theme studio"),
            lx("ألوان Luxo الجاهزة وتركيب ألوانك ومشاركتها بكود", "Luxo palettes, your own mix and a shareable code"),
            { it.themeStudio }, { f, v -> f.copy(themeStudio = v) },
        ),
    )
    LuxoBlock(isTablet) {
        LuxoLabel(lx("شدة التوهج", "Glow strength"))
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(0 to lx("بدون", "None"), 1 to lx("هادئ", "Soft"), 2 to lx("قوي", "Strong")).forEach { (level, label) ->
                androidx.compose.material3.FilterChip(
                    selected = features.glowLevel == level,
                    onClick = { LuxoFeaturesRepository.update { it.copy(glowLevel = level) } },
                    label = { androidx.compose.material3.Text(label) },
                )
            }
        }
    }
    SettingsGroupDivider(isTablet = isTablet)
    switches.forEachIndexed { index, item ->
        if (index > 0) SettingsGroupDivider(isTablet = isTablet)
        SettingsSwitchRow(
            title = item.title,
            description = item.description,
            checked = item.read(features),
            isTablet = isTablet,
            onCheckedChange = { value -> LuxoFeaturesRepository.update { item.write(it, value) } },
        )
    }
}

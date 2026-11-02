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
    val tvSwitches = if (!com.nuvio.app.features.luxo.luxoIsTv()) emptyList() else listOf(
        LuxoFeatureSwitch(
            lx("وضع الشاشة الخفيف 📺", "TV light mode 📺"),
            lx("للشاشات الضعيفة: صور بحجم الشاشة، بدون إعلان يشتغل تلقائي في الرئيسية وبدون حركات خلفية. يشتغل بالكامل بعد إعادة فتح التطبيق", "For slower TVs: screen-sized pictures, no autoplay trailer on Home, no background animations. Fully applies after reopening the app"),
            { it.tvLite }, { f, v -> f.copy(tvLite = v) },
        ),
        LuxoFeatureSwitch(
            lx("التنقل بالريموت 🎮", "Remote navigation 🎮"),
            lx("إطار واضح حول العنصر المحدد، والقوائم تمشي بنعومة مع الأسهم", "A clear frame around the selected item, and lists glide with the arrows"),
            { it.tvRemote }, { f, v -> f.copy(tvRemote = v) },
        ),
    )
    val switches = tvSwitches + listOf(
        LuxoFeatureSwitch(
            lx("السلاسل 🎞️", "Franchises 🎞️"),
            lx("كتالوج الأعمال اللي لها أكثر من جزء: أفلام ومسلسلات وأنمي، بترتيب المشاهدة وعلامة على اللي تمت مشاهدته", "Multi-part works: movies, series and anime, in watch order with what's been watched"),
            { it.franchises }, { f, v -> f.copy(franchises = v) },
        ),
        LuxoFeatureSwitch(
            lx("القنوات وشركات الإنتاج بتصميم Luxo 🏢", "Luxo channels and companies 🏢"),
            lx("بطاقات داكنة بشعارات واضحة، وصفحة جديدة لكل شركة فيها الأفلام والمسلسلات مع ترتيب وفلتر تقييم", "Dark cards with clear logos and a new page per company with sorting and a rating filter"),
            { it.companiesRedesign }, { f, v -> f.copy(companiesRedesign = v) },
        ),
        LuxoFeatureSwitch(
            lx("بكرة الفيلم 🎞️", "Film reel 🎞️"),
            lx("عند سحب شريط الوقت: ٥ لقطات مثل بكرة فيلم مع الوقت والفرق واسم الفصل، والرفع فوق الشريط يبطّئ السحب للدقة، واهتزاز خفيف كل ١٠ ثواني. الإيقاف يرجّع تصميم نوفيو", "While scrubbing: five frames like a film strip with time, difference and chapter; lifting the finger slows the scrub; a light tick every 10 s. Off returns Nuvio's preview"),
            { it.filmReel }, { f, v -> f.copy(filmReel = v) },
        ),
        LuxoFeatureSwitch(
            lx("حفظ الصور ⬇", "Save images ⬇"),
            lx("زر في عارض الخلفيات والملصقات يحفظ الصورة بدقتها الأصلية في الاستوديو", "A button in the image viewer that saves the original to the gallery"),
            { it.saveImages }, { f, v -> f.copy(saveImages = v) },
        ),
        LuxoFeatureSwitch(
            lx("ألوان الصورة في المشغل 🎨", "Picture colours in the player 🎨"),
            lx("تعديلات جاهزة (ذكي، زاهي، طبيعي، سينما، إنقاذ HDR…) وتعديل يدوي للإضاءة والتباين والتشبع، للشاشات اللي ما تدعم HDR", "Ready looks (smart, vivid, natural, cinema, HDR rescue…) plus manual brightness, contrast and saturation for screens without HDR"),
            { it.videoColors }, { f, v -> f.copy(videoColors = v) },
        ),
        LuxoFeatureSwitch(
            lx("منع تعليق الفيديو 🛟", "Freeze guard 🛟"),
            lx("لو الصورة وقفت والتحميل يلف وهو محمّل أصلاً (مثل بعد الترجيع)، يرجّعه يشتغل لحاله", "If the picture freezes with a spinner while the video is already loaded (e.g. after seeking back), it gets it playing again by itself"),
            { it.stallGuard }, { f, v -> f.copy(stallGuard = v) },
        ),
        LuxoFeatureSwitch(
            lx("الوسائط في صفحة العمل 🎞️", "Media on the details page 🎞️"),
            lx("كل الخلفيات والملصقات والشعارات من TMDB، كل أنواع الفيديو، والموسيقى التصويرية مع تشغيل مقاطع منها", "Every TMDB backdrop, poster and logo, every video kind, and the soundtrack with previews"),
            { it.mediaHub }, { f, v -> f.copy(mediaHub = v) },
        ),
        LuxoFeatureSwitch(
            lx("صفحة الممثل بتصميم Luxo 🎭", "Luxo actor page 🎭"),
            lx("صورة كاملة كبيرة، اشتهر بـ، أفضل المتعاونين، والأعمال بترتيب وفلتر تقييم والأفلام والمسلسلات منفصلة", "Big full photo, known for, top collaborators, and the filmography with sorting, a rating filter and movies / series apart"),
            { it.personPage }, { f, v -> f.copy(personPage = v) },
        ),
        LuxoFeatureSwitch(
            lx("ترجمة للعربي 🌐", "Arabic translation 🌐"),
            lx("يترجم القصص والسير اللي مو بالعربي، ويكتب أسماء الممثلين بالحروف العربية (مرة وحدة وتنحفظ)", "Translates stories and biographies that aren't in Arabic and writes people's names in Arabic letters (once, then cached)"),
            { it.arabicTranslate }, { f, v -> f.copy(arabicTranslate = v) },
        ),
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
            lx("كروت تُسحب يميناً للإعجاب ويساراً للتخطي، والمعجَب به ينحفظ في قائمة", "Swipe right to keep, left to skip; kept titles go to a list"),
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
            lx("تحديد بداية ونهاية مقطع ليتكرر تلقائياً", "Mark a start and end and it repeats"),
            { it.playerAbLoop }, { f, v -> f.copy(playerAbLoop = v) },
        ),
        LuxoFeatureSwitch(
            lx("إضاءة حول الفيديو", "Ambient glow"),
            lx("وهج خفيف على أطراف الشاشة بألوان المشهد", "A soft glow at the edges in the scene's colours"),
            { it.ambientGlow }, { f, v -> f.copy(ambientGlow = v) },
        ),
        LuxoFeatureSwitch(
            lx("العد التنازلي للحلقات ⏳", "Episode countdown ⏳"),
            lx("في الرئيسية، وصفحة كاملة مقسّمة: كيدراما، أجنبي، أنمي، وأقسام مخصصة", "On Home, plus a full page split into K-drama, international, anime and your own sections"),
            { it.countdown }, { f, v -> f.copy(countdown = v) },
        ),
        LuxoFeatureSwitch(
            lx("مذكراتي 📔", "My diary 📔"),
            lx("كل المشاهدات بالتاريخ مع التقييم والملاحظة، في صفحة «ذوقي»", "Everything you watched by date, in My taste"),
            { it.diary }, { f, v -> f.copy(diary = v) },
        ),
        LuxoFeatureSwitch(
            lx("تحدي الشهر 🎯", "Monthly challenge 🎯"),
            lx("هدف لعدد الأفلام أو الحلقات بالشهر مع حلقة تقدم ووسام", "A movies or episodes goal with a progress ring and a badge"),
            { it.challenge }, { f, v -> f.copy(challenge = v) },
        ),
        LuxoFeatureSwitch(
            lx("السنة في Luxo 🎬", "Your year in Luxo 🎬"),
            lx("ملخص السنة على شكل ستوريز قابلة للمشاركة", "Your year as shareable stories"),
            { it.wrapped }, { f, v -> f.copy(wrapped = v) },
        ),
        LuxoFeatureSwitch(
            lx("الإعلان كامل بدون قص", "Show the whole trailer"),
            lx("إعلان صفحة الفيلم يظهر كامل بدل ما يتكبّر ويقص نص الفيديو", "The title-page trailer shows in full instead of being zoomed and cropped"),
            { it.trailerFit }, { f, v -> f.copy(trailerFit = v) },
        ),
        LuxoFeatureSwitch(
            lx("خلفية الرئيسية تتحرك مع الميلان", "Home artwork follows tilt"),
            lx("صورة الفيلم الكبيرة فوق تتحرك شوي مع ميلان الجهاز", "The big artwork drifts slightly as the device tilts"),
            { it.heroTilt }, { f, v -> f.copy(heroTilt = v) },
        ),
        LuxoFeatureSwitch(
            lx("ألوان حسب الوقت 🌅", "Time-of-day colours 🌅"),
            lx("الإضاءة أعلى الشاشات تدفى وقت المغرب وتهدى بالليل", "The glow warms at sunset and cools at night"),
            { it.timeTint }, { f, v -> f.copy(timeTint = v) },
        ),
        LuxoFeatureSwitch(
            lx("استوديو الثيمات", "Theme studio"),
            lx("ألوان Luxo الجاهزة وتركيب ألوان خاصة ومشاركتها بكود", "Luxo palettes, your own mix and a shareable code"),
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
    LuxoBlock(isTablet) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(
                selected = false,
                onClick = { com.nuvio.app.features.luxo.LuxoOverlays.traktDoctor = true },
                label = { androidx.compose.material3.Text(lx("🩺 فاحص Trakt", "🩺 Trakt doctor")) },
            )
            if (features.franchises) {
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { com.nuvio.app.features.luxo.LuxoOverlays.franchise = "" },
                    label = { androidx.compose.material3.Text(lx("🎞️ السلاسل", "🎞️ Franchises")) },
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

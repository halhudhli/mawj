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
            lx("فهرس الأعمال ذات الأجزاء المتعددة: أفلام ومسلسلات وأنمي، بترتيب المشاهدة مع علامة على ما تمت مشاهدته", "Multi-part works: movies, series and anime, in watch order with what's been watched"),
            { it.franchises }, { f, v -> f.copy(franchises = v) },
        ),
        LuxoFeatureSwitch(
            lx("القنوات وشركات الإنتاج بتصميم موج 🏢", "Mawj channels and companies 🏢"),
            lx("بطاقات داكنة بشعارات واضحة، وصفحة جديدة لكل شركة فيها الأفلام والمسلسلات مع ترتيب وفلتر تقييم", "Dark cards with clear logos and a new page per company with sorting and a rating filter"),
            { it.companiesRedesign }, { f, v -> f.copy(companiesRedesign = v) },
        ),
        LuxoFeatureSwitch(
            lx("ترجمة الترجمة للعربي 🌐", "Translate subtitles to Arabic 🌐"),
            lx("زر في قائمة المشغل يترجم ترجمة إنجليزية (أو غيرها) للعربي بـ Gemini بنفس التوقيت، وتنحفظ للمرة الجاية", "A player menu item that translates an English (or other) subtitle to Arabic with Gemini, same timing"),
            { it.subtitleTranslate }, { f, v -> f.copy(subtitleTranslate = v) },
        ),
        LuxoFeatureSwitch(
            lx("علامات داخل الحلقة 🔖", "Bookmarks in episodes 🔖"),
            lx("حفظ لحظة مع ملاحظة من قائمة المشغل، والرجوع لها بضغطة، وتظهر في صفحة العمل", "Save a moment with a note from the player menu and jump back to it"),
            { it.bookmarks }, { f, v -> f.copy(bookmarks = v) },
        ),
        LuxoFeatureSwitch(
            lx("توضيح الحوار + صوت ليلي 🗣️🌙", "Dialogue boost + night audio 🗣️🌙"),
            lx("في قائمة المشغل: رفع صوت الكلام، وتهدئة الأصوات العالية ورفع الهادئة", "In the player menu: clearer speech, and loud parts tamed while quiet ones come up"),
            { it.audioFx }, { f, v -> f.copy(audioFx = v) },
        ),
        LuxoFeatureSwitch(
            lx("شارك المشهد مع الترجمة 🎬", "Share the scene with its subtitle 🎬"),
            lx("بطاقة فيها لقطة المشهد وسطر الترجمة واسم العمل، جاهزة للمشاركة", "A card with the frame, the subtitle line and the title, ready to share"),
            { it.shareScene }, { f, v -> f.copy(shareScene = v) },
        ),
        LuxoFeatureSwitch(
            lx("نبهني لما ينزل ⏰", "Release reminders ⏰"),
            lx("للأفلام التي لم تُطرح رقمياً بعد، ولأعمال الممثلين المتابَعين: يصل إشعار عند النزول", "For movies not out digitally yet, and new work from followed actors: a notification when it's out"),
            { it.releaseReminders }, { f, v -> f.copy(releaseReminders = v) },
        ),
        LuxoFeatureSwitch(
            lx("البحث بالوصف والصوت 🔎🎙️", "Search by description and voice 🔎🎙️"),
            lx("يُكتب أو يُقال وصف العمل (مثل «رجل عالق في المريخ») فيظهر العمل", "Type or say what it's about and the title comes up"),
            { it.describeSearch }, { f, v -> f.copy(describeSearch = v) },
        ),
        LuxoFeatureSwitch(
            lx("قارن بين عملين ⚖️", "Compare two titles ⚖️"),
            lx("جنباً إلى جنب: التقييم والمدة والمواسم والإخراج والبطولة ورأي رفيق موج", "Side by side: ratings, runtime, seasons, shared cast and Mawj Companion's take"),
            { it.compareTitles }, { f, v -> f.copy(compareTitles = v) },
        ),
        LuxoFeatureSwitch(
            lx("أغاني البداية والنهاية للأنمي 🎌", "Anime openings & endings 🎌"),
            lx("في الموسيقى: الأوبننق والإندنق الرسمية بالصوت والفيديو من AnimeThemes", "In Music: official openings and endings with audio and video"),
            { it.animeThemes }, { f, v -> f.copy(animeThemes = v) },
        ),
        LuxoFeatureSwitch(
            lx("لوحة الذوق (Bento) 🍱", "Taste board 🍱"),
            lx("في «ذوقي»: اللقب، خريطة العالم، ساعة المشاهدة، العقود، الممثلون والمخرجون والاستوديوهات، توأم الذوق ومزاج كل شهر، مع المشاركة", "In «My taste»: nickname, world map, watch clock, decades, favourite people, taste twin, monthly mood, share"),
            { it.tasteBento }, { f, v -> f.copy(tasteBento = v) },
        ),
        LuxoFeatureSwitch(
            lx("الوقت المتبقي وخطة الإنهاء ⏳", "Time left & plan ⏳"),
            lx("في صفحة المسلسل: كم حلقة وساعة متبقية، ومتى يُنتهى منه حسب عدد الحلقات يومياً", "On a series page: episodes and hours left, and when it'll be finished at your pace"),
            { it.timeLeft }, { f, v -> f.copy(timeLeft = v) },
        ),
        LuxoFeatureSwitch(
            lx("ذاكرة إعدادات كل مسلسل 🧠", "Per-series memory 🧠"),
            lx("لغة الصوت والترجمة وسرعة التشغيل تُحفظ لكل مسلسل وتُطبَّق تلقائياً في حلقاته", "Audio, subtitle and speed are remembered per series and applied to its episodes"),
            { it.seriesMemory }, { f, v -> f.copy(seriesMemory = v) },
        ),
        LuxoFeatureSwitch(
            lx("الوضع الخاص 🕶️", "Private mode 🕶️"),
            lx("زر في «حسابي»: عند تشغيله لا يُسجَّل التقدم ولا السجل ولا يُرسل شيء إلى Trakt", "A button in the profile: while on, no progress, history or Trakt is recorded"),
            { it.privateMode }, { f, v -> f.copy(privateMode = v) },
        ),
        LuxoFeatureSwitch(
            lx("الموسيقى التصويرية 🎵", "الموسيقى التصويرية 🎵"),
            lx("أغاني العمل ومتى تُسمع، مع فتحها في أنغامي أو سبوتيفاي أو يوتيوب (عبر Gemini)", "أغاني العمل ومتى تُسمع، مع فتحها في أنغامي أو سبوتيفاي أو يوتيوب (عبر Gemini)"),
            { it.soundtrack }, { f, v -> f.copy(soundtrack = v) },
        ),
        LuxoFeatureSwitch(
            lx("مواقع التصوير 📍", "مواقع التصوير 📍"),
            lx("الأماكن الحقيقية التي صُوّر فيها العمل، وكل موقع يُفتح على الخريطة (عبر Gemini)", "الأماكن الحقيقية التي صُوّر فيها العمل، وكل موقع يُفتح على الخريطة (عبر Gemini)"),
            { it.filmingLocations }, { f, v -> f.copy(filmingLocations = v) },
        ),
        LuxoFeatureSwitch(
            lx("هل تعلم؟ 💡", "هل تعلم؟ 💡"),
            lx("معلومات من خلف الكواليس عن العمل بلا حرق (عبر Gemini)", "معلومات من خلف الكواليس عن العمل بلا حرق (عبر Gemini)"),
            { it.trivia }, { f, v -> f.copy(trivia = v) },
        ),
        LuxoFeatureSwitch(
            lx("بين الرواية والشاشة 📖", "بين الرواية والشاشة 📖"),
            lx("إن كان العمل مقتبساً من كتاب: أهم الفروق بينهما، بعد تنبيه الحرق (عبر Gemini)", "إن كان العمل مقتبساً من كتاب: أهم الفروق بينهما، بعد تنبيه الحرق (عبر Gemini)"),
            { it.bookVsScreen }, { f, v -> f.copy(bookVsScreen = v) },
        ),
        LuxoFeatureSwitch(
            lx("أين يُعرض رسمياً؟ 📺", "أين يُعرض رسمياً؟ 📺"),
            lx("المنصات الرسمية التي يتوفر فيها العمل في السعودية (من TMDB)", "المنصات الرسمية التي يتوفر فيها العمل في السعودية (من TMDB)"),
            { it.whereToWatch }, { f, v -> f.copy(whereToWatch = v) },
        ),
        LuxoFeatureSwitch(
            lx("دليل المحتوى للأهل 👪", "دليل المحتوى للأهل 👪"),
            lx("التصنيف العمري ومستوى العنف والألفاظ والمشاهد غير اللائقة قبل المشاهدة", "التصنيف العمري ومستوى العنف والألفاظ والمشاهد غير اللائقة قبل المشاهدة"),
            { it.parentsGuide }, { f, v -> f.copy(parentsGuide = v) },
        ),
        LuxoFeatureSwitch(
            lx("ملاحظاتي 📝", "ملاحظاتي 📝"),
            lx("ملاحظة خاصة لكل عمل لا يراها غيرك", "ملاحظة خاصة لكل عمل لا يراها غيرك"),
            { it.personalNotes }, { f, v -> f.copy(personalNotes = v) },
        ),
        LuxoFeatureSwitch(
            lx("شاهدته خارج موج 🎟️", "شاهدته خارج موج 🎟️"),
            lx("تسجيل ما شوهد في السينما أو على منصة أخرى ليكتمل السجل و«ذوقي»", "تسجيل ما شوهد في السينما أو على منصة أخرى ليكتمل السجل و«ذوقي»"),
            { it.watchedOutside }, { f, v -> f.copy(watchedOutside = v) },
        ),
        LuxoFeatureSwitch(
            lx("دليل الحلقات الحشو 🏷️", "Filler guide 🏷️"),
            lx("في الأنمي الطويل (كونان، ون بيس، ناروتو…) تظهر شارة «حشو» أو «مختلط» بجانب رقم الحلقة", "Filler/mixed badges next to anime episode numbers"),
            { it.fillerGuide }, { f, v -> f.copy(fillerGuide = v) },
        ),
        LuxoFeatureSwitch(
            lx("تخطّي الحشو تلقائياً ⏭️", "Skip filler ⏭️"),
            lx("عند الانتقال إلى الحلقة التالية تُتخطّى حلقات الحشو وتُشغَّل أول حلقة من القصة الأصلية", "Autoplay jumps over filler episodes"),
            { it.fillerSkip }, { f, v -> f.copy(fillerSkip = v) },
        ),
        LuxoFeatureSwitch(
            lx("شرح النهاية 🎬", "Ending explained 🎬"),
            lx("زر في رفيق موج يشرح نهاية العمل ومعناها، ويُطلب تأكيد قبل عرض الحرق", "A companion button that explains the ending, after a spoiler confirmation"),
            { it.endingExplained }, { f, v -> f.copy(endingExplained = v) },
        ),
        LuxoFeatureSwitch(
            lx("خريطة تقييم الحلقات 🟩", "Episode ratings map 🟩"),
            lx("في صفحة المسلسل: مربع لكل حلقة ملوّن بحسب تقييمها، موسماً بموسم", "On series pages: a square per episode coloured by its rating"),
            { it.episodeMap }, { f, v -> f.copy(episodeMap = v) },
        ),
        LuxoFeatureSwitch(
            lx("ما بدأته ولم يكتمل ⏸️", "Started, not finished ⏸️"),
            lx("قاعة في «حسابي» تجمع الأعمال المتوقفة منذ أكثر من ثلاثة أسابيع، مع الإكمال أو الإخفاء", "A profile hall with titles paused for over three weeks"),
            { it.unfinished }, { f, v -> f.copy(unfinished = v) },
        ),
        LuxoFeatureSwitch(
            lx("من في المشهد؟ 👤", "Who's in this scene? 👤"),
            lx("من قائمة المشغّل: تُلتقط الصورة الحالية ويُتعرَّف على الممثلين الظاهرين فيها وشخصياتهم (عبر Gemini)", "From the player menu: identifies the actors in the current frame (via Gemini)"),
            { it.whoInScene }, { f, v -> f.copy(whoInScene = v) },
        ),
        LuxoFeatureSwitch(
            lx("حد يومي لملف الأطفال ⏱️", "Kids daily limit ⏱️"),
            lx("مدة مشاهدة يومية لملف الأطفال، يتوقف بعدها التشغيل ويُطلب الرمز للزيادة", "A daily watch time for the kids profile; the PIN adds more"),
            { it.kidsTimeLimit }, { f, v -> f.copy(kidsTimeLimit = v) },
        ),
        LuxoFeatureSwitch(
            lx("التحدث مع الرفيق 🎙️", "Talk to the companion 🎙️"),
            lx("سؤال الرفيق بالصوت، وتُقرأ الإجابة بصوت مسموع", "Ask the companion by voice; the answer is read aloud"),
            { it.companionVoice }, { f, v -> f.copy(companionVoice = v) },
        ),
        LuxoFeatureSwitch(
            lx("يوتيوب ▶️", "YouTube ▶️"),
            lx("قاعة في «حسابي» تفتح موقع يوتيوب الرسمي داخل التطبيق، وعلى الشاشة يُفتح تطبيق يوتيوب الرسمي", "A profile hall that opens the official YouTube site in the app; on TV the official YouTube app opens"),
            { it.youtube }, { f, v -> f.copy(youtube = v) },
        ),
        LuxoFeatureSwitch(
            lx("لعبة «خمّن العمل» 🎯", "«Guess the title» 🎯"),
            lx("تظهر لقطة مموّهة من عمل تمت مشاهدته، ويُختار اسمه من أربعة خيارات", "A blurred frame from something watched; pick the right title of four"),
            { it.guessGame }, { f, v -> f.copy(guessGame = v) },
        ),
        LuxoFeatureSwitch(
            lx("شاشة التوقف 🌌", "Screensaver 🌌"),
            lx("بعد مدة بدون استخدام: خلفيات الأعمال تتحرك ببطء مع الساعة (أي لمسة أو زر يُعيد التطبيق)", "After idle time: slow-moving backdrops with the clock (any touch or key returns)"),
            { it.screensaver }, { f, v -> f.copy(screensaver = v) },
        ),
        LuxoFeatureSwitch(
            lx("صفوف «لأنه تمت مشاهدة…» 🧩", "«Because you watched» rows 🧩"),
            lx("في الرئيسية: اقتراحات مبنية على آخر الأعمال التي تمت مشاهدتها", "On the home: picks based on what was watched last"),
            { it.becauseWatched }, { f, v -> f.copy(becauseWatched = v) },
        ),
        LuxoFeatureSwitch(
            lx("دليل الشخصيات بدون حرق 👥", "Spoiler-free character guide 👥"),
            lx("في صفحة العمل: الشخصيات الرئيسية ومن يؤديها، حتى الحلقة التي تمت مشاهدتها فقط", "On the details page: main characters and who plays them, only up to where you are"),
            { it.characterGuide }, { f, v -> f.copy(characterGuide = v) },
        ),
        LuxoFeatureSwitch(
            lx("سابقًا في… 📺", "Previously on… 📺"),
            lx("عند العودة إلى مسلسل بعد انقطاع، يُعرض قبل الحلقة ملخص قصير للحلقة السابقة دون كشف للأحداث", "After a break, a short spoiler-free recap of the previous episode"),
            { it.previouslyOn }, { f, v -> f.copy(previouslyOn = v) },
        ),
        LuxoFeatureSwitch(
            lx("أين قيلت هذه العبارة؟ 🔎", "Where was this said? 🔎"),
            lx("تُكتب عبارة من عمل، فيُحدَّد الموسم والحلقة والدقيقة التي قيلت فيها تقريبًا", "Type a line and find the season, episode and minute"),
            { it.quoteFinder }, { f, v -> f.copy(quoteFinder = v) },
        ),
        LuxoFeatureSwitch(
            lx("العبارات المحفوظة 💬", "Saved quotes 💬"),
            lx("من قائمة المشغّل تُحفظ عبارة الترجمة الظاهرة، وتُجمع في صفحة العمل وتُشارك كبطاقة", "Save the subtitle line on screen; collected on the title page and shareable"),
            { it.quotes }, { f, v -> f.copy(quotes = v) },
        ),
        LuxoFeatureSwitch(
            lx("مقطع متحرك 🎞️", "Animated clip 🎞️"),
            lx("من قائمة المشغّل يُسجَّل مقطع متحرك (GIF) مدته أربع ثوانٍ ويُحفظ في الصور", "Record a 4-second GIF from the player into Photos"),
            { it.gifClip }, { f, v -> f.copy(gifClip = v) },
        ),
        LuxoFeatureSwitch(
            lx("المصدر الذكي 🎯", "Smart source 🎯"),
            lx("تُحدَّد الجودة والحجم والكلمات المفضلة مرة واحدة، فيُختار أفضل مصدر ويبدأ التشغيل مباشرة", "Set quality, size and preferred words once; the best source plays directly"),
            { it.smartSource }, { f, v -> f.copy(smartSource = v) },
        ),
        LuxoFeatureSwitch(
            lx("التحكم بالشاشة 📱", "Remote control 📱"),
            lx("يصبح الجوال أو التابلت جهاز تحكم للشاشة على الشبكة نفسها، مع زر «أكمل على الشاشة»", "Use the phone/tablet as a TV remote on the same network, plus «Continue on TV»"),
            { it.remote }, { f, v -> f.copy(remote = v) },
        ),
        LuxoFeatureSwitch(
            lx("حالة المسلسلات 📣", "Series status 📣"),
            lx("يصل إشعار عند تجديد مسلسل في المكتبة لموسم جديد أو إلغائه، أو حين تكون الحلقة القادمة ختام الموسم", "A notification when a library series is renewed, cancelled or its finale is next"),
            { it.seriesStatus }, { f, v -> f.copy(seriesStatus = v) },
        ),
        LuxoFeatureSwitch(
            lx("الإضافة إلى التقويم 📅", "Add to calendar 📅"),
            lx("في صفحة العد التنازلي: زر يضيف موعد الحلقة إلى تقويم الجهاز", "On the countdown page: add the episode date to the device calendar"),
            { it.calendarAdd }, { f, v -> f.copy(calendarAdd = v) },
        ),
        LuxoFeatureSwitch(
            lx("البحث بصورة 🖼️", "Search by image 🖼️"),
            lx("تُختار صورة أو لقطة شاشة، فيتعرّف رفيق موج على العمل ويعرضه", "Pick an image or screenshot; Mawj Companion names the title"),
            { it.imageSearch }, { f, v -> f.copy(imageSearch = v) },
        ),
        LuxoFeatureSwitch(
            lx("عجلة الاختيار 🎡", "Choice wheel 🎡"),
            lx("يختار كل فرد من العائلة عملًا، ثم تدور العجلة وتحسم ما يُشاهَد", "Everyone adds a title, the wheel decides"),
            { it.choiceWheel }, { f, v -> f.copy(choiceWheel = v) },
        ),
        LuxoFeatureSwitch(
            lx("ملف الأطفال 🧸", "Kids profile 🧸"),
            lx("عند تفعيله للملف الحالي لا تُفتح إلا الأعمال المناسبة للأطفال حسب التصنيف العمري، ويُطلب رمز لغيرها", "When on for this profile, only kid-rated titles open; others need a code"),
            { it.kidsProfile }, { f, v -> f.copy(kidsProfile = v) },
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
            lx("تعديلات جاهزة (ذكي، زاهي، طبيعي، سينما، إنقاذ HDR…) وتعديل يدوي للإضاءة والتباين والتشبع، للشاشات التي لا تدعم HDR", "Ready looks (smart, vivid, natural, cinema, HDR rescue…) plus manual brightness, contrast and saturation for screens without HDR"),
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
            lx("صفحة الممثل بتصميم موج 🎭", "Mawj actor page 🎭"),
            lx("صورة كاملة كبيرة، اشتهر بـ، أفضل المتعاونين، والأعمال بترتيب وفلتر تقييم والأفلام والمسلسلات منفصلة", "Big full photo, known for, top collaborators, and the filmography with sorting, a rating filter and movies / series apart"),
            { it.personPage }, { f, v -> f.copy(personPage = v) },
        ),
        LuxoFeatureSwitch(
            lx("ترجمة للعربي 🌐", "Arabic translation 🌐"),
            lx("تُترجم القصص والسير غير العربية، وتُكتب أسماء الممثلين بالحروف العربية (مرة واحدة ثم تُحفظ)", "Translates stories and biographies that aren't in Arabic and writes people's names in Arabic letters (once, then cached)"),
            { it.arabicTranslate }, { f, v -> f.copy(arabicTranslate = v) },
        ),
        LuxoFeatureSwitch(
            lx("تصميم موج الكامل ✨", "Full Mawj design ✨"),
            lx("كروت زجاجية وعناوين متدرجة ومفاتيح متوهجة في كل الصفحات. إيقافه يرجع شكل نوفيو", "Glass cards, gradient titles and glowing switches everywhere. Off returns the original look"),
            { it.luxoDesign }, { f, v -> f.copy(luxoDesign = v) },
        ),
        LuxoFeatureSwitch(
            lx("شريط موج Dock 🚀", "Mawj Dock bar 🚀"),
            lx("الشريط العائم الجديد: القسم المفتوح يتمدد بتدرج متوهج وحلقة ضوء عند التنقل", "The new floating bar: the open tab grows into a glowing pill with a ring of light"),
            { it.luxoDock }, { f, v -> f.copy(luxoDock = v) },
        ),
        LuxoFeatureSwitch(
            lx("صفحة الملف الشخصي بتصميم موج 🎟️", "Mawj profile page 🎟️"),
            lx("بطاقة موج Pass والقاعات بدل قائمة الإعدادات العادية", "The Mawj Pass and halls instead of the plain settings list"),
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
            lx("مربعات آخر ستة أشهر، وكلما زادت المشاهدة صار المربع أغمق", "The last 6 months as squares, darker on busier days"),
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
            lx("السنة في موج 🎬", "Your year in Mawj 🎬"),
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
            lx("تتحرك الصورة الكبيرة في الأعلى قليلاً مع ميلان الجهاز", "The big artwork drifts slightly as the device tilts"),
            { it.heroTilt }, { f, v -> f.copy(heroTilt = v) },
        ),
        LuxoFeatureSwitch(
            lx("ألوان حسب الوقت 🌅", "Time-of-day colours 🌅"),
            lx("تدفأ الإضاءة أعلى الشاشات وقت الغروب وتهدأ ليلاً", "The glow warms at sunset and cools at night"),
            { it.timeTint }, { f, v -> f.copy(timeTint = v) },
        ),
        LuxoFeatureSwitch(
            lx("استوديو الثيمات", "Theme studio"),
            lx("ألوان موج الجاهزة وتركيب ألوان خاصة ومشاركتها بكود", "Mawj palettes, your own mix and a shareable code"),
            { it.themeStudio }, { f, v -> f.copy(themeStudio = v) },
        ),
    )
    LuxoBlock(isTablet) { com.nuvio.app.features.luxo.LuxoGeminiKeyBlock() }
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
            if (features.smartSource) {
                androidx.compose.material3.FilterChip(
                    selected = com.nuvio.app.features.luxo.LuxoSmartSource.rules.enabled,
                    onClick = { com.nuvio.app.features.luxo.LuxoOverlays.smartSource = true },
                    label = { androidx.compose.material3.Text(lx("🎯 المصدر الذكي", "🎯 Smart source")) },
                )
            }
            if (features.franchises) {
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { com.nuvio.app.features.luxo.LuxoOverlays.franchise = "" },
                    label = { androidx.compose.material3.Text(lx("🎞️ السلاسل", "🎞️ Franchises")) },
                )
            }
        }
    }
    if (features.screensaver) {
        LuxoBlock(isTablet) {
            LuxoLabel(lx("تظهر شاشة التوقف بعد", "Screensaver after"))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(2, 5, 10, 20).forEach { m ->
                    androidx.compose.material3.FilterChip(
                        selected = com.nuvio.app.features.luxo.LuxoIdle.delayMinutes == m,
                        onClick = { com.nuvio.app.features.luxo.LuxoIdle.setDelay(m) },
                        label = { androidx.compose.material3.Text(lx("$m دقائق", "$m min")) },
                    )
                }
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { com.nuvio.app.features.luxo.LuxoIdle.showNow = true },
                    label = { androidx.compose.material3.Text(lx("معاينة", "Preview")) },
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

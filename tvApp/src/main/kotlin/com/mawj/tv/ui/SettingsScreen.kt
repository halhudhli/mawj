package com.mawj.tv.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mawj.tv.BuildConfig
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Store
import com.mawj.tv.link.PhoneLink
import kotlinx.coroutines.launch

private val PRESETS = listOf(
    Triple("moon-star-twinkle", "هدوء الليل", "موازنة الصوت وخفض الانفجارات"),
    Triple("music-sparkle-sound", "سماعة", "توضيح الحوار وصوت متوازن"),
    Triple("popcorn-pop", "سهرة", "صورة زاهية وصوت كامل"),
    Triple("ticket-tear", "سينما", "ألوان السينما والصوت الأصلي"),
)

/** One touch sets the picture and sound tools together; the same touch again puts back the plain look. */
private fun applyPreset(i: Int) {
    val same = Store.text("preset", "") == PRESETS[i].second
    val (night, level, dialogue, color) = when {
        same -> listOf(false, false, false, "natural0")
        i == 0 -> listOf(true, true, false, "natural0")
        i == 1 -> listOf(false, true, true, "natural")
        i == 2 -> listOf(false, false, false, "vivid")
        else -> listOf(false, false, false, "cinema")
    }
    Store.setFlag("night", night as Boolean); Store.setFlag("level", level as Boolean); Store.setFlag("dialogue", dialogue as Boolean)
    Store.colorPreset = color as String
    Store.setText("preset", if (same) "" else PRESETS[i].second)
}

private val LAYERS = listOf(
    Triple("السطح", "ما يُرى", listOf(Triple("user-star-proud", "ذوقي", "taste"), Triple("sliders-sparkle-tune", "المظهر", "look"), Triple("list-star-knock", "الصفحات", "pages"))),
    Triple("العمق", "ما يُسمع ويُرى", listOf(Triple("list-sparkle-scroll", "المحتوى", "content"), Triple("play-circle-start", "التشغيل", "playback"), Triple("api-sparkle-call", "التكاملات", "integrations"), Triple("calendar-repeat-cycle", "التتبع", "tracking"))),
    Triple("القاع", "ما يعمل خلف الستار", listOf(Triple("user-switch-swap", "الحساب", "account"), Triple("sliders-move", "متقدم", "advanced"), Triple("arrow-down-to-line-bounce", "التحديثات", "updates"), Triple("info-drop", "حول", "about"))),
)

val TOOLS = listOf(
    Triple("film-advance", "السلاسل", "franchises"), Triple("clock-spin", "كم عندي وقت؟", "time"), Triple("calendar-flip", "تقويم الأنمي", "anime"),
    Triple("popcorn-pop", "ماذا نشاهد معاً؟", "together"), Triple("monitor-share-flick", "الشاشة الثانية", "second"), Triple("bar-chart-horizontal-rerank", "ترتيبي الخاص", "tier"),
    Triple("play-pause", "ما بدأته ولم يكتمل", "unfinished"), Triple("dice-tumble", "عجلة الاختيار", "wheel"), Triple("baby-rock", "ملف الأطفال", "kids"),
    Triple("eye-off-blink", "الوضع الخاص", "private"), Triple("eye-hide", "الأعمال المخفية", "hidden"), Triple("sparkles-spark", "ماذا نشاهد الليلة؟", "tonight"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 32.dp, top = 30.dp, bottom = 50.dp)) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                PageHead("أوضاع بلمسة", "كل وضع يضبط الصورة والصوت دفعة واحدة، ولمسة ثانية تعيد ما كان.", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PRESETS.forEachIndexed { i, (icon, name, note) ->
                    IconTile(icon, name, note, selected = Store.text("preset", "") == name, modifier = Modifier.weight(1f).then(if (i == 0) restoreFirst() else Modifier)) { applyPreset(i) }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("حجم البوسترات", 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                listOf("صغير", "متوسط", "كبير", "كبير جداً").forEachIndexed { i, n ->
                    Chip(n, Store.number("posterSize", 2) == i, onClick = { Store.setNumber("posterSize", i); PosterSizeVersion.bump() })
                }
            }
        }
        item {
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LAYERS.forEach { (name, note, items) ->
                    Glass(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            T(name, 18.sp, color = C.GoldLight, weight = FontWeight.Bold)
                            T("  $note", 11.sp, color = C.Muted)
                        }
                        items.forEach { (icon, label, page) ->
                            IconTile(icon, label, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                if (page == "taste") Nav.tab(Route.Profile) else Nav.go(Route.SettingsPage(page))
                            }
                        }
                    }
                }
            }
        }
        item {
            T("أدوات موج", 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TOOLS.forEach { (icon, label, kind) -> IconTile(icon, label, modifier = Modifier.width(196.dp)) { Nav.go(Route.Tool(kind)) } }
            }
        }
        item { T("موج · الإصدار ${BuildConfig.VERSION_NAME}", 12.sp, color = C.Dim, modifier = Modifier.padding(top = 20.dp)) }
    }
}

/** Bumped when the poster size changes, so the rows draw again at the new size. */
object PosterSizeVersion {
    var v by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun bump() { v++ }
}

/** One page of the three layers. */
@Composable
fun SettingsPageScreen(page: String) {
    val title = LAYERS.flatMap { it.third }.firstOrNull { it.third == page }?.second ?: "الإعدادات"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 34.dp, bottom = 50.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHead(title, modifier = Modifier.padding(bottom = 10.dp)) }
        when (page) {
            "look" -> switches(listOf(
                Triple("الأيقونات المتحركة", "تتحرك الأيقونة مرة واحدة عند الوقوف عليها", "animatedIcons"),
                Triple("تبديل البوسترات", "في الصف المحدد، والبوستر العربي أولاً", "posterRotate"),
                Triple("حركة البوسترات", "البوستر المحدد وحده يتنفس ببطء وتعبره لمعة، وتتوقف أثناء التنقل", "posterMotion"),
                Triple("الكتالوج المتحرك", "بطاقة «الجزر» المحددة وحدها تقلّب صورها", "animatedCatalog"),
                Triple("زينة المواسم", "لمسة ثابتة في رمضان والعيد واليوم الوطني", "seasonalThemes"),
                Triple("أصوات التنقل", "صوت قصير جداً عند التنقل", "uiSounds"),
                Triple("شاشة التوقف", "تبديل هادئ للخلفيات عند ترك الشاشة دقائق", "screensaver"),
            ))
            "pages" -> switches(listOf(
                Triple("بوصلة المزاج", "خمسة أمزجة في الرئيسية", "moodCompass"),
                Triple("ماذا نشاهد الليلة؟", "اختيار بالمزاج والوقت", "tonightPick"),
                Triple("الأعلى مشاهدة بالأرقام", "صف العشرة الأوائل بأرقام كبيرة", "top10Rank"),
                Triple("لأنك شاهدت", "صف مقترحات من آخر عمل شوهد", "becauseWatched"),
                Triple("العد التنازلي", "أفلام قادمة في «الجزر»", "countdown"),
                Triple("خط الحلقات", "تقييم كل حلقة في صفحة المسلسل", "episodeCurve"),
                Triple("لماذا قد يعجب", "ثلاثة أسباب في صفحة العمل", "whyLike"),
                Triple("خطة الإنهاء", "كم حلقة في اليوم ومتى ينتهي", "finishPlan"),
                Triple("أين يُعرض رسمياً", "المنصات الرسمية للعمل", "whereToWatch"),
                Triple("دليل الأهل", "التصنيف العمري للعمل", "parentsGuide"),
                Triple("المزيد عن العمل", "أسئلة جيميني عند الطلب فقط", "geminiExtras"),
                Triple("من أين أبدأ؟", "ثلاثة اختيارات في صفحة الممثل", "actorStart"),
                Triple("قاعة الاستوديو", "الحمض النووي والأيقونات والعصور", "companyHall"),
            ))
            "content" -> { item { AddonsList() }; switches(listOf(Triple("إخفاء نسخ الكام", "لا تظهر نسخ التصوير من السينما في المصادر", "hideCam"))) }
            "playback" -> switches(listOf(
                Triple("التشغيل المباشر للملفات الثقيلة", "4K و10-bit والملفات الكبيرة تُعرض مباشرة دون نسخ الإطارات، أخف على الشاشة", "directHeavy"),
                Triple("تنبيه المصادر البطيئة", "يُعلَّم ما لا تدعمه الشاشة (AV1 وغيره) ولا يكون «الأنسب»", "codecGuard"),
                Triple("«الأنسب» في المصادر", "يُعلَّم أنسب مصدر ويُوضع أولاً", "bestSource"),
                Triple("تخطّي الحشو", "في الأنمي الطويل تُتخطّى حلقات الحشو", "fillerSkip"),
                Triple("الوقت المتبقي", "«متبقٍ N د» على بطاقات المتابعة", "timeLeft"),
            ))
            "integrations" -> item {
                Glass {
                    T("TMDB: " + if (com.mawj.tv.data.Tmdb.enabled) "مفعّل" else "غير مفعّل", 15.sp)
                    T("جيميني: " + if (com.mawj.tv.data.Gemini.enabled) "مفعّل" else "غير مفعّل", 15.sp, modifier = Modifier.padding(top = 6.dp))
                    T("حساب Nuvio: " + (com.mawj.tv.data.Account.email?.let { "مسجَّل" } ?: "غير مسجَّل"), 15.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            "tracking" -> item { Glass { T("المزامنة تتم عبر حساب Nuvio (متابعة المشاهدة والإضافات). ربط Trakt يُضاف لاحقاً.", 15.sp, lineHeight = 24.sp) } }
            "account" -> item { Row { Column(Modifier.weight(1f)) { AccountCard() }; Spacer(Modifier.width(20.dp)); PhoneLinkCard() } }
            "advanced" -> switches(listOf(
                Triple("الوضع الخاص", "لا يُسجَّل شيء في الملف ولا في «متابعة المشاهدة»", "privateMode"),
                Triple("ملف الأطفال", "يقتصر الاكتشاف على ما يناسب الصغار", "kidsMode"),
                Triple("مقياس الأداء", "رقم صغير يبين سلاسة الحركة", "perfMeter"),
            ))
            "updates" -> item { Glass { T("الإصدار الحالي: ${BuildConfig.VERSION_NAME}", 16.sp); T("تُنزَّل الإصدارات الجديدة من صفحة إصدارات موج.", 13.sp, color = C.Muted, modifier = Modifier.padding(top = 6.dp)) } }
            "about" -> item {
                Glass {
                    T("موج للشاشة", 18.sp, weight = FontWeight.Bold)
                    T("البيانات والصور: TMDB · التشغيل: mpv · الأيقونات المتحركة: Gentleface (MIT) · الخطوط: ثمانية وBarlow", 13.sp, color = C.Muted, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.switches(list: List<Triple<String, String, String>>) {
    list.forEachIndexed { i, (t, n, k) -> item(key = k) { Box(if (i == 0) restoreFirst() else Modifier) { SwitchRow(t, n, k) } } }
}

@Composable
private fun AddonsList() {
    val names = remember { mutableStateMapOf<String, String>() }
    val urls = Store.addonUrls.toList()
    LaunchedEffect(urls) { urls.forEach { u -> Addons.manifest(u)?.let { names[u] = it.name } } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T("الإضافات المثبّتة، بترتيب ظهورها في الرئيسية. تُضاف من الجوال عبر «الربط بالجوال».", 13.sp, color = C.Muted)
        urls.forEachIndexed { i, u ->
            Row(
                Modifier.fillMaxWidth().background(Color(0xCC111A24), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    T(names[u] ?: "…", 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T(u.substringAfter("://").substringBefore("/"), 11.sp, color = C.Dim, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("أعلى", false, onClick = { Store.moveAddon(u, up = true) }, modifier = if (i == 0) restoreFirst() else Modifier)
                    Chip("أسفل", false, onClick = { Store.moveAddon(u, up = false) })
                    Chip("حذف", false, onClick = { Store.removeAddon(u) })
                }
            }
        }
        Pill("استعادة الإضافات الأساسية") { Store.DEFAULT_ADDONS.forEach(Store::addAddon) }
    }
}

@Composable
fun PhoneLinkCard() {
    val address = remember { PhoneLink.address }
    Column(
        Modifier.width(300.dp).background(C.Raised, RoundedCornerShape(18.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("الربط بالجوال", 20.sp, weight = FontWeight.Bold)
        T("يُصوَّر الرمز بكاميرا الجوال على نفس شبكة المنزل، لإضافة الإضافات والبحث من الجوال.", 13.sp, color = C.Muted, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        if (address == null) {
            T("الشاشة غير متصلة بشبكة منزلية.", 14.sp, color = C.Red)
        } else {
            Box(Modifier.size(200.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(10.dp)) { Qr(address) }
            T(address, 15.sp, color = C.GoldLight, modifier = Modifier.padding(top = 12.dp))
            T("رمز الربط مع ريموت التابلت: ${com.mawj.tv.link.RemoteLink.code}", 14.sp, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun Qr(text: String) {
    val matrix = remember(text) { runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull() } ?: return
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** The Nuvio account: who is signed in, refresh the addons from it, or sign out / in. */
@Composable
fun AccountCard() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).background(C.Raised, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("الحساب", 15.sp, color = C.Muted)
            T(com.mawj.tv.data.Account.email ?: "غير مسجَّل", 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
            note?.let { T(it, 13.sp, color = C.Green) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (com.mawj.tv.data.Account.signedIn) {
                Chip("تحديث الإضافات من الحساب", false, onClick = {
                    scope.launch { val n = com.mawj.tv.data.Account.pullAddons(); note = if (n > 0) "نُزّلت $n إضافة من الحساب" else "لم تُعثر على إضافات في الحساب" }
                })
                Chip("تسجيل الخروج", false, onClick = { com.mawj.tv.data.Account.signOut(); note = null })
            } else {
                Chip("تسجيل الدخول", false, onClick = { com.mawj.tv.Nav.go(com.mawj.tv.Route.Login) })
            }
        }
    }
}

/** When a tablet asks to pair, the 4-digit code shows here by itself (it closes once paired). */
@Composable
fun PairingOverlay() {
    val from = com.mawj.tv.link.RemoteLink.pairingFrom ?: return
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.background(Color(0xF20D1620), RoundedCornerShape(20.dp)).padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T("طلب ربط من «$from»", 16.sp, color = C.Muted)
            T("يُدخَل هذا الرمز في التابلت", 14.sp, color = C.Dim, modifier = Modifier.padding(bottom = 10.dp))
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.mawj.tv.link.RemoteLink.code.forEach { d ->
                        Box(Modifier.size(54.dp, 64.dp).background(C.Sea, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            T(d.toString(), 32.sp, color = C.GoldLight, weight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

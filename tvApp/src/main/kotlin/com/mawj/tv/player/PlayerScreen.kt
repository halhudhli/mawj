package com.mawj.tv.player

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Episode
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Progress
import com.mawj.tv.data.Store
import com.mawj.tv.data.Stream
import com.mawj.tv.ui.C
import com.mawj.tv.ui.Chip
import com.mawj.tv.ui.MIcon
import com.mawj.tv.ui.T
import com.mawj.tv.ui.tvFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class Panel { None, Subs, Audio, Speed, Info, Tools, Colors, Bookmarks, Sleep, Companion, Who, Quiz, Quotes }

/** Panels and bars are see-through a little, so the picture stays in view. */
internal val PanelBg = Color(0xD1070D14)

/** As on the tablet: the panels sit on the dimmed picture, with no box around them. */
internal val PanelDim = Color(0xBD000000)

/** «ألوان الصورة»: ready looks (mpv brightness, contrast, saturation, gamma). */
private val COLOR_PRESETS = listOf(
    Triple("natural0", "الأصلي", intArrayOf(0, 0, 0, 0)),
    Triple("natural", "طبيعي", intArrayOf(2, 6, 6, 0)),
    Triple("vivid", "زاهي", intArrayOf(3, 14, 26, 0)),
    Triple("cinema", "سينما", intArrayOf(-3, 10, -4, -6)),
    Triple("hdr", "إنقاذ HDR", intArrayOf(8, 12, 18, 12)),
)

/** Picks the stream for the next episode: same release first, then same addon and quality. */
suspend fun nextEpisodeStream(type: String, next: Episode, current: Stream): Stream? {
    val list = Addons.streams(type, next.id)
    return current.bingeGroup?.let { bg -> list.firstOrNull { it.bingeGroup == bg } }
        ?: list.firstOrNull { it.addonName == current.addonName && it.quality == current.quality }
        ?: list.firstOrNull { it.addonName == current.addonName }
}

/**
 * The player. Hidden controls: OK pauses, left/right jump 10 s, up/down show the controls.
 * Shown controls: one row of named buttons (subtitles, audio, timing, speed, next, more). «المزيد»
 * opens Mawj's tools. Back closes a panel or the sync bar, then the controls, then leaves.
 */
@Composable
fun PlayerScreen(req: PlayRequest) {
    var view by remember { mutableStateOf<MpvView?>(null) }
    // Heavy files play straight to the screen (no frame copy): lighter, but no picture tools.
    var direct by remember { mutableStateOf(com.mawj.tv.data.Store.feature("directHeavy") && com.mawj.tv.data.Caps.heavy(req.stream)) }
    // Where to start: the saved place, or where direct mode stopped when it falls back.
    var startAt by remember { mutableStateOf(req.startMs) }
    var pos by remember { mutableLongStateOf(req.startMs) }
    var dur by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.None) }
    var syncBar by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var subDelay by remember { mutableDoubleStateOf(Store.subDelay(req.meta.id)) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var next by remember { mutableStateOf<Episode?>(null) }
    var countdown by remember { mutableIntStateOf(-1) }
    var nextDismissed by remember { mutableStateOf(false) }
    var nextBusy by remember { mutableStateOf(false) }
    var subScale by remember { mutableDoubleStateOf(Store.number("subScale", 100) / 100.0) }
    // «سمعت» / «ظهرت»: from the TV bar or from the tablet; both stamped with the video's time.
    var heardMs by remember { mutableStateOf<Long?>(null) }
    var seenMs by remember { mutableStateOf<Long?>(null) }
    var lastShift by remember { mutableStateOf<Double?>(null) }
    // Mawj tools.
    var dialogue by remember { mutableStateOf(Store.flag("dialogue")) }
    var night by remember { mutableStateOf(Store.flag("night")) }
    var level by remember { mutableStateOf(Store.flag("level")) }
    var colorKey by remember { mutableStateOf(Store.colorPreset) }
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var sleepLeftMs by remember { mutableLongStateOf(0L) }
    var sleepAtEnd by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var bookmarksVersion by remember { mutableIntStateOf(0) }
    var quotesVersion by remember { mutableIntStateOf(0) }
    // The last subtitle lines shown (the companion's context); read only when Gemini is on.
    val subLines = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var addonSubs by remember { mutableStateOf<List<com.mawj.tv.data.Subtitle>>(emptyList()) }
    var translatePct by remember { mutableStateOf<Int?>(null) }
    // The tablet's bar: closed (three tools and «›») or open (every tool).
    var expanded by remember { mutableStateOf(false) }
    // «تخطّي المقدمة» and «دليل المحتوى».
    var skipParts by remember { mutableStateOf<List<com.mawj.tv.data.SkipPart>>(emptyList()) }
    var skipped by remember { mutableStateOf(setOf<String>()) }
    var advisory by remember { mutableStateOf<Pair<String?, List<String>>?>(null) }
    var advisoryOn by remember { mutableStateOf(false) }
    val skipFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    val openers = remember { Panel.entries.associateWith { FocusRequester() } }

    fun say(text: String) {
        toast = text
        scope.launch { delay(2_500); if (toast == text) toast = null }
    }

    /** Keeps the position; [push] also sends it to the account (on pause and on leaving, not every few seconds). */
    fun save(push: Boolean = false) {
        val v = view ?: return
        val d = v.durationMs
        if (d <= 0) return
        val p = Progress(
            meta = req.meta, videoId = req.videoId,
            season = req.episode?.season ?: 0, episode = req.episode?.number ?: 0, episodeTitle = req.episode?.title,
            positionMs = v.positionMs, durationMs = d, updatedAt = System.currentTimeMillis(),
        )
        Store.saveProgress(p)
        if (push) com.mawj.tv.data.Account.pushProgress(p)
    }

    fun playNext() {
        val ep = next ?: return
        if (nextBusy) return
        nextBusy = true
        save()
        scope.launch {
            val s = nextEpisodeStream(req.meta.type, ep, req.stream)
            if (s != null) {
                Store.rememberSource(req.meta.id, s)
                Nav.go(Route.Player(PlayRequest(req.meta, ep.id, ep, s, Store.positionFor(ep.id))))
            } else {
                Nav.back(); Nav.go(Route.Sources(req.meta, ep))
            }
        }
    }

    fun mark(kind: String) {
        val p = view?.positionMs ?: return
        if (kind == "heard") heardMs = p else seenMs = p
    }

    fun showControls(focus: FocusRequester = playFocus) {
        lastInput = System.currentTimeMillis()
        controls = true
        scope.launch { delay(30); runCatching { focus.requestFocus() } }
    }

    fun closePanel() {
        val back = if (panel in listOf(Panel.Tools, Panel.Colors, Panel.Bookmarks, Panel.Sleep, Panel.Info, Panel.Companion, Panel.Who, Panel.Quiz, Panel.Quotes)) moreFocus else openers[panel]
        panel = Panel.None
        scope.launch { delay(30); runCatching { (back ?: playFocus).requestFocus() } }
    }

    fun openSync() {
        panel = Panel.None
        controls = false
        syncBar = true
    }

    fun applyFx() {
        view?.setAudioFx(dialogue, night, level)
        Store.setFlag("dialogue", dialogue); Store.setFlag("night", night); Store.setFlag("level", level)
    }

    fun applyColors() {
        val values = COLOR_PRESETS.firstOrNull { it.first == colorKey }?.third ?: intArrayOf(0, 0, 0, 0)
        view?.setColors(values[0], values[1], values[2], values[3])
        Store.colorPreset = colorKey
    }

    fun applySubStyle() {
        view?.setSubStyle(
            Store.flag("subBold"), Store.text("subColor", "#FFFFFF"), Store.text("subEdge", "border"), Store.number("subMargin", 40),
        )
    }

    fun switchSource() {
        save()
        Nav.back()
        if (Nav.top !is Route.Sources) Nav.go(Route.Sources(req.meta, req.episode))
    }

    /** Every tool of the open bar, in the tablet's order (the ones that suit a TV). */
    fun playerTools(v: MpvView): List<ToolItem> = buildList {
        if (com.mawj.tv.data.Gemini.enabled) add(ToolItem("speech-to-text-transcribe", "ترجمة للعربي", translatePct?.let { "$it٪" }) {
            if (translatePct == null) {
                val src = addonSubs.filterNot { it.lang.lowercase().startsWith("ar") }
                    .sortedBy { if (it.lang.lowercase().startsWith("en")) 0 else 1 }.firstOrNull()
                if (src == null) say("لا توجد ترجمة بلغة أخرى لهذه الحلقة") else scope.launch {
                    translatePct = 0
                    val r = com.mawj.tv.data.SubTranslate.translate(src) { p -> translatePct = p }
                    translatePct = null
                    when (r) {
                        is com.mawj.tv.data.SubTranslate.Result.Ready -> { v.addSubtitle(r.path, "عربي ✨ ترجمة موج", "ar", select = true); tracksVersion++; say("الترجمة العربية جاهزة ✅") }
                        com.mawj.tv.data.SubTranslate.Result.AlreadyArabic -> say("هذه الترجمة عربية ولا تحتاج ترجمة")
                        com.mawj.tv.data.SubTranslate.Result.Unreadable -> say("تعذّرت قراءة ملف الترجمة")
                        com.mawj.tv.data.SubTranslate.Result.Failed -> say("تعذّرت الترجمة الآن")
                    }
                }
            }
        })
        add(ToolItem("bookmark-drop", "علامة هنا") { Store.addBookmark(req.videoId, v.positionMs); bookmarksVersion++; say("حُفظت علامة عند ${time(v.positionMs)}") })
        add(ToolItem("bookmarks-cycle", "العلامات", focus = openers.getValue(Panel.Bookmarks)) { panel = Panel.Bookmarks })
        add(ToolItem("user-voice-speak", "توضيح الحوار", if (dialogue) "مفعّل" else null, on = dialogue) { dialogue = !dialogue; applyFx() })
        add(ToolItem("moon-star-twinkle", "صوت ليلي", if (night) "مفعّل" else null, on = night) { night = !night; applyFx() })
        add(ToolItem("message-text-type", "حفظ العبارة") {
            val line = v.subText()
            if (line == null) say("لا توجد عبارة على الشاشة الآن") else { Store.addQuote(req.videoId, v.positionMs, line); quotesVersion++; say("حُفظت العبارة") }
        })
        add(ToolItem("messages-reply", "العبارات", focus = openers.getValue(Panel.Quotes)) { panel = Panel.Quotes })
        if (!direct) add(ToolItem("color-filter-sparkle-split", "ألوان الصورة", COLOR_PRESETS.firstOrNull { it.first == colorKey }?.second?.takeIf { colorKey != "natural0" }, on = colorKey != "natural0", focus = openers.getValue(Panel.Colors)) { panel = Panel.Colors })
        add(ToolItem("waveform-scroll", "موازنة الصوت", if (level) "مفعّلة" else null, on = level) { level = !level; applyFx() })
        if (!direct) add(ToolItem("camera-snap", "لقطة شاشة") {
            scope.launch { val ok = saveShot(v, req.meta.name, v.positionMs); say(if (ok) "حُفظت اللقطة، وتُفتح من الجوال عبر «الربط بالجوال»" else "تعذّر التقاط هذا المشهد") }
        })
        add(ToolItem("repeat-chase", "تكرار مقطع", when { loopA == null -> null; loopB == null -> "البداية محددة"; else -> "يعمل" }, on = loopB != null) {
            when {
                loopA == null -> { loopA = v.positionMs; say("حُددت بداية المقطع، ويُضغط مرة أخرى عند نهايته") }
                loopB == null -> { val b = v.positionMs; if (b > loopA!! + 1_000) { loopB = b; v.setAbLoop(loopA, loopB); v.seekTo(loopA!!); say("يتكرر المقطع الآن") } }
                else -> { loopA = null; loopB = null; v.setAbLoop(null, null); say("أُلغي تكرار المقطع") }
            }
        })
        add(ToolItem("zzz-float", "مؤقت النوم", when { sleepAtEnd -> "نهاية الحلقة"; sleepLeftMs > 0 -> "${(sleepLeftMs + 59_999) / 60_000} د"; else -> null }, on = sleepAtEnd || sleepLeftMs > 0, focus = openers.getValue(Panel.Sleep)) { panel = Panel.Sleep })
        add(ToolItem("ear-sound-hear", "سمعت · ظهرت", if (subDelay != 0.0) fmtDelay(subDelay) else null) { openSync() })
        add(ToolItem("code-block-type", "ترميز الترجمة") { tracksVersion++; panel = Panel.Subs })
        // «ماذا قال؟»: back ten seconds with the subtitles shown for that moment only.
        add(ToolItem("undo-return", "ماذا قال؟") {
            v.seekBy(-10)
            val subs = v.tracks().filter { it.type == "sub" }
            if (subs.none { it.selected }) {
                val pick = subs.firstOrNull { it.lang.startsWith("ar") } ?: subs.firstOrNull()
                if (pick != null) {
                    v.select(pick, "sub"); tracksVersion++
                    scope.launch { delay(14_000); v.select(null, "sub"); tracksVersion++ }
                } else say("لا توجد ترجمة لهذا المقطع")
            }
        })
        if (com.mawj.tv.data.Gemini.enabled) {
            add(ToolItem("help-circle-tilt", "اختبار الحلقة") { v.pause(); panel = Panel.Quiz })
            if (!direct) add(ToolItem("eye-sparkle-focus", "من في المشهد؟") { v.pause(); panel = Panel.Who })
        }
        add(ToolItem("replace-exchange", "تبديل المصدر ") { switchSource() })
    }

    BackHandler {
        when {
            syncBar -> { syncBar = false; heardMs = null; seenMs = null; runCatching { rootFocus.requestFocus() } }
            panel != Panel.None -> closePanel()
            countdown >= 0 -> { nextDismissed = true; countdown = -1 }
            controls -> { controls = false; runCatching { rootFocus.requestFocus() } }
            else -> { save(); Nav.back() }
        }
    }

    // Load, the saved subtitle delay and look, the sound and colour tools, and the addons' subtitles.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        v.load(req.stream.url, req.stream.headers, startAt)
        if (subDelay != 0.0) v.setSubDelay(subDelay)
        if (subScale != 1.0) v.setSubScale(subScale)
        applySubStyle()
        if (dialogue || night || level) applyFx()
        if (colorKey != "natural0") applyColors()
        val subs = Addons.subtitles(req.meta.type, req.videoId)
        addonSubs = subs
        var waited = 0
        while (v.durationMs <= 0 && waited < 40) { delay(250); waited++ }
        var hasArabic = v.tracks().any { it.type == "sub" && it.lang.startsWith("ar") }
        req.extraSubs.forEach { s ->
            val ar = s.lang.startsWith("ar")
            v.addSubtitle(s.url, s.label, s.lang, select = !hasArabic && ar)
            if (ar) hasArabic = true
        }
        subs.filter { it.lang.startsWith("ar") }.take(5).forEachIndexed { i, s ->
            v.addSubtitle(s.url, "${s.label} ${i + 1}", s.lang, select = !hasArabic && i == 0)
        }
        subs.filter { it.lang.startsWith("en") }.take(2).forEach { v.addSubtitle(it.url, it.label, it.lang, select = false) }
        tracksVersion++
    }

    // Both presses in: the difference moves the subtitles and is kept for this title.
    LaunchedEffect(heardMs, seenMs) {
        val h = heardMs; val s = seenMs
        if (h != null && s != null) {
            val shift = (h - s) / 1000.0
            subDelay = ((subDelay + shift) * 100).toLong() / 100.0
            view?.setSubDelay(subDelay)
            Store.saveSubDelay(req.meta.id, subDelay)
            lastShift = shift
            delay(2_000)
            heardMs = null; seenMs = null
        }
    }

    // The tablet's remote sees and drives this player.
    DisposableEffect(view) {
        val v = view
        val link = if (v == null) null else object : com.mawj.tv.link.RemotePlayer {
            override fun state(): kotlinx.serialization.json.JsonObject {
                val t = v.tracks()
                val a = t.filter { it.type == "audio" }; val sb = t.filter { it.type == "sub" }
                return com.mawj.tv.link.RemoteLink.playerState(
                    req.meta, req.episode, pos, dur, !paused,
                    a.map { it.id to it.label }, a.firstOrNull { it.selected }?.id ?: -1,
                    sb.map { it.id to it.label }, sb.firstOrNull { it.selected }?.id ?: -1,
                    (subDelay * 1000).toInt(), (subScale * 18).toInt(), speed.toFloat(), next != null,
                )
            }
            override fun seekTo(ms: Long) { v.seekTo(ms) }
            override fun audio(id: Int) { v.select(Track(id, "audio", "", "", true), "audio"); tracksVersion++ }
            override fun subtitle(id: Int) { v.select(if (id < 0) null else Track(id, "sub", "", "", true), "sub"); tracksVersion++ }
            override fun subDelayMs(ms: Int) { subDelay = ms / 1000.0; v.setSubDelay(subDelay) }
            override fun subSize(sp: Int) { subScale = (sp / 18.0).coerceIn(0.5, 2.0); v.setSubScale(subScale) }
            override fun speed(v2: Float) { speed = v2.toDouble(); v.setSpeed(speed) }
            override fun next() { playNext() }
            override fun syncMark(kind: String) {
                if (!syncBar) openSync()
                mark(kind)
            }
        }
        com.mawj.tv.link.RemoteLink.player = link
        onDispose { if (com.mawj.tv.link.RemoteLink.player === link) com.mawj.tv.link.RemoteLink.player = null }
    }

    // The next episode, for the button and the countdown.
    LaunchedEffect(req.videoId) {
        val ep = req.episode ?: return@LaunchedEffect
        val eps = Addons.meta(req.meta.type, req.meta.id)?.videos.orEmpty().filter { it.season > 0 }
        val i = eps.indexOfFirst { it.id == ep.id }
        // «تخطّي الحشو»: the next episode that isn't filler.
        val filler = com.mawj.tv.data.Filler.fillerIds(req.meta, eps)
        next = if (i >= 0) eps.drop(i + 1).firstOrNull { it.id !in filler } else null
    }

    // «تخطّي المقدمة»: the parts of this episode that can be skipped.
    LaunchedEffect(req.videoId) {
        if (!Store.feature("skipIntro")) return@LaunchedEffect
        skipParts = runCatching { com.mawj.tv.data.Skip.parts(req.meta, req.episode) }.getOrDefault(emptyList())
    }

    // «دليل المحتوى»: the age rating and kinds of scenes, top right, for eight seconds once the picture moves.
    LaunchedEffect(req.videoId) {
        if (!Store.feature("playerAdvisory")) return@LaunchedEffect
        val id = com.mawj.tv.data.Discover.tmdbIdOf(req.meta) ?: return@LaunchedEffect
        val kind = com.mawj.tv.data.Discover.kindOf(req.meta)
        val cert = com.mawj.tv.data.Discover.certification(kind, id)
        val facts = com.mawj.tv.data.Discover.facts(req.meta)
        val flags = facts?.genres.orEmpty().mapNotNull { g ->
            when (g) { "رعب" -> "رعب"; "جريمة" -> "عنف وجريمة"; "حرب" -> "مشاهد حرب"; "إثارة" -> "توتر وإثارة"; "رومانسي" -> "مشاهد عاطفية"; else -> null }
        }.distinct().take(3)
        val rating = cert?.let { c -> if (c.any(Char::isDigit)) "+" + c.filter(Char::isDigit) else c }
        advisory = rating to flags
        while (dur <= 0L || pos < 1_000) delay(500)
        advisoryOn = true
        delay(8_000)
        advisoryOn = false
    }

    // Direct mode guard: if the picture does not start moving within a few seconds, the same
    // source reopens at once in the normal mode (the one that plays every 1080p file).
    LaunchedEffect(view, direct) {
        val v = view ?: return@LaunchedEffect
        if (!direct) return@LaunchedEffect
        delay(9_000)
        val a = withContext(kotlinx.coroutines.Dispatchers.IO) { v.positionMs }
        delay(3_000)
        val b = withContext(kotlinx.coroutines.Dispatchers.IO) { v.positionMs }
        val stuck = withContext(kotlinx.coroutines.Dispatchers.IO) { v.durationMs <= 0 || (b <= a && !v.paused) }
        if (stuck) {
            startAt = maxOf(b, req.startMs)
            view = null
            v.release()
            direct = false
        }
    }

    // A light poll twice a second.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(500)
            pos = v.positionMs
            dur = v.durationMs
            val nowPaused = v.paused
            if (nowPaused && !paused && dur > 0) save(push = true)
            paused = nowPaused
            buffering = v.buffering || dur == 0L
            if (controls && panel == Panel.None && !paused && System.currentTimeMillis() - lastInput > (if (expanded) 9_000 else 5_000)) {
                controls = false
                expanded = false
                runCatching { rootFocus.requestFocus() }
            }
            if (++tick % 30 == 0) save()
            if (com.mawj.tv.data.Gemini.enabled) v.subText()?.let { line ->
                if (subLines.lastOrNull() != line) { subLines.add(line); if (subLines.size > 30) subLines.removeAt(0) }
            }
            // «مؤقت النوم»: counts only while the video plays.
            if (sleepLeftMs > 0 && !paused) {
                sleepLeftMs -= 500
                if (sleepLeftMs <= 0) { sleepLeftMs = 0; v.pause(); say("توقف التشغيل بمؤقت النوم") }
            }
            val remaining = dur - pos
            if (sleepAtEnd && dur > 0 && remaining in 1..1_500) { sleepAtEnd = false; v.pause(); save(); say("توقف التشغيل بمؤقت النوم"); continue }
            if (next != null && !nextDismissed && !sleepAtEnd && dur > 0 && (remaining in 1..15_000 || v.ended)) {
                if (countdown < 0) countdown = 10
                if (tick % 2 == 0) countdown -= 1
                if (countdown <= 0) { playNext(); break }
            } else if (v.ended) {
                save(); Nav.back(); break
            }
        }
    }

    DisposableEffect(Unit) { onDispose { save(push = true); view?.release() } }
    LaunchedEffect(Unit) {
        delay(300)
        if (runCatching { playFocus.requestFocus() }.isFailure) runCatching { rootFocus.requestFocus() }
    }

    val v = view
    val tracks = remember(tracksVersion, v) { v?.tracks().orEmpty() }
    val currentSub = tracks.firstOrNull { it.type == "sub" && it.selected }
    val currentAudio = tracks.firstOrNull { it.type == "audio" && it.selected }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown) lastInput = System.currentTimeMillis(); false }
            .focusRequester(rootFocus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val vv = view ?: return@onKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE -> { vv.togglePause(); true }
                    AndroidKey.KEYCODE_MEDIA_PLAY -> { if (vv.paused) vv.togglePause(); true }
                    AndroidKey.KEYCODE_MEDIA_PAUSE -> { vv.pause(); true }
                    AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> { vv.seekBy(30); true }
                    AndroidKey.KEYCODE_MEDIA_REWIND -> { vv.seekBy(-30); true }
                    AndroidKey.KEYCODE_CAPTIONS, AndroidKey.KEYCODE_PROG_RED -> { tracksVersion++; panel = Panel.Subs; controls = true; true }
                    // The remote's colour keys, as on the tablet's TV mode.
                    AndroidKey.KEYCODE_PROG_GREEN -> { tracksVersion++; panel = Panel.Audio; controls = true; true }
                    AndroidKey.KEYCODE_PROG_YELLOW -> { openSync(); true }
                    AndroidKey.KEYCODE_PROG_BLUE -> { panel = Panel.Speed; controls = true; true }
                    else -> if (controls || syncBar) false else when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_SPACE -> {
                            val part = skipParts.firstOrNull { pos in it.startMs until it.endMs - 1_000 && it.kind !in skipped }
                            when {
                                // A long press opens every tool at once.
                                e.nativeKeyEvent.repeatCount > 0 -> { expanded = true; showControls(moreFocus) }
                                part != null -> { vv.seekTo(part.endMs); skipped = skipped + part.kind }
                                else -> { vv.togglePause(); showControls() }
                            }
                            true
                        }
                        AndroidKey.KEYCODE_DPAD_RIGHT -> { vv.seekBy(10); showControls(seekFocus); true }
                        AndroidKey.KEYCODE_DPAD_LEFT -> { vv.seekBy(-10); showControls(seekFocus); true }
                        AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN, AndroidKey.KEYCODE_MENU -> { showControls(); true }
                        else -> false
                    }
                }
            }
            .focusable(),
    ) {
        androidx.compose.runtime.key(direct) {
            AndroidView(factory = { ctx -> MpvView(ctx, direct).also { it.start(); view = it } }, modifier = Modifier.fillMaxSize())
        }
        if (direct) DirectSubtitles(view)

        if (buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                T(if (dur == 0L) "جارٍ فتح المصدر…" else "جارٍ التحميل…", 20.sp,
                    modifier = Modifier.background(Color(0xB3070D14), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }

        if (advisoryOn && !controls && panel == Panel.None) advisory?.let { (r, lines) -> Advisory(lines, r) }

        if (controls && v != null && !syncBar) {
            val subLabel = currentSub?.let { it.label.substringBefore(" — ").substringBefore(" · ") } ?: "إيقاف"
            val audioLabel = currentAudio?.label?.substringBefore(" — ")?.substringBefore(" · ")
            val short = listOf(
                ToolItem("gauge-rise", "السرعة", "×" + if (speed == 1.0) "1" else speed.toString(), focus = openers.getValue(Panel.Speed)) { panel = Panel.Speed },
                ToolItem("subtitles-sparkle-transcribe", "الترجمات", subLabel, focus = openers.getValue(Panel.Subs)) { tracksVersion++; panel = Panel.Subs },
                ToolItem("audio-lines-meter", "الصوت", audioLabel, focus = openers.getValue(Panel.Audio)) { tracksVersion++; panel = Panel.Audio },
            )
            TabletControls(
                req = req, pos = pos, dur = dur, paused = paused, speed = speed,
                top = listOfNotNull(
                    ToolItem("replace-exchange", "تبديل المصدر") { switchSource() },
                    if (next != null) ToolItem("skip-forward-push", "الحلقة التالية") { playNext() } else null,
                    ToolItem("info-drop", "معلومات الملف", focus = openers.getValue(Panel.Info)) { panel = Panel.Info },
                ),
                short = short,
                all = short + playerTools(v),
                expanded = expanded, dim = panel != Panel.None,
                playFocus = playFocus, seekFocus = seekFocus, moreFocus = moreFocus,
                onBack = { save(); Nav.back() },
                onPlay = { v.togglePause() },
                onSeek = { v.seekBy(it) },
                onExpand = { expanded = it },
            )
        }

        val part = skipParts.firstOrNull { pos in it.startMs until it.endMs - 1_000 && it.kind !in skipped }
        if (part != null && panel == Panel.None && !syncBar && countdown < 0) {
            SkipButton(part, controls, skipFocus) { v?.seekTo(part.endMs); skipped = skipped + part.kind }
        }

        if (syncBar) SyncBar(heardMs, seenMs, lastShift, subDelay, onMark = { mark(it) })

        if (countdown >= 0 && next != null) NextUpCard(next!!, req.meta.background, countdown, onPlay = { playNext() }, onCancel = { nextDismissed = true; countdown = -1 })

        if (v != null) when (panel) {
            Panel.Subs -> SubsPanel(
                tracks, v, subScale, subDelay,
                onScale = { subScale = it; v.setSubScale(it); Store.setNumber("subScale", (it * 100).toInt()) },
                onDelay = { d -> subDelay = d; v.setSubDelay(d); Store.saveSubDelay(req.meta.id, d) },
                onSync = { openSync() },
                onStyle = { applySubStyle() },
                onPick = { tracksVersion++ },
            )
            Panel.Audio -> AudioPanel(
                tracks.filter { it.type == "audio" }, speed, dialogue, night, level,
                onTrack = { t -> v.select(t, "audio"); tracksVersion++ },
                onDialogue = { dialogue = !dialogue; applyFx() },
                onNight = { night = !night; applyFx() },
                onLevel = { level = !level; applyFx() },
                onSpeed = { speed = it; v.setSpeed(it) },
            )
            Panel.Speed -> {
                val speeds = listOf(0.75, 1.0, 1.25, 1.5)
                ChoicePanel("السرعة", speeds.map { (if (it == 1.0) "×1 (عادية)" else "×$it") to (kotlin.math.abs(it - speed) < 0.01) }) { i ->
                    speed = speeds[i]; v.setSpeed(speed)
                }
            }
            Panel.Info -> InfoPanel(v.info() + listOf("المصدر" to (req.stream.addonName + (req.stream.sizeLabel?.let { " · $it" } ?: ""))))
            Panel.Colors -> ChoicePanel("ألوان الصورة", COLOR_PRESETS.map { it.second to (it.first == colorKey) }) { i ->
                colorKey = COLOR_PRESETS[i].first; applyColors()
            }
            Panel.Sleep -> {
                val options = listOf(15, 30, 45, 60, -1, 0)
                ChoicePanel("مؤقت النوم", options.map { m ->
                    val label = when (m) { -1 -> "نهاية الحلقة"; 0 -> "إيقاف المؤقت"; else -> "$m دقيقة" }
                    val on = when (m) {
                        -1 -> sleepAtEnd
                        0 -> sleepLeftMs == 0L && !sleepAtEnd
                        else -> sleepLeftMs > 0 && (sleepLeftMs + 59_999) / 60_000 == m.toLong()
                    }
                    label to on
                }) { i ->
                    when (val m = options[i]) {
                        -1 -> { sleepAtEnd = true; sleepLeftMs = 0 }
                        0 -> { sleepAtEnd = false; sleepLeftMs = 0 }
                        else -> { sleepAtEnd = false; sleepLeftMs = m * 60_000L }
                    }
                    closePanel()
                }
            }
            Panel.Bookmarks -> {
                val list = remember(bookmarksVersion) { Store.bookmarks(req.videoId) }
                BookmarksPanel(list, onJump = { ms -> v.seekTo(ms); closePanel() }, onRemove = { ms -> Store.removeBookmark(req.videoId, ms); bookmarksVersion++ })
            }
            Panel.Companion -> Unit
            Panel.Who -> WhoPanel(req, v)
            Panel.Quiz -> QuizPanel(req, pos, dur, subLines.toList())
            Panel.Quotes -> {
                val list = remember(quotesVersion) { Store.quotes(req.videoId) }
                QuotesPanel(list) { ms -> v.seekTo(ms); closePanel() }
            }
            Panel.Tools -> Unit
            Panel.None -> Unit
        }

        toast?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                T(msg, 16.sp, modifier = Modifier.background(PanelBg, CircleShape).padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }
    }
}

// ---------------- Mawj tools («المزيد») ----------------

/** «سمعت · ظهرت»: a small bar at the top; the difference shows once both are pressed. */
@Composable
private fun SyncBar(heard: Long?, seen: Long?, lastShift: Double?, delay: Double, onMark: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(40); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.background(Color(0x80070D14), CircleShape).border(1.dp, Color(0x24FFFFFF), CircleShape).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyncChip("ear-sound-hear", "سمعت", heard, Modifier.focusRequester(first)) { onMark("heard") }
            Spacer(Modifier.width(5.dp))
            SyncChip("eye-blink", "ظهرت", seen) { onMark("seen") }
            Row(Modifier.padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                T("الفارق", 11.sp, color = C.Muted)
                Spacer(Modifier.width(6.dp))
                T(lastShift?.let { fmtDelay(it) } ?: fmtDelay(delay), 13.sp, color = C.GoldLight, weight = FontWeight.Bold, num = true)
            }
        }
    }
}

@Composable
private fun SyncChip(icon: String, label: String, at: Long?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val done = at != null
    Row(
        modifier.height(32.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onClick = onClick)
            .background(if (focused) C.GoldLight else Color(0x14FFFFFF))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (focused) C.Sea else if (done) C.Green else C.Ink
        MIcon(icon, ink, focused, Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        T(label, 13.sp, color = ink)
        Spacer(Modifier.width(6.dp))
        T(at?.let { timeMs(it) } ?: "—", 10.sp, color = if (focused) C.Sea else if (done) C.Green else C.Dim, num = true)
    }
}

// ---------------- panels ----------------

@Composable
internal fun PanelBox(title: String, width: Int = 300, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(PanelDim), contentAlignment = Alignment.TopStart) {
        Column(
            Modifier.width((width + 40).dp).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = 30.dp, end = 10.dp, top = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            T(title, 30.sp, weight = FontWeight.Bold, serif = true, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

/** One choice row; the current one is gold and receives the focus when the panel opens. */
@Composable
internal fun ChoiceRow(label: String, selected: Boolean, focus: FocusRequester?, count: Int? = null, sub: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(if (sub != null) 46.dp else 38.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.02f, onClick = onClick)
            .background(if (selected) C.GoldLight else if (focused) Color(0x24FFFFFF) else Color.Transparent)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(label, 14.sp, color = if (selected) C.Sea else C.Ink, maxLines = 1, weight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (sub != null) T(sub, 10.sp, color = if (selected) Color(0xFF4A3A1A) else C.Dim, maxLines = 1, num = true)
        }
        if (count != null) {
            T(count.toString(), 10.sp, num = true, color = C.Sea,
                modifier = Modifier.background(if (selected) Color(0x26070D14) else C.Gold, CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun ChoicePanel(title: String, items: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    val focus = remember(items.size) { List(items.size) { FocusRequester() } }
    val current = items.indexOfFirst { it.second }.coerceAtLeast(0)
    LaunchedEffect(Unit) { delay(40); runCatching { focus.getOrNull(current)?.requestFocus() } }
    PanelBox(title) {
        if (items.isEmpty()) T("لا توجد خيارات.", 14.sp, color = C.Muted)
        items.forEachIndexed { i, (label, sel) -> ChoiceRow(label, sel, focus[i]) { onPick(i) } }
    }
}

/**
 * Subtitles, as on the tablet: languages with their counts, that language's subtitles, then the
 * look (timing, size, bold, colour, edge, height, encoding). The focus opens on the chosen one.
 */
@Composable
private fun SubsPanel(
    tracks: List<Track>, v: MpvView, scale: Double, delay: Double,
    onScale: (Double) -> Unit, onDelay: (Double) -> Unit, onSync: () -> Unit, onStyle: () -> Unit, onPick: () -> Unit,
) {
    val subs = tracks.filter { it.type == "sub" }
    val langs = subs.groupBy { it.lang.lowercase().take(2).ifBlank { "?" } }
    val selected = subs.firstOrNull { it.selected }
    var lang by remember { mutableStateOf(selected?.lang?.lowercase()?.take(2)?.ifBlank { "?" } ?: langs.keys.firstOrNull()) }
    val inLang = langs[lang].orEmpty()
    val trackFocus = remember(inLang.size, lang) { List(inLang.size) { FocusRequester() } }
    val offFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40)
        val i = inLang.indexOfFirst { it.selected }
        runCatching { (if (i >= 0) trackFocus[i] else offFocus).requestFocus() }
    }
    var codepage by remember { mutableStateOf("auto") }
    var bold by remember { mutableStateOf(Store.flag("subBold")) }
    var color by remember { mutableStateOf(Store.text("subColor", "#FFFFFF")) }
    var edge by remember { mutableStateOf(Store.text("subEdge", "border")) }
    var margin by remember { mutableIntStateOf(Store.number("subMargin", 40)) }

    Box(Modifier.fillMaxSize().background(PanelDim)) {
        T("الترجمات", 30.sp, weight = FontWeight.Bold, serif = true, modifier = Modifier.padding(start = 30.dp, top = 24.dp))
        Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 74.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Languages.
            Column(Modifier.width(175.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                T("اللغات", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(bottom = 4.dp))
                ChoiceRow("لا شيء", selected == null, offFocus) { v.select(null, "sub"); onPick() }
                langs.forEach { (code, list) ->
                    ChoiceRow(if (code == "?") "أخرى" else Addons.languageName(code), code == lang, null, count = list.size) { lang = code }
                }
            }
            // That language's subtitles.
            Column(Modifier.width(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                T("الترجمات", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(bottom = 4.dp))
                inLang.forEachIndexed { i, t -> TrackCard(t, trackFocus[i]) { v.select(t, "sub"); onPick() } }
            }
            // The look.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("النمط", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(bottom = 4.dp))
                StyleRow("تأخير الترجمة") {
                    Stepper(fmtDelay(delay), onMinus = { onDelay(((delay - 0.1) * 100).toLong() / 100.0) }, onPlus = { onDelay(((delay + 0.1) * 100).toLong() / 100.0) })
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).height(32.dp)
                        .tvFocus(shape = CircleShape, scale = 1.03f, onClick = onSync)
                        .background(Color(0x1AD6B36A)).border(1.dp, Color(0x80D6B36A), CircleShape),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { T("المزامنة بالسمع · سمعت ظهرت", 13.sp, color = C.GoldLight) }
                StyleRow("حجم الخط") {
                    Stepper("${(scale * 100).toInt()}٪", onMinus = { onScale((scale - 0.1).coerceAtLeast(0.5)) }, onPlus = { onScale((scale + 0.1).coerceAtMost(2.0)) })
                }
                StyleRow("عريض") {
                    Chip("مفعّل", bold, onClick = { bold = true; Store.setFlag("subBold", true); onStyle() })
                    Chip("معطّل", !bold, onClick = { bold = false; Store.setFlag("subBold", false); onStyle() })
                }
                StyleRow("اللون") {
                    listOf("#FFFFFF", "#FFD21F", "#2BD9F0", "#FF6A6A", "#1EE38A", "#A066D8", "#FF7A1A").forEach { c ->
                        Box(
                            Modifier.size(22.dp)
                                .tvFocus(shape = CircleShape, scale = 1.15f, onClick = { color = c; Store.setText("subColor", c); onStyle() })
                                .border(2.dp, if (color == c) C.GoldLight else Color.Transparent, CircleShape)
                                .padding(3.dp).background(Color(android.graphics.Color.parseColor(c)), CircleShape),
                        )
                    }
                }
                StyleRow("الحدود والظل") {
                    listOf("border" to "حدود", "shadow" to "ظل", "none" to "بلا").forEach { (k, l) ->
                        Chip(l, edge == k, onClick = { edge = k; Store.setText("subEdge", k); onStyle() })
                    }
                }
                StyleRow("الارتفاع عن الأسفل") {
                    Stepper("$margin", onMinus = { margin = (margin - 10).coerceAtLeast(0); Store.setNumber("subMargin", margin); onStyle() },
                        onPlus = { margin = (margin + 10).coerceAtMost(200); Store.setNumber("subMargin", margin); onStyle() })
                }
                StyleRow("الترميز") {
                    listOf("auto" to "تلقائي", "utf8" to "UTF-8", "arabic" to "عربي").forEach { (k, label) ->
                        Chip(label, codepage == k, onClick = { codepage = k; v.setCodepage(k) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 13.sp, color = C.Muted, modifier = Modifier.weight(1f), maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("−", false, onClick = onMinus)
            T(value, 13.sp, num = true, modifier = Modifier.width(66.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            Chip("+", false, onClick = onPlus)
        }
    }
}

@Composable
private fun BookmarksPanel(list: List<Long>, onJump: (Long) -> Unit, onRemove: (Long) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(list.size) { delay(40); runCatching { first.requestFocus() } }
    PanelBox("العلامات") {
        if (list.isEmpty()) T("لا توجد علامات في هذه الحلقة. تُضاف من «علامة هنا».", 13.sp, color = C.Muted, lineHeight = 20.sp)
        list.forEachIndexed { i, ms ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { ChoiceRow(time(ms), false, if (i == 0) first else null) { onJump(ms) } }
                Chip("حذف", false, onClick = { onRemove(ms) })
            }
        }
    }
}

@Composable
private fun InfoPanel(rows: List<Pair<String, String>>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { first.requestFocus() } }
    PanelBox("معلومات الملف", width = 380) {
        rows.forEachIndexed { i, (k, value) ->
            Row(
                Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first).focusable() else Modifier)
                    .background(Color(0x0DF6F1E4), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) { T(k, 13.sp, color = C.Muted); T(value, 13.sp, maxLines = 1, num = true) }
        }
    }
}

private fun fmtDelay(d: Double): String = String.format(Locale.US, "%+.2f", d) + " ث"

private fun timeMs(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d.%d", (s % 3600) / 60, s % 60, (ms % 1000) / 100)
}

internal fun time(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}


/** Direct mode: mpv shows no subtitles itself, so the current line is drawn here, at the bottom. */
@Composable
private fun DirectSubtitles(v: MpvView?) {
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(v) {
        if (v == null) return@LaunchedEffect
        while (true) {
            // Read off the main thread: a busy player must never freeze the screen.
            line = withContext(kotlinx.coroutines.Dispatchers.IO) { v.subText() }?.trim()?.takeIf { it.isNotEmpty() }
            delay(200)
        }
    }
    val text = line ?: return
    val size = 25f * com.mawj.tv.data.Store.number("subScale", 100) / 100f
    Box(Modifier.fillMaxSize().padding(start = 80.dp, end = 80.dp, bottom = 46.dp), contentAlignment = Alignment.BottomCenter) {
        T(text, size.sp, weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center, shadow = true, lineHeight = (size * 1.45f).sp,
            modifier = Modifier.background(Color(0x59000000), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 4.dp))
    }
}


/** One subtitle in the tablet's style: its source in a small chip, the language, and a tick when chosen. */
@Composable
private fun TrackCard(t: Track, focus: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val ink = if (t.selected) C.Sea else C.Ink
    Row(
        Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.02f, onClick = onClick)
            .background(if (t.selected) C.GoldLight else if (focused) Color(0x24FFFFFF) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val src = t.title.takeIf { it.isNotBlank() && it != t.lang } ?: if (t.external) "خارجية" else "داخل الملف"
            T(src, 9.sp, color = ink, maxLines = 1, num = true,
                modifier = Modifier.background(if (t.selected) Color(0x1A000000) else Color(0x1FFFFFFF), CircleShape).padding(horizontal = 8.dp, vertical = 1.dp))
            T(t.label.substringBefore(" — ").substringBefore(" · "), 14.sp, color = ink, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            T(Addons.languageName(t.lang.take(2)), 10.sp, color = if (t.selected) Color(0xFF4A3C1C) else Color(0xFFAAAAAA), maxLines = 1)
        }
        if (t.selected) T("✓", 17.sp, color = C.Sea, weight = FontWeight.Bold)
    }
}

/** «الصوت»: the audio tracks, then the sound tools and the speed, in the tablet's panel style. */
@Composable
private fun AudioPanel(
    tracks: List<Track>, speed: Double, dialogue: Boolean, night: Boolean, level: Boolean,
    onTrack: (Track) -> Unit, onDialogue: () -> Unit, onNight: () -> Unit, onLevel: () -> Unit, onSpeed: (Double) -> Unit,
) {
    val focus = remember(tracks.size) { List(tracks.size.coerceAtLeast(1)) { FocusRequester() } }
    LaunchedEffect(Unit) { delay(40); runCatching { focus[tracks.indexOfFirst { it.selected }.coerceAtLeast(0)].requestFocus() } }
    Box(Modifier.fillMaxSize().background(PanelDim)) {
        T("الصوت", 30.sp, weight = FontWeight.Bold, serif = true, modifier = Modifier.padding(start = 30.dp, top = 24.dp))
        Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 74.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                T("مسار الصوت", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(bottom = 4.dp))
                if (tracks.isEmpty()) T("لا توجد مسارات.", 13.sp, color = C.Muted)
                tracks.forEachIndexed { i, t -> ChoiceRow(t.label.substringBefore(" — "), t.selected, focus[i]) { onTrack(t) } }
            }
            Column(Modifier.width(270.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("تحسين الصوت", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(bottom = 4.dp))
                SoundSwitch("توضيح الحوار", "رفع صوت الكلام", dialogue, onDialogue)
                SoundSwitch("صوت ليلي", "تهدئة الأصوات العالية", night, onNight)
                SoundSwitch("موازنة المستوى", "صوت ثابت بين المشاهد", level, onLevel)
                T("السرعة", 12.sp, color = Color(0xFFCCCCCC), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.75, 1.0, 1.25, 1.5, 2.0).forEach { sp -> Chip("×" + if (sp == 1.0) "1" else sp.toString(), kotlin.math.abs(sp - speed) < 0.01, onClick = { onSpeed(sp) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundSwitch(title: String, note: String, on: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.02f, onClick = onClick)
            .background(if (focused) Color(0x24FFFFFF) else Color(0x0FFFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 14.sp, weight = FontWeight.SemiBold)
            T(note, 10.sp, color = Color(0xFFAAAAAA))
        }
        Box(
            Modifier.size(36.dp, 20.dp).background(if (on) C.Gold else Color(0x33FFFFFF), CircleShape).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterStart else Alignment.CenterEnd,
        ) { Box(Modifier.size(14.dp).background(if (on) C.Sea else Color(0xFFBBBBBB), CircleShape)) }
    }
}

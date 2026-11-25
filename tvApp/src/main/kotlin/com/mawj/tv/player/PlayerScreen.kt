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
            if (controls && panel == Panel.None && !paused && System.currentTimeMillis() - lastInput > 5_000) {
                controls = false
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
                    AndroidKey.KEYCODE_CAPTIONS -> { panel = Panel.Subs; controls = true; true }
                    else -> if (controls || syncBar) false else when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_SPACE -> { vv.togglePause(); showControls(); true }
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

        if (controls && v != null && !syncBar) {
            Controls(
                req = req, pos = pos, dur = dur, paused = paused, speed = speed,
                subLabel = currentSub?.let { it.label.substringBefore(" — ").substringBefore(" · ") } ?: "إيقاف",
                audioLabel = currentAudio?.label?.substringBefore(" — ")?.substringBefore(" · ") ?: "—",
                hasNext = next != null, delay = subDelay, dim = panel != Panel.None,
                playFocus = playFocus, seekFocus = seekFocus, moreFocus = moreFocus, openers = openers,
                onPlay = { v.togglePause() },
                onSeek = { v.seekBy(it) },
                onOpen = { p -> tracksVersion++; panel = p },
                onSync = { openSync() },
                onNext = { playNext() },
            )
        }

        if (syncBar) SyncBar(heardMs, seenMs, lastShift, subDelay, onMark = { mark(it) })

        if (countdown >= 0 && next != null) NextUp(next!!, countdown, onPlay = { playNext() }, onCancel = { nextDismissed = true; countdown = -1 })

        if (v != null) when (panel) {
            Panel.Subs -> SubsPanel(
                tracks, v, subScale, subDelay,
                onScale = { subScale = it; v.setSubScale(it); Store.setNumber("subScale", (it * 100).toInt()) },
                onDelay = { d -> subDelay = d; v.setSubDelay(d); Store.saveSubDelay(req.meta.id, d) },
                onSync = { openSync() },
                onStyle = { applySubStyle() },
                onPick = { tracksVersion++ },
            )
            Panel.Audio -> ChoicePanel("الصوت", tracks.filter { it.type == "audio" }.map { it.label to it.selected }) { i ->
                v.select(tracks.filter { it.type == "audio" }[i], "audio"); tracksVersion++
            }
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
            Panel.Companion -> CompanionPanel(req, pos, dur, subLines.toList())
            Panel.Who -> WhoPanel(req, v)
            Panel.Quiz -> QuizPanel(req, pos, dur, subLines.toList())
            Panel.Quotes -> {
                val list = remember(quotesVersion) { Store.quotes(req.videoId) }
                QuotesPanel(list) { ms -> v.seekTo(ms); closePanel() }
            }
            Panel.Tools -> ToolsPanel(
                dialogue = dialogue, night = night, level = level,
                colorLabel = COLOR_PRESETS.firstOrNull { it.first == colorKey }?.second ?: "الأصلي",
                loopState = when { loopA == null -> null; loopB == null -> "البداية محددة"; else -> "يعمل" },
                sleepLabel = when { sleepAtEnd -> "نهاية الحلقة"; sleepLeftMs > 0 -> "${(sleepLeftMs + 59_999) / 60_000} د"; else -> null },
                onColors = { panel = Panel.Colors },
                onDialogue = { dialogue = !dialogue; applyFx() },
                onNight = { night = !night; applyFx() },
                onLevel = { level = !level; applyFx() },
                onEncoding = { panel = Panel.Subs },
                onSync = { openSync() },
                onMark = { Store.addBookmark(req.videoId, v.positionMs); bookmarksVersion++; say("حُفظت علامة عند ${time(v.positionMs)}") },
                onBookmarks = { panel = Panel.Bookmarks },
                onLoop = {
                    when {
                        loopA == null -> { loopA = v.positionMs; say("حُددت بداية المقطع، ويُضغط مرة أخرى عند نهايته") }
                        loopB == null -> {
                            val b = v.positionMs
                            if (b > loopA!! + 1_000) { loopB = b; v.setAbLoop(loopA, loopB); v.seekTo(loopA!!); say("يتكرر المقطع الآن") }
                        }
                        else -> { loopA = null; loopB = null; v.setAbLoop(null, null); say("أُلغي تكرار المقطع") }
                    }
                },
                onSleep = { panel = Panel.Sleep },
                gemini = com.mawj.tv.data.Gemini.enabled,
                translateLabel = translatePct?.let { "$it٪" },
                onTranslate = {
                    if (translatePct == null) {
                        val src = addonSubs.filterNot { it.lang.lowercase().startsWith("ar") }
                            .sortedBy { if (it.lang.lowercase().startsWith("en")) 0 else 1 }.firstOrNull()
                        if (src == null) say("لا توجد ترجمة بلغة أخرى لهذه الحلقة") else scope.launch {
                            translatePct = 0
                            val r = com.mawj.tv.data.SubTranslate.translate(src) { p -> translatePct = p }
                            translatePct = null
                            when (r) {
                                is com.mawj.tv.data.SubTranslate.Result.Ready -> {
                                    v.addSubtitle(r.path, "عربي ✨ ترجمة موج", "ar", select = true); tracksVersion++
                                    say("الترجمة العربية جاهزة ✅")
                                }
                                com.mawj.tv.data.SubTranslate.Result.AlreadyArabic -> say("هذه الترجمة عربية ولا تحتاج ترجمة")
                                com.mawj.tv.data.SubTranslate.Result.Unreadable -> say("تعذّرت قراءة ملف الترجمة")
                                com.mawj.tv.data.SubTranslate.Result.Failed -> say("تعذّرت الترجمة الآن")
                            }
                        }
                    }
                },
                onCompanion = { v.pause(); panel = Panel.Companion },
                onWho = { v.pause(); panel = Panel.Who },
                onQuiz = { v.pause(); panel = Panel.Quiz },
                onQuote = {
                    val line = v.subText()
                    if (line == null) say("لا توجد عبارة على الشاشة الآن")
                    else { Store.addQuote(req.videoId, v.positionMs, line); quotesVersion++; say("حُفظت العبارة") }
                },
                onQuotes = { panel = Panel.Quotes },
                onShot = {
                    scope.launch {
                        val ok = saveShot(v, req.meta.name, v.positionMs)
                        say(if (ok) "حُفظت اللقطة، وتُفتح من الجوال عبر «الربط بالجوال»" else "تعذّر التقاط هذا المشهد")
                    }
                },
                onInfo = { panel = Panel.Info },
                onSwitch = { switchSource() },
                direct = direct,
            )
            Panel.None -> Unit
        }

        toast?.let { msg ->
            Box(Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                T(msg, 16.sp, modifier = Modifier.background(PanelBg, CircleShape).padding(horizontal = 20.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun Controls(
    req: PlayRequest, pos: Long, dur: Long, paused: Boolean, speed: Double,
    subLabel: String, audioLabel: String, hasNext: Boolean, delay: Double, dim: Boolean,
    playFocus: FocusRequester, seekFocus: FocusRequester, moreFocus: FocusRequester, openers: Map<Panel, FocusRequester>,
    onPlay: () -> Unit, onSeek: (Int) -> Unit, onOpen: (Panel) -> Unit, onSync: () -> Unit, onNext: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0xCC000000), 1f to Color.Transparent))
                .padding(horizontal = 40.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                T(req.meta.name, 26.sp, weight = FontWeight.Bold, serif = true, maxLines = 1)
                val sub = req.episode?.let { "الموسم ${it.season} · الحلقة ${it.number}" + it.title.takeIf { t -> t.isNotBlank() }?.let { t -> " — $t" }.orEmpty() }
                    ?: req.meta.year?.take(4)
                sub?.let { T(it, 14.sp, color = C.Muted, maxLines = 1) }
            }
            if (dur > 0) {
                val ends = System.currentTimeMillis() + ((dur - pos) / speed).toLong()
                T("ينتهي " + java.text.SimpleDateFormat("h:mm", Locale.US).format(java.util.Date(ends)), 15.sp, color = C.Muted)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6000000)))
                .padding(start = 36.dp, end = 36.dp, top = 40.dp, bottom = 22.dp),
        ) {
            SeekBar(pos, dur, seekFocus, onSeek)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayButton(paused, Modifier.focusRequester(playFocus), onPlay)
                CtrlButton("subtitles-sparkle-transcribe", "الترجمة", subLabel, Modifier.focusRequester(openers.getValue(Panel.Subs))) { onOpen(Panel.Subs) }
                CtrlButton("audio-lines-meter", "الصوت", audioLabel, Modifier.focusRequester(openers.getValue(Panel.Audio))) { onOpen(Panel.Audio) }
                CtrlButton("clock-spin", "ضبط التوقيت", if (delay != 0.0) fmtDelay(delay) else null) { onSync() }
                CtrlButton("gauge-rise", "السرعة", "×" + if (speed == 1.0) "1" else speed.toString(), Modifier.focusRequester(openers.getValue(Panel.Speed))) { onOpen(Panel.Speed) }
                if (hasNext) CtrlButton("skip-forward-push", "الحلقة التالية", null) { onNext() }
                CtrlButton("more-horizontal-wave", "المزيد", null, Modifier.focusRequester(moreFocus), accent = true) { onOpen(Panel.Tools) }
            }
        }
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
    }
}

@Composable
private fun PlayButton(paused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.08f, onClick = onClick)
            .background(if (focused) C.GoldLight else C.Ink),
        contentAlignment = Alignment.Center,
    ) {
        MIcon("play-pause-toggle", C.Sea, focused, Modifier.size(20.dp))
    }
}

/** A named control with its animated icon and what is chosen in it. */
@Composable
private fun CtrlButton(icon: String, label: String, value: String?, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.05f, onClick = onClick)
            .background(if (focused) Color(0x33F1DDA6) else if (accent) Color(0x2ED6B36A) else Color(0x1FFFFFFF))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(icon, if (accent || focused) C.GoldLight else C.Ink, focused, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        T(label, 13.sp, color = if (accent) C.GoldLight else C.Ink, maxLines = 1)
        if (value != null) {
            Spacer(Modifier.width(5.dp))
            T(value, 11.sp, color = C.Muted, maxLines = 1)
        }
    }
}

/** The time line. Focused, left/right jump 10 seconds. Video time always reads left to right. */
@Composable
private fun SeekBar(pos: Long, dur: Long, focus: FocusRequester, onSeek: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key.nativeKeyCode) {
                        AndroidKey.KEYCODE_DPAD_RIGHT -> { onSeek(10); true }
                        AndroidKey.KEYCODE_DPAD_LEFT -> { onSeek(-10); true }
                        else -> false
                    }
                }
                .focusable(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(time(pos), 13.sp, num = true)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(if (focused) 7.dp else 4.dp).background(Color(0x2EFFFFFF), RoundedCornerShape(4.dp))) {
                val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (focused) C.GoldLight else C.Gold, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.width(12.dp))
            T(time(dur), 13.sp, color = C.Muted, num = true)
        }
    }
}

// ---------------- Mawj tools («المزيد») ----------------

@Composable
private fun ToolsPanel(
    dialogue: Boolean, night: Boolean, level: Boolean, colorLabel: String, loopState: String?, sleepLabel: String?,
    onColors: () -> Unit, onDialogue: () -> Unit, onNight: () -> Unit, onLevel: () -> Unit,
    onEncoding: () -> Unit, onSync: () -> Unit,
    onMark: () -> Unit, onBookmarks: () -> Unit, onLoop: () -> Unit,
    onSleep: () -> Unit, onInfo: () -> Unit, onSwitch: () -> Unit,
    gemini: Boolean, translateLabel: String?, onTranslate: () -> Unit,
    onCompanion: () -> Unit, onWho: () -> Unit, onQuiz: () -> Unit,
    onQuote: () -> Unit, onQuotes: () -> Unit, onShot: () -> Unit,
    direct: Boolean = false,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().background(PanelBg, RoundedCornerShape(16.dp)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            T("أدوات موج", 18.sp, weight = FontWeight.Bold)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolGroup("الصورة والصوت", Modifier.weight(1f)) {
                    if (!direct) Tool("color-filter-sparkle-split", "ألوان الصورة", colorLabel.takeIf { it != "الأصلي" }, Modifier.focusRequester(first), onClick = onColors)
                    Tool("user-voice-speak", "توضيح الحوار", if (dialogue) "مفعّل" else null, if (direct) Modifier.focusRequester(first) else Modifier, on = dialogue, onClick = onDialogue)
                    Tool("moon-star-twinkle", "صوت ليلي", if (night) "مفعّل" else null, on = night, onClick = onNight)
                    Tool("waveform-scroll", "تحسين الصوت", if (level) "مفعّل" else null, on = level, onClick = onLevel)
                }
                ToolGroup("الترجمة", Modifier.weight(1f)) {
                    if (gemini) Tool("speech-to-text-transcribe", "ترجمة للعربي", translateLabel, on = translateLabel != null, onClick = onTranslate)
                    Tool("code-block-type", "ترميز الترجمة", null, onClick = onEncoding)
                    Tool("ear-sound-hear", "سمعت · ظهرت", null, onClick = onSync)
                }
                ToolGroup("اللحظة", Modifier.weight(1f)) {
                    Tool("bookmark-drop", "علامة هنا", null, onClick = onMark)
                    Tool("bookmarks-cycle", "العلامات", null, onClick = onBookmarks)
                    Tool("message-text-type", "حفظ العبارة", null, onClick = onQuote)
                    Tool("messages-reply", "العبارات", null, onClick = onQuotes)
                    if (!direct) Tool("camera-snap", "لقطة شاشة", null, onClick = onShot)
                    Tool("repeat-chase", "تكرار مقطع", loopState, on = loopState != null, onClick = onLoop)
                }
                if (gemini) ToolGroup("رفيق موج", Modifier.weight(1f)) {
                    Tool("bots-chat", "رفيق موج", null, onClick = onCompanion)
                    if (!direct) Tool("eye-sparkle-focus", "من في المشهد؟", null, onClick = onWho)
                    Tool("help-circle-tilt", "اختبار الحلقة", null, onClick = onQuiz)
                }
                ToolGroup("التشغيل", Modifier.weight(1f)) {
                    Tool("zzz-float", "مؤقت النوم", sleepLabel, on = sleepLabel != null, onClick = onSleep)
                    Tool("info-drop", "معلومات الملف", null, onClick = onInfo)
                    Tool("replace-exchange", "تبديل المصدر", null, onClick = onSwitch)
                }
            }
        }
    }
}

@Composable
private fun ToolGroup(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        T(title, 11.sp, color = C.Gold, modifier = Modifier.padding(bottom = 2.dp))
        content()
    }
}

@Composable
private fun Tool(icon: String, label: String, value: String?, modifier: Modifier = Modifier, on: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(36.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(9.dp), scale = 1.03f, onClick = onClick)
            .background(if (focused) Color(0x29F1DDA6) else Color(0x0DFFFFFF))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(icon, C.GoldLight, focused, Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        T(label, 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) T(value, 10.sp, color = if (on) C.Green else C.Muted, maxLines = 1)
    }
}

/** «سمعت · ظهرت»: a small bar at the top; the difference shows once both are pressed. */
@Composable
private fun SyncBar(heard: Long?, seen: Long?, lastShift: Double?, delay: Double, onMark: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(40); runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.background(PanelBg, CircleShape).border(1.dp, Color(0x14FFFFFF), CircleShape).padding(5.dp),
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
            .background(if (focused) Color(0x24F1DDA6) else Color.Transparent)
            .border(1.dp, if (focused) C.GoldLight else Color.Transparent, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(icon, if (done) C.Green else C.Ink, focused, Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        T(label, 13.sp, color = if (done) C.Green else C.Ink)
        Spacer(Modifier.width(6.dp))
        T(at?.let { timeMs(it) } ?: "—", 10.sp, color = if (done) C.Green else C.Dim, num = true)
    }
}

// ---------------- panels ----------------

@Composable
internal fun PanelBox(title: String, width: Int = 300, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier.width(width.dp).fillMaxHeight().background(PanelBg).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            T(title, 24.sp, weight = FontWeight.Bold, serif = true)
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
            .background(if (selected) Color(0xE6D6B36A) else if (focused) Color(0x29F1DDA6) else Color(0x0DF6F1E4))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(label, 14.sp, color = if (selected) C.Sea else C.Ink, maxLines = 1, weight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (sub != null) T(sub, 10.sp, color = if (selected) Color(0xFF4A3A1A) else C.Dim, maxLines = 1, num = true)
        }
        if (count != null) {
            T(count.toString(), 10.sp, num = true, color = if (selected) C.Sea else C.GoldLight,
                modifier = Modifier.background(if (selected) Color(0x26070D14) else Color(0x47D6B36A), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
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

    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x73060A0E), 0.45f to Color(0xD1060A0E), 1f to Color(0xDB060A0E)))) {
        Row(Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Languages.
            Column(Modifier.width(175.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                T("الترجمات", 26.sp, weight = FontWeight.Bold, serif = true)
                T("اللغات", 11.sp, color = C.Dim, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                ChoiceRow("لا شيء", selected == null, offFocus) { v.select(null, "sub"); onPick() }
                langs.forEach { (code, list) ->
                    ChoiceRow(if (code == "?") "أخرى" else Addons.languageName(code), code == lang, null, count = list.size) { lang = code }
                }
            }
            // That language's subtitles.
            Column(Modifier.width(235.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                T("الترجمات", 11.sp, color = C.Dim, modifier = Modifier.padding(top = 44.dp, bottom = 2.dp))
                inLang.forEachIndexed { i, t ->
                    ChoiceRow(
                        t.label.substringBefore(" — ").substringBefore(" · "), t.selected, trackFocus[i],
                        sub = (t.title.takeIf { it.isNotBlank() && it != t.lang } ?: if (t.external) "خارجية" else "داخل الملف"),
                    ) { v.select(t, "sub"); onPick() }
                }
            }
            // The look.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("النمط", 11.sp, color = C.Dim, modifier = Modifier.padding(top = 44.dp, bottom = 2.dp))
                StyleRow("تأخير الترجمة") {
                    Stepper(fmtDelay(delay), onMinus = { onDelay(((delay - 0.1) * 100).toLong() / 100.0) }, onPlus = { onDelay(((delay + 0.1) * 100).toLong() / 100.0) })
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).height(32.dp)
                        .tvFocus(shape = CircleShape, scale = 1.03f, onClick = onSync)
                        .background(Color(0x1AD6B36A)).border(1.dp, Color(0x80D6B36A), CircleShape),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { T("سمعت · ظهرت", 13.sp, color = C.GoldLight) }
                StyleRow("حجم الخط") {
                    Stepper("${(scale * 100).toInt()}٪", onMinus = { onScale((scale - 0.1).coerceAtLeast(0.5)) }, onPlus = { onScale((scale + 0.1).coerceAtMost(2.0)) })
                }
                StyleRow("عريض") {
                    Chip("مفعّل", bold, onClick = { bold = true; Store.setFlag("subBold", true); onStyle() })
                    Chip("معطّل", !bold, onClick = { bold = false; Store.setFlag("subBold", false); onStyle() })
                }
                StyleRow("اللون") {
                    listOf("#FFFFFF", "#F5D03B", "#3CC7F0", "#3CCF7A").forEach { c ->
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

@Composable
private fun NextUp(ep: Episode, seconds: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(start = 40.dp, bottom = 130.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.width(320.dp).background(PanelBg, RoundedCornerShape(16.dp)).border(1.dp, Color(0x59D6B36A), RoundedCornerShape(16.dp)).padding(16.dp),
        ) {
            T("الحلقة التالية خلال ${seconds.coerceAtLeast(0)}", 15.sp, color = C.Gold, weight = FontWeight.SemiBold)
            T("${ep.number}. ${ep.title}", 17.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            T("بنفس المصدر والإضافة", 13.sp, color = C.Dim, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("تشغيل الآن", false, onClick = onPlay, modifier = Modifier.focusRequester(focus))
                Chip("إلغاء", false, onClick = onCancel)
            }
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

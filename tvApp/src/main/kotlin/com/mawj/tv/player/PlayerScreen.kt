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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.mawj.tv.ui.T
import com.mawj.tv.ui.tvFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Panel { None, Subs, Audio, Speed, Info, Sync }

/** Picks the stream for the next episode: same release first, then same addon and quality. */
suspend fun nextEpisodeStream(type: String, next: Episode, current: Stream): Stream? {
    val list = Addons.streams(type, next.id)
    return current.bingeGroup?.let { bg -> list.firstOrNull { it.bingeGroup == bg } }
        ?: list.firstOrNull { it.addonName == current.addonName && it.quality == current.quality }
        ?: list.firstOrNull { it.addonName == current.addonName }
}

/**
 * The player. Hidden controls: OK pauses, left/right jump 10 s, up/down show the controls.
 * Shown controls: a row of buttons (subtitles, audio, sync, speed, next, info); each opens its
 * panel with the focus on the current choice. Back closes a panel, then the controls, then leaves.
 */
@Composable
fun PlayerScreen(req: PlayRequest) {
    var view by remember { mutableStateOf<MpvView?>(null) }
    var pos by remember { mutableLongStateOf(req.startMs) }
    var dur by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.None) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var subDelay by remember { mutableDoubleStateOf(Store.subDelay(req.meta.id)) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var next by remember { mutableStateOf<Episode?>(null) }
    var countdown by remember { mutableIntStateOf(-1) }
    var nextDismissed by remember { mutableStateOf(false) }
    var nextBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val openers = remember { Panel.entries.associateWith { FocusRequester() } }

    fun save() {
        val v = view ?: return
        val d = v.durationMs
        if (d <= 0) return
        Store.saveProgress(
            Progress(
                meta = req.meta, videoId = req.videoId,
                season = req.episode?.season ?: 0, episode = req.episode?.number ?: 0, episodeTitle = req.episode?.title,
                positionMs = v.positionMs, durationMs = d, updatedAt = System.currentTimeMillis(),
            ),
        )
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

    fun showControls(focus: FocusRequester = playFocus) {
        lastInput = System.currentTimeMillis()
        controls = true
        scope.launch { delay(30); runCatching { focus.requestFocus() } }
    }

    fun closePanel() {
        val opener = openers[panel]
        panel = Panel.None
        scope.launch { delay(30); runCatching { (opener ?: playFocus).requestFocus() } }
    }

    BackHandler {
        when {
            panel != Panel.None -> closePanel()
            countdown >= 0 -> { nextDismissed = true; countdown = -1 }
            controls -> { controls = false; runCatching { rootFocus.requestFocus() } }
            else -> { save(); Nav.back() }
        }
    }

    // Load, the saved subtitle delay, and the addons' subtitles (Arabic first).
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        v.load(req.stream.url, req.stream.headers, req.startMs)
        if (subDelay != 0.0) v.setSubDelay(subDelay)
        val subs = Addons.subtitles(req.meta.type, req.videoId)
        var waited = 0
        while (v.durationMs <= 0 && waited < 40) { delay(250); waited++ }
        val hasArabic = v.tracks().any { it.type == "sub" && it.lang.startsWith("ar") }
        subs.filter { it.lang.startsWith("ar") }.take(5).forEachIndexed { i, s ->
            v.addSubtitle(s.url, "${s.label} ${i + 1}", s.lang, select = !hasArabic && i == 0)
        }
        subs.filter { it.lang.startsWith("en") }.take(2).forEach { v.addSubtitle(it.url, it.label, it.lang, select = false) }
        tracksVersion++
    }

    // The next episode, for the button and the countdown.
    LaunchedEffect(req.videoId) {
        val ep = req.episode ?: return@LaunchedEffect
        val eps = Addons.meta(req.meta.type, req.meta.id)?.videos.orEmpty().filter { it.season > 0 }
        val i = eps.indexOfFirst { it.id == ep.id }
        next = if (i >= 0) eps.getOrNull(i + 1) else null
    }

    // A light poll twice a second.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(500)
            pos = v.positionMs
            dur = v.durationMs
            paused = v.paused
            buffering = v.buffering || dur == 0L
            if (controls && panel == Panel.None && !paused && System.currentTimeMillis() - lastInput > 5_000) {
                controls = false
                runCatching { rootFocus.requestFocus() }
            }
            if (++tick % 30 == 0) save()
            val remaining = dur - pos
            if (next != null && !nextDismissed && dur > 0 && (remaining in 1..15_000 || v.ended)) {
                if (countdown < 0) countdown = 10
                if (tick % 2 == 0) countdown -= 1
                if (countdown <= 0) { playNext(); break }
            } else if (v.ended) {
                save(); Nav.back(); break
            }
        }
    }

    DisposableEffect(Unit) { onDispose { save(); view?.release() } }
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
                    else -> if (controls) false else when (e.key.nativeKeyCode) {
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
        AndroidView(factory = { ctx -> MpvView(ctx).also { it.start(); view = it } }, modifier = Modifier.fillMaxSize())

        if (buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                T(if (dur == 0L) "جارٍ فتح المصدر…" else "جارٍ التحميل…", 20.sp,
                    modifier = Modifier.background(Color(0xB3070D14), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }

        if (controls && v != null) {
            Controls(
                req = req, pos = pos, dur = dur, paused = paused,
                subLabel = currentSub?.label?.substringBefore(" — ") ?: "إيقاف",
                audioLabel = currentAudio?.label?.substringBefore(" — ") ?: "—",
                speed = speed, hasNext = next != null, delay = subDelay,
                playFocus = playFocus, seekFocus = seekFocus, openers = openers,
                onPlay = { v.togglePause() },
                onSeek = { v.seekBy(it) },
                onOpen = { p -> tracksVersion++; panel = p },
                onNext = { playNext() },
            )
        }

        if (countdown >= 0 && next != null) NextUp(next!!, countdown, onPlay = { playNext() }, onCancel = { nextDismissed = true; countdown = -1 })

        if (v != null) when (panel) {
            Panel.Subs -> SubsPanel(tracks, v, onPick = { tracksVersion++ })
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
            Panel.Sync -> SyncPanel(
                delay = subDelay, positionMs = { v.positionMs },
                onDelay = { d -> subDelay = d; v.setSubDelay(d) },
                onSave = { Store.saveSubDelay(req.meta.id, subDelay) },
            )
            Panel.None -> Unit
        }
    }
}

@Composable
private fun Controls(
    req: PlayRequest, pos: Long, dur: Long, paused: Boolean,
    subLabel: String, audioLabel: String, speed: Double, hasNext: Boolean, delay: Double,
    playFocus: FocusRequester, seekFocus: FocusRequester, openers: Map<Panel, FocusRequester>,
    onPlay: () -> Unit, onSeek: (Int) -> Unit, onOpen: (Panel) -> Unit, onNext: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0xCC070D14), 1f to Color.Transparent))
                .padding(horizontal = 40.dp, vertical = 24.dp),
        ) {
            T(req.meta.name, 26.sp, weight = FontWeight.Bold, maxLines = 1)
            req.episode?.let { T("الموسم ${it.season} · الحلقة ${it.number} — ${it.title}", 16.sp, color = C.Muted, maxLines = 1) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xF0070D14)))
                .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 26.dp),
        ) {
            SeekBar(pos, dur, seekFocus, onSeek)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(if (paused) "▶ تشغيل" else "⏸ إيقاف", false, onClick = onPlay, modifier = Modifier.focusRequester(playFocus))
                Chip("الترجمة · $subLabel", false, onClick = { onOpen(Panel.Subs) }, modifier = Modifier.focusRequester(openers.getValue(Panel.Subs)))
                Chip("الصوت · $audioLabel", false, onClick = { onOpen(Panel.Audio) }, modifier = Modifier.focusRequester(openers.getValue(Panel.Audio)))
                Chip("ضبط التوقيت" + if (delay != 0.0) " · " + fmtDelay(delay) else "", false, onClick = { onOpen(Panel.Sync) }, modifier = Modifier.focusRequester(openers.getValue(Panel.Sync)))
                Chip("السرعة · ×${if (speed == 1.0) "1" else speed.toString()}", false, onClick = { onOpen(Panel.Speed) }, modifier = Modifier.focusRequester(openers.getValue(Panel.Speed)))
                if (hasNext) Chip("الحلقة التالية", false, onClick = onNext)
                Chip("معلومات الملف", false, onClick = { onOpen(Panel.Info) }, modifier = Modifier.focusRequester(openers.getValue(Panel.Info)))
            }
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
            T(time(pos), 15.sp)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).height(if (focused) 10.dp else 6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(5.dp))) {
                val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(if (focused) C.GoldLight else C.Gold, RoundedCornerShape(5.dp)))
            }
            Spacer(Modifier.width(14.dp))
            T(time(dur), 15.sp, color = C.Muted)
        }
    }
}

// ---------------- panels ----------------

@Composable
private fun PanelBox(title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Column(
            Modifier.width(440.dp).fillMaxHeight().background(Color(0xF2070D14)).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(title, 21.sp, weight = FontWeight.Bold, color = C.GoldLight)
            content()
        }
    }
}

/** One choice row; the current one has a tick and receives the focus when the panel opens. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.02f, onClick = onClick)
            .background(if (focused) C.Ink else Color(0x0DF6F1E4))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        T(label, 16.sp, color = if (focused) C.Sea else if (selected) C.GoldLight else C.Ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (selected) T("✓", 17.sp, color = if (focused) C.Sea else C.Gold, weight = FontWeight.Bold)
    }
}

@Composable
private fun ChoicePanel(title: String, items: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    val focus = remember(items.size) { List(items.size) { FocusRequester() } }
    val current = items.indexOfFirst { it.second }.coerceAtLeast(0)
    LaunchedEffect(Unit) { delay(40); runCatching { focus.getOrNull(current)?.requestFocus() } }
    PanelBox(title) {
        if (items.isEmpty()) T("لا توجد خيارات.", 15.sp, color = C.Muted)
        items.forEachIndexed { i, (label, sel) -> ChoiceRow(label, sel, focus[i]) { onPick(i) } }
    }
}

@Composable
private fun SubsPanel(tracks: List<Track>, v: MpvView, onPick: () -> Unit) {
    val subs = tracks.filter { it.type == "sub" }
    val offFocus = remember { FocusRequester() }
    val focus = remember(subs.size) { List(subs.size) { FocusRequester() } }
    var scale by remember { mutableDoubleStateOf(1.0) }
    var codepage by remember { mutableStateOf("auto") }
    LaunchedEffect(Unit) {
        delay(40)
        val i = subs.indexOfFirst { it.selected }
        runCatching { (if (i >= 0) focus[i] else offFocus).requestFocus() }
    }
    PanelBox("الترجمة") {
        ChoiceRow("إيقاف الترجمة", subs.none { it.selected }, offFocus) { v.select(null, "sub"); onPick() }
        subs.forEachIndexed { i, t -> ChoiceRow(t.label, t.selected, focus[i]) { v.select(t, "sub"); onPick() } }
        Spacer(Modifier.height(6.dp))
        T("الحجم", 15.sp, color = C.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("أصغر", false, onClick = { scale = (scale - 0.1).coerceAtLeast(0.5); v.setSubScale(scale) })
            T("${(scale * 100).toInt()}٪", 15.sp)
            Chip("أكبر", false, onClick = { scale = (scale + 0.1).coerceAtMost(2.0); v.setSubScale(scale) })
        }
        Spacer(Modifier.height(6.dp))
        T("الترميز (عند ظهور رموز غريبة فقط)", 15.sp, color = C.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "تلقائي", "utf8" to "UTF-8", "arabic" to "عربي قديم").forEach { (k, label) ->
                Chip(label, codepage == k, onClick = { codepage = k; v.setCodepage(k) })
            }
        }
    }
}

@Composable
private fun InfoPanel(rows: List<Pair<String, String>>) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { first.requestFocus() } }
    PanelBox("معلومات الملف") {
        rows.forEachIndexed { i, (k, value) ->
            Row(
                Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first).focusable() else Modifier)
                    .background(Color(0x0DF6F1E4), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) { T(k, 15.sp, color = C.Muted); T(value, 15.sp, maxLines = 1) }
        }
    }
}

/**
 * «سمعت» and «رأيت»: one press when the line is heard, one when its subtitle shows (either order).
 * Both are stamped with the video's own time, so the difference is exact; it is added to the delay.
 */
@Composable
private fun SyncPanel(delay: Double, positionMs: () -> Long, onDelay: (Double) -> Unit, onSave: () -> Unit) {
    var heard by remember { mutableStateOf<Long?>(null) }
    var seen by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("بانتظار الضغطتين") }
    var saved by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(40); runCatching { first.requestFocus() } }
    LaunchedEffect(heard, seen) {
        val h = heard; val s = seen
        if (h != null && s != null) {
            val shift = (h - s) / 1000.0
            onDelay(((delay + shift) * 100).toLong() / 100.0)
            note = if (shift < 0) "كانت الترجمة متأخرة، فقُدِّمت" else "كانت الترجمة متقدمة، فأُخِّرت"
            saved = false
            kotlinx.coroutines.delay(1500)
            heard = null; seen = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().background(Color(0xF5070D14)).padding(horizontal = 60.dp, vertical = 22.dp)) {
            T("ضبط توقيت الترجمة", 22.sp, weight = FontWeight.Bold, color = C.GoldLight)
            T("يُضغط «سمعت» لحظة سماع جملة، و«رأيت» لحظة ظهور ترجمتها (بأي ترتيب). يُحسب الفرق ويُضبط تلقائياً.", 15.sp, color = C.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SyncButton("سمعت 👂", "لحظة سماع الجملة", heard != null, Modifier.weight(1f).focusRequester(first)) { heard = positionMs() }
                SyncButton("رأيت 👁️", "لحظة ظهور ترجمتها", seen != null, Modifier.weight(1f)) { seen = positionMs() }
                Column(
                    Modifier.width(250.dp).background(Color(0x1AD6B36A), RoundedCornerShape(20.dp)).border(1.dp, Color(0x66D6B36A), RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    T("التأخير الحالي", 14.sp, color = C.Muted)
                    T(fmtDelay(delay), 30.sp, weight = FontWeight.Bold, color = C.GoldLight)
                    T(note, 13.sp, color = C.Dim)
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("− ٠٫١", false, onClick = { onDelay(((delay - 0.1) * 100).toLong() / 100.0); saved = false })
                Chip("+ ٠٫١", false, onClick = { onDelay(((delay + 0.1) * 100).toLong() / 100.0); saved = false })
                Chip("إعادة إلى صفر", false, onClick = { onDelay(0.0); saved = false })
                Chip(if (saved) "حُفظ لهذا العمل ✓" else "حفظ لهذا العمل", saved, onClick = { onSave(); saved = true })
            }
        }
    }
}

@Composable
private fun SyncButton(title: String, hint: String, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.height(120.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(20.dp), scale = 1.03f, onClick = onClick)
            .background(if (focused) C.Ink else C.Raised)
            .border(1.dp, if (done) C.Green else Color(0x1AEEF2F6), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        T(title, 26.sp, weight = FontWeight.Bold, color = if (focused) C.Sea else C.Ink)
        T(hint, 14.sp, color = if (focused) C.Sea else C.Muted)
        if (done) T("✓ سُجِّلت", 14.sp, color = if (focused) C.Sea else C.Green)
    }
}

@Composable
private fun NextUp(ep: Episode, seconds: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(start = 40.dp, bottom = 130.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.width(320.dp).background(Color(0xF20D1620), RoundedCornerShape(16.dp)).border(1.dp, Color(0x59D6B36A), RoundedCornerShape(16.dp)).padding(16.dp),
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

private fun time(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

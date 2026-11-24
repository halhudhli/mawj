package com.mawj.tv.player

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mawj.tv.Nav
import com.mawj.tv.data.Addons
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Progress
import com.mawj.tv.data.Store
import com.mawj.tv.ui.C
import com.mawj.tv.ui.Chip
import com.mawj.tv.ui.T
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The player. OK plays/pauses, left/right jump 10 seconds, up opens the sound and subtitles panel,
 * back closes the panel or leaves (the position is saved for «أكمل المشاهدة»).
 */
@Composable
fun PlayerScreen(req: PlayRequest) {
    var view by remember { mutableStateOf<MpvView?>(null) }
    var pos by remember { mutableLongStateOf(req.startMs) }
    var dur by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val rootFocus = remember { FocusRequester() }

    fun save() {
        val v = view ?: return
        val p = v.positionMs
        val d = v.durationMs
        if (d <= 0) return
        Store.saveProgress(
            Progress(
                meta = req.meta, videoId = req.videoId,
                season = req.episode?.season ?: 0, episode = req.episode?.number ?: 0, episodeTitle = req.episode?.title,
                positionMs = p, durationMs = d, updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun poke() { lastInput = System.currentTimeMillis(); controls = true }

    BackHandler {
        if (panel) { panel = false; runCatching { rootFocus.requestFocus() } }
        else { save(); Nav.back() }
    }

    // Subtitles from the addons, Arabic chosen first unless the file already has Arabic.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        v.load(req.stream.url, req.stream.headers, req.startMs)
        val subs = Addons.subtitles(req.meta.type, req.videoId)
        repeat(40) { if (v.durationMs > 0) return@repeat; delay(250) }
        val hasArabic = v.tracks().any { it.type == "sub" && it.lang.startsWith("ar") }
        val arabic = subs.filter { it.lang.startsWith("ar") }
        arabic.take(5).forEachIndexed { i, s -> v.addSubtitle(s.url, "${s.label} ${i + 1}", s.lang, select = !hasArabic && i == 0) }
        subs.filter { it.lang.startsWith("en") }.take(2).forEach { v.addSubtitle(it.url, it.label, it.lang, select = false) }
    }

    // A light poll: position, pause and buffering twice a second; save every 15 seconds.
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(500)
            pos = v.positionMs
            dur = v.durationMs
            paused = v.paused
            buffering = v.buffering || dur == 0L
            if (controls && !panel && !paused && System.currentTimeMillis() - lastInput > 4_000) controls = false
            if (++tick % 30 == 0) save()
            if (v.ended) { save(); Nav.back(); break }
        }
    }

    DisposableEffect(Unit) { onDispose { save(); view?.release() } }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onKeyEvent { e ->
                if (panel || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val v = view ?: return@onKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKey.KEYCODE_SPACE -> {
                        if (!controls) poke() else { v.togglePause(); poke() }; true
                    }
                    AndroidKey.KEYCODE_MEDIA_PLAY -> { if (v.paused) v.togglePause(); poke(); true }
                    AndroidKey.KEYCODE_MEDIA_PAUSE -> { v.pause(); poke(); true }
                    AndroidKey.KEYCODE_DPAD_RIGHT, AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> { v.seekBy(10); poke(); true }
                    AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_MEDIA_REWIND -> { v.seekBy(-10); poke(); true }
                    AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_MENU, AndroidKey.KEYCODE_CAPTIONS -> { panel = true; controls = true; true }
                    AndroidKey.KEYCODE_DPAD_DOWN -> { poke(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx -> MpvView(ctx).also { it.start(); view = it } },
            modifier = Modifier.fillMaxSize(),
        )
        if (buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                T(if (dur == 0L) "جارٍ فتح المصدر…" else "جارٍ التحميل…", 20.sp, color = C.Ink,
                    modifier = Modifier.background(Color(0xB3070D14), RoundedCornerShape(999.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }
        if (controls) Controls(req, pos, dur, paused)
        if (panel) view?.let { TracksPanel(it) { panel = false; runCatching { rootFocus.requestFocus() } } }
    }
}

@Composable
private fun Controls(req: PlayRequest, pos: Long, dur: Long, paused: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0xCC070D14), 1f to Color.Transparent))
                .padding(horizontal = 40.dp, vertical = 26.dp),
        ) {
            T(req.meta.name, 26.sp, weight = FontWeight.Bold, maxLines = 1)
            req.episode?.let { T("الموسم ${it.season} · الحلقة ${it.number} — ${it.title}", 16.sp, color = C.Muted, maxLines = 1) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6070D14)))
                .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 30.dp),
        ) {
            // Video time always reads left to right.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(time(pos), 15.sp, color = C.Ink)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f).height(6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(3.dp))) {
                        val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(f).height(6.dp).background(C.Gold, RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.width(14.dp))
                    T(time(dur), 15.sp, color = C.Muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            T(
                (if (paused) "⏸ متوقف · " else "") + "OK إيقاف/تشغيل · ◀ ▶ عشر ثوانٍ · ▲ الصوت والترجمة",
                13.sp, color = C.Dim,
            )
        }
    }
}

@Composable
private fun TracksPanel(v: MpvView, onClose: () -> Unit) {
    var tracks by remember { mutableStateOf(v.tracks()) }
    var delayS by remember { mutableStateOf(v.subDelay) }
    var scale by remember { mutableStateOf(1.0) }
    var speed by remember { mutableStateOf(v.speed) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun refresh() { tracks = v.tracks() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        LazyColumn(
            Modifier.width(420.dp).fillMaxHeight().background(Color(0xF2070D14)),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { T("الترجمة", 20.sp, weight = FontWeight.Bold, color = C.GoldLight) }
            item { Chip("إيقاف الترجمة", tracks.none { it.type == "sub" && it.selected }, onClick = { v.select(null, "sub"); refresh() }, modifier = Modifier.focusRequester(first)) }
            items(tracks.filter { it.type == "sub" }, key = { "s${it.id}" }) { t ->
                Chip(t.label, t.selected, onClick = { v.select(t, "sub"); refresh() })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("− ٠٫٢٥", false, onClick = { delayS -= 0.25; v.setSubDelay(delayS) })
                    T("التوقيت ${String.format(Locale.US, "%+.2f", delayS)} ث", 14.sp, color = C.Muted)
                    Chip("+ ٠٫٢٥", false, onClick = { delayS += 0.25; v.setSubDelay(delayS) })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("أصغر", false, onClick = { scale = (scale - 0.1).coerceAtLeast(0.5); v.setSubScale(scale) })
                    T("الحجم ${(scale * 100).toInt()}٪", 14.sp, color = C.Muted)
                    Chip("أكبر", false, onClick = { scale = (scale + 0.1).coerceAtMost(2.0); v.setSubScale(scale) })
                }
            }
            item { Spacer(Modifier.height(8.dp)); T("الصوت", 20.sp, weight = FontWeight.Bold, color = C.GoldLight) }
            items(tracks.filter { it.type == "audio" }, key = { "a${it.id}" }) { t ->
                Chip(t.label, t.selected, onClick = { v.select(t, "audio"); refresh() })
            }
            item { Spacer(Modifier.height(8.dp)); T("السرعة", 20.sp, weight = FontWeight.Bold, color = C.GoldLight) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75, 1.0, 1.25, 1.5).forEach { s ->
                        Chip("${s}×", kotlin.math.abs(speed - s) < 0.01, onClick = { speed = s; v.setSpeed(s) })
                    }
                }
            }
            item { Spacer(Modifier.height(6.dp)); Chip("إغلاق", false, onClick = onClose) }
        }
    }
}

private fun time(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

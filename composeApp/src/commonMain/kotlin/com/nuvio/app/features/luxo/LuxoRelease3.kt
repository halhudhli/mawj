package com.nuvio.app.features.luxo

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal fun luxoNowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** «🕶️ الوضع الخاص»: while on, playback progress, watched marks and scrobbles are not recorded. */
object LuxoPrivateMode {
    var active by mutableStateOf(LuxoPrefsStorage.load("private_mode_v1") == "1")
        private set

    fun toggle() {
        active = !active
        LuxoPrefsStorage.save("private_mode_v1", if (active) "1" else "0")
    }
}

/** Idle tracking for the screensaver. */
object LuxoIdle {
    var lastInputMs: Long = luxoNowMs()
    var playerOpen by mutableStateOf(false)
    /** Luxo windows (dialogs, videos) that are open right now; the screensaver waits while any is. */
    var busy by mutableIntStateOf(0)
    var showNow by mutableStateOf(false)
    var delayMinutes by mutableIntStateOf(LuxoPrefsStorage.load("screensaver_delay")?.toIntOrNull() ?: 5)
        private set

    fun setDelay(m: Int) {
        delayMinutes = m
        LuxoPrefsStorage.save("screensaver_delay", m.toString())
    }

    fun touch() {
        lastInputMs = luxoNowMs()
    }
}

/** Marks a Luxo window as open while it's on screen (touches inside dialogs don't reach the app's root). */
@Composable
fun LuxoIdleBusy() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        LuxoIdle.busy++
        onDispose { LuxoIdle.busy = (LuxoIdle.busy - 1).coerceAtLeast(0) }
    }
}

/** Notes every touch and key press (without consuming them). */
fun luxoIdleWatcher(): Modifier = Modifier
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                LuxoIdle.touch()
            }
        }
    }
    .onPreviewKeyEvent { LuxoIdle.touch(); false }

@Composable
internal fun LuxoScreensaverHost() {
    val features = rememberLuxoFeatures()
    LaunchedEffect(features.screensaver) {
        if (!features.screensaver) return@LaunchedEffect
        while (true) {
            delay(15_000)
            val idle = luxoNowMs() - LuxoIdle.lastInputMs
            val overlayOpen = LuxoOverlays.franchise != null || LuxoOverlays.traktDoctor || LuxoOverlays.guessGame
            if (!LuxoIdle.playerOpen && LuxoIdle.busy == 0 && !overlayOpen && !LuxoIdle.showNow && idle > LuxoIdle.delayMinutes * 60_000L) LuxoIdle.showNow = true
        }
    }
    if (LuxoIdle.showNow) LuxoScreensaver(onExit = { LuxoIdle.touch(); LuxoIdle.showNow = false })
}

private data class SaverSlide(val image: String, val title: String)

@Composable
private fun LuxoScreensaver(onExit: () -> Unit) {
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    var slides by remember { mutableStateOf<List<SaverSlide>>(emptyList()) }
    LaunchedEffect(Unit) {
        val own = LuxoTasteFacts.facts.values.mapNotNull { f -> f.backdrop?.let { SaverSlide("https://image.tmdb.org/t/p/w1280$it", f.title) } }.shuffled().take(40)
        slides = own.ifEmpty {
            val res = LuxoTmdbLite.get("trending/all/week", mapOf("language" to "ar"))
            res?.a("results").orEmpty().mapNotNull { r ->
                r.s("backdrop_path")?.let { SaverSlide("https://image.tmdb.org/t/p/w1280$it", r.s("title") ?: r.s("name") ?: "") }
            }
        }
        if (own.isEmpty() && watched.items.isNotEmpty()) LuxoTasteFacts.ensure(watched.items, limit = 40)
    }
    var index by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(slides) {
        while (slides.isNotEmpty()) {
            delay(9_000)
            index = (index + 1) % slides.size
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val t = kotlin.time.Instant.fromEpochMilliseconds(luxoNowMs()).toLocalDateTime(TimeZone.currentSystemDefault())
            clock = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
            delay(10_000)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val drift = rememberInfiniteTransition(label = "saver")
    val zoom by drift.animateFloat(1f, 1.12f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { onExit(); true }
                .focusRequester(focus)
                .focusable()
                .clickable(interactionSource = null, indication = null, onClick = onExit),
        ) {
            val slide = slides.getOrNull(index)
            Crossfade(targetState = slide, animationSpec = tween(1600), label = "slide") { s ->
                if (s != null) {
                    AsyncImage(
                        model = s.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(40.dp)) {
                Text(clock, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Black)
                slide?.let { Text(it.title, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp) }
            }
            Text("موج", color = Color.White.copy(alpha = 0.5f), fontSize = 22.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(32.dp))
        }
    }
}

// ---- «🎯 خمّن العمل» ----

private data class GuessRound(val image: String, val answer: String, val choices: List<String>)

@Composable
internal fun LuxoGuessGameDialog(onDismiss: () -> Unit) {
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    LaunchedEffect(watched.items.size) { if (watched.items.isNotEmpty()) LuxoTasteFacts.ensure(watched.items) }
    val pool = LuxoTasteFacts.facts.values.filter { it.backdrop != null }.distinctBy { it.title }
    var round by remember { mutableStateOf<GuessRound?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var played by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(LuxoPrefsStorage.load("guess_best")?.toIntOrNull() ?: 0) }
    fun next() {
        if (pool.size < 4) return
        val pick = pool.random()
        val others = pool.filter { it.title != pick.title }.shuffled().take(3).map { it.title }
        round = GuessRound("https://image.tmdb.org/t/p/w1280${pick.backdrop}", pick.title, (others + pick.title).shuffled())
        picked = null
    }
    LaunchedEffect(pool.size >= 4) { if (round == null) next() }
    val blur = if (picked == null) 18.dp else 0.dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LuxoIdleBusy()
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF14121C)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 خمّن العمل", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("النتيجة $score/$played · الأفضل $best", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            }
            val r = round
            if (r == null) {
                Text(
                    if (LuxoTasteFacts.loading) "يتم تجهيز اللقطات من سجل المشاهدة…" else "تلزم مشاهدة 4 أعمال على الأقل لبدء اللعبة",
                    color = Color.White.copy(alpha = 0.7f),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                    // Zoomed in as well as blurred: blur isn't drawn on older Android TVs.
                    val z = if (picked == null) 2.3f else 1f
                    AsyncImage(r.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = z; scaleY = z }.blur(blur))
                }
                r.choices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            val color = when {
                                picked == null -> Color.White.copy(alpha = 0.08f)
                                c == r.answer -> Color(0xFF2E7D5B)
                                c == picked -> Color(0xFF8E2C3A)
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                    .clickable(enabled = picked == null) {
                                        picked = c
                                        played++
                                        if (c == r.answer) score++
                                        if (score > best) { best = score; LuxoPrefsStorage.save("guess_best", best.toString()) }
                                    }
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(c, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2) }
                        }
                    }
                }
                if (picked != null) {
                    Text(if (picked == r.answer) "✔️ إجابة صحيحة" else "✖️ الإجابة: ${r.answer}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (round != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF6F8FBF), Color(0xFF9DB1CF))))
                            .clickable { next() }.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) { Text(if (picked == null) "تخطٍّ" else "التالي", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onDismiss).padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text("إغلاق", color = Color.White)
                }
            }
        }
    }
}

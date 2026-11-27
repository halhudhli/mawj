package com.mawj.tv.player

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.data.Episode
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.SkipPart
import com.mawj.tv.ui.C
import com.mawj.tv.ui.Img
import com.mawj.tv.ui.MIcon
import com.mawj.tv.ui.T
import com.mawj.tv.ui.tvFocus
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** One tool of the player bar: an animated icon, its name (shown above it on focus), and what it does. */
internal class ToolItem(
    val icon: String,
    val label: String,
    val value: String? = null,
    val on: Boolean = false,
    val focus: FocusRequester? = null,
    val onClick: () -> Unit,
)

private val Glass = Color(0x8C0A0A0C)
private val Edge = Color(0x42FFFFFF)

/**
 * The tablet's player, drawn for the TV: back and a few buttons at the top left, the three circles in
 * the middle, the title on the right, «ينتهي» and the time line, the tools pill at the bottom left
 * (it opens to every tool) and the time pill at the bottom right. Positions follow the tablet exactly.
 */
@Composable
internal fun TabletControls(
    req: PlayRequest,
    pos: Long, dur: Long, paused: Boolean, speed: Double,
    top: List<ToolItem>, short: List<ToolItem>, all: List<ToolItem>,
    expanded: Boolean, dim: Boolean,
    playFocus: FocusRequester, seekFocus: FocusRequester, moreFocus: FocusRequester,
    onBack: () -> Unit, onPlay: () -> Unit, onSeek: (Int) -> Unit, onExpand: (Boolean) -> Unit,
) {
    var bubble by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val setBubble: (String, Offset?) -> Unit = { label, at -> bubble = if (at == null) bubble?.takeIf { it.first != label } else label to at }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            // A soft veil at the top and the bottom only, so the picture stays clear.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8C000000), 0.22f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xC7000000))))

            // Top left: back, then a pill of buttons.
            Row(Modifier.padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    Modifier.size(43.dp).tvFocus(shape = CircleShape, scale = 1.08f, onClick = onBack).background(Glass).border(1.dp, Edge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { MIcon("arrow-right-go", Color.White, false, Modifier.size(21.dp).graphicsFlip()) }
                ToolPill(top, null, setBubble)
            }

            // The three circles in the middle.
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(75.dp), verticalAlignment = Alignment.CenterVertically) {
                Circle(58.dp, onClick = { onSeek(-10) }) { Ten(back = true) }
                Circle(64.dp, Modifier.focusRequester(playFocus), onClick = onPlay) { PlayGlyph(paused) }
                Circle(58.dp, onClick = { onSeek(10) }) { Ten(back = false) }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 13.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (dur > 0) {
                        val ends = System.currentTimeMillis() + ((dur - pos) / speed).toLong()
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ends }
                        val h = cal.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
                        val ampm = if (cal.get(java.util.Calendar.AM_PM) == 0) "ص" else "م"
                        T("ينتهي %d:%02d %s".format(Locale.US, h, cal.get(java.util.Calendar.MINUTE), ampm), 13.sp, color = Color(0xFFE6E6E6), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(horizontalAlignment = Alignment.Start) {
                            T(req.meta.name, 31.sp, weight = FontWeight.Bold, serif = true, maxLines = 1, shadow = true)
                            val sub = req.episode?.let { "الموسم ${it.season} · الحلقة ${it.number}" + it.title.takeIf { t -> t.isNotBlank() }?.let { t -> " — $t" }.orEmpty() }
                                ?: req.meta.year?.take(4)
                            sub?.let { T(it, 13.sp, color = Color(0xFFBBBBBB), weight = FontWeight.SemiBold, maxLines = 1, num = req.episode == null) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TimeLine(pos, dur, seekFocus, onSeek)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolPill(
                        if (expanded) all else short,
                        ToolItem(if (expanded) "chevron-right-walk" else "chevron-left-swim", if (expanded) "أقل" else "كل الأدوات", focus = moreFocus) { onExpand(!expanded) },
                        setBubble, Modifier.weight(1f, fill = false),
                    )
                    if (!expanded) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.height(43.dp).background(Glass, CircleShape).border(1.dp, Edge, CircleShape).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            T("${time(pos)} / ${time(dur)}", 14.sp, num = true)
                        }
                    }
                }
            }
            if (dim) Box(Modifier.fillMaxSize().background(Color(0x66000000)))

            // The focused tool's name, in a small bubble just above it (the TV has no touch to discover icons).
            bubble?.let { (text, at) ->
                Box(
                    Modifier.layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(c.maxWidth, c.maxHeight) { p.place((at.x - p.width / 2f).roundToInt().coerceAtLeast(8), (at.y - p.height - 8.dp.toPx()).roundToInt()) }
                    },
                ) {
                    T(text, 12.sp, color = C.GoldLight, maxLines = 1,
                        modifier = Modifier.background(Color(0xE60A0A0C), RoundedCornerShape(8.dp)).border(1.dp, C.Gold, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
        }
    }
}

private fun Modifier.graphicsFlip(): Modifier = this.then(Modifier.layout { m, c ->
    val p = m.measure(c)
    layout(p.width, p.height) { p.placeWithLayer(0, 0) { scaleX = -1f } }
})

@Composable
private fun ToolPill(items: List<ToolItem>, tail: ToolItem?, setBubble: (String, Offset?) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(43.dp).background(Glass, CircleShape).border(1.dp, Edge, CircleShape).padding(horizontal = 5.dp), contentAlignment = Alignment.CenterStart) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(items, key = { _, t -> t.label }) { _, t -> ToolButton(t, setBubble) }
            if (tail != null) item(key = "tail") { ToolButton(tail, setBubble) }
        }
    }
}

@Composable
private fun ToolButton(t: ToolItem, setBubble: (String, Offset?) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focused, center) { if (focused) setBubble(t.label + (t.value?.let { " · $it" } ?: ""), center) else setBubble(t.label + (t.value?.let { " · $it" } ?: ""), null) }
    Box(
        Modifier.size(33.dp)
            .then(if (t.focus != null) Modifier.focusRequester(t.focus) else Modifier)
            .onGloballyPositioned { center = it.boundsInRoot().topCenter }
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.08f, onClick = t.onClick)
            .background(if (focused) C.GoldLight else if (t.on) Color(0x40D6B36A) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        MIcon(t.icon, if (focused) C.Sea else if (t.on) C.GoldLight else Color.White, focused, Modifier.size(20.dp))
    }
}

@Composable
private fun Circle(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(size).onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = CircleShape, scale = 1.08f, onClick = onClick)
            .background(if (focused) Color(0xB3141416) else Color(0x9E0E0E10))
            .border(1.dp, if (focused) Color.Transparent else Edge, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The tablet's ±10 seconds icon: a round arrow with «10» inside. */
@Composable
private fun Ten(back: Boolean) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            scale(if (back) 1f else -1f, 1f) {
                val w = size.width
                drawArc(Color.White, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.14f, w * 0.18f), size = Size(w * 0.72f, w * 0.72f), style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
                val p = Path().apply { moveTo(w * 0.5f, w * 0.02f); lineTo(w * 0.3f, w * 0.18f); lineTo(w * 0.5f, w * 0.34f); close() }
                drawPath(p, Color.White)
            }
        }
        T("10", 9.sp, weight = FontWeight.Bold, num = true, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PlayGlyph(paused: Boolean) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        if (paused) {
            val p = Path().apply { moveTo(w * 0.24f, w * 0.12f); lineTo(w * 0.88f, w * 0.5f); lineTo(w * 0.24f, w * 0.88f); close() }
            drawPath(p, Color.White)
        } else {
            drawRoundRect(Color.White, Offset(w * 0.2f, w * 0.12f), Size(w * 0.2f, w * 0.76f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
            drawRoundRect(Color.White, Offset(w * 0.6f, w * 0.12f), Size(w * 0.2f, w * 0.76f), androidx.compose.ui.geometry.CornerRadius(w * 0.06f))
        }
    }
}

/** The time line: thin and gold; focused, it grows, shows a knob and the time, and left/right move 10 seconds. */
@Composable
private fun TimeLine(pos: Long, dur: Long, focus: FocusRequester, onSeek: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(22.dp)
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val fast = e.nativeKeyEvent.repeatCount > 3
                when (e.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_RIGHT -> { onSeek(if (fast) 30 else 10); true }
                    AndroidKey.KEYCODE_DPAD_LEFT -> { onSeek(if (fast) -30 else -10); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(if (focused) 7.dp else 4.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(Color(0x2EFFFFFF), cornerRadius = r)
            drawRoundRect(C.Gold, size = Size(size.width * f, size.height), cornerRadius = r)
            if (focused) drawCircle(C.GoldLight, size.height * 1.25f, Offset(size.width * f, size.height / 2))
        }
        if (focused) {
            Box(
                Modifier.fillMaxWidth().layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0))
                    layout(c.maxWidth, 0) { p.place((c.maxWidth * f - p.width / 2f).roundToInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), -p.height - 6.dp.roundToPx()) }
                },
            ) {
                Column(
                    Modifier.background(Color(0xE60A0A0C), RoundedCornerShape(8.dp)).border(1.dp, C.Gold, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    T(time(pos), 17.sp, color = C.GoldLight, weight = FontWeight.Bold, num = true)
                    T("◀ ▶ عشر ثوانٍ · مطوّل للأسرع", 9.sp, color = Color(0xFFBBBBBB))
                }
            }
        }
    }
}

/** «دليل المحتوى»: the age rating and the kinds of scenes, top right, for a few seconds at the start. */
@Composable
internal fun Advisory(lines: List<String>, rating: String?) {
    if (rating == null && lines.isEmpty()) return
    Box(Modifier.fillMaxSize().padding(top = 17.dp, end = 22.dp), contentAlignment = Alignment.TopEnd) {
        Row {
            Column(horizontalAlignment = Alignment.End) {
                rating?.let { T(it, 15.sp, color = C.GoldLight, weight = FontWeight.Bold, num = true, shadow = true) }
                lines.forEach { T(it, 12.sp, color = Color(0xFFDDDDDD), shadow = true) }
                T("دليل المحتوى", 9.sp, color = Color(0xFF888888))
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(2.dp).height((22 + lines.size * 17).dp).background(C.Gold))
        }
    }
}

/** «تخطّي المقدمة»: bottom left while the part plays; OK skips even with the buttons hidden. */
@Composable
internal fun SkipButton(part: SkipPart, controls: Boolean, focus: FocusRequester, onSkip: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(start = 22.dp, bottom = if (controls) 120.dp else 34.dp), contentAlignment = Alignment.BottomStart) {
        Row(
            Modifier.height(42.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                .tvFocus(shape = CircleShape, scale = 1.06f, onClick = onSkip)
                .background(if (focused) C.GoldLight else Color(0xB30A0A0C)).border(1.dp, C.Gold, CircleShape)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MIcon("skip-forward-push", if (focused) C.Sea else C.GoldLight, focused, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            T(part.label + if (!controls) " · موافق" else "", 14.sp, color = if (focused) C.Sea else C.GoldLight, weight = FontWeight.SemiBold)
        }
    }
}

/** The next episode: a card at the bottom right with its picture and the countdown; «شغّلها الآن» has the focus. */
@Composable
internal fun NextUpCard(ep: Episode, fallbackImage: String?, seconds: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(40); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().padding(end = 22.dp, bottom = 30.dp), contentAlignment = Alignment.BottomEnd) {
        Row(
            Modifier.width(380.dp).background(Color(0xD6070D14), RoundedCornerShape(14.dp)).border(1.dp, Color(0x59F1DDA6), RoundedCornerShape(14.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(125.dp, 70.dp).background(C.Card, RoundedCornerShape(8.dp))) { Img(ep.thumbnail ?: fallbackImage, 125.dp, 70.dp, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("الحلقة التالية خلال ", 11.sp, color = Color(0xFFBBBBBB))
                    T("${seconds.coerceAtLeast(0)}", 13.sp, color = C.GoldLight, weight = FontWeight.Bold, num = true)
                }
                T("الحلقة ${ep.number}" + ep.title.takeIf { it.isNotBlank() }?.let { " · «$it»" }.orEmpty(), 14.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(vertical = 3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NextBtn("▶ شغّلها الآن", true, Modifier.focusRequester(focus), onPlay)
                    NextBtn("إلغاء", false, Modifier, onCancel)
                }
            }
        }
    }
}

@Composable
private fun NextBtn(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.height(28.dp).onFocusChanged { focused = it.isFocused }.tvFocus(shape = RoundedCornerShape(9.dp), scale = 1.05f, onClick = onClick)
            .background(if (primary) Brush.verticalGradient(listOf(C.GoldLight, C.Gold)) else Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x1FFFFFFF))))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 12.sp, color = if (primary) C.Sea else C.Ink, weight = if (primary) FontWeight.Bold else FontWeight.Normal) }
}

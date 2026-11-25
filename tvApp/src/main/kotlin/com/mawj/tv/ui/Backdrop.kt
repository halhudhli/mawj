package com.mawj.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mawj.tv.Nav
import com.mawj.tv.Route
import kotlinx.coroutines.delay

/**
 * One background for every page: the artwork of what is in focus (or of the open title). It
 * changes only after the focus rests for 200 ms, so fast remote presses never load every
 * picture on the way, then fades once. Over it, a dark scrim keeps text sharp.
 */
object Backdrop {
    var target by mutableStateOf<String?>(null)
        private set

    fun show(url: String?) {
        if (!url.isNullOrBlank()) target = url
    }
}

@Composable
fun AppBackdrop(soft: Boolean = false, clear: Boolean = false) {
    var shown by remember { mutableStateOf<String?>(null) }
    val target = Backdrop.target
    LaunchedEffect(target) {
        if (shown != null) delay(200)
        shown = target
    }
    Box(Modifier.fillMaxSize().background(C.Sea)) {
        Crossfade(targetState = shown, animationSpec = tween(400), label = "backdrop") { url ->
            if (url != null) Img(url, 640.dp, 360.dp, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        // Dark towards the side where the text sits (the right, in Arabic) and towards the bottom.
        // The sources page keeps the artwork clear (its own scrim darkens only under the list).
        if (!soft && !clear) Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0x2E070D14), 0.55f to Color(0x9E070D14), 1f to Color(0xF0070D14)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                when {
                    // Home: the focused title's artwork stays fully clear; only the rows' strip at the bottom is shaded.
                    clear -> Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB3070D14))
                    soft -> Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0x99070D14))
                    else -> Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF5070D14))
                },
            ),
        )
    }
}

/** The side rail, as on the tablet: the profile picture, then icons only; the open page on a dark-gold tile with a gold mark at the edge. */
@Composable
fun Rail(current: Route) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().padding(top = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val initial = com.mawj.tv.data.Account.email?.firstOrNull()?.uppercase() ?: "م"
        var avFocus by remember { mutableStateOf(false) }
        Box(
            Modifier.size(38.dp).onFocusChanged { avFocus = it.isFocused }
                .tvFocus(shape = CircleShape, scale = 1.1f, onClick = { Nav.tab(Route.Profile) })
                .background(Brush.radialGradient(listOf(Color(0xFF7A6AA8), Color(0xFF2B2340))), CircleShape),
            contentAlignment = Alignment.Center,
        ) { T(initial, 16.sp, weight = FontWeight.Bold) }
        Spacer(Modifier.height(22.dp))
        val items = listOf(
            Triple("home-write", Route.Home, "الرئيسية"), Triple("clapperboard-clap", Route.Movies, "أفلام"),
            Triple("tv-channel", Route.Series, "مسلسلات"), Triple("sparkles-spark", Route.Islands, "الجزر"),
            Triple("bookmarks-cycle", Route.Lists, "قوائمي"), Triple("search-scan", Route.Search, "البحث"),
            Triple("library-lean", Route.Library, "المكتبة"),
        )
        items.forEach { (icon, r, _) ->
            RailItem(icon, current == r) { Nav.tab(r) }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** The calm sea of the tablet behind the inner pages: drawn once, never moving. */
@Composable
fun SeaBackground() {
    Canvas(Modifier.fillMaxSize().background(C.Sea)) {
        val w = size.width
        val h = size.height
        fun wave(y: Float, amp: Float, color: Color, line: Color?) {
            val p = Path().apply {
                moveTo(0f, y)
                cubicTo(w * 0.15f, y - amp, w * 0.3f, y + amp, w * 0.45f, y)
                cubicTo(w * 0.6f, y - amp, w * 0.78f, y + amp, w, y - amp * 0.3f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(p, color)
            if (line != null) {
                val l = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.15f, y - amp, w * 0.3f, y + amp, w * 0.45f, y)
                    cubicTo(w * 0.6f, y - amp, w * 0.78f, y + amp, w, y - amp * 0.3f)
                }
                drawPath(l, line, style = Stroke(width = 1.5f))
            }
        }
        wave(h * 0.40f, h * 0.035f, Color(0x8C14263A), Color(0x2ED6B36A))
        wave(h * 0.57f, h * 0.03f, Color(0x8C132539), null)
        wave(h * 0.74f, h * 0.03f, Color(0xB310202F), Color(0x1FD6B36A))
    }
}

enum class Icon { Search, Home, Library, Settings, Play }

@Composable
private fun RailItem(icon: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.width(92.dp).height(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(50.dp)
                .onFocusChanged { focused = it.isFocused }
                .tvFocus(shape = RoundedCornerShape(15.dp), scale = 1.06f, onClick = onClick)
                .background(if (selected) Color(0xFF2A2216) else if (focused) Color(0x22F1DDA6) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            MIcon(icon, if (selected) C.Gold else if (focused) C.GoldLight else Color(0xFFC9D0D7), focused, Modifier.size(26.dp))
        }
        // The mark at the screen edge (the right, in Arabic) beside the open page.
        if (selected) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(24.dp).background(C.Gold, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun DrawIcon(icon: Icon, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.09f)
        when (icon) {
            Icon.Search -> {
                drawCircle(color, radius = w * 0.3f, center = Offset(w * 0.44f, w * 0.44f), style = stroke)
                drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.9f, w * 0.9f), strokeWidth = w * 0.09f)
            }
            Icon.Home -> {
                val p = Path().apply {
                    moveTo(w * 0.12f, w * 0.48f); lineTo(w * 0.5f, w * 0.14f); lineTo(w * 0.88f, w * 0.48f)
                    lineTo(w * 0.88f, w * 0.88f); lineTo(w * 0.12f, w * 0.88f); close()
                }
                drawPath(p, color, style = stroke)
            }
            Icon.Library -> {
                for (i in 0..2) drawRect(color, Offset(w * (0.12f + i * 0.28f), w * 0.14f), androidx.compose.ui.geometry.Size(w * 0.18f, w * 0.72f), style = stroke)
            }
            Icon.Settings -> {
                drawCircle(color, radius = w * 0.34f, center = Offset(w / 2, w / 2), style = stroke)
                drawCircle(color, radius = w * 0.12f, center = Offset(w / 2, w / 2))
            }
            Icon.Play -> {
                val p = Path().apply { moveTo(w * 0.25f, w * 0.15f); lineTo(w * 0.85f, w * 0.5f); lineTo(w * 0.25f, w * 0.85f); close() }
                drawPath(p, color)
            }
        }
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

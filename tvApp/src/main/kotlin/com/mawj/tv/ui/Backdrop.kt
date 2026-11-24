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
fun AppBackdrop() {
    var shown by remember { mutableStateOf<String?>(null) }
    val target = Backdrop.target
    LaunchedEffect(target) {
        if (shown != null) delay(200)
        shown = target
    }
    Box(Modifier.fillMaxSize().background(C.Sea)) {
        Crossfade(targetState = shown, animationSpec = tween(400), label = "backdrop") { url ->
            if (url != null) Img(url, 960.dp, 540.dp, Modifier.fillMaxSize(), ContentScale.Crop)
        }
        // Dark towards the side where the text sits (the right, in Arabic) and towards the bottom.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0x2E070D14), 0.55f to Color(0x9E070D14), 1f to Color(0xF0070D14)),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xF5070D14)),
            ),
        )
    }
}

/** The side rail: search, home, library, settings. */
@Composable
fun Rail(current: Route) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().background(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color(0xD9070D14))).padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("موج", 26.sp, color = C.Gold, weight = FontWeight.Bold)
        Spacer(Modifier.height(26.dp))
        RailItem(Icon.Search, current == Route.Search) { Nav.tab(Route.Search) }
        Spacer(Modifier.height(12.dp))
        RailItem(Icon.Home, current == Route.Home) { Nav.tab(Route.Home) }
        Spacer(Modifier.height(12.dp))
        RailItem(Icon.Library, current == Route.Library) { Nav.tab(Route.Library) }
        Spacer(Modifier.height(12.dp))
        RailItem(Icon.Settings, current == Route.Settings) { Nav.tab(Route.Settings) }
    }
}

enum class Icon { Search, Home, Library, Settings, Play }

@Composable
private fun RailItem(icon: Icon, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(50.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocus(shape = RoundedCornerShape(14.dp), scale = 1.08f, onClick = onClick)
            .background(if (focused) C.Ink else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        DrawIcon(icon, if (focused) C.Sea else if (selected) C.Gold else C.Muted, Modifier.size(24.dp))
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

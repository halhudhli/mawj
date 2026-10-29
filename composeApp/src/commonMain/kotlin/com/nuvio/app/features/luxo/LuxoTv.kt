package com.nuvio.app.features.luxo

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True on Android TV (TCL, Google TV…): remote control, no touchscreen. */
expect fun luxoIsTv(): Boolean

/**
 * A clear focus ring for the TV remote: the focused item grows a little and gets a white outline.
 * Does nothing visible on touch screens until something is focused with a keyboard/remote.
 */
@Composable
fun Modifier.luxoTvFocus(corner: Dp = 18.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            val s = if (focused) 1.06f else 1f
            scaleX = s
            scaleY = s
        }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(corner))
}

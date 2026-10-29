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
 * Kept for older call sites. The focus frame is now drawn for every clickable by
 * [LuxoTvIndication] (see LuxoTvLite.kt), in the draw phase only, so this adds nothing on its own
 * and a card never gets two frames.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.luxoTvFocus(corner: Dp = 18.dp): Modifier = this

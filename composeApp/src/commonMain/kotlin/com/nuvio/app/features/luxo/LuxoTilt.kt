package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset

/**
 * Device tilt for the holographic pass, each axis roughly -1..1 (0 when flat or unsupported).
 */
@Composable
internal expect fun rememberLuxoTilt(enabled: Boolean): Offset

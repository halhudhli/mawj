package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

actual fun luxoIsAndroidPlatform(): Boolean = false

@Composable
actual fun LuxoFrameRateMatch(fps: Float) = Unit

actual fun luxoAudioOutputName(): String? = null

@Composable
actual fun rememberLuxoSubtitleFilePicker(onPicked: (text: String, extension: String) -> Unit): (() -> Unit)? = null

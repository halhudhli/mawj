package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

/** True on the Android builds (phone, tablet, TV). New features land there first. */
expect fun luxoIsAndroidPlatform(): Boolean

/** «مطابقة سرعة الشاشة مع الفيلم»: asks the display for a refresh rate that is a whole multiple of [fps]. */
@Composable
expect fun LuxoFrameRateMatch(fps: Float)

/** Name of the headphones / Bluetooth device the sound is playing on, or null for the device speaker. */
expect fun luxoAudioOutputName(): String?

/**
 * A starter that opens the device's file browser for a subtitle file. The file comes back as text
 * (its encoding detected, so Arabic Windows-1256 files read correctly) with its extension
 * (srt / vtt / ass). Null where the platform has no picker yet.
 */
@Composable
expect fun rememberLuxoSubtitleFilePicker(onPicked: (text: String, extension: String) -> Unit): (() -> Unit)?

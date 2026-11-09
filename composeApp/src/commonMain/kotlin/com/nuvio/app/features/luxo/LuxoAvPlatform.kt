package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

/** True on the Android builds (phone, tablet, TV). New features land there first. */
expect fun luxoIsAndroidPlatform(): Boolean

/** «مطابقة سرعة الشاشة مع الفيلم»: asks the display for a refresh rate that is a whole multiple of [fps]. */
@Composable
expect fun LuxoFrameRateMatch(fps: Float)

/** Name of the headphones / Bluetooth device the sound is playing on, or null for the device speaker. */
expect fun luxoAudioOutputName(): String?

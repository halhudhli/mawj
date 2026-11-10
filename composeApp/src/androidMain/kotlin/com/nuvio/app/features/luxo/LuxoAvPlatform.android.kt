package com.nuvio.app.features.luxo

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.roundToInt

actual fun luxoIsAndroidPlatform(): Boolean = true

private tailrec fun Context.luxoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.luxoActivity()
    else -> null
}

/**
 * Picks, among the display modes with the current resolution, the highest refresh rate that is a
 * whole multiple of the film's frame rate (23.976 → 120 / 48, 25 → 100 / 50, 30 → 120 / 60...).
 * Nothing changes when the screen has no such mode. The original mode returns when playback ends.
 */
@Composable
actual fun LuxoFrameRateMatch(fps: Float) {
    val context = LocalContext.current
    val activity = remember(context) { context.luxoActivity() }
    val key = (fps * 1000f).roundToInt()
    DisposableEffect(activity, key) {
        val window = activity?.window
        if (window != null && fps in 10f..121f) {
            runCatching {
                @Suppress("DEPRECATION")
                val display: Display? = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
                val current = display?.mode
                if (display != null && current != null) {
                    val best = display.supportedModes
                        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                        .filter { mode ->
                            val n = (mode.refreshRate / fps).roundToInt()
                            n >= 1 && abs(mode.refreshRate - n * fps) / mode.refreshRate < 0.012f
                        }
                        .maxByOrNull { it.refreshRate }
                    if (best != null && best.modeId != window.attributes.preferredDisplayModeId) {
                        val attrs = window.attributes
                        attrs.preferredDisplayModeId = best.modeId
                        window.attributes = attrs
                    }
                }
            }
        }
        onDispose {
            runCatching {
                if (window != null && window.attributes.preferredDisplayModeId != 0) {
                    val attrs = window.attributes
                    attrs.preferredDisplayModeId = 0
                    window.attributes = attrs
                }
            }
        }
    }
}

actual fun luxoAudioOutputName(): String? {
    val context = LuxoPrefsStorage.appContext ?: return null
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    val devices = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull() ?: return null
    val bluetoothTypes = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        if (Build.VERSION.SDK_INT >= 31) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }
    devices.firstOrNull { it.type in bluetoothTypes }?.let { device ->
        return device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "سماعة بلوتوث"
    }
    val wiredTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )
    if (devices.any { it.type in wiredTypes }) return "سماعة سلكية"
    return null
}

@Composable
actual fun rememberLuxoSubtitleFilePicker(onPicked: (text: String, extension: String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback = androidx.compose.runtime.rememberUpdatedState(onPicked)
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: uri.lastPathSegment.orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > 8 * 1024 * 1024) return@runCatching
                }
                out.toByteArray()
            } ?: return@runCatching
            val text = com.nuvio.app.features.autosync.SubtitleCharsetDetector.decode(bytes)
            val head = text.trimStart().take(200)
            val extension = when {
                name.endsWith(".vtt", ignoreCase = true) || head.startsWith("WEBVTT", ignoreCase = true) -> "vtt"
                name.endsWith(".ass", ignoreCase = true) || name.endsWith(".ssa", ignoreCase = true) || head.startsWith("[Script Info]", ignoreCase = true) -> "ass"
                else -> "srt"
            }
            if (text.isNotBlank()) callback.value(text, extension)
        }
    }
    return { runCatching { launcher.launch("*/*") } }
}

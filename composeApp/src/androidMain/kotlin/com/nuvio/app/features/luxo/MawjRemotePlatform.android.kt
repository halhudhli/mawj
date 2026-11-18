package com.nuvio.app.features.luxo

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** The open Mawj window, so a key from the tablet lands where the TV remote's key would. */
object MawjRemoteActivity {
    @Volatile var ref: WeakReference<Activity>? = null
}

private val keyThread = Executors.newSingleThreadExecutor { r -> Thread(r, "mawj-remote-keys").apply { isDaemon = true } }

actual fun mawjRemoteKey(key: String) {
    val code = when (key) {
        "up" -> KeyEvent.KEYCODE_DPAD_UP
        "down" -> KeyEvent.KEYCODE_DPAD_DOWN
        "left" -> KeyEvent.KEYCODE_DPAD_LEFT
        "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
        "back" -> KeyEvent.KEYCODE_BACK
        else -> return
    }
    keyThread.execute {
        // Instrumentation sends the key to whichever Mawj window has focus (a sheet or dialog included).
        // It is allowed inside the app's own windows; if the system refuses, the main window gets it directly.
        val sent = runCatching { Instrumentation().sendKeyDownUpSync(code) }.isSuccess
        if (!sent) {
            val activity = MawjRemoteActivity.ref?.get() ?: return@execute
            activity.runOnUiThread {
                runCatching {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }
            }
        }
    }
}

actual fun mawjRemoteVolume(op: String, value: Int): Int {
    val am = LuxoPrefsStorage.appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return -1
    val stream = AudioManager.STREAM_MUSIC
    runCatching {
        when (op) {
            "up" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "set" -> {
                val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
                am.setStreamVolume(stream, (value.coerceIn(0, 100) * max + 50) / 100, AudioManager.FLAG_SHOW_UI)
            }
            else -> Unit
        }
    }
    return runCatching {
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        if (am.isStreamMute(stream)) 0 else am.getStreamVolume(stream) * 100 / max
    }.getOrDefault(-1)
}

actual suspend fun mawjRemotePairRequest(host: String, port: Int, fromName: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 2500)
            s.soTimeout = 3000
            s.getOutputStream().write("PAIR|${fromName.replace('\n', ' ').replace('|', ' ')}\n".toByteArray(Charsets.UTF_8))
            BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine() == "pair"
        }
    }.getOrDefault(false)
}

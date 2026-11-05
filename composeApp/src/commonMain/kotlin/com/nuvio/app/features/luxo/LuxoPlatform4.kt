package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

/** A starter that opens the photo picker; the picked image comes back as (bytes, mime type). */
@Composable
expect fun rememberLuxoImagePicker(onPicked: (ByteArray, String) -> Unit): (() -> Unit)?

/** Records [seconds] of the playing video as a GIF into Photos. */
expect suspend fun luxoRecordGif(name: String, seconds: Int, onProgress: (Float) -> Unit): Boolean

data class LuxoRemoteDevice(val name: String, val host: String, val port: Int)

/** Local-network channel between the tablet/phone and the TV. */
expect object LuxoRemoteNet {
    /** TV side: listens for commands; [onCommand] gets the JSON payload. */
    fun startServer(code: String, deviceName: String, onCommand: (String) -> Unit)
    fun stopServer()
    fun localAddress(): String?
    val port: Int

    /** Controller side. */
    suspend fun discover(timeoutMs: Long): List<LuxoRemoteDevice>
    suspend fun send(host: String, port: Int, code: String, payload: String): Boolean
}

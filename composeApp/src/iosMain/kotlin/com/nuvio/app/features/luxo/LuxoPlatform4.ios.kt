package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

@Composable
actual fun rememberLuxoImagePicker(onPicked: (ByteArray, String) -> Unit): (() -> Unit)? = null

actual suspend fun luxoRecordGif(name: String, seconds: Int, onProgress: (Float) -> Unit): Boolean = false

actual object LuxoRemoteNet {
    actual fun startServer(code: String, deviceName: String, onCommand: (String) -> Unit) {}
    actual fun stopServer() {}
    actual fun localAddress(): String? = null
    actual val port: Int = 0
    actual suspend fun discover(timeoutMs: Long): List<LuxoRemoteDevice> = emptyList()
    actual suspend fun send(host: String, port: Int, code: String, payload: String): Boolean = false
}

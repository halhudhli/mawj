package com.nuvio.app.features.luxo

// iPhone and iPad are never the TV, and the local remote channel is Android only for now.
actual fun mawjRemoteKey(key: String) {}

actual fun mawjRemoteVolume(op: String, value: Int): Int = -1

actual suspend fun mawjRemotePairRequest(host: String, port: Int, fromName: String): Boolean = false

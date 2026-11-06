package com.nuvio.app.features.luxo

actual fun luxoPublishWatchNext(entries: List<LuxoWatchNextEntry>) {}

actual suspend fun luxoRemoteQuery(host: String, port: Int, code: String): String? = null

actual suspend fun luxoSetWallpaper(jpeg: ByteArray): Boolean = false

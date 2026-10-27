package com.nuvio.app.features.luxo

/** Writes [content] to an app cache file and returns a URI the player can load, or null. */
internal expect fun luxoWriteCacheFile(name: String, content: String): String?

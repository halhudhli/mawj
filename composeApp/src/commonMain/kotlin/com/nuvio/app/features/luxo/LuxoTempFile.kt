package com.nuvio.app.features.luxo

/** The subtitle line currently on screen (ExoPlayer). */
object LuxoNowSubtitle {
    @kotlin.concurrent.Volatile
    var text: String = ""
}

/** Writes a temporary text file and returns a file:// URI for the player (null when unsupported). */
expect fun luxoWriteTempFile(name: String, text: String): String?

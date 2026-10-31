package com.nuvio.app.features.luxo

/** Plays a short soundtrack preview (one at a time). */
expect object LuxoAudioPreview {
    fun play(url: String, onDone: () -> Unit)
    fun stop()
}

package com.nuvio.app.features.luxo

actual object LuxoAudioPreview {
    actual fun play(url: String, onDone: () -> Unit) {
        onDone()
    }

    actual fun stop() {}
}

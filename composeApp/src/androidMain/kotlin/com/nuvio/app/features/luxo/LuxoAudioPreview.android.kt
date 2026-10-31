package com.nuvio.app.features.luxo

import android.media.AudioAttributes
import android.media.MediaPlayer

actual object LuxoAudioPreview {
    private var player: MediaPlayer? = null

    actual fun play(url: String, onDone: () -> Unit) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    stop()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onDone()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            stop()
            onDone()
        }
    }

    actual fun stop() {
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
    }
}

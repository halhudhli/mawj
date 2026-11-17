package com.nuvio.app.features.luxo

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.nuvio.app.R

internal actual object MawjSoundPlayer {
    private var pool: SoundPool? = null
    private val loaded = mutableMapOf<MawjSound, Int>()
    private val ready = mutableSetOf<Int>()
    private var opening: MediaPlayer? = null
    private var ambient: MediaPlayer? = null

    private fun res(sound: MawjSound): Int = when (sound) {
        MawjSound.Opening -> R.raw.mawj_opening
        MawjSound.Open -> R.raw.mawj_ui_open
        MawjSound.Back -> R.raw.mawj_ui_back
        MawjSound.Success -> R.raw.mawj_ui_success
        MawjSound.Error -> R.raw.mawj_ui_error
        MawjSound.AmbientSea -> R.raw.mawj_ambient_sea
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun pool(): SoundPool? {
        pool?.let { return it }
        val ctx = LuxoPrefsStorage.appContext ?: return null
        val p = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready += id }
        listOf(MawjSound.Open, MawjSound.Back, MawjSound.Success, MawjSound.Error).forEach { s ->
            loaded[s] = p.load(ctx, res(s), 1)
        }
        pool = p
        return p
    }

    actual fun play(sound: MawjSound, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        if (sound == MawjSound.Opening) {
            val ctx = LuxoPrefsStorage.appContext ?: return
            opening?.release()
            opening = MediaPlayer.create(ctx, res(sound))?.apply {
                setVolume(v, v)
                setOnCompletionListener { it.release(); if (opening === it) opening = null }
                start()
            }
            return
        }
        val p = pool() ?: return
        val id = loaded[sound] ?: return
        if (id in ready) p.play(id, v * 0.8f, v * 0.8f, 1, 0, 1f)
    }

    actual fun startAmbient(volume: Float) {
        if (ambient != null) return
        val ctx = LuxoPrefsStorage.appContext ?: return
        ambient = MediaPlayer.create(ctx, res(MawjSound.AmbientSea))?.apply {
            isLooping = true
            setVolume(volume, volume)
            start()
        }
    }

    actual fun stopAmbient() {
        ambient?.let { runCatching { it.stop() }; it.release() }
        ambient = null
    }
}

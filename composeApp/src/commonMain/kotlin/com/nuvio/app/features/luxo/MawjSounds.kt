package com.nuvio.app.features.luxo

/** Mawj's sounds: the opening sea and short sea cues for moving around the app. */
enum class MawjSound { Opening, Open, Back, Success, Error, AmbientSea }

/** Plays a sound on the device; quiet no-op where the platform has no player yet. */
internal expect object MawjSoundPlayer {
    fun play(sound: MawjSound, volume: Float)
    fun startAmbient(volume: Float)
    fun stopAmbient()
}

/** Settings-aware entry point; every call checks the switches first. */
object MawjSounds {
    fun play(sound: MawjSound) {
        val f = runCatching { LuxoFeaturesRepository.ensureLoaded(); LuxoFeaturesRepository.state.value }.getOrNull() ?: return
        val allowed = when (sound) {
            MawjSound.Opening -> f.mawjOpeningSound
            MawjSound.AmbientSea -> false
            else -> f.mawjUiSounds
        }
        if (!allowed) return
        runCatching { MawjSoundPlayer.play(sound, f.mawjSoundVolume / 100f) }
    }

    fun syncAmbient(on: Boolean) {
        val f = runCatching { LuxoFeaturesRepository.state.value }.getOrNull() ?: return
        runCatching {
            if (on && f.mawjAmbientSea) MawjSoundPlayer.startAmbient(f.mawjSoundVolume / 100f * 0.35f) else MawjSoundPlayer.stopAmbient()
        }
    }
}

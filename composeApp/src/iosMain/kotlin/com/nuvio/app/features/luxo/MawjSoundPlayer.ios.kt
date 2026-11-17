package com.nuvio.app.features.luxo

/** iPhone sounds come with the iPhone build later; silent for now. */
internal actual object MawjSoundPlayer {
    actual fun play(sound: MawjSound, volume: Float) {}
    actual fun startAmbient(volume: Float) {}
    actual fun stopAmbient() {}
}

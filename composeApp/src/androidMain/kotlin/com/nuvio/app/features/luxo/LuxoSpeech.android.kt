package com.nuvio.app.features.luxo

import android.speech.tts.TextToSpeech
import java.util.Locale

private object LuxoTts {
    var engine: TextToSpeech? = null
    var ready = false
    var pending: String? = null

    fun speak(text: String) {
        val e = engine
        if (e != null && ready) { say(e, text); return }
        pending = text
        if (e != null) return
        val context = LuxoPrefsStorage.appContext ?: return
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            val t = engine ?: return@TextToSpeech
            if (ready) {
                runCatching { t.language = Locale("ar") }
                pending?.let { say(t, it) }
            }
            pending = null
        }
    }

    private fun say(e: TextToSpeech, text: String) {
        val clean = text.replace(Regex("[*#_`>]"), " ").take(3800)
        runCatching { e.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "mawj-companion") }
    }
}

actual fun luxoSpeak(text: String) { runCatching { LuxoTts.speak(text) } }

actual fun luxoStopSpeaking() { runCatching { LuxoTts.engine?.stop() } }

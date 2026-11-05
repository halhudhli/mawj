package com.nuvio.app.features.luxo

/** Reads [text] aloud (Arabic voice when available). */
expect fun luxoSpeak(text: String)

expect fun luxoStopSpeaking()

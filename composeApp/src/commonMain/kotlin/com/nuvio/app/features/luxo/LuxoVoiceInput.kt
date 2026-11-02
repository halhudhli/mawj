package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable

/** «🎙️ البحث بالصوت»: returns a starter, or null where speech input isn't available. */
@Composable
expect fun rememberLuxoVoiceInput(onText: (String) -> Unit): (() -> Unit)?

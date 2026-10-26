package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a poster reacts when it is pressed or focused, and whether the screen takes its colour. */
@Serializable
enum class LuxoPosterFocusStyle {
    /** Harbor: no light behind posters, a soft zoom, a deep shadow and a thin glass edge. */
    CLEAN,

    /** A faint glow (20%) in the poster's own colour, and a darker, gently tinted screen. */
    AMBIENT,

    /** No glow; the poster lifts and a small light line appears under it. */
    SPOTLIGHT,
}

@Serializable
data class LuxoLookSettings(
    val posterFocusStyle: LuxoPosterFocusStyle = LuxoPosterFocusStyle.CLEAN,
)

object LuxoLookSettingsRepository {
    private const val storageKey = "look_settings_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoLookSettings())
    val state: StateFlow<LuxoLookSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoLookSettings.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun update(transform: (LuxoLookSettings) -> LuxoLookSettings) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoLookSettings.serializer(), next))
    }
}

@Composable
internal fun rememberLuxoLookSettings(): LuxoLookSettings {
    val settings by remember {
        LuxoLookSettingsRepository.ensureLoaded()
        LuxoLookSettingsRepository.state
    }.collectAsStateWithLifecycle()
    return settings
}

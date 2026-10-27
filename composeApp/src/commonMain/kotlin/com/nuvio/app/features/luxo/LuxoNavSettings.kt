package com.nuvio.app.features.luxo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which Cinemata-style section tabs appear in the bottom bar. */
@Serializable
data class LuxoNavSettings(
    val showMovies: Boolean = true,
    val showSeries: Boolean = true,
    val showLists: Boolean = true,
)

object LuxoNavSettingsRepository {
    private const val storageKey = "nav_settings_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoNavSettings())
    val state: StateFlow<LuxoNavSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoNavSettings.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun update(transform: (LuxoNavSettings) -> LuxoNavSettings) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoNavSettings.serializer(), next))
    }
}

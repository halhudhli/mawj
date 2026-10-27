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

/** On/off switches for Luxo's extra features. Everything can be turned off. */
@Serializable
data class LuxoFeatures(
    val luxoDesign: Boolean = true,
    val luxoDock: Boolean = true,
    /** 0 = no glow, 1 = soft (default), 2 = strong. */
    val glowLevel: Int = 1,
    val trailerFit: Boolean = true,
    val heroTilt: Boolean = true,
    val timeTint: Boolean = true,
    val countdown: Boolean = true,
    val diary: Boolean = true,
    val challenge: Boolean = true,
    val wrapped: Boolean = true,
    val profileHub: Boolean = true,
    val passTilt: Boolean = true,
    val seasonalThemes: Boolean = true,
    val achievements: Boolean = true,
    val heatmap: Boolean = true,
    val shareCard: Boolean = true,
    val haptics: Boolean = true,
    val discoveryQueue: Boolean = true,
    val animeSection: Boolean = true,
    val themeStudio: Boolean = true,
    val playerScreenshot: Boolean = true,
    val playerAbLoop: Boolean = true,
    val ambientGlow: Boolean = false,
    val streamRanking: Boolean = true,
    val hideCamReleases: Boolean = true,
)

object LuxoFeaturesRepository {
    private const val storageKey = "features_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoFeatures())
    val state: StateFlow<LuxoFeatures> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoFeatures.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun current(): LuxoFeatures {
        ensureLoaded()
        return _state.value
    }

    fun update(transform: (LuxoFeatures) -> LuxoFeatures) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoFeatures.serializer(), next))
    }
}

@Composable
fun rememberLuxoFeatures(): LuxoFeatures {
    val state by remember {
        LuxoFeaturesRepository.ensureLoaded()
        LuxoFeaturesRepository.state
    }.collectAsStateWithLifecycle()
    return state
}

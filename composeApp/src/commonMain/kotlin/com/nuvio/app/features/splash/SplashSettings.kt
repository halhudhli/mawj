package com.nuvio.app.features.splash

import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class SplashBackgroundStyle { STARS, WAVES, PARTICLES, GRADIENT, SOLID }

@Serializable
enum class SplashLogoAnimation { FADE, ZOOM, PULSE, NONE }

@Serializable
enum class SplashLogoVariant { SILVER, ORIGINAL, GOLD, ROSE_GOLD, COPPER, EMERALD, ARCTIC_BLUE, GRAPHITE }

@Serializable
data class SplashSettings(
    val enabled: Boolean = true,
    val style: SplashBackgroundStyle = SplashBackgroundStyle.STARS,
    /** 0xRRGGBB */
    val backgroundColor: Int = 0x0D0D0D,
    /** 0xRRGGBB — colour of stars / waves / particles / gradient accent. */
    val accentColor: Int = 0xC9CDD4,
    val logoVariant: SplashLogoVariant = SplashLogoVariant.SILVER,
    val logoAnimation: SplashLogoAnimation = SplashLogoAnimation.ZOOM,
    /** Multiplier of the default logo width. */
    val logoScale: Float = 1f,
    /** Animation speed multiplier. */
    val speed: Float = 1f,
    /** Number of stars / particles / waves multiplier. */
    val density: Float = 1f,
    val glow: Boolean = true,
    val durationMs: Int = DEFAULT_DURATION_MS,
) {
    companion object {
        const val DEFAULT_DURATION_MS = 1300
        const val MIN_DURATION_MS = 600
        const val MAX_DURATION_MS = 4000
    }
}

object SplashSettingsRepository {
    private const val storageKey = "splash_settings_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(SplashSettings())
    val state: StateFlow<SplashSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(SplashSettings.serializer(), payload) }
            .onSuccess { _state.value = it.sanitized() }
    }

    fun update(transform: (SplashSettings) -> SplashSettings) {
        ensureLoaded()
        val next = transform(_state.value).sanitized()
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(SplashSettings.serializer(), next))
    }

    fun reset() = update { SplashSettings() }

    /** How long the launch intro is held before the app may continue. */
    fun introDurationMs(): Long {
        ensureLoaded()
        val settings = _state.value
        return if (settings.enabled) settings.durationMs.toLong() else 0L
    }

    private fun SplashSettings.sanitized() = copy(
        logoScale = logoScale.coerceIn(0.5f, 1.8f),
        speed = speed.coerceIn(0.25f, 3f),
        density = density.coerceIn(0.2f, 2.5f),
        durationMs = durationMs.coerceIn(SplashSettings.MIN_DURATION_MS, SplashSettings.MAX_DURATION_MS),
    )
}

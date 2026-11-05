package com.nuvio.app.features.splash

import com.nuvio.app.features.luxo.LuxoPrefsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json

@Serializable
enum class SplashBackgroundStyle { MAWJ, STARS, WAVES, PARTICLES, GRADIENT, SOLID, AURORA_RIBBONS, FILM, CURTAIN, NEON, PROJECTOR, SPOTLIGHTS, FILM_LEADER, RAIN, BOKEH, WARP, OLD_FILM }

@Serializable
enum class SplashLogoAnimation { FADE, ZOOM, PULSE, NONE, SLIDE_UP, DROP, FLIP, SPIN, FLICKER }

@Serializable
enum class SplashLogoVariant { SILVER, ORIGINAL, GOLD, ROSE_GOLD, COPPER, EMERALD, ARCTIC_BLUE, GRAPHITE }

@Serializable
enum class SplashLogoFont { MAWJ, DEFAULT, CINZEL, BEBAS, BARLOW, POPPINS }

@Serializable
data class SplashSettings(
    val enabled: Boolean = true,
    val style: SplashBackgroundStyle = SplashBackgroundStyle.MAWJ,
    /** 0xRRGGBB */
    val backgroundColor: Int = 0x050608,
    /** 0xRRGGBB — colour of stars / waves / particles / gradient accent. */
    val accentColor: Int = 0xC9CDD4,
    val logoVariant: SplashLogoVariant = SplashLogoVariant.ORIGINAL,
    /** DEFAULT shows the image wordmark; the others write "LUXO" in that font. */
    val logoFont: SplashLogoFont = SplashLogoFont.MAWJ,
    /** 0xRRGGBB — colour of the written "LUXO". */
    val logoTextColor: Int = 0xE8E8EC,
    val logoAnimation: SplashLogoAnimation = SplashLogoAnimation.ZOOM,
    /** Multiplier of the default logo width. */
    val logoScale: Float = 1f,
    /** Animation speed multiplier. */
    val speed: Float = 1f,
    /** Number of stars / particles / waves multiplier. */
    val density: Float = 1f,
    val glow: Boolean = true,
    val durationMs: Int = DEFAULT_DURATION_MS,
    /** Luxo: a tap on the splash skips it. */
    val tapToSkip: Boolean = true,
    /** Luxo: end early (after 1.5 s) when the app is already ready. */
    val shortenWhenReady: Boolean = true,
    /** Luxo: the colours flow inside the letters of the written logo. */
    val flowingLetters: Boolean = true,
    /** «موج»: the word mirrored on the water. */
    val mawjReflection: Boolean = true,
    /** «موج»: MAWJ and the line under the word. */
    val mawjTagline: Boolean = true,
) {
    companion object {
        const val DEFAULT_DURATION_MS = 5000
        const val MIN_DURATION_MS = 2000
        const val MAX_DURATION_MS = 10000
    }
}

/** A tap on the splash asks the gate to move on. */
object SplashSkip {
    var requested by androidx.compose.runtime.mutableStateOf(false)
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
        LuxoPrefsStorage.load(storageKey)?.let { payload ->
            runCatching { json.decodeFromString(SplashSettings.serializer(), payload) }
                .onSuccess { _state.value = it.sanitized() }
        }
        // «موج»: the new identity's splash becomes the default once (the old choices stay selectable).
        if (LuxoPrefsStorage.load(MAWJ_APPLIED_KEY) != "1") {
            val cur = _state.value
            _state.value = cur.copy(
                style = SplashBackgroundStyle.MAWJ,
                logoFont = SplashLogoFont.MAWJ,
                backgroundColor = 0x050608,
                accentColor = 0xC9CED6,
                durationMs = maxOf(cur.durationMs, SplashSettings.DEFAULT_DURATION_MS),
            ).sanitized()
            LuxoPrefsStorage.save(storageKey, json.encodeToString(SplashSettings.serializer(), _state.value))
            LuxoPrefsStorage.save(MAWJ_APPLIED_KEY, "1")
        }
    }

    private const val MAWJ_APPLIED_KEY = "mawj_splash_applied_v1"

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

package com.nuvio.app.features.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import androidx.media3.exoplayer.ExoPlayer
import com.nuvio.app.features.luxo.LuxoFeaturesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f,
)

/**
 * One colour matrix shared by the running ExoPlayer. The GPU asks for it on every frame, so
 * changing [matrix] shows on the video immediately without rebuilding the player.
 */
@OptIn(UnstableApi::class)
internal object LuxoExoColorMatrix : RgbMatrix {
    @Volatile
    var matrix: FloatArray = IDENTITY

    /** The player the effect is installed on (installed once per player). */
    @Volatile
    var installedOn: Any? = null

    /** Installs the colour effect before playback starts, when a look is already chosen. */
    fun installIfNeeded(player: ExoPlayer, tunnelingEnabled: Boolean) {
        if (tunnelingEnabled || installedOn === player) return
        val values = LuxoVideoColorRepository.effective()
        if (values.isNeutral) return
        matrix = values.rgbMatrix()
        runCatching { player.setVideoEffects(listOf(this)) }.onSuccess { installedOn = player }
    }

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
}

/** Keeps the ExoPlayer picture in step with Luxo's colour settings. */
@OptIn(UnstableApi::class)
@Composable
internal fun LuxoExoColorEffect(player: ExoPlayer, tunnelingEnabled: Boolean) {
    // Colour effects need the frame processor, which tunneled playback skips.
    if (tunnelingEnabled) return
    LaunchedEffect(player) {
        // HDR probe for the «ذكي» look.
        launch {
            while (isActive) {
                val transfer = runCatching { player.videoFormat?.colorInfo?.colorTransfer }.getOrNull()
                LuxoVideoColorRepository.videoIsHdr.value =
                    transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
                delay(1_500L)
            }
        }
        combine(
            LuxoVideoColorRepository.state,
            LuxoVideoColorRepository.videoIsHdr,
            LuxoFeaturesRepository.state,
        ) { _, _, _ -> LuxoVideoColorRepository.effective() }
            .collect { values ->
                LuxoExoColorMatrix.matrix = if (values.isNeutral) IDENTITY else values.rgbMatrix()
                if (!values.isNeutral) LuxoExoColorMatrix.installIfNeeded(player, tunnelingEnabled)
            }
    }
}

/** Same for libmpv, through its own picture properties (applied live). */
@Composable
internal fun LuxoMpvColorEffect(
    key: Any?,
    apply: (LuxoColorValues) -> Unit,
    isHdr: suspend () -> Boolean,
) {
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        launch {
            while (isActive) {
                LuxoVideoColorRepository.videoIsHdr.value = runCatching { isHdr() }.getOrDefault(false)
                delay(2_000L)
            }
        }
        combine(
            LuxoVideoColorRepository.state,
            LuxoVideoColorRepository.videoIsHdr,
            LuxoFeaturesRepository.state,
        ) { _, _, _ -> LuxoVideoColorRepository.effective() }
            .collect { apply(it) }
    }
}

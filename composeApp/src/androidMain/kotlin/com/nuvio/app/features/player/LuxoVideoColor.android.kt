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

    /** A player whose source froze with the effect: no colours on it again. */
    @Volatile
    var blockedOn: Any? = null

    /**
     * The effect has to exist before prepare(). Mid-video: stop, add the effect, prepare again and
     * continue from the same position (a second of loading instead of a frozen picture).
     */
    fun reprepareWithEffect(player: ExoPlayer) {
        if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            runCatching { player.setVideoEffects(listOf(this)) }.onSuccess { installedOn = player }
            return
        }
        val position = player.currentPosition
        val playWhenReady = player.playWhenReady
        runCatching {
            player.stop()
            player.setVideoEffects(listOf(this))
            player.prepare()
            player.seekTo(position)
            player.playWhenReady = playWhenReady
        }.onSuccess { installedOn = player }
    }

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

/**
 * Keeps the ExoPlayer picture in step with Luxo's colour settings.
 *
 * ExoPlayer only accepts colour effects that were set up before the video was prepared; adding
 * them in the middle of playback froze the picture (stuck spinner after a seek). So when a look is
 * picked mid-video, the source is re-prepared once at the same position with the effect in place.
 * A freeze guard also watches playback: if the picture is stuck while data is already buffered,
 * it first drops the colour effect (if any), then nudges the player with a seek to the same spot.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LuxoExoColorEffect(player: ExoPlayer, tunnelingEnabled: Boolean) {
    LaunchedEffect(player) {
        // HDR probe for the «ذكي» look + freeze guard.
        launch {
            var stuckSeconds = 0
            var lastNudgeAt = 0L
            var ticks = 0L
            while (isActive) {
                ticks++
                val transfer = runCatching { player.videoFormat?.colorInfo?.colorTransfer }.getOrNull()
                LuxoVideoColorRepository.videoIsHdr.value =
                    transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
                val guardOn = LuxoFeaturesRepository.current().stallGuard
                val buffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING && player.playWhenReady
                val ahead = player.bufferedPosition - player.currentPosition
                stuckSeconds = if (buffering && ahead > 2_500L) stuckSeconds + 1 else 0
                if (guardOn && stuckSeconds >= 5 && ticks - lastNudgeAt > 12) {
                    lastNudgeAt = ticks
                    stuckSeconds = 0
                    if (LuxoExoColorMatrix.installedOn === player) {
                        runCatching { player.setVideoEffects(emptyList()) }
                        LuxoExoColorMatrix.installedOn = null
                        LuxoExoColorMatrix.blockedOn = player
                        LuxoPlayerBridge.toast = "وقّفنا ألوان الصورة لهالمصدر لأنه علّق، وكمّلنا التشغيل ✅"
                    } else {
                        runCatching { player.seekTo(player.currentPosition) }
                        LuxoPlayerBridge.toast = "الفيديو علّق، رجّعناه يشتغل ✅"
                    }
                    launch {
                        delay(3_000L)
                        LuxoPlayerBridge.toast = null
                    }
                }
                delay(1_000L)
            }
        }
        if (tunnelingEnabled) return@LaunchedEffect
        combine(
            LuxoVideoColorRepository.state,
            LuxoVideoColorRepository.videoIsHdr,
            LuxoFeaturesRepository.state,
        ) { _, _, _ -> LuxoVideoColorRepository.effective() }
            .collect { values ->
                LuxoExoColorMatrix.matrix = if (values.isNeutral) IDENTITY else values.rgbMatrix()
                if (!values.isNeutral && LuxoExoColorMatrix.installedOn !== player && LuxoExoColorMatrix.blockedOn !== player) {
                    LuxoExoColorMatrix.reprepareWithEffect(player)
                }
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

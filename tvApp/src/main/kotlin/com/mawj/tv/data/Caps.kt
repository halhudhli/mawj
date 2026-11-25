package com.mawj.tv.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * What this screen's chip can decode in hardware. Read once from the system codec list.
 * A source the chip cannot decode falls to software decoding: on a weak TV that is the
 * slowness seen with AV1 and the crashes with heavy 4K / 10-bit files.
 */
object Caps {
    private data class Hw(val av1: Boolean, val hevc: Boolean, val hevc10: Boolean, val uhd: Boolean)

    private val hw: Hw by lazy {
        val infos = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }.getOrDefault(emptyList())
            .filter { !it.isEncoder && isHardware(it) }
        fun decoders(mime: String) = infos.filter { info -> info.supportedTypes.any { it.equals(mime, true) } }
        val av1 = decoders("video/av01").isNotEmpty()
        val hevcList = decoders("video/hevc")
        val hevc10 = hevcList.any { info ->
            runCatching {
                info.getCapabilitiesForType("video/hevc").profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 }
            }.getOrDefault(false)
        }
        val uhd = (hevcList + decoders("video/avc")).any { info ->
            runCatching {
                val type = info.supportedTypes.first { it.startsWith("video/") }
                info.getCapabilitiesForType(type).videoCapabilities?.isSizeSupported(3840, 2160) == true
            }.getOrDefault(false)
        }
        Hw(av1, hevcList.isNotEmpty(), hevc10, uhd)
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated
        val n = info.name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains("ffmpeg") || n.contains(".sw."))
    }

    /** Why this source would play slowly here, or null when the chip handles it. */
    fun slowReason(s: Stream): String? {
        val codec = s.codec?.uppercase().orEmpty()
        return when {
            "AV1" in codec && !hw.av1 -> "AV1 بلا دعم من الشاشة"
            "HEVC" in codec && !hw.hevc -> "HEVC بلا دعم من الشاشة"
            "HEVC" in codec && s.tenBit && !hw.hevc10 -> "10-bit بلا دعم من الشاشة"
            s.quality >= 2160 && !hw.uhd -> "4K بلا دعم من الشاشة"
            else -> null
        }
    }

    /**
     * A heavy file: 4K, 10-bit HEVC, or very large. Copying its decoded frames costs a weak TV
     * too much memory, so it plays straight to the screen (direct mode).
     */
    fun heavy(s: Stream): Boolean =
        s.quality >= 2160 || s.tenBit || s.dolbyVision || s.sizeBytes > 20L * 1_073_741_824L
}

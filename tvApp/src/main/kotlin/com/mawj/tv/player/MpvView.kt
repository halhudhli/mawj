package com.mawj.tv.player

import android.content.Context
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.Utils

/** A track the viewer can pick (audio or subtitles). */
data class Track(val id: Int, val type: String, val label: String, val lang: String, val selected: Boolean, val title: String = "", val external: Boolean = false)

/**
 * The video surface: libmpv with hardware decoding and settings tuned for TVs (fast start, a
 * read-ahead that rides out network dips, Arabic subtitles from the system fonts).
 */
class MpvView(context: Context, private val direct: Boolean = false) : BaseMPVView(context, null) {
    @Volatile private var released = false

    fun start() {
        runCatching { Utils.copyAssets(context) }
        initialize(context.filesDir.path, context.cacheDir.path)
    }

    override fun initOptions() {
        if (direct) {
            // Heavy files (4K, 10-bit, very large): frames go straight from the chip to the screen,
            // with no copy through memory. The app draws the subtitles itself in this mode.
            setVo("mediacodec_embed")
            mpv.setOptionString("hwdec", "mediacodec")
            mpv.setOptionString("sub-visibility", "no")
        } else {
            setVo("gpu")
            mpv.setOptionString("profile", "fast")
            mpv.setOptionString("hwdec", "mediacodec-copy,mediacodec,no")
        }
        // If the chip cannot decode it, software decoding takes the lighter path.
        mpv.setOptionString("vd-lavc-skiploopfilter", "nonkey")
        mpv.setOptionString("vd-lavc-threads", "${Runtime.getRuntime().availableProcessors().coerceIn(2, 8)}")
        mpv.setOptionString("tls-verify", "yes")
        mpv.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
        // Start sooner and keep playing through short network dips.
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "${48 * 1024 * 1024}")
        mpv.setOptionString("demuxer-max-back-bytes", "${16 * 1024 * 1024}")
        mpv.setOptionString("demuxer-readahead-secs", "30")
        mpv.setOptionString("demuxer-lavf-probesize", "1048576")
        mpv.setOptionString("demuxer-lavf-analyzeduration", "1")
        mpv.setOptionString("cache-pause-wait", "2")
        mpv.setOptionString("network-timeout", "30")
        // Subtitles: Arabic first, not-UTF-8 files read as Windows-1256, system Arabic fonts.
        mpv.setOptionString("slang", "ar,ara,arabic,en,eng")
        mpv.setOptionString("sub-codepage", "cp1256")
        mpv.setOptionString("sub-fonts-dir", "/system/fonts")
        mpv.setOptionString("sub-font", "Noto Naskh Arabic")
        mpv.setOptionString("sub-font-size", "46")
        mpv.setOptionString("sub-border-size", "2.5")
        mpv.setOptionString("sub-shadow-offset", "1")
        mpv.setOptionString("sub-margin-y", "40")
        mpv.setOptionString("msg-level", "all=warn")
        mpv.setPropertyBoolean("keep-open", true)
        mpv.setPropertyBoolean("audio-fallback-to-null", true)
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() {
        mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
    }

    fun load(url: String, headers: Map<String, String>, startMs: Long) {
        val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        if (!ua.isNullOrBlank()) mpv.setPropertyString("user-agent", ua)
        val rest = headers.filterKeys { !it.equals("User-Agent", true) }
            .map { (k, v) -> "$k: ${v.replace(",", "\\,")}" }.joinToString(",")
        mpv.setPropertyString("http-header-fields", rest)
        if (startMs > 5_000) mpv.setPropertyString("start", "${startMs / 1000}")
        mpv.command("loadfile", url, "replace")
    }

    fun addSubtitle(url: String, title: String, lang: String, select: Boolean) {
        runCatching { mpv.command("sub-add", url, if (select) "select" else "auto", title, lang) }
    }

    val positionMs: Long get() = ((runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull() ?: 0.0) * 1000).toLong()
    val durationMs: Long get() = ((runCatching { mpv.getPropertyDouble("duration") }.getOrNull() ?: 0.0) * 1000).toLong()
    val paused: Boolean get() = runCatching { mpv.getPropertyBoolean("pause") }.getOrNull() ?: false
    val buffering: Boolean get() = runCatching { mpv.getPropertyBoolean("paused-for-cache") }.getOrNull() == true ||
        runCatching { mpv.getPropertyBoolean("seeking") }.getOrNull() == true
    val ended: Boolean get() = runCatching { mpv.getPropertyBoolean("eof-reached") }.getOrNull() == true
    val subDelay: Double get() = runCatching { mpv.getPropertyDouble("sub-delay") }.getOrNull() ?: 0.0
    val speed: Double get() = runCatching { mpv.getPropertyDouble("speed") }.getOrNull() ?: 1.0

    fun togglePause() = runCatching { mpv.setPropertyBoolean("pause", !paused) }
    fun pause() = runCatching { mpv.setPropertyBoolean("pause", true) }
    fun seekBy(seconds: Int) = runCatching { mpv.command("seek", "$seconds", "relative") }
    fun seekTo(ms: Long) = runCatching { mpv.command("seek", "${ms / 1000.0}", "absolute") }
    fun setSubDelay(seconds: Double) = runCatching { mpv.setPropertyString("sub-delay", seconds.toString()) }
    fun setSubScale(scale: Double) = runCatching { mpv.setPropertyString("sub-scale", scale.toString()) }
    fun setSpeed(value: Double) = runCatching { mpv.setPropertyString("speed", value.toString()) }

    fun prop(name: String): String? = runCatching { mpv.getPropertyString(name) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** «الترميز»: auto (UTF-8 kept, older files read as Arabic), UTF-8 only, or Arabic forced. */
    fun setCodepage(mode: String) = runCatching {
        mpv.setPropertyString("sub-codepage", when (mode) { "utf8" -> "utf-8"; "arabic" -> "+cp1256"; else -> "cp1256" })
        mpv.command("sub-reload")
    }

    /**
     * «توضيح الحوار» lifts the speech band; «صوت ليلي» tames the loud parts; «تحسين الصوت» evens
     * the level. Audio filters only (cheap next to the video), the same ones as the tablet.
     */
    fun setAudioFx(dialogue: Boolean, night: Boolean, level: Boolean) = runCatching {
        val f = buildList {
            if (dialogue) add("lavfi=[equalizer=f=1800:t=q:w=1.2:g=6]")
            if (night) add("lavfi=[acompressor=threshold=0.1:ratio=4:attack=5:release=200:makeup=2]")
            if (level) add("lavfi=[dynaudnorm=f=500:g=31:p=0.9:m=8]")
        }
        mpv.setPropertyString("af", f.joinToString(","))
    }

    /** «ألوان الصورة»: mpv's own brightness/contrast/saturation/gamma (−100..100), no extra filter. */
    fun setColors(brightness: Int, contrast: Int, saturation: Int, gamma: Int) = runCatching {
        mpv.setPropertyString("brightness", brightness.toString())
        mpv.setPropertyString("contrast", contrast.toString())
        mpv.setPropertyString("saturation", saturation.toString())
        mpv.setPropertyString("gamma", gamma.toString())
    }

    /** «تكرار مقطع»: both ends set → mpv repeats between them; null clears. */
    fun setAbLoop(aMs: Long?, bMs: Long?) = runCatching {
        mpv.setPropertyString("ab-loop-a", aMs?.let { (it / 1000.0).toString() } ?: "no")
        mpv.setPropertyString("ab-loop-b", bMs?.let { (it / 1000.0).toString() } ?: "no")
    }

    /** The subtitle look from the subtitles panel. */
    fun setSubStyle(bold: Boolean, color: String, edge: String, marginY: Int) = runCatching {
        mpv.setPropertyString("sub-bold", if (bold) "yes" else "no")
        mpv.setPropertyString("sub-color", color)
        when (edge) {
            "shadow" -> { mpv.setPropertyString("sub-border-size", "0"); mpv.setPropertyString("sub-shadow-offset", "2.5") }
            "none" -> { mpv.setPropertyString("sub-border-size", "0"); mpv.setPropertyString("sub-shadow-offset", "0") }
            else -> { mpv.setPropertyString("sub-border-size", "2.5"); mpv.setPropertyString("sub-shadow-offset", "1") }
        }
        mpv.setPropertyString("sub-margin-y", marginY.toString())
    }

    /** The subtitle line on screen now (for «حفظ العبارة» and the companion's context). */
    fun subText(): String? = prop("sub-text")?.trim()?.takeIf { it.isNotEmpty() }

    /** «لقطة شاشة» / «من في المشهد»: the current frame (video only) to a file; true when written. */
    fun screenshot(path: String): Boolean = runCatching { mpv.command("screenshot-to-file", path, "video") }.isSuccess && java.io.File(path).let { f ->
        var tries = 0
        while (!f.exists() && tries < 20) { Thread.sleep(100); tries++ }
        f.exists() && f.length() > 0
    }

    /** What «معلومات الملف» shows. */
    fun info(): List<Pair<String, String>> {
        val w = prop("video-params/w"); val h = prop("video-params/h")
        val gamma = prop("video-params/gamma").orEmpty()
        val hdr = when { gamma.contains("pq") -> " · HDR10"; gamma.contains("hlg") -> " · HLG"; else -> "" }
        val fps = runCatching { mpv.getPropertyDouble("container-fps") }.getOrNull()
        val hw = prop("hwdec-current")
        val ch = runCatching { mpv.getPropertyInt("audio-params/channel-count") }.getOrNull()
        return listOfNotNull(
            if (w != null && h != null) "الدقة" to "${w}×${h}$hdr" else null,
            prop("video-codec")?.let { "الفيديو" to (it.substringBefore(" (") + (fps?.let { f -> " · " + String.format(java.util.Locale.US, "%.3f", f) } ?: "")) },
            prop("audio-codec-name")?.let { "الصوت" to (it.uppercase() + (ch?.let { c -> " · " + if (c >= 6) "${c - 1}.1" else "$c.0" } ?: "")) },
            "فك الترميز" to (if (hw.isNullOrBlank() || hw == "no") "المعالج" else "العتاد (أسرع)"),
            prop("file-format")?.let { "الحاوية" to it.uppercase() },
        )
    }

    fun select(track: Track?, type: String) = runCatching {
        mpv.setPropertyString(if (type == "audio") "aid" else "sid", track?.id?.toString() ?: "no")
    }

    fun tracks(): List<Track> {
        val count = runCatching { mpv.getPropertyInt("track-list/count") }.getOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { i ->
            val type = mpv.getPropertyString("track-list/$i/type") ?: return@mapNotNull null
            if (type != "audio" && type != "sub") return@mapNotNull null
            val id = mpv.getPropertyInt("track-list/$i/id") ?: return@mapNotNull null
            val lang = mpv.getPropertyString("track-list/$i/lang").orEmpty()
            val title = mpv.getPropertyString("track-list/$i/title").orEmpty()
            val codec = mpv.getPropertyString("track-list/$i/codec").orEmpty()
            val channels = mpv.getPropertyInt("track-list/$i/demux-channel-count")
            val external = mpv.getPropertyBoolean("track-list/$i/external") == true
            val label = buildString {
                append(if (lang.isNotBlank()) com.mawj.tv.data.Addons.languageName(lang) else "مسار $id")
                if (title.isNotBlank() && title != lang) append(" — ").append(title)
                if (type == "audio") {
                    if (codec.isNotBlank()) append(" · ").append(codec.uppercase())
                    channels?.let { append(" · ").append(if (it >= 6) "${it - 1}.1" else "$it.0") }
                }
                if (external) append(" · خارجية")
            }
            Track(id, if (type == "sub") "sub" else "audio", label, lang, mpv.getPropertyBoolean("track-list/$i/selected") == true, title, external)
        }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { holder.removeCallback(this) }
        runCatching { mpv.destroy() }
    }
}

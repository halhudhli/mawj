package com.mawj.tv.player

import android.content.Context
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.Utils

/** A track the viewer can pick (audio or subtitles). */
data class Track(val id: Int, val type: String, val label: String, val lang: String, val selected: Boolean)

/**
 * The video surface: libmpv with hardware decoding and settings tuned for TVs (fast start, a
 * read-ahead that rides out network dips, Arabic subtitles from the system fonts).
 */
class MpvView(context: Context) : BaseMPVView(context, null) {
    @Volatile private var released = false

    fun start() {
        runCatching { Utils.copyAssets(context) }
        initialize(context.filesDir.path, context.cacheDir.path)
    }

    override fun initOptions() {
        setVo("gpu")
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("hwdec", "mediacodec-copy,mediacodec,no")
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
            Track(id, if (type == "sub") "sub" else "audio", label, lang, mpv.getPropertyBoolean("track-list/$i/selected") == true)
        }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { holder.removeCallback(this) }
        runCatching { mpv.destroy() }
    }
}

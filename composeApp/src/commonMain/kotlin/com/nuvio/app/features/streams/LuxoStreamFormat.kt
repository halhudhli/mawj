package com.nuvio.app.features.streams

/**
 * LUXO: turns the free-form stream text of any add-on into four tidy lines:
 *  1. resolution | Cloud/P2P | ⚡ Cached • source | service
 *  2. release type 📊 bitrate codec 🔆 HDR / DV / bit depth
 *  3. audio 🔊 channels ⏱️ duration
 *  4. size (highlighted) + language flags
 * Returns null when too little could be read, so the card keeps the original text.
 */
internal object LuxoStreamFormat {
    data class Lines(val top: String, val video: String?, val audio: String?, val size: String?, val flags: String?)

    private val RES = Regex("""\b(4320p|2160p|1440p|1080p|720p|576p|480p|360p)\b|\b(4K|UHD|8K)\b""", RegexOption.IGNORE_CASE)
    private val SOURCE = Regex("""\b(BluRay REMUX|REMUX|BluRay|BDRip|BRRip|WEB-DL|WEBDL|WEBRip|WEB|HDTV|DVDRip|DVD|HDRip|CAM|TS)\b""", RegexOption.IGNORE_CASE)
    private val CODEC = Regex("""\b(HEVC|x265|H\.?265|AVC|x264|H\.?264|AV1|VP9)\b""", RegexOption.IGNORE_CASE)
    private val HDR = Regex("""\b(DV|Dolby ?Vision|HDR10\+|HDR10|HDR|HLG|10bit|10-bit|12bit|SDR)\b""", RegexOption.IGNORE_CASE)
    private val AUDIO = Regex("""\b(Atmos|TrueHD|DTS[- ]?HD(?: MA)?|DTS[- ]?X|DTS|DD\+|DDP|E-?AC-?3|AC-?3|DD|AAC|FLAC|Opus|MP3)\b""", RegexOption.IGNORE_CASE)
    private val CHANNELS = Regex("""\b(7\.1|5\.1|2\.0|stereo|mono)\b""", RegexOption.IGNORE_CASE)
    private val SIZE = Regex("""(\d+(?:[.,]\d+)?)\s*(TB|GB|GiB|MB|MiB)\b""", RegexOption.IGNORE_CASE)
    private val BITRATE = Regex("""(\d+(?:\.\d+)?)\s*(Mbps|Mb/s|kbps)""", RegexOption.IGNORE_CASE)
    private val DURATION = Regex("""\b(\d{1,2})h[: ]?(\d{1,2})m(?:[: ]?(\d{1,2})s)?\b""", RegexOption.IGNORE_CASE)
    private val SERVICE = Regex("""\b(TorBox|Real-?Debrid|RD|AllDebrid|AD|Premiumize|PM|Debrid-?Link|EasyDebrid|Offcloud)\b""", RegexOption.IGNORE_CASE)
    private val FLAG = Regex("""[\x{1F1E6}-\x{1F1FF}]{2}""")

    fun lines(stream: StreamItem): Lines? {
        val text = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints.filename)
            .joinToString("\n")
        val res = RES.find(text)?.value?.let { if (it.equals("4K", true) || it.equals("UHD", true)) "2160p" else it }
        val source = SOURCE.find(text)?.value
        val codec = CODEC.find(text)?.value?.uppercase()?.replace("X265", "HEVC")?.replace("X264", "AVC")
        val hdr = HDR.findAll(text).map { it.value.replace("Dolby Vision", "DV", true) }.distinctBy { it.lowercase() }.toList()
        val audio = AUDIO.findAll(text).map { it.value.replace("DDP", "DD+", true) }.distinctBy { it.lowercase() }.toList()
        val channels = CHANNELS.find(text)?.value
        val size = stream.behaviorHints.videoSize?.let(::formatBytes) ?: SIZE.find(text)?.value
        val bitrate = BITRATE.find(text)?.value
        val duration = DURATION.find(text)?.value
        val service = SERVICE.find(text)?.value
        val cached = when {
            Regex("""⚡|\bcached\b|instant""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "⚡ Cached"
            Regex("""⏳|uncached|not cached""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "⏳ Not cached"
            else -> null
        }
        val cloud = when {
            service != null -> "Cloud"
            Regex("""\bP2P\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "P2P"
            else -> null
        }
        val flags = FLAG.findAll(text).map { it.value }.distinct().joinToString(" ").ifBlank { null }

        val known = listOfNotNull(res, source, codec, size, audio.firstOrNull()).size
        if (known < 2) return null

        val origin = listOfNotNull(cached, stream.addonName.takeIf { it.isNotBlank() }).joinToString(" • ").ifBlank { null }
        val top = listOfNotNull(res, cloud, origin, service).joinToString(" | ")
        val video = listOfNotNull(
            source,
            bitrate?.let { "📊 $it" },
            codec,
            hdr.takeIf { it.isNotEmpty() }?.joinToString(" | ")?.let { "🔆 $it" },
        ).joinToString(" ").ifBlank { null }
        val audioLine = listOfNotNull(
            audio.takeIf { it.isNotEmpty() }?.joinToString(" | "),
            channels?.let { "🔊 $it" },
            duration?.let { "⏱️ $it" },
        ).joinToString(" ").ifBlank { null }
        return Lines(top = top.ifBlank { stream.streamLabel }, video = video, audio = audioLine, size = size?.let { "💾 $it" }, flags = flags)
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1) "${(gb * 10).toLong() / 10.0} GB" else "${bytes / 1_048_576} MB"
    }
}

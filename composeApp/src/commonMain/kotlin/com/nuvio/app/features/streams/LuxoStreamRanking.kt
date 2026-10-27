package com.nuvio.app.features.streams

/**
 * LUXO: orders each add-on's streams best-first and can drop cam/telesync copies.
 * The score is simple and readable: resolution, cached on a debrid service, a clean source,
 * HDR and lossless audio count up; uncached and cam releases count down.
 */
internal object LuxoStreamRanking {
    private val CAM = Regex("""\b(CAM|CAMRIP|HDCAM|TS|TELESYNC|HDTS|TC|TELECINE|SCR|SCREENER)\b""", RegexOption.IGNORE_CASE)

    private fun text(stream: StreamItem): String =
        listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints.filename).joinToString(" ")

    fun isCam(stream: StreamItem): Boolean = CAM.containsMatchIn(text(stream))

    fun score(stream: StreamItem): Int {
        val t = text(stream)
        var score = 0
        score += when {
            Regex("""2160p|\b4K\b|\bUHD\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 40
            Regex("""1080p""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 30
            Regex("""720p""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 18
            Regex("""480p|576p""", RegexOption.IGNORE_CASE).containsMatchIn(t) -> 8
            else -> 12
        }
        if (Regex("""⚡|\bcached\b|instant""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 25
        if (Regex("""⏳|uncached|not cached""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score -= 12
        if (Regex("""REMUX""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 9
        else if (Regex("""BluRay|BDRip""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 7
        else if (Regex("""WEB-?DL""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 6
        else if (Regex("""WEBRip|\bWEB\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 4
        if (Regex("""\bDV\b|Dolby ?Vision|HDR10\+|HDR""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 4
        if (Regex("""Atmos|TrueHD|DTS-?HD""", RegexOption.IGNORE_CASE).containsMatchIn(t)) score += 3
        if (CAM.containsMatchIn(t)) score -= 100
        return score
    }

    fun apply(groups: List<AddonStreamGroup>, rank: Boolean, hideCam: Boolean): List<AddonStreamGroup> =
        groups.map { group ->
            var streams = group.streams
            if (hideCam) {
                val clean = streams.filterNot(::isCam)
                // Never empty a group completely: if everything is cam, keep what there is.
                if (clean.isNotEmpty()) streams = clean
            }
            if (rank) streams = streams.withIndex().sortedWith(compareByDescending<IndexedValue<StreamItem>> { score(it.value) }.thenBy { it.index }).map { it.value }
            group.copy(streams = streams)
        }
}

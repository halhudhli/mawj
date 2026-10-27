package com.nuvio.app.features.luxo

/**
 * Asks the image host for its sharpest copy. TMDB serves "original" (often 2000×3000 for posters,
 * up to 4K for backdrops); MetaHub has a "large" size. Other hosts are left untouched.
 */
internal fun luxoHighResImage(url: String?, maxBackdrop1080: Boolean = false): String? {
    if (url.isNullOrBlank()) return url
    val tmdb = Regex("""(image\.tmdb\.org/t/p/)(w\d+|original)(/)""")
    if (tmdb.containsMatchIn(url)) {
        return url.replace(tmdb) { m -> m.groupValues[1] + (if (maxBackdrop1080) "w1280" else "original") + m.groupValues[3] }
    }
    val metahub = Regex("""(images\.metahub\.space/(?:poster|background)/)(small|medium)(/)""")
    if (metahub.containsMatchIn(url)) {
        return url.replace(metahub) { m -> m.groupValues[1] + "large" + m.groupValues[3] }
    }
    return url
}

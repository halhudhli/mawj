package com.nuvio.app.core.format

import androidx.compose.ui.text.intl.Locale
import com.nuvio.app.core.time.parseEpisodeReleaseLocalDate

fun formatReleaseDateForDisplay(raw: String): String = formatReleaseDate(raw, includeYear = true)

fun formatReleaseDateWithoutYear(raw: String): String = formatReleaseDate(raw, includeYear = false)

internal fun formatReleaseDate(
    raw: String,
    includeYear: Boolean,
    localeTag: String = Locale.current.toLanguageTag(),
): String {
    val date = parseEpisodeReleaseLocalDate(raw) ?: return com.nuvio.app.features.luxo.LuxoDateStyle.digits(raw)
    val style = com.nuvio.app.features.luxo.LuxoDateStyle
    val text = if (style.hijri.value) {
        style.formatHijri(date, includeYear, arabic = localeTag.startsWith("ar", ignoreCase = true))
            ?: formatCalendarDate(date, localeTag, includeYear)
    } else {
        formatCalendarDate(date, localeTag, includeYear)
    }
    return style.digits(text)
}

internal expect fun formatCalendarDate(isoDate: String, localeTag: String, includeYear: Boolean): String

/**
 * Fork: picks the stored release info, then the hydrated one, then [fallback], and formats it.
 * Used by Library and Profile Insight cards.
 */
internal fun resolveReleaseInfoForDisplay(
    stored: String?,
    hydrated: String?,
    fallback: String,
): String = formatReleaseDateForDisplay(
    stored?.trim()?.takeIf { it.isNotBlank() }
        ?: hydrated?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback,
)

/**
 * Parses a release/air string (ISO date, year-only, or timestamp prefix) for compact UI (e.g. year chips).
 */
fun extractReleaseYearForDisplay(raw: String): Int? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    if (t.length == 4 && t.all { it.isDigit() }) {
        return t.toIntOrNull()?.takeIf { it in 1000..9999 }
    }
    val datePart = parseEpisodeReleaseLocalDate(t) ?: return null
    val yearStr = datePart.split('-').firstOrNull() ?: return null
    return yearStr.toIntOrNull()?.takeIf { it in 1000..9999 }
}

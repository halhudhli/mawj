package com.nuvio.app.features.luxo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Luxo: optional Hijri dates and Arabic-Indic digits (٠١٢٣) for release/air dates.
 */
internal object LuxoDateStyle {
    private const val KEY_HIJRI = "date_hijri"
    private const val KEY_ARABIC_DIGITS = "date_arabic_digits"

    private val _hijri = MutableStateFlow(load(KEY_HIJRI))
    val hijri: StateFlow<Boolean> = _hijri.asStateFlow()

    private val _arabicDigits = MutableStateFlow(load(KEY_ARABIC_DIGITS))
    val arabicDigits: StateFlow<Boolean> = _arabicDigits.asStateFlow()

    fun setHijri(enabled: Boolean) {
        _hijri.value = enabled
        runCatching { LuxoPrefsStorage.save(KEY_HIJRI, enabled.toString()) }
    }

    fun setArabicDigits(enabled: Boolean) {
        _arabicDigits.value = enabled
        runCatching { LuxoPrefsStorage.save(KEY_ARABIC_DIGITS, enabled.toString()) }
    }

    private fun load(key: String): Boolean =
        runCatching { LuxoPrefsStorage.load(key)?.toBooleanStrictOrNull() }.getOrNull() ?: false

    private val hijriMonthsAr = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val hijriMonthsEn = listOf(
        "Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah",
    )

    /** Formats an ISO yyyy-MM-dd date as Hijri, or returns null when it can't be parsed. */
    fun formatHijri(isoDate: String, includeYear: Boolean, arabic: Boolean): String? {
        val parts = isoDate.take(10).split('-')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        val (hy, hm, hd) = gregorianToHijri(y, m, d)
        val month = (if (arabic) hijriMonthsAr else hijriMonthsEn)[(hm - 1).coerceIn(0, 11)]
        val suffix = if (arabic) "هـ" else "AH"
        return if (includeYear) "$hd $month $hy $suffix" else "$hd $month"
    }

    /** Applies the user's digit preference to any display string. */
    fun digits(text: String): String {
        if (!_arabicDigits.value) return text
        return buildString(text.length) {
            text.forEach { c -> append(if (c in '0'..'9') ('٠' + (c - '0')) else c) }
        }
    }

    /** Arithmetical (tabular) Islamic calendar; may differ from Umm al-Qura by about a day. */
    private fun gregorianToHijri(year: Int, month: Int, day: Int): Triple<Int, Int, Int> {
        // Gregorian -> Julian Day Number.
        val a = (14 - month) / 12
        val yy = year + 4800 - a
        val mm = month + 12 * a - 3
        val jdn = day + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
        // Julian Day Number -> tabular Hijri.
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l = l - 10631 * n + 354
        val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
        val hMonth = (24 * l) / 709
        val hDay = l - (709 * hMonth) / 24
        val hYear = 30 * n + j - 30
        return Triple(hYear, hMonth, hDay)
    }
}

package com.nuvio.app.features.luxo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** How far ahead the countdown looks: 7 days up to 6 months (TV light mode stops at 60 days). */
object LuxoCountdownRange {
    val options = listOf(7, 14, 30, 60, 90, 180)
    private const val KEY = "countdown_range_days"
    var days by mutableIntStateOf(LuxoPrefsStorage.load(KEY)?.toIntOrNull()?.takeIf { it in options } ?: 30)
        private set

    fun set(value: Int) {
        days = value
        LuxoPrefsStorage.save(KEY, value.toString())
    }

    /** What is actually loaded on this device. */
    fun effective(): Int = if (luxoLiteMode()) days.coerceAtMost(60) else days

    fun label(d: Int): String = when (d) {
        7 -> lx("أسبوع", "1 week")
        14 -> lx("أسبوعين", "2 weeks")
        30 -> lx("شهر", "1 month")
        60 -> lx("شهرين", "2 months")
        90 -> lx("٣ شهور", "3 months")
        180 -> lx("٦ شهور", "6 months")
        else -> "$d"
    }
}

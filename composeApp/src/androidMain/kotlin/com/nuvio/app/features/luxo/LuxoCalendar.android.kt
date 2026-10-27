package com.nuvio.app.features.luxo

import android.content.Intent
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

internal actual fun luxoAddToCalendar(title: String, dateIso: String, description: String): Boolean {
    val context = LuxoPrefsStorage.appContext ?: return false
    val parts = dateIso.take(10).split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return false
    // All-day events are expressed in UTC midnight-to-midnight.
    val start = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
    }.timeInMillis
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 24L * 60 * 60 * 1000)
        .putExtra(CalendarContract.Events.HAS_ALARM, 1)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

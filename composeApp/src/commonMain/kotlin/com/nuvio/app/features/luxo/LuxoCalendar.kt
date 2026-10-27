package com.nuvio.app.features.luxo

/**
 * Luxo: opens the device calendar with an all-day event on [dateIso] (yyyy-MM-dd) so an episode's
 * air date can be saved with a reminder. Returns false when no calendar app could be opened.
 */
internal expect fun luxoAddToCalendar(title: String, dateIso: String, description: String): Boolean

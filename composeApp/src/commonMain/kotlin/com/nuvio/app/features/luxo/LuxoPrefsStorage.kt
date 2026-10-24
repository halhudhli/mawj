package com.nuvio.app.features.luxo

/**
 * Small device-wide key/value store for Luxo's own customization settings
 * (splash screen, fonts, navigation bar). Values are JSON payloads.
 */
internal expect object LuxoPrefsStorage {
    fun load(key: String): String?
    fun save(key: String, value: String)
}

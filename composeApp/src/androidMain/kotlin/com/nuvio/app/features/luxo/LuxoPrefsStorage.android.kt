package com.nuvio.app.features.luxo

import android.content.Context
import android.content.SharedPreferences

internal actual object LuxoPrefsStorage {
    private const val preferencesName = "luxo_customization"
    private var preferences: SharedPreferences? = null

    /** App-private folder for Luxo's cached files (e.g. fonts). */
    internal var filesDir: java.io.File? = null
        private set

    internal var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (filesDir == null) filesDir = context.applicationContext.filesDir
        if (preferences == null) {
            preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }
    }

    actual fun load(key: String): String? = preferences?.getString(key, null)

    actual fun save(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }

    actual fun all(): Map<String, String> =
        preferences?.all.orEmpty().mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
}

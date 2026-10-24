package com.nuvio.app.features.luxo

import android.content.Context
import android.content.SharedPreferences

internal actual object LuxoPrefsStorage {
    private const val preferencesName = "luxo_customization"
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (preferences == null) {
            preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }
    }

    actual fun load(key: String): String? = preferences?.getString(key, null)

    actual fun save(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }
}

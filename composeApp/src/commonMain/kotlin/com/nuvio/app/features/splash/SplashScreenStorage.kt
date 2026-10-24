package com.nuvio.app.features.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.app.core.storage.PlatformDataStorePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SplashScreenStorage {
    private val BACKGROUND_STYLE_KEY = stringPreferencesKey("splash_background_style")
    private val BACKGROUND_COLOR_KEY = stringPreferencesKey("splash_background_color")

    private val dataStore: DataStore<Preferences> by lazy {
        PlatformDataStorePath.datastore
    }

    val backgroundStyle: Flow<SplashBackgroundStyle> = dataStore.data.map { prefs ->
        val style = prefs[BACKGROUND_STYLE_KEY] ?: SplashBackgroundStyle.STARS.name
        try {
            SplashBackgroundStyle.valueOf(style)
        } catch (e: Exception) {
            SplashBackgroundStyle.STARS
        }
    }

    val backgroundColor: Flow<Long> = dataStore.data.map { prefs ->
        prefs[BACKGROUND_COLOR_KEY]?.toLongOrNull() ?: 0xFF000000L
    }

    suspend fun setBackgroundStyle(style: SplashBackgroundStyle) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_STYLE_KEY] = style.name
        }
    }

    suspend fun setBackgroundColor(color: Long) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR_KEY] = color.toString()
        }
    }
}

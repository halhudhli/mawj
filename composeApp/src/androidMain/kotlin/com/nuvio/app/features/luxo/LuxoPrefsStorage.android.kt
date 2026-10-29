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
            installCrashRecorder()
        }
    }

    /** Saves the stack trace of a crash so the next launch can show it (and it can be sent to us). */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(error).take(6000)
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.SUPPORTED_ABIS.joinToString()}"
                preferences?.edit()?.putString("last_crash", "$device\n\n$trace")?.commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    actual fun load(key: String): String? = preferences?.getString(key, null)

    actual fun save(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }

    actual fun all(): Map<String, String> =
        preferences?.all.orEmpty().mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
}

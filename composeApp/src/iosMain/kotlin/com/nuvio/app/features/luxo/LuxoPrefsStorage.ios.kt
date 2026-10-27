package com.nuvio.app.features.luxo

import platform.Foundation.NSUserDefaults

internal actual object LuxoPrefsStorage {
    private const val prefix = "luxo_customization_"

    actual fun load(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(prefix + key)

    actual fun save(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = prefix + key)
    }

    actual fun all(): Map<String, String> =
        NSUserDefaults.standardUserDefaults.dictionaryRepresentation().entries.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            if (!name.startsWith(prefix)) return@mapNotNull null
            (value as? String)?.let { name.removePrefix(prefix) to it }
        }.toMap()
}

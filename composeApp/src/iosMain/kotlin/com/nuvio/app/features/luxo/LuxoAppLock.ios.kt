package com.nuvio.app.features.luxo

internal actual fun luxoDeviceUnlockAvailable(): Boolean = false

internal actual suspend fun luxoDeviceUnlock(title: String, subtitle: String): Boolean = true

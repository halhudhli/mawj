package com.nuvio.app.features.luxo

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager

private var cachedIsTv: Boolean? = null

actual fun luxoIsTv(): Boolean {
    cachedIsTv?.let { return it }
    // The TV APK always runs in TV mode, even on boxes that don't report themselves as a TV.
    if (com.nuvio.app.features.updater.LuxoTvBuildConfig.TV_BUILD) {
        cachedIsTv = true
        return true
    }
    val context = LuxoPrefsStorage.appContext ?: return false
    val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    val tv = uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    cachedIsTv = tv
    return tv
}

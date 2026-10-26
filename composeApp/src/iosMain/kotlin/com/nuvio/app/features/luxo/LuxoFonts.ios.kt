package com.nuvio.app.features.luxo

import androidx.compose.ui.text.font.FontFamily

// Imported app fonts and per-script mixing are Android-only for now; iOS keeps the bundled fonts.
internal actual fun luxoFontFamilyFromFile(path: String): FontFamily? = null

internal actual fun luxoScriptFontsSupported(): Boolean = false

internal actual fun luxoFontCachePath(name: String): String? = null

internal actual fun luxoWriteFontCache(name: String, bytes: ByteArray): String? = null

internal actual fun luxoBuildScriptFontFamily(latin: LuxoFontFiles, arabic: LuxoFontFiles?): FontFamily? = null

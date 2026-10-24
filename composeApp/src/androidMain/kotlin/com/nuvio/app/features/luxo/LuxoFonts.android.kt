package com.nuvio.app.features.luxo

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

internal actual fun luxoFontFamilyFromFile(path: String): FontFamily? {
    val file = File(path)
    if (!file.isFile) return null
    return FontFamily(Font(file))
}

package com.nuvio.app.features.luxo

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

internal actual fun luxoFontFamilyFromFile(path: String): FontFamily? {
    val file = File(path)
    if (!file.isFile) return null
    return FontFamily(Font(file))
}

internal actual fun luxoScriptFontsSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

private fun fontCacheDir(): File? = LuxoPrefsStorage.filesDir?.resolve("luxo_fonts")?.apply { mkdirs() }

internal actual fun luxoFontCachePath(name: String): String? =
    fontCacheDir()?.resolve(name)?.takeIf { it.isFile && it.length() > 0 }?.absolutePath

internal actual fun luxoWriteFontCache(name: String, bytes: ByteArray): String? = runCatching {
    val target = fontCacheDir()?.resolve(name) ?: return null
    val temp = File(target.parentFile, "$name.tmp")
    temp.writeBytes(bytes)
    if (!temp.renameTo(target)) {
        temp.delete()
        return null
    }
    target.absolutePath
}.getOrNull()

internal actual fun luxoBuildScriptFontFamily(latin: LuxoFontFiles, arabic: LuxoFontFiles?): FontFamily? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return ScriptFonts.build(latin, arabic)
}

@RequiresApi(Build.VERSION_CODES.Q)
private object ScriptFonts {
    fun build(latin: LuxoFontFiles, arabic: LuxoFontFiles?): FontFamily {
        val weights = listOf(
            FontWeight.Light to 300,
            FontWeight.Normal to 400,
            FontWeight.Medium to 500,
            FontWeight.SemiBold to 600,
            FontWeight.Bold to 700,
            FontWeight.ExtraBold to 800,
        )
        val fonts = weights.map { (composeWeight, platformWeight) ->
            PrebuiltTypefaceFont(typeface(latin, arabic, platformWeight), composeWeight)
        }
        return FontFamily(fonts)
    }

    private fun typeface(latin: LuxoFontFiles, arabic: LuxoFontFiles?, weight: Int): android.graphics.Typeface {
        val builder = android.graphics.Typeface.CustomFallbackBuilder(platformFamily(latin))
        arabic?.let { builder.addCustomFallback(platformFamily(it)) }
        builder.setSystemFallback("sans-serif")
        builder.setStyle(android.graphics.fonts.FontStyle(weight, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT))
        return builder.build()
    }

    private fun platformFamily(files: LuxoFontFiles): android.graphics.fonts.FontFamily {
        val regular = android.graphics.fonts.Font.Builder(File(files.regular)).setWeight(400).build()
        val family = android.graphics.fonts.FontFamily.Builder(regular)
        files.bold?.let { bold ->
            runCatching {
                family.addFont(android.graphics.fonts.Font.Builder(File(bold)).setWeight(700).build())
            }
        }
        return family.build()
    }
}

/** A Compose font backed by a Typeface that is already built (with its script fallbacks). */
private class PrebuiltTypefaceFont(
    val typeface: android.graphics.Typeface,
    override val weight: FontWeight,
) : AndroidFont(FontLoadingStrategy.Blocking, PrebuiltTypefaceLoader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
}

private object PrebuiltTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): android.graphics.Typeface? =
        (font as? PrebuiltTypefaceFont)?.typeface

    override suspend fun awaitLoad(context: Context, font: AndroidFont): android.graphics.Typeface? =
        loadBlocking(context, font)
}

package com.mawj.tv.ui

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
import com.mawj.tv.MawjTv
import com.mawj.tv.R

/**
 * The tablet's fonts: Thmanyah for Arabic letters and Barlow for Latin letters and numbers.
 * Android 10+ joins them into one family (each letter picks its script's font by itself). Older
 * TVs get Thmanyah for everything, and numbers still use [num] where they are drawn.
 */
object Fonts {
    val num: FontFamily = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_regular, FontWeight.Medium),
        Font(R.font.barlow_bold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

    val serif: FontFamily = FontFamily(Font(R.font.thmanyah, FontWeight.Normal), Font(R.font.thmanyah, FontWeight.Bold))

    val ui: FontFamily by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { Joined.build(MawjTv.app) }.getOrNull() ?: serif else serif
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Joined {
        fun build(context: Context): FontFamily {
            val weights = listOf(FontWeight.Normal to 400, FontWeight.Medium to 500, FontWeight.SemiBold to 600, FontWeight.Bold to 700)
            return FontFamily(weights.map { (w, p) -> Prebuilt(typeface(context, p), w) })
        }

        private fun typeface(context: Context, weight: Int): android.graphics.Typeface {
            val res = context.resources
            val latin = android.graphics.fonts.FontFamily.Builder(
                android.graphics.fonts.Font.Builder(res, R.font.barlow_regular).setWeight(400).build(),
            ).addFont(android.graphics.fonts.Font.Builder(res, R.font.barlow_bold).setWeight(700).build()).build()
            val arabic = android.graphics.fonts.FontFamily.Builder(
                android.graphics.fonts.Font.Builder(res, R.font.thmanyah).setWeight(400).build(),
            ).build()
            return android.graphics.Typeface.CustomFallbackBuilder(latin)
                .addCustomFallback(arabic)
                .setSystemFallback("sans-serif")
                .setStyle(android.graphics.fonts.FontStyle(weight, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT))
                .build()
        }
    }

    /** A Compose font backed by a Typeface already built with its script fallbacks. */
    private class Prebuilt(val typeface: android.graphics.Typeface, override val weight: FontWeight) :
        AndroidFont(FontLoadingStrategy.Blocking, Loader, FontVariation.Settings()) {
        override val style: FontStyle = FontStyle.Normal
    }

    private object Loader : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont) = (font as? Prebuilt)?.typeface
        override suspend fun awaitLoad(context: Context, font: AndroidFont) = loadBlocking(context, font)
    }
}

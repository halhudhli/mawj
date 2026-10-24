package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.intl.Locale

/** Luxo-only screens are bilingual: Arabic when the app runs in Arabic, English otherwise. */
@Composable
@ReadOnlyComposable
internal fun lx(ar: String, en: String): String =
    if (Locale.current.language == "ar") ar else en

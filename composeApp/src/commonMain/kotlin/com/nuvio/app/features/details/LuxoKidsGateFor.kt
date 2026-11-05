package com.nuvio.app.features.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.luxo.LuxoKids
import com.nuvio.app.features.luxo.LuxoKidsGate
import com.nuvio.app.features.luxo.LuxoTmdb

/** «ملف الأطفال» over the details page (finds the title on TMDB first). */
@Composable
internal fun LuxoKidsGateFor(meta: MetaDetails) {
    if (!LuxoKids.isOn()) return
    var found by remember(meta.id) { mutableStateOf<Pair<String?, String?>?>(null) }
    LaunchedEffect(meta.id) {
        val b = runCatching { LuxoTmdb.media(meta.id, meta.type, meta.imdbId) }.getOrNull()
        found = b?.tmdbId to b?.mediaType
    }
    val f = found
    LuxoKidsGate(meta.id, f?.first, f?.second, loading = f == null)
}

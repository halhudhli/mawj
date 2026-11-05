package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.nuvio.app.features.luxo.LuxoNowSubtitle
import com.nuvio.app.features.luxo.LuxoQuote
import com.nuvio.app.features.luxo.LuxoQuotes
import com.nuvio.app.features.luxo.LuxoRemote
import com.nuvio.app.features.luxo.luxoIsTv
import com.nuvio.app.features.luxo.luxoNowMs
import com.nuvio.app.features.luxo.luxoRecordGif
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Player menu entries: save the line on screen, record a GIF, continue on the TV. */
@Composable
internal fun LuxoIdeas4PlayerHooks(title: String, season: Int?, episode: Int?, positionMs: Long, metaId: String, metaType: String) {
    val features = rememberLuxoFeatures()
    val scope = rememberCoroutineScope()
    val pos by rememberUpdatedState(positionMs)
    fun toast(t: String) {
        scope.launch { LuxoPlayerBridge.toast = t; delay(2_500L); if (LuxoPlayerBridge.toast == t) LuxoPlayerBridge.toast = null }
    }
    DisposableEffect(features.quotes, features.gifClip, features.remote, metaId) {
        LuxoPlayerBridge.onSaveQuoteClick = if (features.quotes) ({
            val line = LuxoNowSubtitle.text.trim()
            if (line.isBlank()) toast("لا توجد عبارة ظاهرة الآن")
            else { LuxoQuotes.add(LuxoQuote(title, line, season, episode, pos, luxoNowMs())); toast("حُفظت العبارة 💬") }
        }) else null
        LuxoPlayerBridge.onGifClick = if (features.gifClip) ({
            scope.launch {
                LuxoPlayerBridge.gifLabel = "جارٍ التسجيل…"
                val ok = luxoRecordGif(title, 4) { p -> LuxoPlayerBridge.gifLabel = "جارٍ التسجيل… ${(p * 100).toInt()}٪" }
                LuxoPlayerBridge.gifLabel = "مقطع متحرك (4 ثوانٍ)"
                toast(if (ok) "حُفظ المقطع المتحرك في الصور ← Mawj 🎞️" else "تعذّر تسجيل المقطع (بعض المصادر محمية)")
            }
        }) else null
        LuxoPlayerBridge.onContinueOnTvClick = if (features.remote && !luxoIsTv() && LuxoRemote.target != null && metaId.isNotBlank()) ({
            scope.launch {
                val url = com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl(if (metaType == "movie") "movie" else "series", metaId)
                val ok = LuxoRemote.send(buildJsonObject { put("t", "open"); put("url", url) })
                toast(if (ok) "فُتح العمل على الشاشة 📺" else "تعذّر الوصول إلى الشاشة")
            }
        }) else null
        onDispose {
            LuxoPlayerBridge.onSaveQuoteClick = null
            LuxoPlayerBridge.onGifClick = null
            LuxoPlayerBridge.onContinueOnTvClick = null
        }
    }
}

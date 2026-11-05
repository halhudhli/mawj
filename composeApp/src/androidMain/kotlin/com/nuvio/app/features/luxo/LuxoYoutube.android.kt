package com.nuvio.app.features.luxo

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

actual fun luxoOpenYoutubeApp(): Boolean = runCatching {
    val context = LuxoPrefsStorage.appContext ?: return@runCatching false
    val pm = context.packageManager
    val pkg = listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.google.android.youtube")
        .firstOrNull { p -> runCatching { pm.getLaunchIntentForPackage(p) != null || pm.getLeanbackLaunchIntentForPackage(p) != null }.getOrDefault(false) }
        ?: return@runCatching false
    val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return@runCatching false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)

private fun isYoutubeHost(host: String): Boolean =
    host.endsWith("youtube.com") || host.endsWith("youtu.be") || host.endsWith("google.com") ||
        host.endsWith("googlevideo.com") || host.endsWith("gstatic.com") || host.endsWith("ytimg.com") ||
        host.endsWith("youtube-nocookie.com") || host.endsWith("googleusercontent.com") || host.endsWith("accounts.google.com")

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun LuxoYoutubeWeb(modifier: Modifier, onBackHandler: ((() -> Boolean)) -> Unit) {
    val holder = remember { arrayOfNulls<WebView>(1) }
    val fullscreen = remember { arrayOfNulls<Pair<View, WebChromeClient.CustomViewCallback>>(1) }
    DisposableEffect(Unit) {
        onBackHandler {
            val fs = fullscreen[0]
            val w = holder[0]
            when {
                fs != null -> { runCatching { fs.second.onCustomViewHidden() }; true }
                w != null && w.canGoBack() -> { w.goBack(); true }
                else -> false
            }
        }
        onDispose {
            holder[0]?.let { w -> runCatching { w.stopLoading(); w.loadUrl("about:blank"); w.destroy() } }
            holder[0] = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val frame = FrameLayout(context)
            val web = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        fullscreen[0]?.let { runCatching { it.second.onCustomViewHidden() } }
                        fullscreen[0] = view to callback
                        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        this@apply.visibility = View.INVISIBLE
                    }

                    override fun onHideCustomView() {
                        fullscreen[0]?.let { frame.removeView(it.first) }
                        fullscreen[0] = null
                        this@apply.visibility = View.VISIBLE
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        val scheme = uri.scheme.orEmpty()
                        if (scheme == "http" || scheme == "https") {
                            if (isYoutubeHost(uri.host.orEmpty())) return false
                        }
                        // Links that leave YouTube open outside the app.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                }
                holder[0] = this
                loadUrl("https://m.youtube.com/")
            }
            frame.addView(web)
            frame
        },
    )
}

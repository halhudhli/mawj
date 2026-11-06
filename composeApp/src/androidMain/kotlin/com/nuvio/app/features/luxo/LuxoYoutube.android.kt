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

private fun launchFirst(packages: List<String>): Boolean = runCatching {
    val context = LuxoPrefsStorage.appContext ?: return@runCatching false
    val pm = context.packageManager
    for (p in packages) {
        val intent = runCatching { pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p) }.getOrNull() ?: continue
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return@runCatching true
    }
    false
}.getOrDefault(false)

actual fun luxoOpenYoutubeAdFree(): Boolean =
    if (luxoIsTv()) launchFirst(listOf("com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv", "com.teamsmart.videomanager.tv"))
    else launchFirst(listOf("app.revanced.android.youtube", "app.rvx.android.youtube", "app.revanced.android.apps.youtube"))

// ---- ad blocking for the in-app page
private val AD_HOSTS = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "google-analytics.com", "googletagservices.com", "googletagmanager.com", "imasdk.googleapis.com",
)
private val AD_PATHS = listOf("/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_", "/ad_break", "/youtubei/v1/log_event", "/api/stats/qoe?adformat", "ad_type=")

private fun isAdRequest(url: android.net.Uri): Boolean {
    val host = url.host.orEmpty()
    if (AD_HOSTS.any { host == it || host.endsWith(".$it") }) return true
    val s = url.toString()
    return AD_PATHS.any { it in s }
}

private const val AD_SKIP_JS = """
(function(){
  if (window.__mawjAds) return; window.__mawjAds = true;
  var css = document.createElement('style');
  css.textContent = 'ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-video-renderer,ytm-ad-slot-renderer,ad-slot-renderer,ytd-ad-slot-renderer,ytm-banner-promo-renderer,#player-ads,.ytp-ad-overlay-container,.ytp-ad-module .ytp-ad-overlay-slot,ytm-statement-banner-renderer,ytm-mealbar-promo-renderer{display:none!important}';
  (document.head||document.documentElement).appendChild(css);
  function tick(){
    try{
      var player = document.querySelector('.html5-video-player');
      var video = document.querySelector('video');
      var ad = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
      if (ad && video){ video.muted = true; if (isFinite(video.duration)) video.currentTime = video.duration; video.playbackRate = 16; }
      else if (video && video.playbackRate === 16){ video.playbackRate = 1; video.muted = false; }
      var skip = document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytm-ad-skip-button');
      if (!skip && ad) skip = document.querySelector('[class*="ad-skip"],[class*="skip-ad"]');
      if (skip) skip.click();
      document.querySelectorAll('ytm-promoted-sparkles-web-renderer,ytm-ad-slot-renderer,ad-slot-renderer').forEach(function(e){ var r=e.closest('ytm-rich-item-renderer,ytm-item-section-renderer'); (r||e).remove(); });
    }catch(e){}
  }
  setInterval(tick, 300);
})();
"""

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
                val blockAds = runCatching { LuxoFeaturesRepository.current().youtubeAdBlock }.getOrDefault(true)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                        if (blockAds && isAdRequest(request.url)) {
                            return android.webkit.WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        return null
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        if (blockAds) view.evaluateJavascript(AD_SKIP_JS, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (blockAds) view.evaluateJavascript(AD_SKIP_JS, null)
                    }

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

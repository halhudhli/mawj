package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.allowRgb565
import coil3.bitmapFactoryMaxParallelism
import coil3.svg.SvgDecoder
import com.nuvio.app.core.network.DnsPlatform
import com.nuvio.app.core.network.SharedOkHttpClient
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        DnsPlatform.initialize(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        com.nuvio.app.features.luxo.LuxoPrefsStorage.initialize(context)
        val lite = runCatching { com.nuvio.app.features.luxo.luxoLiteMode() }.getOrDefault(false)
        return ImageLoader.Builder(context)
            .crossfade(!lite)
            .apply {
                if (lite) {
                    // Luxo TV light mode (the way Nuvio TV keeps weak boxes smooth): decode to the
                    // size on screen, keep a memory cache sized to the TV's RAM, fewer parallel
                    // decodes so scrolling never stalls, and 16-bit pictures on boxes with ≤2 GB.
                    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                    val ramMb = info.totalMem / (1024 * 1024)
                    precision(coil3.size.Precision.INEXACT)
                    memoryCache {
                        coil3.memory.MemoryCache.Builder()
                            .maxSizePercent(context, if (ramMb <= 2048) 0.15 else 0.20)
                            .build()
                    }
                    bitmapFactoryMaxParallelism(2)
                    if (ramMb <= 2048 || am.isLowRamDevice) allowRgb565(true)
                }
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            HttpClient(OkHttp) { engine { preconfigured = SharedOkHttpClient.base } }
                        },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

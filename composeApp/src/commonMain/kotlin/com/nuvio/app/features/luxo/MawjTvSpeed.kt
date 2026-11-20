package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.delay

/**
 * TV: once the home rows are known, the first posters of every row are downloaded ahead into the
 * disk cache (small decode, nothing kept in memory), one row after another, so moving down with the
 * remote finds them ready instead of waiting on the network. Switch: «تجهيز البوسترات مسبقاً».
 */
@Composable
fun MawjTvPosterPrefetch(rows: List<List<MetaPreview>>) {
    if (!luxoIsTv() || !rememberLuxoFeatures().tvPrefetch) return
    val context = LocalPlatformContext.current
    val urls = rows.map { row ->
        row.take(10).mapNotNull { m ->
            (m.poster?.takeIf { it.isNotBlank() } ?: m.rawPosterUrl?.takeIf { it.isNotBlank() } ?: m.banner?.takeIf { it.isNotBlank() })
        }
    }
    LaunchedEffect(urls) {
        if (urls.isEmpty()) return@LaunchedEffect
        // Let the visible rows load first.
        delay(1_500L)
        val loader = SingletonImageLoader.get(context)
        urls.forEach { row ->
            row.forEach { url ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .size(48, 72)
                        .precision(Precision.INEXACT)
                        .build(),
                )
            }
            delay(250L)
        }
    }
}

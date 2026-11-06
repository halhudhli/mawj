package com.nuvio.app.features.luxo

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

private const val WATCH_NEXT_IDS = "watch_next_ids"

actual fun luxoPublishWatchNext(entries: List<LuxoWatchNextEntry>) {
    val context = LuxoPrefsStorage.appContext ?: return
    Thread {
        runCatching {
            val resolver = context.contentResolver
            // Remove what was published before, then add the current list.
            LuxoPrefsStorage.load(WATCH_NEXT_IDS).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.forEach { id ->
                runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) }
            }
            val ids = entries.mapNotNull { e ->
                runCatching {
                    val builder = WatchNextProgram.Builder()
                        .setType(if (e.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                        .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setLastEngagementTimeUtcMillis(e.lastMs)
                        .setLastPlaybackPositionMillis(e.positionMs.toInt())
                        .setDurationMillis(e.durationMs.toInt())
                        .setTitle(e.title)
                        .setInternalProviderId(e.id)
                        .setIntentUri(Uri.parse(e.deepLink))
                    e.subtitle?.let { builder.setDescription(it) }
                    e.poster?.let { builder.setPosterArtUri(Uri.parse(it)).setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9) }
                    resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, builder.build().toContentValues())
                        ?.let { android.content.ContentUris.parseId(it) }
                }.getOrNull()
            }
            LuxoPrefsStorage.save(WATCH_NEXT_IDS, ids.joinToString(","))
        }
    }.apply { isDaemon = true }.start()
}

actual suspend fun luxoRemoteQuery(host: String, port: Int, code: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 2500)
            s.soTimeout = 3000
            s.getOutputStream().write("$code|{\"type\":\"nowPlaying\"}\n".toByteArray(Charsets.UTF_8))
            val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine().orEmpty()
            if (line.startsWith("ok|")) line.substring(3) else null
        }
    }.getOrNull()
}

actual suspend fun luxoSetWallpaper(jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val context = LuxoPrefsStorage.appContext ?: return@runCatching false
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@runCatching false
        WallpaperManager.getInstance(context).setBitmap(bitmap)
        true
    }.getOrDefault(false)
}

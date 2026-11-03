package com.nuvio.app.features.luxo

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun luxoSaveImageFromUrl(url: String, name: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val context = LuxoPrefsStorage.appContext ?: return@runCatching false
        val bytes = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Luxo/1.0")
            try { inputStream.use { it.readBytes() } } finally { disconnect() }
        }
        val ext = when {
            url.endsWith(".png", true) -> "png"
            url.endsWith(".svg", true) -> "svg"
            else -> "jpg"
        }
        val mime = when (ext) { "png" -> "image/png"; "svg" -> "image/svg+xml"; else -> "image/jpeg" }
        val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(60).ifBlank { "موج" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mawj")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
    }.getOrDefault(false)
}

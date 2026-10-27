package com.nuvio.app.features.player

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.profiles.ProfileBiometricAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The biggest visible video view in the current window. */
private fun findVideoView(root: View?): View? {
    var best: View? = null
    var bestArea = 0
    fun walk(view: View) {
        if (view.visibility != View.VISIBLE) return
        if ((view is SurfaceView || view is TextureView) && view.width * view.height > bestArea) {
            best = view
            bestArea = view.width * view.height
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
    }
    root?.let(::walk)
    return best
}

private suspend fun copyFrame(width: Int, height: Int): Bitmap? = withContext(Dispatchers.Main) {
    val activity = ProfileBiometricAuth.hostActivity() ?: return@withContext null
    val view = findVideoView(activity.window?.decorView) ?: return@withContext null
    if (view.width <= 0 || view.height <= 0) return@withContext null
    val w = if (width > 0) width else view.width
    val h = if (height > 0) height else view.height
    when (view) {
        is TextureView -> view.getBitmap(w, h)
        is SurfaceView -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !view.holder.surface.isValid) return@withContext null
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            suspendCancellableCoroutine { cont ->
                runCatching {
                    PixelCopy.request(view, bitmap, { result ->
                        cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
                    }, Handler(Looper.getMainLooper()))
                }.onFailure { cont.resume(null) }
            }
        }
        else -> null
    }
}

internal actual suspend fun luxoCaptureVideoFrame(width: Int, height: Int): ImageBitmap? =
    runCatching { copyFrame(width, height)?.asImageBitmap() }.getOrNull()

internal actual suspend fun luxoSaveVideoScreenshot(name: String): Boolean = runCatching {
    val bitmap = copyFrame(0, 0) ?: return false
    val context = LuxoPrefsStorage.appContext ?: return false
    withContext(Dispatchers.IO) {
        val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(60).ifBlank { "Luxo" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luxo")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    }
}.getOrDefault(false)

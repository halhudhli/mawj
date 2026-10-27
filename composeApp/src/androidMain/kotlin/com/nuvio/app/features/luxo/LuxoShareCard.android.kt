package com.nuvio.app.features.luxo

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File

internal actual fun luxoShareImage(image: ImageBitmap, title: String): Boolean = runCatching {
    val context = LuxoPrefsStorage.appContext ?: return false
    // Hardware bitmaps can't be compressed directly, so copy to a normal one first.
    val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    val dir = File(context.cacheDir, "luxo_share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "luxo-${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
    true
}.getOrDefault(false)

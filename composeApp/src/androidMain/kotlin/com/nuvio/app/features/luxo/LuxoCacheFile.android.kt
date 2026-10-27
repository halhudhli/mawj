package com.nuvio.app.features.luxo

import android.net.Uri
import java.io.File

internal actual fun luxoWriteCacheFile(name: String, content: String): String? = runCatching {
    val context = LuxoPrefsStorage.appContext ?: return null
    val dir = File(context.cacheDir, "luxo_subtitles").apply { mkdirs() }
    // Keep the folder small: only the latest few translations.
    dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
    val file = File(dir, name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
    file.writeText(content, Charsets.UTF_8)
    Uri.fromFile(file).toString()
}.getOrNull()

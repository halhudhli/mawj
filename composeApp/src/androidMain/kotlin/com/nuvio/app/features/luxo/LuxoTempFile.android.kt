package com.nuvio.app.features.luxo

actual fun luxoWriteTempFile(name: String, text: String): String? = runCatching {
    val context = LuxoPrefsStorage.appContext ?: return null
    val dir = java.io.File(context.cacheDir, "luxo").apply { mkdirs() }
    val file = java.io.File(dir, name)
    file.writeText(text, Charsets.UTF_8)
    android.net.Uri.fromFile(file).toString()
}.getOrNull()

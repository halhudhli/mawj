package com.nuvio.app.features.player

import androidx.compose.ui.graphics.ImageBitmap

internal actual suspend fun luxoCaptureVideoFrame(width: Int, height: Int): ImageBitmap? = null

internal actual suspend fun luxoSaveVideoScreenshot(name: String): Boolean = false

internal actual suspend fun luxoCaptureVideoJpeg(width: Int, height: Int): ByteArray? = null

internal actual suspend fun luxoVideoViewSize(): androidx.compose.ui.unit.IntSize? = null

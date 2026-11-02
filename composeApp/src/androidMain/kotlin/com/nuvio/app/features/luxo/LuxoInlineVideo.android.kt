package com.nuvio.app.features.luxo

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun LuxoInlineVideo(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val view = remember(url) {
        VideoView(context).apply {
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setVideoURI(Uri.parse(url))
            setOnPreparedListener { it.start() }
        }
    }
    DisposableEffect(view) { onDispose { runCatching { view.stopPlayback() } } }
    AndroidView(factory = { view }, modifier = modifier)
}

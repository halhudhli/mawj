package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A plain video (e.g. an anime opening from AnimeThemes) played inside the app. */
@Composable
expect fun LuxoInlineVideo(url: String, modifier: Modifier)

@Composable
fun LuxoVideoDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.nuvio.app.features.luxo.LuxoIdleBusy()
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            LuxoInlineVideo(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Box(
                Modifier.align(Alignment.TopEnd).padding(18.dp).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 18.sp) }
        }
    }
}

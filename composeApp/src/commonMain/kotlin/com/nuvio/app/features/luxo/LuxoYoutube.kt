package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Opens the official YouTube app (used on TV). Returns false when it is not installed. */
expect fun luxoOpenYoutubeApp(): Boolean

/** The official YouTube site inside the app. [goBack] is set to a function that steps back in its history (false when nothing is left). */
@Composable
expect fun LuxoYoutubeWeb(modifier: Modifier, onBackHandler: ((() -> Boolean)) -> Unit)

/** «يوتيوب»: the official site inside Mawj on tablets; the official app on TV. No key is needed. */
@Composable
fun LuxoYoutubeDialog(onDismiss: () -> Unit) {
    val tv = luxoIsTv()
    var appMissing by remember { mutableStateOf(false) }
    if (tv) {
        LaunchedEffect(Unit) {
            if (luxoOpenYoutubeApp()) onDismiss() else appMissing = true
        }
        if (!appMissing) return
    }
    var webBack by remember { mutableStateOf<(() -> Boolean)?>(null) }
    Dialog(
        onDismissRequest = { if (webBack?.invoke() != true) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lx("يوتيوب", "YouTube"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                TextButton(onClick = onDismiss) { Text(lx("إغلاق", "Close"), color = Color(0xFFFF5A5A)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LuxoYoutubeWeb(Modifier.fillMaxSize()) { back -> webBack = back }
            }
        }
    }
}

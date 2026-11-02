package com.nuvio.app.features.luxo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** «🎬 شارك المشهد مع الترجمة»: the frame, the line on screen, the title and the Luxo mark. */
@Composable
fun LuxoSceneShareDialog(frame: ImageBitmap, subtitle: String, title: String, label: String, onDismiss: () -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf(subtitle.trim()) }
    var failed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.nuvio.app.features.luxo.LuxoIdleBusy()
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black),
            ) {
                Image(bitmap = frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (line.isNotBlank()) {
                        Text(
                            line,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 12f)),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (label.isNotBlank()) Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                        Text("Luxo", style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF8C7BFF), Color(0xFFFF7EB6)))), fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            if (subtitle.isBlank()) {
                androidx.compose.material3.OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    placeholder = { Text("سطر يظهر على الصورة (اختياري)") },
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                )
            }
            if (failed) Text("تعذّرت المشاركة", color = Color(0xFFFF8A80))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareButton("مشاركة") {
                    scope.launch {
                        val image = runCatching { layer.toImageBitmap() }.getOrNull()
                        failed = image == null || !luxoShareImage(image, title)
                        if (!failed) onDismiss()
                    }
                }
                ShareButton("إغلاق", onDismiss)
            }
        }
    }
}

@Composable
private fun ShareButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF8C7BFF), Color(0xFFFF7EB6))))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

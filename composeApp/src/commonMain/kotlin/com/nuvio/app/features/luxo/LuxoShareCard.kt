package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Shares a rendered image through the system share sheet. */
internal expect fun luxoShareImage(image: ImageBitmap, title: String): Boolean

/** Story-sized card for a title: poster, your stars and note, and the Luxo mark. */
@Composable
internal fun LuxoShareCardDialog(title: LuxoSavedTitle, onDismiss: () -> Unit) {
    val rating = LuxoUserData.rating(title.type, title.id)
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF0D0D14)),
            ) {
                AsyncImage(
                    model = title.banner ?: title.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f)))))
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("MAWJ", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 6.sp)
                    Spacer(Modifier.height(18.dp))
                    AsyncImage(
                        model = title.poster,
                        contentDescription = title.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp)),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(title.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (rating != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(luxoStarsText(rating.score), color = Color(0xFFFFD86B), fontSize = 22.sp)
                        rating.note.takeIf { it.isNotBlank() }?.let { note ->
                            Spacer(Modifier.height(10.dp))
                            Text("“$note”", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(lx("تمت مشاهدته على موج", "Watched on Mawj"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text(lx("إلغاء", "Cancel")) }
                Button(onClick = {
                    scope.launch {
                        val image = runCatching { layer.toImageBitmap() }.getOrNull()
                        failed = image == null || !luxoShareImage(image, title.name)
                        if (!failed) onDismiss()
                    }
                }) { Text(lx("مشاركة", "Share")) }
            }
            if (failed) Text(lx("تعذّر تجهيز الصورة، تُرجى المحاولة مرة أخرى", "Couldn't prepare the image, try again"), color = Color(0xFFFF6B6B))
        }
    }
}

private fun luxoStarsText(score: Int): String =
    "★".repeat(score / 2) + "☆".repeat(5 - score / 2) + "  " + luxoStarsLabel(score) + "/5"

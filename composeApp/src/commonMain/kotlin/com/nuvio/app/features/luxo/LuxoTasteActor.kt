package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.watched.WatchedRepository

/** From «ذوقي»: an actor's works that were watched, and a way into their full page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxoTasteActorDialog(name: String, photo: String?, onDismiss: () -> Unit) {
    val watched by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    val facts = LuxoTasteFacts.facts.toMap()
    val works = remember(watched.items, facts.size, name) {
        watched.items.groupBy { LuxoTasteFacts.keyOf(it) }.mapNotNull { (k, list) ->
            val f = facts[k] ?: return@mapNotNull null
            if (name !in f.cast) null else Triple(f, list.first(), list.count { it.episode != null })
        }.sortedByDescending { it.second.markedAtEpochMs }
    }
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(opening) {
        if (!opening) return@LaunchedEffect
        val id = LuxoTmdbLite.get("search/person", mapOf("query" to LuxoTmdbLite.enc(name)))?.a("results")?.firstOrNull()?.i("id")
        val detail = id?.let { com.nuvio.app.features.tmdb.TmdbMetadataService.fetchPersonDetail(it) }
        opening = false
        if (detail != null) { onDismiss(); LuxoOverlays.person = detail }
    }
    IDialog("🎭 $name", onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
                photo?.let { AsyncImage(it, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("شوهد له ${works.size} ${if (works.size in 3..10) "أعمال" else "عملاً"}", color = Color(0xFFEDCB78), fontSize = 14.sp)
            }
            if (opening) CircularProgressIndicator(Modifier.size(22.dp)) else IButton("صفحته الكاملة") { opening = true }
        }
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                works.forEach { (f, item, eps) ->
                    Column(Modifier.width(100.dp).clickable {
                        onDismiss()
                        LuxoOverlays.openMeta(MetaPreview(id = item.id, type = item.type, name = item.name, poster = f.poster ?: item.poster))
                    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
                            (f.poster ?: item.poster)?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Text(f.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(f.year?.toString(), if (eps > 0) "$eps حلقة" else null).joinToString(" · "), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

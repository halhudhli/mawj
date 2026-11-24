package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.home.MetaPreview

/**
 * MAWJ: the title that was just opened from a row. While its full page loads, the page shows its
 * artwork, logo and story at once instead of an empty black screen with a spinner.
 */
object MawjOpeningPreview {
    private val recent = LinkedHashMap<String, MetaPreview>()

    fun put(meta: MetaPreview) {
        val key = "${meta.type}:${meta.id}"
        recent.remove(key)
        recent[key] = meta
        while (recent.size > 12) recent.remove(recent.keys.first())
    }

    fun get(type: String, id: String): MetaPreview? = recent["$type:$id"]
}

@Composable
internal fun MawjPreviewHero(meta: MetaPreview, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        val art = meta.banner ?: meta.landscapePoster ?: meta.poster
        if (art != null) {
            AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)))
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp, vertical = 40.dp).widthIn(max = 620.dp),
        ) {
            if (meta.logo != null) {
                AsyncImage(model = meta.logo, contentDescription = meta.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    modifier = Modifier.height(110.dp).fillMaxWidth())
            } else {
                Text(meta.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
            }
            meta.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
            }
            NuvioLoadingIndicator(modifier = Modifier.padding(top = 18.dp), size = 28.dp)
        }
    }
}

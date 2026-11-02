package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.features.details.components.DetailSectionTitle

/** Remembers which logos are dark (black text on transparent) so they're drawn white on the dark theme. */
internal object LuxoLogoTone {
    val dark = mutableStateMapOf<String, Boolean>()

    /** Average brightness of the logo's visible pixels, measured on a tiny copy. */
    fun measure(url: String, painter: Painter) {
        if (dark.containsKey(url)) return
        val w = 48
        val h = 24
        val bitmap = ImageBitmap(w, h)
        runCatching {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
                with(painter) { draw(Size(w.toFloat(), h.toFloat())) }
            }
            val pixels = IntArray(w * h)
            bitmap.readPixels(pixels)
            var sum = 0.0
            var count = 0
            var colorful = 0
            pixels.forEach { p ->
                val a = (p ushr 24) and 0xFF
                if (a < 60) return@forEach
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                if (maxOf(r, g, b) - minOf(r, g, b) > 60) colorful++
                count++
            }
            // Dark and not colourful → draw it white. Colourful logos keep their colours.
            dark[url] = count > 0 && sum / count < 0.35 && colorful < count / 4
        }.onFailure { dark[url] = false }
    }
}

@Composable
internal fun LuxoSmartLogo(url: String, name: String, modifier: Modifier) {
    val isDark = LuxoLogoTone.dark[url] == true
    AsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Fit,
        colorFilter = if (isDark) ColorFilter.tint(Color.White) else null,
        onState = { state -> if (state is AsyncImagePainter.State.Success) LuxoLogoTone.measure(url, state.painter) },
        modifier = modifier,
    )
}

internal fun luxoFlag(country: String?): String {
    val c = country?.uppercase()?.takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } } ?: return ""
    val first = 0x1F1E6 + (c[0] - 'A')
    val second = 0x1F1E6 + (c[1] - 'A')
    return buildString {
        appendCodePointCompat(first)
        appendCodePointCompat(second)
    }
}

private fun StringBuilder.appendCodePointCompat(cp: Int) {
    val v = cp - 0x10000
    append(((v ushr 10) + 0xD800).toChar())
    append(((v and 0x3FF) + 0xDC00).toChar())
}

/** Networks + production companies as dark glass cards (replaces the white logo tiles). */
@Composable
internal fun LuxoProductionSection(
    meta: MetaDetails,
    showHeader: Boolean,
    horizontalScrollPadding: Dp,
    onCompanyClick: ((MetaCompany, String) -> Unit)?,
) {
    val isSeries = meta.type != "movie"
    val groups = buildList {
        if (meta.networks.isNotEmpty()) add(Triple("القنوات والمنصات", meta.networks, "network"))
        if (meta.productionCompanies.isNotEmpty()) add(Triple(if (isSeries) "الإنتاج والاستوديوهات" else "شركات الإنتاج", meta.productionCompanies, "company"))
    }
    if (groups.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.forEachIndexed { index, (title, items, kind) ->
            if (showHeader || index > 0) DetailSectionTitle(title = title)
            LazyRow(
                modifier = Modifier.nuvioHorizontalScrollBleed(horizontalScrollPadding).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalScrollPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items.distinctBy { it.tmdbId ?: it.name.hashCode() }, key = { "$kind:${it.tmdbId}:${it.name}" }) { company ->
                    LuxoCompanyCard(
                        company = company,
                        label = when {
                            kind == "network" -> "قناة / منصة"
                            meta.type == "anime" || (isSeries && meta.genres.any { it.contains("Anim", true) || it.contains("رسوم") }) -> "استوديو"
                            else -> "شركة إنتاج"
                        },
                        onClick = company.tmdbId?.let { _ -> onCompanyClick?.let { cb -> { cb(company, kind) } } },
                    )
                }
            }
        }
    }
}

@Composable
private fun LuxoCompanyCard(company: MetaCompany, label: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            val logo = company.logo
            if (!logo.isNullOrBlank()) {
                LuxoSmartLogo(logo, company.name, Modifier.fillMaxSize().padding(4.dp))
            } else {
                Text(
                    company.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Text(
            company.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
    }
}

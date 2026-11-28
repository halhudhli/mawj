package com.nuvio.app.features.details.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.extractReleaseYearForDisplay
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.SPECIALS_SEASON_NUMBER

// «صفحة العمل — المسرح» (Mawj new interface): the hero text, the episode line and «لماذا قد يعجب».

private val TGold = Color(0xFFD6B36A)
private val TGoldLight = Color(0xFFF1DDA6)
private val TInk = Color(0xFFF6F1E4)
private val TBody = Color(0xFFB7C2CE)
private val TMeta = Color(0xFFC3CCD6)
private val TFaint = Color(0xFF8796A8)
private val TRaised = Color(0xFF0D1620)

private fun arabicDigits(s: String): String = s.map { c -> if (c in '0'..'9') ('٠' + (c - '0')) else c }.joinToString("")

/** Gold line over the title: kind · seasons · years. */
internal fun mawjTitleEyebrow(meta: MetaDetails): String {
    val seasons = meta.videos.mapNotNull { it.season }.filter { it > SPECIALS_SEASON_NUMBER }.toSet().size
    val kind = if (meta.type == "movie") "فيلم" else "مسلسل"
    val parts = buildList {
        add(kind)
        if (seasons > 0) add(arabicDigits(seasons.toString()) + if (seasons == 1) " موسم" else if (seasons in 3..10) " مواسم" else " موسماً")
        meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(arabicDigits(it.replace("-", "–"))) }
    }
    return parts.joinToString(" · ")
}

/** Title, ratings line and genres, as on the approved title page. */
@Composable
internal fun MawjTitleHead(meta: MetaDetails, showRatings: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 620.dp)) {
        Text(mawjTitleEyebrow(meta), color = TGold, fontSize = 14.sp)
        val swash = com.nuvio.app.features.luxo.rememberLuxoFeatures().swashTitles && with(com.nuvio.app.features.luxo.Touches) { meta.name.hasArabic() }
        Text(
            meta.name, color = TInk, fontFamily = if (swash) com.nuvio.app.features.luxo.rememberMawjSwash() else FontFamily.Serif, fontWeight = if (swash) FontWeight.Normal else FontWeight.Bold,
            fontSize = 64.sp, lineHeight = 68.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val imdb = meta.imdbRating?.takeIf { showRatings && (it.toDoubleOrNull() ?: 0.0) > 0.0 }
            imdb?.let {
                if (com.nuvio.app.features.luxo.rememberLuxoFeatures().moonRatings) it.toDoubleOrNull()?.let { v -> com.nuvio.app.features.luxo.MawjMoonRating(v, 16.dp) }
                Text("IMDb $it", color = TGold, fontSize = 15.sp)
            }
            if (meta.genres.isNotEmpty()) Text(meta.genres.take(3).joinToString(" · "), color = TMeta, fontSize = 15.sp, maxLines = 1)
            meta.releaseInfo?.let(::extractReleaseYearForDisplay)?.takeIf { meta.type == "movie" }?.let { Text(it.toString(), color = TMeta, fontSize = 15.sp) }
            meta.ageRating?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TMeta, fontSize = 12.sp, modifier = Modifier.border(1.dp, Color(0x4DEEF2F6), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
internal fun MawjTitleOverview(text: String) {
    Text(text, color = TBody, fontSize = 16.sp, lineHeight = 30.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 620.dp))
}

private fun MetaVideo.mawjScore(): Double? = imdbRating?.takeIf { it > 0.0 } ?: tmdbRating?.takeIf { it > 0.0 } ?: rating?.takeIf { it > 0.0 }

/** «خط الحلقات»: each episode is a point, its height its rating; the gold point is the season's best. */
@Composable
internal fun MawjEpisodeLine(meta: MetaDetails, onEpisodeClick: (MetaVideo) -> Unit) {
    val bySeason = remember(meta.videos) {
        meta.videos.filter { (it.season ?: 0) > SPECIALS_SEASON_NUMBER && it.episode != null }
            .groupBy { it.season!! }.toSortedMap()
            .mapValues { (_, eps) -> eps.sortedBy { it.episode } }
    }
    if (bySeason.isEmpty()) return
    var season by remember(meta.id) { mutableIntStateOf(bySeason.keys.last()) }
    val eps = bySeason[season].orEmpty()
    val scored = eps.mapNotNull { e -> e.mawjScore()?.let { e to it } }
    val best = scored.maxByOrNull { it.second }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("خط الحلقات", color = TInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bySeason.keys.forEach { n ->
                    val on = n == season
                    Box(
                        Modifier.height(36.dp).clip(CircleShape).background(if (on) TGold else Color.Transparent)
                            .border(1.dp, if (on) TGold else Color(0x2EEEF2F6), CircleShape).clickable { season = n }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("الموسم ${arabicDigits(n.toString())}", color = if (on) Color(0xFF070D14) else TMeta, fontSize = 13.sp) }
                }
            }
        }
        if (scored.size >= 2) {
            Text("كل نقطة حلقة، وارتفاعها تقييمها. النقطة الذهبية أقوى حلقة في الموسم.", color = TFaint, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)).background(TRaised).border(1.dp, Color(0x24D6B36A), RoundedCornerShape(18.dp))) {
                Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
                    val lo = (scored.minOf { it.second } - 0.3).coerceAtLeast(0.0)
                    val hi = (scored.maxOf { it.second } + 0.2).coerceAtMost(10.0)
                    val span = (hi - lo).coerceAtLeast(0.5)
                    val n = scored.size
                    // Episode one sits at the reading start (the right edge in Arabic).
                    val pts = scored.mapIndexed { i, (_, r) ->
                        val fx = if (n == 1) 0.5f else i / (n - 1f)
                        val x = if (rtl) size.width * (1f - fx) else size.width * fx
                        Offset(x, size.height * (1f - ((r - lo) / span).toFloat()))
                    }
                    val line = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2f
                            cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                        }
                    }
                    val fill = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
                    drawPath(fill, TGold.copy(alpha = 0.07f))
                    drawPath(line, TGold.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()))
                    scored.forEachIndexed { i, (e, _) ->
                        val isBest = best?.first == e
                        drawCircle(if (isBest) TGold else Color(0xFFEEF2F6), radius = if (isBest) 7.dp.toPx() else 4.dp.toPx(), center = pts[i])
                    }
                }
                best?.let { (e, r) ->
                    Text(
                        "ح${arabicDigits((e.episode ?: 0).toString())} · ★ ${((r * 10).toInt() / 10.0)}", color = TGoldLight, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
        // The season's strongest episodes, ready to play.
        val picks = (if (scored.size >= 3) scored.sortedByDescending { it.second }.map { it.first } else eps).take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            picks.forEach { e ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onEpisodeClick(e) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF2F4A5A), Color(0xFF14202D))))) {
                        (e.thumbnail ?: meta.background)?.let { AsyncImage(model = it, contentDescription = e.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        Text("ح${arabicDigits((e.episode ?: 0).toString())}", color = TGold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                    Text(e.title, color = Color(0xFFE6EBF0), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta2 = listOfNotNull(e.runtime?.let { "${arabicDigits(it.toString())} د" }, e.mawjScore()?.let { "★ ${((it * 10).toInt() / 10.0)}" }).joinToString(" · ")
                    if (meta2.isNotBlank()) Text(meta2, color = TFaint, fontSize = 12.sp)
                }
            }
        }
    }
}

/** «لماذا قد يعجب»: three plain reasons from what is known about the title. */
@Composable
internal fun MawjWhyCard(meta: MetaDetails) {
    val reasons = remember(meta.id, meta.imdbRating, meta.runtime) {
        buildList {
            meta.imdbRating?.toDoubleOrNull()?.takeIf { it >= 7.5 }?.let { add("تقييمه $it على IMDb، من الأعمال الأعلى تقييماً في نوعه.") }
            val who = meta.director.firstOrNull() ?: meta.writer.firstOrNull()
            who?.let { add(if (meta.director.isNotEmpty()) "من إخراج $it." else "من كتابة $it.") }
            if (meta.genres.size >= 2) add("${meta.genres[0]} و${meta.genres[1]} في عمل واحد.")
            meta.runtime?.takeIf { it.isNotBlank() }?.let { rt ->
                val minutes = Regex("\\d+").find(rt)?.value
                if (minutes != null) add(if (meta.type == "movie") "مدته ${arabicDigits(minutes)} دقيقة، تكفيها سهرة واحدة." else "حلقاته ${arabicDigits(minutes)} دقيقة، تناسب سهرة واحدة.")
            }
            meta.cast.firstOrNull()?.name?.let { add("بطولة $it.") }
        }.take(3)
    }
    if (reasons.isEmpty()) return
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TRaised).border(1.dp, Color(0x2ED6B36A), RoundedCornerShape(20.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("لماذا قد يعجب", color = TInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        reasons.forEachIndexed { i, r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(arabicDigits((i + 1).toString()), color = TGold, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                Text(r, color = TMeta, fontSize = 14.sp, lineHeight = 25.sp)
            }
        }
    }
}

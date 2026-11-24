@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Episode
import com.mawj.tv.data.Meta
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Store
import kotlinx.coroutines.launch

/**
 * «متابعة»: plays the same source chosen before. The link is looked up again (links from
 * debrid services expire); if it is gone, the saved link is tried as it is.
 */
suspend fun resumeSameSource(meta: MetaPreview, episode: Episode?): Boolean {
    val saved = Store.rememberedSource(meta.id) ?: return false
    val videoId = episode?.id ?: meta.id
    val fresh = Addons.streams(meta.type, videoId)
    val match = fresh.firstOrNull { it.addonName == saved.addonName && it.name == saved.name && it.title == saved.title }
        ?: saved.bingeGroup?.let { bg -> fresh.firstOrNull { it.bingeGroup == bg } }
        ?: fresh.firstOrNull { it.addonName == saved.addonName && it.quality == saved.quality }
    val stream = match ?: if (episode == null || Store.progressFor(meta.id)?.videoId == videoId) saved else return false
    Nav.go(Route.Player(PlayRequest(meta, videoId, episode, stream, Store.positionFor(videoId))))
    return true
}

@Composable
fun DetailScreen(preview: MetaPreview) {
    LaunchedEffect(preview.id) { Backdrop.show(preview.background ?: preview.poster) }
    var meta by remember(preview.id) { mutableStateOf<Meta?>(null) }
    // Cast photos and studio logos (TMDB), loaded after the page shows.
    var credits by remember(preview.id) { mutableStateOf<Pair<List<com.mawj.tv.data.CastMember>, List<com.mawj.tv.data.Company>>?>(null) }
    LaunchedEffect(preview.id) { kotlinx.coroutines.delay(400); credits = com.mawj.tv.data.Tmdb.credits(preview.id, preview.type) }
    LaunchedEffect(preview.id) {
        meta = Addons.meta(preview.type, preview.id)
        meta?.preview?.background?.let(Backdrop::show)
    }
    val m = meta?.preview ?: preview
    val isSeries = preview.type == "series" || (meta?.videos?.size ?: 0) > 1
    val resume = Store.progressFor(preview.id)
    val episodes = meta?.videos.orEmpty()
    val seasons = episodes.map { it.season }.distinct().sortedBy { if (it == 0) 999 else it }
    var season by rememberSaveable(preview.id) { mutableStateOf(resume?.season ?: -1) }
    if (season == -1 && seasons.isNotEmpty()) season = seasons.firstOrNull { it > 0 } ?: seasons.first()
    // The episode the play button plays: where it stopped, or the first not watched yet.
    val nextEpisode = remember(episodes, resume) {
        episodes.firstOrNull { it.id == resume?.videoId }
            ?: episodes.firstOrNull { it.season > 0 && !Store.isWatched(it.id) }
            ?: episodes.firstOrNull()
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    val playLabel = when {
        busy -> "جارٍ فتح المصدر…"
        resume != null && isSeries -> "متابعة م${resume.season} ح${resume.episode}"
        resume != null -> "متابعة المشاهدة"
        isSeries && nextEpisode != null -> "تشغيل م${nextEpisode.season} ح${nextEpisode.number}"
        else -> "تشغيل"
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 56.dp, bottom = 60.dp)) {
        item {
            Column(Modifier.width(620.dp)) {
                if (!m.logo.isNullOrBlank()) Img(m.logo, 380.dp, 110.dp, Modifier.width(380.dp).height(110.dp), ContentScale.Fit)
                else T(m.name, 46.sp, weight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(10.dp))
                MetaLine(listOfNotNull(m.rating?.let { "★ $it" }, m.year, m.genres.take(3).joinToString(" · "), meta?.runtime))
                Spacer(Modifier.height(14.dp))
                T(m.description.orEmpty(), 17.sp, color = Color2.Body, maxLines = 5, lineHeight = 28.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Pill(playLabel, primary = true, modifier = Modifier.focusRequester(playFocus)) {
                        if (busy) return@Pill
                        val ep = if (isSeries) (episodes.firstOrNull { it.id == resume?.videoId } ?: nextEpisode) else null
                        // The viewer picks the source; only «متابعة» reuses the one picked before.
                        if (resume == null || Store.rememberedSource(m.id) == null) {
                            Nav.go(Route.Sources(m, ep))
                            return@Pill
                        }
                        busy = true; error = null
                        scope.launch {
                            if (!resumeSameSource(m, ep)) Nav.go(Route.Sources(m, ep))
                            busy = false
                        }
                    }
                    Pill("المصادر") {
                        val ep = if (isSeries) (episodes.firstOrNull { it.id == resume?.videoId } ?: nextEpisode) else null
                        Nav.go(Route.Sources(m, ep))
                    }
                }
                error?.let { T(it, 15.sp, color = C.Red, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        if (isSeries && seasons.isNotEmpty()) {
            item {
                LazyRow(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(seasons) { s -> Chip(if (s == 0) "إضافات" else "الموسم $s", s == season, onClick = { season = s }) }
                }
            }
            item {
                LazyRow(Modifier.padding(top = 16.dp).focusRestorer(), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(episodes.filter { it.season == season }, key = { it.id }) { ep ->
                        EpisodeCard(ep, Store.isWatched(ep.id), m) { Nav.go(Route.Sources(m, ep)) }
                    }
                }
            }
        }
        val c = credits
        if (c != null && (c.first.isNotEmpty() || c.second.isNotEmpty())) {
            item { CastRow(c.first) }
            item { CompanyRow(c.second) }
        }
        if (meta != null && (c == null || c.first.isEmpty()) && (meta!!.cast.isNotEmpty() || meta!!.directors.isNotEmpty())) {
            item {
                Column(Modifier.padding(top = 30.dp)) {
                    if (meta!!.directors.isNotEmpty()) T("إخراج: " + meta!!.directors.joinToString("، "), 15.sp, color = C.Muted)
                    if (meta!!.cast.isNotEmpty()) T("بطولة: " + meta!!.cast.take(8).joinToString("، "), 15.sp, color = C.Muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

private object Color2 { val Body = androidx.compose.ui.graphics.Color(0xFFC9D2DC) }

@Composable
private fun EpisodeCard(ep: Episode, watched: Boolean, meta: MetaPreview, onClick: () -> Unit) {
    Column(Modifier.width(250.dp)) {
        WideCard(
            title = "${ep.number}. ${ep.title}",
            image = ep.thumbnail ?: meta.background,
            fraction = if (watched) 1f else 0f,
            onFocused = {},
            onClick = onClick,
            w = 250.dp, h = 140.dp,
        )
        ep.overview?.let { T(it, 13.sp, color = C.Dim, maxLines = 2, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

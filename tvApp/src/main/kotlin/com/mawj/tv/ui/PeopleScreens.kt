@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.CastMember
import com.mawj.tv.data.Company
import com.mawj.tv.data.CompanyPage
import com.mawj.tv.data.Person
import com.mawj.tv.data.Tmdb
import com.mawj.tv.data.TmdbWork
import com.mawj.tv.data.toPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- on the title page ----------------

/** Cast with photos; a card opens the actor's page. */
@Composable
fun CastRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Column(Modifier.padding(top = 28.dp)) {
        T("طاقم العمل", 19.sp, weight = FontWeight.SemiBold)
        LazyRow(Modifier.focusRestorer().padding(top = 10.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(cast, key = { it.id }) { c ->
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).tvFocus(shape = CircleShape, scale = 1.08f, onClick = { Nav.go(Route.Person(c.id, c.name)) }).background(C.Card, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.photo != null) Img(c.photo, 96.dp, 96.dp, Modifier.fillMaxSize())
                        else T(c.name.take(1), 30.sp, color = C.Gold, weight = FontWeight.Bold)
                    }
                    T(c.name, 14.sp, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    if (c.role.isNotBlank()) T(c.role, 12.sp, color = C.Dim, maxLines = 1, align = TextAlign.Center)
                }
            }
        }
    }
}

/** Production companies with their logos; a card opens the studio page. */
@Composable
fun CompanyRow(companies: List<Company>) {
    if (companies.isEmpty()) return
    Column(Modifier.padding(top = 24.dp)) {
        T("الإنتاج", 19.sp, weight = FontWeight.SemiBold)
        LazyRow(Modifier.focusRestorer().padding(top = 10.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(companies, key = { it.id }) { c ->
                Box(
                    Modifier.size(180.dp, 80.dp).tvFocus(shape = RoundedCornerShape(14.dp), onClick = { Nav.go(Route.Company(c.id, c.name)) })
                        .background(C.Raised).padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (c.logo != null) Logo(c.logo, 150.dp, 50.dp, Modifier.fillMaxSize())
                    else T(c.name, 15.sp, maxLines = 2, align = TextAlign.Center)
                }
            }
        }
    }
}

/** Studio logos are often dark on transparent: drawn as one light colour so they show on the sea. */
@Composable
private fun Logo(url: String, w: Dp, h: Dp, modifier: Modifier, tint: Color = C.GoldLight) {
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).build() },
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
        modifier = modifier,
    )
}

// ---------------- actor ----------------

@Composable
fun PersonScreen(id: Int, name: String) {
    var person by remember(id) { mutableStateOf<Person?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { person = Tmdb.person(id); failed = person == null }
    Box(Modifier.fillMaxSize().background(C.Sea)) {
        val p = person
        if (p == null) {
            Loading(if (failed) "تعذّر تحميل صفحة $name." else "جارٍ تحميل صفحة $name…")
            return@Box
        }
        PhotoSlideshow(p.photos)
        // Dark towards the text (right) and the bottom.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x26070D14), 0.45f to Color(0x99070D14), 0.62f to Color(0xF5070D14))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xF5070D14))))
        Column(Modifier.fillMaxSize().padding(start = 60.dp, top = 56.dp)) {
            Column(Modifier.width(560.dp)) {
                T("${p.job} — ${p.works.size} عملاً", 15.sp, color = C.Gold, weight = FontWeight.SemiBold)
                T(p.name, 50.sp, weight = FontWeight.Bold, maxLines = 1)
                MetaLine(listOfNotNull(p.birthday?.let { "مواليد ${it.take(4)}" }, p.place?.substringAfterLast(", ")))
                if (p.photos.size > 1) SlideDots(p.photos.size)
                Spacer(Modifier.height(12.dp))
                T(p.bio.ifBlank { "لا تتوفر نبذة." }, 16.sp, color = C.Muted, maxLines = 5, lineHeight = 27.sp)
            }
            Spacer(Modifier.weight(1f))
            WorksRow("الأعمال", p.works)
            Spacer(Modifier.height(30.dp))
        }
    }
}

private object Slides { var index by mutableIntStateOf(0) }

/**
 * The actor's photos change every 6 seconds with a slow fade. Only the shown photo is in memory;
 * the next one is fetched a second early so the change never waits. The face is framed from the top.
 */
@Composable
private fun PhotoSlideshow(photos: List<String>) {
    if (photos.isEmpty()) return
    val ctx = LocalContext.current
    var index by remember(photos) { mutableIntStateOf(0) }
    LaunchedEffect(photos) {
        Slides.index = 0
        if (photos.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_000)
            val next = (index + 1) % photos.size
            coil3.SingletonImageLoader.get(ctx).enqueue(ImageRequest.Builder(ctx).data(photos[next]).size(720, 1080).build())
            delay(1_000)
            index = next
            Slides.index = next
        }
    }
    Box(Modifier.fillMaxHeight().width(560.dp)) {
        Crossfade(targetState = photos[index], animationSpec = tween(1200), label = "actor") { url ->
            AsyncImage(
                model = remember(url) { ImageRequest.Builder(ctx).data(url).size(720, 1080).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SlideDots(count: Int) {
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(22.dp, 4.dp).background(if (i == Slides.index) C.Gold else Color(0x33F6F1E4), RoundedCornerShape(2.dp)))
        }
    }
}

// ---------------- studio ----------------

@Composable
fun CompanyScreen(id: Int, name: String) {
    var sort by rememberSaveable(id) { mutableStateOf("new") }
    var page by remember(id) { mutableStateOf<CompanyPage?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, sort) { page = Tmdb.company(id, sort); failed = page == null }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B121A))) {
        val p = page
        // The logo fills the background: centred, contained, faint, never stretched or cut.
        p?.company?.logo?.let { Logo(it, 520.dp, 300.dp, Modifier.align(Alignment.CenterEnd).padding(end = 80.dp).size(520.dp, 300.dp).alpha(0.14f), C.GoldLight) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xE60B121A))))
        Column(Modifier.fillMaxSize().padding(start = 60.dp, top = 56.dp)) {
            T("شركة إنتاج" + (p?.let { " — ${it.works.size} عملاً" } ?: ""), 15.sp, color = C.Gold, weight = FontWeight.SemiBold)
            T(p?.company?.name ?: name, 50.sp, weight = FontWeight.Bold, maxLines = 1)
            p?.country?.let { MetaLine(listOf(it)) }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
                Chip("الأحدث", sort == "new", onClick = { sort = "new" }, modifier = Modifier.focusRequester(first))
                Chip("الأعلى تقييماً", sort == "top", onClick = { sort = "top" })
            }
            Spacer(Modifier.weight(1f))
            when {
                p != null -> WorksRow("من أعمالها", p.works)
                failed -> T("تعذّر تحميل أعمال الشركة.", 16.sp, color = C.Muted)
                else -> T("جارٍ التحميل…", 16.sp, color = C.Muted)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------------- shared ----------------

@Composable
private fun WorksRow(title: String, works: List<TmdbWork>) {
    if (works.isEmpty()) return
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf<Int?>(null) }
    T(title, 19.sp, weight = FontWeight.SemiBold)
    LazyRow(Modifier.focusRestorer().fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(works, key = { it.type + it.tmdbId }) { w ->
            Box(
                Modifier.size(132.dp, 198.dp).tvFocus(onClick = {
                    if (opening != null) return@tvFocus
                    opening = w.tmdbId
                    scope.launch {
                        w.toPreview()?.let { Nav.go(Route.Detail(it)) }
                        opening = null
                    }
                }).background(C.Card),
            ) {
                Img(w.poster, 132.dp, 198.dp, Modifier.fillMaxSize())
                if (w.poster == null) T(w.name, 15.sp, weight = FontWeight.Bold, align = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(10.dp))
                if (opening == w.tmdbId) Box(Modifier.fillMaxSize().background(Color(0x99070D14)), contentAlignment = Alignment.Center) { T("…", 22.sp) }
            }
        }
    }
}

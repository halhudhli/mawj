package com.nuvio.app.features.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.LuxoCollaborator
import com.nuvio.app.features.luxo.LuxoCredit
import com.nuvio.app.features.luxo.LuxoImage
import com.nuvio.app.features.luxo.LuxoPersonExtras
import com.nuvio.app.features.luxo.LuxoTmdb
import com.nuvio.app.features.luxo.LuxoTranslate
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.launch

private val PAccent1 = Color(0xFF6F8FBF)
private val PAccent2 = Color(0xFF9DB1CF)

private enum class LuxoCreditKind(val label: String) { ALL("الكل"), MOVIES("أفلام"), SERIES("مسلسلات") }
private enum class LuxoCreditSort(val label: String) { FAME("الأشهر"), RATING("الأعلى تقييماً"), NEWEST("الأحدث"), OLDEST("الأقدم") }
private enum class LuxoRatingFloor(val label: String, val min: Double) { ANY("كل التقييمات", 0.0), SIX("+6", 6.0), SEVEN("+7", 7.0), EIGHT("+8", 8.0), NINE("+9", 9.0) }
private enum class LuxoWatchFilter(val label: String) { ALL("الكل"), WATCHED("تمت مشاهدته ✅"), UNWATCHED("لم تتم مشاهدته") }

/**
 * The Luxo actor page: a big full photo (not a circle), «اشتهر بـ», best collaborators, and the
 * full filmography with sorting, a rating floor and movies / series apart. Collaborators open in
 * place (Back returns to the previous person).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LuxoPersonPage(
    initial: PersonDetail,
    onOpenMeta: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = remember(initial.tmdbId) { mutableStateListOf(initial) }
    val person = stack.last()
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    var extras by remember(person.tmdbId) { mutableStateOf<LuxoPersonExtras?>(null) }
    var arabicName by remember(person.tmdbId) { mutableStateOf<String?>(null) }
    var arabicBio by remember(person.tmdbId) { mutableStateOf<String?>(null) }
    var collaboratorNames by remember(person.tmdbId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(person.tmdbId) {
        val loaded = runCatching { LuxoTmdb.person(person.tmdbId) }.getOrNull()
        extras = loaded
        val nameSource = loaded?.englishName ?: person.name
        if (!LuxoTranslate.hasArabic(person.name)) {
            arabicName = LuxoTranslate.names(listOf(nameSource))[nameSource]
        }
        loaded?.collaborators?.map { it.name }?.takeIf { it.isNotEmpty() }?.let { names ->
            collaboratorNames = LuxoTranslate.names(names)
        }
        val bio = person.biography ?: loaded?.englishBiography
        arabicBio = if (bio != null && !LuxoTranslate.hasArabic(bio)) LuxoTranslate.text(bio) else bio
    }

    var kind by remember { mutableStateOf(LuxoCreditKind.ALL) }
    var sort by remember { mutableStateOf(LuxoCreditSort.FAME) }
    var floor by remember { mutableStateOf(LuxoRatingFloor.ANY) }
    var watchFilter by remember { mutableStateOf(LuxoWatchFilter.ALL) }
    var yearFilter by remember(person.tmdbId) { mutableStateOf<String?>(null) }
    val isWatched = com.nuvio.app.features.luxo.rememberLuxoWatchedMatcher()
    var awards by remember(person.tmdbId) { mutableStateOf<Pair<List<com.nuvio.app.features.luxo.LuxoAward>, Int>?>(null) }
    LaunchedEffect(extras?.wikidataId) {
        extras?.wikidataId?.let { awards = com.nuvio.app.features.luxo.LuxoAwards.load(it) }
    }
    val birthYear = person.birthday?.take(4)?.toIntOrNull()
    var viewer by remember { mutableStateOf<Pair<List<LuxoImage>, Int>?>(null) }

    val today = remember { CurrentDateProvider.todayIsoDate() }
    val credits = extras?.credits.orEmpty()
    val filtered = remember(credits, sort, floor, watchFilter, yearFilter, isWatched) {
        credits
            .filter { floor == LuxoRatingFloor.ANY || (it.rating >= floor.min && it.votes >= 20) }
            .filter { yearFilter == null || it.year == yearFilter }
            .filter {
                when (watchFilter) {
                    LuxoWatchFilter.ALL -> true
                    LuxoWatchFilter.WATCHED -> isWatched(it)
                    LuxoWatchFilter.UNWATCHED -> !isWatched(it)
                }
            }
            .let { list ->
                when (sort) {
                    LuxoCreditSort.FAME -> list.sortedByDescending { it.votes }
                    LuxoCreditSort.RATING -> list.sortedWith(compareByDescending<LuxoCredit> { it.votes >= 20 }.thenByDescending { it.rating })
                    LuxoCreditSort.NEWEST -> list.sortedByDescending { it.date ?: "0000" }
                    LuxoCreditSort.OLDEST -> list.filter { it.date != null }.sortedBy { it.date }
                }
            }
    }
    val movies = filtered.filter { it.mediaType == "movie" }
    val series = filtered.filter { it.mediaType == "tv" }
    val backdrop = extras?.knownFor?.firstOrNull { it.backdrop != null }?.backdrop

    fun open(credit: LuxoCredit) = onOpenMeta(
        MetaPreview(id = credit.metaId, type = credit.metaType, name = credit.title, poster = credit.poster, banner = credit.backdrop, releaseInfo = credit.year),
    )

    fun openPerson(c: LuxoCollaborator) {
        if (opening) return
        opening = true
        scope.launch {
            TmdbMetadataService.fetchPersonDetail(c.id)?.let { stack.add(it) }
            opening = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp
        val side = if (wide) 32.dp else 18.dp
        // Ambient backdrop of their best-known work.
        backdrop?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(if (wide) 520.dp else 420.dp),
            )
            Box(
                Modifier.fillMaxWidth().height(if (wide) 520.dp else 420.dp).background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), MaterialTheme.colorScheme.background.copy(alpha = 0.85f), MaterialTheme.colorScheme.background),
                    ),
                ),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (wide) 138.dp else 112.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            full { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(52.dp)) }
            full {
                PersonHero(
                    person = person,
                    extras = extras,
                    arabicName = arabicName,
                    bio = arabicBio ?: person.biography,
                    wide = wide,
                    today = today,
                    onPhoto = { photos, i -> viewer = photos to i },
                    watchedCount = credits.count(isWatched),
                    followed = com.nuvio.app.features.luxo.LuxoFollowedPeople.isFollowed(person.tmdbId),
                    onFollow = { com.nuvio.app.features.luxo.LuxoFollowedPeople.toggle(person.tmdbId) },
                )
            }

            val known = extras?.knownFor.orEmpty()
            if (known.isNotEmpty()) {
                full { Header("اشتهر بـ ⭐") }
                full {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(known, key = { "${it.mediaType}:${it.id}" }) { credit -> KnownForCard(credit, wide) { open(credit) } }
                    }
                }
            }

            val collaborators = extras?.collaborators.orEmpty()
            if (collaborators.isNotEmpty()) {
                full { Header("أفضل المتعاونين 🤝") }
                full {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(collaborators, key = { it.id }) { c ->
                            CollaboratorCard(c, collaboratorNames[c.name] ?: c.name) { openPerson(c) }
                        }
                    }
                }
            }

            // Hidden gems: high rating, not widely seen.
            val gems = credits.filter { it.rating >= 7.3 && it.votes in 40..1500 && it.poster != null }
                .sortedByDescending { it.rating }.take(12)
            if (gems.isNotEmpty()) {
                full { Header("جواهر مخفية 💎") }
                full {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(gems, key = { "gem:${it.mediaType}:${it.id}" }) { credit ->
                            Box(Modifier.width(if (wide) 138.dp else 112.dp)) {
                                CreditCard(credit, today, isWatched(credit), birthYear) { open(credit) }
                            }
                        }
                    }
                }
            }

            // Rating across the years.
            val curve = credits.filter { it.votes >= 20 && it.year != null }
                .groupBy { it.year!! }
                .mapValues { (_, list) -> list.sumOf { it.rating } / list.size }
                .entries.sortedBy { it.key }.associate { it.key to it.value }
            if (curve.size >= 3) {
                full { Header("منحنى التقييم 📈") }
                full { RatingCurve(curve) }
            }

            awards?.let { (wins, nominations) ->
                if (wins.isNotEmpty() || nominations > 0) {
                    full { Header("الجوائز 🏆") }
                    full { AwardsBlock(wins, nominations) }
                }
            }

            full { Header("الأعمال 🎬") }
            full {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LuxoCreditKind.entries.forEach { k ->
                            val count = when (k) {
                                LuxoCreditKind.ALL -> filtered.size
                                LuxoCreditKind.MOVIES -> movies.size
                                LuxoCreditKind.SERIES -> series.size
                            }
                            Chip("${k.label} · $count", kind == k) { kind = k }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الترتيب", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LuxoCreditSort.entries) { s -> Chip(s.label, sort == s, small = true) { sort = s } }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المشاهدة", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LuxoWatchFilter.entries) { w -> Chip(w.label, watchFilter == w, small = true) { watchFilter = w } }
                        }
                    }
                    // Career timeline: tap a year to see only that year.
                    val years = credits.mapNotNull { it.year }.groupingBy { it }.eachCount()
                        .entries.sortedByDescending { it.key }.associate { it.key to it.value }
                    if (years.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("المسيرة", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item { Chip("كل السنين", yearFilter == null, small = true) { yearFilter = null } }
                                items(years.entries.toList(), key = { it.key }) { (y, n) ->
                                    YearChip(y, n, years.values.max(), yearFilter == y, birthYear) { yearFilter = if (yearFilter == y) null else y }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("التقييم", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LuxoRatingFloor.entries) { f -> Chip(if (f == LuxoRatingFloor.ANY) f.label else "★ ${f.label}", floor == f, small = true) { floor = f } }
                        }
                    }
                }
            }

            if (extras == null) {
                full {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else {
                if (kind != LuxoCreditKind.SERIES) creditGrid("الأفلام", movies, today, isWatched, birthYear, ::open)
                if (kind != LuxoCreditKind.MOVIES) creditGrid("المسلسلات", series, today, isWatched, birthYear, ::open)
                if (filtered.isEmpty()) {
                    full { Text("لا توجد أعمال بهذا الفلتر", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(12.dp)) }
                }
            }
        }

        if (opening) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    viewer?.let { (images, start) -> LuxoImageViewer(images = images, start = start, onDismiss = { viewer = null }) }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.creditGrid(
    title: String,
    list: List<LuxoCredit>,
    today: String,
    isWatched: (LuxoCredit) -> Boolean,
    birthYear: Int?,
    onOpen: (LuxoCredit) -> Unit,
) {
    if (list.isEmpty()) return
    full {
        Text("$title · ${list.size}", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
    items(list, key = { "$title:${it.mediaType}:${it.id}" }) { credit -> CreditCard(credit, today, isWatched(credit), birthYear) { onOpen(credit) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonHero(
    person: PersonDetail,
    extras: LuxoPersonExtras?,
    arabicName: String?,
    bio: String?,
    wide: Boolean,
    today: String,
    onPhoto: (List<LuxoImage>, Int) -> Unit,
    watchedCount: Int = 0,
    followed: Boolean = false,
    onFollow: () -> Unit = {},
) {
    val photos = extras?.photos.orEmpty()
    val photo = photos.firstOrNull()?.url("h632") ?: person.profilePhoto
    val name = arabicName ?: person.name
    val secondName = listOfNotNull(extras?.englishName, person.name).firstOrNull { it != name }
    val credits = extras?.credits.orEmpty()
    val rated = credits.filter { it.votes >= 20 }
    val avg = if (rated.isEmpty()) null else rated.sumOf { it.rating } / rated.size
    val photoView: @Composable (Modifier) -> Unit = { m ->
        Box(
            m
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                .clickable(enabled = photos.isNotEmpty()) { onPhoto(photos, 0) },
        ) {
            AsyncImage(model = photo, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (photos.size > 1) {
                Text(
                    "📷 ${photos.size}",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
    val info: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, color = Color.White, fontSize = if (wide) 34.sp else 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
            secondName?.let { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                person.knownFor?.let { Fact(departmentArabic(it)) }
                person.birthday?.let { b -> Fact(ageLine(b, person.deathday, today)) }
                person.placeOfBirth?.let { Fact("📍 ${it.substringAfterLast(", ").ifBlank { it }}") }
                val m = credits.count { it.mediaType == "movie" }
                val s = credits.count { it.mediaType == "tv" }
                if (m > 0) Fact("🎬 $m فيلم")
                if (s > 0) Fact("📺 $s مسلسل")
                avg?.let { Fact("★ ${(it * 10).toInt() / 10.0} متوسط أعماله") }
                if (credits.isNotEmpty()) Fact("✅ تمت مشاهدة $watchedCount من ${credits.size}")
            }
            Text(
                if (followed) "🔔 تتم متابعته" else "🔔 متابعة الممثل",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (followed) Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f)))
                        else Brush.horizontalGradient(listOf(PAccent1, PAccent2)),
                    )
                    .clickable(onClick = onFollow)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
            bio?.takeIf { it.isNotBlank() }?.let { text ->
                var expanded by remember(text) { mutableStateOf(false) }
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
                )
                Text(if (expanded) "أقل" else "المزيد", color = PAccent1, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { expanded = !expanded })
            }
            if (photos.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos.drop(1).take(20), key = { _, it -> it.path }) { index, img ->
                        AsyncImage(
                            model = img.url("w185"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(64.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).clickable { onPhoto(photos, index + 1) },
                        )
                    }
                }
            }
        }
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            photoView(Modifier.width(300.dp))
            Box(Modifier.weight(1f).padding(top = 8.dp)) { info() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { photoView(Modifier.width(240.dp)) }
            info()
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Fact(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = if (small) 13.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(listOf(PAccent1, PAccent2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
            )
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.4f else 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 8.dp),
    )
}

@Composable
private fun KnownForCard(credit: LuxoCredit, wide: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(if (wide) 280.dp else 230.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))) {
            AsyncImage(model = credit.backdrop ?: credit.poster, contentDescription = credit.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(credit.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(credit.year, credit.role?.let { "بدور $it" }).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (credit.rating > 0) RatingBadge(credit.rating, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

@Composable
private fun CollaboratorCard(c: LuxoCollaborator, name: String, onClick: () -> Unit) {
    Column(Modifier.width(110.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = c.photo,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(Modifier.height(6.dp))
        Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${c.count} أعمال معاً · ${c.role}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CreditCard(credit: LuxoCredit, today: String, watched: Boolean = false, birthYear: Int? = null, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
            if (credit.poster != null) {
                AsyncImage(model = credit.poster, contentDescription = credit.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(credit.title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            }
            if (credit.rating > 0 && credit.votes >= 5) RatingBadge(credit.rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (watched) {
                Text(
                    "✓",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color(0xFF3DDC97), androidx.compose.foundation.shape.CircleShape).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            val age = birthYear?.let { b -> credit.year?.toIntOrNull()?.minus(b) }?.takeIf { it in 3..110 }
            if (age != null) {
                Text(
                    "عمره $age",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if ((credit.date ?: "") > today) {
                Text(
                    "قريباً",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Brush.horizontalGradient(listOf(PAccent1, PAccent2)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(credit.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        Text(
            listOfNotNull(credit.year, credit.role, credit.episodes?.takeIf { it > 1 }?.let { "$it حلقة" }).joinToString(" · "),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RatingBadge(rating: Double, modifier: Modifier) {
    val color = when {
        rating >= 8 -> Color(0xFF3DDC97)
        rating >= 7 -> Color(0xFFB7E36A)
        rating >= 6 -> Color(0xFFFFC857)
        else -> Color(0xFFFF8A65)
    }
    Text(
        "★ ${(rating * 10).toInt() / 10.0}",
        color = Color.Black,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.background(color, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun departmentArabic(department: String): String = when (department) {
    "Acting" -> "🎭 ممثل"
    "Directing" -> "🎬 مخرج"
    "Writing" -> "✍️ كاتب"
    "Production" -> "🎞️ منتج"
    "Sound" -> "🎵 موسيقى"
    "Camera" -> "🎥 تصوير"
    "Editing" -> "✂️ مونتاج"
    else -> department
}

private fun ageLine(birthday: String, deathday: String?, today: String): String {
    val by = birthday.take(4).toIntOrNull() ?: return "🎂 $birthday"
    val endYear = (deathday ?: today).take(4).toIntOrNull() ?: return "🎂 $by"
    val endMd = (deathday ?: today).drop(5)
    var age = endYear - by
    if (endMd < birthday.drop(5)) age -= 1
    return if (deathday != null) "🕯️ $by – ${deathday.take(4)} ($age سنة)" else "🎂 $by · $age سنة"
}


@Composable
private fun YearChip(year: String, count: Int, max: Int, selected: Boolean, birthYear: Int?, onClick: () -> Unit) {
    val age = birthYear?.let { year.toIntOrNull()?.minus(it) }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Brush.verticalGradient(listOf(PAccent1, PAccent2))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f))),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A small bar: taller = busier year.
        Box(
            Modifier
                .width(18.dp)
                .height((6 + 22f * count / max.coerceAtLeast(1)).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = if (selected) 0.9f else 0.45f)),
        )
        Spacer(Modifier.height(4.dp))
        Text(year, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(if (age != null && age in 3..110) "$count · $age سنة" else "$count", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

@Composable
private fun RatingCurve(points: Map<String, Double>) {
    val entries = points.entries.toList()
    val best = entries.maxByOrNull { it.value }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        best?.let { Text("أقوى سنة: ${it.key} · ★ ${(it.value * 10).toInt() / 10.0}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val minV = 4.0
            val maxV = 9.5
            fun x(i: Int) = if (entries.size == 1) size.width / 2 else size.width * i / (entries.size - 1)
            fun y(v: Double) = size.height * (1f - ((v.coerceIn(minV, maxV) - minV) / (maxV - minV)).toFloat())
            // guide lines at 6, 7, 8
            listOf(6.0, 7.0, 8.0).forEach { g ->
                drawLine(Color.White.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, y(g)), androidx.compose.ui.geometry.Offset(size.width, y(g)), 1.5f)
            }
            val path = androidx.compose.ui.graphics.Path()
            entries.forEachIndexed { i, e -> if (i == 0) path.moveTo(x(i), y(e.value)) else path.lineTo(x(i), y(e.value)) }
            drawPath(path, Brush.horizontalGradient(listOf(PAccent1, PAccent2)), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            entries.forEachIndexed { i, e ->
                drawCircle(if (e == best) PAccent2 else Color.White, radius = if (e == best) 8f else 4.5f, center = androidx.compose.ui.geometry.Offset(x(i), y(e.value)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(entries.first().key, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(entries.last().key, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun AwardsBlock(wins: List<com.nuvio.app.features.luxo.LuxoAward>, nominations: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("🏆 ${wins.size} جائزة")
            if (nominations > 0) Fact("🎖️ $nominations ترشيح")
            Fact("المصدر: ويكي بيانات")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(wins.take(40), key = { it.name + it.year }) { a ->
                Column(
                    Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0x33FFD27B), Color(0x14FFFFFF))))
                        .border(1.dp, Color(0x55FFD27B), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text("🏆 " + (a.year ?: ""), color = Color(0xFFFFD27B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(a.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    a.forWork?.let { Text("عن: $it", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

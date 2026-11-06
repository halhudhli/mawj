package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.luxo.LuxoCredit
import com.nuvio.app.features.luxo.rememberLuxoWatchedMatcher
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

private val CAccent1 = Color(0xFF6F8FBF)
private val CAccent2 = Color(0xFF9DB1CF)

private enum class CompanyTab(val label: String, val media: String) { MOVIES("أفلام", "movie"), SERIES("مسلسلات", "tv") }
private enum class CompanySort(val label: String) { POPULAR("الأشهر"), RATING("الأعلى تقييماً"), NEWEST("الأحدث"), OLDEST("الأقدم") }
private val CompanyFloors = listOf(0.0 to "كل التقييمات", 6.0 to "★ +6", 7.0 to "★ +7", 8.0 to "★ +8", 9.0 to "★ +9")

private data class CompanyHeader(val name: String, val logo: String?, val country: String?, val headquarters: String?, val parent: String?)

private object LuxoCompanyApi {
    private val json = Json { ignoreUnknownKeys = true }
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull

    suspend fun get(endpoint: String, query: Map<String, String>): JsonObject? {
        val key = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.parseToJsonElement(httpGetText(buildTmdbUrl(endpoint, key, query))) as? JsonObject }.getOrNull()
    }

    suspend fun header(kind: String, id: Int): CompanyHeader? {
        val o = get("$kind/$id", emptyMap()) ?: return null
        return CompanyHeader(
            name = o.s("name") ?: return null,
            logo = o.s("logo_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
            country = o.s("origin_country"),
            headquarters = o.s("headquarters"),
            parent = (o["parent_company"] as? JsonObject)?.s("name"),
        )
    }

    /** One page of a company's / network's works, with the total count. */
    suspend fun page(kind: String, id: Int, media: String, sort: CompanySort, floor: Double, page: Int): Pair<List<LuxoCredit>, Int> {
        val dateField = if (media == "movie") "primary_release_date" else "first_air_date"
        val query = buildMap {
            put("language", "ar")
            put("page", page.toString())
            put(if (kind == "network") "with_networks" else "with_companies", id.toString())
            put(
                "sort_by",
                when (sort) {
                    CompanySort.POPULAR -> "popularity.desc"
                    CompanySort.RATING -> "vote_average.desc"
                    CompanySort.NEWEST -> "$dateField.desc"
                    CompanySort.OLDEST -> "$dateField.asc"
                },
            )
            if (sort == CompanySort.RATING) put("vote_count.gte", "150")
            if (floor > 0) {
                put("vote_average.gte", floor.toString())
                put("vote_count.gte", if (sort == CompanySort.RATING) "150" else "20")
            }
            if (sort == CompanySort.NEWEST) put("$dateField.lte", com.nuvio.app.features.watchprogress.CurrentDateProvider.todayIsoDate())
        }
        val o = get("discover/$media", query) ?: return emptyList<LuxoCredit>() to 0
        val list = (o["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { r ->
            val rid = r.i("id") ?: return@mapNotNull null
            LuxoCredit(
                id = rid,
                mediaType = media,
                title = r.s("title") ?: r.s("name") ?: return@mapNotNull null,
                poster = r.s("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                backdrop = r.s("backdrop_path")?.let { "https://image.tmdb.org/t/p/w780$it" },
                date = r.s("release_date") ?: r.s("first_air_date"),
                rating = r.d("vote_average") ?: 0.0,
                votes = r.i("vote_count") ?: 0,
                popularity = r.d("popularity") ?: 0.0,
                role = null,
                episodes = null,
                enTitle = r.s("original_title") ?: r.s("original_name"),
            )
        }
        return list to (o.i("total_results") ?: list.size)
    }
}

/** The new company / channel page: cover, info, movies and series apart, sorting and a rating floor. */
@Composable
internal fun LuxoCompanyPage(
    kind: String,
    id: Int,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenMeta: (MetaPreview) -> Unit,
) {
    if (com.nuvio.app.features.luxo.rememberLuxoFeatures().companyHall) {
        LuxoCompanyHall(kind, id, fallbackName, onBack, onOpenMeta)
        return
    }
    var header by remember(id) { mutableStateOf<CompanyHeader?>(null) }
    var tab by remember(id) { mutableStateOf(if (kind == "network") CompanyTab.SERIES else CompanyTab.MOVIES) }
    var sort by remember { mutableStateOf(CompanySort.POPULAR) }
    var floor by remember { mutableStateOf(0.0) }
    val works = remember(id) { mutableStateListOf<LuxoCredit>() }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var totals by remember(id) { mutableStateOf<Map<CompanyTab, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val isWatched = rememberLuxoWatchedMatcher()

    LaunchedEffect(id) {
        header = LuxoCompanyApi.header(kind, id)
        // Count both tabs once for the chips.
        totals = CompanyTab.entries.associateWith { t ->
            if (kind == "network" && t == CompanyTab.MOVIES) 0 else LuxoCompanyApi.page(kind, id, t.media, CompanySort.POPULAR, 0.0, 1).second
        }
    }
    LaunchedEffect(id, tab, sort, floor) {
        loading = true
        works.clear()
        page = 1
        val (list, t) = LuxoCompanyApi.page(kind, id, tab.media, sort, floor, 1)
        works.addAll(list)
        total = t
        loading = false
    }
    fun loadMore() {
        if (loading || works.size >= total) return
        loading = true
        scope.launch {
            val next = page + 1
            val (list, _) = LuxoCompanyApi.page(kind, id, tab.media, sort, floor, next)
            works.addAll(list.filter { n -> works.none { it.id == n.id } })
            page = next
            loading = false
        }
    }

    val cover = works.firstOrNull { it.backdrop != null }?.backdrop
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp
        val side = if (wide) 32.dp else 18.dp
        cover?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(380.dp))
            Box(
                Modifier.fillMaxWidth().height(380.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), MaterialTheme.colorScheme.background.copy(alpha = 0.9f), MaterialTheme.colorScheme.background)),
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
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(56.dp)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(260.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val logo = header?.logo
                        if (logo != null) LuxoSmartLogo(logo, header?.name ?: fallbackName, Modifier.fillMaxSize())
                        else Text(header?.name ?: fallbackName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(header?.name ?: fallbackName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    val facts = listOfNotNull(
                        header?.country?.let { "${luxoFlag(it)} $it" },
                        header?.headquarters,
                        header?.parent?.let { "تابعة لـ $it" },
                        totals[CompanyTab.MOVIES]?.takeIf { it > 0 }?.let { "🎬 $it فيلم" },
                        totals[CompanyTab.SERIES]?.takeIf { it > 0 }?.let { "📺 $it مسلسل" },
                    )
                    Text(facts.joinToString(" · "), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    val watched = works.count(isWatched)
                    if (watched > 0) {
                        Text("✅ تمت مشاهدة $watched من المعروض", color = Color(0xFF3DDC97), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompanyTab.entries.forEach { t ->
                            val n = totals[t]
                            if (kind == "network" && t == CompanyTab.MOVIES) return@forEach
                            CChip(t.label + (n?.let { " · $it" } ?: ""), tab == t) { tab = t }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CompanySort.entries) { s -> CChip(s.label, sort == s, small = true) { sort = s } }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CompanyFloors) { (v, label) -> CChip(label, floor == v, small = true) { floor = v } }
                    }
                }
            }
            items(works, key = { "${it.mediaType}:${it.id}" }) { credit ->
                Column(
                    Modifier.clickable {
                        onOpenMeta(
                            MetaPreview(
                                id = credit.metaId,
                                type = credit.metaType,
                                name = credit.title,
                                poster = credit.poster,
                                banner = credit.backdrop,
                                releaseInfo = credit.year,
                            ),
                        )
                    },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        AsyncImage(model = credit.poster, contentDescription = credit.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (credit.rating > 0 && credit.votes >= 5) {
                            Text(
                                "★ ${(credit.rating * 10).toInt() / 10.0}",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                    .background(if (credit.rating >= 7) Color(0xFF3DDC97) else Color(0xFFFFC857), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (isWatched(credit)) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0xFF3DDC97)),
                                contentAlignment = Alignment.Center,
                            ) { Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(credit.title, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    credit.year?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(color = Color.White)
                        works.isEmpty() -> Text("لا توجد أعمال بهذا الفلتر", color = Color.White.copy(alpha = 0.6f))
                        works.size < total -> CChip("عرض المزيد (${works.size} من $total)", false) { loadMore() }
                    }
                }
            }
        }
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
        }
    }
}

@Composable
private fun CChip(text: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = if (small) 13.sp else 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Brush.horizontalGradient(listOf(CAccent1, CAccent2))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))),
            )
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.4f else 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 6.dp else 8.dp),
    )
}

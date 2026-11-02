package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.luxoAccentColors
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.trakt.TraktConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.toLocalDateTime

/**
 * World countdown: the next episode of every airing series, from three sources merged without
 * duplicates (TVmaze first for its exact air time, then Trakt, then Simkl). Grouped by country,
 * with Chinese, Indian and Arabic productions filtered out entirely.
 */
internal data class LuxoAiring(
    val key: String,
    val title: String,
    val poster: String?,
    val season: Int?,
    val episode: Int?,
    val airMs: Long,
    val country: String?,
    val language: String?,
    val isAnime: Boolean,
    val imdb: String?,
    val tmdb: String?,
    val source: String,
)

internal object LuxoWorldCountdown {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _items = MutableStateFlow<List<LuxoAiring>?>(null)
    val items: StateFlow<List<LuxoAiring>?> = _items.asStateFlow()
    private var loadedAt = 0L
    private var loadedDays = 0
    private var loading = false

    private val excludedCountries = setOf(
        "CN", "TW", "HK", "MO", "IN",
        "SA", "AE", "EG", "KW", "QA", "BH", "OM", "JO", "LB", "SY", "IQ", "MA", "DZ", "TN", "LY", "SD", "YE", "PS", "MR",
    )
    private val excludedLanguages = setOf(
        "zh", "cn", "chinese", "mandarin", "cantonese",
        "hi", "hindi", "ta", "tamil", "te", "telugu", "ml", "malayalam", "kn", "kannada", "bn", "bengali",
        "mr", "marathi", "pa", "punjabi", "gu", "gujarati",
        "ar", "arabic",
    )

    fun isExcluded(country: String?, language: String?): Boolean =
        country?.uppercase() in excludedCountries || language?.lowercase() in excludedLanguages

    private fun now() = Clock.System.now().toEpochMilliseconds()

    // Heavy downloads and JSON parsing stay off the main thread (TVs freeze otherwise).
    suspend fun refresh(force: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { refreshOffMain(force) }

    private suspend fun refreshOffMain(force: Boolean) {
        if (loading) return
        val days = LuxoCountdownRange.effective()
        if (!force && _items.value != null && loadedDays == days && now() - loadedAt < 3 * 3_600_000L) return
        loading = true
        rangeEndMs = now() + days * 86_400_000L
        try {
            val tvmaze = runCatching { fromTvmaze() }.getOrDefault(emptyList())
            // Show TVmaze straight away, then merge the others in as they arrive.
            merge(tvmaze)
            val trakt = runCatching { fromTrakt() }.getOrDefault(emptyList())
            merge(tvmaze + trakt)
            val simkl = runCatching { fromSimkl() }.getOrDefault(emptyList())
            merge(tvmaze + trakt + simkl)
            loadedAt = now()
            loadedDays = days
        } finally {
            loading = false
        }
    }

    /** Keep the earliest upcoming episode per series; sources earlier in the list win ties. */
    private var rangeEndMs = Long.MAX_VALUE

    private fun merge(all: List<LuxoAiring>) {
        val now = now()
        val end = rangeEndMs
        val seen = mutableMapOf<String, LuxoAiring>()
        val byIds = mutableMapOf<String, String>()
        all.filter { it.airMs > now - 3_600_000L && it.airMs <= end && !isExcluded(it.country, it.language) }.forEach { item ->
            val ids = listOfNotNull(
                item.imdb?.let { "imdb:$it" },
                item.tmdb?.let { "tmdb:$it" },
                "name:" + item.title.lowercase().filter { it.isLetterOrDigit() },
            )
            val existingKey = ids.firstNotNullOfOrNull { byIds[it] }
            val existing = existingKey?.let { seen[it] }
            if (existing == null) {
                seen[item.key] = item
                ids.forEach { byIds[it] = item.key }
            } else {
                // Same show from another source: keep the first source, but fill gaps it lacks.
                seen[existing.key] = existing.copy(
                    poster = existing.poster ?: item.poster,
                    country = existing.country ?: item.country,
                    language = existing.language ?: item.language,
                    imdb = existing.imdb ?: item.imdb,
                    tmdb = existing.tmdb ?: item.tmdb,
                    isAnime = existing.isAnime || item.isAnime,
                )
                ids.forEach { byIds.putIfAbsent(it, existing.key) }
            }
        }
        _items.value = seen.values.filter { !isExcluded(it.country, it.language) }.sortedBy { it.airMs }
    }

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JsonElement? {
        val response = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
            httpRequestRaw("GET", url, headers + ("Accept" to "application/json"), "", maxResponseBodyBytes = 12 * 1024 * 1024)
        } ?: return null
        if (response.status !in 200..299) return null
        return runCatching { json.parseToJsonElement(response.body) }.getOrNull()
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(name: String): String? = (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String): Int? = (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

    private fun isoDate(daysFromToday: Int): String {
        val ms = now() + daysFromToday * 86_400_000L
        val day = ms / 86_400_000L
        // civil date from epoch day
        val z = day + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    // --- TVmaze: free, exact airstamp, country and language on every show.
    private suspend fun fromTvmaze(): List<LuxoAiring> = coroutineScope {
        val gate = Semaphore(3) // TVmaze allows ~20 calls / 10 s
        // Daily schedules: two weeks on a tablet, five days on a TV in light mode (one call per day and country).
        val tvmazeDays = if (luxoLiteMode()) 5 else LuxoCountdownRange.effective().coerceAtMost(14)
        val urls = (0 until tvmazeDays).flatMap { day ->
            val date = isoDate(day)
            listOf("US", "GB", "KR", "JP", "CA", "AU").map { "https://api.tvmaze.com/schedule?country=$it&date=$date" } +
                "https://api.tvmaze.com/schedule/web?date=$date"
        }
        urls.map { url ->
            async {
                gate.withPermit {
                    val result = get(url)
                    delay(350L)
                    (result as? JsonArray).orEmpty().mapNotNull { parseTvmaze(it.obj()) }
                }
            }
        }.awaitAll().flatten()
    }

    private fun parseTvmaze(ep: JsonObject?): LuxoAiring? {
        ep ?: return null
        val show = ep["show"].obj() ?: (ep["_embedded"].obj()?.get("show")).obj() ?: return null
        if (show.str("type")?.lowercase() in setOf("news", "talk show", "sports", "game show")) return null
        val airMs = ep.str("airstamp")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return null
        val network = show["network"].obj() ?: show["webChannel"].obj()
        val country = network?.get("country").obj()?.str("code")
        val genres = (show["genres"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
        val language = show.str("language")
        val externals = show["externals"].obj()
        return LuxoAiring(
            key = "tvmaze:${show.int("id")}",
            title = show.str("name") ?: return null,
            poster = show["image"].obj()?.let { it.str("original") ?: it.str("medium") },
            season = ep.int("season"),
            episode = ep.int("number"),
            airMs = airMs,
            country = country,
            language = language,
            isAnime = "Anime" in genres || (show.str("type") == "Animation" && (country == "JP" || language == "Japanese")),
            imdb = externals?.str("imdb"),
            tmdb = null,
            source = "TVmaze",
        )
    }

    // --- Trakt: used when a Trakt client id is configured for the build.
    private suspend fun fromTrakt(): List<LuxoAiring> {
        if (TraktConfig.CLIENT_ID.isBlank()) return emptyList()
        // Trakt gives up to 31 days per call, so 6 months = 6 calls.
        val total = LuxoCountdownRange.effective()
        val root = (0 until total step 31).flatMap { start ->
            val span = (total - start).coerceAtMost(31)
            (get(
                "https://api.trakt.tv/calendars/all/shows/${isoDate(start)}/$span?extended=full",
                mapOf("trakt-api-key" to TraktConfig.CLIENT_ID, "trakt-api-version" to "2"),
            ) as? JsonArray).orEmpty()
        }
        return root.mapNotNull { element ->
            val obj = element.obj() ?: return@mapNotNull null
            val show = obj["show"].obj() ?: return@mapNotNull null
            val ep = obj["episode"].obj()
            val ids = show["ids"].obj()
            val airMs = obj.str("first_aired")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return@mapNotNull null
            val genres = (show["genres"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }.orEmpty()
            val country = show.str("country")?.uppercase()
            LuxoAiring(
                key = "trakt:${ids?.int("trakt")}",
                title = show.str("title") ?: return@mapNotNull null,
                poster = null,
                season = ep?.int("season"),
                episode = ep?.int("number"),
                airMs = airMs,
                country = country,
                language = show.str("language"),
                isAnime = "anime" in genres || (country == "JP" && "animation" in genres),
                imdb = ids?.str("imdb"),
                tmdb = ids?.int("tmdb")?.toString(),
                source = "Trakt",
            )
        }
    }

    // --- Simkl: public calendar files (no key). No country, so anime is JP and TV is filled in by the merge.
    private suspend fun fromSimkl(): List<LuxoAiring> = (
        listOf("https://data.simkl.in/calendar/anime.json" to true) +
            // The general TV file is big: tablets only.
            (if (luxoLiteMode()) emptyList() else listOf("https://data.simkl.in/calendar/tv.json" to false))
        ).flatMap { (url, anime) ->
        (get(url) as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element.obj() ?: return@mapNotNull null
            val airMs = obj.str("date")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return@mapNotNull null
            val ids = obj["ids"].obj()
            val ep = obj["episode"].obj()
            val poster = obj.str("poster")?.let { "https://simkl.in/posters/${it}_m.jpg" }
            LuxoAiring(
                key = "simkl:${ids?.int("simkl_id")}",
                title = obj.str("title") ?: return@mapNotNull null,
                poster = poster,
                season = ep?.int("season"),
                episode = ep?.int("episode"),
                airMs = airMs,
                country = if (anime) "JP" else null,
                language = if (anime) "Japanese" else null,
                isAnime = anime,
                imdb = ids?.str("imdb"),
                tmdb = ids?.str("tmdb"),
                source = "Simkl",
            )
        }
    }
}

// ------------------------------------------------------------------ sections

internal data class LuxoWorldSection(val id: String, val label: String, val order: Int)

private val countryNamesAr = mapOf(
    "US" to "🇺🇸 أمريكي", "GB" to "🇬🇧 بريطاني", "KR" to "🇰🇷 كوري (K-Drama)", "JP" to "🌸 أنمي ومسلسلات يابانية",
    "CA" to "🇨🇦 كندي", "AU" to "🇦🇺 أسترالي", "ES" to "🇪🇸 إسباني", "DE" to "🇩🇪 ألماني", "FR" to "🇫🇷 فرنسي",
    "IT" to "🇮🇹 إيطالي", "MX" to "🇲🇽 مكسيكي", "BR" to "🇧🇷 برازيلي", "TR" to "🇹🇷 تركي", "SE" to "🇸🇪 سويدي",
    "DK" to "🇩🇰 دنماركي", "NO" to "🇳🇴 نرويجي", "FI" to "🇫🇮 فنلندي", "NL" to "🇳🇱 هولندي", "BE" to "🇧🇪 بلجيكي",
    "PL" to "🇵🇱 بولندي", "TH" to "🇹🇭 تايلندي", "PH" to "🇵🇭 فلبيني", "ID" to "🇮🇩 إندونيسي", "IE" to "🇮🇪 إيرلندي",
    "NZ" to "🇳🇿 نيوزيلندي", "AR" to "🇦🇷 أرجنتيني", "CO" to "🇨🇴 كولومبي", "RU" to "🇷🇺 روسي", "IL" to "🇮🇱",
)
private val countryNamesEn = mapOf(
    "US" to "🇺🇸 US", "GB" to "🇬🇧 UK", "KR" to "🇰🇷 K-Drama", "JP" to "🌸 Anime & Japan",
)
private val languageCountry = mapOf(
    "English" to null, "Korean" to "KR", "Japanese" to "JP", "Spanish" to "ES", "German" to "DE", "French" to "FR",
    "Italian" to "IT", "Portuguese" to "BR", "Turkish" to "TR", "Swedish" to "SE", "Danish" to "DK", "Norwegian" to "NO",
    "Thai" to "TH", "Polish" to "PL", "Dutch" to "NL", "Finnish" to "FI", "Russian" to "RU",
)

internal fun LuxoAiring.sectionId(): String = when {
    isAnime -> "JP"
    country != null -> country.uppercase().let { if (it == "UK") "GB" else it }
    else -> languageCountry[language] ?: "WORLD"
}

@Composable
private fun sectionLabel(id: String): String = when (id) {
    "WORLD" -> lx("🌐 منصات عالمية", "🌐 Global streaming")
    else -> lx(countryNamesAr[id] ?: "🏳️ $id", countryNamesEn[id] ?: countryNamesAr[id] ?: id)
}

private val sectionOrder = listOf("US", "GB", "KR", "JP")

// ------------------------------------------------------------------ UI

@Composable
private fun liveCountdown(targetMs: Long, nowMs: Long): String {
    val left = ((targetMs - nowMs) / 1000).coerceAtLeast(0)
    if (left == 0L) return lx("يُعرض الآن 🔴", "Airing now 🔴")
    val d = left / 86_400
    val h = (left % 86_400) / 3600
    val m = (left % 3600) / 60
    val s = left % 60
    fun two(v: Long) = v.toString().padStart(2, '0')
    return LuxoDateStyle.digits(if (d > 0) "${d}ي : ${two(h)}س : ${two(m)}د : ${two(s)}ث" else "${two(h)}:${two(m)}:${two(s)}")
}

@Composable
internal fun LuxoWorldCountdownContent(onOpen: (MetaPreview) -> Unit, modifier: Modifier = Modifier) {
    val items by LuxoWorldCountdown.items.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf("ALL") }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(LuxoCountdownRange.days) { LuxoWorldCountdown.refresh() }
    // Longer ranges read better by time: this week, this month, next month, then month by month.
    var byTime by remember(LuxoCountdownRange.days) { mutableStateOf(LuxoCountdownRange.days > 30) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    val tokens = MaterialTheme.nuvio
    val accents = luxoAccentColors()
    val list = items
    if (list == null) {
        LuxoSkeletonList(modifier = modifier.padding(16.dp))
        return
    }
    val grouped = if (byTime) list.groupBy { luxoTimeBucket(it.airMs, now) } else list.groupBy { it.sectionId() }
    val sectionIds = if (byTime) grouped.keys.sorted() else
        grouped.keys.sortedWith(compareBy({ sectionOrder.indexOf(it).let { i -> if (i < 0) 99 else i } }, { -(grouped[it]?.size ?: 0) }))
    @Composable
    fun groupLabel(id: String): String = if (id.startsWith("T")) luxoTimeBucketLabel(id) else sectionLabel(id)
    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (byTime) lx("🕐 حسب الوقت", "🕐 By time") else lx("🌍 حسب الدولة", "🌍 By country"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(accents))
                    .clickable { byTime = !byTime; selected = "ALL" }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            (listOf("ALL") + sectionIds).forEach { id ->
                val isOn = id == selected
                val count = if (id == "ALL") list.size else grouped[id]?.size ?: 0
                Text(
                    text = (if (id == "ALL") lx("الكل", "All") else groupLabel(id)) + "  " + LuxoDateStyle.digits("$count"),
                    color = if (isOn) Color.White else tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isOn) Brush.horizontalGradient(accents) else Brush.horizontalGradient(listOf(tokens.colors.surface, tokens.colors.surface)))
                        .border(1.dp, accents.first().copy(alpha = if (isOn) 0f else 0.25f), RoundedCornerShape(50))
                        .clickable { selected = id }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = (maxWidth.value / 330f).toInt().coerceIn(1, 4)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val shown = if (selected == "ALL") sectionIds else listOf(selected)
                shown.forEach { sectionId ->
                    val sectionItems = grouped[sectionId].orEmpty()
                    if (sectionItems.isEmpty()) return@forEach
                    item(key = "h_$sectionId", span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(Brush.linearGradient(accents)))
                            Spacer(Modifier.width(8.dp))
                            Text(groupLabel(sectionId), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = tokens.colors.textPrimary)
                            Spacer(Modifier.weight(1f))
                            Text(LuxoDateStyle.digits("${sectionItems.size}"), color = tokens.colors.textMuted)
                        }
                    }
                    items(sectionItems, key = { "${sectionId}_${it.key}" }) { item ->
                        val soon = item.airMs - now < 86_400_000L
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(tokens.colors.surface)
                                .border(1.dp, accents.first().copy(alpha = if (soon) 0.45f else 0.12f), RoundedCornerShape(20.dp))
                                .clickable {
                                    val id = item.imdb ?: return@clickable
                                    onOpen(MetaPreview(id = id, type = "series", name = item.title, poster = item.poster))
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = item.poster,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(66.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.title, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val code = buildString {
                                    item.season?.let { append("S").append(it.toString().padStart(2, '0')) }
                                    item.episode?.let { append("E").append(it.toString().padStart(2, '0')) }
                                }
                                if (code.isNotBlank()) Text(code, color = tokens.colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    liveCountdown(item.airMs, now),
                                    color = tokens.colors.textPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                )
                                Text(item.source, color = tokens.colors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            lx("ما قدرنا نجيب المواعيد الحين. تأكد من الإنترنت وجرب بعد شوي.", "Couldn't load schedules right now."),
                            color = tokens.colors.textMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}



/** Sortable time bucket ids: T0 this week, T1 this month, T2 next month, T3yyyy-mm later months. */
internal fun luxoTimeBucket(airMs: Long, nowMs: Long): String {
    val days = (airMs - nowMs) / 86_400_000L
    return when {
        days < 7 -> "T0"
        days < 30 -> "T1"
        days < 60 -> "T2"
        else -> {
            val ld = kotlin.time.Instant.fromEpochMilliseconds(airMs)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            "T3" + ld.year + "-" + (ld.month.ordinal + 1).toString().padStart(2, '0')
        }
    }
}

@Composable
internal fun luxoTimeBucketLabel(id: String): String = when {
    id == "T0" -> lx("هذا الأسبوع", "This week")
    id == "T1" -> lx("هذا الشهر", "This month")
    id == "T2" -> lx("الشهر القادم", "Next month")
    id.startsWith("T3") -> {
        val parts = id.removePrefix("T3").split("-")
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val ar = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        val en = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        lx("${ar[m - 1]} ${parts[0]}", "${en[m - 1]} ${parts[0]}")
    }
    else -> id
}

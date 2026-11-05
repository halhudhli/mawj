package com.nuvio.app.features.luxo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Pages Luxo opens on top of everything (they can open titles through the app's own deep link). */
object LuxoOverlays {
    /** null = closed, "" = the «السلاسل» home, "collection:ID" / "group:KEY" / "anime:ID" = one franchise. */
    var franchise by mutableStateOf<String?>(null)
    var traktDoctor by mutableStateOf(false)
    var guessGame by mutableStateOf(false)
    var choiceWheel by mutableStateOf(false)
    var remote by mutableStateOf(false)
    var kids by mutableStateOf(false)
    var smartSource by mutableStateOf(false)
    var youtube by mutableStateOf(false)

    fun openMeta(preview: MetaPreview) {
        franchise = null
        com.nuvio.app.core.deeplink.handleAppUrl(com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl(preview.type, preview.id))
    }
}

enum class LuxoFranchiseKind { MOVIES, SERIES, ANIME }

data class LuxoFranchisePart(
    val key: String,
    val title: String,
    val enTitle: String?,
    val date: String?,
    val poster: String?,
    val rating: Double,
    /** "movie" / "tv". */
    val mediaType: String,
    val tmdbId: Int?,
    /** e.g. «فيلم», «OVA», «يمكن تخطيه». */
    val note: String? = null,
    val optional: Boolean = false,
) {
    val year: String? get() = date?.take(4)?.takeIf { it.length == 4 }
    fun asCredit() = LuxoCredit(
        id = tmdbId ?: -key.hashCode(),
        mediaType = mediaType,
        title = title,
        poster = poster,
        backdrop = null,
        date = date,
        rating = rating,
        votes = 100,
        popularity = 0.0,
        role = null,
        episodes = null,
        enTitle = enTitle,
    )
}

data class LuxoFranchise(
    val key: String,
    val kind: LuxoFranchiseKind,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String? = null,
    val parts: List<LuxoFranchisePart> = emptyList(),
    val partCount: Int = parts.size,
)

/** A hand-picked list of series universes (TMDB ids; "m" = movie, "t" = series). */
private data class SeriesGroup(val key: String, val title: String, val members: List<Pair<Char, Int>>)

private val seriesGroups = listOf(
    SeriesGroup("bb", "عالم Breaking Bad", listOf('t' to 1396, 't' to 60059, 'm' to 559969)),
    SeriesGroup("got", "عالم Game of Thrones", listOf('t' to 1399, 't' to 94997, 't' to 224372)),
    SeriesGroup("twd", "عالم The Walking Dead", listOf('t' to 1402, 't' to 62286, 't' to 94305, 't' to 194583, 't' to 211684, 't' to 206586)),
    SeriesGroup("sw", "مسلسلات Star Wars", listOf('t' to 82856, 't' to 115036, 't' to 92830, 't' to 83867, 't' to 114461, 't' to 114478, 't' to 202879)),
    SeriesGroup("mcu", "مسلسلات Marvel", listOf('t' to 85271, 't' to 88396, 't' to 84958, 't' to 88329, 't' to 92749, 't' to 92782, 't' to 114472, 't' to 138501, 't' to 138505)),
    SeriesGroup("boys", "عالم The Boys", listOf('t' to 76479, 't' to 205715, 't' to 108249)),
    SeriesGroup("dexter", "عالم Dexter", listOf('t' to 1405, 't' to 131927, 't' to 219937, 't' to 259909)),
    SeriesGroup("arrow", "عالم Arrowverse", listOf('t' to 1412, 't' to 60735, 't' to 62688, 't' to 62643, 't' to 89247, 't' to 79501)),
    SeriesGroup("csi", "عالم CSI", listOf('t' to 1431, 't' to 1620, 't' to 2458, 't' to 61811, 't' to 112836)),
    SeriesGroup("ncis", "عالم NCIS", listOf('t' to 4614, 't' to 17610, 't' to 61387, 't' to 135934, 't' to 229215)),
    SeriesGroup("chicago", "عالم Chicago", listOf('t' to 44006, 't' to 58841, 't' to 62650, 't' to 67011)),
    SeriesGroup("bbt", "عالم The Big Bang Theory", listOf('t' to 1418, 't' to 71728)),
    SeriesGroup("pb", "Prison Break", listOf('t' to 2288, 'm' to 176241)),
    SeriesGroup("yellowstone", "عالم Yellowstone", listOf('t' to 73586, 't' to 118357, 't' to 157744)),
    SeriesGroup("lotr", "عالم Middle-earth", listOf('m' to 120, 'm' to 121, 'm' to 122, 'm' to 49051, 'm' to 57158, 'm' to 122917, 't' to 84773, 'm' to 839033)),
    SeriesGroup("st", "عالم Star Trek", listOf('t' to 253, 't' to 655, 't' to 580, 't' to 1855, 't' to 314, 't' to 67198, 't' to 85949, 't' to 103516, 't' to 85948)),
    SeriesGroup("bcs", "عالم The Office", listOf('t' to 2316, 't' to 2996)),
    SeriesGroup("peaky", "Peaky Blinders", listOf('t' to 60574, 'm' to 1157302)),
    SeriesGroup("witcher", "عالم The Witcher", listOf('t' to 71912, 't' to 120919, 'm' to 778106)),
    SeriesGroup("money", "عالم La Casa de Papel", listOf('t' to 71446, 't' to 197189)),
)

/** Popular TMDB movie collections (more are added from the viewer's own watch history). */
private val collectionIds = listOf(
    1241, 10, 119, 121938, 9485, 10194, 263, 295, 86311, 131292, 131295, 131296, 284433, 531241, 404609, 87359, 328,
    2344, 264, 230, 8091, 528, 84, 2150, 86066, 131635, 33514, 31562, 645, 1575, 553717, 8945, 748, 173710, 77816,
    89137, 8354, 386382, 313086, 656, 2602, 8650, 726871, 87096, 448150, 558216, 391860, 382685, 295130, 435259,
    86119, 1570, 945, 91361, 304, 86055, 573436, 228446, 115575, 9735, 1709, 87800, 2980,
)

private val animeSeeds = listOf(
    "Bleach", "Naruto", "One Piece", "Shingeki no Kyojin", "Kimetsu no Yaiba", "Jujutsu Kaisen", "Boku no Hero Academia",
    "Dragon Ball", "Fullmetal Alchemist", "Hunter x Hunter (2011)", "Sword Art Online", "Re:Zero kara Hajimeru Isekai Seikatsu",
    "Fate/Zero", "Gintama", "Haikyuu!!", "Mob Psycho 100", "Tokyo Ghoul", "Black Clover", "Fairy Tail", "Code Geass",
    "Steins;Gate", "Bakemonogatari", "JoJo no Kimyou na Bouken", "Chainsaw Man", "SPY×FAMILY", "Vinland Saga", "Overlord",
    "Tensei shitara Slime Datta Ken", "Kaguya-sama wa Kokurasetai", "Kaguya-sama", "Mushoku Tensei", "Dr. STONE",
)

object LuxoFranchiseRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Semaphore(6)
    private val cache = mutableMapOf<String, LuxoFranchise>()
    private var homeCache: Map<LuxoFranchiseKind, List<LuxoFranchise>>? = null

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.o(k: String) = this[k] as? JsonObject
    private fun JsonObject.a(k: String) = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private suspend fun tmdb(endpoint: String, query: Map<String, String> = mapOf("language" to "ar")): JsonObject? {
        val key = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return null
        return gate.withPermit { runCatching { json.parseToJsonElement(httpGetText(buildTmdbUrl(endpoint, key, query))) as? JsonObject }.getOrNull() }
    }

    private suspend fun anilist(query: String, variables: JsonObject = buildJsonObject { }): JsonObject? = runCatching {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()
        val r = httpRequestRaw("POST", "https://graphql.anilist.co", mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body)
        (json.parseToJsonElement(r.body) as? JsonObject)?.o("data")
    }.getOrNull()

    private fun img(path: String?, size: String) = path?.let { "https://image.tmdb.org/t/p/$size$it" }

    /** The «السلاسل» home: movie collections, series universes and anime franchises. */
    suspend fun home(watchedMovieTmdbIds: List<Int>): Map<LuxoFranchiseKind, List<LuxoFranchise>> = withContext(Dispatchers.Default) {
        homeCache?.let { return@withContext it }
        coroutineScope {
            // Collections the viewer already started come first.
            val fromHistory = watchedMovieTmdbIds.distinct().take(40).map { id ->
                async { tmdb("movie/$id", mapOf("language" to "en-US"))?.o("belongs_to_collection")?.i("id") }
            }
            val moviesD = async {
                val ids = (fromHistory.awaitAll().filterNotNull() + collectionIds).distinct()
                ids.map { id -> async { collection(id) } }.awaitAll().filterNotNull().filter { it.parts.size >= 2 }
            }
            val seriesD = async { seriesGroups.map { g -> async { group(g) } }.awaitAll().filterNotNull() }
            val animeD = async { animeCards() }
            val result = mapOf(
                LuxoFranchiseKind.MOVIES to moviesD.await(),
                LuxoFranchiseKind.SERIES to seriesD.await(),
                LuxoFranchiseKind.ANIME to animeD.await(),
            )
            homeCache = result
            result
        }
    }

    suspend fun collection(id: Int): LuxoFranchise? {
        cache["collection:$id"]?.let { return it }
        val o = tmdb("collection/$id") ?: return null
        val en = tmdb("collection/$id", mapOf("language" to "en-US"))
        val enTitles = en?.a("parts")?.associate { (it.i("id") ?: 0) to it.s("title") }.orEmpty()
        val parts = o.a("parts").mapNotNull { p ->
            val pid = p.i("id") ?: return@mapNotNull null
            LuxoFranchisePart(
                key = "movie:$pid",
                title = p.s("title") ?: enTitles[pid] ?: return@mapNotNull null,
                enTitle = enTitles[pid],
                date = p.s("release_date"),
                poster = img(p.s("poster_path"), "w342"),
                rating = p.d("vote_average") ?: 0.0,
                mediaType = "movie",
                tmdbId = pid,
            )
        }.sortedBy { it.date ?: "9999" }
        val f = LuxoFranchise(
            key = "collection:$id",
            kind = LuxoFranchiseKind.MOVIES,
            title = o.s("name") ?: en?.s("name") ?: return null,
            poster = img(o.s("poster_path") ?: en?.s("poster_path"), "w342"),
            backdrop = img(o.s("backdrop_path") ?: en?.s("backdrop_path"), "w780"),
            overview = o.s("overview") ?: en?.s("overview"),
            parts = parts,
        )
        cache[f.key] = f
        return f
    }

    private suspend fun group(g: SeriesGroup): LuxoFranchise? = coroutineScope {
        cache["group:${g.key}"]?.let { return@coroutineScope it }
        val parts = g.members.map { (type, id) ->
            async {
                val media = if (type == 'm') "movie" else "tv"
                val o = tmdb("$media/$id") ?: return@async null
                val en = tmdb("$media/$id", mapOf("language" to "en-US"))
                LuxoFranchisePart(
                    key = "$media:$id",
                    title = o.s("title") ?: o.s("name") ?: en?.s("title") ?: en?.s("name") ?: return@async null,
                    enTitle = en?.s("title") ?: en?.s("name"),
                    date = o.s("release_date") ?: o.s("first_air_date"),
                    poster = img(o.s("poster_path"), "w342"),
                    rating = o.d("vote_average") ?: 0.0,
                    mediaType = media,
                    tmdbId = id,
                    note = if (media == "movie") "فيلم" else o.i("number_of_seasons")?.let { "$it مواسم" },
                )
            }
        }.awaitAll().filterNotNull().sortedBy { it.date ?: "9999" }
        if (parts.size < 2) return@coroutineScope null
        val first = parts.first()
        val backdrop = tmdb("${first.mediaType}/${first.tmdbId}")?.s("backdrop_path")
        LuxoFranchise("group:${g.key}", LuxoFranchiseKind.SERIES, g.title, first.poster, img(backdrop, "w780"), null, parts).also { cache[it.key] = it }
    }

    private suspend fun animeCards(): List<LuxoFranchise> {
        // One AniList call for every card (GraphQL aliases).
        val q = buildString {
            append("query {")
            animeSeeds.forEachIndexed { i, seed ->
                append(" a$i: Media(search: ${JsonPrimitive(seed)}, type: ANIME, sort: POPULARITY_DESC) { id title { romaji english native } coverImage { large } bannerImage relations { edges { relationType } } }")
            }
            append(" }")
        }
        val data = anilist(q) ?: return emptyList()
        return animeSeeds.indices.mapNotNull { i ->
            val m = data.o("a$i") ?: return@mapNotNull null
            val id = m.i("id") ?: return@mapNotNull null
            val title = m.o("title")
            val count = m.o("relations")?.a("edges")?.count { it.s("relationType") in setOf("PREQUEL", "SEQUEL", "SIDE_STORY", "SPIN_OFF", "PARENT") } ?: 0
            LuxoFranchise(
                key = "anime:$id",
                kind = LuxoFranchiseKind.ANIME,
                title = title?.s("english") ?: title?.s("romaji") ?: return@mapNotNull null,
                poster = m.o("coverImage")?.s("large"),
                backdrop = m.s("bannerImage"),
                partCount = count + 1,
            )
        }.distinctBy { it.key }
    }

    /** The whole anime franchise: follows prequels, sequels, side stories and movies (3 levels deep). */
    suspend fun anime(id: Int): LuxoFranchise? = withContext(Dispatchers.Default) {
        cache["anime:$id"]?.let { return@withContext it }
        val node = "id type format episodes title { romaji english native } startDate { year month day } coverImage { large } bannerImage averageScore"
        val q = "query (\$id: Int) { Media(id: \$id, type: ANIME) { $node relations { edges { relationType node { $node relations { edges { relationType node { $node relations { edges { relationType node { $node } } } } } } } } } } }"
        val data = anilist(q, buildJsonObject { put("id", id) }) ?: return@withContext null
        val root = data.o("Media") ?: return@withContext null
        val found = linkedMapOf<Int, Pair<JsonObject, String?>>()
        fun walk(m: JsonObject, relation: String?, depth: Int) {
            val mid = m.i("id") ?: return
            if (m.s("type") != "ANIME") return
            if (found.containsKey(mid)) return
            found[mid] = m to relation
            if (depth >= 3) return
            m.o("relations")?.a("edges")?.forEach { e ->
                val rel = e.s("relationType")
                if (rel in setOf("PREQUEL", "SEQUEL", "SIDE_STORY", "SPIN_OFF", "PARENT", "SUMMARY", "ALTERNATIVE")) {
                    e.o("node")?.let { walk(it, rel, depth + 1) }
                }
            }
        }
        walk(root, null, 0)
        val parts = found.values.map { (m, rel) ->
            val t = m.o("title")
            val sd = m.o("startDate")
            val date = sd?.i("year")?.let { y -> "$y-${(sd.i("month") ?: 1).toString().padStart(2, '0')}-${(sd.i("day") ?: 1).toString().padStart(2, '0')}" }
            val format = m.s("format")
            val optional = rel in setOf("SIDE_STORY", "SUMMARY", "SPIN_OFF", "ALTERNATIVE") || format in setOf("OVA", "ONA", "SPECIAL", "MUSIC")
            LuxoFranchisePart(
                key = "anilist:${m.i("id")}",
                title = t?.s("english") ?: t?.s("romaji") ?: "?",
                enTitle = t?.s("romaji"),
                date = date,
                poster = m.o("coverImage")?.s("large"),
                rating = (m.i("averageScore") ?: 0) / 10.0,
                mediaType = if (format == "MOVIE") "movie" else "tv",
                tmdbId = null,
                note = listOfNotNull(
                    when (format) {
                        "MOVIE" -> "فيلم"
                        "OVA", "ONA" -> format
                        "SPECIAL" -> "حلقة خاصة"
                        "TV", "TV_SHORT" -> m.i("episodes")?.let { "$it حلقة" }
                        else -> null
                    },
                    if (optional) "يمكن تخطيه" else null,
                ).joinToString(" · ").ifBlank { null },
                optional = optional,
            )
        }.sortedBy { it.date ?: "9999" }
        val t = root.o("title")
        LuxoFranchise(
            key = "anime:$id",
            kind = LuxoFranchiseKind.ANIME,
            title = t?.s("english") ?: t?.s("romaji") ?: "Anime",
            poster = root.o("coverImage")?.s("large"),
            backdrop = root.s("bannerImage"),
            parts = parts,
        ).also { cache[it.key] = it }
    }

    suspend fun load(key: String): LuxoFranchise? = when {
        key.startsWith("collection:") -> key.removePrefix("collection:").toIntOrNull()?.let { collection(it) }
        key.startsWith("group:") -> seriesGroups.firstOrNull { it.key == key.removePrefix("group:") }?.let { group(it) }
        key.startsWith("anime:") -> key.removePrefix("anime:").toIntOrNull()?.let { anime(it) }
        else -> null
    }

    /** Which universe a series belongs to (for the banner on its page). */
    fun groupForTmdb(mediaType: String, id: Int): String? {
        val c = if (mediaType == "movie") 'm' else 't'
        return seriesGroups.firstOrNull { g -> g.members.any { it.first == c && it.second == id } }?.let { "group:${it.key}" }
    }

    /** Resolves an AniList part to a TMDB title to open it in the app. */
    suspend fun resolveToMeta(part: LuxoFranchisePart): MetaPreview? {
        if (part.tmdbId != null) {
            return MetaPreview(id = "tmdb:${part.tmdbId}", type = if (part.mediaType == "movie") "movie" else "series", name = part.title, poster = part.poster)
        }
        val media = if (part.mediaType == "movie") "movie" else "tv"
        for (q in listOfNotNull(part.title, part.enTitle).distinct()) {
            val res = tmdb("search/$media", mapOf("query" to urlEncode(q.substringBefore(":").trim()), "language" to "ar")) ?: continue
            val hit = res.a("results").firstOrNull() ?: continue
            val id = hit.i("id") ?: continue
            return MetaPreview(id = "tmdb:$id", type = if (media == "movie") "movie" else "series", name = hit.s("title") ?: hit.s("name") ?: part.title, poster = part.poster)
        }
        return null
    }

    /** Story-chronological order from «رفيق Luxo» (cached); null when it can't tell. */
    private val chronoCache = mutableMapOf<String, List<Int>>()

    suspend fun chronologicalOrder(f: LuxoFranchise): List<Int>? {
        chronoCache[f.key]?.let { return it }
        val apiKey = com.nuvio.app.features.details.LuxoCompanionApi.loadKey() ?: return null
        val list = f.parts.mapIndexed { i, p -> "${i + 1}. ${p.enTitle ?: p.title} (${p.year ?: "?"})" }.joinToString("\n")
        val prompt = "رتّب أجزاء سلسلة «${f.title}» حسب الترتيب الزمني لأحداث القصة (وليس تاريخ العرض). " +
            "أعد JSON فقط بهذا الشكل: {\"order\":[أرقام الأجزاء بالترتيب]} ويجب أن تحتوي كل الأرقام مرة واحدة.\n\n$list"
        val answer = com.nuvio.app.features.details.LuxoCompanionApi.ask(apiKey, prompt)
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val order = runCatching {
            ((json.parseToJsonElement(answer.substring(start, end + 1)) as JsonObject)["order"] as JsonArray)
                .mapNotNull { (it as? JsonPrimitive)?.intOrNull?.minus(1) }
        }.getOrNull() ?: return null
        if (order.sorted() != f.parts.indices.toList()) return null
        chronoCache[f.key] = order
        return order
    }
}

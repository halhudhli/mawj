package com.mawj.tv.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

/** TMDB genre names in Arabic (films and series share most ids). */
object Genres {
    private val names = mapOf(
        28 to "أكشن", 12 to "مغامرة", 16 to "رسوم متحركة", 35 to "كوميديا", 80 to "جريمة", 99 to "وثائقي", 18 to "دراما",
        10751 to "عائلي", 14 to "فانتازيا", 36 to "تاريخ", 27 to "رعب", 10402 to "موسيقى", 9648 to "غموض", 10749 to "رومانسي",
        878 to "خيال علمي", 10770 to "فيلم تلفزيوني", 53 to "إثارة", 10752 to "حرب", 37 to "غربي", 10759 to "أكشن ومغامرة",
        10762 to "أطفال", 10763 to "أخبار", 10764 to "واقع", 10765 to "خيال علمي وفانتازيا", 10766 to "مسلسل يومي",
        10767 to "حوارات", 10768 to "حرب وسياسة",
    )

    fun name(id: Int): String? = names[id]
}

/** One card of «الجزر» (or a mood): a title and the TMDB query that fills it. */
@Immutable
data class Island(
    val title: String,
    val kind: String,
    val query: String,
    val path: String = "/discover/$kind",
    /** Opens a person or a studio instead of a list. */
    val personId: Int? = null,
    val companyId: Int? = null,
)

@Immutable
data class IslandTab(val title: String, val cards: List<Island>)

@Immutable
data class EpisodeScore(val number: Int, val name: String, val rating: Double, val still: String?, val runtime: Int?)

@Immutable
data class Provider(val name: String, val logo: String?, val kind: String)

@Immutable
data class TitleFacts(
    val tmdbId: Int,
    val kind: String,
    val runtime: Int?,
    val episodes: Int?,
    val seasons: Int?,
    val genres: List<String>,
    val creators: List<String>,
    val networks: List<Company>,
    val collectionId: Int?,
    val collectionName: String?,
    val status: String?,
    val tagline: String?,
    val language: String?,
    val rating: Double,
    val votes: Int,
    val year: String?,
)

@Immutable
data class PersonFull(
    val id: Int,
    val name: String,
    val englishName: String?,
    val job: String,
    val birthday: String?,
    val deathday: String?,
    val place: String?,
    val bio: String,
    val photos: List<String>,
    val works: List<TmdbWork>,
) {
    val age: Int? get() = birthday?.let { b ->
        runCatching { ChronoUnit.YEARS.between(LocalDate.parse(b), deathday?.let(LocalDate::parse) ?: LocalDate.now()).toInt() }.getOrNull()
    }
}

/** Lists and facts from TMDB for the TV's pages. Everything is cached by [Tmdb]. */
object Discover {
    private const val REGION = "SA"

    suspend fun list(path: String, query: String = "", kind: String? = null, keepNoPoster: Boolean = false): List<TmdbWork> {
        val o = Tmdb.get(path, "language=ar&$query") ?: return emptyList()
        return (o["results"] as? JsonArray ?: o["parts"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let { j -> Tmdb.work(j, kind) } }
            .filter { keepNoPoster || it.poster != null }
    }

    suspend fun trending(kind: String) = list("/trending/$kind/week", kind = kind)
    suspend fun topRated(kind: String) = list("/$kind/top_rated", kind = kind)
    suspend fun discover(kind: String, query: String): List<TmdbWork> {
        // «ملف الأطفال»: discovery keeps to what suits children.
        val kids = if (!Store.feature("kidsMode")) "" else if (kind == "movie") "&certification_country=US&certification.lte=PG" else "&with_genres=10762|10751"
        return list("/discover/$kind", "sort_by=popularity.desc&vote_count.gte=40&include_adult=false&$query$kids", kind)
    }

    suspend fun of(i: Island): List<TmdbWork> = list(i.path, i.query, i.kind)

    // ---------------- moods (بوصلة المزاج) ----------------

    val moods: List<Pair<String, Pair<String, String>>> = listOf(
        "هادئ" to ("with_genres=10751|10402&vote_average.gte=6.8" to "with_genres=10751|35&vote_average.gte=7"),
        "مشوّق" to ("with_genres=53|9648&vote_average.gte=6.8" to "with_genres=9648|80&vote_average.gte=7.4"),
        "يُضحك" to ("with_genres=35&vote_average.gte=6.6" to "with_genres=35&vote_average.gte=7.4"),
        "مؤثر" to ("with_genres=18|10749&vote_average.gte=7.6&vote_count.gte=800" to "with_genres=18&vote_average.gte=8"),
        "يدعو للتفكير" to ("with_genres=878|99&vote_average.gte=7.3&vote_count.gte=500" to "with_genres=10765|99&vote_average.gte=7.8"),
    )

    /** «ماذا نشاهد الليلة؟»: a mood, the time there is, and film or series. */
    suspend fun tonight(mood: Int, minutes: Int, kind: String): List<TmdbWork> {
        val (m, t) = moods.getOrElse(mood) { moods[0] }.second
        return if (kind == "movie") discover("movie", "$m&with_runtime.lte=${minutes.coerceAtLeast(80)}&vote_count.gte=300")
        else discover("tv", "$t&vote_count.gte=150")
    }

    // ---------------- «الجزر» ----------------

    val islands: List<IslandTab> = listOf(
        IslandTab("حسب مزاجك", listOf(
            Island("راحة البال", "movie", "with_genres=10751|16&vote_average.gte=7"),
            Island("يلحس العقول", "movie", "with_genres=878|9648&vote_average.gte=7.5&vote_count.gte=1500"),
            Island("إثارة بلا حدود", "movie", "with_genres=53|28&vote_average.gte=7&vote_count.gte=1500"),
            Island("ملاحم سينمائية", "movie", "with_genres=12|36|10752&with_runtime.gte=140&vote_average.gte=7.4"),
            Island("جرعة سعادة", "movie", "with_genres=35|10751&vote_average.gte=7"),
            Island("بكاء جماعي", "movie", "with_genres=18|10749&vote_average.gte=7.8&vote_count.gte=1000"),
        )),
        IslandTab("شبكات التلفزيون", listOf(
            Island("Netflix", "tv", "with_networks=213"), Island("HBO", "tv", "with_networks=49"), Island("AMC", "tv", "with_networks=174"),
            Island("Apple TV+", "tv", "with_networks=2552"), Island("Disney+", "tv", "with_networks=2739"), Island("Prime Video", "tv", "with_networks=1024"),
            Island("BBC One", "tv", "with_networks=4"), Island("FX", "tv", "with_networks=88"), Island("Hulu", "tv", "with_networks=453"),
        )),
        IslandTab("منصات البث", listOf(
            Island("Netflix", "movie", "with_watch_providers=8&watch_region=US"), Island("Prime Video", "movie", "with_watch_providers=9&watch_region=US"),
            Island("Disney+", "movie", "with_watch_providers=337&watch_region=US"), Island("Apple TV+", "movie", "with_watch_providers=350&watch_region=US"),
            Island("Max", "movie", "with_watch_providers=1899&watch_region=US"), Island("Hulu", "movie", "with_watch_providers=15&watch_region=US"),
        )),
        IslandTab("التصنيفات", listOf(28, 35, 18, 27, 878, 16, 80, 10749, 10752, 36, 14, 99).map { g ->
            Island(Genres.name(g) ?: "", "movie", "with_genres=$g&vote_count.gte=300")
        }),
        IslandTab("مقتبس من", listOf(
            Island("روايات وكتب", "movie", "with_keywords=818&vote_count.gte=300"), Island("قصة حقيقية", "movie", "with_keywords=9672&vote_count.gte=300"),
            Island("ألعاب فيديو", "movie", "with_keywords=41645"), Island("كوميكس", "movie", "with_keywords=9717&vote_count.gte=300"),
            Island("مانغا", "tv", "with_keywords=13141"), Island("روايات للتلفزيون", "tv", "with_keywords=818&vote_count.gte=100"),
        )),
        IslandTab("النخبة", listOf(
            Island("الأعلى تقييماً · أفلام", "movie", "", "/movie/top_rated"), Island("الأعلى تقييماً · مسلسلات", "tv", "", "/tv/top_rated"),
            Island("إجماع النقاد", "movie", "sort_by=vote_average.desc&vote_count.gte=8000"), Island("جواهر مخفية", "movie", "sort_by=vote_average.desc&vote_count.gte=300&vote_count.lte=2500&vote_average.gte=7.5"),
        )),
        IslandTab("من كل جيل", listOf(1970, 1980, 1990, 2000, 2010, 2020).map { y ->
            Island("أفلام ${y}s", "movie", "primary_release_date.gte=$y-01-01&primary_release_date.lte=${y + 9}-12-31&vote_count.gte=1000")
        }),
        IslandTab("أنمي", listOf(
            Island("أنمي يُعرض الآن", "tv", "with_genres=16&with_original_language=ja&air_date.gte=${LocalDate.now().minusDays(7)}"),
            Island("أشهر الأنمي", "tv", "with_genres=16&with_original_language=ja&vote_count.gte=300"),
            Island("أفلام أنمي", "movie", "with_genres=16&with_original_language=ja&vote_count.gte=200"),
            Island("الأعلى تقييماً", "tv", "with_genres=16&with_original_language=ja&sort_by=vote_average.desc&vote_count.gte=500"),
        )),
        IslandTab("السينما العالمية", listOf(
            Island("السينما العربية", "movie", "with_original_language=ar&vote_count.gte=20"), Island("الكورية", "movie", "with_original_language=ko&vote_count.gte=200"),
            Island("اليابانية", "movie", "with_original_language=ja&vote_count.gte=200&without_genres=16"), Island("الإسبانية", "movie", "with_original_language=es&vote_count.gte=300"),
            Island("الفرنسية", "movie", "with_original_language=fr&vote_count.gte=300"), Island("الهندية", "movie", "with_original_language=hi&vote_count.gte=200"),
            Island("التركية", "tv", "with_original_language=tr&vote_count.gte=30"), Island("الدراما الكورية", "tv", "with_original_language=ko&vote_count.gte=100"),
        )),
        IslandTab("الوثائقية", listOf(
            Island("أفلام وثائقية", "movie", "with_genres=99&vote_count.gte=150"), Island("سلاسل وثائقية", "tv", "with_genres=99&vote_count.gte=80"),
            Island("الطبيعة", "tv", "with_genres=99&with_keywords=221355"), Island("تلفزيون الواقع", "tv", "with_genres=10764&vote_count.gte=40"),
        )),
        IslandTab("الاستوديوهات", listOf(
            Island("Pixar", "movie", "with_companies=3", companyId = 3), Island("Marvel Studios", "movie", "with_companies=420", companyId = 420),
            Island("A24", "movie", "with_companies=41077", companyId = 41077), Island("Studio Ghibli", "movie", "with_companies=10342", companyId = 10342),
            Island("Blumhouse", "movie", "with_companies=3172", companyId = 3172), Island("Lucasfilm", "movie", "with_companies=1", companyId = 1),
        )),
        IslandTab("المخرجون", listOf(
            Island("كريستوفر نولان", "movie", "with_crew=525", personId = 525), Island("دوني فيلنوف", "movie", "with_crew=137427", personId = 137427),
            Island("مارتن سكورسيزي", "movie", "with_crew=1032", personId = 1032), Island("بونغ جون-هو", "movie", "with_crew=21684", personId = 21684),
            Island("هاياو ميازاكي", "movie", "with_crew=608", personId = 608), Island("ديفيد فينشر", "movie", "with_crew=7467", personId = 7467),
        )),
    )

    /** Upcoming films with the days left, for «العد التنازلي». */
    suspend fun countdown(): List<Pair<TmdbWork, Long>> {
        val today = LocalDate.now()
        return list("/movie/upcoming", "region=US", "movie").mapNotNull { w ->
            val d = w.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, d)
            if (days in 1..120) w to days else null
        }.sortedBy { it.second }.take(8)
    }

    // ---------------- a title ----------------

    suspend fun tmdbIdOf(meta: MetaPreview): Int? = when {
        meta.id.startsWith("tmdb:") -> meta.id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
        meta.id.startsWith("tt") -> Tmdb.idFor(meta.id.substringBefore(':'), meta.type)
        else -> null
    }

    fun kindOf(meta: MetaPreview) = if (meta.type == "series") "tv" else "movie"

    suspend fun facts(meta: MetaPreview): TitleFacts? {
        val id = tmdbIdOf(meta) ?: return null
        val kind = kindOf(meta)
        val o = Tmdb.get("/$kind/$id", "language=ar&append_to_response=credits") ?: return null
        val crew = ((o["credits"] as? JsonObject)?.get("crew") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val creators = if (kind == "tv") (o["created_by"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }
        else crew.filter { it.str("job") == "Director" }.mapNotNull { it.str("name") }
        val coll = o["belongs_to_collection"] as? JsonObject
        return TitleFacts(
            tmdbId = id, kind = kind,
            runtime = o.int("runtime") ?: (o["episode_run_time"] as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.content?.toIntOrNull() },
            episodes = o.int("number_of_episodes"), seasons = o.int("number_of_seasons"),
            genres = (o["genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
            creators = creators.take(3),
            networks = (o["networks"] as? JsonArray).orEmpty().mapNotNull { n ->
                val j = n as? JsonObject ?: return@mapNotNull null
                Company(j.int("id") ?: return@mapNotNull null, j.str("name") ?: "", Tmdb.img(j.str("logo_path"), "w300"))
            },
            collectionId = coll?.int("id"), collectionName = coll?.str("name"),
            status = o.str("status"), tagline = o.str("tagline")?.takeIf { it.isNotBlank() },
            language = o.str("original_language"),
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, votes = o.int("vote_count") ?: 0,
            year = (o.str("release_date") ?: o.str("first_air_date"))?.take(4),
        )
    }

    suspend fun season(tvId: Int, season: Int): List<EpisodeScore> {
        val o = Tmdb.get("/tv/$tvId/season/$season", "language=ar") ?: return emptyList()
        return (o["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
            val j = e as? JsonObject ?: return@mapNotNull null
            EpisodeScore(j.int("episode_number") ?: return@mapNotNull null, j.str("name") ?: "",
                (j["vote_average"] as? JsonPrimitive)?.doubleOrNull ?: 0.0, Tmdb.img(j.str("still_path"), "w300"), j.int("runtime"))
        }
    }

    suspend fun providers(kind: String, id: Int): List<Provider> {
        val r = (Tmdb.get("/$kind/$id/watch/providers")?.get("results") as? JsonObject) ?: return emptyList()
        val region = (r[REGION] ?: r["AE"] ?: r["US"]) as? JsonObject ?: return emptyList()
        return listOf("flatrate" to "اشتراك", "free" to "مجاني", "rent" to "إيجار", "buy" to "شراء").flatMap { (k, label) ->
            (region[k] as? JsonArray).orEmpty().mapNotNull { p ->
                val j = p as? JsonObject ?: return@mapNotNull null
                Provider(j.str("provider_name") ?: return@mapNotNull null, Tmdb.img(j.str("logo_path"), "w92"), label)
            }
        }.distinctBy { it.name }.take(10)
    }

    /** The age rating: Saudi first, then the US. */
    suspend fun certification(kind: String, id: Int): String? {
        return if (kind == "movie") {
            val list = (Tmdb.get("/movie/$id/release_dates")?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun cert(c: String) = list.firstOrNull { it.str("iso_3166_1") == c }?.let { (it["release_dates"] as? JsonArray).orEmpty() }
                ?.mapNotNull { (it as? JsonObject)?.str("certification")?.takeIf(String::isNotBlank) }?.firstOrNull()
            cert(REGION) ?: cert("US")
        } else {
            val list = (Tmdb.get("/tv/$id/content_ratings")?.get("results") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            (list.firstOrNull { it.str("iso_3166_1") == REGION } ?: list.firstOrNull { it.str("iso_3166_1") == "US" })?.str("rating")
        }
    }

    suspend fun similar(kind: String, id: Int) = list("/$kind/$id/recommendations", kind = kind).take(16)

    suspend fun collection(id: Int) = list("/collection/$id", kind = "movie").sortedBy { it.date ?: "9999" }

    // ---------------- a person ----------------

    suspend fun person(id: Int): PersonFull? {
        val o = Tmdb.get("/person/$id", "append_to_response=images,combined_credits&language=ar") ?: return null
        val en = Tmdb.get("/person/$id", "language=en")
        val bio = o.str("biography")?.takeIf { it.isNotBlank() } ?: en?.str("biography") ?: ""
        val photos = (((o["images"] as? JsonObject)?.get("profiles")) as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("file_path") }.take(8).mapNotNull { Tmdb.img(it, "h632") }
            .ifEmpty { listOfNotNull(Tmdb.img(o.str("profile_path"), "h632")) }
        val credits = o["combined_credits"] as? JsonObject
        val dept = o.str("known_for_department")
        val cast = (credits?.get("cast") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { j -> Tmdb.work(j) } }
        val crew = (credits?.get("crew") as? JsonArray).orEmpty().mapNotNull { j -> (j as? JsonObject)?.takeIf { it.str("job") == "Director" }?.let { Tmdb.work(it) } }
        val works = (if (dept == "Directing") crew + cast else cast + crew)
            .filter { it.poster != null }
            .distinctBy { it.type + it.tmdbId }
        val name = o.str("name") ?: ""
        return PersonFull(
            id = id, name = name, englishName = en?.str("name")?.takeIf { it != name },
            job = when (dept) { "Directing" -> "مخرج"; "Writing" -> "كاتب"; "Production" -> "منتج"; else -> "ممثل" },
            birthday = o.str("birthday"), deathday = o.str("deathday"), place = o.str("place_of_birth"),
            bio = bio, photos = photos, works = works,
        )
    }

    suspend fun popularPeople(): List<CastMember> =
        (Tmdb.get("/person/popular", "language=ar")?.get("results") as? JsonArray).orEmpty().mapNotNull { p ->
            val j = p as? JsonObject ?: return@mapNotNull null
            CastMember(j.int("id") ?: return@mapNotNull null, j.str("name") ?: return@mapNotNull null, "", Tmdb.img(j.str("profile_path"), "w185"))
        }
}

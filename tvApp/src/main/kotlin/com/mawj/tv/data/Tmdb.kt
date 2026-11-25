package com.mawj.tv.data

import androidx.compose.runtime.Immutable
import com.mawj.tv.BuildConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class CastMember(val id: Int, val name: String, val role: String, val photo: String?)

@Immutable
data class Company(val id: Int, val name: String, val logo: String?)

@Immutable
data class Person(
    val id: Int,
    val name: String,
    val job: String,
    val birthday: String?,
    val place: String?,
    val bio: String,
    val photos: List<String>,
    val works: List<TmdbWork>,
)

@Immutable
data class CompanyPage(val company: Company, val country: String?, val works: List<TmdbWork>)

/** A title from TMDB; opening it looks up its IMDb id so the addons can serve it. */
@Immutable
data class TmdbWork(val tmdbId: Int, val type: String, val name: String, val poster: String?, val backdrop: String?, val year: String?, val rating: Double, val genres: List<Int> = emptyList(), val overview: String? = null, val date: String? = null, val votes: Int = 0)

/** Cast photos, actor and studio pages. Answers cached in memory; nothing if no key is set. */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p"
    private val key = BuildConfig.TMDB_KEY.trim()
    val enabled: Boolean get() = key.isNotEmpty()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, JsonObject>()

    internal suspend fun get(path: String, query: String = ""): JsonObject? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val bearer = key.length > 60
        val url = "$API$path?${if (bearer) "" else "api_key=$key&"}$query"
        cache[url]?.let { return@withContext it }
        val body = runCatching {
            val r = Addons.http.get(url) { if (bearer) header("Authorization", "Bearer $key") }
            if (r.status.isSuccess()) r.bodyAsText() else null
        }.getOrNull() ?: return@withContext null
        (runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull())?.also { cache[url] = it }
    }

    fun img(path: String?, size: String): String? = path?.takeIf { it.isNotBlank() }?.let { "$IMG/$size$it" }

    /** IMDb id (tt…) to TMDB id. */
    suspend fun idFor(imdbId: String, type: String): Int? {
        if (!imdbId.startsWith("tt")) return null
        val o = get("/find/$imdbId", "external_source=imdb_id") ?: return null
        val list = (o[if (type == "series") "tv_results" else "movie_results"] as? JsonArray)
            ?: (o["movie_results"] as? JsonArray)
        return ((list?.firstOrNull() as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull
    }

    /** Cast (with photos) and production companies (with logos) of a title. */
    /** A title's posters for the slow change on the home: Arabic ones first, then English or text-free ones. */
    data class PosterSet(val arabic: List<String>, val others: List<String>) {
        fun isNotEmpty() = arabic.isNotEmpty() || others.isNotEmpty()
    }

    private val posterSets = ConcurrentHashMap<String, PosterSet>()

    suspend fun posters(meta: MetaPreview): PosterSet {
        posterSets[meta.id]?.let { return it }
        val empty = PosterSet(emptyList(), emptyList())
        if (!enabled) return empty
        val id = when {
            meta.id.startsWith("tt") -> idFor(meta.id.substringBefore(':'), meta.type)
            meta.id.startsWith("tmdb:") -> meta.id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
            else -> null
        } ?: return empty.also { posterSets[meta.id] = it }
        val kind = if (meta.type == "series") "tv" else "movie"
        val o = get("/$kind/$id/images", "include_image_language=ar,en,null") ?: return empty
        val list = (o["posters"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun pick(filter: (String?) -> Boolean) = list
            .filter { filter((it["iso_639_1"] as? JsonPrimitive)?.contentOrNull) }
            .sortedByDescending { (it["vote_average"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0 }
            .mapNotNull { img((it["file_path"] as? JsonPrimitive)?.contentOrNull, "w500") }
            .take(2)
        return PosterSet(pick { it == "ar" }, pick { it == "en" || it == null }).also { posterSets[meta.id] = it }
    }

    suspend fun credits(imdbId: String, type: String): Pair<List<CastMember>, List<Company>> {
        val id = idFor(imdbId, type) ?: return emptyList<CastMember>() to emptyList()
        val kind = if (type == "series") "tv" else "movie"
        val o = get("/$kind/$id", "append_to_response=${if (kind == "tv") "aggregate_credits" else "credits"}&language=ar") ?: return emptyList<CastMember>() to emptyList()
        val creditsObj = (o["credits"] ?: o["aggregate_credits"]) as? JsonObject
        val cast = (creditsObj?.get("cast") as? JsonArray).orEmpty().mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            val role = co.str("character") ?: ((co["roles"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("character") ?: ""
            CastMember(co.int("id") ?: return@mapNotNull null, co.str("name") ?: return@mapNotNull null, role, img(co.str("profile_path"), "w185"))
        }.take(16)
        val companies = (o["production_companies"] as? JsonArray).orEmpty().mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            Company(co.int("id") ?: return@mapNotNull null, co.str("name") ?: "", img(co.str("logo_path"), "w300"))
        }.take(6)
        return cast to companies
    }

    suspend fun person(id: Int): Person? {
        val o = get("/person/$id", "append_to_response=images,combined_credits&language=ar") ?: return null
        val bio = o.str("biography") ?: get("/person/$id", "language=en")?.str("biography") ?: ""
        val photos = (((o["images"] as? JsonObject)?.get("profiles")) as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("file_path") }.take(6).mapNotNull { img(it, "h632") }
            .ifEmpty { listOfNotNull(img(o.str("profile_path"), "h632")) }
        val works = (((o["combined_credits"] as? JsonObject)?.get("cast")) as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::work) }
            .distinctBy { it.type + it.tmdbId }
            .sortedByDescending { it.rating }
            .take(30)
        return Person(
            id = id,
            name = o.str("name") ?: "",
            job = when (o.str("known_for_department")) { "Directing" -> "إخراج"; "Writing" -> "كتابة"; "Production" -> "إنتاج"; else -> "تمثيل" },
            birthday = o.str("birthday"),
            place = o.str("place_of_birth"),
            bio = bio,
            photos = photos,
            works = works,
        )
    }

    /** A studio and its titles; [sort] is "new" (latest) or "top" (best rated). */
    suspend fun company(id: Int, sort: String): CompanyPage? {
        val c = get("/company/$id") ?: return null
        val order = if (sort == "top") "vote_average.desc&vote_count.gte=300" else "primary_release_date.desc&vote_count.gte=20"
        val movies = (get("/discover/movie", "with_companies=$id&sort_by=$order&language=ar")?.get("results") as? JsonArray).orEmpty()
        val tvOrder = if (sort == "top") "vote_average.desc&vote_count.gte=150" else "first_air_date.desc&vote_count.gte=10"
        val shows = (get("/discover/tv", "with_companies=$id&sort_by=$tvOrder&language=ar")?.get("results") as? JsonArray).orEmpty()
        val works = (movies.mapNotNull { (it as? JsonObject)?.let { o -> work(o, "movie") } } + shows.mapNotNull { (it as? JsonObject)?.let { o -> work(o, "tv") } })
            .let { list -> if (sort == "top") list.sortedByDescending { it.rating } else list.sortedByDescending { it.year ?: "" } }
        return CompanyPage(Company(id, c.str("name") ?: "", img(c.str("logo_path"), "w500")), c.str("origin_country"), works)
    }

    /** The IMDb id of a TMDB title, to open it through the addons. */
    suspend fun imdbOf(w: TmdbWork): String? {
        val o = get("/${w.type}/${w.tmdbId}/external_ids") ?: return null
        return o.str("imdb_id")
    }

    internal fun work(o: JsonObject, forcedType: String? = null): TmdbWork? {
        val type = forcedType ?: o.str("media_type") ?: return null
        if (type != "movie" && type != "tv") return null
        return TmdbWork(
            tmdbId = o.int("id") ?: return null,
            type = type,
            name = o.str("title") ?: o.str("name") ?: return null,
            poster = img(o.str("poster_path"), "w342"),
            backdrop = img(o.str("backdrop_path"), "w780"),
            year = (o.str("release_date") ?: o.str("first_air_date"))?.take(4),
            rating = (o["vote_average"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            genres = (o["genre_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.intOrNull },
            overview = o.str("overview")?.takeIf { it.isNotBlank() },
            date = o.str("release_date") ?: o.str("first_air_date"),
            votes = o.int("vote_count") ?: 0,
        )
    }

    internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
}

/** Opens a TMDB title in the app (through its IMDb id, which the addons understand). */
suspend fun TmdbWork.toPreview(): MetaPreview? {
    val imdb = Tmdb.imdbOf(this) ?: return null
    return MetaPreview(
        id = imdb, type = if (type == "tv") "series" else "movie", name = name, poster = poster, background = backdrop,
        logo = null, year = year, rating = if (rating > 0) String.format(java.util.Locale.US, "%.1f", rating) else null,
        description = null, genres = emptyList(),
    )
}


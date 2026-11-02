package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.details.MetaTrailer
import com.nuvio.app.features.tmdb.TmdbService
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/* ─────────────── Models ─────────────── */

data class LuxoImage(val path: String, val width: Int, val height: Int, val language: String?) {
    fun url(size: String) = "https://image.tmdb.org/t/p/$size$path"
}

data class LuxoTrack(
    val title: String,
    val artist: String,
    val durationSec: Int,
    val previewUrl: String?,
    /** "Deezer", "Apple Music", "AnimeThemes". */
    val source: String = "",
    /** Opening/ending video (AnimeThemes). */
    val videoUrl: String? = null,
)

data class LuxoAlbum(
    val title: String,
    val artist: String,
    val cover: String?,
    val link: String,
    val source: String,
    val year: String?,
    val tracks: List<LuxoTrack>,
)

/** Everything found for a title, merged from every source with duplicates removed. */
data class LuxoSoundtrack(
    /** Openings / endings / inserts (anime). */
    val themes: List<LuxoTrack> = emptyList(),
    val albums: List<LuxoAlbum> = emptyList(),
) {
    val isEmpty: Boolean get() = themes.isEmpty() && albums.all { it.tracks.isEmpty() }
    val allTracks: List<LuxoTrack> get() = themes + albums.flatMap { it.tracks }
}

data class LuxoMediaBundle(
    val backdrops: List<LuxoImage> = emptyList(),
    val posters: List<LuxoImage> = emptyList(),
    val logos: List<LuxoImage> = emptyList(),
    /** Every TMDB video (clips, featurettes, behind the scenes, teasers…), not only trailers. */
    val videos: List<MetaTrailer> = emptyList(),
    val englishTitle: String? = null,
    val year: String? = null,
    /** Title in the work's own language (Turkish, Korean, Japanese…) and that language's code. */
    val originalTitle: String? = null,
    val originalLanguage: String? = null,
    /** Composers credited on TMDB (used to verify soundtrack albums). */
    val composers: List<String> = emptyList(),
    val isAnime: Boolean = false,
    val tmdbId: String? = null,
    val mediaType: String? = null,
    /** TMDB collection the movie belongs to (for the «السلاسل» banner). */
    val collectionId: Int? = null,
    val collectionName: String? = null,
)

data class LuxoCredit(
    val id: Int,
    val mediaType: String,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val date: String?,
    val rating: Double,
    val votes: Int,
    val popularity: Double,
    val role: String?,
    val episodes: Int?,
    /** English title (for matching the watch history). */
    val enTitle: String? = null,
) {
    val year: String? get() = date?.take(4)?.takeIf { it.length == 4 }
    val metaId: String get() = "tmdb:$id"
    val metaType: String get() = if (mediaType == "tv") "series" else "movie"
}

data class LuxoCollaborator(val id: Int, val name: String, val photo: String?, val count: Int, val role: String)

data class LuxoPersonExtras(
    val photos: List<LuxoImage> = emptyList(),
    val credits: List<LuxoCredit> = emptyList(),
    val knownFor: List<LuxoCredit> = emptyList(),
    val collaborators: List<LuxoCollaborator> = emptyList(),
    val instagram: String? = null,
    val imdbId: String? = null,
    val englishName: String? = null,
    val englishBiography: String? = null,
    val wikidataId: String? = null,
)

/* ─────────────── Fetching ─────────────── */

object LuxoTmdb {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaCache = mutableMapOf<String, LuxoMediaBundle>()
    private val personCache = mutableMapOf<Int, LuxoPersonExtras>()
    private val gate = Semaphore(5)

    internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    internal fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    internal fun JsonObject.int(key: String): Int? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
    internal fun JsonObject.dbl(key: String): Double? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
    internal fun JsonObject.arr(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private suspend fun tmdb(endpoint: String, query: Map<String, String> = emptyMap()): JsonObject? {
        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return null
        return gate.withPermit {
            runCatching { json.parseToJsonElement(httpGetText(buildTmdbUrl(endpoint, apiKey, query))) as? JsonObject }.getOrNull()
        }
    }

    internal suspend fun web(url: String): JsonObject? =
        runCatching { json.parseToJsonElement(httpGetText(url)) as? JsonObject }.getOrNull()

    private fun images(list: List<JsonObject>): List<LuxoImage> = list.mapNotNull { o ->
        val path = o.str("file_path") ?: return@mapNotNull null
        LuxoImage(path, o.int("width") ?: 0, o.int("height") ?: 0, o.str("iso_639_1"))
    }.distinctBy { it.path }

    /** Gallery + all videos for a movie / series / anime. */
    suspend fun media(id: String, type: String, imdbId: String?): LuxoMediaBundle? = withContext(Dispatchers.Default) {
        val mediaType = TmdbService.normalizeMediaType(if (type == "anime") "series" else type)
        if (mediaType != "movie" && mediaType != "tv") return@withContext null
        val tmdbId = TmdbService.ensureTmdbId(id, mediaType, imdbId) ?: return@withContext null
        val cacheKey = "$mediaType:$tmdbId"
        mediaCache[cacheKey]?.let { return@withContext it }
        coroutineScope {
            val imagesD = async { tmdb("$mediaType/$tmdbId/images", mapOf("include_image_language" to "ar,en,null,ja,ko")) }
            val videosD = async { tmdb("$mediaType/$tmdbId/videos", mapOf("include_video_language" to "ar,en,null")) }
            val detailD = async { tmdb("$mediaType/$tmdbId", mapOf("language" to "en-US")) }
            val creditsD = async { tmdb(if (mediaType == "tv") "tv/$tmdbId/aggregate_credits" else "movie/$tmdbId/credits") }
            val imagesJ = imagesD.await()
            val videosJ = videosD.await()
            val detailJ = detailD.await()
            val videos = videosJ?.arr("results").orEmpty()
                .filter { (it.str("site") ?: "").equals("YouTube", ignoreCase = true) && it.str("key") != null }
                .map { v ->
                    MetaTrailer(
                        id = v.str("id") ?: v.str("key")!!,
                        key = v.str("key")!!,
                        name = v.str("name") ?: "Video",
                        site = "YouTube",
                        size = v.int("size"),
                        type = v.str("type") ?: "Clip",
                        official = (v["official"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false,
                        publishedAt = v.str("published_at"),
                        iso6391 = v.str("iso_639_1"),
                    )
                }
                .sortedWith(compareBy<MetaTrailer> { luxoVideoTypeOrder(it.type) }.thenByDescending { it.publishedAt ?: "" })
            val bundle = LuxoMediaBundle(
                backdrops = images(imagesJ?.arr("backdrops").orEmpty()),
                posters = images(imagesJ?.arr("posters").orEmpty()),
                logos = images(imagesJ?.arr("logos").orEmpty()),
                videos = videos,
                englishTitle = detailJ?.str(if (mediaType == "movie") "title" else "name")
                    ?: detailJ?.str(if (mediaType == "movie") "original_title" else "original_name"),
                year = detailJ?.str(if (mediaType == "movie") "release_date" else "first_air_date")?.take(4),
                originalTitle = detailJ?.str(if (mediaType == "movie") "original_title" else "original_name"),
                originalLanguage = detailJ?.str("original_language"),
                composers = creditsD.await()?.arr("crew").orEmpty().filter { c ->
                    val jobs = listOfNotNull(c.str("job")) + ((c["jobs"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("job") }.orEmpty())
                    jobs.any { it == "Original Music Composer" || it == "Music" || it == "Composer" || it == "Theme Song Performance" }
                }.mapNotNull { it.str("name") }.distinct().take(6),
                isAnime = detailJ?.str("original_language") == "ja" &&
                    (detailJ["genres"] as? JsonArray)?.any { (it as? JsonObject)?.int("id") == 16 } == true,
                tmdbId = tmdbId,
                mediaType = mediaType,
                collectionId = (detailJ?.get("belongs_to_collection") as? JsonObject)?.int("id"),
                collectionName = (detailJ?.get("belongs_to_collection") as? JsonObject)?.str("name"),
            )
            mediaCache[cacheKey] = bundle
            bundle
        }
    }

    /** Everything the Luxo actor page needs beyond the base person detail. */
    suspend fun person(personId: Int): LuxoPersonExtras? = withContext(Dispatchers.Default) {
        personCache[personId]?.let { return@withContext it }
        coroutineScope {
            val arD = async { tmdb("person/$personId", mapOf("language" to "ar", "append_to_response" to "combined_credits,images,external_ids")) }
            val enD = async { tmdb("person/$personId", mapOf("language" to "en-US", "append_to_response" to "combined_credits")) }
            val ar = arD.await() ?: return@coroutineScope null
            val en = enD.await()
            val department = ar.str("known_for_department") ?: "Acting"
            val acting = department == "Acting"
            val combined = ar["combined_credits"].obj()
            val enTitles = en?.get("combined_credits").obj()?.let { c ->
                (c.arr("cast") + c.arr("crew")).associate { (it.int("id") ?: 0) to (it.str("title") ?: it.str("name")) }
            }.orEmpty()
            val raw = if (acting) combined?.arr("cast").orEmpty() else combined?.arr("crew").orEmpty().filter { c ->
                val job = c.str("job").orEmpty()
                job in setOf("Director", "Screenplay", "Writer", "Creator", "Novel", "Story", "Producer", "Executive Producer", "Original Music Composer", "Director of Photography")
            }
            val credits = raw.mapNotNull { c -> credit(c, enTitles) }
                .filterNot { it.isSelfAppearance }
                .groupBy { "${it.credit.mediaType}:${it.credit.id}" }
                .map { (_, group) -> group.first().credit.copy(role = group.mapNotNull { it.credit.role }.distinct().take(2).joinToString(" · ").ifBlank { null }) }
            val knownFor = credits
                .filter { it.votes >= 50 && it.poster != null }
                .sortedByDescending { it.votes * (0.6 + it.rating / 10.0) }
                .take(10)
            val collaborators = collaborators(personId, knownFor.take(12))
            val socials = ar["external_ids"].obj()
            val extras = LuxoPersonExtras(
                photos = images(ar["images"].obj()?.arr("profiles").orEmpty()),
                credits = credits,
                knownFor = knownFor,
                collaborators = collaborators,
                instagram = socials?.str("instagram_id"),
                imdbId = socials?.str("imdb_id"),
                englishName = en?.str("name"),
                englishBiography = en?.str("biography"),
                wikidataId = socials?.str("wikidata_id"),
            )
            personCache[personId] = extras
            extras
        }
    }

    private class RawCredit(val credit: LuxoCredit, val isSelfAppearance: Boolean)

    private fun credit(c: JsonObject, enTitles: Map<Int, String?>): RawCredit? {
        val id = c.int("id") ?: return null
        val type = c.str("media_type") ?: return null
        if (type != "movie" && type != "tv") return null
        val genres = (c["genre_ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }.orEmpty()
        val role = c.str("character") ?: c.str("job")
        val self = type == "tv" && (genres.any { it == 10767 || it == 10763 || it == 10764 } ||
            role?.lowercase()?.let { it == "self" || it == "himself" || it == "herself" || it.startsWith("self ") || it.contains("(voice) - self") } == true)
        val localized = c.str("title") ?: c.str("name")
        val title = localized?.takeIf { LuxoTranslate.hasArabic(it) } ?: enTitles[id] ?: localized ?: return null
        return RawCredit(
            LuxoCredit(
                id = id,
                mediaType = type,
                title = title,
                poster = c.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                backdrop = c.str("backdrop_path")?.let { "https://image.tmdb.org/t/p/w780$it" },
                date = c.str("release_date") ?: c.str("first_air_date"),
                rating = c.dbl("vote_average") ?: 0.0,
                votes = c.int("vote_count") ?: 0,
                popularity = c.dbl("popularity") ?: 0.0,
                role = role,
                episodes = c.int("episode_count"),
                enTitle = enTitles[id],
            ),
            self,
        )
    }

    /** People who appear most often with this person across their best-known works. */
    private suspend fun collaborators(personId: Int, works: List<LuxoCredit>): List<LuxoCollaborator> = coroutineScope {
        val lists = works.map { work ->
            async {
                val endpoint = if (work.mediaType == "tv") "tv/${work.id}/aggregate_credits" else "movie/${work.id}/credits"
                val body = tmdb(endpoint) ?: return@async emptyList<Triple<Int, JsonObject, String>>()
                val cast = body.arr("cast").filter { (it.int("order") ?: 99) < 15 }.map { it to "ممثل" }
                val crew = body.arr("crew").filter { c ->
                    c.str("job") == "Director" || (c["jobs"] as? JsonArray)?.any { (it as? JsonObject)?.str("job") == "Director" } == true
                }.map { it to "مخرج" }
                (cast + crew).mapNotNull { (o, role) ->
                    val id = o.int("id") ?: return@mapNotNull null
                    if (id == personId) return@mapNotNull null
                    Triple(id, o, role)
                }.distinctBy { it.first }
            }
        }.awaitAll()
        val counts = mutableMapOf<Int, Int>()
        val info = mutableMapOf<Int, Pair<JsonObject, String>>()
        lists.forEach { list ->
            list.forEach { (id, o, role) ->
                counts[id] = (counts[id] ?: 0) + 1
                if (info[id] == null || role == "مخرج") info[id] = o to role
            }
        }
        counts.filter { it.value >= 2 }
            .entries.sortedByDescending { it.value }
            .take(12)
            .mapNotNull { (id, count) ->
                val (o, role) = info[id] ?: return@mapNotNull null
                LuxoCollaborator(
                    id = id,
                    name = o.str("name") ?: return@mapNotNull null,
                    photo = o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" },
                    count = count,
                    role = role,
                )
            }
    }

    internal fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
}

internal fun luxoVideoTypeOrder(type: String): Int = when (type) {
    "Trailer" -> 0
    "Teaser" -> 1
    "Clip" -> 2
    "Featurette" -> 3
    "Behind the Scenes" -> 4
    "Opening Credits" -> 5
    "Bloopers" -> 6
    else -> 7
}

internal fun luxoVideoTypeArabic(type: String): String = when (type) {
    "Trailer" -> "إعلانات"
    "Teaser" -> "تشويقي"
    "Clip" -> "مقاطع"
    "Featurette" -> "تقارير"
    "Behind the Scenes" -> "خلف الكواليس"
    "Opening Credits" -> "الشارة"
    "Bloopers" -> "أخطاء التصوير"
    else -> "فيديوهات"
}

internal fun urlEncode(s: String): String = buildString {
    s.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch) else {
            append('%')
            append("0123456789ABCDEF"[c shr 4])
            append("0123456789ABCDEF"[c and 15])
        }
    }
}

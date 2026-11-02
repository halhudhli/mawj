package com.nuvio.app.features.luxo

import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Finds a title's real soundtrack in every place at once — Deezer, Apple Music (iTunes),
 * MusicBrainz (which labels albums officially as "Soundtrack") and AnimeThemes (anime openings and
 * endings) — then keeps only albums that are verifiably that title's music and merges all their
 * tracks without repeats.
 *
 * An album is accepted when its name contains the title (in the original language or English) AND
 * at least one proof: a soundtrack word (in that language too), a MusicBrainz soundtrack entry with
 * the same name, or the title's TMDB composer as the artist. Karaoke, covers, tributes, remixes and
 * "inspired by" albums are always rejected.
 */
object LuxoSoundtrackFinder {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, LuxoSoundtrack>()
    private const val UA = "Luxo/1.0 (personal media app)"

    private fun JsonElement?.o(): JsonObject? = this as? JsonObject
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.a(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private suspend fun get(url: String, headers: Map<String, String>? = null): JsonObject? = withTimeoutOrNull(12_000L) {
        runCatching {
            val body = if (headers == null) httpGetText(url) else httpGetTextWithHeaders(url, headers)
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull()
    }

    fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val rejectWords = listOf(
        "karaoke", "tribute", "cover", "covers", "remix", "remixes", "inspired", "made famous", "lofi", "lo fi",
        "8 bit", "8bit", "music box", "lullaby", "piano tribute", "workout", "ringtone", "in the style of", "parody",
    )

    private fun soundtrackWords(language: String?, isSeries: Boolean): List<String> = buildList {
        addAll(listOf("soundtrack", "original score", "score", "ost", "music from", "motion picture", "original series", "original music"))
        when (language) {
            "tr" -> addAll(listOf("dizi müzikleri", "film müzikleri", "orijinal", "müzikleri"))
            "ko" -> addAll(listOf("드라마", "사운드트랙", "ost"))
            "ja" -> addAll(listOf("サウンドトラック", "オリジナル", "サントラ", "ost", "主題歌", "オープニング", "エンディング"))
            "zh", "cn" -> addAll(listOf("原声", "电视剧", "ost"))
            "es" -> addAll(listOf("banda sonora", "bso"))
            "fr" -> addAll(listOf("bande originale"))
            "it" -> addAll(listOf("colonna sonora"))
            "pt" -> addAll(listOf("trilha sonora"))
            "de" -> addAll(listOf("filmmusik"))
            "hi" -> addAll(listOf("songs", "soundtrack"))
        }
        if (isSeries) add("season")
    }.map { norm(it) }

    private class Candidate(
        val title: String,
        val artist: String,
        val cover: String?,
        val link: String,
        val source: String,
        val year: String?,
        val loadTracks: suspend () -> List<LuxoTrack>,
    )

    suspend fun find(
        title: String,
        originalTitle: String?,
        language: String?,
        year: String?,
        isSeries: Boolean,
        isAnime: Boolean,
        composers: List<String>,
    ): LuxoSoundtrack = withContext(Dispatchers.Default) {
        val key = "$title|$originalTitle|$year"
        cache[key]?.let { return@withContext it }
        val names = listOfNotNull(originalTitle?.takeIf { language != "en" }, title).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val normNames = names.map { norm(it) }.filter { it.length >= 2 }
        val words = soundtrackWords(language, isSeries)
        val composerNorms = composers.map { norm(it) }
        val workYear = year?.toIntOrNull()

        coroutineScope {
            val queries = buildList {
                names.forEach { n ->
                    add("$n soundtrack")
                    add("$n OST")
                    add(n)
                }
                composers.take(2).forEach { c -> add("${names.last()} $c") }
            }.distinct().take(9)

            val deezerD = queries.map { q -> async { deezer(q) } }
            val itunesD = queries.take(6).map { q -> async { itunes(q) } }
            val mbD = names.map { n -> async { musicBrainzSoundtrackTitles(n) } }
            val themesD = async { if (isAnime) animeThemes(names) else emptyList() }

            val candidates = (deezerD.awaitAll() + itunesD.awaitAll()).flatten()
            val mbTitles = mbD.awaitAll().flatten().map { norm(it) }.toSet()

            val accepted = candidates.mapNotNull { c ->
                val t = norm(c.title)
                val artist = norm(c.artist)
                if (rejectWords.any { t.contains(it) || artist.contains(it) }) return@mapNotNull null
                val nameHit = normNames.firstOrNull { n -> t == n || t.startsWith("$n ") || t.contains(" $n ") || t.endsWith(" $n") || t.contains(n) && n.length >= 5 }
                    ?: return@mapNotNull null
                val wordProof = words.any { it.isNotBlank() && t.contains(it) }
                val mbProof = mbTitles.any { it == t || (it.contains(nameHit) && t.contains(it)) }
                val composerProof = composerNorms.any { it.isNotBlank() && (artist.contains(it) || it.contains(artist) && artist.length > 3) }
                // Same name only (e.g. a band's album called «Bleach») is never enough.
                if (!wordProof && !mbProof && !composerProof) return@mapNotNull null
                val y = c.year?.take(4)?.toIntOrNull()
                if (workYear != null && y != null && y < workYear - 1) return@mapNotNull null
                var score = 0
                if (wordProof) score += 3
                if (mbProof) score += 4
                if (composerProof) score += 5
                if (t.startsWith(nameHit)) score += 1
                c to score
            }
                .sortedByDescending { it.second }
                .distinctBy { norm(it.first.title) + "|" + it.first.source }
                .take(8)

            val albums = accepted.map { (c, _) ->
                async {
                    LuxoAlbum(c.title, c.artist, c.cover, c.link, c.source, c.year?.take(4), runCatching { c.loadTracks() }.getOrDefault(emptyList()))
                }
            }.awaitAll()

            val themes = themesD.await()
            val result = dedupe(themes, albums)
            cache[key] = result
            result
        }
    }

    /** One entry per piece of music, across every album and source; the copy with a preview wins. */
    private fun dedupe(themes: List<LuxoTrack>, albums: List<LuxoAlbum>): LuxoSoundtrack {
        fun trackKey(title: String): String {
            var t = title.lowercase()
            t = t.replace(Regex("\\((?:[^)]*?)(tv size|tv ver|tv version|remaster|remastered|version|edit|instrumental|off vocal|from [^)]*|ost|original soundtrack|movie ver|full)[^)]*\\)"), " ")
            t = t.replace(Regex("\\[(?:[^]]*?)(tv size|remaster|version|instrumental|off vocal)[^]]*]"), " ")
            t = t.replace(Regex(" - (tv size|remaster(ed)?( \\d{4})?|single version|album version|instrumental|from .*)$"), " ")
            return norm(t)
        }
        val seen = mutableSetOf<String>()
        val keptThemes = themes.filter { seen.add(trackKey(it.title)) }
        // Albums whose tracks carry previews first, so the playable copy is the one kept.
        val ordered = albums.sortedByDescending { album -> album.tracks.count { it.previewUrl != null } }
        val kept = ordered.map { album ->
            album.copy(tracks = album.tracks.filter { it.title.isNotBlank() && seen.add(trackKey(it.title)) })
        }.filter { it.tracks.isNotEmpty() }
        return LuxoSoundtrack(themes = keptThemes, albums = kept)
    }

    private suspend fun deezer(q: String): List<Candidate> {
        val res = get("https://api.deezer.com/search/album?limit=15&q=${urlEncode(q)}") ?: return emptyList()
        return res.a("data").mapNotNull { a ->
            val id = a.i("id") ?: return@mapNotNull null
            Candidate(
                title = a.s("title") ?: return@mapNotNull null,
                artist = a.o("artist")?.s("name").orEmpty(),
                cover = a.s("cover_xl") ?: a.s("cover_big"),
                link = a.s("link") ?: "https://www.deezer.com/album/$id",
                source = "Deezer",
                year = null,
            ) {
                get("https://api.deezer.com/album/$id/tracks?limit=100")?.a("data").orEmpty().mapNotNull { t ->
                    LuxoTrack(
                        title = t.s("title") ?: return@mapNotNull null,
                        artist = t.o("artist")?.s("name").orEmpty(),
                        durationSec = t.i("duration") ?: 0,
                        previewUrl = t.s("preview"),
                        source = "Deezer",
                    )
                }
            }
        }
    }

    private fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject

    private suspend fun itunes(q: String): List<Candidate> {
        val res = get("https://itunes.apple.com/search?media=music&entity=album&limit=20&term=${urlEncode(q)}") ?: return emptyList()
        return res.a("results").mapNotNull { a ->
            val id = a.l("collectionId") ?: return@mapNotNull null
            Candidate(
                title = a.s("collectionName") ?: return@mapNotNull null,
                artist = a.s("artistName").orEmpty(),
                cover = a.s("artworkUrl100")?.replace("100x100bb", "600x600bb"),
                link = a.s("collectionViewUrl") ?: "https://music.apple.com/album/$id",
                source = "Apple Music",
                year = a.s("releaseDate"),
            ) {
                get("https://itunes.apple.com/lookup?entity=song&limit=200&id=$id")?.a("results").orEmpty()
                    .filter { it.s("wrapperType") == "track" }
                    .mapNotNull { t ->
                        LuxoTrack(
                            title = t.s("trackName") ?: return@mapNotNull null,
                            artist = t.s("artistName").orEmpty(),
                            durationSec = ((t.l("trackTimeMillis") ?: 0L) / 1000L).toInt(),
                            previewUrl = t.s("previewUrl"),
                            source = "Apple Music",
                        )
                    }
            }
        }
    }

    /** Album names MusicBrainz officially files as soundtracks for this title. */
    private suspend fun musicBrainzSoundtrackTitles(name: String): List<String> {
        val q = urlEncode("releasegroup:\"$name\" AND secondarytype:soundtrack")
        val res = get("https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=25&query=$q", mapOf("User-Agent" to UA, "Accept" to "application/json"))
            ?: return emptyList()
        return res.a("release-groups").mapNotNull { it.s("title") }
    }

    /** Anime openings / endings (official) with audio and video, from AnimeThemes. */
    private suspend fun animeThemes(names: List<String>): List<LuxoTrack> {
        for (name in names) {
            val url = "https://api.animethemes.moe/anime?filter[name]=${urlEncode(name)}" +
                "&include=animethemes.song.artists,animethemes.animethemeentries.videos.audio&page[size]=5"
            val res = get(url) ?: continue
            val anime = res.a("anime")
            if (anime.isEmpty()) {
                val search = get("https://api.animethemes.moe/search?q=${urlEncode(name)}&fields[search]=anime" +
                    "&include[anime]=animethemes.song.artists,animethemes.animethemeentries.videos.audio")
                val list = search?.o("search")?.a("anime").orEmpty()
                if (list.isNotEmpty()) return themesOf(list.take(4))
                continue
            }
            return themesOf(anime)
        }
        return emptyList()
    }

    private fun themesOf(anime: List<JsonObject>): List<LuxoTrack> = anime.flatMap { a ->
        a.a("animethemes").mapNotNull { theme ->
            val slug = theme.s("slug").orEmpty() // OP1, ED2…
            val song = theme.o("song")
            val songTitle = song?.s("title") ?: return@mapNotNull null
            val artists = song.a("artists").mapNotNull { it.s("name") }.joinToString(", ")
            val video = theme.a("animethemeentries").firstOrNull()?.a("videos")?.firstOrNull()
            val label = when {
                slug.startsWith("OP") -> "بداية ${slug.removePrefix("OP")}".trim()
                slug.startsWith("ED") -> "نهاية ${slug.removePrefix("ED")}".trim()
                else -> slug
            }
            LuxoTrack(
                title = songTitle,
                artist = listOf(label, artists).filter { it.isNotBlank() }.joinToString(" · "),
                durationSec = 90,
                previewUrl = video?.o("audio")?.s("link"),
                source = "AnimeThemes",
                videoUrl = video?.s("link"),
            )
        }
    }
}

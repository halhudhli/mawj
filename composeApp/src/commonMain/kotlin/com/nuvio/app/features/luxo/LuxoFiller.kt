package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.s

/**
 * «دليل الحلقات الحشو»: marks filler episodes of long anime (Conan, One Piece, Naruto…) with a badge,
 * and lets autoplay skip them. Lists come from animefillerlist.com (absolute episode numbers) and are
 * cached for a week; episodes are matched by their position across all seasons.
 */
object LuxoFiller {
    enum class Kind { Filler, Mixed }

    /** video id → kind, for every show whose page has been opened. */
    private val byVideo = mutableStateMapOf<String, Kind>()

    fun kind(videoId: String): Kind? = byVideo[videoId]

    fun skipEnabled(): Boolean = LuxoFeaturesRepository.current().let { it.fillerGuide && it.fillerSkip }

    /** True when autoplay should pass over this episode. */
    fun shouldSkip(video: MetaVideo): Boolean = skipEnabled() && byVideo[video.id] == Kind.Filler

    private const val WEEK_MS = 7L * 86_400_000L
    private val ALIASES = mapOf(
        "case closed" to "detective-conan",
        "المحقق كونان" to "detective-conan",
        "ون بيس" to "one-piece",
        "ناروتو" to "naruto",
        "ناروتو شيبودن" to "naruto-shippuden",
        "naruto shippuden" to "naruto-shippuden",
        "naruto: shippuden" to "naruto-shippuden",
        "bleach: thousand-year blood war" to "bleach",
        "boruto: naruto next generations" to "boruto-naruto-next-generations",
        "دراغون بول" to "dragon-ball",
        "هنتر x هنتر" to "hunter-x-hunter-2011",
        "hunter x hunter" to "hunter-x-hunter-2011",
        "fairy tail" to "fairy-tail",
        "black clover" to "black-clover",
    )

    private fun slugOf(name: String): String {
        val lower = name.trim().lowercase()
        ALIASES[lower]?.let { return it }
        return lower.map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-')
    }

    private fun parseRanges(text: String): Set<Int> {
        val out = mutableSetOf<Int>()
        Regex("(\\d+)\\s*-\\s*(\\d+)|(\\d+)").findAll(text.replace(Regex("<[^>]+>"), " ")).forEach { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && b >= a && b - a < 2000) out += a..b
            else m.groupValues[3].toIntOrNull()?.let { out += it }
        }
        return out
    }

    /** Returns "F:1,2,3|M:4,5" or null. */
    private fun parsePage(html: String): String? {
        val filler = mutableSetOf<Int>()
        val mixed = mutableSetOf<Int>()
        // Summary blocks: <div class="filler"> … <span class="Episodes">6, 8-10</span>
        Regex("<div class=\"(filler|mixed_canon/filler)\"[^>]*>.*?<span class=\"Episodes\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).forEach { m ->
                val set = parseRanges(m.groupValues[2])
                if (m.groupValues[1] == "filler") filler += set else mixed += set
            }
        if (filler.isEmpty() && mixed.isEmpty()) {
            // Episode table rows: <tr class="filler odd"> <td class="Number">6</td>
            Regex("<tr[^>]*class=\"([^\"]*)\"[^>]*>\\s*<td class=\"Number\">\\s*(\\d+)", RegexOption.DOT_MATCHES_ALL)
                .findAll(html).forEach { m ->
                    val cls = m.groupValues[1]
                    val n = m.groupValues[2].toIntOrNull() ?: return@forEach
                    when {
                        cls.contains("mixed") -> mixed += n
                        cls.split(' ').any { it == "filler" } -> filler += n
                    }
                }
        }
        if (filler.isEmpty() && mixed.isEmpty()) return null
        return "F:" + filler.sorted().joinToString(",") + "|M:" + mixed.sorted().joinToString(",")
    }

    private suspend fun fetchFor(slug: String): String? = runCatching {
        parsePage(httpGetText("https://www.animefillerlist.com/shows/$slug"))
    }.getOrNull()

    /** English name from TMDB when the page title is Arabic or localized. */
    private suspend fun englishName(meta: MetaDetails): String? {
        val id = meta.id
        val tmdb = when {
            id.startsWith("tmdb:") -> id.removePrefix("tmdb:").substringBefore(':').toIntOrNull()
            id.startsWith("tt") -> LuxoTmdbLite.get("find/${id.substringBefore(':')}", mapOf("external_source" to "imdb_id"))
                ?.a("tv_results")?.firstOrNull()?.i("id")
            else -> null
        } ?: return null
        return LuxoTmdbLite.get("tv/$tmdb", mapOf("language" to "en-US"))?.s("name")
    }

    private suspend fun listFor(meta: MetaDetails): String? {
        val cacheKey = "luxo_filler/${slugOf(meta.id).ifBlank { "x" }}.txt"
        AppFileCache.read(cacheKey)?.let { cached ->
            val at = cached.substringBefore('\n').toLongOrNull() ?: 0L
            if (luxoNowMs() - at < WEEK_MS) return cached.substringAfter('\n').takeIf { it.startsWith("F:") }
        }
        val names = buildList {
            add(meta.name)
            englishName(meta)?.let { add(it) }
        }.distinct()
        var found: String? = null
        for (n in names) {
            val slug = slugOf(n)
            if (slug.isBlank()) continue
            found = fetchFor(slug)
            if (found != null) break
        }
        AppFileCache.write(cacheKey, "${luxoNowMs()}\n${found ?: "none"}")
        return found
    }

    private fun looksLikeAnime(meta: MetaDetails): Boolean {
        val g = meta.genres.joinToString(" ").lowercase()
        return meta.id.startsWith("kitsu:") || meta.id.startsWith("mal:") || meta.id.startsWith("anilist:") ||
            g.contains("anime") || g.contains("animation") || g.contains("أنمي") || g.contains("رسوم")
    }

    /** Loads the list for [meta] (series only) and fills the video map. */
    suspend fun load(meta: MetaDetails) {
        if (!LuxoFeaturesRepository.current().fillerGuide || meta.type != "series" || !looksLikeAnime(meta)) return
        val episodes = meta.videos.filter { (it.season ?: 0) > 0 && it.episode != null }
            .sortedWith(compareBy<MetaVideo>({ it.season }, { it.episode }))
        if (episodes.size < 24) return
        val list = listFor(meta) ?: return
        val filler = list.substringAfter("F:").substringBefore('|').split(',').mapNotNull { it.toIntOrNull() }.toSet()
        val mixed = list.substringAfter("|M:", "").split(',').mapNotNull { it.toIntOrNull() }.toSet()
        // Kitsu-style ids keep absolute numbers in one season; otherwise the position across seasons is used.
        val singleSeason = episodes.all { it.season == episodes.first().season }
        episodes.forEachIndexed { index, v ->
            val absolute = if (singleSeason) v.episode!! else index + 1
            when (absolute) {
                in filler -> byVideo[v.id] = Kind.Filler
                in mixed -> byVideo[v.id] = Kind.Mixed
            }
        }
    }
}

/** Kick-off on the details page. */
@Composable
fun LuxoFillerLoader(meta: MetaDetails) {
    LaunchedEffect(meta.id, meta.videos.size) { runCatching { LuxoFiller.load(meta) } }
}

/** «حشو» / «مختلط» next to the episode number. */
@Composable
fun LuxoFillerBadge(videoId: String, textSize: TextUnit, modifier: Modifier = Modifier) {
    val kind = LuxoFiller.kind(videoId) ?: return
    val (label, color) = when (kind) {
        LuxoFiller.Kind.Filler -> "حشو" to Color(0xFFE0565B)
        LuxoFiller.Kind.Mixed -> "مختلط" to Color(0xFFD9A43A)
    }
    Text(
        label,
        color = Color.White,
        fontSize = textSize,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.9f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

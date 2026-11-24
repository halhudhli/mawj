package com.mawj.tv.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * «تخطّي الحشو» for long anime: the filler episodes come from animefillerlist.com (one page per
 * show, kept a week), and the next-episode step passes over them. Only for anime with 24+ episodes.
 */
object Filler {
    private const val WEEK_MS = 7L * 86_400_000L
    private val memory = ConcurrentHashMap<String, Set<Int>>()
    private val ALIASES = mapOf(
        "case closed" to "detective-conan",
        "naruto shippuden" to "naruto-shippuden",
        "naruto: shippuden" to "naruto-shippuden",
        "bleach: thousand-year blood war" to "bleach",
        "boruto: naruto next generations" to "boruto-naruto-next-generations",
        "hunter x hunter" to "hunter-x-hunter-2011",
    )

    private fun looksAnime(meta: MetaPreview): Boolean =
        meta.id.startsWith("kitsu:") || meta.id.startsWith("mal:") ||
            meta.genres.any { val g = it.lowercase(); "anim" in g || "أنمي" in g }

    private fun slugOf(name: String): String {
        val lower = name.trim().lowercase()
        ALIASES[lower]?.let { return it }
        return lower.map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("").replace(Regex("-+"), "-").trim('-')
    }

    private fun parseRanges(text: String): Set<Int> {
        val out = mutableSetOf<Int>()
        Regex("(\\d+)\\s*-\\s*(\\d+)|(\\d+)").findAll(text.replace(Regex("<[^>]+>"), " ")).forEach { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && b >= a && b - a < 2000) out += a..b else m.groupValues[3].toIntOrNull()?.let { out += it }
        }
        return out
    }

    private fun parsePage(html: String): Set<Int> {
        val filler = mutableSetOf<Int>()
        Regex("<div class=\"filler\"[^>]*>.*?<span class=\"Episodes\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).forEach { filler += parseRanges(it.groupValues[1]) }
        if (filler.isEmpty()) {
            Regex("<tr[^>]*class=\"([^\"]*)\"[^>]*>\\s*<td class=\"Number\">\\s*(\\d+)", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach { m ->
                if (m.groupValues[1].split(' ').any { it == "filler" }) m.groupValues[2].toIntOrNull()?.let { filler += it }
            }
        }
        return filler
    }

    private suspend fun numbers(name: String): Set<Int> {
        val slug = slugOf(name).ifBlank { return emptySet() }
        memory[slug]?.let { return it }
        Store.text("filler:$slug", "").takeIf { it.isNotBlank() }?.let { saved ->
            val at = saved.substringBefore('|').toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < WEEK_MS) {
                return saved.substringAfter('|').split(',').mapNotNull { it.toIntOrNull() }.toSet().also { memory[slug] = it }
            }
        }
        val html = withContext(Dispatchers.IO) {
            runCatching {
                val r = Addons.http.get("https://www.animefillerlist.com/shows/$slug")
                if (r.status.isSuccess()) r.bodyAsText() else null
            }.getOrNull()
        }
        val set = html?.let(::parsePage).orEmpty()
        Store.setText("filler:$slug", "${System.currentTimeMillis()}|${set.joinToString(",")}")
        memory[slug] = set
        return set
    }

    /** The ids of this show's filler episodes (empty when the switch is off or it isn't a long anime). */
    suspend fun fillerIds(meta: MetaPreview, episodes: List<Episode>): Set<String> {
        if (!Store.fillerSkip || !looksAnime(meta)) return emptySet()
        val sorted = episodes.filter { it.season > 0 }.sortedWith(compareBy({ it.season }, { it.number }))
        if (sorted.size < 24) return emptySet()
        val nums = numbers(meta.name)
        if (nums.isEmpty()) return emptySet()
        val single = sorted.all { it.season == sorted.first().season }
        return sorted.mapIndexedNotNull { i, e -> if ((if (single) e.number else i + 1) in nums) e.id else null }.toSet()
    }
}

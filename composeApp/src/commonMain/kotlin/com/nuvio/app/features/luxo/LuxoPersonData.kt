package com.nuvio.app.features.luxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Matches TMDB credits against the watch history: by TMDB id when the history has one, otherwise
 * by title (Arabic or English) and year, so items saved with IMDb ids still count.
 */
@Composable
fun rememberLuxoWatchedMatcher(): (LuxoCredit) -> Boolean {
    val state by remember { WatchedRepository.ensureLoaded(); WatchedRepository.uiState }.collectAsState()
    val index = remember(state.items) {
        val ids = HashSet<String>()
        val names = HashMap<String, MutableSet<String?>>()
        state.items.forEach { item ->
            ids += item.id.lowercase()
            val key = LuxoSoundtrackFinder.norm(item.name)
            if (key.isNotBlank()) names.getOrPut(key) { mutableSetOf() } += item.releaseInfo?.take(4)
        }
        ids to names
    }
    return remember(index) {
        { credit: LuxoCredit ->
            val (ids, names) = index
            if ("tmdb:${credit.id}" in ids) {
                true
            } else {
                listOfNotNull(credit.title, credit.enTitle).any { t ->
                    val years = names[LuxoSoundtrackFinder.norm(t)] ?: return@any false
                    val y = credit.year
                    // Series episodes carry no year; movies must match the year (±1) to avoid remakes.
                    credit.mediaType == "tv" || y == null || years.any { it == null || it == y || it.toIntOrNull()?.let { v -> kotlin.math.abs(v - (y.toIntOrNull() ?: v)) <= 1 } == true }
                }
            }
        }
    }
}

data class LuxoAward(val name: String, val year: String?, val forWork: String?)

/** Awards from Wikidata (a sourced database, nothing guessed): wins + the number of nominations. */
object LuxoAwards {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, Pair<List<LuxoAward>, Int>>()
    private val headers = mapOf("User-Agent" to "Luxo/1.0 (personal media app)", "Accept" to "application/json")

    private fun JsonObject.o(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.itemId(): String? =
        o("mainsnak")?.o("datavalue")?.o("value")?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.qualifierId(p: String): String? =
        o("qualifiers")?.arr(p)?.firstOrNull()?.o("datavalue")?.o("value")?.let { (it["id"] as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.qualifierYear(p: String): String? =
        o("qualifiers")?.arr(p)?.firstOrNull()?.o("datavalue")?.o("value")
            ?.let { (it["time"] as? JsonPrimitive)?.contentOrNull }?.removePrefix("+")?.take(4)

    suspend fun load(wikidataId: String): Pair<List<LuxoAward>, Int> = withContext(Dispatchers.Default) {
        cache[wikidataId]?.let { return@withContext it }
        val result = runCatching {
            val entity = (json.parseToJsonElement(
                httpGetTextWithHeaders("https://www.wikidata.org/wiki/Special:EntityData/$wikidataId.json", headers),
            ) as JsonObject).o("entities")?.o(wikidataId) ?: return@runCatching emptyList<LuxoAward>() to 0
            val claims = entity.o("claims")
            val wins = claims?.arr("P166").orEmpty()
            val nominations = claims?.arr("P1411").orEmpty().size
            val raw = wins.mapNotNull { claim ->
                val award = claim.itemId() ?: return@mapNotNull null
                Triple(award, claim.qualifierYear("P585"), claim.qualifierId("P1686"))
            }
            val ids = (raw.map { it.first } + raw.mapNotNull { it.third }).distinct()
            val labels = mutableMapOf<String, String>()
            ids.chunked(45).forEach { chunk ->
                val body = httpGetTextWithHeaders(
                    "https://www.wikidata.org/w/api.php?action=wbgetentities&props=labels&languages=ar%7Cen&format=json&ids=" + chunk.joinToString("%7C"),
                    headers,
                )
                (json.parseToJsonElement(body) as? JsonObject)?.o("entities")?.forEach { (id, value) ->
                    val l = (value as? JsonObject)?.o("labels")
                    val text = (l?.o("ar") ?: l?.o("en"))?.let { (it["value"] as? JsonPrimitive)?.contentOrNull }
                    if (text != null) labels[id] = text
                }
            }
            raw.mapNotNull { (award, year, work) ->
                LuxoAward(labels[award] ?: return@mapNotNull null, year, work?.let { labels[it] })
            }.distinctBy { it.name + it.year }.sortedByDescending { it.year ?: "0" } to nominations
        }.getOrDefault(emptyList<LuxoAward>() to 0)
        cache[wikidataId] = result
        result
    }
}

/** People the viewer follows (new work from them is flagged; notifications use the same list). */
object LuxoFollowedPeople {
    private const val KEY = "followed_people_v1"
    val ids = mutableStateListOf<Int>().apply {
        LuxoPrefsStorage.load(KEY)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.let { addAll(it) }
    }

    fun isFollowed(id: Int) = id in ids

    fun toggle(id: Int) {
        if (id in ids) ids.remove(id) else ids.add(id)
        LuxoPrefsStorage.save(KEY, ids.joinToString(","))
        LuxoReminders.rememberApiKey()
    }
}

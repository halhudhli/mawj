package com.mawj.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime

/** One title in the viewing record. */
data class Watched(val meta: MetaPreview, val minutes: Int, val finished: Int, val last: Long)

/**
 * What the profile is built from, kept on the TV only: minutes per day and per hour, minutes per
 * title, finished episodes and films, saved lists, hidden titles and followed people.
 * Nothing is recorded in «الوضع الخاص».
 */
object Library {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    /** yyyy-MM-dd → minutes. */
    val days = mutableStateMapOf<String, Int>()
    /** Minutes per hour of the day (0..23). */
    val hours = IntArray(24)
    val titles = mutableStateMapOf<String, Watched>()
    var finishedEpisodes by mutableIntStateOf(0)
        private set
    var finishedMovies by mutableIntStateOf(0)
        private set

    /** name → titles. «للمشاهدة لاحقاً» always exists. */
    val lists = mutableStateMapOf<String, List<MetaPreview>>()
    val hidden = mutableStateListOf<String>()
    /** id → name. */
    val followed = mutableStateMapOf<Int, String>()

    const val LATER = "للمشاهدة لاحقاً"

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mawj_library", Context.MODE_PRIVATE)
        runCatching {
            (json.parseToJsonElement(prefs.getString("days", "{}")!!) as JsonObject).forEach { (k, v) -> days[k] = (v as JsonPrimitive).content.toInt() }
        }
        prefs.getString("hours", null)?.split(',')?.forEachIndexed { i, v -> if (i < 24) hours[i] = v.toIntOrNull() ?: 0 }
        runCatching {
            json.parseToJsonElement(prefs.getString("titles", "[]")!!).jsonArray.forEach { el ->
                val o = el.jsonObject
                val m = meta(o["m"]!!.jsonObject) ?: return@forEach
                titles[m.id] = Watched(m, o.n("min"), o.n("fin"), o.l("at"))
            }
        }
        finishedEpisodes = prefs.getInt("finEp", 0)
        finishedMovies = prefs.getInt("finMv", 0)
        runCatching {
            (json.parseToJsonElement(prefs.getString("lists", "{}")!!) as JsonObject).forEach { (k, v) ->
                lists[k] = (v as JsonArray).mapNotNull { (it as? JsonObject)?.let(::meta) }
            }
        }
        if (LATER !in lists) lists[LATER] = emptyList()
        hidden.addAll(prefs.getString("hidden", "")!!.split('\n').filter { it.isNotBlank() })
        prefs.getString("followed", "")!!.split('\n').filter { it.contains('\u001F') }.forEach {
            it.substringBefore('\u001F').toIntOrNull()?.let { id -> followed[id] = it.substringAfter('\u001F') }
        }
    }

    // ---------------- the viewing record ----------------

    /** Called with each saved position; counts only real watching (forward, at most 20 minutes a step). */
    fun record(meta: MetaPreview, deltaMs: Long) {
        if (Store.feature("privateMode")) return
        if (deltaMs !in 20_000..20 * 60_000L) return
        val min = (deltaMs / 60_000L).toInt().coerceAtLeast(1)
        val now = LocalDateTime.now()
        val day = now.toLocalDate().toString()
        days[day] = (days[day] ?: 0) + min
        hours[now.hour] += min
        val old = titles[meta.id]
        titles[meta.id] = Watched(meta, (old?.minutes ?: 0) + min, old?.finished ?: 0, System.currentTimeMillis())
        save()
    }

    fun finished(meta: MetaPreview, episode: Boolean) {
        if (Store.feature("privateMode")) return
        if (episode) finishedEpisodes++ else finishedMovies++
        val old = titles[meta.id]
        titles[meta.id] = Watched(meta, old?.minutes ?: 0, (old?.finished ?: 0) + 1, System.currentTimeMillis())
        save()
    }

    val totalMinutes: Int get() = days.values.sum()

    /** The hour with the most watching, or null before any. */
    val peakHour: Int? get() = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }

    /** Minutes on each of the last [n] days, oldest first. */
    fun lastDays(n: Int): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (days[it.toString()] ?: 0) }
    }

    /** Days in a row with some watching, up to today. */
    val streak: Int get() {
        var d = LocalDate.now()
        var n = 0
        if ((days[d.toString()] ?: 0) == 0) d = d.minusDays(1)
        while ((days[d.toString()] ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    private fun save() {
        prefs.edit()
            .putString("days", JsonObject(days.mapValues { JsonPrimitive(it.value) }).toString())
            .putString("hours", hours.joinToString(","))
            .putString("titles", JsonArray(titles.values.sortedByDescending { it.last }.take(400).map { w ->
                buildJsonObject { put("m", enc(w.meta)); put("min", w.minutes); put("fin", w.finished); put("at", w.last) }
            }).toString())
            .putInt("finEp", finishedEpisodes).putInt("finMv", finishedMovies)
            .apply()
    }

    // ---------------- lists, hidden, followed ----------------

    fun inList(name: String, id: String) = lists[name].orEmpty().any { it.id == id }

    fun toggle(name: String, meta: MetaPreview) {
        val cur = lists[name].orEmpty()
        lists[name] = if (cur.any { it.id == meta.id }) cur.filter { it.id != meta.id } else listOf(meta) + cur
        saveLists()
    }

    fun newList(name: String) {
        if (name.isBlank() || name in lists) return
        lists[name] = emptyList(); saveLists()
    }

    fun deleteList(name: String) {
        if (name == LATER) return
        lists.remove(name); saveLists()
    }

    private fun saveLists() {
        prefs.edit().putString("lists", JsonObject(lists.mapValues { (_, v) -> JsonArray(v.map(::enc)) }).toString()).apply()
    }

    fun hide(id: String) {
        if (id !in hidden) hidden.add(id)
        prefs.edit().putString("hidden", hidden.joinToString("\n")).apply()
    }

    fun unhide(id: String) {
        hidden.remove(id)
        prefs.edit().putString("hidden", hidden.joinToString("\n")).apply()
    }

    fun toggleFollow(id: Int, name: String) {
        if (followed.containsKey(id)) followed.remove(id) else followed[id] = name
        prefs.edit().putString("followed", followed.entries.joinToString("\n") { "${it.key}\u001F${it.value}" }).apply()
    }

    // ---------------- encoding ----------------

    private fun enc(m: MetaPreview): JsonObject = buildJsonObject {
        put("id", m.id); put("type", m.type); put("name", m.name)
        m.poster?.let { put("p", it) }; m.background?.let { put("b", it) }; m.year?.let { put("y", it) }; m.rating?.let { put("r", it) }
    }

    private fun meta(o: JsonObject): MetaPreview? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content
        return MetaPreview(s("id") ?: return null, s("type") ?: "movie", s("name") ?: "", s("p"), s("b"), null, s("y"), s("r"), null, emptyList())
    }

    private fun JsonObject.n(k: String) = (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}

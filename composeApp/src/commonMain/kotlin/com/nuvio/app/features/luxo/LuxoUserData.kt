package com.nuvio.app.features.luxo

import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** A title saved by the user (in a list or with a rating), enough to show a poster and open it. */
@Serializable
internal data class LuxoSavedTitle(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val banner: String? = null,
    val addedAtMs: Long = 0L,
) {
    fun toPreview(): MetaPreview = MetaPreview(id = id, type = type, name = name, poster = poster, banner = banner)
}

@Serializable
internal data class LuxoRating(
    val title: LuxoSavedTitle,
    /** 1..10, shown as five stars in half steps. */
    val score: Int,
    val note: String = "",
    val ratedAtMs: Long = 0L,
)

@Serializable
internal data class LuxoUserList(
    val id: String,
    val name: String,
    val emoji: String = "🎬",
    val items: List<LuxoSavedTitle> = emptyList(),
    val createdAtMs: Long = 0L,
)

@Serializable
internal data class LuxoUserDataState(
    val ratings: Map<String, LuxoRating> = emptyMap(),
    val lists: List<LuxoUserList> = emptyList(),
)

/**
 * Luxo: personal ratings/notes and the user's own named lists ("أفلام الويكند", "أشوفها مع صديقتي").
 * Stored on the device and included in the settings backup.
 */
internal object LuxoUserData {
    const val STORAGE_KEY = "user_data_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<LuxoUserDataState> = _state.asStateFlow()

    private fun load(): LuxoUserDataState =
        runCatching { LuxoPrefsStorage.load(STORAGE_KEY) }.getOrNull()
            ?.let { payload -> runCatching { json.decodeFromString(LuxoUserDataState.serializer(), payload) }.getOrNull() }
            ?: LuxoUserDataState()

    /** Re-reads storage, e.g. after restoring a backup. */
    fun reload() { _state.value = load() }

    private fun update(transform: (LuxoUserDataState) -> LuxoUserDataState) {
        val next = transform(_state.value)
        _state.value = next
        runCatching { LuxoPrefsStorage.save(STORAGE_KEY, json.encodeToString(LuxoUserDataState.serializer(), next)) }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun key(type: String, id: String): String = "${type.lowercase()}:$id"

    // ---- Ratings ----

    fun rating(type: String, id: String): LuxoRating? = _state.value.ratings[key(type, id)]

    fun rate(title: LuxoSavedTitle, score: Int, note: String) {
        update { state ->
            state.copy(
                ratings = state.ratings + (key(title.type, title.id) to LuxoRating(
                    title = title,
                    score = score.coerceIn(1, 10),
                    note = note.trim(),
                    ratedAtMs = now(),
                )),
            )
        }
    }

    fun clearRating(type: String, id: String) {
        update { state -> state.copy(ratings = state.ratings - key(type, id)) }
    }

    // ---- Lists ----

    fun createList(name: String, emoji: String): LuxoUserList {
        val list = LuxoUserList(
            id = "list_${now()}",
            name = name.trim().ifBlank { "قائمتي" },
            emoji = emoji.ifBlank { "🎬" },
            createdAtMs = now(),
        )
        update { state -> state.copy(lists = state.lists + list) }
        return list
    }

    fun renameList(listId: String, name: String, emoji: String) {
        update { state ->
            state.copy(lists = state.lists.map { list ->
                if (list.id == listId) list.copy(name = name.trim().ifBlank { list.name }, emoji = emoji.ifBlank { list.emoji }) else list
            })
        }
    }

    fun deleteList(listId: String) {
        update { state -> state.copy(lists = state.lists.filterNot { it.id == listId }) }
    }

    fun moveList(listId: String, up: Boolean) {
        update { state ->
            val index = state.lists.indexOfFirst { it.id == listId }
            val target = if (up) index - 1 else index + 1
            if (index < 0 || target !in state.lists.indices) return@update state
            val mutable = state.lists.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(target, item)
            state.copy(lists = mutable)
        }
    }

    fun isInList(listId: String, type: String, id: String): Boolean =
        _state.value.lists.firstOrNull { it.id == listId }?.items?.any { key(it.type, it.id) == key(type, id) } == true

    fun toggleInList(listId: String, title: LuxoSavedTitle) {
        update { state ->
            state.copy(lists = state.lists.map { list ->
                if (list.id != listId) return@map list
                val k = key(title.type, title.id)
                if (list.items.any { key(it.type, it.id) == k }) {
                    list.copy(items = list.items.filterNot { key(it.type, it.id) == k })
                } else {
                    list.copy(items = listOf(title.copy(addedAtMs = now())) + list.items)
                }
            })
        }
    }

    fun removeFromList(listId: String, type: String, id: String) {
        update { state ->
            state.copy(lists = state.lists.map { list ->
                if (list.id == listId) list.copy(items = list.items.filterNot { key(it.type, it.id) == key(type, id) }) else list
            })
        }
    }
}

/** "4.5" style label for a 1..10 score. */
internal fun luxoStarsLabel(score: Int): String {
    val stars = score / 2.0
    return if (score % 2 == 0) "${score / 2}" else stars.toString()
}

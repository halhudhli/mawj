package com.nuvio.app.features.luxo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class LuxoBookmark(
    val title: String,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val note: String = "",
    val createdMs: Long = 0L,
)

/** «🔖 علامات داخل الحلقة»: moments saved from the player, with a note. */
object LuxoBookmarks {
    private const val KEY = "bookmarks_v1"
    private val json = Json { ignoreUnknownKeys = true }
    val all = mutableStateListOf<LuxoBookmark>().apply {
        LuxoPrefsStorage.load(KEY)?.let { p -> runCatching { json.decodeFromString(ListSerializer(LuxoBookmark.serializer()), p) }.getOrNull()?.let { addAll(it) } }
    }

    private fun save() = LuxoPrefsStorage.save(KEY, json.encodeToString(ListSerializer(LuxoBookmark.serializer()), all.toList()))

    fun add(b: LuxoBookmark) {
        all.add(b)
        save()
    }

    fun remove(b: LuxoBookmark) {
        all.remove(b)
        save()
    }

    fun forEpisode(title: String, season: Int?, episode: Int?) =
        all.filter { it.title == title && it.season == season && it.episode == episode }.sortedBy { it.positionMs }
}

fun luxoFormatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/**
 * «🗣️ توضيح الحوار» and «🌙 صوت ليلي». The audio thread reads the plain flags; the UI reads the
 * state copies.
 */
object LuxoAudioFx {
    private const val KEY = "audio_fx_v1"

    @kotlin.concurrent.Volatile
    var dialogueOn: Boolean = false
        private set

    @kotlin.concurrent.Volatile
    var nightOn: Boolean = false
        private set

    var dialogue by mutableStateOf(false)
        private set
    var night by mutableStateOf(false)
        private set

    init {
        val saved = LuxoPrefsStorage.load(KEY).orEmpty()
        dialogue = 'd' in saved
        night = 'n' in saved
        dialogueOn = dialogue
        nightOn = night
    }

    private fun persist() = LuxoPrefsStorage.save(KEY, (if (dialogue) "d" else "") + (if (night) "n" else ""))

    fun toggleDialogue() {
        dialogue = !dialogue
        dialogueOn = dialogue
        persist()
    }

    fun toggleNight() {
        night = !night
        nightOn = night
        persist()
    }
}

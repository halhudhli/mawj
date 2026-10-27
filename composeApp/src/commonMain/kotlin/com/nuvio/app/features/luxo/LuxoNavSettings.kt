package com.nuvio.app.features.luxo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the main navigation lives. */
enum class LuxoNavPlacement(val id: String) {
    /** The floating/classic bar, top or bottom as chosen in the bar style settings. */
    BAR("bar"),
    /** A rail on the reading-start edge: right in Arabic, left in English. */
    SIDE("side"),
    /** Bar on phones and portrait, side rail on wide landscape screens. */
    AUTO("auto"),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: BAR
    }
}

/** Tab ids used for ordering. The profile tab is always last. */
object LuxoNavTabIds {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val LISTS = "lists"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val LIVE_TV = "livetv"
    val DEFAULT_ORDER = listOf(HOME, MOVIES, SERIES, LISTS, SEARCH, LIBRARY, LIVE_TV)

    fun label(id: String): String = when (id) {
        HOME -> "الرئيسية · Home"
        MOVIES -> "أفلام · Movies"
        SERIES -> "مسلسلات · Series"
        LISTS -> "قوائمي · Lists"
        SEARCH -> "بحث · Search"
        LIBRARY -> "المكتبة · Library"
        LIVE_TV -> "بث مباشر · Live TV"
        else -> id
    }
}

/** Luxo's own navigation options. Every extra has its own switch. */
@Serializable
data class LuxoNavSettings(
    val showMovies: Boolean = true,
    val showSeries: Boolean = true,
    val showLists: Boolean = true,
    val placement: String = LuxoNavPlacement.BAR.id,
    val railExpanded: Boolean = false,
    val hideOnScroll: Boolean = false,
    val showLabels: Boolean = true,
    val badges: Boolean = true,
    val quickActions: Boolean = true,
    val order: List<String> = LuxoNavTabIds.DEFAULT_ORDER,
) {
    val placementValue: LuxoNavPlacement get() = LuxoNavPlacement.of(placement)

    /** Saved order plus any tab added in a later version, never duplicated. */
    fun resolvedOrder(): List<String> =
        (order.filter { it in LuxoNavTabIds.DEFAULT_ORDER } + LuxoNavTabIds.DEFAULT_ORDER).distinct()

    fun rank(id: String): Int = resolvedOrder().indexOf(id).let { if (it < 0) 99 else it }
}

object LuxoNavSettingsRepository {
    private const val storageKey = "nav_settings_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(LuxoNavSettings())
    val state: StateFlow<LuxoNavSettings> = _state.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = LuxoPrefsStorage.load(storageKey) ?: return
        runCatching { json.decodeFromString(LuxoNavSettings.serializer(), payload) }
            .onSuccess { _state.value = it }
    }

    fun update(transform: (LuxoNavSettings) -> LuxoNavSettings) {
        ensureLoaded()
        val next = transform(_state.value)
        _state.value = next
        LuxoPrefsStorage.save(storageKey, json.encodeToString(LuxoNavSettings.serializer(), next))
    }

    fun move(id: String, by: Int) = update { settings ->
        val list = settings.resolvedOrder().toMutableList()
        val from = list.indexOf(id)
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return@update settings
        list.add(to, list.removeAt(from))
        settings.copy(order = list)
    }
}

package com.nuvio.app.features.luxo

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ready-made rows for the Movies / Series tabs, built from TMDB lists. */
internal object LuxoSmartCollections {
    private class Definition(
        val key: String,
        val title: String,
        val endpoint: String,
        val query: Map<String, String> = emptyMap(),
    )

    private val movieRows = listOf(
        Definition("trending", "رائج هالأسبوع", "trending/movie/week"),
        Definition("top", "الأعلى تقييماً", "discover/movie", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "2500")),
        Definition("short", "أفلام قصيرة · أقل من 90 دقيقة", "discover/movie", mapOf(
            "sort_by" to "popularity.desc", "with_runtime.lte" to "90", "with_runtime.gte" to "60", "vote_count.gte" to "300",
        )),
        Definition("family", "للعائلة", "discover/movie", mapOf("sort_by" to "popularity.desc", "with_genres" to "10751", "vote_count.gte" to "200")),
    )

    private val seriesRows = listOf(
        Definition("trending", "رائج هالأسبوع", "trending/tv/week"),
        Definition("top", "الأعلى تقييماً", "discover/tv", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "1500")),
        Definition("mini", "مسلسلات قصيرة · موسم واحد", "discover/tv", mapOf("sort_by" to "popularity.desc", "with_type" to "2", "vote_count.gte" to "150")),
        Definition("family", "للعائلة والأطفال", "discover/tv", mapOf("sort_by" to "popularity.desc", "with_genres" to "10751|10762", "vote_count.gte" to "100")),
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<HomeCatalogSection>>()

    suspend fun load(contentType: String): List<HomeCatalogSection> {
        val cacheKey = contentType
        mutex.withLock { cache[cacheKey]?.let { return it } }
        val definitions = if (contentType == LuxoContentType.MOVIE) movieRows else seriesRows
        val sections = coroutineScope {
            definitions.map { definition ->
                async {
                    val items = runCatching {
                        LuxoTmdbSearch.fetchList(definition.endpoint, definition.query, contentType)
                    }.getOrDefault(emptyList())
                    items.takeIf { it.isNotEmpty() }?.let {
                        HomeCatalogSection(
                            key = "luxo_smart_${contentType}_${definition.key}",
                            title = definition.title,
                            subtitle = "TMDB",
                            addonName = "موج",
                            target = CatalogTarget.Addon(
                                manifestUrl = "luxo://smart",
                                contentType = contentType,
                                catalogId = definition.key,
                            ),
                            items = it,
                            availableItemCount = it.size,
                            hasMore = false,
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (sections.isNotEmpty()) mutex.withLock { cache[cacheKey] = sections }
        return sections
    }
}

package com.mawj.tv.data

import androidx.compose.runtime.Immutable

/** One installed addon, read from its manifest. */
@Immutable
data class Addon(
    val manifestUrl: String,
    val id: String,
    val name: String,
    val resources: List<AddonResource>,
    val types: List<String>,
    val idPrefixes: List<String>,
    val catalogs: List<CatalogDef>,
) {
    val base: String get() = manifestUrl.removeSuffix("/manifest.json").trimEnd('/')

    /** Whether this addon answers [resource] for this type and id. */
    fun serves(resource: String, type: String, id: String): Boolean {
        val r = resources.firstOrNull { it.name == resource } ?: return false
        val types = r.types ?: this.types
        val prefixes = r.idPrefixes ?: idPrefixes
        if (types.isNotEmpty() && type !in types) return false
        return prefixes.isEmpty() || prefixes.any { id.startsWith(it) }
    }
}

@Immutable
data class AddonResource(val name: String, val types: List<String>?, val idPrefixes: List<String>?)

@Immutable
data class CatalogDef(
    val addon: Addon?,
    val type: String,
    val id: String,
    val name: String,
    /** Needs an extra (search, genre...) to answer, so it isn't a home row. */
    val needsExtra: Boolean,
    val searchable: Boolean,
)

/** A title in a row or a result. */
@Immutable
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val logo: String?,
    val year: String?,
    val rating: String?,
    val description: String?,
    val genres: List<String>,
)

@Immutable
data class Meta(
    val preview: MetaPreview,
    val runtime: String?,
    val cast: List<String>,
    val directors: List<String>,
    val videos: List<Episode>,
)

@Immutable
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val thumbnail: String?,
    val overview: String?,
)

@Immutable
data class Stream(
    val addonName: String,
    val url: String,
    val name: String,
    val title: String,
    val headers: Map<String, String>,
    val quality: Int,
    val hdr: Boolean,
    val dolbyVision: Boolean,
    val sizeLabel: String?,
    val sizeBytes: Long,
    val arabic: Boolean,
    /** Streams of the same release across episodes share this (for the next episode). */
    val bingeGroup: String? = null,
)

@Immutable
data class Subtitle(val url: String, val lang: String, val label: String)

/** What the player needs to play one thing and remember where it stopped. */
@Immutable
data class PlayRequest(
    val meta: MetaPreview,
    val videoId: String,
    val episode: Episode?,
    val stream: Stream,
    val startMs: Long,
)

/** A saved position for «أكمل المشاهدة». */
@Immutable
data class Progress(
    val meta: MetaPreview,
    val videoId: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

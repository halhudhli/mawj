package com.nuvio.app.features.luxo

import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.streams.StreamSubtitle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * «تشغيل على الشاشة»: the tablet sends the exact stream it is playing (link, headers, subtitles,
 * episode and position) and the TV opens it straight in the player, replacing whatever it shows.
 */
object MawjPlayOnTv {
    /** TV: launches waiting for the navigator (the TV's own profile is set there). */
    val requests = MutableSharedFlow<PlayerLaunch>(extraBufferCapacity = 2)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val subtitles = ListSerializer(StreamSubtitle.serializer())

    /** A link the TV can't reach: the tablet's own local server, with no torrent to restart there. */
    fun canSend(launch: PlayerLaunch): Boolean {
        if (!launch.torrentInfoHash.isNullOrBlank()) return true
        val host = launch.sourceUrl.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        return launch.sourceUrl.isNotBlank() && host != "127.0.0.1" && host != "localhost" && host != "0.0.0.0"
    }

    fun encode(launch: PlayerLaunch, positionMs: Long): JsonObject = buildJsonObject {
        put("t", "play")
        put("title", launch.title)
        put("url", launch.sourceUrl)
        launch.sourceAudioUrl?.let { put("audio", it) }
        put("headers", JsonObject(launch.sourceHeaders.mapValues { JsonPrimitive(it.value) }))
        put("subs", json.encodeToJsonElement(subtitles, launch.externalSubtitles))
        launch.streamType?.let { put("streamType", it) }
        launch.logo?.let { put("logo", it) }
        launch.poster?.let { put("poster", it) }
        launch.background?.let { put("background", it) }
        launch.seasonNumber?.let { put("season", it) }
        launch.episodeNumber?.let { put("episode", it) }
        launch.episodeTitle?.let { put("episodeTitle", it) }
        launch.episodeThumbnail?.let { put("episodeThumb", it) }
        put("streamTitle", launch.streamTitle)
        launch.streamSubtitle?.let { put("streamSubtitle", it) }
        launch.bingeGroup?.let { put("binge", it) }
        put("provider", launch.providerName)
        launch.providerAddonId?.let { put("addonId", it) }
        launch.contentType?.let { put("contentType", it) }
        launch.videoId?.let { put("videoId", it) }
        put("metaId", launch.parentMetaId)
        put("metaType", launch.parentMetaType)
        launch.torrentInfoHash?.let { put("infoHash", it) }
        launch.torrentFileIdx?.let { put("fileIdx", it) }
        launch.torrentFilename?.let { put("torrentFile", it) }
        if (launch.torrentTrackers.isNotEmpty()) put("trackers", JsonArray(launch.torrentTrackers.map(::JsonPrimitive)))
        put("pos", positionMs.coerceAtLeast(0L))
        launch.contentLanguage?.let { put("lang", it) }
        launch.sourceFilename?.let { put("file", it) }
    }

    /** TV: turns the message back into a launch; null when it has no playable link. */
    fun decode(o: JsonObject): PlayerLaunch? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        val url = s("url") ?: return null
        val metaId = s("metaId") ?: return null
        val headers = (o["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
        val subs = o["subs"]?.let { runCatching { json.decodeFromJsonElement(subtitles, it) }.getOrNull() }.orEmpty()
        val trackers = (o["trackers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return PlayerLaunch(
            profileId = 0,
            title = s("title") ?: "",
            sourceUrl = url,
            sourceAudioUrl = s("audio"),
            sourceHeaders = headers,
            externalSubtitles = subs,
            streamType = s("streamType"),
            logo = s("logo"),
            poster = s("poster"),
            background = s("background"),
            seasonNumber = i("season"),
            episodeNumber = i("episode"),
            episodeTitle = s("episodeTitle"),
            episodeThumbnail = s("episodeThumb"),
            streamTitle = s("streamTitle") ?: (s("title") ?: ""),
            streamSubtitle = s("streamSubtitle"),
            bingeGroup = s("binge"),
            providerName = s("provider") ?: "",
            providerAddonId = s("addonId"),
            contentType = s("contentType"),
            videoId = s("videoId"),
            parentMetaId = metaId,
            parentMetaType = s("metaType") ?: "movie",
            torrentInfoHash = s("infoHash"),
            torrentFileIdx = i("fileIdx"),
            torrentFilename = s("torrentFile"),
            torrentTrackers = trackers,
            initialPositionMs = (o["pos"] as? JsonPrimitive)?.longOrNull ?: 0L,
            contentLanguage = s("lang"),
            sourceFilename = s("file"),
        )
    }
}

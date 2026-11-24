package com.mawj.tv.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

/**
 * The engine: talks the Stremio addon protocol (manifest, catalog, meta, stream, subtitles).
 * One shared HTTP client, answers cached in memory, and every addon asked in parallel.
 */
object Addons {
    val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 8_000
        }
        engine { config { retryOnConnectionFailure(true) } }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = HashMap<String, Pair<Long, JsonObject>>()
    private val lock = Mutex()
    private val manifests = java.util.concurrent.ConcurrentHashMap<String, Addon>()

    private suspend fun getJson(url: String, ttlMs: Long = 10 * 60_000L): JsonObject? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        lock.withLock { cache[url]?.takeIf { now - it.first < ttlMs }?.let { return@withContext it.second } }
        val body = runCatching {
            val r = http.get(url)
            if (r.status.isSuccess()) r.bodyAsText() else null
        }.getOrNull() ?: return@withContext null
        val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return@withContext null
        lock.withLock { cache[url] = now to obj }
        obj
    }

    // ---- manifests ----

    suspend fun installed(): List<Addon> = coroutineScope {
        Store.addonUrls.toList().map { url -> async { manifest(url) } }.awaitAll().filterNotNull()
    }

    suspend fun manifest(url: String): Addon? {
        manifests[url]?.let { return it }
        val o = getJson(url, ttlMs = 6 * 60 * 60_000L) ?: return null
        val addon = parseManifest(url, o)
        manifests[url] = addon
        return addon
    }

    private fun parseManifest(url: String, o: JsonObject): Addon {
        val resources = (o["resources"] as? JsonArray).orEmpty().mapNotNull { r ->
            when (r) {
                is JsonPrimitive -> AddonResource(r.content, null, null)
                is JsonObject -> AddonResource(r.str("name") ?: return@mapNotNull null, r.strList("types"), r.strList("idPrefixes"))
                else -> null
            }
        }
        val base = Addon(url, o.str("id") ?: url, o.str("name") ?: "إضافة", resources, o.strList("types").orEmpty(), o.strList("idPrefixes").orEmpty(), emptyList())
        val catalogs = (o["catalogs"] as? JsonArray).orEmpty().mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            val extras = (co["extra"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val required = extras.filter { (it["isRequired"] as? JsonPrimitive)?.content == "true" }.mapNotNull { it.str("name") } +
                co.strList("extraRequired").orEmpty()
            val searchable = extras.any { it.str("name") == "search" } || "search" in co.strList("extraSupported").orEmpty()
            CatalogDef(
                addon = null,
                type = co.str("type") ?: return@mapNotNull null,
                id = co.str("id") ?: return@mapNotNull null,
                name = co.str("name") ?: "",
                needsExtra = required.isNotEmpty(),
                searchable = searchable,
            )
        }
        return base.copy(catalogs = catalogs).let { a -> a.copy(catalogs = a.catalogs.map { it.copy(addon = a) }) }
    }

    // ---- catalogs ----

    /** Every home row: catalogs that need no extra, in the addons' order. */
    suspend fun homeRows(): List<CatalogDef> =
        installed().flatMap { a -> a.catalogs.filter { !it.needsExtra } }

    suspend fun catalog(def: CatalogDef): List<MetaPreview> {
        val a = def.addon ?: return emptyList()
        val o = getJson("${a.base}/catalog/${def.type}/${enc(def.id)}.json") ?: return emptyList()
        return (o["metas"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { m -> preview(m, def.type) } }
    }

    suspend fun search(query: String): List<MetaPreview> = coroutineScope {
        val q = query.trim()
        if (q.length < 2) return@coroutineScope emptyList()
        installed().flatMap { a -> a.catalogs.filter { it.searchable } }.map { def ->
            async {
                withTimeoutOrNull(12_000) {
                    val o = getJson("${def.addon!!.base}/catalog/${def.type}/${enc(def.id)}/search=${enc(q)}.json", ttlMs = 5 * 60_000L)
                    (o?.get("metas") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { m -> preview(m, def.type) } }
                }.orEmpty()
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    // ---- meta ----

    suspend fun meta(type: String, id: String): Meta? {
        val addon = installed().firstOrNull { it.serves("meta", type, id) } ?: return null
        val o = (getJson("${addon.base}/meta/$type/${enc(id)}.json")?.get("meta") as? JsonObject) ?: return null
        val videos = (o["videos"] as? JsonArray).orEmpty().mapNotNull { v ->
            val vo = v as? JsonObject ?: return@mapNotNull null
            Episode(
                id = vo.str("id") ?: return@mapNotNull null,
                season = vo.str("season")?.toIntOrNull() ?: 0,
                number = (vo.str("episode") ?: vo.str("number"))?.toIntOrNull() ?: 0,
                title = vo.str("name") ?: vo.str("title") ?: "",
                thumbnail = vo.str("thumbnail"),
                overview = vo.str("overview") ?: vo.str("description"),
            )
        }.sortedWith(compareBy({ if (it.season == 0) 999 else it.season }, { it.number }))
        return Meta(
            preview = preview(o, type),
            runtime = o.str("runtime"),
            cast = o.strList("cast").orEmpty(),
            directors = o.strList("director").orEmpty(),
            videos = videos,
        )
    }

    // ---- streams ----

    /** Streams from every addon that serves this video, sorted best first. */
    suspend fun streams(type: String, videoId: String): List<Stream> = coroutineScope {
        installed().filter { it.serves("stream", type, videoId) }.map { a ->
            async {
                withTimeoutOrNull(25_000) {
                    val o = getJson("${a.base}/stream/$type/${enc(videoId)}.json", ttlMs = 3 * 60_000L)
                    (o?.get("streams") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { s -> stream(a.name, s) } }
                }.orEmpty()
            }
        }.awaitAll().flatten()
            .sortedWith(compareByDescending<Stream> { it.quality }.thenByDescending { it.dolbyVision || it.hdr }.thenByDescending { it.sizeBytes })
    }

    /** The addons that answer streams for this video (the sources page loads each one by itself). */
    suspend fun streamAddons(type: String, videoId: String): List<Addon> = installed().filter { it.serves("stream", type, videoId) }

    /** One addon's streams; null when it failed or took too long. */
    suspend fun streamsFrom(a: Addon, type: String, videoId: String): List<Stream>? = withTimeoutOrNull(25_000) {
        val o = getJson("${a.base}/stream/$type/${enc(videoId)}.json", ttlMs = 3 * 60_000L) ?: return@withTimeoutOrNull null
        (o["streams"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { s -> stream(a.name, s) } }
    }

    suspend fun subtitles(type: String, videoId: String): List<Subtitle> = coroutineScope {
        installed().filter { it.serves("subtitles", type, videoId) }.map { a ->
            async {
                withTimeoutOrNull(10_000) {
                    val o = getJson("${a.base}/subtitles/$type/${enc(videoId)}.json")
                    (o?.get("subtitles") as? JsonArray).orEmpty().mapNotNull { s ->
                        val so = s as? JsonObject ?: return@mapNotNull null
                        val lang = so.str("lang") ?: ""
                        Subtitle(so.str("url") ?: return@mapNotNull null, lang, languageName(lang))
                    }
                }.orEmpty()
            }
        }.awaitAll().flatten()
    }

    // ---- parsing ----

    private fun preview(o: JsonObject, fallbackType: String) = MetaPreview(
        id = o.str("id") ?: o.str("imdb_id") ?: "",
        type = o.str("type") ?: fallbackType,
        name = o.str("name") ?: "",
        poster = o.str("poster"),
        background = o.str("background"),
        logo = o.str("logo"),
        year = o.str("releaseInfo") ?: o.str("year"),
        rating = o.str("imdbRating"),
        description = o.str("description"),
        genres = o.strList("genres") ?: o.strList("genre").orEmpty(),
    )

    private fun stream(addonName: String, o: JsonObject): Stream? {
        val url = o.str("url") ?: return null // torrents without a link need a torrent engine (later)
        val name = o.str("name") ?: addonName
        val title = o.str("title") ?: o.str("description") ?: ""
        val hints = o["behaviorHints"] as? JsonObject
        val headers = ((hints?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
        val text = "$name $title ${hints?.str("filename").orEmpty()}".lowercase()
        val quality = when {
            "2160" in text || "4k" in text || "uhd" in text -> 2160
            "1440" in text -> 1440
            "1080" in text -> 1080
            "720" in text -> 720
            "480" in text -> 480
            else -> 0
        }
        val bytes = hints?.str("videoSize")?.toLongOrNull() ?: parseSize(text)
        val raw = "$name\n$title"
        val audioKind = when {
            "truehd" in text -> "TrueHD"
            Regex("dts[-. ]?(hd|x|ma)").containsMatchIn(text) -> "DTS-HD"
            "dts" in text -> "DTS"
            Regex("ddp|dd\\+|e-?ac-?3|eac3").containsMatchIn(text) -> "DD+"
            Regex("\\bdd\\b|ac-?3|dolby digital").containsMatchIn(text) -> "DD"
            "aac" in text -> "AAC"
            "opus" in text -> "Opus"
            else -> null
        }
        val channels = Regex("\\b([1-9])[. ]([01])\\b").find(text)?.value?.replace(' ', '.')
        val audio = listOfNotNull(listOfNotNull(audioKind, channels).joinToString(" ").ifBlank { null }, if ("atmos" in text) "Atmos" else null)
            .joinToString(" · ").ifBlank { null }
        val dv = "dolby vision" in text || Regex("\\bdv\\b|dovi").containsMatchIn(text)
        val hdrLabel = when {
            dv && "hdr" in text -> "DV · HDR"
            dv -> "DV"
            "hdr10+" in text || "hdr10plus" in text -> "HDR10+"
            "hdr10" in text -> "HDR10"
            "hdr" in text -> "HDR"
            else -> null
        }
        val cached = when {
            "⚡" in raw || Regex("\\[(rd|tb|ad|pm|dl|ed|oc|pp)\\+\\]").containsMatchIn(text) || "instant" in text || "cached" in text -> true
            else -> false
        }
        val seeders = Regex("(?:👤|seeders?:?|seeds?:?)\\s*(\\d+)").find(raw.lowercase())?.groupValues?.get(1)?.toIntOrNull()
        val file = hints?.str("filename") ?: raw.lines().map { it.trim().replace(Regex("^[^A-Za-z0-9\\[(]+"), "") }.firstOrNull { Regex("\\.(mkv|mp4|avi|m4v|ts)$", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        return Stream(
            addonName = addonName,
            url = url,
            name = name.replace("\n", " "),
            title = title,
            headers = headers,
            quality = quality,
            hdr = "hdr" in text,
            dolbyVision = "dolby vision" in text || Regex("\\bdv\\b|dovi").containsMatchIn(text),
            sizeLabel = if (bytes > 0) formatSize(bytes) else null,
            sizeBytes = bytes,
            arabic = "arabic" in text || "عرب" in text || Regex("\\bara?\\b").containsMatchIn(text),
            bingeGroup = hints?.str("bingeGroup"),
            source = when {
                "remux" in text -> "REMUX"
                Regex("blu-?ray|bdrip|brrip|\\bbd\\b").containsMatchIn(text) -> "BluRay"
                Regex("web-?dl").containsMatchIn(text) -> "WEB-DL"
                "webrip" in text || "web-rip" in text -> "WEBRip"
                "hdtv" in text -> "HDTV"
                Regex("hdcam|camrip|telesync|\\bcam\\b|\\bhdts\\b").containsMatchIn(text) -> "CAM"
                Regex("\\bweb\\b").containsMatchIn(text) -> "WEB"
                else -> null
            },
            codec = when {
                Regex("hevc|x265|h\\.?265").containsMatchIn(text) -> "HEVC"
                "av1" in text -> "AV1"
                Regex("x264|h\\.?264|\\bavc\\b").containsMatchIn(text) -> "H.264"
                else -> null
            },
            tenBit = Regex("10[- ]?bit").containsMatchIn(text),
            hdrLabel = hdrLabel,
            audio = audio,
            cached = cached,
            seeders = seeders,
            file = file,
        )
    }

    private fun parseSize(text: String): Long {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(gb|mb|gib|mib)").find(text) ?: return 0L
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        return (n * if (m.groupValues[2].startsWith("g")) 1_073_741_824.0 else 1_048_576.0).toLong()
    }

    private fun formatSize(bytes: Long): String =
        if (bytes >= 1_073_741_824L) String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        else "${bytes / 1_048_576L} MB"

    fun languageName(code: String): String = when (code.lowercase().take(3)) {
        "ara", "ar" -> "العربية"
        "eng", "en" -> "الإنجليزية"
        "fre", "fra", "fr" -> "الفرنسية"
        "spa", "es" -> "الإسبانية"
        "tur", "tr" -> "التركية"
        "ger", "deu", "de" -> "الألمانية"
        "ita", "it" -> "الإيطالية"
        "por", "pt" -> "البرتغالية"
        "per", "fas", "fa" -> "الفارسية"
        "urd", "ur" -> "الأردية"
        "hin", "hi" -> "الهندية"
        "kor", "ko" -> "الكورية"
        "jpn", "ja" -> "اليابانية"
        "chi", "zho", "zh" -> "الصينية"
        "rus", "ru" -> "الروسية"
        else -> code.ifBlank { "غير معروفة" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

internal fun JsonObject.strList(key: String): List<String>? = when (val v: JsonElement? = this[key]) {
    is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> v.contentOrNull?.let { listOf(it) }
    else -> null
}

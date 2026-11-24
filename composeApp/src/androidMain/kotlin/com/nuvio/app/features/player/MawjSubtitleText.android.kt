package com.nuvio.app.features.player

import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.autosync.SubtitleCharsetDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Android side of «ترميز الترجمة»: raw bytes in, text by the Mawj rule out. */
internal object MawjSubtitleText {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun decode(bytes: ByteArray, languageHint: String?): String =
        MawjSubtitleDecode.decode(bytes, languageHint) { b, hint -> SubtitleCharsetDetector.decode(b, languageHint = hint) }

    /** The subtitle's bytes, from the network (the app's own HTTP client) or a local file. */
    suspend fun fetchBytes(url: String, headers: Map<String, String>): ByteArray = withContext(Dispatchers.IO) {
        when {
            url.startsWith("file:", ignoreCase = true) -> File(URI(url)).readBytes()
            url.startsWith("/") -> File(url).readBytes()
            else -> {
                val response = httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers,
                    body = "",
                    maxResponseBodyBytes = MAX_BYTES,
                )
                check(response.status in 200..299) { "Subtitle HTTP ${response.status}" }
                response.bodyBytes
            }
        }.let(::gunzipIfNeeded)
    }

    /** Some providers send the subtitle gzipped without saying so. */
    private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size < 2 || bytes[0] != 0x1F.toByte() || bytes[1] != 0x8B.toByte()) return bytes
        return runCatching { java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() } }.getOrDefault(bytes)
    }
}

actual suspend fun mawjFetchSubtitleText(
    url: String,
    headers: Map<String, String>,
    languageHint: String?,
): String {
    val bytes = MawjSubtitleText.fetchBytes(url, headers)
    return withContext(Dispatchers.Default) { MawjSubtitleText.decode(bytes, languageHint) }
}

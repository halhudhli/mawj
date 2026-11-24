package com.nuvio.app.features.player

import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.addons.httpRequestRaw

actual suspend fun mawjFetchSubtitleText(
    url: String,
    headers: Map<String, String>,
    languageHint: String?,
): String {
    if (!url.startsWith("http", ignoreCase = true)) return httpGetTextWithHeaders(url, headers)
    val response = httpRequestRaw(method = "GET", url = url, headers = headers, body = "", maxResponseBodyBytes = 8 * 1024 * 1024)
    check(response.status in 200..299) { "Subtitle HTTP ${response.status}" }
    return MawjSubtitleDecode.decode(response.bodyBytes, languageHint) { b, _ -> b.decodeToString() }
}

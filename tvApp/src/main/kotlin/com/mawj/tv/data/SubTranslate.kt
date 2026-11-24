package com.mawj.tv.data

import com.mawj.tv.MawjTv
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * «ترجمة للعربي» on the TV: a subtitle in another language (English first) is read with its right
 * encoding, cleaned into plain WebVTT lines, translated by Gemini with the same timings, and kept
 * on disk for next time. An Arabic file is never sent.
 */
object SubTranslate {
    sealed interface Result {
        data class Ready(val path: String) : Result
        data object AlreadyArabic : Result
        data object Unreadable : Result
        data object Failed : Result
    }

    private data class Cue(val start: Long, val end: Long, val text: String)

    private fun isArabic(c: Char) = c in 'ؠ'..'ي' || c in 'ٮ'..'ۓ'

    private fun looksArabic(text: String): Boolean {
        var letters = 0; var ar = 0
        for (c in text.take(6000)) if (c.isLetter()) { letters++; if (isArabic(c)) ar++ }
        return letters > 0 && ar * 10 >= letters * 3
    }

    private fun decode(bytes: ByteArray, lang: String): String {
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8 != null) return utf8.removePrefix("﻿")
        val cs = when (lang.lowercase().take(2)) {
            "ar" -> "windows-1256"; "tr" -> "windows-1254"; "ru", "uk", "bg", "sr" -> "windows-1251"
            "el" -> "windows-1253"; "he", "iw" -> "windows-1255"; "pl", "cs", "hu", "ro", "hr", "sk" -> "windows-1250"
            else -> "windows-1252"
        }
        return String(bytes, runCatching { Charset.forName(cs) }.getOrDefault(Charsets.ISO_8859_1))
    }

    private fun ms(t: String): Long? {
        val m = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})[.,](\\d{1,3})").find(t.trim()) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0
        return ((h * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[4].padEnd(3, '0').toLong()
    }

    private fun parse(text: String): List<Cue> =
        text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n")).mapNotNull { block ->
            val lines = block.trim().lines()
            val ti = lines.indexOfFirst { "-->" in it }
            if (ti < 0) return@mapNotNull null
            val (a, b) = lines[ti].split("-->").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            val s = ms(a) ?: return@mapNotNull null
            val e = ms(b) ?: return@mapNotNull null
            val body = lines.drop(ti + 1).joinToString("\n")
                .replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").trim()
            if (body.isBlank()) null else Cue(s, e, body)
        }

    private fun vttTime(ms: Long): String =
        String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", ms / 3_600_000, (ms / 60_000) % 60, (ms / 1000) % 60, ms % 1000)

    suspend fun translate(sub: Subtitle, onProgress: (Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val dir = File(MawjTv.app.filesDir, "translated").apply { mkdirs() }
        val out = File(dir, "ar_${sub.url.hashCode().toUInt().toString(16)}.vtt")
        if (out.isFile && out.length() > 20) return@withContext Result.Ready(out.absolutePath)
        val bytes = runCatching { Addons.http.get(sub.url).readRawBytes() }.getOrNull() ?: return@withContext Result.Unreadable
        val text = decode(bytes, sub.lang)
        if (looksArabic(text)) return@withContext Result.AlreadyArabic
        val cues = parse(text)
        if (cues.isEmpty()) return@withContext Result.Unreadable
        val from = Addons.languageName(sub.lang).let { if (it == sub.lang) "the original language" else sub.lang }
        val batches = cues.chunked(80)
        var done = 0
        val gate = Semaphore(3)
        val translated = coroutineScope {
            batches.map { batch ->
                async {
                    gate.withPermit {
                        var result: List<String>? = null
                        repeat(2) {
                            if (result != null) return@repeat
                            val input = kotlinx.serialization.json.JsonArray(batch.map { kotlinx.serialization.json.JsonPrimitive(it.text) }).toString()
                            val answer = Gemini.ask(
                                "Translate these movie/TV subtitle lines (language code: $from) to natural, fluent Arabic " +
                                    "(Modern Standard Arabic that sounds natural to Saudi viewers). Keep names as they are, keep line breaks " +
                                    "inside an item as \\n, keep it short like real subtitles, no explanations. Return ONLY a JSON array of " +
                                    "${batch.size} strings in the same order.\n$input",
                            )
                            result = Gemini.stringArray(answer)?.takeIf { it.size == batch.size }
                        }
                        done++
                        onProgress(done * 100 / batches.size)
                        result
                    }
                }
            }.awaitAll()
        }
        if (translated.count { it == null } * 4 > translated.size) return@withContext Result.Failed
        val lines = translated.flatMapIndexed { i, t -> t ?: batches[i].map { it.text } }
        val vtt = buildString {
            append("WEBVTT\n\n")
            cues.forEachIndexed { i, c ->
                append(vttTime(c.start)).append(" --> ").append(vttTime(c.end)).append('\n')
                append(lines.getOrNull(i).orEmpty().replace("\\n", "\n").trim().ifBlank { c.text }).append("\n\n")
            }
        }
        out.writeText(vtt, Charsets.UTF_8)
        Result.Ready(out.absolutePath)
    }
}

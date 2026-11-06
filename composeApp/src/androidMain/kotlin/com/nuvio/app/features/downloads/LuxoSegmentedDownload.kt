package com.nuvio.app.features.downloads

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * «تحميل متعدد الاتصالات»: debrid CDNs (TorBox, Real-Debrid…) cap each connection, so a big file is
 * split into parts that download at the same time, each written at its own offset. Progress of every
 * part is kept in a small side file, so a paused or interrupted download resumes where each part stopped.
 */
internal object LuxoSegmentedDownload {
    private const val MIN_SIZE = 24L * 1024 * 1024

    fun enabled(): Boolean = runCatching {
        com.nuvio.app.features.luxo.LuxoFeaturesRepository.current().fastDownload
    }.getOrDefault(true)

    private fun connections(): Int = if (runCatching { com.nuvio.app.features.luxo.luxoLiteMode() }.getOrDefault(false)) 4 else 8

    private class Part(val start: Long, val end: Long, val done: AtomicLong)

    private fun readState(file: File, total: Long): List<Part>? = runCatching {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.firstOrNull()?.toLongOrNull() != total) return null
        lines.drop(1).map { l ->
            val (s, e, d) = l.split(',').map { it.trim().toLong() }
            Part(s, e, AtomicLong(d.coerceIn(0L, e - s)))
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun writeState(file: File, total: Long, parts: List<Part>) = runCatching {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(buildString {
            appendLine(total)
            parts.forEach { appendLine("${it.start},${it.end},${it.done.get()}") }
        })
        tmp.renameTo(file)
    }

    /** True when a segmented download of this file is already under way. */
    fun hasState(state: File): Boolean = state.isFile

    fun stateTotal(state: File): Long? = runCatching { state.readLines().firstOrNull()?.toLongOrNull() }.getOrNull()

    fun worthIt(total: Long?, acceptsRanges: Boolean): Boolean = enabled() && acceptsRanges && total != null && total >= MIN_SIZE

    private fun request(item: DownloadItem, from: Long, to: Long, validator: String?): Request =
        Request.Builder().url(item.sourceUrl).apply {
            item.sourceHeaders.forEach { (key, value) ->
                if (!key.equals("Range", true) && !key.equals("If-Range", true) && !key.equals("Accept-Encoding", true)) header(key, value)
            }
            header("Accept-Encoding", "identity")
            header("Range", "bytes=$from-$to")
            validator?.let { header("If-Range", it) }
        }.build()

    /** Downloads [total] bytes into [sink] over several connections. */
    suspend fun run(
        item: DownloadItem,
        sink: DownloadSink,
        state: File,
        total: Long,
        validator: String?,
        client: OkHttpClient,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) = coroutineScope {
        val parts = readState(state, total) ?: run {
            val n = connections()
            val size = total / n
            List(n) { i ->
                val s = i * size
                val e = if (i == n - 1) total else s + size
                Part(s, e, AtomicLong(0L))
            }.also { writeState(state, total, it) }
        }
        val downloaded = AtomicLong(parts.sumOf { it.done.get() })
        sink.openChannel().use { channel ->
            if (channel.size() > total) channel.truncate(total)
            val ticker = launch {
                while (isActive) {
                    onProgress(downloaded.get(), total)
                    writeState(state, total, parts)
                    delay(1_000L)
                }
            }
            try {
                parts.map { part ->
                    async(Dispatchers.IO) {
                        var failures = 0
                        while (part.done.get() < part.end - part.start) {
                            ensureActive()
                            val from = part.start + part.done.get()
                            try {
                                client.newCall(request(item, from, part.end - 1, validator)).execute().use { response ->
                                    if (response.code != 206) {
                                        if (!response.isSuccessful) throw DownloadHttpException(response.code)
                                        throw IOException("Server stopped honouring ranges")
                                    }
                                    val range = response.header("Content-Range")?.let(::parseDownloadContentRange)
                                    if (range == null || range.first != from) throw IOException("Server returned an invalid download range")
                                    val input = response.body?.byteStream() ?: throw IOException("Download response is empty")
                                    val buffer = ByteArray(256 * 1024)
                                    input.use {
                                        while (part.done.get() < part.end - part.start) {
                                            ensureActive()
                                            val left = (part.end - part.start - part.done.get()).coerceAtMost(buffer.size.toLong()).toInt()
                                            val n = it.read(buffer, 0, left)
                                            if (n == -1) break
                                            var pos = part.start + part.done.get()
                                            val bb = ByteBuffer.wrap(buffer, 0, n)
                                            while (bb.hasRemaining()) pos += channel.write(bb, pos)
                                            part.done.addAndGet(n.toLong())
                                            downloaded.addAndGet(n.toLong())
                                            failures = 0
                                        }
                                    }
                                }
                            } catch (http: DownloadHttpException) {
                                throw http
                            } catch (error: IOException) {
                                ensureActive()
                                failures++
                                if (failures > 6) throw error
                                delay(1_500L * failures)
                            }
                        }
                    }
                }.awaitAll()
            } finally {
                ticker.cancel()
                writeState(state, total, parts)
            }
            withContext(Dispatchers.IO) { channel.force(true) }
        }
        if (downloaded.get() < total) throw IOException("Download ended before all bytes were received")
        onProgress(total, total)
        state.delete()
    }
}

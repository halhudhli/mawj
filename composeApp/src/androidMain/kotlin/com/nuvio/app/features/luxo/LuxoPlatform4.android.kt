package com.nuvio.app.features.luxo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.nuvio.app.features.player.luxoCaptureVideoFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Calendar
import java.util.TimeZone

// ---------------------------------------------------------------- image picker

@Composable
actual fun rememberLuxoImagePicker(onPicked: (ByteArray, String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            // Large photos are scaled down to keep the request small.
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching
            val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
            val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
            callback.value(out.toByteArray(), "image/jpeg")
        }
    }
    return { runCatching { launcher.launch("image/*") } }
}

// ---------------------------------------------------------------- GIF

actual suspend fun luxoRecordGif(name: String, seconds: Int, onProgress: (Float) -> Unit): Boolean = runCatching {
    val fps = 8
    val total = seconds * fps
    val frames = ArrayList<Bitmap>(total)
    repeat(total) { i ->
        val f = luxoCaptureVideoFrame(480, 270)?.asAndroidBitmap() ?: return@runCatching false
        frames += f.copy(Bitmap.Config.ARGB_8888, false)
        onProgress((i + 1f) / total * 0.7f)
        delay(1000L / fps)
    }
    val context = LuxoPrefsStorage.appContext ?: return@runCatching false
    withContext(Dispatchers.Default) {
        val bytes = LuxoGifEncoder.encode(frames, 100 / fps) { p -> onProgress(0.7f + 0.3f * p) }
        withContext(Dispatchers.IO) {
            val safe = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(60).ifBlank { "Mawj" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${System.currentTimeMillis()}.gif")
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mawj")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }
    }
}.getOrDefault(false)

/** A small GIF89a encoder: a fixed 6×7×6 colour palette and LZW compression. */
private object LuxoGifEncoder {
    private fun index(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 6 / 256) * 42 + (g * 7 / 256) * 6 + (b * 6 / 256)
    }

    fun encode(frames: List<Bitmap>, delayCs: Int, onProgress: (Float) -> Unit): ByteArray {
        val w = frames.first().width
        val h = frames.first().height
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        writeShort(out, w); writeShort(out, h)
        out.write(0xF7) // global colour table, 256 entries
        out.write(0); out.write(0)
        for (i in 0 until 256) {
            if (i < 252) {
                val r = i / 42; val g = (i / 6) % 7; val b = i % 6
                out.write(r * 255 / 5); out.write(g * 255 / 6); out.write(b * 255 / 5)
            } else {
                out.write(0); out.write(0); out.write(0)
            }
        }
        // Loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
        val pixels = IntArray(w * h)
        val idx = ByteArray(w * h)
        frames.forEachIndexed { n, bmp ->
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) idx[i] = index(pixels[i]).toByte()
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); writeShort(out, delayCs); out.write(0); out.write(0)
            out.write(0x2C); writeShort(out, 0); writeShort(out, 0); writeShort(out, w); writeShort(out, h); out.write(0)
            lzw(out, idx, 8)
            onProgress((n + 1f) / frames.size)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    private fun writeShort(o: OutputStream, v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }

    private fun lzw(o: OutputStream, data: ByteArray, minCode: Int) {
        o.write(minCode)
        val clear = 1 shl minCode
        val eoi = clear + 1
        val block = ByteArrayOutputStream()
        var cur = 0; var bits = 0
        var codeSize = minCode + 1
        fun emit(code: Int) {
            cur = cur or (code shl bits); bits += codeSize
            while (bits >= 8) { block.write(cur and 0xFF); cur = cur ushr 8; bits -= 8 }
        }
        val dict = HashMap<Int, Int>(8192)
        var next = eoi + 1
        emit(clear)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) {
                prefix = found
            } else {
                emit(prefix)
                if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
                if (next < 4096) {
                    dict[key] = next++
                } else {
                    emit(clear); dict.clear(); next = eoi + 1; codeSize = minCode + 1
                }
                prefix = k
            }
        }
        emit(prefix)
        if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
        emit(eoi)
        if (bits > 0) block.write(cur and 0xFF)
        val bytes = block.toByteArray()
        var p = 0
        while (p < bytes.size) {
            val n = minOf(255, bytes.size - p)
            o.write(n); o.write(bytes, p, n); p += n
        }
        o.write(0)
    }
}

// ---------------------------------------------------------------- remote

actual object LuxoRemoteNet {
    actual val port: Int = 47850
    private const val DISCOVERY_PORT = 47851
    @Volatile private var server: ServerSocket? = null
    @Volatile private var discovery: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())

    private val starting = java.util.concurrent.atomic.AtomicBoolean(false)

    actual fun startServer(code: String, deviceName: String, onCommand: (String) -> Unit) {
        // The sockets are opened on background threads, so a second quick call must not start a second pair.
        if (server != null || !starting.compareAndSet(false, true)) return
        val context = LuxoPrefsStorage.appContext
        runCatching {
            lock = (context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("mawj-remote")?.apply { setReferenceCounted(false); acquire() }
        }
        Thread {
            runCatching {
                val ss = ServerSocket()
                ss.reuseAddress = true
                runCatching { ss.bind(InetSocketAddress(port)) }.onFailure { runCatching { ss.close() }; starting.set(false); throw it }
                server = ss
                while (!ss.isClosed) {
                    val s = runCatching { ss.accept() }.getOrNull() ?: break
                    Thread {
                        runCatching {
                            s.soTimeout = 5000
                            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                            val line = reader.readLine().orEmpty()
                            val sep = line.indexOf('|')
                            val ok = sep > 0 && line.substring(0, sep) == code
                            s.getOutputStream().write((if (ok) "ok\n" else "denied\n").toByteArray())
                            if (ok) {
                                val payload = line.substring(sep + 1)
                                main.post { runCatching { onCommand(payload) } }
                            }
                        }
                        runCatching { s.close() }
                    }.start()
                }
            }
        }.apply { isDaemon = true }.start()
        Thread {
            runCatching {
                val ds = DatagramSocket(null)
                ds.reuseAddress = true
                ds.bind(InetSocketAddress(DISCOVERY_PORT))
                discovery = ds
                val buf = ByteArray(256)
                while (!ds.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    runCatching { ds.receive(packet) }.getOrNull() ?: break
                    if (String(packet.data, 0, packet.length) == "MAWJ?") {
                        val reply = "MAWJ!|$deviceName|$port".toByteArray()
                        runCatching { ds.send(DatagramPacket(reply, reply.size, packet.address, packet.port)) }
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    actual fun stopServer() {
        runCatching { server?.close() }
        runCatching { discovery?.close() }
        runCatching { lock?.release() }
        server = null
        discovery = null
        starting.set(false)
    }

    actual fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    actual suspend fun discover(timeoutMs: Long): List<LuxoRemoteDevice> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, LuxoRemoteDevice>()
        runCatching {
            DatagramSocket().use { ds ->
                ds.broadcast = true
                ds.soTimeout = 400
                val ask = "MAWJ?".toByteArray()
                val end = System.currentTimeMillis() + timeoutMs
                var lastSend = 0L
                val buf = ByteArray(256)
                while (System.currentTimeMillis() < end) {
                    if (System.currentTimeMillis() - lastSend > 700) {
                        runCatching { ds.send(DatagramPacket(ask, ask.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)) }
                        lastSend = System.currentTimeMillis()
                    }
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        ds.receive(packet)
                        val parts = String(packet.data, 0, packet.length).split('|')
                        val host = packet.address.hostAddress
                        if (parts.size == 3 && parts[0] == "MAWJ!" && host != null) {
                            found[host] = LuxoRemoteDevice(parts[1], host, parts[2].toIntOrNull() ?: port)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        }
        found.values.toList()
    }

    actual suspend fun send(host: String, port: Int, code: String, payload: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 2500)
                s.soTimeout = 3000
                s.getOutputStream().write("$code|${payload.replace('\n', ' ')}\n".toByteArray(Charsets.UTF_8))
                BufferedReader(InputStreamReader(s.getInputStream())).readLine() == "ok"
            }
        }.getOrDefault(false)
    }
}


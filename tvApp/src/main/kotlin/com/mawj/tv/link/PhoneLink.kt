package com.mawj.tv.link

import android.os.Handler
import android.os.Looper
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Store
import com.mawj.tv.ui.SearchState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * «الربط بالجوال»: a tiny page served by the TV on the home network. The phone opens it (QR code)
 * to paste an addon link or type a search, so nothing has to be typed with the remote.
 */
object PhoneLink {
    const val PORT = 8765
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())

    val address: String?
        get() = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()?.let { "http://$it:$PORT" }

    fun start() {
        if (server != null) return
        thread(name = "mawj-link", isDaemon = true) {
            val s = runCatching { ServerSocket(PORT) }.getOrNull() ?: return@thread
            server = s
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 8_000
        val input = BufferedReader(InputStreamReader(c.getInputStream(), Charsets.UTF_8))
        val requestLine = input.readLine() ?: return
        var length = 0
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = if (length > 0) CharArray(length.coerceAtMost(8_192)).let { buf ->
            var read = 0
            while (read < buf.size) { val n = input.read(buf, read, buf.size - read); if (n < 0) break; read += n }
            String(buf, 0, read)
        } else ""
        val form = body.split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }.toMap()
        var message = ""
        if (requestLine.startsWith("POST /add")) {
            val url = form["url"].orEmpty()
            val ok = Store.normalizeManifestUrl(url) != null
            if (ok) main.post { Store.addAddon(url) }
            message = if (ok) "أُضيفت الإضافة إلى الشاشة." else "الرابط غير صالح."
        } else if (requestLine.startsWith("POST /search")) {
            val q = form["q"].orEmpty()
            main.post { SearchState.query = q; Nav.tab(Route.Search) }
            message = "يظهر البحث الآن على الشاشة."
        }
        val html = page(message).toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(html)
        out.flush()
    }

    private fun page(message: String) = """
<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>موج — الربط بالجوال</title>
<style>
body{margin:0;background:#070D14;color:#F6F1E4;font-family:system-ui,sans-serif;padding:22px 16px}
h1{color:#F1DDA6;font-size:26px;margin:0 0 4px}p{color:#A9B6C4;margin:0 0 18px}
form{background:#0D1620;border:1px solid rgba(214,179,106,.3);border-radius:16px;padding:16px;margin-bottom:16px}
label{display:block;font-weight:600;margin-bottom:8px}
input{width:100%;box-sizing:border-box;font-size:17px;padding:12px;border-radius:12px;border:1px solid #2a3846;background:#070D14;color:#F6F1E4;direction:ltr}
button{margin-top:10px;width:100%;font-size:17px;font-weight:700;padding:12px;border:0;border-radius:999px;background:#D6B36A;color:#070D14}
.msg{background:rgba(91,191,122,.15);border:1px solid rgba(91,191,122,.5);color:#BDEBCB;border-radius:12px;padding:10px 14px;margin-bottom:16px}
</style></head><body>
<h1>موج</h1><p>ما يُرسل من هنا يظهر على الشاشة مباشرة.</p>
${if (message.isNotEmpty()) "<div class=\"msg\">$message</div>" else ""}
<form method="post" action="/add"><label>إضافة إضافة</label><input name="url" placeholder="https://…/manifest.json" autocomplete="off"><button>إضافة</button></form>
<form method="post" action="/search"><label>البحث على الشاشة</label><input name="q" dir="auto" placeholder="اسم فيلم أو مسلسل"><button>بحث</button></form>
</body></html>
""".trimIndent()
}

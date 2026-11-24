package com.mawj.tv.link

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Episode
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Store
import com.mawj.tv.data.Stream
import com.mawj.tv.data.Subtitle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** What the open player lets the tablet do and see. */
interface RemotePlayer {
    fun state(): JsonObject
    fun seekTo(ms: Long)
    fun audio(id: Int)
    fun subtitle(id: Int)
    fun subDelayMs(ms: Int)
    fun subSize(sp: Int)
    fun speed(v: Float)
    fun next()
    /** «سمعت» / «رأيت» pressed on the tablet; stamped with the video time on arrival. */
    fun syncMark(kind: String)
}

/**
 * The tablet's remote («ريموت موج») speaks to this: the same protocol as before (TCP 47850,
 * discovery on UDP 47851, a 4-digit code), so the tablet app needs no change to connect.
 */
object RemoteLink {
    private const val PORT = 47850
    private const val DISCOVERY = 47851
    private const val NAME = "موج · الشاشة"
    private val main = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var started = false
    private var lock: WifiManager.MulticastLock? = null

    lateinit var code: String
        private set

    /** A tablet asked to pair: the code shows on the TV for a while. */
    var pairingFrom by mutableStateOf<String?>(null)

    @Volatile var player: RemotePlayer? = null
    var activity: WeakReference<Activity>? = null
    private lateinit var audio: AudioManager

    fun start(context: Context) {
        if (started) return
        started = true
        val prefs = context.getSharedPreferences("mawj_tv", Context.MODE_PRIVATE)
        code = prefs.getString("remote_code", null)?.takeIf { it.length == 4 }
            ?: (1000..9999).random().toString().also { prefs.edit().putString("remote_code", it).apply() }
        audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("mawj-remote").apply { setReferenceCounted(false); acquire() }
        }
        thread(name = "mawj-remote", isDaemon = true) {
            val ss = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } }.getOrNull() ?: return@thread
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(s) }; runCatching { s.close() } }
            }
        }
        thread(name = "mawj-discovery", isDaemon = true) {
            val ds = runCatching { DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(DISCOVERY)) } }.getOrNull() ?: return@thread
            val buf = ByteArray(256)
            while (!ds.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                runCatching { ds.receive(p) }.getOrNull() ?: break
                if (String(p.data, 0, p.length) == "MAWJ?") {
                    val reply = "MAWJ!|$NAME|$PORT".toByteArray()
                    runCatching { ds.send(DatagramPacket(reply, reply.size, p.address, p.port)) }
                }
            }
        }
    }

    private fun handle(s: Socket) {
        s.soTimeout = 5_000
        val line = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).readLine().orEmpty()
        val out = s.getOutputStream()
        if (line.startsWith("PAIR|")) {
            out.write("pair\n".toByteArray())
            val from = line.substring(5).take(40)
            main.post {
                pairingFrom = from
                main.postDelayed({ if (pairingFrom == from) pairingFrom = null }, 90_000)
            }
            return
        }
        val sep = line.indexOf('|')
        val ok = sep > 0 && line.substring(0, sep) == code
        out.write((if (ok) "ok|${reply().toString().replace('\n', ' ')}\n" else "denied\n").toByteArray(Charsets.UTF_8))
        out.flush()
        if (!ok) return
        val o = runCatching { json.parseToJsonElement(line.substring(sep + 1)) as JsonObject }.getOrNull() ?: return
        main.post { runCatching { command(o) } }
    }

    private fun reply(): JsonObject {
        val p = player?.state() ?: JsonObject(emptyMap())
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val vol = if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        return JsonObject(p + ("vol" to JsonPrimitive(vol)))
    }

    private fun command(o: JsonObject) {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        val p = player
        when (s("t")) {
            "ping" -> pairingFrom = null
            "key" -> s("k")?.let(::key)
            "vol" -> volume(s("op") ?: return, i("v") ?: 0)
            "seekTo" -> (o["ms"] as? JsonPrimitive)?.longOrNull?.let { p?.seekTo(it.coerceAtLeast(0)) }
            "audio" -> i("i")?.let { p?.audio(it) }
            "sub" -> i("i")?.let { p?.subtitle(it) }
            "subDelay" -> i("ms")?.let { p?.subDelayMs(it) }
            "subSize" -> i("sp")?.let { p?.subSize(it) }
            "speed" -> (o["v"] as? JsonPrimitive)?.floatOrNull?.let { p?.speed(it) }
            "next" -> p?.next()
            "syncMark" -> s("k")?.let { p?.syncMark(it) }
            // «تشغيل على الشاشة»: straight into the player, replacing what plays.
            "play" -> play(o)
            "open" -> Unit
        }
    }

    private fun play(o: JsonObject) {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        val url = s("url") ?: return
        val metaId = s("metaId") ?: return
        val type = if (s("metaType") == "movie") "movie" else "series"
        val headers = (o["headers"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
        val subs = (o["subs"] as? JsonArray).orEmpty().mapNotNull { e ->
            val so = e as? JsonObject ?: return@mapNotNull null
            val u = (so["url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val lang = (so["language"] as? JsonPrimitive)?.contentOrNull ?: (so["lang"] as? JsonPrimitive)?.contentOrNull ?: ""
            Subtitle(u, lang, (so["name"] as? JsonPrimitive)?.contentOrNull ?: com.mawj.tv.data.Addons.languageName(lang))
        }
        val meta = MetaPreview(metaId, type, s("title") ?: "", s("poster"), s("background"), s("logo"), null, null, null, emptyList())
        val season = i("season"); val number = i("episode")
        val videoId = s("videoId") ?: if (season != null && number != null) "$metaId:$season:$number" else metaId
        val episode = if (season != null && number != null) Episode(videoId, season, number, s("episodeTitle") ?: "", s("episodeThumb"), null) else null
        val stream = Stream(
            addonName = s("provider") ?: "التابلت", url = url, name = s("streamTitle") ?: "", title = s("streamSubtitle") ?: "",
            headers = headers, quality = 0, hdr = false, dolbyVision = false, sizeLabel = null, sizeBytes = 0, arabic = false,
            bingeGroup = s("binge"),
        )
        val start = (o["pos"] as? JsonPrimitive)?.longOrNull ?: Store.positionFor(videoId)
        Store.rememberSource(metaId, stream)
        Nav.go(Route.Player(PlayRequest(meta, videoId, episode, stream, start, subs)))
    }

    private fun key(k: String) {
        val code = when (k) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "back" -> KeyEvent.KEYCODE_BACK
            else -> return
        }
        val a = activity?.get() ?: return
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun volume(op: String, value: Int) {
        val st = AudioManager.STREAM_MUSIC
        runCatching {
            when (op) {
                "up" -> audio.adjustStreamVolume(st, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "down" -> audio.adjustStreamVolume(st, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "mute" -> audio.adjustStreamVolume(st, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                "set" -> audio.setStreamVolume(st, (value.coerceIn(0, 100) * audio.getStreamMaxVolume(st) + 50) / 100, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    /** The tablet's view of the player, in the shape its remote already reads. */
    fun playerState(
        meta: MetaPreview, episode: Episode?, posMs: Long, durMs: Long, playing: Boolean,
        audio: List<Pair<Int, String>>, audioSel: Int, subs: List<Pair<Int, String>>, subSel: Int,
        subDelayMs: Int, subSizeSp: Int, speed: Float, hasNext: Boolean,
    ): JsonObject = buildJsonObject {
        put("metaId", meta.id); put("metaType", meta.type); put("title", meta.name)
        episode?.let { put("season", it.season); put("episode", it.number) }
        put("pos", posMs); put("dur", durMs); put("playing", playing)
        put("remote", buildJsonObject {
            meta.poster?.let { put("poster", it) }
            episode?.let { put("epTitle", it.title) }
            put("audio", buildJsonArray { audio.forEach { (i, l) -> add(buildJsonObject { put("i", i); put("l", l) }) } })
            put("audioSel", audioSel)
            put("subs", buildJsonArray { subs.forEach { (i, l) -> add(buildJsonObject { put("i", i); put("l", l) }) } })
            put("subSel", subSel)
            put("subDelay", subDelayMs); put("subSize", subSizeSp); put("speed", speed)
            put("resize", "fit"); put("next", hasNext); put("skip", false)
            put("addon", JsonArray(emptyList())); put("addonLoading", false); put("fonts", JsonArray(emptyList()))
            put("color", 0); put("bg", false); put("bold", false); put("pos", 0)
        })
    }
}

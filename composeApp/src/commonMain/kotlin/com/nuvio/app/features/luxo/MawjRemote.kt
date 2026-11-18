package com.nuvio.app.features.luxo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ============================================================================ shared state

/** Tab jumps the tablet can ask for (collected by the main screen). */
object MawjRemoteNav {
    val requests = MutableSharedFlow<String>(extraBufferCapacity = 4)
}

/** TV: a tablet asked to pair, so the code is on screen. */
object MawjRemotePair {
    var from by mutableStateOf<String?>(null)
    var shownAt by mutableLongStateOf(0L)

    fun onRequest(name: String) {
        from = name.ifBlank { "جهاز تحكم" }
        shownAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
    }

    fun done() { from = null }
}

/** TV: the player's tracks and settings for the tablet, and what the tablet may change. */
object MawjRemotePlayer {
    @kotlin.concurrent.Volatile var state: String = "{}"
    var actions: Actions? = null

    class Actions(
        val audio: (Int) -> Unit,
        val subtitle: (Int) -> Unit,
        val subtitleDelay: (Int) -> Unit,
        val subtitleSize: (Int) -> Unit,
        val speed: (Float) -> Unit,
        val resize: (String) -> Unit,
        val next: (() -> Unit)?,
        val skip: (() -> Unit)?,
        val addonSubtitle: (String) -> Unit = {},
        val fetchAddonSubtitles: () -> Unit = {},
        /** Subtitle look: font (null = default), text colour (ARGB), dark box behind, bold, height from the bottom. */
        val subtitleFont: (String?) -> Unit = {},
        val subtitleColor: (Int) -> Unit = {},
        val subtitleBackground: (Boolean) -> Unit = {},
        val subtitleBold: (Boolean) -> Unit = {},
        val subtitlePosition: (Int) -> Unit = {},
    )

    /** Subtitles from add-ons and the subtitle look, sent next to the built-in tracks. */
    class SubtitleExtras(
        val addon: List<Pair<String, String>> = emptyList(),
        val addonSelected: String? = null,
        val addonLoading: Boolean = false,
        val fonts: List<Pair<String, String>> = emptyList(),
        val font: String? = null,
        val color: Int = -1,
        val background: Boolean = false,
        val bold: Boolean = false,
        val position: Int = 20,
    )

    fun publish(
        poster: String?, episodeTitle: String?,
        audio: List<Pair<Int, String>>, audioSelected: Int,
        subtitles: List<Pair<Int, String>>, subtitleSelected: Int,
        subtitleDelayMs: Int, subtitleSizeSp: Int,
        speed: Float, resize: String, hasNext: Boolean, hasSkip: Boolean,
        extras: SubtitleExtras = SubtitleExtras(),
    ) {
        state = buildJsonObject {
            poster?.let { put("poster", it) }
            episodeTitle?.let { put("epTitle", it) }
            put("audio", JsonArray(audio.map { (i, l) -> buildJsonObject { put("i", i); put("l", l) } }))
            put("audioSel", audioSelected)
            put("subs", JsonArray(subtitles.map { (i, l) -> buildJsonObject { put("i", i); put("l", l) } }))
            put("subSel", subtitleSelected)
            put("subDelay", subtitleDelayMs); put("subSize", subtitleSizeSp)
            put("speed", speed); put("resize", resize); put("next", hasNext); put("skip", hasSkip)
            put("addon", JsonArray(extras.addon.map { (k, l) -> buildJsonObject { put("k", k); put("l", l) } }))
            extras.addonSelected?.let { put("addonSel", it) }
            put("addonLoading", extras.addonLoading)
            put("fonts", JsonArray(extras.fonts.map { (f, l) -> buildJsonObject { put("f", f); put("l", l) } }))
            extras.font?.let { put("font", it) }
            put("color", extras.color); put("bg", extras.background); put("bold", extras.bold); put("pos", extras.position)
        }.toString()
    }

    fun clear() { state = "{}"; actions = null }
}

// ============================================================================ TV side

/** TV: runs one of the new remote commands; false when [type] is not one of them. */
internal fun mawjHandleRemoteCommand(type: String?, o: JsonObject): Boolean {
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
    val player = com.nuvio.app.features.player.LuxoPlayerBridge.remoteController
    val acts = MawjRemotePlayer.actions
    when (type) {
        "ping" -> MawjRemotePair.done()
        "key" -> s("k")?.let { mawjRemoteKey(it) }
        "nav" -> s("to")?.let { MawjRemoteNav.requests.tryEmit(it) }
        "vol" -> mawjRemoteVolume(s("op") ?: return true, i("v") ?: 0)
        "seekTo" -> (o["ms"] as? JsonPrimitive)?.longOrNull?.let { player?.seekTo(it.coerceAtLeast(0L)) }
        "audio" -> i("i")?.let { acts?.audio?.invoke(it) }
        "sub" -> i("i")?.let { acts?.subtitle?.invoke(it) }
        "subDelay" -> i("ms")?.let { acts?.subtitleDelay?.invoke(it) }
        "subSize" -> i("sp")?.let { acts?.subtitleSize?.invoke(it) }
        "speed" -> (o["v"] as? JsonPrimitive)?.floatOrNull?.let { acts?.speed?.invoke(it) }
        "resize" -> s("m")?.let { acts?.resize?.invoke(it) }
        "addonSub" -> s("k")?.let { acts?.addonSubtitle?.invoke(it) }
        "fetchSubs" -> acts?.fetchAddonSubtitles?.invoke()
        "subFont" -> acts?.subtitleFont?.invoke(s("f")?.takeIf { it.isNotBlank() })
        "subColor" -> i("c")?.let { acts?.subtitleColor?.invoke(it) }
        "subBg" -> (o["on"] as? JsonPrimitive)?.booleanOrNull?.let { acts?.subtitleBackground?.invoke(it) }
        "subBold" -> (o["on"] as? JsonPrimitive)?.booleanOrNull?.let { acts?.subtitleBold?.invoke(it) }
        "subPos" -> i("v")?.let { acts?.subtitlePosition?.invoke(it) }
        "next" -> acts?.next?.invoke()
        "skip" -> acts?.skip?.invoke()
        else -> return false
    }
    return true
}

/** TV: the pairing code, shown by itself when a tablet picks this TV. */
@Composable
internal fun MawjPairCodeOverlay() {
    val from = MawjRemotePair.from ?: return
    var left by remember(MawjRemotePair.shownAt) { mutableIntStateOf(60) }
    LaunchedEffect(MawjRemotePair.shownAt) {
        while (left > 0) { delay(1000); left-- }
        MawjRemotePair.done()
    }
    Dialog(onDismissRequest = { MawjRemotePair.done() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xF5121C26), Color(0xFA070D14))))
                .border(1.dp, RGoldLine, RoundedCornerShape(26.dp)).padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("طلب اتصال من $from", color = RMuted, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Digits read left to right, as typed on the tablet.
                LuxoRemote.code.forEach { d ->
                    Box(
                        Modifier.width(76.dp).height(100.dp).clip(RoundedCornerShape(16.dp)).background(RAbyss)
                            .border(1.dp, RGoldLine, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(d.toString(), color = RMoon, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 58.sp) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(Modifier.size(20.dp)) {
                    drawCircle(Color(0x1FEEF2F6), style = Stroke(3.dp.toPx()))
                    drawArc(RGold, -90f, 360f * left / 60f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                Text("يُدخل الرمز في جهاز التحكم · يختفي بعد $left ثانية", color = RFaint, fontSize = 14.sp)
            }
        }
    }
}

// ============================================================================ controller side

private val RAbyss = Color(0xFF03070B)
private val RSea = Color(0xFF070D14)
private val RRaised = Color(0xFF0D1620)
private val RCard = Color(0xFF121C26)
private val RTide = Color(0xFF1C3E5C)
private val RCrest = Color(0xFF2E5470)
private val RGold = Color(0xFFD6B36A)
private val RGoldDeep = Color(0xFFA9843F)
private val RGoldLight = Color(0xFFF1DDA6)
private val RMoon = Color(0xFFFBF3DC)
private val RInk = Color(0xFFEEF2F6)
private val RInkStrong = Color(0xFFF6F1E4)
private val RMuted = Color(0xFFA9B6C4)
private val RFaint = Color(0xFF8796A8)
private val ROnGold = Color(0xFF070D14)
private val ROk = Color(0xFF6FC3A5)
private val RLine = Color(0x1AEEF2F6)
private val RGoldLine = Color(0x47D6B36A)

/** What the TV says it is showing. */
private class RemoteNow(o: JsonObject?) {
    private fun s(k: String, src: JsonObject? = root) = (src?.get(k) as? JsonPrimitive)?.contentOrNull
    private fun l(k: String, src: JsonObject? = root) = (src?.get(k) as? JsonPrimitive)?.longOrNull
    private fun i(k: String, src: JsonObject? = root) = (src?.get(k) as? JsonPrimitive)?.intOrNull
    private val root = o
    private val r = o?.get("remote") as? JsonObject
    val title = s("title")?.takeIf { it.isNotBlank() }
    val season = i("season"); val episode = i("episode")
    val pos = l("pos") ?: 0L; val dur = l("dur") ?: 0L
    val playing = (o?.get("playing") as? JsonPrimitive)?.booleanOrNull ?: false
    val vol = i("vol") ?: -1
    val poster = s("poster", r); val epTitle = s("epTitle", r)
    private fun list(k: String) = (r?.get(k) as? JsonArray).orEmpty().mapNotNull { e ->
        val eo = e as? JsonObject ?: return@mapNotNull null
        val idx = (eo["i"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        idx to ((eo["l"] as? JsonPrimitive)?.contentOrNull ?: "مسار ${idx + 1}")
    }
    val audio = list("audio"); val audioSel = i("audioSel", r) ?: -1
    val subs = list("subs"); val subSel = i("subSel", r) ?: -1
    val subDelay = i("subDelay", r) ?: 0; val subSize = i("subSize", r) ?: 18
    val speed = (r?.get("speed") as? JsonPrimitive)?.floatOrNull ?: 1f
    val resize = s("resize", r) ?: "Fit"
    val hasNext = (r?.get("next") as? JsonPrimitive)?.booleanOrNull ?: false
    val hasSkip = (r?.get("skip") as? JsonPrimitive)?.booleanOrNull ?: false
    private fun pairs(k: String, a: String, b: String) = (r?.get(k) as? JsonArray).orEmpty().mapNotNull { e ->
        val eo = e as? JsonObject ?: return@mapNotNull null
        val key = (eo[a] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        key to ((eo[b] as? JsonPrimitive)?.contentOrNull ?: key)
    }
    val addon = pairs("addon", "k", "l"); val addonSel = s("addonSel", r)
    val addonLoading = (r?.get("addonLoading") as? JsonPrimitive)?.booleanOrNull ?: false
    val fonts = pairs("fonts", "f", "l"); val font = s("font", r)
    val color = i("color", r) ?: -1
    val subBg = (r?.get("bg") as? JsonPrimitive)?.booleanOrNull ?: false
    val subBold = (r?.get("bold") as? JsonPrimitive)?.booleanOrNull ?: false
    val subPos = i("pos", r) ?: 20
    val playingSomething get() = title != null && dur > 0
}

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** The tablet or phone remote: pairing first, then the moon dial. */
@Composable
internal fun MawjRemoteScreen(onDismiss: () -> Unit) {
    var pairing by remember { mutableStateOf(LuxoRemote.target?.split('|')?.size?.let { it < 4 } ?: true) }
    Dialog(onDismissRequest = onDismiss, properties = luxoFullScreenDialogProperties()) {
        BoxWithConstraints(Modifier.fillMaxSize().background(RSea)) {
            val wide = maxWidth >= 900.dp
            val deviceName = if (maxWidth >= 600.dp) "التابلت" else "الجوال"
            RemoteBackdrop()
            if (pairing) RemotePairing(deviceName, onDismiss = onDismiss, onPaired = { pairing = false })
            else RemoteControls(wide, onDismiss = onDismiss, onChangeTv = { pairing = true })
        }
    }
}

@Composable
private fun RemoteBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1622), RSea), endY = size.height * 0.55f))
        drawCircle(Brush.radialGradient(listOf(Color(0x33F1DDA6), Color.Transparent), center = Offset(size.width * 0.86f, 0f), radius = size.minDimension * 0.45f), radius = size.minDimension * 0.45f, center = Offset(size.width * 0.86f, 0f))
        // A still sea along the bottom, drawn once.
        val layers = listOf(Color(0xFF122A3E) to 0.66f, Color(0xFF163450) to 0.73f, Color(0xFF1B3D5A) to 0.80f, Color(0xFF204564) to 0.87f, Color(0xFF0A1622) to 0.94f)
        layers.forEachIndexed { idx, (c, y) ->
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width + 8f) {
                val yy = size.height * y + sin(x * 0.009f + idx * 1.1f) * (11 - idx) * density + sin(x * 0.0037f + idx * 2.2f) * 7f * density
                path.lineTo(x, yy); x += 8f
            }
            path.lineTo(size.width, size.height); path.close()
            drawPath(path, Brush.verticalGradient(listOf(c, RSea), startY = size.height * y - 14f, endY = size.height), alpha = 0.9f)
        }
        val crest = androidx.compose.ui.graphics.Path()
        var x = 0f
        while (x <= size.width) {
            val yy = size.height * 0.66f + sin(x * 0.009f) * 11 * density + sin(x * 0.0037f) * 7f * density
            if (x == 0f) crest.moveTo(x, yy) else crest.lineTo(x, yy); x += 8f
        }
        drawPath(crest, RGold.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx()))
    }
}

// ---------------------------------------------------------------------------- pairing

@Composable
private fun RemotePairing(deviceName: String, onDismiss: () -> Unit, onPaired: () -> Unit) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<LuxoRemoteDevice>?>(null) }
    var picked by remember { mutableStateOf<LuxoRemoteDevice?>(null) }
    var manual by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(picked) { if (picked == null) { devices = null; devices = LuxoRemoteNet.discover(3500) } }

    fun pick(d: LuxoRemoteDevice) {
        picked = d; code = ""; error = null
        scope.launch { if (!mawjRemotePairRequest(d.host, d.port, deviceName)) error = "لم تُظهر الشاشة الرمز. يُتأكد أن «موج» مفتوحة عليها" }
    }
    fun connect() {
        val d = picked ?: return
        if (code.length < 4 || busy) return
        busy = true
        scope.launch {
            val ok = LuxoRemoteNet.send(d.host, d.port, code, buildJsonObject { put("t", "ping") }.toString())
            busy = false
            if (ok) { LuxoRemote.saveTarget(d.host, d.port, code, d.name); onPaired() } else { error = "الرمز غير صحيح"; code = "" }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ريموت موج", color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.weight(1f))
            RoundIcon("✕", onDismiss)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true, picked != null, false).forEach { on -> Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(if (on) RGold else Color(0x24EEF2F6))) }
        }
        val p = picked
        if (p == null) {
            Sonar()
            Text("البحث عن شاشات «موج»", color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("يلزم أن تكون الشاشة والجهاز على الشبكة نفسها، و«موج» مفتوحة على الشاشة", color = RMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            devices?.forEach { d ->
                Row(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RCard).border(1.dp, RGoldLine, RoundedCornerShape(18.dp))
                        .clickable { pick(d) }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ROk))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("${d.host} · يُضغط للاتصال", color = RFaint, fontSize = 12.sp)
                    }
                }
            }
            if (devices?.isEmpty() == true) {
                Text("لم يُعثر على شاشة. يمكن إدخال عنوانها:", color = RFaint, fontSize = 13.sp)
                Row(Modifier.widthIn(max = 420.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = manual, onValueChange = { manual = it.trim() }, singleLine = true, placeholder = { Text("مثل 192.168.1.20") }, modifier = Modifier.weight(1f))
                    GoldButton("متابعة") { if (manual.isNotBlank()) pick(LuxoRemoteDevice("موج · الشاشة", manual, LuxoRemoteNet.port)) }
                }
                Text("إعادة البحث", color = RGoldLight, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable { devices = null; scope.launch { devices = LuxoRemoteNet.discover(3500) } }.padding(8.dp))
            }
        } else {
            Text("الرمز ظاهر الآن على الشاشة", color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("يُدخل الرمز المكوّن من أربعة أرقام كما يظهر على الشاشة", color = RMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
            CodeBoxes(code) { v -> code = v; error = null; if (v.length == 4) connect() }
            error?.let { Text(it, color = Color(0xFFE0786A), fontSize = 13.sp, textAlign = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("إظهار الرمز مرة أخرى") { pick(p) }
                GhostButton("شاشة أخرى") { picked = null }
            }
        }
    }
}

@Composable
private fun Sonar() {
    val t = rememberInfiniteTransition(label = "mawj_sonar")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "mawj_sonar_t")
    Canvas(Modifier.size(140.dp)) {
        val maxR = size.minDimension / 2f
        repeat(3) { k ->
            val f = (phase + k / 3f) % 1f
            drawCircle(RGold.copy(alpha = (1f - f) * 0.8f), radius = maxR * (0.35f + f * 0.65f), style = Stroke(1.dp.toPx()))
        }
        drawCircle(Brush.radialGradient(listOf(RMoon, RGold, RGoldDeep), center = center, radius = maxR * 0.3f), radius = maxR * 0.3f)
    }
}

@Composable
private fun CodeBoxes(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Left to right, as on the TV.
                    (0 until 4).forEach { idx ->
                        val ch = value.getOrNull(idx)
                        val focus = idx == value.length
                        Box(
                            Modifier.width(58.dp).height(72.dp).clip(RoundedCornerShape(16.dp)).background(RAbyss)
                                .border(if (focus) 2.dp else 1.dp, if (focus) RGold else RGoldLine, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(ch?.toString() ?: "", color = RMoon, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp) }
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------- the remote

@Composable
private fun RemoteControls(wide: Boolean, onDismiss: () -> Unit, onChangeTv: () -> Unit) {
    val scope = rememberCoroutineScope()
    val t = LuxoRemote.target?.split('|').orEmpty()
    val host = t.getOrNull(0).orEmpty(); val port = t.getOrNull(1)?.toIntOrNull() ?: LuxoRemoteNet.port
    val code = t.getOrNull(2).orEmpty(); val name = t.getOrNull(3) ?: "موج · الشاشة"
    var now by remember { mutableStateOf(RemoteNow(null)) }
    var online by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf<String?>(null) }
    var touchMode by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    // A local position that keeps moving between answers from the TV.
    var shownPos by remember { mutableLongStateOf(0L) }

    suspend fun refresh() {
        val raw = luxoRemoteQuery(host, port, code)
        online = raw != null
        if (raw != null) {
            now = RemoteNow(runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject }.getOrNull())
            shownPos = now.pos
        }
    }
    LaunchedEffect(host) { while (true) { refresh(); delay(1500) } }
    LaunchedEffect(now) { while (now.playing) { delay(1000); shownPos = (shownPos + 1000).coerceAtMost(now.dur) } }
    LaunchedEffect(toast) { if (toast != null) { delay(1600); toast = null } }

    fun cmd(label: String? = null, refreshAfter: Boolean = false, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        scope.launch {
            val ok = LuxoRemote.send(buildJsonObject(build))
            if (!ok) toast = "تعذّر الوصول إلى الشاشة" else if (label != null) toast = label
            if (refreshAfter) { delay(350); refresh() }
        }
    }
    val key: (String) -> Unit = { k -> cmd { put("t", "key"); put("k", k) } }

    Column(Modifier.fillMaxSize()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (online) ROk else Color(0xFFE0786A)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(if (online) "متصل · $host" else "الشاشة غير متاحة · يُتأكد أن «موج» مفتوحة عليها", color = RFaint, fontSize = 12.sp)
            }
            Segmented(listOf("القرص", "لوحة لمس"), if (touchMode) 1 else 0) { touchMode = it == 1 }
            Spacer(Modifier.width(10.dp))
            GhostButton("تغيير الشاشة", onChangeTv)
            Spacer(Modifier.width(8.dp))
            RoundIcon("✕", onDismiss)
        }
        val nowPanel: @Composable (Modifier) -> Unit = { m -> NowPanel(m, now, shownPos, onSeek = { ms -> shownPos = ms; cmd(refreshAfter = true) { put("t", "seekTo"); put("ms", ms) } }, cmd = { l, b -> cmd(l, true, b) }) }
        val dialPanel: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (touchMode) TouchPad(key) else MoonDial(
                        key = key,
                        canSeek = now.playingSomething,
                        positionMs = shownPos,
                        onSeek = { target, delta -> shownPos = target; cmd((if (delta >= 0) "تقديم " else "ترجيع ") + fmt(abs(delta)), true) { put("t", "seekTo"); put("ms", target) } },
                        durationMs = now.dur,
                    )
                    TideVolume(now.vol) { op, v -> cmd(refreshAfter = true) { put("t", "vol"); put("op", op); put("v", v) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KeyTile(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") { key("back") }
                    KeyTile(Icons.Rounded.Home, "الرئيسية") { cmd { put("t", "nav"); put("to", "home") } }
                    KeyTile(Icons.Rounded.Search, "بحث") { typing = true }
                }
            }
        }
        val settingsPanel: @Composable (Modifier) -> Unit = { m -> SettingsPanel(m, now, scroll = wide) { l, b -> cmd(l, true, b) } }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                nowPanel(Modifier.weight(1f))
                dialPanel(Modifier)
                settingsPanel(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                dialPanel(Modifier.fillMaxWidth())
                nowPanel(Modifier.fillMaxWidth())
                settingsPanel(Modifier.fillMaxWidth())
            }
        }
    }
    toast?.let { msg ->
        Box(Modifier.fillMaxSize().padding(bottom = 26.dp), contentAlignment = Alignment.BottomCenter) {
            Row(Modifier.clip(CircleShape).background(Color(0xEB03070B)).border(1.dp, RGoldLine, CircleShape).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("على الشاشة: ", color = RFaint, fontSize = 13.sp); Text(msg, color = RGoldLight, fontSize = 13.sp)
            }
        }
    }
    if (typing) TypeDialog(onDismiss = { typing = false }) { q -> typing = false; cmd("بحث: «$q»") { put("t", "search"); put("q", q) } }
}

@Composable
private fun NowPanel(modifier: Modifier, now: RemoteNow, shownPos: Long, onSeek: (Long) -> Unit, cmd: (String?, kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!now.playingSomething) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xE00D1620)).border(1.dp, RLine, RoundedCornerShape(22.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("لا يُعرض شيء الآن", color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("يُستخدم القرص للتنقل في «موج» على الشاشة، وتظهر هنا أزرار التشغيل والإعدادات عند بدء أي عمل.", color = RMuted, fontSize = 14.sp)
            }
            return@Column
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF2C5B7C), Color(0xFF10202F), RSea))).border(1.dp, RLine, RoundedCornerShape(20.dp))) {
            now.poster?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB070D14)))))
            Text("يُعرض الآن", color = ROnGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp).clip(CircleShape).background(RGold).padding(horizontal = 10.dp, vertical = 2.dp))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                val ep = if (now.season != null && now.episode != null) "الموسم ${now.season} · الحلقة ${now.episode}" + (now.epTitle?.let { " · «$it»" } ?: "") else null
                ep?.let { Text(it, color = RMuted, fontSize = 12.sp) }
                Text(now.title.orEmpty(), color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 2)
            }
        }
        // timeline
        val frac = if (now.dur > 0) (shownPos.toFloat() / now.dur).coerceIn(0f, 1f) else 0f
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        Box(
            Modifier.fillMaxWidth().height(22.dp).pointerInput(now.dur, rtl) {
                detectTapGestures { p ->
                    // The bar fills from the start side; in RTL that is the right edge.
                    val f = (if (rtl) (size.width - p.x) else p.x) / size.width
                    onSeek((now.dur * f.coerceIn(0f, 1f)).toLong())
                }
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x1AEEF2F6)))
            Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(RGoldDeep, RGold))))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(shownPos), color = RFaint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("−" + fmt(now.dur - shownPos), color = RFaint, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("−٦٠") { cmd("ترجيع دقيقة") { put("t", "seek"); put("d", -60_000L) } }
            RoundIcon("−١٠") { cmd("ترجيع 10 ثوانٍ") { put("t", "seek"); put("d", -10_000L) } }
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(RGold).clickable { cmd(if (now.playing) "إيقاف مؤقت" else "تشغيل") { put("t", "pp") } },
                contentAlignment = Alignment.Center,
            ) { Text(if (now.playing) "❚❚" else "▶", color = ROnGold, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            RoundIcon("+١٠") { cmd("تقديم 10 ثوانٍ") { put("t", "seek"); put("d", 10_000L) } }
            RoundIcon("+٦٠") { cmd("تقديم دقيقة") { put("t", "seek"); put("d", 60_000L) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            if (now.hasSkip) GhostButton("تخطي المقدمة", gold = true) { cmd("تخطي المقدمة") { put("t", "skip") } }
            if (now.hasNext) GhostButton("الحلقة التالية ⏭", gold = true) { cmd("الحلقة التالية") { put("t", "next") } }
        }
    }
}

@Composable
private fun MoonDial(key: (String) -> Unit, canSeek: Boolean, positionMs: Long, durationMs: Long, onSeek: (Long, Long) -> Unit) {
    val ripple = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var knobAngle by remember { mutableFloatStateOf(0f) }
    var seekDelta by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    val press: (String) -> Unit = { k -> key(k); scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(750)) } }
    val posNow by androidx.compose.runtime.rememberUpdatedState(positionMs)
    val durNow by androidx.compose.runtime.rememberUpdatedState(durationMs)
    val seekNow by androidx.compose.runtime.rememberUpdatedState(onSeek)
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(canSeek) {
                if (!canSeek) return@pointerInput
                var last = 0f; var acc = 0f; var start = 0L
                detectDragGestures(
                    onDragStart = { p ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        if (hypot(p.x - c.x, p.y - c.y) < size.width * 0.38f) return@detectDragGestures
                        dragging = true; acc = 0f; start = posNow
                        last = atan2(p.x - c.x, -(p.y - c.y))
                    },
                    onDrag = { change, _ ->
                        if (!dragging) return@detectDragGestures
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val a = atan2(change.position.x - c.x, -(change.position.y - c.y))
                        var d = a - last
                        if (d > PI) d -= (2 * PI).toFloat(); if (d < -PI) d += (2 * PI).toFloat()
                        acc += d; last = a; knobAngle = a
                        // A full turn is two minutes.
                        seekDelta = (acc / (2 * PI) * 120_000).toLong()
                    },
                    onDragEnd = {
                        if (dragging && seekDelta != 0L) seekNow((start + seekDelta).coerceIn(0L, durNow), seekDelta)
                        dragging = false; seekDelta = 0L
                    },
                    onDragCancel = { dragging = false; seekDelta = 0L },
                )
            },
        ) {
            val r = size.minDimension / 2f
            drawCircle(RGold.copy(alpha = 0.18f), radius = r * 0.97f, style = Stroke(1.dp.toPx()))
            drawCircle(Brush.sweepGradient(listOf(RGoldDeep, RGoldLight, RGoldDeep, RGoldLight, RGoldDeep)), radius = r * 0.92f, style = Stroke(1.3.dp.toPx()), alpha = 0.8f)
            for (k in 0 until 120) {
                val a = k / 120f * 2 * PI.toFloat()
                val big = k % 10 == 0
                val r1 = r * (if (big) 0.83f else 0.87f); val r2 = r * 0.91f
                drawLine(if (big) RGold else RGold.copy(alpha = 0.38f), Offset(center.x + r1 * sin(a), center.y - r1 * cos(a)), Offset(center.x + r2 * sin(a), center.y - r2 * cos(a)), strokeWidth = if (big) 1.5.dp.toPx() else 0.7.dp.toPx())
            }
            // the disk
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1C3348), Color(0xFF0F1D2A), Color(0xFF08111A)), center = Offset(center.x, center.y - r * 0.25f), radius = r * 0.9f), radius = r * 0.74f)
            drawCircle(RGold.copy(alpha = 0.22f), radius = r * 0.74f, style = Stroke(1.dp.toPx()))
            if (canSeek) drawCircle(RGoldLight, radius = 5.dp.toPx(), center = Offset(center.x + r * 0.92f * sin(knobAngle), center.y - r * 0.92f * cos(knobAngle)))
            if (ripple.value in 0.001f..0.999f) drawCircle(RGoldLight.copy(alpha = 1f - ripple.value), radius = r * (0.2f + ripple.value * 0.8f), style = Stroke(1.5.dp.toPx()))
        }
        val arrow: @Composable (String, String, Alignment) -> Unit = { glyph, k, align ->
            Box(Modifier.fillMaxSize().padding(46.dp), contentAlignment = align) {
                Box(Modifier.size(64.dp).clip(CircleShape).clickable { press(k) }, contentAlignment = Alignment.Center) {
                    Text(glyph, color = RMuted, fontSize = 22.sp)
                }
            }
        }
        // Screen directions stay physical (left is left) whatever the text direction.
        arrow("▲", "up", Alignment.TopCenter)
        arrow("▼", "down", Alignment.BottomCenter)
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            arrow("◀", "left", Alignment.CenterStart)
            arrow("▶", "right", Alignment.CenterEnd)
        }
        Box(
            Modifier.size(118.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFFBEE), RGoldLight, RGold, RGoldDeep), center = Offset(110f, 95f)))
                .clickable { press("ok") },
            contentAlignment = Alignment.Center,
        ) { Text("موافق", color = ROnGold, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
        if (dragging) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text((if (seekDelta >= 0) "+" else "−") + fmt(abs(seekDelta)), color = RGoldLight, fontSize = 13.sp, modifier = Modifier.offset { IntOffset(0, -30) }.clip(CircleShape).background(RAbyss).border(1.dp, RGoldLine, CircleShape).padding(horizontal = 12.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun TouchPad(key: (String) -> Unit) {
    val step = with(LocalDensity.current) { 46.dp.toPx() }
    var trail by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier.size(320.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF13263A), Color(0xFF08111A)))).border(1.dp, RGoldLine, CircleShape)
            .pointerInput(Unit) {
                var sx = 0f; var sy = 0f
                detectDragGestures(
                    onDragStart = { p -> sx = p.x; sy = p.y; trail = p },
                    onDrag = { change, _ ->
                        val p = change.position; trail = p
                        val dx = p.x - sx; val dy = p.y - sy
                        if (abs(dx) > step || abs(dy) > step) {
                            key(if (abs(dx) > abs(dy)) (if (dx > 0) "right" else "left") else (if (dy > 0) "down" else "up"))
                            sx = p.x; sy = p.y
                        }
                    },
                    onDragEnd = { trail = null },
                    onDragCancel = { trail = null },
                )
            }
            .pointerInput(Unit) { detectTapGestures { key("ok") } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 22.dp.toPx()
            var y = gap / 2
            while (y < size.height) { var x = gap / 2; while (x < size.width) { drawCircle(RCrest.copy(alpha = 0.3f), 1.2f, Offset(x, y)); x += gap }; y += gap }
            trail?.let { drawCircle(Brush.radialGradient(listOf(RGoldLight.copy(alpha = 0.45f), Color.Transparent), center = it, radius = 30.dp.toPx()), radius = 30.dp.toPx(), center = it) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("لوحة لمس", color = RMuted, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("السحب للتنقل · النقر للاختيار", color = RFaint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TideVolume(volume: Int, send: (String, Int) -> Unit) {
    var local by remember(volume) { mutableIntStateOf(volume.coerceAtLeast(0)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundIcon("+") { send("up", 0) }
        Box(
            Modifier.width(36.dp).height(170.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE60D1620)).border(1.dp, RLine, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ -> local = ((1f - change.position.y / size.height) * 100).toInt().coerceIn(0, 100) },
                        onDragEnd = { send("set", local) },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(local / 100f).background(Brush.verticalGradient(listOf(RCrest, RTide))))
            Box(Modifier.fillMaxWidth().fillMaxHeight(local / 100f), contentAlignment = Alignment.TopCenter) { Box(Modifier.fillMaxWidth().height(2.dp).background(RGold)) }
        }
        RoundIcon("−") { send("down", 0) }
        Text(if (volume >= 0) "$local" else "—", color = RFaint, fontSize = 11.sp)
        Text("كتم", color = RMuted, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).clickable { send("mute", 0) }.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(modifier: Modifier, now: RemoteNow, scroll: Boolean, cmd: (String?, kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) -> Unit) {
    if (!now.playingSomething) { Box(modifier); return }
    var tab by remember { mutableIntStateOf(0) }
    Column(if (scroll) modifier.verticalScroll(rememberScrollState()) else modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Segmented(listOf("الصوت", "الترجمة", "شكلها", "الصورة", "السرعة"), tab, fill = true) { tab = it }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xE00D1620)).border(1.dp, RLine, RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    Label("مسار الصوت")
                    if (now.audio.isEmpty()) Text("لا توجد مسارات أخرى في هذا الملف", color = RFaint, fontSize = 13.sp)
                    now.audio.forEach { (i, l) -> TrackRow(l, i == now.audioSel) { cmd("الصوت: $l") { put("t", "audio"); put("i", i) } } }
                }
                1 -> {
                    // Add-on subtitles are fetched when this tab opens, as the TV's subtitle panel does.
                    LaunchedEffect(Unit) { if (now.addon.isEmpty()) cmd(null) { put("t", "fetchSubs") } }
                    TrackRow("بدون ترجمة", now.subSel == -1 && now.addonSel == null) { cmd("الترجمة: بدون") { put("t", "sub"); put("i", -1) } }
                    if (now.subs.isNotEmpty()) {
                        Label("مدمجة في الملف")
                        now.subs.forEach { (i, l) -> TrackRow(l, now.addonSel == null && i == now.subSel) { cmd("الترجمة: $l") { put("t", "sub"); put("i", i) } } }
                    }
                    Label("من الإضافات")
                    when {
                        now.addon.isNotEmpty() -> now.addon.forEach { (k, l) -> TrackRow(l, k == now.addonSel) { cmd("الترجمة: $l") { put("t", "addonSub"); put("k", k) } } }
                        now.addonLoading -> Text("جارٍ جلب الترجمات…", color = RFaint, fontSize = 13.sp)
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("لم تُعثر على ترجمات من الإضافات بعد", color = RFaint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            GhostButton("جلب الترجمات", gold = true) { cmd("جلب الترجمات") { put("t", "fetchSubs") } }
                        }
                    }
                    Label("التوقيت")
                    val delayText: (Int) -> String = { v -> (if (v > 0) "+" else "") + ((v / 100) / 10f).toString() + " ث" }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-1000, -100).forEach { d ->
                            RoundIcon(if (d == -1000) "−1" else "−0.1") { val v = now.subDelay + d; cmd("توقيت الترجمة ${delayText(v)}") { put("t", "subDelay"); put("ms", v) } }
                        }
                        Text(delayText(now.subDelay), color = RGoldLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        listOf(100, 1000).forEach { d ->
                            RoundIcon(if (d == 1000) "+1" else "+0.1") { val v = now.subDelay + d; cmd("توقيت الترجمة ${delayText(v)}") { put("t", "subDelay"); put("ms", v) } }
                        }
                    }
                    Text("الرقم السالب يُظهر الترجمة أبكر، والموجب يؤخرها", color = RFaint, fontSize = 12.sp)
                    if (now.subDelay != 0) GhostButton("إعادة التوقيت إلى صفر") { cmd("توقيت الترجمة: صفر") { put("t", "subDelay"); put("ms", 0) } }
                }
                2 -> {
                    Label("الخط")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("الافتراضي", now.font == null) { cmd("خط الترجمة: الافتراضي") { put("t", "subFont"); put("f", "") } }
                        now.fonts.forEach { (f, l) -> Chip(l, f == now.font) { cmd("خط الترجمة: $l") { put("t", "subFont"); put("f", f) } } }
                    }
                    Label("الحجم")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("صغير" to 15, "متوسط" to 18, "كبير" to 24, "كبير جداً" to 30).forEach { (l, sp) ->
                            Chip(l, abs(now.subSize - sp) <= 1) { cmd("حجم الترجمة: $l") { put("t", "subSize"); put("sp", sp) } }
                        }
                    }
                    Label("اللون")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0xFFFFFFFF.toInt() to "أبيض", 0xFFFFE066.toInt() to "أصفر", 0xFFF1DDA6.toInt() to "ذهبي", 0xFF9FE3FF.toInt() to "سماوي").forEach { (c, l) ->
                            val on = now.color == c
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) RGold else RLine, CircleShape)
                                    .clickable { cmd("لون الترجمة: $l") { put("t", "subColor"); put("c", c) } },
                            )
                        }
                    }
                    Label("المظهر")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("خلفية داكنة", now.subBg) { cmd(if (now.subBg) "إزالة خلفية الترجمة" else "خلفية داكنة للترجمة") { put("t", "subBg"); put("on", !now.subBg) } }
                        Chip("خط عريض", now.subBold) { cmd(if (now.subBold) "خط عادي" else "خط عريض") { put("t", "subBold"); put("on", !now.subBold) } }
                    }
                    Label("الارتفاع عن أسفل الشاشة")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon("▼") { val v = (now.subPos - 10).coerceAtLeast(0); cmd("خفض الترجمة") { put("t", "subPos"); put("v", v) } }
                        Text("${now.subPos}", color = RGoldLight, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        RoundIcon("▲") { val v = (now.subPos + 10).coerceAtMost(200); cmd("رفع الترجمة") { put("t", "subPos"); put("v", v) } }
                    }
                }
                3 -> {
                    Label("أبعاد الصورة")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ملاءمة" to "Fit", "تعبئة" to "Fill", "تكبير" to "Zoom").forEach { (l, m) ->
                            Chip(l, now.resize == m) { cmd("أبعاد الصورة: $l") { put("t", "resize"); put("m", m) } }
                        }
                    }
                }
                else -> {
                    Label("سرعة التشغيل")
                    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                    val idx = speeds.indexOfFirst { abs(it - now.speed) < 0.01f }.coerceAtLeast(0)
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.minDimension.coerceAtMost(size.width / 2f) * 0.85f
                            val c = Offset(size.width / 2f, size.height * 0.92f)
                            drawArc(Color(0x1AEEF2F6), 180f, 180f, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                            val sweep = 180f * idx / (speeds.size - 1)
                            drawArc(RGold, 180f, sweep, false, topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                            val a = ((180f + sweep) / 180f * PI).toFloat()
                            drawCircle(RGoldLight, 9.dp.toPx(), Offset(c.x + r * cos(a), c.y + r * sin(a)))
                        }
                        Text(speedLabel(now.speed), color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, modifier = Modifier.padding(top = 30.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                        speeds.forEach { v -> Chip(speedLabel(v), abs(v - now.speed) < 0.01f) { cmd("السرعة ${speedLabel(v)}") { put("t", "speed"); put("v", v) } } }
                    }
                }
            }
        }
    }
}

private fun speedLabel(v: Float): String {
    val s = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
    return "$s×"
}

@Composable
private fun TypeDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(RRaised).border(1.dp, RGoldLine, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("البحث على الشاشة", color = RInkStrong, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("اسم الفيلم أو المسلسل") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton("بحث") { if (q.isNotBlank()) onSend(q.trim()) }
                GhostButton("إلغاء", onDismiss)
            }
        }
    }
}

// ---------------------------------------------------------------------------- small pieces

@Composable
private fun Label(text: String) = Text(text, color = RFaint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected) Color(0x14D6B36A) else RCard)
            .border(1.dp, if (selected) RGoldLine else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).border(1.5.dp, if (selected) RGold else Color(0x4DEEF2F6), CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(RGold))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = RInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, style = TextStyle(textDirection = TextDirection.Content))
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(CircleShape).background(if (selected) RGold else Color.Transparent)
            .border(1.dp, if (selected) RGold else Color(0x2EEEF2F6), CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) ROnGold else RMuted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun Segmented(items: List<String>, selected: Int, fill: Boolean = false, onSelect: (Int) -> Unit) {
    Row(
        (if (fill) Modifier.fillMaxWidth() else Modifier).clip(RoundedCornerShape(18.dp)).background(Color(0xD90D1620)).border(1.dp, RLine, RoundedCornerShape(18.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(14.dp)).background(if (on) RGold else Color.Transparent).clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (on) ROnGold else RMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun RoundIcon(text: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xD9121C26)).border(1.dp, RLine, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = RInk, fontSize = 14.sp)
    }
}

@Composable
private fun KeyTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xD90D1620)).border(1.dp, RLine, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = RInk, modifier = Modifier.size(22.dp))
        Text(label, color = RMuted, fontSize = 12.sp)
    }
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(RGold).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, color = ROnGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) = GhostButton(text, gold = false, onClick = onClick)

@Composable
private fun GhostButton(text: String, gold: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (gold) Color(0x12D6B36A) else Color.Transparent).border(1.dp, if (gold) RGoldLine else RLine, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
    ) { Text(text, color = if (gold) RGoldLight else RMuted, fontSize = 13.sp) }
}

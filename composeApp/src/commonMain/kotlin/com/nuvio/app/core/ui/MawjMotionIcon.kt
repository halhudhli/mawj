package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import nuvio.composeapp.generated.resources.Res

/*
 * Gentleface animated icons (https://gentleface.com/animated-icons, MIT licence, © andaja).
 * Each icon is a 16×16 SVG whose motion is native SVG animation (SMIL). Compose cannot play SMIL,
 * so this file reads the SVG once and draws it at any moment of its timeline: <animate>,
 * <animateTransform>, <animateMotion>, clip paths and masks — everything the 1,000 icons use.
 * The rest pose is time 0; "once" plays the icon's [start, end] window and returns to rest.
 */

/** The icons shipped with Mawj (files/motion/<name>.svg): play window start, end, and full length (s). */
object MawjMotion {
    val frames: Map<String, FloatArray> = mapOf(
        "home-write" to floatArrayOf(0.25f, 2.25f, 2.9f),
        "clapperboard-clap" to floatArrayOf(0.3f, 1.6f, 2.4f),
        "tv-channel" to floatArrayOf(0.25f, 3.2f, 3.6f),
        "sparkles-spark" to floatArrayOf(0.3f, 1.95f, 2.8f),
        "bookmarks-cycle" to floatArrayOf(0.2f, 2.1f, 3f),
        "search-scan" to floatArrayOf(0.25f, 2.6f, 3f),
        "book-open-turn" to floatArrayOf(0.25f, 2.3f, 3.2f),
        "radio-tower-broadcast" to floatArrayOf(0.25f, 2.3f, 3.2f),
        "film-advance" to floatArrayOf(0.25f, 1.6f, 2.6f),
        "clock-spin" to floatArrayOf(0.25f, 3.35f, 3.6f),
        "calendar-flip" to floatArrayOf(0.25f, 2.2f, 3f),
        "popcorn-pop" to floatArrayOf(0.2f, 2.2f, 2.8f),
        "monitor-share-flick" to floatArrayOf(0.25f, 1.65f, 2.6f),
        "podium-rise" to floatArrayOf(0.3f, 2.4f, 3.2f),
        "replay-rewind" to floatArrayOf(0.25f, 2.4f, 3f),
        "monitor-play-video" to floatArrayOf(0.25f, 3f, 3.2f),
        "dice-tumble" to floatArrayOf(0.2f, 2.85f, 3.2f),
        "ship-wheel-spin" to floatArrayOf(0.25f, 2.7f, 3.2f),
        "remote-control-fire" to floatArrayOf(0.25f, 2.75f, 3.2f),
        "baby-rock" to floatArrayOf(0.25f, 2f, 2.6f),
        "eye-hide" to floatArrayOf(0.25f, 1.96f, 4.2f),
        "user-switch-swap" to floatArrayOf(0.25f, 1.55f, 2.6f),
        "champagne-glasses-clink" to floatArrayOf(0.2f, 2.3f, 3f),
        "brush-paint" to floatArrayOf(0.25f, 2.7f, 3.2f),
        "puzzle-plug" to floatArrayOf(0.2f, 2.75f, 3f),
        "play-circle-start" to floatArrayOf(0.3f, 2f, 2.8f),
        "link-connect" to floatArrayOf(0.25f, 1.8f, 2.6f),
        "bell-ring" to floatArrayOf(0.2f, 1.5f, 2.6f),
        "sync-chase" to floatArrayOf(0.25f, 2f, 3f),
        "sliders-move" to floatArrayOf(0.3f, 2.4f, 3f),
        "download-absorb" to floatArrayOf(0.2f, 2f, 2.8f),
        "info-drop" to floatArrayOf(0.2f, 2.2f, 3f),
    )

    private val cache = mutableMapOf<String, MotionSvg>()

    /** Settings halls and Mawj tools by their title (Arabic or English). */
    fun forHall(title: String): String? = hallMotions.entries.firstOrNull { (k, _) -> k.any { title.startsWith(it) } }?.value

    private val hallMotions: Map<List<String>, String> = mapOf(
        listOf("ذوقي", "My taste") to "sparkles-spark",
        listOf("السلاسل", "Franchises") to "film-advance",
        listOf("كم عندي وقت", "Time budget") to "clock-spin",
        listOf("تقويم الأنمي", "Anime season") to "calendar-flip",
        listOf("ماذا نشاهد معاً", "Watch together") to "popcorn-pop",
        listOf("الشاشة الثانية", "Second screen") to "monitor-share-flick",
        listOf("ترتيبي الخاص", "My tiers") to "podium-rise",
        listOf("ما بدأته ولم يكتمل", "Unfinished") to "replay-rewind",
        listOf("يوتيوب", "YouTube") to "monitor-play-video",
        listOf("خمّن العمل", "Guess the title") to "dice-tumble",
        listOf("عجلة الاختيار", "Choice wheel") to "ship-wheel-spin",
        listOf("التحكم بالشاشة", "Remote control") to "remote-control-fire",
        listOf("ملف الأطفال", "Kids profile") to "baby-rock",
        listOf("الوضع الخاص", "Private mode") to "eye-hide",
        listOf("الحساب", "Account") to "user-switch-swap",
        listOf("سهرة جماعية", "Watch party") to "champagne-glasses-clink",
        listOf("المظهر", "Appearance") to "brush-paint",
        listOf("المحتوى", "Content") to "puzzle-plug",
        listOf("التشغيل", "Playback") to "play-circle-start",
        listOf("التكاملات", "Integrations") to "link-connect",
        listOf("الإشعارات", "Notifications") to "bell-ring",
        listOf("التتبّع", "التتبع", "Tracking") to "sync-chase",
        listOf("متقدم", "Advanced") to "sliders-move",
        listOf("التحديثات", "Updates") to "download-absorb",
        listOf("حول", "About") to "info-drop",
    )

    fun cached(name: String): MotionSvg? = cache[name]

    suspend fun load(name: String): MotionSvg? {
        cache[name]?.let { return it }
        val text = runCatching { Res.readBytes("files/motion/$name.svg").decodeToString() }.getOrNull() ?: return null
        val svg = runCatching { MotionSvg(parseSvg(text)) }.getOrNull() ?: return null
        cache[name] = svg
        return svg
    }
}

/**
 * An animated Gentleface icon. It rests still; it plays once each time [playKey] changes (not on
 * first show, unless [playOnAppear]), or runs continuously while [loop] is true. With the
 * «أيقونات متحركة» switch off it stays on its rest pose. Until the file loads, [fallback] is shown.
 */
@Composable
fun MawjMotionIcon(
    name: String,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    playKey: Any? = null,
    playOnAppear: Boolean = false,
    loop: Boolean = false,
    fallback: ImageVector? = null,
    /** When false a [playKey] change does not play (e.g. a tab losing its selection). */
    active: Boolean = true,
    /** Wait before playing, so a group of icons moves one after another rather than all at once. */
    startDelayMs: Long = 0,
) {
    val svg by produceState(MawjMotion.cached(name), name) { value = MawjMotion.load(name) }
    val moving = com.nuvio.app.features.luxo.rememberLuxoFeatures().mawjAnimatedIcons
    val time = remember(name) { Animatable(0f) }
    val shown = remember(name) { BooleanArray(1) }
    LaunchedEffect(svg, playKey, loop, moving) {
        val s = svg ?: return@LaunchedEffect
        val f = MawjMotion.frames[name] ?: floatArrayOf(0f, s.duration, s.duration)
        val first = !shown[0]
        shown[0] = true
        if (!moving) { time.snapTo(0f); return@LaunchedEffect }
        if (loop) {
            while (true) {
                time.snapTo(0f)
                time.animateTo(f[2], tween((f[2] * 1000).toInt(), easing = LinearEasing))
            }
        }
        if ((first && !playOnAppear) || !active) { time.snapTo(0f); return@LaunchedEffect }
        if (startDelayMs > 0) kotlinx.coroutines.delay(startDelayMs)
        time.snapTo(f[0])
        time.animateTo(f[1], tween(((f[1] - f[0]) * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
        time.snapTo(0f)
    }
    val s = svg
    if (s == null) {
        if (fallback != null) Icon(fallback, contentDescription, modifier, tint = tint) else Box(modifier)
        return
    }
    Canvas(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val k = size.minDimension / 16f
        withTransform({
            translate((size.width - 16f * k) / 2f, (size.height - 16f * k) / 2f)
            scale(k, k, pivot = Offset.Zero)
        }) {
            s.draw(this, time.value, tint)
        }
    }
}

// ---------------------------------------------------------------- SVG tree

class SvgNode(val name: String, val attrs: Map<String, String>) {
    val children = mutableListOf<SvgNode>()
    internal val anims: List<SvgAnim> by lazy {
        children.filter { it.name.startsWith("animate") }.map { SvgAnim(it) }
    }
    internal val shapes: List<SvgNode> by lazy { children.filter { !it.name.startsWith("animate") } }
}

private val attrRegex = Regex("([a-zA-Z:-]+)=\"([^\"]*)\"")

internal fun parseSvg(text: String): SvgNode {
    val root = SvgNode("#root", emptyMap())
    val stack = ArrayDeque<SvgNode>().apply { addLast(root) }
    var i = 0
    while (true) {
        val lt = text.indexOf('<', i)
        if (lt < 0) break
        val gt = text.indexOf('>', lt)
        if (gt < 0) break
        val body = text.substring(lt + 1, gt)
        i = gt + 1
        if (body.startsWith("/")) { if (stack.size > 1) stack.removeLast(); continue }
        if (body.startsWith("!") || body.startsWith("?")) continue
        val selfClose = body.endsWith("/")
        val b = if (selfClose) body.dropLast(1) else body
        val name = b.trim().takeWhile { !it.isWhitespace() }
        val attrs = attrRegex.findAll(b).associate { it.groupValues[1] to it.groupValues[2] }
        val node = SvgNode(name, attrs)
        stack.last().children += node
        if (!selfClose) stack.addLast(node)
    }
    return root.children.first { it.name == "svg" }
}

// ---------------------------------------------------------------- numbers

private val numRegex = Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")

/** A value string split into its numbers and the text around them, so two can be blended. */
internal class NumTemplate(val raw: String) {
    val parts: List<String>
    val nums: FloatArray
    init {
        val p = mutableListOf<String>()
        val n = mutableListOf<Float>()
        var last = 0
        numRegex.findAll(raw).forEach { m ->
            p += raw.substring(last, m.range.first)
            n += m.value.toFloatOrNull() ?: 0f
            last = m.range.last + 1
        }
        p += raw.substring(last)
        parts = p
        nums = n.toFloatArray()
    }

    fun blend(other: NumTemplate, f: Float): String {
        if (other.nums.size != nums.size) return if (f < 1f) raw else other.raw
        val sb = StringBuilder()
        for (i in nums.indices) {
            sb.append(parts[i])
            // A space keeps "1 -2" from merging; no scientific notation for tiny values.
            if (i > 0 && sb.isNotEmpty() && sb.last().isDigit()) sb.append(' ')
            sb.append(fmt(nums[i] + (other.nums[i] - nums[i]) * f))
        }
        sb.append(parts[nums.size])
        return sb.toString()
    }
}

private fun fmt(v: Float): String {
    if (abs(v) < 0.0005f) return "0"
    val scaled = kotlin.math.round(v * 1000f).toLong()
    val neg = scaled < 0
    val a = abs(scaled)
    val whole = a / 1000
    val frac = (a % 1000).toString().padStart(3, '0').trimEnd('0')
    return (if (neg) "-" else "") + whole + (if (frac.isEmpty()) "" else ".$frac")
}

private fun nums(s: String?): FloatArray =
    if (s == null) FloatArray(0) else numRegex.findAll(s).map { it.value.toFloatOrNull() ?: 0f }.toList().toFloatArray()

private fun seconds(s: String?): Float {
    if (s == null) return 1f
    return when {
        s.endsWith("ms") -> (s.dropLast(2).toFloatOrNull() ?: 1000f) / 1000f
        s.endsWith("s") -> s.dropLast(1).toFloatOrNull() ?: 1f
        else -> s.toFloatOrNull() ?: 1f
    }
}

// ---------------------------------------------------------------- animation

internal class SvgAnim(node: SvgNode) {
    val kind = node.name
    val attribute = node.attrs["attributeName"] ?: if (kind == "animateMotion") "motion" else ""
    val type = node.attrs["type"] ?: "translate"
    val dur = seconds(node.attrs["dur"]).coerceAtLeast(0.001f)
    val discrete = node.attrs["calcMode"] == "discrete" || attribute == "visibility"
    val values: List<NumTemplate> = (node.attrs["values"] ?: listOfNotNull(node.attrs["from"], node.attrs["to"]).joinToString(";"))
        .split(';').map { NumTemplate(it.trim()) }
    val keyPoints: FloatArray? = node.attrs["keyPoints"]?.let { s -> s.split(';').map { it.trim().toFloatOrNull() ?: 0f }.toFloatArray() }
    private val count = keyPoints?.size ?: values.size
    val keyTimes: FloatArray = node.attrs["keyTimes"]?.split(';')?.map { it.trim().toFloatOrNull() ?: 0f }?.toFloatArray()
        ?: FloatArray(count) { if (count <= 1) 0f else it.toFloat() / (count - 1) }
    private val splines = if (node.attrs["calcMode"] == "spline") node.attrs["keySplines"]?.split(';')?.map {
        val v = nums(it)
        if (v.size == 4) CubicBezierEasing(v[0], v[1], v[2], v[3]) else LinearEasing
    } else null
    val motionPath: Path? = node.attrs["path"]?.let { runCatching { addPathNodes(it).toPath() }.getOrNull() }
    val rotateAuto = node.attrs["rotate"] == "auto"

    /** Segment index and eased progress inside it at time t (seconds). */
    fun at(t: Float): Pair<Int, Float> {
        val p = ((t % dur) + dur) % dur / dur
        val n = keyTimes.size
        if (n <= 1) return 0 to 0f
        var seg = 0
        while (seg < n - 2 && p >= keyTimes[seg + 1]) seg++
        if (discrete) {
            var d = 0
            while (d < n - 1 && p >= keyTimes[d + 1]) d++
            return d to 0f
        }
        val span = keyTimes[seg + 1] - keyTimes[seg]
        val raw = if (span <= 0f) 1f else ((p - keyTimes[seg]) / span).coerceIn(0f, 1f)
        val eased = splines?.getOrNull(seg)?.transform(raw) ?: raw
        return seg to eased
    }

    fun stringAt(t: Float): String {
        val (seg, f) = at(t)
        if (discrete || seg + 1 >= values.size) return values[seg.coerceAtMost(values.size - 1)].raw
        return values[seg].blend(values[seg + 1], f)
    }

    fun numbersAt(t: Float): FloatArray {
        val (seg, f) = at(t)
        val a = values[seg.coerceAtMost(values.size - 1)].nums
        if (discrete || seg + 1 >= values.size) return a
        val b = values[seg + 1].nums
        if (a.size != b.size) return if (f < 1f) a else b
        return FloatArray(a.size) { a[it] + (b[it] - a[it]) * f }
    }

    fun keyPointAt(t: Float): Float {
        val kp = keyPoints ?: return 0f
        val (seg, f) = at(t)
        if (discrete || seg + 1 >= kp.size) return kp[seg.coerceAtMost(kp.size - 1)]
        return kp[seg] + (kp[seg + 1] - kp[seg]) * f
    }
}

// ---------------------------------------------------------------- drawing

private data class Style(
    val fill: String,
    val stroke: String,
    val strokeWidth: Float,
    val visible: Boolean,
    val mask: Boolean,
)

class MotionSvg internal constructor(private val root: SvgNode) {
    private val ids = mutableMapOf<String, SvgNode>()
    val duration: Float

    init {
        fun walk(n: SvgNode) { n.attrs["id"]?.let { ids[it] = n }; n.children.forEach(::walk) }
        walk(root)
        var d = 0f
        fun durs(n: SvgNode) { n.anims.forEach { d = maxOf(d, it.dur) }; n.shapes.forEach(::durs) }
        durs(root)
        duration = if (d > 0f) d else 1f
    }

    private val pathCache = mutableMapOf<String, Path>()

    private fun pathOf(d: String): Path? {
        pathCache[d]?.let { return it }
        val p = runCatching { addPathNodes(d).toPath() }.getOrNull() ?: return null
        if (pathCache.size < 64) pathCache[d] = p
        return p
    }

    private fun attr(n: SvgNode, key: String, t: Float): String? =
        n.anims.lastOrNull { it.kind == "animate" && it.attribute == key }?.stringAt(t) ?: n.attrs[key]

    private fun num(n: SvgNode, key: String, t: Float, def: Float = 0f): Float =
        attr(n, key, t)?.let { nums(it).firstOrNull() } ?: def

    fun draw(scope: DrawScope, t: Float, tint: Color) {
        val base = Style(
            fill = root.attrs["fill"] ?: "black",
            stroke = root.attrs["stroke"] ?: "none",
            strokeWidth = root.attrs["stroke-width"]?.toFloatOrNull() ?: 1f,
            visible = true,
            mask = false,
        )
        root.shapes.forEach { drawNode(scope, it, t, tint, base) }
    }

    private fun styleOf(n: SvgNode, t: Float, parent: Style): Style {
        val vis = attr(n, "visibility", t)
        return parent.copy(
            fill = n.attrs["fill"] ?: parent.fill,
            stroke = n.attrs["stroke"] ?: parent.stroke,
            strokeWidth = n.attrs["stroke-width"]?.toFloatOrNull() ?: parent.strokeWidth,
            visible = when (vis) { "hidden", "collapse" -> false; "visible" -> true; else -> parent.visible },
        )
    }

    /** The node's own transform at time t: an animated transform replaces the static one. */
    private fun DrawScope.applyTransform(n: SvgNode, t: Float, block: DrawScope.() -> Unit) {
        val motion = n.anims.lastOrNull { it.kind == "animateMotion" }
        val animated = n.anims.lastOrNull { it.kind == "animateTransform" }
        val ops = mutableListOf<Pair<String, FloatArray>>()
        if (motion?.motionPath != null) {
            val m = PathMeasure().apply { setPath(motion.motionPath, false) }
            val dist = motion.keyPointAt(t).coerceIn(0f, 1f) * m.length
            val pos = m.getPosition(dist)
            ops += "translate" to floatArrayOf(pos.x, pos.y)
            if (motion.rotateAuto) {
                val tan = m.getTangent(dist)
                ops += "rotate" to floatArrayOf((atan2(tan.y, tan.x) * 180f / PI.toFloat()))
            }
        }
        if (animated != null) {
            ops += animated.type to animated.numbersAt(t)
        } else {
            n.attrs["transform"]?.let { tr ->
                Regex("(translate|rotate|scale)\\(([^)]*)\\)").findAll(tr).forEach { ops += it.groupValues[1] to nums(it.groupValues[2]) }
            }
        }
        applyOps(ops, 0, block)
    }

    private fun DrawScope.applyOps(ops: List<Pair<String, FloatArray>>, i: Int, block: DrawScope.() -> Unit) {
        if (i >= ops.size) { block(); return }
        val (kind, v) = ops[i]
        withTransform({
            when (kind) {
                "translate" -> translate(v.getOrElse(0) { 0f }, v.getOrElse(1) { 0f })
                "rotate" -> rotate(v.getOrElse(0) { 0f }, Offset(v.getOrElse(1) { 0f }, v.getOrElse(2) { 0f }))
                "scale" -> { val sx = v.getOrElse(0) { 1f }; scale(sx, v.getOrElse(1) { sx }, Offset.Zero) }
            }
        }) { applyOps(ops, i + 1, block) }
    }

    private fun refId(v: String?): String? = v?.substringAfter("url(#", "")?.substringBefore(")")?.takeIf { it.isNotEmpty() }

    private fun drawNode(scope: DrawScope, n: SvgNode, t: Float, tint: Color, parent: Style) {
        when (n.name) {
            "defs", "clipPath", "mask", "title", "desc" -> return
        }
        val style = styleOf(n, t, parent)
        scope.applyTransform(n, t) {
            val clip = refId(n.attrs["clip-path"])?.let { ids[it] }?.let { clipShape(it, t) }
            val mask = refId(n.attrs["mask"])?.let { ids[it] }
            val body: DrawScope.() -> Unit = {
                if (mask != null) {
                    val canvas = drawContext.canvas
                    canvas.saveLayer(Rect(-4f, -4f, 20f, 20f), Paint())
                    drawSelf(this, n, t, tint, style)
                    mask.shapes.forEach { drawNode(this, it, t, tint, style.copy(fill = "#fff", stroke = "none", visible = true, mask = true)) }
                    canvas.restore()
                } else {
                    drawSelf(this, n, t, tint, style)
                }
            }
            if (clip != null) clipPath(clip) { body() } else body()
        }
    }

    private fun drawSelf(scope: DrawScope, n: SvgNode, t: Float, tint: Color, style: Style) {
        if (n.name == "g" || n.name == "svg") {
            n.shapes.forEach { drawNode(scope, it, t, tint, style) }
            return
        }
        if (!style.visible) return
        val path = shapePath(n, t) ?: return
        if (n.attrs["fill-rule"] == "evenodd") path.fillType = PathFillType.EvenOdd
        // In a mask, white shows and black hides: hidden parts are cut out of what is already drawn.
        if (style.mask) {
            if (style.fill == "#000") scope.drawPath(path, Color.Black, style = Fill, blendMode = BlendMode.DstOut)
            if (style.stroke == "#000") strokeOf(n, t, style, path)?.let { scope.drawPath(path, Color.Black, style = it, blendMode = BlendMode.DstOut) }
            return
        }
        if (style.fill != "none") scope.drawPath(path, tint, style = Fill)
        if (style.stroke != "none" && style.strokeWidth > 0f) {
            val st = strokeOf(n, t, style, path) ?: return
            scope.drawPath(path, tint, style = st)
        }
    }

    private fun strokeOf(n: SvgNode, t: Float, style: Style, path: Path): Stroke? {
        val dashRaw = attr(n, "stroke-dasharray", t)
        var effect: PathEffect? = null
        if (dashRaw != null && dashRaw != "none") {
            var dash = nums(dashRaw)
            if (dash.size % 2 == 1) dash = dash + dash
            var offset = num(n, "stroke-dashoffset", t)
            val pathLength = n.attrs["pathLength"]?.toFloatOrNull()
            if (pathLength != null && pathLength > 0f) {
                val k = PathMeasure().apply { setPath(path, false) }.length / pathLength
                dash = FloatArray(dash.size) { dash[it] * k }
                offset *= k
            }
            // A pattern with no visible dash draws nothing (avoids round-cap dots).
            if (dash.isEmpty() || (0 until dash.size step 2).all { dash[it] <= 0.0005f }) return null
            effect = PathEffect.dashPathEffect(FloatArray(dash.size) { dash[it].coerceAtLeast(0.0005f) }, offset)
        }
        return Stroke(width = style.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
    }

    private fun shapePath(n: SvgNode, t: Float): Path? = when (n.name) {
        "path" -> attr(n, "d", t)?.let { pathOf(it) }?.let { Path().apply { addPath(it) } }
        "circle" -> {
            val r = num(n, "r", t)
            val cx = num(n, "cx", t); val cy = num(n, "cy", t)
            if (r <= 0f) null else Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
        }
        "ellipse" -> {
            val rx = num(n, "rx", t); val ry = num(n, "ry", t)
            val cx = num(n, "cx", t); val cy = num(n, "cy", t)
            if (rx <= 0f || ry <= 0f) null else Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
        }
        "rect" -> {
            val x = num(n, "x", t); val y = num(n, "y", t)
            val w = num(n, "width", t); val h = num(n, "height", t)
            var rx = attr(n, "rx", t)?.let { nums(it).firstOrNull() }
            var ry = attr(n, "ry", t)?.let { nums(it).firstOrNull() }
            if (rx == null) rx = ry
            if (ry == null) ry = rx
            if (w <= 0f || h <= 0f) null else Path().apply {
                addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(rx ?: 0f, ry ?: 0f)))
            }
        }
        else -> null
    }

    /** A clipPath's shapes merged into one path (their own transforms included). */
    private fun clipShape(clip: SvgNode, t: Float): Path {
        val out = Path()
        fun add(n: SvgNode) {
            if (n.name == "g") { n.shapes.forEach(::add); return }
            val p = shapePath(n, t) ?: return
            val ops = n.attrs["transform"]?.let { tr ->
                Regex("translate\\(([^)]*)\\)").find(tr)?.let { nums(it.groupValues[1]) }
            }
            if (ops != null && ops.isNotEmpty()) p.translate(Offset(ops[0], ops.getOrElse(1) { 0f }))
            out.addPath(p)
        }
        clip.shapes.forEach(::add)
        return out
    }
}

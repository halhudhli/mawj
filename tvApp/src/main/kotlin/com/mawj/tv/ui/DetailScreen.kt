@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.mawj.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Discover
import com.mawj.tv.data.Episode
import com.mawj.tv.data.EpisodeScore
import com.mawj.tv.data.Gemini
import com.mawj.tv.data.Library
import com.mawj.tv.data.Meta
import com.mawj.tv.data.MetaPreview
import com.mawj.tv.data.PlayRequest
import com.mawj.tv.data.Provider
import com.mawj.tv.data.Store
import com.mawj.tv.data.TitleFacts
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * «متابعة»: plays the same source chosen before. The link is looked up again (links from
 * debrid services expire); if it is gone, the saved link is tried as it is.
 */
suspend fun resumeSameSource(meta: MetaPreview, episode: Episode?): Boolean {
    val saved = Store.rememberedSource(meta.id) ?: return false
    val videoId = episode?.id ?: meta.id
    val fresh = Addons.streams(meta.type, videoId)
    val match = fresh.firstOrNull { it.addonName == saved.addonName && it.name == saved.name && it.title == saved.title }
        ?: saved.bingeGroup?.let { bg -> fresh.firstOrNull { it.bingeGroup == bg } }
        ?: fresh.firstOrNull { it.addonName == saved.addonName && it.quality == saved.quality }
    val stream = match ?: if (episode == null || Store.progressFor(meta.id)?.videoId == videoId) saved else return false
    Nav.go(Route.Player(PlayRequest(meta, videoId, episode, stream, Store.positionFor(videoId))))
    return true
}

private enum class DPanel { None, Plan, Map, Where, Parents, More, Collection }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(preview: MetaPreview) {
    LaunchedEffect(preview.id) { Backdrop.show(preview.background ?: preview.poster) }
    var meta by remember(preview.id) { mutableStateOf<Meta?>(null) }
    var credits by remember(preview.id) { mutableStateOf<Pair<List<com.mawj.tv.data.CastMember>, List<com.mawj.tv.data.Company>>?>(null) }
    var facts by remember(preview.id) { mutableStateOf<TitleFacts?>(null) }
    var scores by remember(preview.id) { mutableStateOf<Map<Int, List<EpisodeScore>>>(emptyMap()) }
    LaunchedEffect(preview.id) {
        meta = Addons.meta(preview.type, preview.id)
        meta?.preview?.background?.let(Backdrop::show)
    }
    LaunchedEffect(preview.id) {
        kotlinx.coroutines.delay(350)
        credits = com.mawj.tv.data.Tmdb.credits(preview.id, preview.type)
        facts = Discover.facts(preview)
    }
    val m = meta?.preview ?: preview
    val isSeries = preview.type == "series" || (meta?.videos?.size ?: 0) > 1
    val resume = Store.progressFor(preview.id)
    val episodes = meta?.videos.orEmpty()
    val seasons = episodes.map { it.season }.distinct().sortedBy { if (it == 0) 999 else it }
    var season by rememberSaveable(preview.id) { mutableStateOf(resume?.season ?: -1) }
    if (season == -1 && seasons.isNotEmpty()) season = seasons.firstOrNull { it > 0 } ?: seasons.first()
    val nextEpisode = remember(episodes, resume) {
        episodes.firstOrNull { it.id == resume?.videoId }
            ?: episodes.firstOrNull { it.season > 0 && !Store.isWatched(it.id) }
            ?: episodes.firstOrNull()
    }
    // Episodes still to watch, from where the viewer is.
    val remaining = remember(episodes, nextEpisode) {
        val from = episodes.indexOf(nextEpisode).coerceAtLeast(0)
        episodes.drop(from).filter { it.season > 0 && !Store.isWatched(it.id) }
    }
    val f = facts
    LaunchedEffect(f?.tmdbId, season) {
        if (f != null && f.kind == "tv" && season > 0 && season !in scores) scores = scores + (season to Discover.season(f.tmdbId, season))
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(DPanel.None) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    val playLabel = when {
        busy -> "جارٍ فتح المصدر…"
        resume != null && isSeries -> "▶ متابعة · S${resume.season}E${resume.episode}"
        resume != null -> "▶ متابعة المشاهدة"
        isSeries && nextEpisode != null -> "▶ تشغيل · S${nextEpisode.season}E${nextEpisode.number}"
        else -> "▶ تشغيل"
    }
    fun play() {
        if (busy) return
        val ep = if (isSeries) (episodes.firstOrNull { it.id == resume?.videoId } ?: nextEpisode) else null
        if (resume == null || Store.rememberedSource(m.id) == null) { Nav.go(Route.Sources(m, ep)); return }
        busy = true
        scope.launch { if (!resumeSameSource(m, ep)) Nav.go(Route.Sources(m, ep)); busy = false }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 52.dp, bottom = 60.dp)) {
            item {
                Column(Modifier.width(640.dp)) {
                    T(listOfNotNull(if (isSeries) "مسلسل" else "فيلم", f?.year ?: m.year, f?.seasons?.let { "$it مواسم" }, f?.networks?.firstOrNull()?.name).joinToString(" · "),
                        14.sp, color = C.GoldLight, shadow = true)
                    if (!m.logo.isNullOrBlank()) Img(m.logo, 380.dp, 110.dp, Modifier.width(380.dp).height(110.dp), ContentScale.Fit)
                    else T(m.name, 46.sp, weight = FontWeight.Bold, maxLines = 2, shadow = true)
                    Spacer(Modifier.height(8.dp))
                    MetaLine(listOfNotNull(m.rating?.let { "★ $it" }, meta?.runtime, (f?.genres ?: m.genres).take(3).joinToString("، ").ifBlank { null }))
                    f?.tagline?.let { T("«$it»", 14.sp, color = C.GoldLight, shadow = true, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(10.dp))
                    T(m.description.orEmpty(), 16.sp, color = Color(0xFFDCE2E8), maxLines = 4, lineHeight = 27.sp, shadow = true)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill(playLabel, primary = true, modifier = Modifier.focusRequester(playFocus)) { play() }
                        Pill("المصادر") { Nav.go(Route.Sources(m, if (isSeries) nextEpisode else null)) }
                        val saved = Library.inList(Library.LATER, m.id)
                        Pill(if (saved) "✓ في قائمتي" else "＋ قائمتي") { Library.toggle(Library.LATER, m) }
                    }
                    if (isSeries && remaining.isNotEmpty() && Store.feature("timeLeft")) {
                        val mins = remaining.size * (f?.runtime ?: 45)
                        T("المتبقي ${remaining.size} حلقة · قرابة ${(mins + 30) / 60} ساعة", 13.sp, color = C.Muted, shadow = true, modifier = Modifier.padding(top = 12.dp))
                    }
                    Store.text("plan:${m.id}", "").takeIf { it.isNotBlank() }?.let { T("خطة الإنهاء: $it", 13.sp, color = C.GoldLight, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (isSeries && seasons.isNotEmpty()) {
                item {
                    LazyRow(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(seasons) { s -> Chip(if (s == 0) "إضافات" else "الموسم $s", s == season, onClick = { season = s }) }
                    }
                }
                item {
                    LazyRow(Modifier.padding(top = 14.dp).focusRestorer(), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(episodes.filter { it.season == season }, key = { it.id }) { ep ->
                            EpisodeCard(ep, Store.isWatched(ep.id), m, scores[season]?.firstOrNull { it.number == ep.number }?.rating) { Nav.go(Route.Sources(m, ep)) }
                        }
                    }
                }
                val sc = scores[season].orEmpty().filter { it.rating > 0 }
                if (Store.feature("episodeCurve") && sc.size >= 3) item { EpisodeCurve(season, sc) }
            }
            if (Store.feature("whyLike") && f != null) item { WhyLike(f) }
            item {
                FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isSeries && Store.feature("finishPlan") && remaining.isNotEmpty()) Chip("خطة الإنهاء", false, onClick = { panel = DPanel.Plan })
                    if (isSeries && f != null) Chip("خريطة تقييم الحلقات", false, onClick = { panel = DPanel.Map })
                    if (Store.feature("whereToWatch") && f != null) Chip("أين يُعرض رسمياً", false, onClick = { panel = DPanel.Where })
                    if (Store.feature("parentsGuide") && f != null) Chip("دليل الأهل", false, onClick = { panel = DPanel.Parents })
                    if (f?.collectionId != null) Chip("السلسلة · ${f.collectionName.orEmpty()}", false, onClick = { panel = DPanel.Collection })
                    Chip(if (m.id in Library.hidden) "إظهار العمل" else "إخفاء العمل", false, onClick = {
                        if (m.id in Library.hidden) Library.unhide(m.id) else Library.hide(m.id)
                    })
                    if (Gemini.enabled && Store.feature("geminiExtras")) GoldChip("✦ المزيد عن العمل") { panel = DPanel.More }
                }
            }
            val c = credits
            if (c != null) {
                item { CastRow(c.first) }
                item { CompanyRow((f?.networks.orEmpty() + c.second).distinctBy { it.id }) }
            }
            if (f != null) item {
                Column(Modifier.padding(top = 10.dp)) {
                    WorksRow("مقترحات مشابهة", "sim:${f.kind}:${f.tmdbId}", null, {}) { Discover.similar(f.kind, f.tmdbId) }
                }
            }
            if (f != null) item { DetailsTable(f, meta) }
        }
        when (panel) {
            DPanel.None -> Unit
            DPanel.Plan -> FinishPlan(m, remaining, f?.runtime) { panel = DPanel.None }
            DPanel.Map -> if (f != null) RatingMap(f) { panel = DPanel.None }
            DPanel.Where -> if (f != null) WherePanel(f) { panel = DPanel.None }
            DPanel.Parents -> if (f != null) ParentsPanel(f) { panel = DPanel.None }
            DPanel.More -> MorePanel(m, f, resume?.season) { panel = DPanel.None }
            DPanel.Collection -> if (f?.collectionId != null) SidePanel(f.collectionName ?: "السلسلة", { panel = DPanel.None }, width = 460.dp) {
                val list = rememberWorks("coll:${f.collectionId}") { Discover.collection(f.collectionId) }
                val open = rememberOpener()
                if (list == null) T("جارٍ التحميل…", 14.sp, color = C.Muted)
                list?.forEachIndexed { i, w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).tvFocus(shape = RoundedCornerShape(10.dp), onClick = { open(w) }).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("${i + 1}", 18.sp, color = C.Gold, num = true, weight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Img(w.poster, 40.dp, 60.dp, Modifier.size(40.dp, 60.dp))
                        Column(Modifier.padding(start = 10.dp)) {
                            T(w.name, 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            T(listOfNotNull(w.year, if (w.rating > 0) "★ %.1f".format(w.rating) else null).joinToString(" · "), 11.sp, color = C.Muted, num = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: Episode, watched: Boolean, meta: MetaPreview, rating: Double?, onClick: () -> Unit) {
    Column(Modifier.width(250.dp)) {
        WideCard(
            title = "${ep.number}. ${ep.title}",
            image = ep.thumbnail ?: meta.background,
            fraction = if (watched) 1f else 0f,
            onFocused = {},
            onClick = onClick,
            w = 250.dp, h = 140.dp,
            badge = rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
        )
        ep.overview?.let { T(it, 13.sp, color = C.Dim, maxLines = 2, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** «خط الحلقات»: the rating of every episode of the season as one gold line; the best one is marked. */
@Composable
private fun EpisodeCurve(season: Int, list: List<EpisodeScore>) {
    val best = list.maxBy { it.rating }
    val lo = (list.minOf { it.rating } - 0.3).coerceAtLeast(0.0)
    val hi = (list.maxOf { it.rating } + 0.2).coerceAtMost(10.0)
    Glass(Modifier.padding(top = 22.dp).width(720.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T("خط الحلقات", 19.sp, weight = FontWeight.Bold)
            T("  الموسم $season", 13.sp, color = C.Muted)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 10.dp)) {
            val n = list.size
            fun pt(i: Int) = Offset(
                size.width * (1f - i / (n - 1f)), // right to left, like the page
                (size.height * (1f - ((list[i].rating - lo) / (hi - lo)).toFloat())).coerceIn(0f, size.height),
            )
            val path = Path().apply { list.indices.forEach { i -> val p = pt(i); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(path, C.Gold, style = Stroke(width = 3f))
            list.indices.forEach { i -> drawCircle(if (list[i] == best) C.GoldLight else C.Gold, if (list[i] == best) 7f else 4f, pt(i)) }
        }
        T("الأعلى: الحلقة ${best.number} «${best.name}» · ★ %.1f".format(best.rating), 13.sp, color = C.Muted, maxLines = 1)
    }
}

/** «لماذا قد يعجب»: three plain reasons from the facts (rating, makers, mix of genres). */
@Composable
private fun WhyLike(f: TitleFacts) {
    val reasons = buildList {
        if (f.rating >= 7.5 && f.votes > 200) add("تقييمه %.1f من أكثر من %,d مقيّم، من الأعلى في نوعه.".format(f.rating, f.votes))
        if (f.creators.isNotEmpty()) add((if (f.kind == "tv") "من صنع " else "من إخراج ") + f.creators.joinToString(" و") + ".")
        if (f.genres.size >= 2) add("يجمع ${f.genres.take(3).joinToString(" و")} في عمل واحد.")
        f.networks.firstOrNull()?.let { add("من إنتاج ${it.name}.") }
        val watched = Library.titles.values.count { w -> w.meta.genres.any { it in f.genres } }
        if (watched >= 2) add("يشبه $watched أعمال شوهدت من قبل في نفس النوع.")
    }.take(3)
    if (reasons.isEmpty()) return
    Glass(Modifier.padding(top = 16.dp).width(720.dp)) {
        T("لماذا قد يعجب", 19.sp, weight = FontWeight.Bold)
        reasons.forEachIndexed { i, r -> T("${i + 1}. $r", 15.sp, color = Color(0xFFDCE2E8), lineHeight = 24.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun DetailsTable(f: TitleFacts, meta: Meta?) {
    val rows = listOfNotNull(
        f.status?.let { "الحالة" to when (it) { "Ended" -> "منتهٍ"; "Returning Series" -> "مستمر"; "Released" -> "معروض"; "Canceled" -> "أُلغي"; else -> it } },
        f.language?.let { "اللغة الأصلية" to com.mawj.tv.data.Addons.languageName(it) },
        f.runtime?.let { "المدة" to "$it دقيقة" },
        f.seasons?.let { "المواسم" to "$it" },
        f.episodes?.let { "الحلقات" to "$it" },
        f.creators.takeIf { it.isNotEmpty() }?.let { (if (f.kind == "tv") "الصانع" else "الإخراج") to it.joinToString("، ") },
        meta?.cast?.takeIf { it.isNotEmpty() }?.let { "بطولة" to it.take(5).joinToString("، ") },
    )
    Glass(Modifier.padding(top = 20.dp).width(720.dp)) {
        T("التفاصيل", 19.sp, weight = FontWeight.Bold)
        rows.forEach { (k, v) ->
            Row(Modifier.padding(top = 6.dp)) {
                T(k, 14.sp, color = C.Muted, modifier = Modifier.width(130.dp))
                T(v, 14.sp, maxLines = 2)
            }
        }
    }
}

// ---------------- side panels ----------------

/** «خطة الإنهاء»: episodes per day → how many days and the date of the last episode. */
@Composable
private fun FinishPlan(m: MetaPreview, remaining: List<Episode>, runtime: Int?, onClose: () -> Unit) {
    var perDay by remember { mutableStateOf(Store.number("planDay:${m.id}", 2)) }
    val days = (remaining.size + perDay - 1) / perDay
    val end = LocalDate.now().plusDays((days - 1).toLong().coerceAtLeast(0))
    SidePanel("خطة الإنهاء", onClose) {
        T("كم حلقة في اليوم؟", 14.sp, color = C.Muted)
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n -> Chip("$n", n == perDay, onClick = { perDay = n }, modifier = if (n == perDay) restoreFirst() else Modifier) }
        }
        Glass {
            T("ينتهي خلال $days ${if (days == 1) "يوم" else "أيام"}", 22.sp, weight = FontWeight.Bold)
            T("آخر حلقة يوم ${end.dayOfMonth}/${end.monthValue} · ${remaining.size} حلقة · قرابة ${(remaining.size * (runtime ?: 45) + 30) / 60} ساعة", 13.sp, color = C.Muted)
        }
        Spacer(Modifier.height(12.dp))
        Pill("حفظ الخطة", primary = true) {
            Store.setNumber("planDay:${m.id}", perDay)
            Store.setText("plan:${m.id}", "$perDay حلقة يومياً حتى ${end.dayOfMonth}/${end.monthValue}")
            onClose()
        }
    }
}

/** «خريطة تقييم الحلقات»: every season as a row of coloured squares, green best, red weakest. */
@Composable
private fun RatingMap(f: TitleFacts, onClose: () -> Unit) {
    var all by remember { mutableStateOf<List<Pair<Int, List<EpisodeScore>>>?>(null) }
    LaunchedEffect(f.tmdbId) { all = (1..(f.seasons ?: 1).coerceAtMost(15)).map { it to Discover.season(f.tmdbId, it) } }
    SidePanel("خريطة تقييم الحلقات", onClose, width = 520.dp) {
        val a = all
        if (a == null) { T("جارٍ التحميل…", 14.sp, color = C.Muted); return@SidePanel }
        Box(restoreFirst().size(1.dp).tvFocus(onClick = {}))
        a.forEach { (s, eps) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                T("م$s", 12.sp, color = C.Muted, modifier = Modifier.width(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    eps.take(26).forEach { e ->
                        val c = when {
                            e.rating <= 0 -> Color(0x33FFFFFF)
                            e.rating >= 8.5 -> Color(0xFF3FA66B)
                            e.rating >= 7.5 -> Color(0xFF8FBF4A)
                            e.rating >= 6.5 -> Color(0xFFE0B04A)
                            else -> Color(0xFFD9604F)
                        }
                        Box(Modifier.size(15.dp).background(c, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        T("أخضر: 8.5 فأعلى · أصفر: 6.5 إلى 7.5 · أحمر: أقل", 11.sp, color = C.Dim, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun WherePanel(f: TitleFacts, onClose: () -> Unit) {
    var list by remember { mutableStateOf<List<Provider>?>(null) }
    LaunchedEffect(f.tmdbId) { list = Discover.providers(f.kind, f.tmdbId) }
    SidePanel("أين يُعرض رسمياً", onClose) {
        Box(restoreFirst().size(1.dp).tvFocus(onClick = {}))
        when {
            list == null -> T("جارٍ التحميل…", 14.sp, color = C.Muted)
            list!!.isEmpty() -> T("لا تتوفر معلومات عن عرضه الرسمي في المنطقة.", 14.sp, color = C.Muted)
            else -> list!!.forEach { p ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Img(p.logo, 34.dp, 34.dp, Modifier.size(34.dp))
                    T(p.name, 15.sp, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
                    T(p.kind, 12.sp, color = C.GoldLight)
                }
            }
        }
        T("المصدر: TMDB / JustWatch", 10.sp, color = C.Dim, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ParentsPanel(f: TitleFacts, onClose: () -> Unit) {
    var cert by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(f.tmdbId) { cert = Discover.certification(f.kind, f.tmdbId); loaded = true }
    SidePanel("دليل الأهل", onClose) {
        Box(restoreFirst().size(1.dp).tvFocus(onClick = {}))
        if (!loaded) { T("جارٍ التحميل…", 14.sp, color = C.Muted); return@SidePanel }
        T(cert?.let { "التصنيف العمري: $it" } ?: "لا يتوفر تصنيف عمري.", 18.sp, weight = FontWeight.Bold)
        val note = when (cert?.uppercase()) {
            "G", "TV-G", "TV-Y", "0", "U" -> "مناسب لكل الأعمار."
            "PG", "TV-PG", "7", "TV-Y7" -> "يُفضّل بإشراف الأهل للصغار."
            "PG-13", "TV-14", "12", "13", "14", "R12", "R13" -> "فوق 13 سنة: قد يحوي عنفاً أو ألفاظاً."
            "R", "TV-MA", "16", "18", "R15", "R18", "NC-17" -> "للكبار فقط."
            else -> null
        }
        note?.let { T(it, 15.sp, color = C.Muted, modifier = Modifier.padding(top = 8.dp)) }
        val flags = f.genres.filter { it in listOf("رعب", "جريمة", "حرب", "إثارة") }
        if (flags.isNotEmpty()) T("أنواع قد تحوي مشاهد قاسية: ${flags.joinToString("، ")}", 13.sp, color = C.GoldLight, modifier = Modifier.padding(top = 8.dp))
    }
}

/** «المزيد عن العمل»: Gemini answers on demand only; nothing is sent until a question is chosen. */
@Composable
private fun MorePanel(m: MetaPreview, f: TitleFacts?, season: Int?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val title = "«${m.name}»" + (f?.year?.let { " ($it)" } ?: "")
    val asks = buildList {
        add("هل تعلم؟" to "اذكر 5 معلومات ممتعة وقصيرة عن $title دون حرق للأحداث.")
        add("الموسيقى التصويرية" to "من ألّف الموسيقى التصويرية لـ $title؟ اذكر أبرز المقطوعات والأغاني فيه باختصار.")
        add("مواقع التصوير" to "أين صُوّر $title؟ اذكر المدن والأماكن باختصار.")
        add("بين الرواية والشاشة" to "هل $title مقتبس من كتاب أو قصة؟ إن كان كذلك فاذكر أهم الفروق باختصار دون حرق كبير، وإلا فقل ذلك.")
        add("دليل الشخصيات" to "عرّف بالشخصيات الرئيسية في $title بسطر لكل شخصية، دون حرق.")
        if (m.type == "series" && (season ?: 0) > 1) add("سابقاً في…" to "لخّص أحداث $title حتى نهاية الموسم ${season!! - 1} بنقاط قصيرة، دون أي شيء بعده.")
        add("شرح النهاية" to "اشرح نهاية $title بوضوح. تنبيه: يحوي حرقاً كاملاً للأحداث.")
    }
    SidePanel("المزيد عن العمل", onClose, width = 480.dp) {
        asks.forEachIndexed { i, (label, prompt) ->
            Chip(label, false, onClick = {
                if (busy) return@Chip
                busy = true; answer = null
                scope.launch { answer = Gemini.ask("اكتب بالعربية الفصحى السهلة وباختصار.\n$prompt") ?: "تعذّر الوصول الآن."; busy = false }
            }, modifier = (if (i == 0) restoreFirst() else Modifier).padding(bottom = 6.dp))
        }
        if (busy) T("يُجهَّز الجواب…", 13.sp, color = C.Gold)
        answer?.let { Glass(Modifier.padding(top = 8.dp)) { T(it, 14.sp, lineHeight = 23.sp) } }
        T("شرح النهاية يحوي حرقاً للأحداث.", 10.sp, color = C.Dim, modifier = Modifier.padding(top = 8.dp))
    }
}

package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.luxo.IButton
import com.nuvio.app.features.luxo.IChip
import com.nuvio.app.features.luxo.IDialog
import com.nuvio.app.features.luxo.LuxoCollaborator
import com.nuvio.app.features.luxo.LuxoCredit
import com.nuvio.app.features.luxo.LuxoPersonExtras
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.LuxoTmdb
import com.nuvio.app.features.luxo.LuxoTmdbLite
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.i
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.luxo.luxoLiteMode
import com.nuvio.app.features.luxo.luxoNowMs
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val Gold = Color(0xFFEDCB78)
private val Card = Color(0xFF10131C)

@Composable
private fun SectionTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        sub?.let { Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp) }
    }
}

@Composable
private fun PosterTile(c: LuxoCredit, watched: Boolean, width: Int = 104, tag: String? = null, onClick: () -> Unit) {
    Column(Modifier.width(width.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
            c.poster?.let { AsyncImage(it, c.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (watched) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0xFF2E7D4F)), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            tag?.let {
                Text(it, color = Color(0xFF1A1206), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp).clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Text(c.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(c.year, if (c.votes >= 20) "★ ${(c.rating * 10).toInt() / 10.0}" else null).joinToString(" · "), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

/**
 * Extras on the actor page: progress through their works, where to start, a marathon list, photos
 * through the years, voice roles, comparison, six degrees, a stills quiz, their career around the
 * world, quick facts and a birthday reminder. Each part has its own switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LuxoPersonPlus(
    person: PersonDetail,
    extras: LuxoPersonExtras?,
    displayName: String,
    isWatched: (LuxoCredit) -> Boolean,
    onOpen: (LuxoCredit) -> Unit,
    onOpenPerson: (Int) -> Unit,
) {
    val f = rememberLuxoFeatures()
    val credits = extras?.credits.orEmpty().filter { it.poster != null }
    if (extras == null) return
    var dialog by remember(person.tmdbId) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- «تقدّمك في أعماله»
        if (f.actorProgress && credits.isNotEmpty()) {
            val watched = credits.count(isWatched)
            val frac = watched.toFloat() / credits.size
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
                    .clickable { dialog = "progress" }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, style = Stroke(9.dp.toPx()))
                        drawArc(Brush.sweepGradient(listOf(Color(0xFF2E9AB0), Gold, Color(0xFF2E9AB0))), -90f, 360f * frac, false, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$watched", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("من ${credits.size}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("تقدّمك في أعماله", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(
                        when {
                            frac >= 0.75f -> "🏅 خبيرة في أعمال $displayName"
                            frac >= 0.5f -> "🎖️ شوهد أكثر من نصف أعماله"
                            watched == 0 -> "لم يُشاهَد له شيء بعد"
                            else -> "بقي ${credits.size - watched} عملاً"
                        },
                        color = Gold, fontSize = 13.sp,
                    )
                    Text("بالضغط تظهر قائمتا ما شوهد وما لم يُشاهَد", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }
        }

        // ---- «من أين أبدأ؟»
        if (f.actorStartHere) {
            val unwatched = credits.filter { !isWatched(it) }
            val best = unwatched.filter { it.votes >= 200 }.maxByOrNull { it.rating }
            val famous = unwatched.filter { it != best }.maxByOrNull { it.votes }
            val gem = unwatched.filter { it != best && it != famous && it.rating >= 7.2 && it.votes in 30..1500 }.maxByOrNull { it.rating }
            val picks = listOfNotNull(best?.let { it to "الأعلى تقييماً" }, famous?.let { it to "الأشهر" }, gem?.let { it to "جوهرة مخفية" })
            if (picks.isNotEmpty()) {
                SectionTitle("🧭 من أين أبدأ؟", "ثلاثة اختيارات من أعماله التي لم تُشاهَد بعد")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    picks.forEach { (c, tag) -> PosterTile(c, false, 128, tag) { onOpen(c) } }
                }
            }
        }

        // ---- Tools row
        val tools = buildList {
            if (f.actorMarathon) add("marathon" to "🍿 ماراثون الممثل")
            if (f.actorCompare) add("compare" to "⚖️ قارن ممثلين")
            if (f.actorSixDegrees) add("degrees" to "🔗 الدرجات الست")
            if (f.actorStillQuiz && credits.count { it.backdrop != null } >= 6) add("quiz" to "🎯 من أي عمل هذه اللقطة؟")
            if (f.actorWorldMap) add("world" to "🌍 مسيرته حول العالم")
            if (f.actorFacts) add("facts" to "💡 حقائق سريعة")
        }
        if (tools.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.forEach { (k, label) -> IChip(label) { dialog = k } }
                if (f.actorBirthday && person.birthday != null && person.deathday == null) {
                    val on = LuxoBirthdays.has(person.tmdbId)
                    IChip(if (on) "🎂 تذكير ميلاده مفعّل" else "🎂 تذكير بعيد ميلاده") {
                        val pick = credits.filter { !isWatched(it) }.maxByOrNull { it.votes } ?: credits.maxByOrNull { it.votes }
                        LuxoBirthdays.toggle(person.tmdbId, displayName, person.birthday, pick)
                    }
                }
            }
        }

        // ---- «صوره عبر السنين»
        if (f.actorPhotosYears) PhotosThroughYears(person.tmdbId)

        // ---- «الأدوار الصوتية»
        if (f.actorVoiceRoles) {
            val voice = credits.filter { (it.role ?: "").contains("voice", ignoreCase = true) || (it.role ?: "").contains("صوت") }
                .sortedByDescending { it.votes }
            if (voice.isNotEmpty()) {
                SectionTitle("🎙️ الأدوار الصوتية", "أعمال أدّى فيها بصوته")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(voice, key = { "v:${it.mediaType}:${it.id}" }) { c ->
                        PosterTile(c, isWatched(c), tag = c.role?.replace("(voice)", "")?.trim()?.take(18)) { onOpen(c) }
                    }
                }
            }
        }
    }

    when (dialog) {
        "progress" -> ProgressDialog(credits, isWatched, onOpen) { dialog = null }
        "marathon" -> MarathonDialog(displayName, credits, isWatched, onOpen) { dialog = null }
        "compare" -> CompareDialog(person.tmdbId, displayName, extras) { dialog = null }
        "degrees" -> DegreesDialog(person.tmdbId, displayName, credits, onOpenPerson) { dialog = null }
        "quiz" -> StillQuizDialog(credits) { dialog = null }
        "world" -> WorldDialog(displayName, credits) { dialog = null }
        "facts" -> FactsDialog(person, displayName) { dialog = null }
    }
}

// ---------------------------------------------------------------- progress lists

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressDialog(credits: List<LuxoCredit>, isWatched: (LuxoCredit) -> Boolean, onOpen: (LuxoCredit) -> Unit, onDismiss: () -> Unit) {
    var showWatched by remember { mutableStateOf(true) }
    var byRating by remember { mutableStateOf(false) }
    val watched = credits.filter(isWatched)
    val rest = credits.filterNot(isWatched)
    IDialog("📊 تقدّمك في أعماله", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "✓ تمت مشاهدته (${watched.size})", false to "○ لم يُشاهَد بعد (${rest.size})").forEach { (k, label) ->
                Text(label, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (showWatched == k) Color(0xFF6F8FBF) else Color.White.copy(alpha = 0.08f))
                        .clickable { showWatched = k }.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "الأحدث", true to "الأعلى تقييماً").forEach { (k, label) ->
                Text(label, color = Color.White.copy(alpha = if (byRating == k) 1f else 0.5f), fontSize = 12.sp, modifier = Modifier.clickable { byRating = k }.padding(4.dp))
            }
        }
        val list = (if (showWatched) watched else rest).let { l -> if (byRating) l.sortedByDescending { if (it.votes >= 20) it.rating else 0.0 } else l.sortedByDescending { it.date ?: "" } }
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (list.isEmpty()) Text(if (showWatched) "لم يُشاهَد له شيء بعد" else "شوهدت كل أعماله 🏆", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { c -> PosterTile(c, isWatched(c), 96) { onOpen(c); onDismiss() } }
            }
        }
    }
}

// ---------------------------------------------------------------- marathon

@Composable
private fun MarathonDialog(name: String, credits: List<LuxoCredit>, isWatched: (LuxoCredit) -> Boolean, onOpen: (LuxoCredit) -> Unit, onDismiss: () -> Unit) {
    val list = remember(credits) {
        credits.filter { !isWatched(it) && it.votes >= 80 && it.rating >= 6.4 && it.date != null }.sortedBy { it.date }.take(25)
    }
    var saved by remember { mutableStateOf(false) }
    IDialog("🍿 ماراثون $name", onDismiss) {
        Text("أفضل أعماله التي لم تُشاهَد بعد، مرتبة من الأقدم إلى الأحدث.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        if (list.isEmpty()) { Text("لا يوجد ما يكفي لماراثون.", color = Color.White.copy(alpha = 0.7f)); return@IDialog }
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).clickable { onOpen(c); onDismiss() }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    Box(Modifier.width(40.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) { c.poster?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.year ?: ""} · ★ ${(c.rating * 10).toInt() / 10.0}" + if (c.mediaType == "tv") " · مسلسل" else "", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
        IButton(if (saved) "أُضيفت إلى المكتبة ✓" else "إضافة الكل إلى المكتبة") {
            if (!saved) {
                list.forEach { c ->
                    runCatching { LibraryRepository.save(LibraryItem(id = c.metaId, type = c.metaType, name = c.title, poster = c.poster, banner = c.backdrop, releaseInfo = c.year, savedAtEpochMs = luxoNowMs())) }
                }
                saved = true
            }
        }
    }
}

// ---------------------------------------------------------------- photos through the years

private data class TaggedPhoto(val url: String, val year: String?, val work: String?)

@Composable
private fun PhotosThroughYears(personId: Int) {
    var photos by remember(personId) { mutableStateOf<List<TaggedPhoto>>(emptyList()) }
    var big by remember { mutableStateOf<TaggedPhoto?>(null) }
    LaunchedEffect(personId) {
        val res = LuxoTmdbLite.get("person/$personId/tagged_images")?.a("results").orEmpty()
        photos = res.mapNotNull { r ->
            val path = r.s("file_path") ?: return@mapNotNull null
            val media = r.o("media")
            val date = media?.s("release_date") ?: media?.s("first_air_date")
            TaggedPhoto("https://image.tmdb.org/t/p/w500$path", date?.take(4)?.takeIf { it.length == 4 }, media?.s("title") ?: media?.s("name"))
        }.distinctBy { it.url }.sortedBy { it.year ?: "9999" }.take(if (luxoLiteMode()) 16 else 40)
    }
    if (photos.size < 3) return
    SectionTitle("📸 صوره عبر السنين", "من بداياته إلى اليوم")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(photos, key = { it.url }) { p ->
            Column(Modifier.width(190.dp).clickable { big = p }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f))) {
                    AsyncImage(p.url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    p.year?.let {
                        Text(it, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
                p.work?.let { Text(it, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    big?.let { p ->
        IDialog(listOfNotNull(p.work, p.year).joinToString(" · ").ifBlank { "صورة" }, onDismiss = { big = null }) {
            AsyncImage(p.url.replace("/w500", "/original"), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(14.dp)))
        }
    }
}

// ---------------------------------------------------------------- person search (compare / six degrees)

private data class PersonHit(val id: Int, val name: String, val photo: String?, val known: String?)

@Composable
private fun PersonSearch(onPick: (PersonHit) -> Unit) {
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PersonHit>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        hits = LuxoTmdbLite.get("search/person", mapOf("query" to LuxoTmdbLite.enc(q.trim())))?.a("results").orEmpty().take(8).mapNotNull { r ->
            val id = r.i("id") ?: return@mapNotNull null
            PersonHit(id, r.s("name").orEmpty(), r.s("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" },
                r.a("known_for").mapNotNull { it.s("title") ?: it.s("name") }.take(2).joinToString("، "))
        }
    }
    OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("يُكتب اسم الممثل الآخر") })
    hits.forEach { h ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(h) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) { h.photo?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
            Column { Text(h.name, color = Color.White, fontSize = 14.sp); if (!h.known.isNullOrBlank()) Text(h.known, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1) }
        }
    }
}

// ---------------------------------------------------------------- compare

@Composable
private fun CompareDialog(meId: Int, meName: String, me: LuxoPersonExtras, onDismiss: () -> Unit) {
    var other by remember { mutableStateOf<PersonHit?>(null) }
    var otherExtras by remember { mutableStateOf<LuxoPersonExtras?>(null) }
    LaunchedEffect(other) { otherExtras = null; other?.let { otherExtras = runCatching { LuxoTmdb.person(it.id) }.getOrNull() } }
    IDialog("⚖️ قارن ممثلين", onDismiss) {
        val o = other
        if (o == null) { PersonSearch { other = it }; return@IDialog }
        val oe = otherExtras
        if (oe == null) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(22.dp)); Text("تُجمع الأعمال…", color = Color.White) }; return@IDialog }
        fun stats(e: LuxoPersonExtras): List<String> {
            val rated = e.credits.filter { it.votes >= 50 }
            val years = e.credits.mapNotNull { it.year?.toIntOrNull() }
            return listOf(
                e.credits.size.toString(),
                e.credits.count { it.mediaType == "movie" }.toString(),
                e.credits.count { it.mediaType == "tv" }.toString(),
                if (rated.isEmpty()) "—" else ((rated.sumOf { it.rating } / rated.size) * 10).toInt().div(10.0).toString(),
                rated.maxByOrNull { it.rating }?.title ?: "—",
                if (years.isEmpty()) "—" else "${years.min()}–${years.max()}",
            )
        }
        val labels = listOf("الأعمال", "الأفلام", "المسلسلات", "متوسط التقييم", "الأعلى تقييماً", "سنوات المسيرة")
        val a = stats(me); val b = stats(oe)
        Row(Modifier.fillMaxWidth()) {
            Text(meName, color = Gold, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("", modifier = Modifier.weight(0.9f))
            Text(o.name, color = Color(0xFF9DB8E0), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
        labels.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (i % 2 == 0) 0.04f else 0f)).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a[i], color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 2)
                Text(l, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text(b[i], color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        val shared = me.credits.filter { c -> oe.credits.any { it.id == c.id && it.mediaType == c.mediaType } }.sortedByDescending { it.votes }
        Text(if (shared.isEmpty()) "لم يجتمعا في أي عمل." else "🤝 جمعتهما ${shared.size} أعمال", color = Color.White, fontWeight = FontWeight.Bold)
        if (shared.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(shared.take(12), key = { "${it.mediaType}${it.id}" }) { c -> PosterTile(c, false, 88) {} } }
        Text("مقارنة بممثل آخر", color = Color(0xFF9DB8E0), fontSize = 13.sp, modifier = Modifier.clickable { other = null }.padding(4.dp))
    }
}

// ---------------------------------------------------------------- six degrees

private data class Link(val personId: Int, val personName: String, val work: String)

private suspend fun castOf(c: LuxoCredit): List<Pair<Int, String>> {
    val path = if (c.mediaType == "tv") "tv/${c.id}/aggregate_credits" else "movie/${c.id}/credits"
    return LuxoTmdbLite.get(path)?.a("cast").orEmpty().take(18).mapNotNull { p -> p.i("id")?.let { it to p.s("name").orEmpty() } }
}

private suspend fun neighbours(credits: List<LuxoCredit>): Map<Int, Link> = coroutineScope {
    val top = credits.sortedByDescending { it.votes }.take(if (luxoLiteMode()) 12 else 22)
    val out = HashMap<Int, Link>()
    top.chunked(6).forEach { chunk ->
        chunk.map { c -> async { c to runCatching { castOf(c) }.getOrDefault(emptyList()) } }.awaitAll().forEach { (c, cast) ->
            cast.forEach { (id, name) -> if (id !in out) out[id] = Link(id, name, c.title) }
        }
    }
    out
}

@Composable
private fun DegreesDialog(meId: Int, meName: String, myCredits: List<LuxoCredit>, onOpenPerson: (Int) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf<PersonHit?>(null) }
    var result by remember { mutableStateOf<List<String>?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        working = true; result = null
        val mine = neighbours(myCredits)
        mine[t.id]?.let { l ->
            result = listOf(meName, "🎬 ${l.work}", t.name); working = false; return@LaunchedEffect
        }
        val theirs = neighbours(runCatching { LuxoTmdb.person(t.id) }.getOrNull()?.credits.orEmpty())
        val bridge = mine.keys.filter { it != meId && it != t.id && it in theirs }.firstOrNull()
        result = if (bridge != null) {
            val a = mine.getValue(bridge); val b = theirs.getValue(bridge)
            listOf(meName, "🎬 ${a.work}", a.personName, "🎬 ${b.work}", t.name)
        } else emptyList()
        working = false
    }
    IDialog("🔗 لعبة الدرجات الست", onDismiss) {
        Text("يُختار ممثل آخر، فيظهر أقصر طريق بينهما عبر الأعمال المشتركة.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
        val t = target
        if (t == null) { PersonSearch { target = it }; return@IDialog }
        when {
            working -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(22.dp)); Text("يُبحث عن الطريق بين ${meName} و${t.name}…", color = Color.White) }
            result.isNullOrEmpty() -> Text("الطريق بينهما أبعد من درجتين. يمكن تجربة ممثل آخر.", color = Color.White.copy(alpha = 0.75f))
            else -> {
                val steps = result!!
                val degrees = steps.count { it.startsWith("🎬") }
                Text("$degrees ${if (degrees == 1) "درجة" else "درجات"} فقط!", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEach { s ->
                        val isWork = s.startsWith("🎬")
                        Text(s, color = if (isWork) Color.White.copy(alpha = 0.6f) else Color.White, fontSize = if (isWork) 13.sp else 17.sp,
                            fontWeight = if (isWork) FontWeight.Normal else FontWeight.Bold, modifier = Modifier.padding(start = if (isWork) 18.dp else 0.dp))
                        if (s != steps.last()) Text("↓", color = Gold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        if (!working) Text("ممثل آخر", color = Color(0xFF9DB8E0), fontSize = 13.sp, modifier = Modifier.clickable { target = null; result = null }.padding(4.dp))
    }
}

// ---------------------------------------------------------------- stills quiz

@Composable
private fun StillQuizDialog(credits: List<LuxoCredit>, onDismiss: () -> Unit) {
    val pool = remember(credits) { credits.filter { it.backdrop != null }.sortedByDescending { it.votes }.take(30).shuffled() }
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val rounds = minOf(5, pool.size)
    IDialog("🎯 من أي عمل هذه اللقطة؟", onDismiss) {
        if (round >= rounds) {
            Text("النتيجة: $score من $rounds " + if (score == rounds) "🏆" else "👏", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            IButton("جولة جديدة") { round = 0; score = 0; picked = null }
            return@IDialog
        }
        val answer = pool[round]
        val options = remember(round) { (pool.filter { it != answer }.shuffled().take(2) + answer).shuffled() }
        Text("الجولة ${round + 1} من $rounds", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
            AsyncImage(answer.backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        options.forEachIndexed { i, o ->
            val bg = when {
                picked == null -> Color.White.copy(alpha = 0.06f)
                o == answer -> Color(0xFF2E7D4F)
                i == picked -> Color(0xFF9B2C2C)
                else -> Color.White.copy(alpha = 0.04f)
            }
            Text("${o.title}${o.year?.let { " ($it)" } ?: ""}", color = Color.White,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).clickable(enabled = picked == null) {
                    picked = i; if (o == answer) score++
                }.padding(12.dp))
        }
        if (picked != null) IButton(if (round + 1 >= rounds) "النتيجة" else "التالية") { round++; picked = null }
    }
}

// ---------------------------------------------------------------- around the world

private fun flagOf(iso: String): String = iso.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }?.let { code ->
    // Regional indicator letters as surrogate pairs (U+1F1E6 = D83C DDE6).
    code.map { c -> charArrayOf('\uD83C', (0xDDE6 + (c - 'A')).toChar()).concatToString() }.joinToString("")
} ?: "🏳️"

@Composable
private fun WorldDialog(name: String, credits: List<LuxoCredit>, onDismiss: () -> Unit) {
    var countries by remember { mutableStateOf<List<Triple<String, String, Int>>?>(null) }
    LaunchedEffect(credits) {
        val top = credits.sortedByDescending { it.votes }.take(if (luxoLiteMode()) 16 else 30)
        val counts = HashMap<String, Pair<String, Int>>()
        coroutineScope {
            top.chunked(8).forEach { chunk ->
                chunk.map { c -> async { LuxoTmdbLite.get("${c.mediaType}/${c.id}", mapOf("language" to "ar"))?.a("production_countries").orEmpty() } }.awaitAll().forEach { list ->
                    list.forEach { pc ->
                        val iso = pc.s("iso_3166_1") ?: return@forEach
                        counts[iso] = (pc.s("name") ?: iso) to ((counts[iso]?.second ?: 0) + 1)
                    }
                }
            }
        }
        countries = counts.entries.map { Triple(it.key, it.value.first, it.value.second) }.sortedByDescending { it.third }
    }
    IDialog("🌍 مسيرة $name حول العالم", onDismiss) {
        val c = countries
        if (c == null) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(22.dp)); Text("تُجمع الدول…", color = Color.White) }; return@IDialog }
        if (c.isEmpty()) { Text("لا تتوفر بيانات.", color = Color.White.copy(alpha = 0.7f)); return@IDialog }
        Text("أعماله الأشهر صُنعت في ${c.size} ${if (c.size in 3..10) "دول" else "دولة"}", color = Gold, fontWeight = FontWeight.Bold)
        val max = c.first().third
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            c.forEach { (iso, n, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(flagOf(iso), fontSize = 24.sp)
                    Column(Modifier.weight(1f)) {
                        Text(n, color = Color.White, fontSize = 14.sp)
                        Box(Modifier.fillMaxWidth(count.toFloat() / max).height(6.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF2E9AB0), Gold))))
                    }
                    Text("$count", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- quick facts

@Composable
private fun FactsDialog(person: PersonDetail, name: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(person.tmdbId) {
        val path = "luxo_person_facts/${person.tmdbId}.txt"
        AppFileCache.read(path)?.let { c ->
            if (luxoNowMs() - (c.substringBefore('\n').toLongOrNull() ?: 0L) < 30L * 86_400_000L) { text = c.substringAfter('\n'); return@LaunchedEffect }
        }
        val key = LuxoCompanionApi.loadKey()
        if (key == null) { text = "يلزم مفتاح Gemini (من «الإعدادات ← المزايا ← مفتاح Gemini»)."; return@LaunchedEffect }
        val answer = LuxoCompanionApi.ask(
            key,
            "اذكر ثماني حقائق موثوقة وممتعة عن ${person.name}" + (person.birthday?.let { " (مواليد $it)" } ?: "") +
                ": بداياته، أدوار غيّرت مسيرته، مواقف من التصوير، مهارات أو اهتمامات معروفة عنه. كل حقيقة في سطر يبدأ بـ «• ». " +
                "لا إشاعات ولا تفاصيل من حياته الخاصة غير المعلنة. بالعربية الفصحى، بلا Markdown.",
            search = true,
        )
        if (answer.length > 60 && !answer.startsWith("تعذّر")) AppFileCache.write(path, "${luxoNowMs()}\n$answer")
        text = answer
    }
    IDialog("💡 حقائق سريعة عن $name", onDismiss) {
        val t = text
        if (t == null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(22.dp)); Text("تُجمع الحقائق…", color = Color.White) }
        else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { Text(t, color = Color.White, fontSize = 15.sp, lineHeight = 24.sp) }
        Text("المصدر: Gemini، وقد لا تكون كل المعلومات دقيقة.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- birthdays

/** «تذكير بعيد ميلاده»: read by the evening check, which posts a note with a suggestion on the day. */
object LuxoBirthdays {
    private const val KEY = "person_birthdays_v1"
    /** "id|MM-DD|name|title|metaType|metaId" lines. */
    fun all(): List<List<String>> = LuxoPrefsStorage.load(KEY).orEmpty().lines().filter { it.isNotBlank() }.map { it.split('|') }.filter { it.size >= 6 }
    fun has(id: Int): Boolean { version; return all().any { it[0] == id.toString() } }

    fun toggle(id: Int, name: String, birthday: String?, pick: LuxoCredit?) {
        val rest = all().filter { it[0] != id.toString() }
        val next = if (has(id) || birthday == null || birthday.length < 10) rest else rest + listOf(listOf(
            id.toString(), birthday.substring(5, 10), name.replace('|', ' '), pick?.title?.replace('|', ' ').orEmpty(), pick?.metaType.orEmpty(), pick?.metaId.orEmpty(),
        ))
        LuxoPrefsStorage.save(KEY, next.joinToString("\n") { it.joinToString("|") })
        version++
        com.nuvio.app.features.luxo.LuxoEvening.reschedule(force = true)
    }

    var version by mutableStateOf(0)
        private set
}

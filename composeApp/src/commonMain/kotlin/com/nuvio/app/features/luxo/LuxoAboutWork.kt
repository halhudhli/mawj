package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.details.LuxoCompanionApi
import com.nuvio.app.features.luxo.LuxoTmdbLite.a
import com.nuvio.app.features.luxo.LuxoTmdbLite.o
import com.nuvio.app.features.luxo.LuxoTmdbLite.s
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.WatchedRepository

/**
 * «✨ المزيد عن العمل»: one chip on the details page that opens a panel of extras —
 * soundtrack, filming locations, trivia, book vs screen, where it streams officially,
 * a parents' content guide, personal notes and «شاهدته خارج موج».
 */
data class LuxoWork(
    val metaId: String,
    val metaType: String,
    val title: String,
    val englishTitle: String?,
    val year: String?,
    val tmdbId: String?,
    val mediaType: String?,
    val poster: String?,
)

private enum class AboutTab(val emoji: String, val label: String, val needsGemini: Boolean) {
    Soundtrack("🎵", "الموسيقى التصويرية", true),
    Locations("📍", "مواقع التصوير", true),
    Trivia("💡", "هل تعلم؟", true),
    Book("📖", "بين الرواية والشاشة", true),
    Providers("📺", "أين يُعرض رسمياً؟", false),
    Parents("👪", "دليل المحتوى للأهل", true),
    Notes("📝", "ملاحظاتي", false),
    Outside("🎟️", "شاهدته خارج موج", false),
}

private fun AboutTab.enabled(f: LuxoFeatures): Boolean = when (this) {
    AboutTab.Soundtrack -> f.soundtrack
    AboutTab.Locations -> f.filmingLocations
    AboutTab.Trivia -> f.trivia
    AboutTab.Book -> f.bookVsScreen
    AboutTab.Providers -> f.whereToWatch
    AboutTab.Parents -> f.parentsGuide
    AboutTab.Notes -> f.personalNotes
    AboutTab.Outside -> f.watchedOutside
}

@Composable
fun LuxoAboutWorkChip(work: LuxoWork) {
    val f = rememberLuxoFeatures()
    val tabs = AboutTab.entries.filter { it.enabled(f) }
    if (tabs.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    IChip("✨ المزيد عن العمل") { open = true }
    if (open) LuxoAboutWorkDialog(work, tabs) { open = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LuxoAboutWorkDialog(work: LuxoWork, tabs: List<AboutTab>, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf<AboutTab?>(null) }
    IDialog(tab?.let { "${it.emoji} ${it.label}" } ?: "✨ المزيد عن العمل", onDismiss = { if (tab != null) tab = null else onDismiss() }) {
        Text(work.title + (work.year?.let { " · $it" } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        val current = tab
        if (current == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEach { t ->
                    Column(
                        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))
                            .clickable { tab = t }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(t.emoji, fontSize = 26.sp)
                        Text(t.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Text("بعض الأقسام تعتمد على Gemini، وقد لا تكون دقيقة دائماً.", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
        } else {
            Text("‹ رجوع", color = Color(0xFF9DB8E0), fontSize = 13.sp, modifier = Modifier.clickable { tab = null }.padding(vertical = 2.dp))
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (current) {
                    AboutTab.Soundtrack -> SoundtrackView(work)
                    AboutTab.Locations -> LocationsView(work)
                    AboutTab.Trivia -> AskView(work, "trivia", triviaPrompt(work), search = true)
                    AboutTab.Book -> BookView(work)
                    AboutTab.Providers -> ProvidersView(work)
                    AboutTab.Parents -> ParentsView(work)
                    AboutTab.Notes -> NotesView(work)
                    AboutTab.Outside -> OutsideView(work)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Gemini helpers

private fun workLine(w: LuxoWork): String = buildString {
    append("«").append(w.title).append("»")
    w.englishTitle?.takeIf { it != w.title }?.let { append(" (").append(it).append(")") }
    w.year?.let { append(" — ").append(it) }
    append(if (w.mediaType == "tv" || w.metaType == "series") "، مسلسل" else "، فيلم")
}

private const val PLAIN = " اكتب بالعربية الفصحى، بلا تنسيق Markdown ولا رموز نجمية."

/** Answers are cached per work for a month, to save the key's quota. */
private suspend fun askCached(w: LuxoWork, kind: String, prompt: String, search: Boolean): String {
    val path = "luxo_about/${kind}_${w.metaId.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")}.txt"
    AppFileCache.read(path)?.let { c ->
        val at = c.substringBefore('\n').toLongOrNull() ?: 0L
        if (luxoNowMs() - at < 30L * 86_400_000L) return c.substringAfter('\n')
    }
    val key = LuxoCompanionApi.loadKey() ?: return "يلزم مفتاح Gemini (من «الإعدادات ← مفتاح Gemini»)."
    val answer = LuxoCompanionApi.ask(key, prompt, search = search)
    if (answer.length > 40 && !answer.startsWith("تعذّر") && !answer.startsWith("Gemini")) AppFileCache.write(path, "${luxoNowMs()}\n$answer")
    return answer
}

@Composable
private fun rememberAnswer(w: LuxoWork, kind: String, prompt: String, search: Boolean): String? {
    var answer by remember(kind) { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, w.metaId) { answer = askCached(w, kind, prompt, search) }
    return answer
}

@Composable
private fun Loading(text: String = "يُجهَّز…") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(text, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun AskView(w: LuxoWork, kind: String, prompt: String, search: Boolean) {
    val a = rememberAnswer(w, kind, prompt, search)
    if (a == null) Loading() else Text(a, color = Color.White, fontSize = 15.sp, lineHeight = 24.sp)
}

private fun triviaPrompt(w: LuxoWork) =
    "اذكر ثماني معلومات ممتعة وموثوقة من خلف كواليس ${workLine(w)}: التصوير، الممثلون، الأرقام، المواقف الطريفة. " +
        "كل معلومة في سطر يبدأ بـ «• ». بدون حرق لأحداث القصة.$PLAIN"

// ---------------------------------------------------------------- 🎵 soundtrack

@Composable
private fun SoundtrackView(w: LuxoWork) {
    val prompt = "اذكر الأغاني والمقطوعات المعروفة في ${workLine(w)} (الأغاني المرخّصة والموسيقى الرئيسية). " +
        "أعد كل أغنية في سطر بهذا الشكل فقط: اسم الأغنية | الفنان | متى تُسمع (وصف قصير بلا حرق). " +
        "حتى 20 سطراً، بلا مقدمة ولا خاتمة. إن لم تتوفر معلومات موثوقة فاكتب: لا تتوفر معلومات."
    val a = rememberAnswer(w, "ost", prompt, search = true) ?: return Loading("تُجمع الأغاني…")
    val rows = a.lines().map { it.trim().trimStart('•', '-', '*', ' ') }.filter { it.count { c -> c == '|' } >= 1 }
    if (rows.isEmpty()) { Text(a, color = Color.White); return }
    val uri = LocalUriHandler.current
    rows.forEach { line ->
        val parts = line.split('|').map { it.trim() }
        val song = parts.getOrNull(0).orEmpty()
        val artist = parts.getOrNull(1).orEmpty()
        val q = LuxoTmdbLite.enc("$song $artist")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(song, color = Color.White, fontWeight = FontWeight.Bold)
            if (artist.isNotBlank()) Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IChip("أنغامي") { runCatching { uri.openUri("https://play.anghami.com/search/$q") } }
                IChip("سبوتيفاي") { runCatching { uri.openUri("https://open.spotify.com/search/$q") } }
                IChip("يوتيوب") { runCatching { uri.openUri("https://www.youtube.com/results?search_query=$q") } }
            }
        }
    }
}

// ---------------------------------------------------------------- 📍 filming locations

@Composable
private fun LocationsView(w: LuxoWork) {
    val prompt = "اذكر مواقع تصوير ${workLine(w)} الحقيقية. أعد كل موقع في سطر بهذا الشكل فقط: " +
        "اسم المكان | المدينة، الدولة | ما الذي صُوّر فيه (بلا حرق). حتى 15 سطراً بلا مقدمة. " +
        "اكتب اسم المكان كما يُبحث عنه في الخرائط. إن لم تتوفر معلومات موثوقة فاكتب: لا تتوفر معلومات."
    val a = rememberAnswer(w, "locations", prompt, search = true) ?: return Loading("تُجمع المواقع…")
    val rows = a.lines().map { it.trim().trimStart('•', '-', '*', ' ') }.filter { it.contains('|') }
    if (rows.isEmpty()) { Text(a, color = Color.White); return }
    val uri = LocalUriHandler.current
    rows.forEach { line ->
        val p = line.split('|').map { it.trim() }
        val place = p.getOrNull(0).orEmpty()
        val city = p.getOrNull(1).orEmpty()
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(place, color = Color.White, fontWeight = FontWeight.Bold)
                if (city.isNotBlank()) Text(city, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                p.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) }
            }
            IChip("🗺️ الخريطة") { runCatching { uri.openUri("https://www.google.com/maps/search/?api=1&query=" + LuxoTmdbLite.enc("$place $city")) } }
        }
    }
}

// ---------------------------------------------------------------- 📖 book vs screen

@Composable
private fun BookView(w: LuxoWork) {
    var confirmed by remember { mutableStateOf(false) }
    if (!confirmed) {
        Text("يكشف هذا القسم فروقاً قد تتضمن أحداثاً من القصة.", color = Color.White.copy(alpha = 0.75f))
        IButton("عرض المقارنة") { confirmed = true }
        return
    }
    AskView(
        w, "book",
        "هل ${workLine(w)} مقتبس من رواية أو كتاب أو قصة مصوّرة؟ إن لم يكن مقتبساً فاكتب سطراً واحداً: «العمل ليس مقتبساً من كتاب». " +
            "وإن كان مقتبساً: اذكر اسم الكتاب ومؤلفه وسنة نشره، ثم أهم الفروق بين الكتاب والعمل المصوَّر في نقاط تبدأ بـ «• » " +
            "(الشخصيات، الأحداث، النهاية)، ثم سطراً عن أيهما يُنصح به أولاً.$PLAIN",
        search = true,
    )
}

// ---------------------------------------------------------------- 📺 official providers (TMDB, Saudi Arabia)

@Composable
private fun ProvidersView(w: LuxoWork) {
    val type = if (w.mediaType == "tv" || w.metaType == "series") "tv" else "movie"
    var result by remember { mutableStateOf<List<Pair<String, List<Pair<String, String?>>>>?>(null) }
    LaunchedEffect(w.tmdbId) {
        val id = w.tmdbId
        if (id == null) { result = emptyList(); return@LaunchedEffect }
        val all = LuxoTmdbLite.get("$type/$id/watch/providers")?.o("results")
        val region = all?.o("SA") ?: all?.o("AE")
        fun list(k: String) = region?.a(k).orEmpty().mapNotNull { p -> p.s("provider_name")?.let { it to LuxoTmdbLite.img(p.s("logo_path"), "w92") } }
        result = listOf("اشتراك" to list("flatrate"), "مجاناً" to list("free"), "إيجار" to list("rent"), "شراء" to list("buy"))
            .filter { it.second.isNotEmpty() }
    }
    val r = result ?: return Loading()
    if (r.isEmpty()) {
        Text("لا تتوفر بيانات عن عرضه رسمياً في السعودية حالياً.", color = Color.White.copy(alpha = 0.75f))
        return
    }
    r.forEach { (label, providers) ->
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        providers.forEach { (name, logo) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    logo?.let { AsyncImage(it, null, modifier = Modifier.size(36.dp)) }
                }
                Text(name, color = Color.White, fontSize = 15.sp)
            }
        }
    }
    Text("المصدر: TMDB و JustWatch · المنطقة: السعودية", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
}

// ---------------------------------------------------------------- 👪 parents' guide

@Composable
private fun ParentsView(w: LuxoWork) {
    val tv = w.mediaType == "tv" || w.metaType == "series"
    var rating by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(w.tmdbId) {
        val id = w.tmdbId ?: return@LaunchedEffect
        rating = if (tv) {
            val list = LuxoTmdbLite.get("tv/$id/content_ratings")?.a("results").orEmpty()
            (list.firstOrNull { it.s("iso_3166_1") == "SA" } ?: list.firstOrNull { it.s("iso_3166_1") == "US" })?.s("rating")
        } else {
            val list = LuxoTmdbLite.get("movie/$id/release_dates")?.a("results").orEmpty()
            val r = list.firstOrNull { it.s("iso_3166_1") == "SA" } ?: list.firstOrNull { it.s("iso_3166_1") == "US" }
            r?.a("release_dates")?.mapNotNull { it.s("certification") }?.firstOrNull()
        }
    }
    rating?.let { Text("التصنيف العمري: $it", color = Color.White, fontWeight = FontWeight.Bold) }
    AskView(
        w, "parents",
        "قدّم دليلاً مختصراً للأهل عن محتوى ${workLine(w)}. لكل بند اذكر المستوى (لا يوجد، خفيف، متوسط، شديد) مع سطر توضيح بلا حرق: " +
            "• العنف والدماء\n• الألفاظ\n• المشاهد غير اللائقة والإيحاءات\n• المشاهد المخيفة\n• التدخين والكحول والمخدرات\n" +
            "ثم سطراً أخيراً: العمر المناسب المقترح.$PLAIN",
        search = true,
    )
}

// ---------------------------------------------------------------- 📝 notes

object LuxoNotes {
    private fun key(id: String) = "note_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}_$id"
    fun get(id: String): String = LuxoPrefsStorage.load(key(id)).orEmpty()
    fun set(id: String, text: String) = LuxoPrefsStorage.save(key(id), text)
}

@Composable
private fun NotesView(w: LuxoWork) {
    var text by remember { mutableStateOf(LuxoNotes.get(w.metaId)) }
    var saved by remember { mutableStateOf(false) }
    Text("ملاحظات خاصة بهذا العمل، تُحفظ على هذا الجهاز ولا يراها غيرك.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; saved = false },
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
        placeholder = { Text("رأيي، اقتباس أعجبني، مع من شاهدته…") },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IButton("حفظ") { LuxoNotes.set(w.metaId, text.trim()); saved = true }
        if (saved) Text("حُفظت ✓", color = Color(0xFF8FD19E), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- 🎟️ watched outside Mawj

object LuxoOutside {
    private fun key() = "outside_${com.nuvio.app.features.profiles.ProfileRepository.activeProfileId}"
    /** metaId → "place|epochMs|title". */
    fun all(): Map<String, String> = LuxoPrefsStorage.load(key()).orEmpty().lines().filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    fun place(id: String): String? = all()[id]?.substringBefore('|')
    fun set(id: String, place: String, title: String) {
        val m = all().toMutableMap(); m[id] = "$place|${luxoNowMs()}|$title"
        LuxoPrefsStorage.save(key(), m.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutsideView(w: LuxoWork) {
    var place by remember { mutableStateOf(LuxoOutside.place(w.metaId)) }
    val isMovie = !(w.mediaType == "tv" || w.metaType == "series")
    Text(
        if (isMovie) "يُسجَّل الفيلم كمُشاهَد في سجلك وفي «ذوقي»، مع مكان المشاهدة."
        else "يُسجَّل المسلسل في سجلك مع مكان المشاهدة. ولتعليم حلقات بعينها يُضغط مطولاً على الحلقة أو الموسم.",
        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("🎬 في السينما", "📱 منصة أخرى", "📺 على التلفزيون", "✈️ في رحلة").forEach { p ->
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (place == p) Color(0xFF6F8FBF) else Color.White.copy(alpha = 0.08f))
                    .clickable {
                        place = p
                        LuxoOutside.set(w.metaId, p, w.title)
                        if (isMovie) {
                            WatchedRepository.ensureLoaded()
                            WatchedRepository.markWatched(
                                WatchedItem(id = w.metaId, type = w.metaType, name = w.title, poster = w.poster, releaseInfo = w.year, markedAtEpochMs = luxoNowMs()),
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(p, color = Color.White, fontSize = 14.sp) }
        }
    }
    place?.let { Text("سُجّل: $it ✓", color = Color(0xFF8FD19E), fontSize = 13.sp) }
}

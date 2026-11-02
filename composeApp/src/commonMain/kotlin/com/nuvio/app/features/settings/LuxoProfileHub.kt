package com.nuvio.app.features.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.AppLoadingProfileAvatar
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.luxo.LuxoUserData
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.luxoTvFocus
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.luxo.rememberLuxoTilt
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ------------------------------------------------------------------ stats

internal data class LuxoPassStats(
    val movies: Int,
    val episodes: Int,
    val hours: Int,
    val streakDays: Int,
    val library: Int,
    val ratings: Int,
    val lists: Int,
    val nightShare: Float,
    val seriesShare: Float,
)

private const val DAY_MS = 24L * 60 * 60 * 1000

@Composable
internal fun rememberLuxoPassStats(): LuxoPassStats {
    val watched by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val progress by remember {
        WatchProgressRepository.ensureLoaded()
        WatchProgressRepository.uiState
    }.collectAsStateWithLifecycle()
    val library by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()
    val userData by LuxoUserData.state.collectAsStateWithLifecycle()
    return remember(watched.items, progress.entries, library.items, userData) {
        val episodes = watched.items.count { it.season != null }
        val movies = watched.items.filter { it.season == null && it.type == "movie" }.distinctBy { it.id }.size
        val inProgressMs = progress.entries.sumOf { it.lastPositionMs.coerceAtLeast(0L) }
        // Finished titles rarely keep a position, so count a typical runtime for them.
        val hours = ((inProgressMs / 3_600_000L) + movies * 2 + (episodes * 45) / 60).toInt()
        val stamps = watched.items.map { it.markedAtEpochMs } + progress.entries.map { it.lastUpdatedEpochMs }
        val days = stamps.filter { it > 0 }.map { it / DAY_MS }.toSortedSet()
        var streak = 0
        var best = 0
        var previous: Long? = null
        days.forEach { day ->
            streak = if (previous != null && day == previous!! + 1) streak + 1 else 1
            if (streak > best) best = streak
            previous = day
        }
        // Night = between midnight and 4am (UTC+3 is the common case for Luxo, close enough for a badge).
        val night = stamps.count { stamp -> ((stamp / 3_600_000L + 3) % 24) in 0..3 }
        LuxoPassStats(
            movies = movies,
            episodes = episodes,
            hours = hours,
            streakDays = best,
            library = library.items.size,
            ratings = userData.ratings.size,
            lists = userData.lists.size,
            nightShare = if (stamps.isEmpty()) 0f else night.toFloat() / stamps.size,
            seriesShare = if (movies + episodes == 0) 0f else episodes.toFloat() / (movies + episodes / 6f + episodes),
        )
    }
}

@Composable
private fun luxoTasteTitle(stats: LuxoPassStats): String = when {
    stats.movies + stats.episodes < 5 -> lx("بداية الحكاية ✨", "The story begins ✨")
    stats.nightShare > 0.4f -> lx("سهران الليل 🌙", "Night owl 🌙")
    stats.episodes > stats.movies * 12 -> lx("مدمن مسلسلات 📺", "Series addict 📺")
    stats.movies > stats.episodes / 4 + 10 -> lx("سينمائي أصيل 🎬", "True cinephile 🎬")
    else -> lx("متذوّق متوازن ⚖️", "Balanced taste ⚖️")
}

// ------------------------------------------------------------------ hub

internal fun LazyListScope.luxoProfileHubContent(
    isTablet: Boolean,
    onProfileClick: (() -> Unit)?,
    onAccountClick: () -> Unit,
    onWatchPartyClick: (() -> Unit)?,
    onTrackingClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onContentDiscoveryClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onCheckForUpdatesClick: (() -> Unit)?,
    onLicensesAttributionsClick: () -> Unit,
) {
    item(key = "luxo_pass") {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            LuxoPassCard(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = if (isTablet) 0.dp else 16.dp),
            )
        }
    }
    item(key = "luxo_halls") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isTablet) 0.dp else 16.dp)
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                lx("القاعات", "Halls"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
            val halls = buildList {
                if (onProfileClick != null) {
                    add(LuxoHall(lx("ذوقي", "My taste"), lx("الإحصائيات والأوسمة وخريطة المشاهدة", "Stats, badges and your watch map"), Icons.Rounded.AutoAwesome, Color(0xFFB57BFF), onProfileClick, wide = true))
                }
                if (com.nuvio.app.features.luxo.LuxoFeaturesRepository.current().franchises) {
                    add(LuxoHall(lx("السلاسل", "Franchises"), lx("الأجزاء بترتيب المشاهدة", "Parts in watch order"), Icons.Rounded.Movie, Color(0xFFFF9A5C), { com.nuvio.app.features.luxo.LuxoOverlays.franchise = "" }))
                }
                val luxoF = com.nuvio.app.features.luxo.LuxoFeaturesRepository.current()
                if (luxoF.guessGame) {
                    add(LuxoHall(lx("خمّن العمل", "Guess the title"), lx("لقطة مموّهة وأربعة خيارات", "A blurred frame, four choices"), Icons.Rounded.SportsEsports, Color(0xFF5ED6C9), { com.nuvio.app.features.luxo.LuxoOverlays.guessGame = true }))
                }
                if (luxoF.privateMode) {
                    val on = com.nuvio.app.features.luxo.LuxoPrivateMode.active
                    add(LuxoHall(
                        lx(if (on) "الوضع الخاص: يعمل" else "الوضع الخاص", if (on) "Private mode: on" else "Private mode"),
                        lx(if (on) "لا يُسجَّل شيء الآن · الضغط يوقفه" else "مشاهدة بدون تسجيل التقدم ولا Trakt", if (on) "Nothing is recorded · tap to stop" else "Watch without recording progress or Trakt"),
                        Icons.Rounded.VisibilityOff,
                        if (on) Color(0xFF7BE0A3) else Color(0xFF9AA3AD),
                        { com.nuvio.app.features.luxo.LuxoPrivateMode.toggle() },
                    ))
                }
                add(LuxoHall(lx("الحساب", "Account"), lx("نوفيو والمزامنة", "Nuvio and sync"), Icons.Rounded.AccountCircle, Color(0xFF5CC8FF), onAccountClick))
                if (onWatchPartyClick != null) {
                    add(LuxoHall(lx("سهرة جماعية", "Watch party"), lx("شاهد مع الأصدقاء", "Watch together"), Icons.Rounded.Groups, Color(0xFFFF7BA9), onWatchPartyClick))
                }
                add(LuxoHall(lx("المظهر", "Appearance"), lx("الثيمات والخطوط والشريط وإضافات Luxo", "Themes, fonts, the bar and Luxo extras"), Icons.Rounded.Palette, Color(0xFFFFB95C), onAppearanceClick, wide = true))
                add(LuxoHall(lx("المحتوى", "Content"), lx("الإضافات والكتالوج", "Addons and catalogs"), Icons.Rounded.Extension, Color(0xFF7BE0A3), onContentDiscoveryClick))
                add(LuxoHall(lx("التشغيل", "Playback"), lx("المشغل والترجمة", "Player and subtitles"), Icons.Rounded.PlayArrow, Color(0xFFFF6B6B), onPlaybackClick))
                add(LuxoHall(lx("التكاملات", "Integrations"), lx("الخدمات المرتبطة", "Linked services"), Icons.Rounded.Link, Color(0xFF8C9EFF), onIntegrationsClick))
                add(LuxoHall(lx("الإشعارات", "Notifications"), lx("تنبيهات الحلقات", "Episode alerts"), Icons.Rounded.Notifications, Color(0xFFFFD86B), onNotificationsClick))
                add(LuxoHall(lx("التتبّع", "Tracking"), "Trakt", Icons.Rounded.Sync, Color(0xFFED1C24), onTrackingClick))
                add(LuxoHall(lx("متقدم", "Advanced"), lx("النسخ الاحتياطي وكل الخيارات", "Backup and every option"), Icons.Rounded.Tune, Color(0xFF9AA3AD), onAdvancedClick))
                if (onCheckForUpdatesClick != null) {
                    add(LuxoHall(lx("التحديثات", "Updates"), lx("آخر إصدار من Luxo", "Latest Luxo release"), Icons.Rounded.NewReleases, Color(0xFF6BE3FF), onCheckForUpdatesClick))
                }
                add(LuxoHall(lx("حول", "About"), lx("التراخيص والشكر", "Licenses and credits"), Icons.Rounded.Info, Color(0xFFC9B6FF), onLicensesAttributionsClick))
            }
            LuxoHallGrid(halls = halls, columns = if (isTablet) 4 else 2)
        }
    }
}

private class LuxoHall(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
    val wide: Boolean = false,
)

@Composable
private fun LuxoHallGrid(halls: List<LuxoHall>, columns: Int) {
    // Pack halls into rows: a wide hall takes two cells.
    val rows = mutableListOf<MutableList<LuxoHall>>()
    var current = mutableListOf<LuxoHall>()
    var used = 0
    halls.forEach { hall ->
        val span = if (hall.wide) 2 else 1
        if (used + span > columns) {
            rows += current
            current = mutableListOf()
            used = 0
        }
        current += hall
        used += span
    }
    if (current.isNotEmpty()) rows += current
    var number = 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var cells = 0
                row.forEach { hall ->
                    number += 1
                    val span = if (hall.wide) 2 else 1
                    cells += span
                    LuxoHallTile(hall, number, Modifier.weight(span.toFloat()))
                }
                if (cells < columns) Spacer(Modifier.weight((columns - cells).toFloat()))
            }
        }
    }
}

@Composable
private fun RowScope.LuxoHallTile(hall: LuxoHall, number: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier
            .heightIn(min = 128.dp)
            .luxoTvFocus(24.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(hall.tint.copy(alpha = 0.26f), tokens.colors.surface.copy(alpha = 0.9f), tokens.colors.surface),
                    start = Offset.Zero,
                    end = Offset(600f, 600f),
                ),
            )
            .border(1.dp, hall.tint.copy(alpha = 0.22f), shape)
            .clickable(onClick = hall.onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(hall.tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(hall.icon, contentDescription = null, tint = hall.tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = lx("قاعة", "Hall") + " " + number.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                color = hall.tint.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            hall.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            hall.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ the pass

/** A ticket with notches on both long edges. */
private class LuxoTicketShape(private val notch: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = notch * density.density
        val corner = 26f * density.density
        val body = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    Rect(Offset.Zero, size),
                    androidx.compose.ui.geometry.CornerRadius(corner, corner),
                ),
            )
        }
        // The stub sits at the end side; the notches mark the tear line.
        val tearX = if (layoutDirection == LayoutDirection.Rtl) size.width * 0.26f else size.width * 0.74f
        val holes = Path().apply {
            addOval(Rect(Offset(tearX, 0f), radius))
            addOval(Rect(Offset(tearX, size.height), radius))
        }
        val result = Path()
        result.op(body, holes, PathOperation.Difference)
        return Outline.Generic(result)
    }
}

@Composable
private fun LuxoPassCard(modifier: Modifier = Modifier) {
    val features = rememberLuxoFeatures()
    val tilt = rememberLuxoTilt(features.passTilt)
    var flipped by rememberSaveable { mutableStateOf(false) }
    val flip by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = 0.72f, stiffness = 240f), label = "pass_flip")
    val shine = rememberInfiniteTransition(label = "pass_shine")
    val sweep by shine.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "pass_sweep",
    )
    val stats = rememberLuxoPassStats()
    val shape = remember { LuxoTicketShape(notch = 14f) }

    Box(
        modifier = modifier
            .aspectRatio(1.62f)
            .graphicsLayer {
                rotationY = flip + tilt.x * 7f
                rotationX = -tilt.y * 6f
                cameraDistance = 14f * density
            }
            .clip(shape)
            .clickable { flipped = !flipped },
    ) {
        val showBack = flip > 90f
        // Base: deep night gradient.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1B1035), Color(0xFF3A1250), Color(0xFF14283F)),
                        start = Offset.Zero,
                        end = Offset(1200f, 700f),
                    ),
                ),
        )
        // Holographic foil: a rainbow band that drifts with time and the device tilt.
        Canvas(Modifier.fillMaxSize()) {
            val travel = size.width * 1.6f
            val center = -size.width * 0.3f + travel * ((sweep + tilt.x * 0.25f + 1f) % 1f)
            val band = size.width * 0.55f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x33FF5FD2),
                        Color(0x4D7BF7FF),
                        Color(0x40FFF27B),
                        Color(0x33B57BFF),
                        Color.Transparent,
                    ),
                    start = Offset(center - band, 0f),
                    end = Offset(center + band, size.height * (0.8f + tilt.y * 0.3f)),
                ),
            )
            // Fine guilloche lines for a printed-pass feel.
            val lines = 18
            for (i in 0 until lines) {
                val y = size.height * i / lines
                val wobble = sin((i / lines.toFloat()) * 2f * PI.toFloat() + sweep * 2f * PI.toFloat()) * 6f
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(0f, y + wobble),
                    end = Offset(size.width, y - wobble),
                    strokeWidth = 1f,
                )
            }
        }
        if (!showBack) {
            LuxoPassFront(stats)
        } else {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                LuxoPassBack(stats)
            }
        }
    }
}

@Composable
private fun LuxoPassFront(stats: LuxoPassStats) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile
    val index = profile?.profileIndex ?: 1
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxSize()) {
        // Main part
        Column(
            modifier = Modifier
                .weight(0.74f)
                .fillMaxHeight()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LUXO PASS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("★ VIP", color = Color(0xFFFFD86B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(2.dp, Brush.sweepGradient(listOf(Color(0xFFFF5FD2), Color(0xFF7BF7FF), Color(0xFFFFF27B), Color(0xFFFF5FD2))), CircleShape)
                        .padding(3.dp),
                ) {
                    if (profile != null) AppLoadingProfileAvatar(profile = profile, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.name?.takeIf { it.isNotBlank() } ?: "Luxo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(luxoTasteTitle(stats), color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(lx("رقم العضوية", "Member no."), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                    Text(
                        "LX-" + (index * 1307 % 10000).toString().padStart(4, '0'),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
                LuxoBarcode(seed = index, modifier = Modifier.width(96.dp).height(26.dp))
            }
        }
        // Tear line
        Canvas(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 18.dp)) {
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
        // Stub
        Column(
            modifier = Modifier
                .weight(0.26f)
                .fillMaxHeight()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lx("مقعد", "Seat"), color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
            Text(
                "${('A' + (index - 1).mod(8))}-${(index * 7).mod(30) + 1}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                lx("الضغط يقلب البطاقة", "Tap to flip"),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (isRtl) Unit
}

@Composable
private fun LuxoPassBack(stats: LuxoPassStats) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(lx("سجل المشاهدة", "Watch record"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LuxoPassStat(stats.movies.toString(), lx("فيلم", "Movies"))
            LuxoPassStat(stats.episodes.toString(), lx("حلقة", "Episodes"))
            LuxoPassStat("≈" + stats.hours, lx("ساعة", "Hours"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LuxoPassStat(stats.streakDays.toString(), lx("أطول سلسلة أيام", "Best streak"))
            LuxoPassStat(stats.ratings.toString(), lx("تقييم", "Ratings"))
            LuxoPassStat(stats.lists.toString(), lx("قائمة", "Lists"))
        }
        Text(
            "Luxo · " + AppVersionConfig.VERSION_NAME,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun RowScope.LuxoPassStat(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LuxoBarcode(seed: Int, modifier: Modifier) {
    Canvas(modifier) {
        var x = 0f
        var state = seed * 9301 + 49297
        while (x < size.width) {
            state = (state * 9301 + 49297) % 233280
            val w = 1f + (abs(state) % 4)
            drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w + 1f + (abs(state / 7) % 3)
        }
    }
}


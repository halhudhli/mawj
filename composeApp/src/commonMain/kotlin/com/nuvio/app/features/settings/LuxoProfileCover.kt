package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.luxo.LuxoPrefsStorage
import com.nuvio.app.features.luxo.LuxoUserData
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository

/**
 * The top of «الملف الشخصي», in the spirit of a streaming profile page: a wide cover taken from the
 * user's own titles (a tap moves to the next one), the profile picture and name, what has been
 * watched in numbers, and «قوائمي» — every list the user made, as a row of posters.
 */
@Composable
internal fun LuxoProfileCover(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    // «قوائمي» opens over the profile page, where lists are made, renamed and filled.
    var listsOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    val onListsClick = { listsOpen = true }
    if (listsOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { listsOpen = false },
            properties = com.nuvio.app.features.luxo.luxoFullScreenDialogProperties(),
        ) {
            Box(Modifier.fillMaxSize().background(tokens.colors.background)) {
                com.nuvio.app.features.luxo.LuxoListsScreen(modifier = Modifier.fillMaxSize(), onPosterClick = { listsOpen = false })
                Text(
                    lx("إغلاق", "Close"),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)).clickable { listsOpen = false }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
    val stats = rememberLuxoPassStats()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val profile = profileState.activeProfile
    val progress by remember { WatchProgressRepository.ensureLoaded(); WatchProgressRepository.uiState }.collectAsStateWithLifecycle()
    val userData by LuxoUserData.state.collectAsStateWithLifecycle()

    // Wide pictures from what was watched recently, then from the lists.
    val covers = remember(progress.entries, userData.lists) {
        (progress.entries.sortedByDescending { it.lastUpdatedEpochMs }.mapNotNull { it.background } +
            userData.lists.flatMap { list -> list.items.mapNotNull { it.banner } })
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)
    }
    var coverIndex by remember { mutableIntStateOf(LuxoPrefsStorage.load("profile_cover_index")?.toIntOrNull() ?: 0) }
    val cover = covers.getOrNull(if (covers.isEmpty()) 0 else coverIndex % covers.size)
    val accent = runCatching { Color(("FF" + (profile?.avatarColorHex ?: "#E5484D").removePrefix("#")).toLong(16)) }.getOrDefault(tokens.colors.accent)
    val side = if (isTablet) 0.dp else 16.dp

    Column(Modifier.fillMaxWidth().padding(horizontal = side)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (isTablet) 3.2f else 2.1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), tokens.colors.surface)))
                .clickable(enabled = covers.size > 1) {
                    coverIndex = (coverIndex + 1) % covers.size
                    LuxoPrefsStorage.save("profile_cover_index", coverIndex.toString())
                },
        ) {
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            if (covers.size > 1) {
                Text(
                    lx("يُضغط لتغيير الغلاف", "Tap to change the cover"),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(if (isTablet) 76.dp else 64.dp).clip(CircleShape).background(accent)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val url = profile?.avatarUrl
                    if (!url.isNullOrBlank()) {
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text((profile?.name ?: "م").take(1), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Column {
                    Text(profile?.name?.ifBlank { null } ?: lx("ملفي", "My profile"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        lx("${stats.movies} فيلم · ${stats.episodes} حلقة · ${stats.hours} ساعة", "${stats.movies} films · ${stats.episodes} episodes · ${stats.hours} h"),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lx("قوائمي", "My lists"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                lx("+ قائمة جديدة", "+ New list"),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(accent).clickable(onClick = onListsClick).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        val lists = userData.lists.filter { it.items.isNotEmpty() }
        if (lists.isEmpty()) {
            Text(
                lx("لا توجد قوائم بعد. تُنشأ القوائم من صفحة أي عمل أو من «قوائمي».", "No lists yet. Create one from any title or from «My lists»."),
                color = tokens.colors.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        lists.forEach { list ->
            Text("${list.emoji} ${list.name}", color = tokens.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).clickable(onClick = onListsClick))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.items.take(20).forEach { title ->
                    Box(Modifier.width(if (isTablet) 96.dp else 78.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(tokens.colors.surface).clickable(onClick = onListsClick)) {
                        if (!title.poster.isNullOrBlank()) {
                            AsyncImage(model = title.poster, contentDescription = title.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(title.name, color = tokens.colors.textSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center).padding(6.dp), maxLines = 3)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

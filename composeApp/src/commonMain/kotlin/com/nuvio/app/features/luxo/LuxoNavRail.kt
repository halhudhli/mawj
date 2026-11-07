package com.nuvio.app.features.luxo

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.FloatingNavigationItem
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.library.libraryUpcomingEpisodesFlow
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource

@Composable
fun rememberLuxoNavSettings(): LuxoNavSettings {
    val state by remember {
        LuxoNavSettingsRepository.ensureLoaded()
        LuxoNavSettingsRepository.state
    }.collectAsStateWithLifecycle()
    return state
}

/** How many followed episodes air today (for the library badge). */
@Composable
internal fun rememberLuxoTodayEpisodeCount(enabled: Boolean): Int {
    if (!enabled) return 0
    val today = remember { CurrentDateProvider.todayIsoDate() }
    val count by remember(today) {
        libraryUpcomingEpisodesFlow(days = 1).map { list -> list.count { it.dateIso.take(10) == today } }
    }.collectAsStateWithLifecycle(initialValue = 0)
    return count
}

@Composable
internal fun LuxoNavBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-4).dp)
            .widthIn(min = 16.dp)
            .height(16.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF4F6D))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Hides the navigation while the content scrolls down and brings it back on the way up. */
@Stable
class LuxoNavHideState {
    var hidden by mutableStateOf(false)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -6f) hidden = true else if (available.y > 6f) hidden = false
            return Offset.Zero
        }
    }
}

/** Width the content keeps clear for the collapsed rail. */
val LuxoRailInset: Dp = 88.dp

/**
 * A side rail on the reading-start edge (right in Arabic, left in English, thanks to RTL).
 * Collapsed it shows icons; tapping the arrow or swiping inward opens it with the labels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LuxoSideRail(
    items: List<FloatingNavigationItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val accent = tokens.colors.accent
    val muted = tokens.colors.textMuted
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Luxo TV: the rail never hides (a hidden rail could still take the remote's focus), and it
    // opens with names while the remote is on it, closing again when focus returns to the page.
    val tv = rememberLuxoTvRemote()
    var railFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val hidden = hidden && !tv
    val expanded = if (tv) expanded || railFocused else expanded
    val width by animateDpAsState(if (expanded) 212.dp else 68.dp, spring(dampingRatio = 0.82f, stiffness = 420f), label = "rail_width")
    val hide by animateFloatAsState(if (hidden) 1f else 0f, tween(260), label = "rail_hide")
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(30.dp)
    val tabs = items.filter { it.content == null }
    val profile = items.firstOrNull { it.content != null }

    Box(
        modifier = modifier
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(top = topInset + 12.dp, bottom = bottomInset + 12.dp)
            .padding(horizontal = 10.dp)
            .graphicsLayer {
                translationX = (if (isRtl) 260f else -260f) * hide * density
                alpha = 1f - hide
            },
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(tokens.colors.surface.copy(alpha = 0.92f), tokens.colors.background.copy(alpha = 0.88f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                .pointerInput(isRtl) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            val inward = if (isRtl) -total else total
                            if (inward > 40f) onExpandedChange(true) else if (inward < -40f) onExpandedChange(false)
                        },
                    ) { _, delta -> total += delta }
                }
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!tv) Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = { onExpandedChange(!expanded) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = muted,
                )
            }
            Spacer(Modifier.height(4.dp))
            tabs.forEach { item ->
                val selected = item.selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .luxoTvFocus(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .combinedClickable(onClick = item.onClick, onLongClick = item.onLongClick)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(24.dp)) {
                        val tint = if (selected) accent else muted
                        when {
                            item.icon != null -> Icon(item.icon, item.label, Modifier.size(24.dp), tint = tint)
                            item.drawable != null -> Icon(painterResource(item.drawable), item.label, Modifier.size(24.dp), tint = tint)
                        }
                        LuxoNavBadge(item.badge, Modifier.align(Alignment.TopEnd))
                    }
                    if (expanded) {
                        Text(
                            text = item.label,
                            color = if (selected) accent else tokens.colors.textPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (profile != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        profile.content?.invoke(profile.onClick, null)
                    }
                    if (expanded) {
                        Text(profile.label, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

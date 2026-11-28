package com.nuvio.app.features.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.MawjTint
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.features.luxo.lx
import com.nuvio.app.features.luxo.rememberLuxoFeatures
import com.nuvio.app.features.player.seekpreview.SeekPreviewController
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_audio
import nuvio.composeapp.generated.resources.compose_player_close
import nuvio.composeapp.generated.resources.compose_player_lock_controls
import nuvio.composeapp.generated.resources.compose_player_picture_in_picture
import nuvio.composeapp.generated.resources.compose_player_subtitles
import nuvio.composeapp.generated.resources.compose_player_unlock_controls
import nuvio.composeapp.generated.resources.party_title
import nuvio.composeapp.generated.resources.player_cast
import org.jetbrains.compose.resources.stringResource

/*
 * «تصميم مشغّل موج» (flag mawjPlayerLook): the tablet player's look only. Every button calls the
 * same action as before; only where things sit and how they are drawn changes:
 *  - the name and episode at the top on the reading side with the back button beside it, the touch
 *    buttons (lock, cast, picture in picture, watch party) on the other side;
 *  - a thin gold ring around play showing how much has been watched;
 *  - one glass «رصيف» at the bottom: the time line, then the main tools by name, «ينتهي» and the
 *    next episode;
 *  - «المزيد» opens a sheet with every other tool, named and grouped.
 */

private val DockInk = Color(0xFFF4F1EA)
private val DockMuted = Color(0xFFA9B6C4)
private val DockGlass = Color(0xFF0B1520)

@Composable
internal fun rememberMawjPlayerLook(): Boolean = rememberLuxoFeatures().mawjPlayerLook

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MawjPlayerChrome(
    title: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    snapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isLocked: Boolean,
    showPlaybackControls: Boolean,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    onInteraction: () -> Unit,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    onPartyClick: (() -> Unit)?,
    partyActive: Boolean,
    onCastClick: (() -> Unit)?,
    isCasting: Boolean,
    onPictureInPictureClick: (() -> Unit)?,
    onOrientationToggleClick: (() -> Unit)?,
    isPortraitPlayback: Boolean,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    skipBackwardSeconds: Int,
    skipForwardSeconds: Int,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSpeedClick: () -> Unit,
    speedPickerVisible: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSpeedPickerDismiss: () -> Unit,
    onResizeModeClick: () -> Unit,
    onNextEpisodeClick: (() -> Unit)?,
    actions: Pair<List<PlayerControlAction>, List<PlayerControlAction>>,
    parentalWarnings: List<ParentalWarning>,
    showParentalGuide: Boolean,
    onParentalGuideAnimationComplete: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    seekPreview: SeekPreviewController?,
    onTimelineTouchChange: (Boolean) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    DisposableEffect(sheetOpen) {
        if (sheetOpen) LuxoPlayerActionsState.expanded = true
        onDispose { if (sheetOpen) LuxoPlayerActionsState.expanded = false }
    }
    PlatformBackHandler(enabled = sheetOpen) { sheetOpen = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * 0.72f
        // The veil: soft at the top for the name, deeper at the bottom for the dock.
        Box(
            Modifier.fillMaxWidth().height(190.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent))),
        )
        Box(
            Modifier.fillMaxWidth().height(300.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.74f)))),
        )

        // ── top: back + name on the reading side, touch buttons on the other ──
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(playerToolbarTopInsets(metrics))
                .padding(horizontal = metrics.horizontalPadding),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                GlassCircle(
                    onClick = onBack,
                    description = stringResource(Res.string.compose_player_close),
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(top = 2.dp)) {
                    val line = listOfNotNull(
                        seasonNumber?.let { lx("الموسم $it", "Season $it") },
                        episodeNumber?.let { lx("الحلقة $it", "Episode $it") },
                    ).joinToString(" · ")
                    if (line.isNotEmpty()) {
                        Text(line, color = DockMuted, fontSize = 13.sp, maxLines = 1)
                    }
                    val swash = rememberLuxoFeatures().swashTitles && title.any { it in '؀'..'ۿ' }
                    Text(
                        title,
                        color = DockInk,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (swash) com.nuvio.app.features.luxo.rememberMawjSwash() else FontFamily.Serif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    episodeTitle?.takeIf { it.isNotBlank() && it != title }?.let {
                        Text(
                            "«$it»",
                            color = MawjTint.gold,
                            fontSize = 15.sp,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ParentalGuideOverlay(
                        warnings = parentalWarnings,
                        isVisible = showParentalGuide,
                        onAnimationComplete = onParentalGuideAnimationComplete,
                        contentPadding = PaddingValues(top = 8.dp),
                    )
                }
                if (showPlaybackControls) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCircle(
                            onClick = onLockToggle,
                            description = stringResource(if (isLocked) Res.string.compose_player_unlock_controls else Res.string.compose_player_lock_controls),
                            icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        )
                        onCastClick?.let {
                            GlassCircle(it, stringResource(Res.string.player_cast), if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast, gold = isCasting)
                        }
                        onPictureInPictureClick?.let {
                            GlassCircle(it, stringResource(Res.string.compose_player_picture_in_picture), Icons.Rounded.PictureInPictureAlt)
                        }
                        onPartyClick?.let {
                            GlassCircle(it, stringResource(Res.string.party_title), Icons.Rounded.Groups, gold = partyActive)
                        }
                        onOrientationToggleClick?.let {
                            GlassCircle(it, stringResource(playerOrientationToggleLabel(isPortraitPlayback)), playerOrientationToggleIcon(isPortraitPlayback))
                        }
                    }
                }
            }
        }

        if (showPlaybackControls) {
            // ── centre: the play button inside a thin gold ring of how much has been watched ──
            val watched = if (snapshot.durationMs > 0) (displayedPositionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f
            val gold = MawjTint.gold
            val ring = metrics.playIconSize + metrics.playButtonPadding * 2 + 14.dp
            Box(Modifier.align(Alignment.Center).padding(bottom = 36.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(ring)) {
                    val w = 2.dp.toPx()
                    val inset = w / 2
                    val arcSize = Size(size.width - w, size.height - w)
                    drawArc(Color.White.copy(alpha = 0.16f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                    drawArc(gold, -90f, 360f * watched, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    CenterControls(
                        snapshot = snapshot,
                        metrics = metrics,
                        skipBackwardSeconds = skipBackwardSeconds,
                        skipForwardSeconds = skipForwardSeconds,
                        onSeekBack = { onInteraction(); onSeekBack() },
                        onSeekForward = { onInteraction(); onSeekForward() },
                        onTogglePlayback = { onInteraction(); onTogglePlayback() },
                    )
                }
            }

            // ── bottom: the glass dock ──
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(playerTimelineBottomInsets(metrics))
                    .padding(horizontal = metrics.horizontalPadding)
                    .clip(RoundedCornerShape(26.dp))
                    .background(DockGlass.copy(alpha = 0.66f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Column {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatPlaybackTime(displayedPositionMs), color = DockInk, fontSize = 13.sp, maxLines = 1)
                            Spacer(Modifier.width(12.dp))
                            PlayerTimeline(
                                snapshot = snapshot,
                                displayedPositionMs = displayedPositionMs,
                                onScrubChange = onScrubChange,
                                onScrubFinished = { onInteraction(); onScrubFinished(it) },
                                seekPreview = seekPreview,
                                onTouchChange = onTimelineTouchChange,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (showRemainingTime) formatPlaybackRemaining(displayedPositionMs, snapshot.durationMs) else formatPlaybackTime(snapshot.durationMs),
                                color = DockMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { onRuntimeClick(); onInteraction() }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolChip(
                                label = stringResource(Res.string.compose_player_subtitles),
                                painter = appIconPainter(AppIconResource.PlayerSubtitles),
                                onClick = { onInteraction(); onSubtitleClick() },
                            )
                            ToolChip(
                                label = stringResource(Res.string.compose_player_audio),
                                painter = appIconPainter(AppIconResource.PlayerAudioFilled),
                                onClick = { onInteraction(); onAudioClick() },
                            )
                            Box {
                                ToolChip(
                                    label = lx("السرعة", "Speed"),
                                    value = formatPlaybackSpeedLabel(snapshot.playbackSpeed).replace("x", "×"),
                                    icon = Icons.Rounded.Speed,
                                    onClick = { onInteraction(); onSpeedClick() },
                                )
                                PlayerSpeedPopup(
                                    visible = speedPickerVisible,
                                    currentSpeed = snapshot.playbackSpeed,
                                    onSpeedSelected = onSpeedSelected,
                                    onDismiss = onSpeedPickerDismiss,
                                )
                            }
                            ToolChip(
                                label = lx("الأبعاد", "Aspect"),
                                value = stringResource(resizeMode.labelRes),
                                painter = appIconPainter(AppIconResource.PlayerAspectRatio),
                                onClick = { onInteraction(); onResizeModeClick() },
                            )
                            if (actions.second.isNotEmpty()) {
                                ToolChip(
                                    label = lx("المزيد", "More"),
                                    icon = Icons.Rounded.AutoAwesome,
                                    gold = true,
                                    onClick = { onInteraction(); sheetOpen = true },
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        PlayerEndTimeLabel(
                            positionMs = displayedPositionMs,
                            durationMs = snapshot.durationMs,
                            playbackSpeed = snapshot.playbackSpeed,
                            isPlaying = snapshot.isPlaying,
                            metrics = metrics,
                        )
                        onNextEpisodeClick?.let { next ->
                            Spacer(Modifier.width(8.dp))
                            ToolChip(
                                label = lx("الحلقة التالية", "Next episode"),
                                icon = Icons.Rounded.SkipNext,
                                onClick = { onInteraction(); next() },
                            )
                        }
                    }
                }
            }
        }

        // ── «المزيد»: every other tool, named and grouped ──
        if (sheetOpen) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sheetOpen = false },
            )
            val audioLabel = stringResource(Res.string.compose_player_audio)
            // The rest of the tools, and the chapters from the main strip; the audio tool is already on the dock.
            val all = actions.second.filter { it.description != audioLabel } +
                actions.first.filter { it.icon == Icons.AutoMirrored.Rounded.ListAlt }
            val mate = all.firstOrNull { it.group == MawjToolGroup.Mate }
            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 1000.dp).fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(DockGlass.copy(alpha = 0.95f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .heightIn(max = sheetMax)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(44.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lx("أدوات موج", "Mawj tools"), color = DockInk, fontSize = 21.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                    mate?.let { m ->
                        Row(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .border(1.dp, MawjTint.gold.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                .clickable(role = Role.Button) { sheetOpen = false; onInteraction(); m.onClick() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MawjTint.gold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(m.description, color = DockInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(lx("سؤال عن المشهد دون حرق", "Ask about the scene, no spoilers"), color = DockMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(MawjToolGroup.Subtitles, MawjToolGroup.Picture, MawjToolGroup.Moment, MawjToolGroup.Watch).forEach { group ->
                        val items = all.filter { it.group == group }
                        if (items.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(group.title, color = MawjTint.gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items.forEach { a ->
                                        ToolTile(a) {
                                            sheetOpen = false
                                            onInteraction()
                                            a.onClick()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun GlassCircle(onClick: () -> Unit, description: String, icon: ImageVector, gold: Boolean = false) {
    Box(
        Modifier.size(46.dp).clip(CircleShape)
            .background(DockGlass.copy(alpha = 0.55f))
            .border(1.dp, if (gold) MawjTint.gold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (gold) MawjTint.gold else DockInk, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolChip(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    gold: Boolean = false,
) {
    val tint = if (gold) MawjTint.gold else DockInk
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (gold) MawjTint.gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (gold) MawjTint.gold.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            painter != null -> Icon(painter, null, tint = tint, modifier = Modifier.size(20.dp))
            icon != null -> Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            value?.let { Text(it, color = DockMuted, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun ToolTile(action: PlayerControlAction, onClick: () -> Unit) {
    val (name, state) = action.description.split(" · ", limit = 2).let { it[0] to it.getOrNull(1) }
    val on = action.on
    val tint = if (on) MawjTint.gold else DockInk
    Column(
        Modifier.width(124.dp).heightIn(min = 84.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) MawjTint.gold.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (on) MawjTint.gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            action.painter != null -> Icon(action.painter, null, tint = tint, modifier = Modifier.size(24.dp))
            action.icon != null -> Icon(action.icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(name, color = DockInk, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        state?.let { Text(it, color = if (on) MawjTint.gold else DockMuted, fontSize = 10.sp, maxLines = 1) }
    }
}

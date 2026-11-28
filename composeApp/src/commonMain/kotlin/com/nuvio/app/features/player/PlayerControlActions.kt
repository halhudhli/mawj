package com.nuvio.app.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun playerOrientationToggleIcon(isPortraitPlayback: Boolean): ImageVector =
    if (isPortraitPlayback) Icons.Rounded.StayCurrentLandscape else Icons.Rounded.StayCurrentPortrait

internal fun playerOrientationToggleLabel(isPortraitPlayback: Boolean): StringResource =
    if (isPortraitPlayback) Res.string.player_action_rotate_landscape else Res.string.player_action_rotate_portrait

@Composable
internal fun PlayerToolbar(
    isLocked: Boolean,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    onCastClick: (() -> Unit)? = null,
    isCasting: Boolean = false,
    modifier: Modifier = Modifier,
    onPartyClick: (() -> Unit)? = null,
    partyActive: Boolean = false,
    onOrientationToggleClick: (() -> Unit)? = null,
    isPortraitPlayback: Boolean = false,
    onPictureInPictureClick: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        // Toolbar actions share one liquid-glass capsule; close sits in its own glass circle.
        Row(
            modifier = Modifier.playerLiquidGlass(
                shape = PlayerGlassCapsule,
                refractionHeight = 8.dp,
                refractionAmount = 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onPictureInPictureClick?.let {
                PlayerAction(
                    description = stringResource(Res.string.compose_player_picture_in_picture),
                    icon = Icons.Rounded.PictureInPictureAlt,
                    onClick = it,
                )
            }
            onOrientationToggleClick?.let {
                PlayerAction(
                    description = stringResource(playerOrientationToggleLabel(isPortraitPlayback)),
                    icon = playerOrientationToggleIcon(isPortraitPlayback),
                    onClick = it,
                )
            }
            if (onPartyClick != null) {
                PlayerAction(
                    description = stringResource(Res.string.party_title),
                    icon = Icons.Rounded.Groups,
                    onClick = onPartyClick,
                    tint = if (partyActive) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            onCastClick?.let {
                PlayerAction(
                    description = stringResource(Res.string.player_cast),
                    icon = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    onClick = it,
                )
            }
            PlayerAction(
                description = stringResource(
                    if (isLocked) Res.string.compose_player_unlock_controls else Res.string.compose_player_lock_controls,
                ),
                icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                onClick = onLockToggle,
            )
        }
        Spacer(Modifier.width(8.dp))
        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier.playerLiquidGlass(CircleShape, refractionHeight = 8.dp, refractionAmount = 12.dp),
            shape = CircleShape,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            buttonSize = 48.dp,
            iconSize = 24.dp,
            contentDescription = stringResource(Res.string.compose_player_close),
        )
    }
}

private const val ShowUpstreamStreamInfoAction = false

@Composable
internal fun PlayerControlActions(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    showRemainingTime: Boolean,
    onRuntimeClick: () -> Unit,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)? = null,
    onNextEpisodeClick: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    speedPickerVisible: Boolean = false,
    onSpeedSelected: (Float) -> Unit = {},
    onSpeedPickerDismiss: () -> Unit = {},
    onResizeModeClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onOpenInExternalPlayer: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onSubmitIntroClick: (() -> Unit)?,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    onSwitchEngineClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    onRateClick: (() -> Unit)? = null,
    userRating: Int? = null,
    onInteraction: () -> Unit,
) {
    val (primaryActions, secondaryActions) = playerActionLists(
        playbackSnapshot, resizeMode, onSubtitleClick, onAudioClick, onSourcesClick, onEpisodesClick, onChaptersClick,
        onNextEpisodeClick, onSpeedClick, speedPickerVisible, onSpeedSelected, onSpeedPickerDismiss, onResizeModeClick,
        onVideoSettingsClick, onOpenInExternalPlayer, onStreamInfoClick, onSubmitIntroClick, qualityLabel, onQualityClick,
        onSwitchEngineClick, onInfoClick, onRateClick, userRating,
    )
    val hasOverflow = secondaryActions.isNotEmpty()
    var expanded by remember(hasOverflow) { mutableStateOf(false) }
    // While the extra actions are open the controls stay on screen (like the rest of the strip) until
    // the screen is tapped; before, the 3.5 s auto-hide closed them mid-choice.
    androidx.compose.runtime.DisposableEffect(expanded) {
        LuxoPlayerActionsState.expanded = expanded
        onDispose { LuxoPlayerActionsState.expanded = false }
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(expanded, scrollState.maxValue) {
        if (expanded) scrollState.animateScrollTo(scrollState.maxValue) else scrollState.scrollTo(0)
    }
    // Mawj: dragging the strip (or opening its extra buttons) counts as activity, so the controls
    // do not hide while the buttons are being browsed.
    LaunchedEffect(scrollState, expanded) {
        androidx.compose.runtime.snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
            .collect { onInteraction() }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerTimelineContentInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .playerLiquidGlass(
                            shape = PlayerGlassCapsule,
                            refractionHeight = 8.dp,
                            refractionAmount = 12.dp,
                        )
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (if (expanded) primaryActions + secondaryActions else primaryActions).forEach { action ->
                        Box {
                            PlayerAction(
                                description = action.description,
                                onClick = {
                                    onInteraction()
                                    action.onClick()
                                },
                                icon = action.icon,
                                painter = action.painter,
                                iconSize = action.iconSize,
                            )
                            action.popup?.invoke()
                        }
                    }
                    if (hasOverflow) {
                        PlayerAction(
                            description = stringResource(
                                if (expanded) Res.string.compose_player_fewer_actions else Res.string.compose_player_more_actions,
                            ),
                            onClick = {
                                expanded = !expanded
                                onInteraction()
                            },
                            icon = if (expanded) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .widthIn(min = 48.dp)
                    .playerLiquidGlass(
                        shape = PlayerGlassCapsule,
                        refractionHeight = 6.dp,
                        refractionAmount = 8.dp,
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(
                            if (showRemainingTime) Res.string.compose_player_show_elapsed_time else Res.string.compose_player_show_remaining_time,
                        ),
                        onClick = {
                            onRuntimeClick()
                            onInteraction()
                        },
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatPlaybackRuntime(displayedPositionMs, playbackSnapshot.durationMs, showRemainingTime),
                    style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontSize = (metrics.timeSize.value + 2).sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Every player action, the main strip ([first]) and the rest ([second]); shared by both looks. */
@Composable
internal fun playerActionLists(
    playbackSnapshot: PlayerPlaybackSnapshot,
    resizeMode: PlayerResizeMode,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSourcesClick: (() -> Unit)?,
    onEpisodesClick: (() -> Unit)?,
    onChaptersClick: (() -> Unit)?,
    onNextEpisodeClick: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    speedPickerVisible: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onSpeedPickerDismiss: () -> Unit,
    onResizeModeClick: () -> Unit,
    onVideoSettingsClick: (() -> Unit)?,
    onOpenInExternalPlayer: (() -> Unit)?,
    onStreamInfoClick: () -> Unit,
    onSubmitIntroClick: (() -> Unit)?,
    qualityLabel: String?,
    onQualityClick: (() -> Unit)?,
    onSwitchEngineClick: (() -> Unit)?,
    onInfoClick: (() -> Unit)?,
    onRateClick: (() -> Unit)?,
    userRating: Int?,
): Pair<List<PlayerControlAction>, List<PlayerControlAction>> {
    val primaryActions = listOfNotNull(
        onNextEpisodeClick?.let {
            PlayerControlAction(
                stringResource(Res.string.player_next_episode), it,
                icon = Icons.Rounded.SkipNext, iconSize = 40.dp,
            )
        },
        PlayerControlAction(
            "${stringResource(Res.string.compose_player_speed)} ${formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed)}",
            onSpeedClick, icon = Icons.Rounded.Speed,
            popup = {
                PlayerSpeedPopup(
                    visible = speedPickerVisible,
                    currentSpeed = playbackSnapshot.playbackSpeed,
                    onSpeedSelected = onSpeedSelected,
                    onDismiss = onSpeedPickerDismiss,
                )
            },
        ),
        PlayerControlAction(
            stringResource(resizeMode.labelRes), onResizeModeClick,
            painter = appIconPainter(AppIconResource.PlayerAspectRatio),
        ),
        PlayerControlAction(
            stringResource(Res.string.compose_player_subtitles), onSubtitleClick,
            painter = appIconPainter(AppIconResource.PlayerSubtitles),
        ),
        onChaptersClick?.let {
            PlayerControlAction(
                stringResource(Res.string.player_chapters), it,
                icon = Icons.AutoMirrored.Rounded.ListAlt,
            )
        },
    )
    val luxoSleepLabel = LuxoPlayerBridge.sleepLabel
    val secondaryActions = listOfNotNull(
        LuxoPlayerBridge.onCompanionClick?.let {
            PlayerControlAction("رفيق موج", it, icon = Icons.Rounded.AutoAwesome, group = MawjToolGroup.Mate, on = false)
        },
        LuxoPlayerBridge.onColorsClick?.let {
            PlayerControlAction("ألوان الصورة", it, icon = Icons.Rounded.Palette, group = MawjToolGroup.Picture, on = false)
        },
        LuxoPlayerBridge.onSubtitleFileClick?.let {
            PlayerControlAction("ترجمة من ملف", it, icon = Icons.Rounded.UploadFile, group = MawjToolGroup.Subtitles, on = false)
        },
        LuxoPlayerBridge.onPictureClick?.let {
            PlayerControlAction("تحسين الصورة", it, icon = Icons.Rounded.AutoFixHigh, group = MawjToolGroup.Picture, on = false)
        },
        LuxoPlayerBridge.onSoundClick?.let {
            PlayerControlAction("تحسين الصوت", it, icon = Icons.Rounded.GraphicEq, group = MawjToolGroup.Picture, on = false)
        },
        LuxoPlayerBridge.onEncodingClick?.let {
            PlayerControlAction("ترميز الترجمة · ${MawjSubtitleEncoding.mode.value.label}", it, icon = Icons.Rounded.TextFormat, group = MawjToolGroup.Subtitles, on = false)
        },
        LuxoPlayerBridge.onTranslateSubtitlesClick?.let {
            PlayerControlAction(LuxoPlayerBridge.translateLabel, it, icon = Icons.Rounded.Translate, group = MawjToolGroup.Subtitles, on = false)
        },
        LuxoPlayerBridge.onBookmarkClick?.let {
            PlayerControlAction("علامة هنا", it, icon = Icons.Rounded.BookmarkAdd, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onBookmarksListClick?.let {
            PlayerControlAction("العلامات", it, icon = Icons.Rounded.Bookmarks, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onDialogueClick?.let {
            PlayerControlAction(if (com.nuvio.app.features.luxo.LuxoAudioFx.dialogue) "توضيح الحوار · مفعّل" else "توضيح الحوار", it, icon = Icons.Rounded.RecordVoiceOver, group = MawjToolGroup.Picture, on = com.nuvio.app.features.luxo.LuxoAudioFx.dialogue)
        },
        LuxoPlayerBridge.onNightAudioClick?.let {
            PlayerControlAction(if (com.nuvio.app.features.luxo.LuxoAudioFx.night) "صوت ليلي · مفعّل" else "صوت ليلي", it, icon = Icons.Rounded.NightsStay, group = MawjToolGroup.Picture, on = com.nuvio.app.features.luxo.LuxoAudioFx.night)
        },
        LuxoPlayerBridge.onShareSceneClick?.let {
            PlayerControlAction("مشاركة المشهد", it, icon = Icons.Rounded.Share, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onSaveQuoteClick?.let {
            PlayerControlAction("حفظ العبارة", it, icon = Icons.Rounded.FormatQuote, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onGifClick?.let {
            PlayerControlAction(LuxoPlayerBridge.gifLabel, it, icon = Icons.Rounded.Gif, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onWallpaperClick?.let {
            PlayerControlAction("خلفية من المشهد", it, icon = Icons.Rounded.Palette, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onQuizClick?.let {
            PlayerControlAction("اختبار الحلقة", it, icon = Icons.Rounded.AutoAwesome, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onWhoInSceneClick?.let {
            PlayerControlAction("من في المشهد؟", it, icon = Icons.Rounded.Groups, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onContinueOnTvClick?.let {
            PlayerControlAction("أكمل على الشاشة", it, icon = Icons.Rounded.Tv, group = MawjToolGroup.Watch, on = false)
        },
        LuxoPlayerBridge.onScreenshotClick?.let {
            PlayerControlAction("لقطة شاشة", it, icon = Icons.Rounded.PhotoCamera, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onAbLoopClick?.let {
            PlayerControlAction(LuxoPlayerBridge.abLoopLabel, it, icon = Icons.Rounded.Repeat, group = MawjToolGroup.Moment, on = false)
        },
        LuxoPlayerBridge.onSleepTimerClick?.let {
            PlayerControlAction(
                luxoSleepLabel?.let { label -> "مؤقت النوم · $label" } ?: "مؤقت النوم",
                it,
                icon = Icons.Rounded.Bedtime,
                group = MawjToolGroup.Watch,
                on = luxoSleepLabel != null,
            )
        },
        PlayerControlAction(
            stringResource(Res.string.compose_player_audio), onAudioClick,
            painter = appIconPainter(AppIconResource.PlayerAudioFilled),
            group = MawjToolGroup.Picture,
        ),
        onQualityClick?.let {
            PlayerControlAction(
                qualityLabel?.takeIf { label -> label.isNotBlank() } ?: "Quality", it,
                icon = Icons.Rounded.HighQuality,
                group = MawjToolGroup.Picture,
            )
        },
        onSourcesClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_sources), it,
                painter = appIconPainter(AppIconResource.PlayerSource),
            )
        },
        onEpisodesClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_episodes), it,
                painter = appIconPainter(AppIconResource.PlayerEpisodes),
            )
        },
        onRateClick?.let {
            PlayerControlAction(
                userRating?.let { rating -> stringResource(Res.string.user_rating_action_rated, rating) }
                    ?: stringResource(Res.string.user_rating_action_rate),
                it,
                icon = if (userRating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            )
        },
        onOpenInExternalPlayer?.let {
            PlayerControlAction(
                stringResource(Res.string.streams_open_external_player), it,
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        },
        onInfoClick?.let {
            PlayerControlAction(
                stringResource(Res.string.compose_player_playback_info), it,
                icon = Icons.Rounded.Info,
            )
        },
        onStreamInfoClick.takeIf { ShowUpstreamStreamInfoAction }?.let {
            PlayerControlAction(
                stringResource(Res.string.cd_stream_info), onStreamInfoClick,
                icon = Icons.Rounded.Info,
            )
        },
        onSwitchEngineClick?.let {
            PlayerControlAction(
                stringResource(Res.string.cd_switch_player_engine), it,
                icon = Icons.Rounded.SwapHoriz,
            )
        },
        onVideoSettingsClick?.let {
            PlayerControlAction(
                stringResource(Res.string.player_action_video_settings), it,
                icon = Icons.Rounded.Build,
            )
        },
        onSubmitIntroClick?.let {
            PlayerControlAction(
                stringResource(Res.string.submit_intro_action), it,
                icon = Icons.Rounded.Flag,
            )
        },
    )
    return primaryActions to secondaryActions
}

@Composable
private fun PlayerAction(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        if (painter != null) {
            Icon(painter, description, tint = tint, modifier = Modifier.size(iconSize))
        } else if (icon != null) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

internal val PlayerGlassCapsule = RoundedCornerShape(percent = 50)

internal data class PlayerControlAction(
    val description: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val painter: Painter? = null,
    val iconSize: Dp = 24.dp,
    val popup: (@Composable () -> Unit)? = null,
    /** Where the action sits in Mawj's «المزيد» sheet. */
    val group: MawjToolGroup = MawjToolGroup.Watch,
    /** Shown in gold there (a tool that is on). */
    val on: Boolean = false,
)

/** The groups of Mawj's «المزيد» sheet. */
internal enum class MawjToolGroup(val title: String) {
    Mate("رفيق موج"),
    Subtitles("الترجمة"),
    Picture("الصوت والصورة"),
    Moment("اللحظة"),
    Watch("المشاهدة"),
}


/** Whether the player's extra actions ("more") are open; the auto-hide waits while they are. */
internal object LuxoPlayerActionsState {
    var expanded by androidx.compose.runtime.mutableStateOf(false)
}

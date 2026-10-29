package com.nuvio.app.features.player

import androidx.compose.foundation.focusable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.MutableState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * A hardware-keyboard action the player understands, independent of which layer recognised the
 * key. Compose maps key events to these directly; on iOS the mpv view controller maps its own
 * `UIPress` events to [wireCode] and hands them back, so both paths end up in the same runtime
 * actions instead of each driving the player on its own.
 */
enum class PlayerKeyboardShortcut(val wireCode: String) {
    TogglePlayback("toggle_playback"),
    SeekBackward("seek_backward"),
    SeekForward("seek_forward"),
    Exit("exit"),
    // Luxo (TV remote): up/down/OK on the bare video show the controls so the remote can reach them.
    ShowControls("show_controls"),
    ;

    companion object {
        fun fromWireCode(code: String): PlayerKeyboardShortcut? =
            entries.firstOrNull { it.wireCode == code }
    }
}

/**
 * Handles hardware-keyboard shortcuts on the player surface.
 *
 * The modifier makes the node focusable and holds [focusRequester], because Compose only delivers
 * key events to the focused subtree and overlays take focus away while they are open.
 *
 * [enabledState] is read inside the handler rather than captured, so toggling it never rebuilds
 * the modifier chain — a rebuild would drop focus mid-playback. While it is false the handler
 * returns false, leaving the keys to whatever overlay currently owns them (a subtitle search
 * field, for instance).
 */
internal fun Modifier.playerKeyboardShortcuts(
    focusRequester: FocusRequester,
    enabledState: State<Boolean>,
    onShortcutState: State<(PlayerKeyboardShortcut) -> Unit>,
    // Luxo: true only while the video surface itself holds focus. When the remote has moved focus
    // onto a control button, the arrows must move between buttons instead of seeking.
    selfFocused: MutableState<Boolean>,
    // Luxo: moves focus from the video into the control buttons (after they appear).
    onEnterControls: State<() -> Unit>,
): Modifier {
    return onFocusChanged { selfFocused.value = it.isFocused }
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !enabledState.value) return@onPreviewKeyEvent false
            val onSurface = selfFocused.value
            val shortcut = when (event.key) {
                Key.Spacebar, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> PlayerKeyboardShortcut.TogglePlayback
                Key.MediaFastForward -> PlayerKeyboardShortcut.SeekForward
                Key.MediaRewind -> PlayerKeyboardShortcut.SeekBackward
                Key.Escape -> PlayerKeyboardShortcut.Exit
                Key.DirectionLeft -> if (onSurface) PlayerKeyboardShortcut.SeekBackward else null
                Key.DirectionRight -> if (onSurface) PlayerKeyboardShortcut.SeekForward else null
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> if (onSurface) PlayerKeyboardShortcut.TogglePlayback else null
                Key.DirectionUp, Key.DirectionDown -> if (onSurface) PlayerKeyboardShortcut.ShowControls else null
                else -> null
            } ?: return@onPreviewKeyEvent false
            onShortcutState.value(shortcut)
            if (shortcut == PlayerKeyboardShortcut.ShowControls) onEnterControls.value()
            true
        }
}

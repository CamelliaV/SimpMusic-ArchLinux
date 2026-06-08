package com.maxrave.simpmusic

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import java.awt.event.KeyEvent as AwtKeyEvent

enum class DesktopPlaybackKey {
    Left,
    Right,
    Space,
    P,
}

enum class DesktopPlaybackShortcutAction {
    SeekBackward,
    SeekForward,
    PreviousTrack,
    NextTrack,
    PlayPause,
}

fun playbackShortcutActionForWindowKey(
    key: DesktopPlaybackKey,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isMetaPressed: Boolean = false,
): DesktopPlaybackShortcutAction? =
    when (key) {
        DesktopPlaybackKey.Left ->
            if (isCtrlPressed && isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.PreviousTrack
            } else if (!isCtrlPressed && !isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.SeekBackward
            } else {
                null
            }

        DesktopPlaybackKey.Right ->
            if (isCtrlPressed && isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.NextTrack
            } else if (!isCtrlPressed && !isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.SeekForward
            } else {
                null
            }

        DesktopPlaybackKey.Space ->
            if (!isCtrlPressed && !isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.PlayPause
            } else {
                null
            }

        DesktopPlaybackKey.P ->
            if (isCtrlPressed && isAltPressed && !isMetaPressed) {
                DesktopPlaybackShortcutAction.PlayPause
            } else {
                null
            }
    }

fun DesktopPlaybackShortcutAction.toUIEvent(): UIEvent =
    when (this) {
        DesktopPlaybackShortcutAction.SeekBackward -> UIEvent.Backward
        DesktopPlaybackShortcutAction.SeekForward -> UIEvent.Forward
        DesktopPlaybackShortcutAction.PreviousTrack -> UIEvent.SkipToPrevious
        DesktopPlaybackShortcutAction.NextTrack -> UIEvent.Next
        DesktopPlaybackShortcutAction.PlayPause -> UIEvent.PlayPause
    }

fun Key.toDesktopPlaybackKey(): DesktopPlaybackKey? =
    when (this) {
        Key.DirectionLeft -> DesktopPlaybackKey.Left
        Key.DirectionRight -> DesktopPlaybackKey.Right
        Key.Spacebar -> DesktopPlaybackKey.Space
        Key.P -> DesktopPlaybackKey.P
        else -> null
    }

fun Int.toDesktopPlaybackKeyFromAwt(): DesktopPlaybackKey? =
    when (this) {
        AwtKeyEvent.VK_LEFT -> DesktopPlaybackKey.Left
        AwtKeyEvent.VK_RIGHT -> DesktopPlaybackKey.Right
        AwtKeyEvent.VK_SPACE -> DesktopPlaybackKey.Space
        AwtKeyEvent.VK_P -> DesktopPlaybackKey.P
        else -> null
    }

fun playbackShortcutActionForAwtWindowKey(
    keyCode: Int,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isSuperKeyPressed: Boolean = false,
): DesktopPlaybackShortcutAction? {
    val key = keyCode.toDesktopPlaybackKeyFromAwt() ?: return null
    return playbackShortcutActionForWindowKey(
        key = key,
        isCtrlPressed = isCtrlPressed,
        isAltPressed = isAltPressed,
        isMetaPressed = isMetaPressed || isSuperKeyPressed,
    )
}

fun globalPlaybackShortcutActionForKey(key: DesktopPlaybackKey): DesktopPlaybackShortcutAction? =
    when (key) {
        DesktopPlaybackKey.Left -> DesktopPlaybackShortcutAction.PreviousTrack
        DesktopPlaybackKey.Right -> DesktopPlaybackShortcutAction.NextTrack
        DesktopPlaybackKey.P -> DesktopPlaybackShortcutAction.PlayPause
        DesktopPlaybackKey.Space -> null
    }

fun dispatchDesktopPlaybackShortcut(
    keyEvent: KeyEvent,
    sharedViewModel: SharedViewModel,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    val key = keyEvent.key.toDesktopPlaybackKey() ?: return false
    val action =
        playbackShortcutActionForWindowKey(
            key = key,
            isCtrlPressed = keyEvent.isCtrlPressed,
            isAltPressed = keyEvent.isAltPressed,
            isMetaPressed = keyEvent.isMetaPressed,
        ) ?: return false

    sharedViewModel.onUIEvent(action.toUIEvent())
    return true
}

package com.maxrave.simpmusic

import java.awt.event.KeyEvent

enum class DesktopWindowUiShortcutAction {
    ToggleNowPlayingPanel,
}

fun desktopWindowUiShortcutActionForAwtKey(
    keyCode: Int,
    isCtrlPressed: Boolean = false,
    isAltPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isSuperKeyPressed: Boolean = false,
): DesktopWindowUiShortcutAction? =
    when (keyCode) {
        KeyEvent.VK_S ->
            if (isCtrlPressed && !isAltPressed && !isMetaPressed && !isSuperKeyPressed) {
                DesktopWindowUiShortcutAction.ToggleNowPlayingPanel
            } else {
                null
            }

        else -> null
    }

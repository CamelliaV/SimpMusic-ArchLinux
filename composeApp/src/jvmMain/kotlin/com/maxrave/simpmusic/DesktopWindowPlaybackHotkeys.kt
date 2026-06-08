package com.maxrave.simpmusic

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.viewModel.SharedViewModel
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.TextComponent
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

object DesktopWindowPlaybackHotkeys {
    private const val TAG = "DesktopWindowPlaybackHotkeys"

    fun install(
        window: Window,
        sharedViewModel: SharedViewModel,
    ): AutoCloseable {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        var superKeyPressed = false
        val dispatcher =
            java.awt.KeyEventDispatcher { event ->
                if (event.windowAncestor(focusManager) != window) return@KeyEventDispatcher false

                when (event.id) {
                    KeyEvent.KEY_PRESSED -> {
                        if (event.keyCode.isSuperKey()) {
                            superKeyPressed = true
                            return@KeyEventDispatcher false
                        }
                    }

                    KeyEvent.KEY_RELEASED -> {
                        if (event.keyCode.isSuperKey()) {
                            superKeyPressed = false
                            return@KeyEventDispatcher false
                        }
                        return@KeyEventDispatcher false
                    }

                    else -> return@KeyEventDispatcher false
                }

                if (event.component.isTextInput()) return@KeyEventDispatcher false

                val uiAction =
                    desktopWindowUiShortcutActionForAwtKey(
                        keyCode = event.keyCode,
                        isCtrlPressed = event.isControlDown || event.hasModifier(InputEvent.CTRL_DOWN_MASK),
                        isAltPressed = event.isAltDown || event.hasModifier(InputEvent.ALT_DOWN_MASK),
                        isMetaPressed = event.isMetaDown || event.hasModifier(InputEvent.META_DOWN_MASK),
                        isSuperKeyPressed = superKeyPressed,
                    )
                if (uiAction == DesktopWindowUiShortcutAction.ToggleNowPlayingPanel) {
                    sharedViewModel.requestToggleNowPlayingPanel()
                    event.consume()
                    Logger.d(TAG, "Dispatched focused-window UI shortcut: $uiAction")
                    return@KeyEventDispatcher true
                }

                val action =
                    playbackShortcutActionForAwtWindowKey(
                        keyCode = event.keyCode,
                        isCtrlPressed = event.isControlDown || event.hasModifier(InputEvent.CTRL_DOWN_MASK),
                        isAltPressed = event.isAltDown || event.hasModifier(InputEvent.ALT_DOWN_MASK),
                        isMetaPressed = event.isMetaDown || event.hasModifier(InputEvent.META_DOWN_MASK),
                        isSuperKeyPressed = superKeyPressed,
                    ) ?: return@KeyEventDispatcher false

                sharedViewModel.onUIEvent(action.toUIEvent())
                event.consume()
                Logger.d(TAG, "Dispatched focused-window playback shortcut: $action")
                true
            }

        focusManager.addKeyEventDispatcher(dispatcher)
        Logger.d(TAG, "Installed focused-window playback hotkeys")
        return AutoCloseable {
            focusManager.removeKeyEventDispatcher(dispatcher)
        }
    }

    private fun KeyEvent.windowAncestor(focusManager: KeyboardFocusManager): Window? =
        component?.let { SwingUtilities.getWindowAncestor(it) } ?: focusManager.activeWindow

    private fun Component?.isTextInput(): Boolean =
        this is TextComponent || this is JTextComponent

    private fun KeyEvent.hasModifier(mask: Int): Boolean = modifiersEx and mask != 0

    private fun Int.isSuperKey(): Boolean = this == KeyEvent.VK_WINDOWS || this == KeyEvent.VK_META
}

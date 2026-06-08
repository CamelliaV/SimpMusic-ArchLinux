package com.maxrave.simpmusic

import com.maxrave.logger.Logger
import com.sun.jna.NativeLong
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object DesktopGlobalPlaybackHotkey {
    private const val TAG = "DesktopGlobalPlaybackHotkey"
    private const val X11_KEYSYM_LEFT = 0xff51L
    private const val X11_KEYSYM_RIGHT = 0xff53L

    fun install(onShortcut: (DesktopPlaybackShortcutAction) -> Unit): AutoCloseable {
        val registration: StartableHotkeyRegistration? =
            when {
                Platform.isWindows() -> WindowsCtrlAltHotkeys(onShortcut)
                Platform.isLinux() -> LinuxX11CtrlAltHotkeys(onShortcut)
                else -> null
            }

        return try {
            registration?.start()
            registration ?: AutoCloseable {}
        } catch (error: Throwable) {
            Logger.e(TAG, "Global Ctrl+Alt playback shortcut registration failed: ${error.message}")
            registration?.close()
            AutoCloseable {}
        }
    }

    private interface StartableHotkeyRegistration : AutoCloseable {
        fun start()
    }

    private data class WindowsGlobalHotkey(
        val id: Int,
        val keyCode: Int,
        val action: DesktopPlaybackShortcutAction,
    )

    private data class X11GlobalHotkey(
        val keySym: Long,
        val action: DesktopPlaybackShortcutAction,
    )

    private class WindowsCtrlAltHotkeys(
        private val onShortcut: (DesktopPlaybackShortcutAction) -> Unit,
    ) : StartableHotkeyRegistration {
        private val running = AtomicBoolean(false)
        private val ready = CountDownLatch(1)
        private val threadId = AtomicInteger(0)
        private val registeredHotkeys = mutableListOf<WindowsGlobalHotkey>()
        private var thread: Thread? = null

        override fun start() {
            running.set(true)
            thread =
                Thread({
                    val id = Kernel32.INSTANCE.GetCurrentThreadId()
                    threadId.set(id)
                    val modifiers = WinUser.MOD_CONTROL or WinUser.MOD_ALT or WinUser.MOD_NOREPEAT

                    windowsGlobalHotkeys().forEach { hotkey ->
                        if (User32.INSTANCE.RegisterHotKey(null, hotkey.id, modifiers, hotkey.keyCode)) {
                            registeredHotkeys.add(hotkey)
                            Logger.d(TAG, "Registered global Ctrl+Alt+${hotkey.keyCode.toDisplayKey()} hotkey on Windows")
                        } else {
                            Logger.e(TAG, "Windows RegisterHotKey(Ctrl+Alt+${hotkey.keyCode.toDisplayKey()}) failed")
                        }
                    }

                    if (registeredHotkeys.isEmpty()) {
                        running.set(false)
                        ready.countDown()
                        return@Thread
                    }

                    ready.countDown()
                    val message = WinUser.MSG()
                    while (running.get()) {
                        val result = User32.INSTANCE.GetMessage(message, null, 0, 0)
                        if (result <= 0) break
                        if (message.message == WinUser.WM_HOTKEY) {
                            registeredHotkeys
                                .firstOrNull { it.id == message.wParam.toInt() }
                                ?.let { onShortcut(it.action) }
                        }
                    }

                    registeredHotkeys.forEach { hotkey ->
                        User32.INSTANCE.UnregisterHotKey(Pointer.NULL, hotkey.id)
                    }
                    registeredHotkeys.clear()
                }, "SimpMusic-Global-CtrlAltPlayback")
                    .apply {
                        isDaemon = true
                        start()
                    }

            ready.await()
        }

        override fun close() {
            running.set(false)
            val id = threadId.get()
            if (id != 0) {
                User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, WPARAM(0), LPARAM(0))
            }
            thread?.join(500)
        }

        private fun windowsGlobalHotkeys(): List<WindowsGlobalHotkey> =
            listOf(
                WindowsGlobalHotkey(0x534d50, KeyEvent.VK_P, DesktopPlaybackShortcutAction.PlayPause),
                WindowsGlobalHotkey(0x534d4c, KeyEvent.VK_LEFT, DesktopPlaybackShortcutAction.PreviousTrack),
                WindowsGlobalHotkey(0x534d52, KeyEvent.VK_RIGHT, DesktopPlaybackShortcutAction.NextTrack),
            )
    }

    private class LinuxX11CtrlAltHotkeys(
        private val onShortcut: (DesktopPlaybackShortcutAction) -> Unit,
    ) : StartableHotkeyRegistration {
        private val running = AtomicBoolean(false)
        private var display: X11.Display? = null
        private var rootWindow: X11.Window? = null
        private var keyCodes: Map<Int, DesktopPlaybackShortcutAction> = emptyMap()
        private var thread: Thread? = null
        private val xGrabFailed = AtomicBoolean(false)
        private val xErrorHandler =
            X11.XErrorHandler { _, error ->
                xGrabFailed.set(true)
                Logger.e(
                    TAG,
                    "X11 global Ctrl+Alt playback shortcut registration error: error=${error.error_code.toInt() and 0xff}, request=${error.request_code.toInt() and 0xff}",
                )
                0
            }

        override fun start() {
            val sessionType = System.getenv("XDG_SESSION_TYPE") ?: ""
            if (sessionType.equals("wayland", ignoreCase = true)) {
                Logger.w(TAG, "Global Ctrl+Alt playback shortcuts use X11 hotkeys; Wayland compositors may block them")
            }

            val x11 = X11.INSTANCE
            val openedDisplay =
                x11.XOpenDisplay(null)
                    ?: run {
                        Logger.e(TAG, "XOpenDisplay failed; global Ctrl+Alt playback shortcuts are unavailable")
                        return
                    }
            val root = x11.XDefaultRootWindow(openedDisplay)
            val resolvedKeyCodes =
                x11GlobalHotkeys()
                    .mapNotNull { hotkey ->
                        val code = x11.XKeysymToKeycode(openedDisplay, X11.KeySym(hotkey.keySym)).toInt() and 0xff
                        if (code == 0) {
                            Logger.e(TAG, "Could not resolve X11 keycode for keysym ${hotkey.keySym}")
                            null
                        } else {
                            code to hotkey.action
                        }
                    }.toMap()

            if (resolvedKeyCodes.isEmpty()) {
                x11.XCloseDisplay(openedDisplay)
                Logger.e(TAG, "No X11 keycodes resolved for global Ctrl+Alt playback shortcuts")
                return
            }

            display = openedDisplay
            rootWindow = root
            keyCodes = resolvedKeyCodes
            running.set(true)

            xGrabFailed.set(false)
            val previousErrorHandler = x11.XSetErrorHandler(xErrorHandler)
            grabCtrlAltVariants(x11, openedDisplay, root, resolvedKeyCodes.keys)
            x11.XSync(openedDisplay, false)
            x11.XSetErrorHandler(previousErrorHandler)
            if (xGrabFailed.get()) {
                running.set(false)
                x11.XCloseDisplay(openedDisplay)
                Logger.e(TAG, "X11 global Ctrl+Alt playback shortcut registration failed; a hotkey may already be reserved")
                return
            }

            x11.XSelectInput(openedDisplay, root, NativeLong(X11.KeyPressMask.toLong()))
            x11.XFlush(openedDisplay)
            Logger.d(TAG, "Registered global Ctrl+Alt playback hotkeys on X11")

            thread =
                Thread({
                    val event = X11.XEvent()
                    while (running.get()) {
                        x11.XNextEvent(openedDisplay, event)
                        if (!running.get()) break
                        if (event.type == X11.KeyPress) {
                            event.setType(X11.XKeyEvent::class.java)
                            event.read()
                            if (
                                event.xkey.state and X11.ControlMask != 0 &&
                                event.xkey.state and X11.Mod1Mask != 0
                            ) {
                                keyCodes[event.xkey.keycode]?.let(onShortcut)
                            }
                        }
                    }
                }, "SimpMusic-Global-CtrlAltPlayback")
                    .apply {
                        isDaemon = true
                        start()
                    }
        }

        override fun close() {
            running.set(false)
            val x11 = X11.INSTANCE
            val openedDisplay = display
            val root = rootWindow
            if (openedDisplay != null && root != null && keyCodes.isNotEmpty()) {
                ungrabCtrlAltVariants(x11, openedDisplay, root, keyCodes.keys)
                x11.XFlush(openedDisplay)
                x11.XCloseDisplay(openedDisplay)
            }
            thread?.interrupt()
        }

        private fun grabCtrlAltVariants(
            x11: X11,
            display: X11.Display,
            root: X11.Window,
            keyCodes: Collection<Int>,
        ) {
            keyCodes.forEach { keyCode ->
                ctrlAltModifierVariants().forEach { modifiers ->
                    x11.XGrabKey(display, keyCode, modifiers, root, 1, X11.GrabModeAsync, X11.GrabModeAsync)
                }
            }
        }

        private fun ungrabCtrlAltVariants(
            x11: X11,
            display: X11.Display,
            root: X11.Window,
            keyCodes: Collection<Int>,
        ) {
            keyCodes.forEach { keyCode ->
                ctrlAltModifierVariants().forEach { modifiers ->
                    x11.XUngrabKey(display, keyCode, modifiers, root)
                }
            }
        }

        private fun ctrlAltModifierVariants(): List<Int> {
            val baseModifiers = X11.ControlMask or X11.Mod1Mask
            return listOf(
                baseModifiers,
                baseModifiers or X11.LockMask,
                baseModifiers or X11.Mod2Mask,
                baseModifiers or X11.LockMask or X11.Mod2Mask,
            )
        }

        private fun x11GlobalHotkeys(): List<X11GlobalHotkey> =
            listOf(
                X11GlobalHotkey('P'.code.toLong(), DesktopPlaybackShortcutAction.PlayPause),
                X11GlobalHotkey(X11_KEYSYM_LEFT, DesktopPlaybackShortcutAction.PreviousTrack),
                X11GlobalHotkey(X11_KEYSYM_RIGHT, DesktopPlaybackShortcutAction.NextTrack),
            )
    }

    private fun Int.toDisplayKey(): String =
        when (this) {
            KeyEvent.VK_P -> "P"
            KeyEvent.VK_LEFT -> "Left"
            KeyEvent.VK_RIGHT -> "Right"
            else -> toString()
        }
}

package com.maxrave.simpmusic.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.WindowScope
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

@Composable
fun WindowScope.InstallDesktopMiddleMouseHorizontalDragDispatcher(uiScale: Float = 1f) {
    DisposableEffect(window, uiScale) {
        var activeTargetHit: DesktopMiddleMouseHorizontalDragTargetHit? = null
        var lastWindowX: Float? = null
        var lastContentPoint: Point? = null
        val debugEnabled = java.lang.Boolean.getBoolean("simpmusic.middleMouseDrag.debug")
        val contentComponent = SwingUtilities.getRootPane(window)?.contentPane ?: window

        fun debug(message: String) {
            if (debugEnabled) {
                System.err.println("[SimpMusicMiddleMouseDrag] $message")
            }
        }

        val evdevDispatcher =
            DesktopEvdevMouseInputDispatcher(
                window = window,
                contentComponent = contentComponent,
                uiScale = uiScale,
                cachedPointerInContent = { lastContentPoint },
                debug = ::debug,
            )

        fun sourceComponent(event: MouseEvent): Component? =
            event.source as? Component ?: event.component

        fun sourceWindow(event: MouseEvent): Window? {
            val source = sourceComponent(event) ?: return null
            if (source === window) return window
            if (source is Window) return source
            return SwingUtilities.getWindowAncestor(source)
        }

        fun contentPoint(event: MouseEvent): Point? {
            val source = sourceComponent(event) ?: return null
            return runCatching {
                SwingUtilities.convertPoint(source, event.point, contentComponent)
            }.getOrNull()
        }

        val listener =
            AWTEventListener { awtEvent ->
                val event = awtEvent as? MouseEvent ?: return@AWTEventListener
                if (sourceWindow(event) !== window) return@AWTEventListener
                contentPoint(event)?.let { point ->
                    lastContentPoint = Point(point)
                }

                when (desktopMouseNavigationDirection(eventId = event.id, button = event.button)) {
                    DesktopMouseNavigationDirection.Back -> {
                        if (DesktopMouseNavigationActions.dispatchBack()) {
                            debug("side-button back button=${event.button}")
                            event.consume()
                        }
                        return@AWTEventListener
                    }
                    DesktopMouseNavigationDirection.Forward -> {
                        if (DesktopMouseNavigationActions.dispatchForward()) {
                            debug("side-button forward button=${event.button}")
                            event.consume()
                        }
                        return@AWTEventListener
                    }
                    null -> Unit
                }

                val isMiddleMousePressed =
                    isAwtMiddleMousePressed(
                        button = event.button,
                        modifiersEx = event.modifiersEx,
                    )

                if (
                    shouldClearAwtMiddleMouseDrag(
                        eventId = event.id,
                        button = event.button,
                        isMiddleMousePressed = isMiddleMousePressed,
                    )
                ) {
                    if (activeTargetHit != null) {
                        debug("clear id=${event.id} button=${event.button}")
                    }
                    activeTargetHit = null
                    lastWindowX = null
                    return@AWTEventListener
                }

                if (!isMiddleMousePressed) return@AWTEventListener

                val point = contentPoint(event)
                if (point == null) {
                    debug("skip: no point for id=${event.id}")
                    return@AWTEventListener
                }

                when (event.id) {
                    MouseEvent.MOUSE_PRESSED -> {
                        activeTargetHit =
                            DesktopMiddleMouseHorizontalDragTargets.findTargetHitAt(
                                windowX = point.x.toFloat(),
                                windowY = point.y.toFloat(),
                                uiScale = uiScale,
                            )
                        lastWindowX = point.x.toFloat()
                        debug("press x=${point.x} y=${point.y} target=$activeTargetHit scale=$uiScale")
                        if (activeTargetHit != null) {
                            event.consume()
                        }
                    }
                    MouseEvent.MOUSE_DRAGGED,
                    MouseEvent.MOUSE_MOVED,
                    -> {
                        if (!isAwtMiddleMouseDragMotionEvent(event.id)) return@AWTEventListener
                        if (activeTargetHit == null) {
                            activeTargetHit =
                                DesktopMiddleMouseHorizontalDragTargets.findTargetHitAt(
                                    windowX = point.x.toFloat(),
                                    windowY = point.y.toFloat(),
                                    uiScale = uiScale,
                                )
                            debug("drag-start x=${point.x} y=${point.y} target=$activeTargetHit scale=$uiScale")
                        }

                        val previousX = lastWindowX
                        lastWindowX = point.x.toFloat()
                        val targetHit = activeTargetHit
                        if (previousX == null || targetHit == null) return@AWTEventListener

                        val delta =
                            middleMouseHorizontalDragDelta(
                                isDragging = true,
                                previousX = previousX,
                                currentX = point.x.toFloat(),
                            ) ?: return@AWTEventListener

                        if (DesktopMiddleMouseHorizontalDragTargets.dispatchToTarget(hit = targetHit, deltaX = delta)) {
                            debug("drag x=${point.x} y=${point.y} target=$targetHit delta=$delta")
                            event.consume()
                        }
                    }
                }
            }

        Toolkit
            .getDefaultToolkit()
            .addAWTEventListener(
                listener,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
            )
        evdevDispatcher.start()

        onDispose {
            evdevDispatcher.stop()
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }
}

internal fun isAwtMiddleMousePressed(
    button: Int,
    modifiersEx: Int,
): Boolean = button == MouseEvent.BUTTON2 || modifiersEx and MouseEvent.BUTTON2_DOWN_MASK != 0

internal fun isAwtMiddleMouseDragMotionEvent(eventId: Int): Boolean =
    eventId == MouseEvent.MOUSE_DRAGGED || eventId == MouseEvent.MOUSE_MOVED

internal fun shouldClearAwtMiddleMouseDrag(
    eventId: Int,
    button: Int,
    isMiddleMousePressed: Boolean,
): Boolean =
    when (eventId) {
        MouseEvent.MOUSE_RELEASED -> button == MouseEvent.BUTTON2 || !isMiddleMousePressed
        MouseEvent.MOUSE_DRAGGED,
        MouseEvent.MOUSE_MOVED,
        MouseEvent.MOUSE_EXITED,
        -> !isMiddleMousePressed
        else -> false
    }

internal enum class DesktopMouseNavigationDirection {
    Back,
    Forward,
}

internal fun desktopMouseNavigationDirection(
    eventId: Int,
    button: Int,
): DesktopMouseNavigationDirection? {
    if (eventId != MouseEvent.MOUSE_PRESSED) return null
    return when (button) {
        4, 8 -> DesktopMouseNavigationDirection.Back
        5, 9 -> DesktopMouseNavigationDirection.Forward
        else -> null
    }
}

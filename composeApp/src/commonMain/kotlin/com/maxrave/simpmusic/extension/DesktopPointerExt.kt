package com.maxrave.simpmusic.extension

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.desktopMiddleMouseHorizontalDrag(state: LazyListState): Modifier {
    if (getPlatform() != Platform.Desktop) return this
    val coroutineScope = rememberCoroutineScope()
    val targetId = remember { DesktopMiddleMouseHorizontalDragTargets.register() }
    DisposableEffect(targetId) {
        onDispose { DesktopMiddleMouseHorizontalDragTargets.unregister(targetId) }
    }
    return this.then(
        Modifier.desktopMiddleMouseHorizontalDrag(
            coroutineScope = coroutineScope,
            targetId = targetId,
            scrollBy = { delta ->
                state.scrollBy(delta)
            },
        ),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.desktopMiddleMouseHorizontalDrag(state: LazyGridState): Modifier {
    if (getPlatform() != Platform.Desktop) return this
    val coroutineScope = rememberCoroutineScope()
    val targetId = remember { DesktopMiddleMouseHorizontalDragTargets.register() }
    DisposableEffect(targetId) {
        onDispose { DesktopMiddleMouseHorizontalDragTargets.unregister(targetId) }
    }
    return this.then(
        Modifier.desktopMiddleMouseHorizontalDrag(
            coroutineScope = coroutineScope,
            targetId = targetId,
            scrollBy = { delta ->
                state.scrollBy(delta)
            },
        ),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.desktopMiddleMouseHorizontalDrag(state: ScrollState): Modifier {
    if (getPlatform() != Platform.Desktop) return this
    val coroutineScope = rememberCoroutineScope()
    val targetId = remember { DesktopMiddleMouseHorizontalDragTargets.register() }
    DisposableEffect(targetId) {
        onDispose { DesktopMiddleMouseHorizontalDragTargets.unregister(targetId) }
    }
    return this.then(
        Modifier.desktopMiddleMouseHorizontalDrag(
            coroutineScope = coroutineScope,
            targetId = targetId,
            scrollBy = { delta ->
                state.scrollBy(delta)
            },
        ),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.desktopMiddleMouseHorizontalDrag(
    coroutineScope: CoroutineScope,
    targetId: Long,
    scrollBy: suspend (Float) -> Unit,
): Modifier {
    val dispatchScrollBy: (Float) -> Unit = { delta ->
        coroutineScope.launch {
            scrollBy(delta)
        }
    }

    return onGloballyPositioned { coordinates ->
        DesktopMiddleMouseHorizontalDragTargets.update(
            id = targetId,
            bounds = coordinates.boundsInWindow(),
            scrollBy = dispatchScrollBy,
        )
    }.pointerInput(coroutineScope, scrollBy) {
        awaitEachGesture {
            var lastPosition: Offset? = null

            while (lastPosition == null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Exit) return@awaitEachGesture
                if (!shouldCaptureMiddleMouseHorizontalDragEvent(event.isMiddleMouseEvent())) {
                    if (event.type == PointerEventType.Press) return@awaitEachGesture
                    continue
                }

                lastPosition = event.changes.firstOrNull()?.position
                event.changes.forEach { it.consume() }
            }

            while (lastPosition != null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val position = event.changes.firstOrNull()?.position

                if (event.type == PointerEventType.Move && position != null) {
                    val delta =
                        middleMouseHorizontalDragDelta(
                            isDragging = true,
                            previousX = lastPosition.x,
                            currentX = position.x,
                        )
                    lastPosition = position
                    if (delta != null) {
                        dispatchScrollBy(delta)
                    }
                    event.changes.forEach { it.consume() }
                    continue
                }

                if (event.type == PointerEventType.Release || event.type == PointerEventType.Exit) {
                    event.changes.forEach { it.consume() }
                    lastPosition = null
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun PointerEvent.isMiddleMouseEvent(): Boolean =
    shouldStartMiddleMouseHorizontalDrag(
        isComposeMiddleMouseEvent = buttons.isTertiaryPressed,
        isNativeMiddleMouseEvent = isNativeMiddleMouseEvent(),
    )

internal fun shouldStartMiddleMouseHorizontalDrag(
    isComposeMiddleMouseEvent: Boolean,
    isNativeMiddleMouseEvent: Boolean,
): Boolean = isComposeMiddleMouseEvent || isNativeMiddleMouseEvent

internal fun shouldCaptureMiddleMouseHorizontalDragEvent(isMiddleMouseEvent: Boolean): Boolean = isMiddleMouseEvent

@OptIn(ExperimentalComposeUiApi::class)
internal expect fun PointerEvent.isNativeMiddleMouseEvent(): Boolean

@OptIn(ExperimentalComposeUiApi::class)
internal expect fun PointerEvent.isPrimaryButtonEvent(): Boolean

internal fun middleMouseHorizontalDragDelta(
    isDragging: Boolean,
    previousX: Float?,
    currentX: Float,
): Float? {
    if (!isDragging || previousX == null) return null
    val delta = currentX - previousX
    return delta.takeIf { it != 0f }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.dismissOnPrimaryClickAway(
    enabled: Boolean,
    onDismiss: () -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, onDismiss) {
        awaitEachGesture {
            var startPosition: Offset? = null
            var maxDistancePx = 0f
            var isPrimaryButton = false

            while (startPosition == null) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press) {
                    startPosition = event.changes.firstOrNull()?.position
                    isPrimaryButton = event.isPrimaryButtonEvent()
                }
            }

            var pointerIsPressed = true
            while (pointerIsPressed) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull()
                if (change == null) {
                    pointerIsPressed = event.type != PointerEventType.Release
                    continue
                }

                startPosition.let { start ->
                    maxDistancePx =
                        max(
                            maxDistancePx,
                            (change.position - start).getDistance(),
                        )
                }
                pointerIsPressed = change.pressed
            }

            if (shouldDismissClickAway(
                    isPrimaryButton = isPrimaryButton,
                    maxDistancePx = maxDistancePx,
                    touchSlopPx = viewConfiguration.touchSlop,
                )
            ) {
                onDismiss()
            }
        }
    }
}

internal fun shouldDismissClickAway(
    isPrimaryButton: Boolean,
    maxDistancePx: Float,
    touchSlopPx: Float,
): Boolean = isPrimaryButton && maxDistancePx <= touchSlopPx

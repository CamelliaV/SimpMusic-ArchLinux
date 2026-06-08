package com.maxrave.simpmusic.extension

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import java.awt.event.MouseEvent

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun PointerEvent.isNativeMiddleMouseEvent(): Boolean {
    val event = nativeEvent as? MouseEvent ?: return false
    return event.button == MouseEvent.BUTTON2 ||
        event.modifiersEx and MouseEvent.BUTTON2_DOWN_MASK != 0
}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun PointerEvent.isPrimaryButtonEvent(): Boolean = button == PointerButton.Primary

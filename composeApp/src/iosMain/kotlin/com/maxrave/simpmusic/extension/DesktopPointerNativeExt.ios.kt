package com.maxrave.simpmusic.extension

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEvent

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun PointerEvent.isNativeMiddleMouseEvent(): Boolean = false

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun PointerEvent.isPrimaryButtonEvent(): Boolean = true

package com.maxrave.simpmusic.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

internal object DesktopMouseNavigationActions {
    private val lock = Any()
    private var onBack: (() -> Unit)? = null
    private var onForward: (() -> Unit)? = null
    private var backCount = 0
    private var forwardCount = 0

    fun setHandlers(
        onBack: () -> Unit,
        onForward: () -> Unit,
    ) {
        synchronized(lock) {
            this.onBack = onBack
            this.onForward = onForward
        }
    }

    fun clearHandlers() {
        synchronized(lock) {
            onBack = null
            onForward = null
        }
    }

    fun dispatchBack(): Boolean {
        val handler = synchronized(lock) { onBack } ?: return false
        synchronized(lock) {
            backCount += 1
        }
        handler()
        return true
    }

    fun dispatchForward(): Boolean {
        val handler = synchronized(lock) { onForward } ?: return false
        synchronized(lock) {
            forwardCount += 1
        }
        handler()
        return true
    }

    fun dispatchedBackCountForTest(): Int = synchronized(lock) { backCount }

    fun dispatchedForwardCountForTest(): Int = synchronized(lock) { forwardCount }

    fun clearForTest() {
        synchronized(lock) {
            backCount = 0
            forwardCount = 0
        }
        clearHandlers()
    }
}

@Composable
fun InstallDesktopMouseNavigationHandlers(
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val currentBack = rememberUpdatedState(onBack)
    val currentForward = rememberUpdatedState(onForward)

    DisposableEffect(Unit) {
        DesktopMouseNavigationActions.setHandlers(
            onBack = { currentBack.value() },
            onForward = { currentForward.value() },
        )
        onDispose {
            DesktopMouseNavigationActions.clearHandlers()
        }
    }
}

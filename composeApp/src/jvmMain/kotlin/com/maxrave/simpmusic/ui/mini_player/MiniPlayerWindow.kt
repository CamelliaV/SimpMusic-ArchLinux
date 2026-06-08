package com.maxrave.simpmusic.ui.mini_player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.maxrave.domain.manager.DesktopFontFamily
import com.maxrave.domain.manager.DesktopUiScale
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.DesktopWindowPlaybackHotkeys
import com.maxrave.simpmusic.DesktopWindowCompatibility
import com.maxrave.simpmusic.dispatchDesktopPlaybackShortcut
import com.maxrave.simpmusic.ui.theme.LocalAppFontFamily
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.jetbrains.compose.resources.painterResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.circle_app_icon
import java.awt.Dimension
import java.util.prefs.Preferences

/**
 * Mini player window - a separate always-on-top window for music controls.
 * Spotify-style frameless design with custom close button.
 *
 * Features:
 * - Always on top of other windows
 * - Frameless (no title bar)
 * - Resizable (default 400x110 dp)
 * - Shares player state with main window
 * - Close-safe (doesn't close main app)
 * - Remembers window position
 * - Keyboard shortcuts (Space: play/pause, arrows: seek, Ctrl+arrows: prev/next)
 */
@Composable
fun MiniPlayerWindow(
    sharedViewModel: SharedViewModel,
    onCloseRequest: () -> Unit,
    requiresOpaqueWindow: Boolean = DesktopWindowCompatibility.requiresOpaqueWindow(),
    uiScale: Float = DesktopUiScale.DEFAULT,
    fontFamily: String = DesktopFontFamily.DEFAULT,
) {
    val prefs = remember { Preferences.userRoot().node("SimpMusic/MiniPlayer") }

    // Minimum size constraints
    val minWidth = 200f
    val minHeight = 56f

    // Load saved position or use default (with minimum constraints)
    val savedX = prefs.getFloat("windowX", Float.NaN)
    val savedY = prefs.getFloat("windowY", Float.NaN)
    val savedWidth = prefs.getFloat("windowWidth", 400f).coerceAtLeast(minWidth)
    val savedHeight = prefs.getFloat("windowHeight", 56f).coerceAtLeast(minHeight)

    var windowState by remember {
        mutableStateOf(
            WindowState(
                placement = WindowPlacement.Floating,
                position =
                    if (savedX.isNaN() || savedY.isNaN()) {
                        WindowPosition(Alignment.BottomEnd)
                    } else {
                        WindowPosition(savedX.coerceAtLeast(0f).dp, savedY.coerceAtLeast(0f).dp)
                    },
                size = DpSize(savedWidth.coerceAtLeast(0f).dp, savedHeight.coerceAtLeast(0f).dp),
            ),
        )
    }

    // Save position on change
    LaunchedEffect(windowState.position, windowState.size) {
        val pos = windowState.position
        Logger.w("MiniPlayerWindow", "Saving position: $pos")
        if (pos is WindowPosition.Absolute) {
            prefs.putFloat("windowX", pos.x.value)
            prefs.putFloat("windowY", pos.y.value)
        }
        prefs.putFloat("windowWidth", windowState.size.width.value)
        prefs.putFloat("windowHeight", windowState.size.height.value)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "SimpMusic - Mini Player",
        icon = painterResource(Res.drawable.circle_app_icon),
        alwaysOnTop = true,
        undecorated = !requiresOpaqueWindow,
        transparent = !requiresOpaqueWindow,
        resizable = true,
        state = windowState,
        onKeyEvent = { keyEvent ->
            dispatchDesktopPlaybackShortcut(keyEvent, sharedViewModel)
        },
    ) {
        DisposableEffect(window, sharedViewModel) {
            val windowHotkeys =
                DesktopWindowPlaybackHotkeys.install(
                    window = window,
                    sharedViewModel = sharedViewModel,
                )
            onDispose {
                windowHotkeys.close()
            }
        }

        val baseDensity = LocalDensity.current
        val scaledDensity =
            remember(baseDensity, uiScale) {
                Density(
                    density = baseDensity.density * DesktopUiScale.normalize(uiScale),
                    fontScale = baseDensity.fontScale,
                )
            }

        // Set minimum size at AWT level to prevent flickering
        LaunchedEffect(Unit) {
            (window as? java.awt.Window)?.minimumSize =
                Dimension(
                    (minWidth * window.graphicsConfiguration.defaultTransform.scaleX).toInt(),
                    (minHeight * window.graphicsConfiguration.defaultTransform.scaleY).toInt(),
                )
        }

        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalAppFontFamily provides DesktopFontFamily.normalize(fontFamily),
        ) {
            MiniPlayerRoot(
                sharedViewModel = sharedViewModel,
                onClose = onCloseRequest,
                windowState = windowState,
            )
        }
    }
}

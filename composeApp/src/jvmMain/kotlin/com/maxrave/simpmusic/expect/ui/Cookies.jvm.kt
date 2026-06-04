package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import javax.swing.SwingUtilities

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String =
            CookieHandler
                .getDefault()
                ?.let { handler ->
                    runCatching {
                        handler.get(URI(url), emptyMap())["Cookie"]?.joinToString("; ") ?: ""
                    }.getOrDefault("")
                } ?: ""

        override fun removeAllCookies() {
            CookieHandler.setDefault(
                newDesktopCookieManager(),
            )
        }
    }

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                createDesktopWebViewPanel(
                    initUrl = initUrl,
                    state = state,
                    onPageFinished = onPageFinished,
                )
            },
        )
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {
    val initUrl = "https://discord.com/login"
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                createDesktopWebViewPanel(
                    initUrl = initUrl,
                    state = state,
                    onPageFinished = {},
                    onLocationFinished = { engine, location ->
                        if (location.endsWith("/app") || location.startsWith("https://discord.com/channels")) {
                            readDiscordToken(engine)?.let { token ->
                                SwingUtilities.invokeLater {
                                    onLoginDone(token)
                                }
                            }
                        }
                    },
                )
            },
        )
        aboveContent()
    }
}

private fun createDesktopWebViewPanel(
    initUrl: String,
    state: MutableState<WebViewState>,
    onPageFinished: (String) -> Unit,
    onLocationFinished: (WebEngine, String) -> Unit = { _, _ -> },
): JFXPanel {
    ensureDesktopCookieManager()
    val panel = JFXPanel()
    Platform.setImplicitExit(false)
    Platform.runLater {
        val webView = WebView()
        val engine = webView.engine
        engine.isJavaScriptEnabled = true
        engine.userAgent = DESKTOP_WEBVIEW_USER_AGENT
        engine.loadWorker.progressProperty().addListener { _, _, progress ->
            updateStateOnSwingThread(
                state,
                WebViewState.Loading((progress.toDouble() * 100).toInt().coerceIn(0, 100)),
            )
        }
        engine.loadWorker.stateProperty().addListener { _, _, workerState ->
            if (workerState == Worker.State.SUCCEEDED) {
                val location = engine.location
                SwingUtilities.invokeLater {
                    state.value = WebViewState.Finished
                    onPageFinished(location)
                }
                onLocationFinished(engine, location)
            } else if (workerState == Worker.State.RUNNING) {
                updateStateOnSwingThread(state, WebViewState.Loading(0))
            }
        }
        panel.scene = Scene(webView)
        engine.load(initUrl)
    }
    return panel
}

private fun updateStateOnSwingThread(
    state: MutableState<WebViewState>,
    webViewState: WebViewState,
) {
    SwingUtilities.invokeLater {
        state.value = webViewState
    }
}

private fun ensureDesktopCookieManager() {
    if (CookieHandler.getDefault() == null) {
        CookieHandler.setDefault(newDesktopCookieManager())
    }
}

private fun newDesktopCookieManager(): CookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

private fun readDiscordToken(engine: WebEngine): String? =
    runCatching {
        val token =
            engine.executeScript(
                """
                (function() {
                    var iframe = document.createElement('iframe');
                    document.body.appendChild(iframe);
                    var token = iframe.contentWindow.localStorage.token;
                    document.body.removeChild(iframe);
                    return token ? token.slice(1, -1) : "";
                })()
                """.trimIndent(),
            )?.toString()

        token
            ?.takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
    }.getOrNull()

private const val DESKTOP_WEBVIEW_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

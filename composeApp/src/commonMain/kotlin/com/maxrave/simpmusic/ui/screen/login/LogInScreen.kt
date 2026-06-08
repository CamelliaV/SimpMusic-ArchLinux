package com.maxrave.simpmusic.ui.screen.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.common.Config
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.canImportYouTubeCookiesFromBrowser
import com.maxrave.simpmusic.expect.openUrl
import com.maxrave.simpmusic.expect.useEmbeddedGoogleLoginWebView
import com.maxrave.simpmusic.expect.ui.PlatformWebView
import com.maxrave.simpmusic.expect.ui.createWebViewCookieManager
import com.maxrave.simpmusic.expect.ui.rememberWebViewState
import com.maxrave.simpmusic.ui.component.DevLogInBottomSheet
import com.maxrave.simpmusic.ui.component.DevLogInType
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.BrowserCookieAccountImportResult
import com.maxrave.simpmusic.viewModel.LogInViewModel
import com.maxrave.simpmusic.viewModel.SettingsViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import simpmusic.composeapp.generated.resources.desktop_google_login_browser_import_failed
import simpmusic.composeapp.generated.resources.desktop_google_login_compact_input
import simpmusic.composeapp.generated.resources.desktop_google_login_cookie_placeholder
import simpmusic.composeapp.generated.resources.desktop_google_login_description
import simpmusic.composeapp.generated.resources.desktop_google_login_import_browser
import simpmusic.composeapp.generated.resources.desktop_google_login_imported_from
import simpmusic.composeapp.generated.resources.desktop_google_login_importing
import simpmusic.composeapp.generated.resources.desktop_google_login_open_browser
import simpmusic.composeapp.generated.resources.desktop_google_login_with_cookie
import simpmusic.composeapp.generated.resources.log_in
import simpmusic.composeapp.generated.resources.login_failed
import simpmusic.composeapp.generated.resources.login_success

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LoginScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: LogInViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit,
    showBottomNavigation: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val coroutineScope = rememberCoroutineScope()
    var devLoginSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var desktopCookie by rememberSaveable {
        mutableStateOf("")
    }
    var browserCookieImporting by rememberSaveable {
        mutableStateOf(false)
    }
    var browserCookieImportStatus by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val state = rememberWebViewState()

    LaunchedEffect(state) {
        snapshotFlow { state.value }.collect {
            Logger.d(
                "LogInScreen",
                "WebViewState: ${
                    when (it) {
                        is com.maxrave.simpmusic.expect.ui.WebViewState.Finished -> "Finished"
                        is com.maxrave.simpmusic.expect.ui.WebViewState.Loading -> "Loading ${it.progress}%"
                    }
                }",
            )
        }
    }

    // Hide bottom navigation when entering this screen
    LaunchedEffect(Unit) {
        hideBottomNavigation()
        createWebViewCookieManager().removeAllCookies()
    }

    // Show bottom navigation when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            showBottomNavigation()
        }
    }

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(
                Modifier
                    .size(
                        innerPadding.calculateTopPadding() + 64.dp,
                    ),
            )
            if (useEmbeddedGoogleLoginWebView()) {
                // WebView for YouTube Music login
                PlatformWebView(
                    state,
                    Config.LOG_IN_URL,
                    aboveContent = {
                        if (devLoginSheet) {
                            DevLogInBottomSheet(
                                onDismiss = {
                                    devLoginSheet = false
                                },
                                onDone = { cookie ->
                                    coroutineScope.launch {
                                        val success = settingsViewModel.addAccount(cookie)
                                        if (success) {
                                            viewModel.makeToast(getString(Res.string.login_success))
                                            navController.navigateUp()
                                        } else {
                                            viewModel.makeToast(getString(Res.string.login_failed))
                                        }
                                    }
                                },
                                type = DevLogInType.YouTube,
                            )
                        }
                    },
                ) { url ->
                    Logger.d("LogInScreen", "Current URL: $url")
                    if (url == Config.YOUTUBE_MUSIC_MAIN_URL) {
                        coroutineScope.launch {
                            val success =
                                createWebViewCookieManager()
                                    .getCookie(url)
                                    .takeIf {
                                        it.isNotEmpty()
                                    }?.let {
                                        settingsViewModel.addAccount(it)
                                    } ?: false

                            createWebViewCookieManager().removeAllCookies()

                            if (success) {
                                viewModel.makeToast(getString(Res.string.login_success))
                                navController.navigateUp()
                            } else {
                                viewModel.makeToast(getString(Res.string.login_failed))
                            }
                        }
                    }
                }
            } else {
                DesktopGoogleLoginPanel(
                    modifier = Modifier.weight(1f),
                    cookie = desktopCookie,
                    canImportFromBrowser = canImportYouTubeCookiesFromBrowser(),
                    browserImporting = browserCookieImporting,
                    browserImportStatus = browserCookieImportStatus,
                    onCookieChange = { desktopCookie = it },
                    onImportFromBrowser = {
                        coroutineScope.launch {
                            browserCookieImporting = true
                            browserCookieImportStatus = getString(Res.string.desktop_google_login_importing)
                            runCatching {
                                settingsViewModel.importBrowserCookiesAndAddAccount()
                            }.onSuccess { result ->
                                when (result) {
                                    is BrowserCookieAccountImportResult.Success -> {
                                        desktopCookie = ""
                                        browserCookieImportStatus =
                                            getString(
                                                Res.string.desktop_google_login_imported_from,
                                                result.sourceDescription,
                                            )
                                        viewModel.makeToast(getString(Res.string.login_success))
                                        navController.navigateUp()
                                    }

                                    is BrowserCookieAccountImportResult.Failure -> {
                                        browserCookieImportStatus =
                                            getString(Res.string.desktop_google_login_browser_import_failed)
                                        Logger.e("LogInScreen", "Browser cookie import failed: ${result.message}")
                                        viewModel.makeToast(getString(Res.string.login_failed))
                                    }

                                    BrowserCookieAccountImportResult.Unsupported -> {
                                        browserCookieImportStatus =
                                            getString(Res.string.desktop_google_login_browser_import_failed)
                                        viewModel.makeToast(getString(Res.string.desktop_google_login_browser_import_failed))
                                    }
                                }
                            }.onFailure { error ->
                                Logger.e("LogInScreen", "Browser cookie import failed: ${error.message}")
                                browserCookieImportStatus = error.message
                                viewModel.makeToast(getString(Res.string.desktop_google_login_browser_import_failed))
                            }
                            browserCookieImporting = false
                        }
                    },
                    onOpenBrowser = { openUrl(Config.LOG_IN_URL) },
                    onSubmit = {
                        coroutineScope.launch {
                            val success = settingsViewModel.addAccount(desktopCookie)
                            if (success) {
                                viewModel.makeToast(getString(Res.string.login_success))
                                navController.navigateUp()
                            } else {
                                viewModel.makeToast(getString(Res.string.login_failed))
                            }
                        }
                    },
                    onOpenManualSheet = {
                        devLoginSheet = true
                    },
                )
                if (devLoginSheet) {
                    DevLogInBottomSheet(
                        onDismiss = {
                            devLoginSheet = false
                        },
                        onDone = { cookie ->
                            desktopCookie = cookie
                            coroutineScope.launch {
                                val success = settingsViewModel.addAccount(cookie)
                                if (success) {
                                    viewModel.makeToast(getString(Res.string.login_success))
                                    navController.navigateUp()
                                } else {
                                    viewModel.makeToast(getString(Res.string.login_failed))
                                }
                            }
                        },
                        type = DevLogInType.YouTube,
                    )
                }
            }
        }

        // Top App Bar with haze effect
        TopAppBar(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                        blurEnabled = true
                    },
            title = {
                Text(
                    text = stringResource(Res.string.log_in),
                    style = typo().titleMedium,
                )
            },
            navigationIcon = {
                Box(Modifier.padding(horizontal = 5.dp)) {
                    RippleIconButton(
                        Res.drawable.baseline_arrow_back_ios_new_24,
                        Modifier.size(32.dp),
                        true,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        devLoginSheet = true
                    },
                ) {
                    Icon(
                        Icons.Default.LogoDev,
                        "Developer Mode",
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )
    }
}

@Composable
private fun DesktopGoogleLoginPanel(
    modifier: Modifier = Modifier,
    cookie: String,
    canImportFromBrowser: Boolean,
    browserImporting: Boolean,
    browserImportStatus: String?,
    onCookieChange: (String) -> Unit,
    onImportFromBrowser: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSubmit: () -> Unit,
    onOpenManualSheet: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.desktop_google_login_description),
                style = typo().labelMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onImportFromBrowser,
                enabled = canImportFromBrowser && !browserImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (browserImporting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            stringResource(Res.string.desktop_google_login_importing),
                            style = typo().labelSmall,
                        )
                    }
                } else {
                    Text(
                        stringResource(Res.string.desktop_google_login_import_browser),
                        style = typo().labelSmall,
                    )
                }
            }
            browserImportStatus?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
                    style = typo().bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(
                onClick = onOpenBrowser,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(Res.string.desktop_google_login_open_browser),
                    style = typo().labelSmall,
                )
            }
            OutlinedTextField(
                value = cookie,
                onValueChange = onCookieChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                placeholder = {
                    Text(
                        stringResource(Res.string.desktop_google_login_cookie_placeholder),
                        style = typo().bodySmall,
                    )
                },
            )
            Button(
                onClick = onSubmit,
                enabled = cookie.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(Res.string.desktop_google_login_with_cookie),
                    style = typo().labelSmall,
                )
            }
            TextButton(onClick = onOpenManualSheet) {
                Text(
                    stringResource(Res.string.desktop_google_login_compact_input),
                    style = typo().labelSmall,
                )
            }
        }
    }
}

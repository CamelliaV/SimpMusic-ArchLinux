package com.maxrave.simpmusic.viewModel

import com.maxrave.common.Config
import com.maxrave.domain.data.entities.GoogleAccountEntity
import com.maxrave.domain.extension.toNetScapeString
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AccountRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.canImportYouTubeCookiesFromBrowser
import com.maxrave.simpmusic.expect.importYouTubeCookieCandidatesFromBrowser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull

class YouTubeAccountManager(
    private val dataStoreManager: DataStoreManager,
    private val commonRepository: CommonRepository,
    private val accountRepository: AccountRepository,
) {
    suspend fun addAccount(
        cookie: String,
        netscapeCookie: String? = null,
    ): Boolean {
        val currentCookie = dataStoreManager.cookie.first()
        val currentPageId = dataStoreManager.pageId.first()
        val currentLoggedIn = dataStoreManager.loggedIn.first() == DataStoreManager.TRUE
        try {
            dataStoreManager.setCookie(cookie, "")
            dataStoreManager.setLoggedIn(true)

            val accountInfoList =
                accountRepository
                    .getAccountInfo(cookie)
                    .lastOrNull()
                    .orEmpty()
            if (accountInfoList.isEmpty()) {
                Logger.w(TAG, "addAccount: Account info is null")
                restoreAccountState(currentCookie, currentPageId, currentLoggedIn)
                return false
            }

            Logger.d(TAG, "addAccount: validated ${accountInfoList.size} account(s): ${accountInfoList.joinToString { it.email }}")
            accountRepository.getGoogleAccounts().lastOrNull()?.forEach {
                Logger.d(TAG, "set used: email=${it.email} start")
                accountRepository
                    .updateGoogleAccountUsed(it.email, false)
                    .firstOrNull()
                    ?.let { updated ->
                        Logger.w(TAG, "set used: email=${it.email} updatedRows=$updated")
                    }
            }
            dataStoreManager.putString("AccountName", accountInfoList.first().name)
            dataStoreManager.putString(
                "AccountThumbUrl",
                accountInfoList
                    .first()
                    .thumbnails
                    .lastOrNull()
                    ?.url ?: "",
            )
            val cookieItem =
                netscapeCookie ?: commonRepository
                    .getCookiesFromInternalDatabase(Config.YOUTUBE_MUSIC_MAIN_URL, getPackageName())
                    .toNetScapeString()
            commonRepository.writeTextToFile(cookieItem, "${getFileDir()}/ytdlp-cookie.txt").let {
                Logger.d(TAG, "addAccount: write cookie file: $it")
            }
            accountInfoList.forEachIndexed { index, account ->
                accountRepository
                    .insertGoogleAccount(
                        GoogleAccountEntity(
                            email = account.email,
                            name = account.name,
                            thumbnailUrl =
                                account
                                    .thumbnails
                                    .lastOrNull()
                                    ?.url ?: "",
                            cache = cookie,
                            isUsed = index == 0,
                            netscapeCookie = cookieItem,
                            pageId = account.pageId,
                        ),
                    ).firstOrNull()
                    ?.let {
                        Logger.w(TAG, "addAccount: inserted email=${account.email} result=$it")
                    }
            }
            dataStoreManager.setLoggedIn(true)
            dataStoreManager.setCookie(cookie, accountInfoList.first().pageId)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.e(TAG, "addAccount: ${e.message}")
            restoreAccountState(currentCookie, currentPageId, currentLoggedIn)
            return false
        }
    }

    suspend fun storedCookieHasAccountInfo(): Boolean {
        val cookie = dataStoreManager.cookie.first()
        if (cookie.isBlank()) return false
        return accountRepository
            .getAccountInfo(cookie)
            .lastOrNull()
            ?.isNotEmpty() == true
    }

    suspend fun importBrowserCookiesAndAddAccount(): BrowserCookieAccountImportResult {
        if (!canImportYouTubeCookiesFromBrowser()) {
            return BrowserCookieAccountImportResult.Unsupported
        }

        val importedCandidates =
            runCatching {
                importYouTubeCookieCandidatesFromBrowser()
            }.getOrElse {
                Logger.e(TAG, "Browser cookie import failed: ${it.message}")
                return BrowserCookieAccountImportResult.Failure(it.message ?: "Unknown error")
            }

        if (importedCandidates.isEmpty()) {
            return BrowserCookieAccountImportResult.Failure("No authenticated YouTube browser profile was found.")
        }

        val failedSources = mutableListOf<String>()
        for (importedCookies in importedCandidates) {
            Logger.d(
                TAG,
                "Trying browser cookie candidate source=${importedCookies.sourceDescription} cookieCount=${importedCookies.cookieCount}",
            )
            val added =
                addAccount(
                    importedCookies.cookieHeader,
                    importedCookies.netscapeCookie,
                )
            if (added) {
                return BrowserCookieAccountImportResult.Success(
                    sourceDescription = importedCookies.sourceDescription,
                    cookieCount = importedCookies.cookieCount,
                )
            }
            failedSources += importedCookies.sourceDescription
        }

        return BrowserCookieAccountImportResult.Failure(
            "Browser cookies were imported but account validation failed for " +
                failedSources.joinToString(", "),
        )
    }

    private suspend fun restoreAccountState(
        cookie: String,
        pageId: String?,
        loggedIn: Boolean,
    ) {
        dataStoreManager.setCookie(cookie, pageId)
        dataStoreManager.setLoggedIn(loggedIn)
    }

    companion object {
        private const val TAG = "YouTubeAccountManager"
    }
}

sealed class BrowserCookieAccountImportResult {
    data class Success(
        val sourceDescription: String,
        val cookieCount: Int,
    ) : BrowserCookieAccountImportResult()

    data class Failure(
        val message: String,
    ) : BrowserCookieAccountImportResult()

    data object Unsupported : BrowserCookieAccountImportResult()
}

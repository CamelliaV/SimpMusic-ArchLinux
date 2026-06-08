package com.maxrave.simpmusic.expect

data class ImportedYouTubeCookies(
    val cookieHeader: String,
    val netscapeCookie: String,
    val sourceDescription: String,
    val cookieCount: Int,
)

class BrowserCookieImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

expect fun canImportYouTubeCookiesFromBrowser(): Boolean

expect suspend fun importYouTubeCookiesFromBrowser(): ImportedYouTubeCookies

expect suspend fun importYouTubeCookieCandidatesFromBrowser(): List<ImportedYouTubeCookies>

package com.maxrave.simpmusic.expect

actual fun canImportYouTubeCookiesFromBrowser(): Boolean = false

actual suspend fun importYouTubeCookiesFromBrowser(): ImportedYouTubeCookies =
    throw BrowserCookieImportException("Browser cookie import is only available on desktop.")

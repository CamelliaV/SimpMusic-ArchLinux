package com.maxrave.simpmusic.expect.ui

import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWebViewCookieManagerTest {
    private val originalCookieHandler = CookieHandler.getDefault()

    @AfterTest
    fun restoreCookieHandler() {
        CookieHandler.setDefault(originalCookieHandler)
    }

    @Test
    fun `returns empty cookie header when no default cookie handler is installed`() {
        CookieHandler.setDefault(null)

        assertEquals("", createWebViewCookieManager().getCookie("https://music.youtube.com/"))
    }

    @Test
    fun `reads cookies from the default JVM cookie manager`() {
        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        cookieManager.put(
            URI("https://music.youtube.com/"),
            mapOf(
                "Set-Cookie" to
                    listOf(
                        "SID=sid-value; Domain=.youtube.com; Path=/",
                        "SAPISID=sapisid-value; Domain=.youtube.com; Path=/",
                    ),
            ),
        )

        assertEquals(
            "SID=sid-value; SAPISID=sapisid-value",
            createWebViewCookieManager().getCookie("https://music.youtube.com/"),
        )
    }

    @Test
    fun `remove all cookies installs an empty JVM cookie manager`() {
        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        cookieManager.put(
            URI("https://music.youtube.com/"),
            mapOf("Set-Cookie" to listOf("SID=sid-value; Domain=.youtube.com; Path=/")),
        )

        createWebViewCookieManager().removeAllCookies()

        assertEquals("", createWebViewCookieManager().getCookie("https://music.youtube.com/"))
    }
}

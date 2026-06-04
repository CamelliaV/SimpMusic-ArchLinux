package com.maxrave.simpmusic.expect

import kotlin.test.Test
import kotlin.test.assertFalse

class DesktopGoogleLoginWebViewTest {
    @Test
    fun `desktop does not use embedded Google login WebView`() {
        assertFalse(useEmbeddedGoogleLoginWebView())
    }
}

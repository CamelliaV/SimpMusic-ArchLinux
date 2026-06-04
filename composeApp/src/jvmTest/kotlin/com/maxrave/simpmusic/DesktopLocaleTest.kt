package com.maxrave.simpmusic

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLocaleTest {
    @Test
    fun `selects supported language from simplified chinese system locale`() {
        assertEquals("zh-CN", supportedDesktopLanguage(Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `selects saved language before system locale`() {
        assertEquals(
            "fr-FR",
            resolveDesktopLanguage(
                savedLanguage = "fr-FR",
                manuallySelected = true,
                desktopLocaleText = "zh_CN.UTF-8",
                systemLocale = Locale.SIMPLIFIED_CHINESE,
            ),
        )
    }

    @Test
    fun `selects desktop messages locale before saved default when language was not manually selected`() {
        assertEquals(
            "zh-CN",
            resolveDesktopLanguage(
                savedLanguage = "en-US",
                manuallySelected = false,
                desktopLocaleText = "zh_CN.UTF-8",
                systemLocale = Locale.US,
            ),
        )
    }

    @Test
    fun `falls back to english for unsupported system locale`() {
        assertEquals("en-US", supportedDesktopLanguage(Locale.forLanguageTag("sv-SE")))
    }
}

package com.maxrave.simpmusic.expect

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class BrowserCookieImporterTest {
    @Test
    fun `decrypts chromium v11 cookie with host hash prefix`() {
        val password = "safe-storage-secret".toByteArray(StandardCharsets.UTF_8)
        val plaintext = ByteArray(CHROMIUM_HOST_HASH_PREFIX_LENGTH) { it.toByte() } + "cookie-value".toByteArray()
        val encryptedValue =
            "v11".toByteArray(StandardCharsets.US_ASCII) +
                encryptChromiumCookieValue(plaintext, deriveChromiumLinuxKey(password))

        assertEquals(
            "cookie-value",
            decryptLinuxChromiumCookie(
                encryptedValue = encryptedValue,
                metaVersion = CHROMIUM_META_VERSION_WITH_HOST_HASH,
                v11Password = password,
            ),
        )
    }

    @Test
    fun `builds music youtube cookie header and netscape cookie text`() {
        val imported =
            buildImportedYouTubeCookies(
                records =
                    listOf(
                        BrowserCookieRecord(
                            domain = ".youtube.com",
                            name = "SAPISID",
                            value = "sapi-value",
                            isSecure = true,
                            expiresUtc = 0,
                            path = "/",
                        ),
                        BrowserCookieRecord(
                            domain = "music.youtube.com",
                            name = "PREF",
                            value = "pref-value",
                            isSecure = false,
                            expiresUtc = 123,
                            path = "/",
                        ),
                        BrowserCookieRecord(
                            domain = ".google.com",
                            name = "SID",
                            value = "google-sid",
                            isSecure = true,
                            expiresUtc = 456,
                            path = "/",
                        ),
                    ),
                sourceDescription = "Chrome Default",
            )

        assertEquals("SAPISID=sapi-value; PREF=pref-value", imported.cookieHeader)
        assertEquals("Chrome Default", imported.sourceDescription)
        assertEquals(3, imported.cookieCount)
        assertFalse(imported.cookieHeader.contains("google-sid"))
        assertContains(imported.netscapeCookie, ".youtube.com\tTRUE\t/\tTRUE\t0\tSAPISID\tsapi-value")
        assertContains(imported.netscapeCookie, ".google.com\tTRUE\t/\tTRUE\t456\tSID\tgoogle-sid")
    }

    @Test
    fun `throws when browser cookies do not contain music youtube credentials`() {
        assertFailsWith<BrowserCookieImportException> {
            buildImportedYouTubeCookies(
                records =
                    listOf(
                        BrowserCookieRecord(
                            domain = ".google.com",
                            name = "SID",
                            value = "google-sid",
                            isSecure = true,
                            expiresUtc = 456,
                            path = "/",
                        ),
                    ),
                sourceDescription = "Chrome Default",
            )
        }
    }

    private fun encryptChromiumCookieValue(
        plaintext: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(ByteArray(CHROMIUM_AES_BLOCK_SIZE) { ' '.code.toByte() }),
        )
        return cipher.doFinal(plaintext)
    }
}

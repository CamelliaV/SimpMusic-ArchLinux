package com.maxrave.simpmusic.expect

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

actual fun canImportYouTubeCookiesFromBrowser(): Boolean =
    System.getProperty("os.name", "").contains("Linux", ignoreCase = true)

actual suspend fun importYouTubeCookiesFromBrowser(): ImportedYouTubeCookies =
    importYouTubeCookieCandidatesFromBrowser().first()

actual suspend fun importYouTubeCookieCandidatesFromBrowser(): List<ImportedYouTubeCookies> =
    withContext(Dispatchers.IO) {
        if (!canImportYouTubeCookiesFromBrowser()) {
            throw BrowserCookieImportException("Browser cookie import is currently available on Linux desktop only.")
        }

        val candidates = findChromiumCookieDatabases()
        if (candidates.isEmpty()) {
            throw BrowserCookieImportException("No Chromium-based browser cookie database was found.")
        }

        val errors = mutableListOf<String>()
        val recordSets =
            candidates.mapNotNull { candidate ->
                runCatching {
                    BrowserCookieRecordSet(
                        sourceDescription = candidate.sourceDescription,
                        records = readChromiumCookieRecords(candidate),
                    )
                }.getOrElse { error ->
                    errors += "${candidate.sourceDescription}: ${error.message ?: "read failed"}"
                    null
                }
            }
        val imported = buildImportedYouTubeCookieCandidates(recordSets)
        if (imported.isNotEmpty()) {
            return@withContext imported
        }

        throw BrowserCookieImportException(
            "Could not import a valid YouTube login from browser cookies." +
                errors.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\n", separator = "\n").orEmpty(),
        )
    }

internal const val CHROMIUM_AES_BLOCK_SIZE = 16
internal const val CHROMIUM_HOST_HASH_PREFIX_LENGTH = 32
internal const val CHROMIUM_META_VERSION_WITH_HOST_HASH = 24

internal data class BrowserCookieRecord(
    val domain: String,
    val name: String,
    val value: String,
    val isSecure: Boolean,
    val expiresUtc: Long,
    val path: String,
)

internal data class BrowserCookieRecordSet(
    val sourceDescription: String,
    val records: List<BrowserCookieRecord>,
)

internal fun buildImportedYouTubeCookieCandidates(recordSets: List<BrowserCookieRecordSet>): List<ImportedYouTubeCookies> =
    recordSets.mapNotNull { recordSet ->
        runCatching {
            buildImportedYouTubeCookies(
                records = recordSet.records,
                sourceDescription = recordSet.sourceDescription,
            )
        }.getOrNull()
    }

internal fun buildImportedYouTubeCookies(
    records: List<BrowserCookieRecord>,
    sourceDescription: String,
): ImportedYouTubeCookies {
    val relevantRecords =
        records
            .filter { it.value.isNotEmpty() && isGoogleOrYouTubeCookieDomain(it.domain) }
            .distinctBy { "${it.domain}\u0000${it.path}\u0000${it.name}" }

    val headerRecords =
        relevantRecords.filter {
            domainMatches("music.youtube.com", it.domain) && pathMatches("/", it.path)
        }
    val youtubeRecords =
        relevantRecords.filter {
            domainMatches("www.youtube.com", it.domain) && pathMatches("/", it.path)
        }

    val cookieHeader = headerRecords.joinToString("; ") { "${it.name}=${it.value}" }
    if (cookieHeader.isBlank() || !youtubeRecords.hasYtDlpAuthenticatedYouTubeCookies()) {
        throw BrowserCookieImportException("No authenticated YouTube browser cookie was found.")
    }
    Logger.d(
        "BrowserCookieImporter",
        "Built YouTube cookie candidate source=$sourceDescription headerNames=${headerRecords.map { it.name }.distinct().sorted()} youtubeAuthNames=${youtubeRecords.map { it.name }.filter { it in YOUTUBE_AUTH_COOKIE_NAMES }.distinct().sorted()} relevantDomainCounts=${relevantRecords.groupingBy { it.domain }.eachCount()}",
    )

    return ImportedYouTubeCookies(
        cookieHeader = cookieHeader,
        netscapeCookie = relevantRecords.toNetscapeCookieText(),
        sourceDescription = sourceDescription,
        cookieCount = relevantRecords.size,
    )
}

private val YOUTUBE_AUTH_COOKIE_NAMES =
    setOf("LOGIN_INFO", "SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID")

private fun List<BrowserCookieRecord>.hasYtDlpAuthenticatedYouTubeCookies(): Boolean {
    val names = map { it.name }.toSet()
    return "LOGIN_INFO" in names &&
        ("SAPISID" in names || "__Secure-1PAPISID" in names || "__Secure-3PAPISID" in names)
}

internal fun deriveChromiumLinuxKey(password: ByteArray): ByteArray {
    val salt = "saltysalt".toByteArray(StandardCharsets.UTF_8)
    val blockIndex = byteArrayOf(0, 0, 0, 1)
    return hmacSha1(password, salt + blockIndex).copyOf(CHROMIUM_AES_BLOCK_SIZE)
}

private fun hmacSha1(
    key: ByteArray,
    message: ByteArray,
): ByteArray {
    val blockSize = 64
    val normalizedKey =
        when {
            key.size > blockSize -> MessageDigest.getInstance("SHA-1").digest(key)
            else -> key
        }
    val paddedKey = ByteArray(blockSize)
    normalizedKey.copyInto(paddedKey)

    val outerPad = ByteArray(blockSize) { index -> (paddedKey[index].toInt() xor 0x5c).toByte() }
    val innerPad = ByteArray(blockSize) { index -> (paddedKey[index].toInt() xor 0x36).toByte() }
    val digest = MessageDigest.getInstance("SHA-1")
    val innerHash = digest.digest(innerPad + message)
    return digest.digest(outerPad + innerHash)
}

internal fun decryptLinuxChromiumCookie(
    encryptedValue: ByteArray,
    metaVersion: Int,
    v11Password: ByteArray?,
): String? {
    if (encryptedValue.size <= 3) return null
    val version = encryptedValue.copyOfRange(0, 3).toString(StandardCharsets.US_ASCII)
    val ciphertext = encryptedValue.copyOfRange(3, encryptedValue.size)
    val keys =
        when (version) {
            "v10" ->
                listOf(
                    deriveChromiumLinuxKey("peanuts".toByteArray(StandardCharsets.UTF_8)),
                    deriveChromiumLinuxKey(ByteArray(0)),
                )

            "v11" ->
                listOfNotNull(v11Password?.let(::deriveChromiumLinuxKey)) +
                    deriveChromiumLinuxKey(ByteArray(0))

            else -> return null
        }

    return keys.firstNotNullOfOrNull { key ->
        runCatching {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(ByteArray(CHROMIUM_AES_BLOCK_SIZE) { ' '.code.toByte() }),
            )
            val plaintext = cipher.doFinal(ciphertext)
            val valueBytes =
                if (metaVersion >= CHROMIUM_META_VERSION_WITH_HOST_HASH &&
                    plaintext.size >= CHROMIUM_HOST_HASH_PREFIX_LENGTH
                ) {
                    plaintext.copyOfRange(CHROMIUM_HOST_HASH_PREFIX_LENGTH, plaintext.size)
                } else {
                    plaintext
                }
            valueBytes.toString(StandardCharsets.UTF_8)
        }.getOrNull()
    }
}

private data class BrowserCookieDatabaseCandidate(
    val browser: ChromiumBrowserConfig,
    val cookiesPath: Path,
) {
    val sourceDescription: String =
        "${browser.displayName} ${cookiesPath.parent?.name ?: ""}".trim()
}

private data class ChromiumBrowserConfig(
    val displayName: String,
    val userDataDir: Path,
    val keyringName: String,
)

private fun findChromiumCookieDatabases(): List<BrowserCookieDatabaseCandidate> =
    chromiumBrowserConfigs()
        .flatMap { browser ->
            if (!browser.userDataDir.exists() || !browser.userDataDir.isDirectory()) {
                emptyList()
            } else {
                Files
                    .walk(browser.userDataDir)
                    .use { stream ->
                        stream
                            .filter { it.name == "Cookies" }
                            .map { BrowserCookieDatabaseCandidate(browser, it) }
                            .toList()
                    }
            }
        }.sortedByDescending { Files.getLastModifiedTime(it.cookiesPath).toMillis() }

private fun chromiumBrowserConfigs(): List<ChromiumBrowserConfig> {
    val configHome =
        System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".config")

    return listOf(
        ChromiumBrowserConfig("Google Chrome", configHome.resolve("google-chrome"), "Chrome"),
        ChromiumBrowserConfig("Google Chrome Unstable", configHome.resolve("google-chrome-unstable"), "Chrome"),
        ChromiumBrowserConfig("Chromium", configHome.resolve("chromium"), "Chromium"),
        ChromiumBrowserConfig("Brave", configHome.resolve("BraveSoftware/Brave-Browser"), "Brave"),
        ChromiumBrowserConfig("Microsoft Edge", configHome.resolve("microsoft-edge"), "Chromium"),
        ChromiumBrowserConfig("Vivaldi", configHome.resolve("vivaldi"), "Chrome"),
    )
}

private fun readChromiumCookieRecords(candidate: BrowserCookieDatabaseCandidate): List<BrowserCookieRecord> {
    val tempDir = Files.createTempDirectory("simpmusic-browser-cookies")
    return try {
        val tempDb = tempDir.resolve("Cookies")
        Files.copy(candidate.cookiesPath, tempDb, StandardCopyOption.REPLACE_EXISTING)
        BundledSQLiteDriver().open(tempDb.absolutePathString()).use { connection ->
            val metaVersion = readChromiumMetaVersion(connection)
            val secureColumn = if ("is_secure" in readColumnNames(connection, "cookies")) "is_secure" else "secure"
            val v11Password = readLinuxChromiumV11Password(candidate.browser.keyringName)
            readChromiumCookieRows(
                connection = connection,
                metaVersion = metaVersion,
                secureColumn = secureColumn,
                v11Password = v11Password,
            )
        }
    } catch (e: BrowserCookieImportException) {
        throw e
    } catch (e: Exception) {
        throw BrowserCookieImportException("Failed to read ${candidate.cookiesPath}", e)
    } finally {
        runCatching {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(Files::deleteIfExists)
        }
    }
}

private fun readChromiumMetaVersion(connection: SQLiteConnection): Int =
    connection
        .prepare("SELECT value FROM meta WHERE key = 'version'")
        .use { statement ->
            if (statement.step()) statement.getText(0).toIntOrNull() ?: 0 else 0
        }

private fun readColumnNames(
    connection: SQLiteConnection,
    tableName: String,
): Set<String> =
    connection
        .prepare("PRAGMA table_info($tableName)")
        .use { statement ->
            buildSet {
                while (statement.step()) {
                    add(statement.getText(1))
                }
            }
        }

private fun readChromiumCookieRows(
    connection: SQLiteConnection,
    metaVersion: Int,
    secureColumn: String,
    v11Password: ByteArray?,
): List<BrowserCookieRecord> =
    connection
        .prepare("SELECT host_key, name, value, encrypted_value, path, expires_utc, $secureColumn FROM cookies")
        .use { statement ->
            buildList {
                while (statement.step()) {
                    val domain = statement.getText(0)
                    if (!isGoogleOrYouTubeCookieDomain(domain)) continue

                    val value =
                        statement.getTextOrEmpty(2).ifEmpty {
                            statement
                                .getBlobOrNull(3)
                                ?.let {
                                    decryptLinuxChromiumCookie(
                                        encryptedValue = it,
                                        metaVersion = metaVersion,
                                        v11Password = v11Password,
                                    )
                                }.orEmpty()
                        }

                    if (value.isEmpty()) continue

                    add(
                        BrowserCookieRecord(
                            domain = domain,
                            name = statement.getText(1),
                            value = value,
                            isSecure = statement.getLong(6) == 1L,
                            expiresUtc = statement.getLong(5),
                            path = statement.getTextOrEmpty(4).ifEmpty { "/" },
                        ),
                    )
                }
            }
        }

private fun SQLiteStatement.getTextOrEmpty(columnIndex: Int): String =
    if (isNull(columnIndex)) "" else getText(columnIndex)

private fun SQLiteStatement.getBlobOrNull(columnIndex: Int): ByteArray? =
    if (isNull(columnIndex)) null else getBlob(columnIndex)

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }

private fun readLinuxChromiumV11Password(browserKeyringName: String): ByteArray? =
    when (chooseLinuxKeyring()) {
        LinuxKeyring.KWALLET,
        LinuxKeyring.KWALLET5,
        LinuxKeyring.KWALLET6,
        -> readKWalletPassword(browserKeyringName)

        LinuxKeyring.BASIC_TEXT -> null
    }

private enum class LinuxKeyring {
    KWALLET,
    KWALLET5,
    KWALLET6,
    BASIC_TEXT,
}

private fun chooseLinuxKeyring(): LinuxKeyring {
    val desktop = System.getenv("XDG_CURRENT_DESKTOP").orEmpty()
    val kdeVersion = System.getenv("KDE_SESSION_VERSION").orEmpty()
    val desktopSession = System.getenv("DESKTOP_SESSION").orEmpty()

    return if (desktop.split(":").any { it.equals("KDE", ignoreCase = true) } ||
        desktopSession.contains("plasma", ignoreCase = true)
    ) {
        when (kdeVersion) {
            "6" -> LinuxKeyring.KWALLET6
            "5" -> LinuxKeyring.KWALLET5
            else -> LinuxKeyring.KWALLET5
        }
    } else {
        LinuxKeyring.BASIC_TEXT
    }
}

private fun readKWalletPassword(browserKeyringName: String): ByteArray {
    val networkWallet = readKWalletNetworkWallet().ifBlank { "kdewallet" }
    val result =
        runProcess(
            listOf(
                "kwallet-query",
                "--read-password",
                "$browserKeyringName Safe Storage",
                "--folder",
                "$browserKeyringName Keys",
                networkWallet,
            ),
        )

    if (result.exitCode != 0 || result.stdout.startsWith("failed to read", ignoreCase = true)) {
        return ByteArray(0)
    }
    return result.stdout.trimEnd('\n').toByteArray(StandardCharsets.UTF_8)
}

private fun readKWalletNetworkWallet(): String {
    val keyring = chooseLinuxKeyring()
    val serviceName =
        when (keyring) {
            LinuxKeyring.KWALLET -> "org.kde.kwalletd"
            LinuxKeyring.KWALLET5 -> "org.kde.kwalletd5"
            LinuxKeyring.KWALLET6 -> "org.kde.kwalletd6"
            LinuxKeyring.BASIC_TEXT -> return ""
        }
    val walletPath =
        when (keyring) {
            LinuxKeyring.KWALLET -> "/modules/kwalletd"
            LinuxKeyring.KWALLET5 -> "/modules/kwalletd5"
            LinuxKeyring.KWALLET6 -> "/modules/kwalletd6"
            LinuxKeyring.BASIC_TEXT -> return ""
        }

    return runProcess(
        listOf(
            "dbus-send",
            "--session",
            "--print-reply=literal",
            "--dest=$serviceName",
            walletPath,
            "org.kde.KWallet.networkWallet",
        ),
    ).takeIf { it.exitCode == 0 }?.stdout?.trim().orEmpty()
}

private data class ProcessResult(
    val stdout: String,
    val exitCode: Int,
)

private fun runProcess(command: List<String>): ProcessResult =
    runCatching {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching ProcessResult("", -1)
        }
        ProcessResult(
            stdout = process.inputStream.bufferedReader().readText(),
            exitCode = process.exitValue(),
        )
    }.getOrDefault(ProcessResult("", -1))

private fun isGoogleOrYouTubeCookieDomain(domain: String): Boolean {
    val host = domain.trimStart('.').lowercase()
    return host == "youtube.com" ||
        host.endsWith(".youtube.com") ||
        host == "google.com" ||
        host.endsWith(".google.com")
}

private fun domainMatches(
    targetHost: String,
    cookieDomain: String,
): Boolean {
    val domain = cookieDomain.lowercase()
    val target = targetHost.lowercase()
    return if (domain.startsWith(".")) {
        target == domain.drop(1) || target.endsWith(domain)
    } else {
        target == domain
    }
}

private fun pathMatches(
    targetPath: String,
    cookiePath: String,
): Boolean =
    targetPath.startsWith(cookiePath.ifBlank { "/" })

private fun List<BrowserCookieRecord>.toNetscapeCookieText(): String =
    buildString {
        appendLine("# Netscape HTTP Cookie File")
        appendLine("# Browser cookies imported by SimpMusic.")
        appendLine("# This is a generated file! Do not edit.")
        appendLine()
        for (cookie in this@toNetscapeCookieText) {
            append(cookie.domain)
            append('\t')
            append(if (cookie.domain.startsWith(".")) "TRUE" else "FALSE")
            append('\t')
            append(cookie.path.ifBlank { "/" })
            append('\t')
            append(if (cookie.isSecure) "TRUE" else "FALSE")
            append('\t')
            append(cookie.expiresUtc)
            append('\t')
            append(cookie.name)
            append('\t')
            append(cookie.value)
            appendLine()
        }
    }

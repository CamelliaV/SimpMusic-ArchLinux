package com.maxrave.simpmusic

import com.maxrave.common.SUPPORTED_LANGUAGE
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal fun resolveDesktopLanguage(
    savedLanguage: String?,
    manuallySelected: Boolean = false,
    desktopLocaleText: String? = readDesktopLocaleText(),
    systemLocale: Locale = Locale.getDefault(),
): String =
    if (manuallySelected) {
        normalizedSupportedDesktopLanguageCode(savedLanguage) ?: supportedDesktopLanguage(systemLocale)
    } else {
        supportedDesktopLanguageFromText(desktopLocaleText) ?: supportedDesktopLanguage(systemLocale)
    }

internal fun supportedDesktopLanguageFromText(localeText: String?): String? {
    val normalizedText = localeText?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (normalizedText == "C" ||
        normalizedText.startsWith("C.") ||
        normalizedText.equals("POSIX", ignoreCase = true)
    ) {
        return null
    }
    val languageTag =
        normalizedText
            .substringBefore('.')
            .replace('_', '-')
    return supportedDesktopLanguage(Locale.forLanguageTag(languageTag))
}

internal fun supportedDesktopLanguage(locale: Locale): String {
    val language = locale.language.lowercase()
    val country = locale.country.uppercase()
    val script = locale.script

    return when {
        language == "zh" && (script.equals("Hant", ignoreCase = true) || country in setOf("TW", "HK", "MO")) -> "zh-Hant-TW"
        language == "zh" -> "zh-CN"
        language == "he" || language == "iw" -> "iw-IL"
        language == "id" || language == "in" -> "id-ID"
        else -> SUPPORTED_LANGUAGE.codes.firstOrNull { code ->
            code.substringBefore('-').equals(language, ignoreCase = true)
        } ?: SUPPORTED_LANGUAGE.codes.first()
    }
}

private fun normalizedSupportedDesktopLanguageCode(code: String?): String? {
    val normalized =
        when (code?.takeIf { it.isNotBlank() }) {
            "he-IL" -> "iw-IL"
            "in-ID" -> "id-ID"
            else -> code
    }
    return normalized?.takeIf { it in SUPPORTED_LANGUAGE.codes }
}

private fun readDesktopLocaleText(): String? =
    System.getenv("LC_MESSAGES")
        ?.takeIf { supportedDesktopLanguageFromText(it) != null }
        ?: readPlasmaLocaleRcMessages()

private fun readPlasmaLocaleRcMessages(): String? {
    val configHome =
        System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".config")
    val path = configHome.resolve("plasma-localerc")
    if (!Files.exists(path)) return null

    var inFormatsSection = false
    return runCatching {
        Files.readAllLines(path)
            .firstNotNullOfOrNull { rawLine ->
                val line = rawLine.trim()
                when {
                    line == "[Formats]" -> {
                        inFormatsSection = true
                        null
                    }
                    line.startsWith("[") -> {
                        inFormatsSection = false
                        null
                    }
                    inFormatsSection && line.startsWith("LC_MESSAGES=") ->
                        line.substringAfter('=').trim().takeIf { it.isNotBlank() }
                    else -> null
                }
            }
    }.getOrNull()
}

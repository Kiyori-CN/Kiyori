package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import androidx.compose.runtime.Immutable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
internal enum class BrowserDiagnosticLevel {
    INFO,
    WARNING,
    ERROR,
}

@Immutable
internal enum class BrowserDiagnosticCategory {
    PROVIDER,
    CAPABILITY,
    SESSION,
    NAVIGATION,
    WEBVIEW,
    PERMISSION,
    POPUP,
    USERSCRIPT,
    MEDIA,
    DOWNLOAD,
}

@Immutable
internal enum class BrowserDiagnosticScope {
    CURRENT_SESSION,
    ALL_SESSIONS,
}

@Immutable
internal data class BrowserDiagnosticEntry(
    val timestamp: Long,
    val sequence: Long = 0L,
    val repeatCount: Int = 1,
    val level: BrowserDiagnosticLevel,
    val category: BrowserDiagnosticCategory,
    val event: String,
    val sessionId: String? = null,
    val profile: WebSessionProfile? = null,
    val documentToken: String? = null,
    val host: String = "",
    val message: String = "",
    val details: Map<String, String> = emptyMap(),
)

internal class BrowserDiagnosticLog(
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val lock = Any()
    private val entries = mutableListOf<BrowserDiagnosticEntry>()
    private var nextSequence = 0L

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun append(entry: BrowserDiagnosticEntry) {
        val sanitized = sanitizeBrowserDiagnosticEntry(entry)
        synchronized(lock) {
            val previousIndex =
                entries.indexOfLast { previous ->
                    shouldCoalesceBrowserDiagnostic(previous, sanitized)
                }
            if (previousIndex >= 0) {
                val previous = entries[previousIndex]
                val updated =
                    previous.copy(
                        timestamp = maxOf(previous.timestamp, sanitized.timestamp),
                        repeatCount =
                            (previous.repeatCount.toLong() + sanitized.repeatCount.toLong())
                                .coerceAtMost(MAX_REPEAT_COUNT.toLong())
                                .toInt(),
                    )
                entries.removeAt(previousIndex)
                entries += updated
                return
            }
            nextSequence += 1L
            entries += sanitized.copy(sequence = nextSequence)
            if (entries.size > maxEntries) {
                entries.subList(0, entries.size - maxEntries).clear()
            }
        }
    }

    fun snapshot(): List<BrowserDiagnosticEntry> =
        synchronized(lock) { entries.toList() }

    fun clear(scope: BrowserDiagnosticScope, sessionId: String?) {
        synchronized(lock) {
            when (scope) {
                BrowserDiagnosticScope.ALL_SESSIONS -> entries.clear()
                BrowserDiagnosticScope.CURRENT_SESSION -> {
                    if (sessionId == null) return
                    entries.removeAll { entry -> entry.sessionId == sessionId }
                }
            }
        }
    }

    companion object {
        internal const val MAX_ENTRIES = 1_000
    }
}

internal fun filterBrowserDiagnosticEntries(
    entries: List<BrowserDiagnosticEntry>,
    scope: BrowserDiagnosticScope,
    activeSessionId: String?,
    level: BrowserDiagnosticLevel?,
    category: BrowserDiagnosticCategory?,
    query: String,
): List<BrowserDiagnosticEntry> {
    val normalizedQuery = query.trim()
    return entries
        .asSequence()
        .filter { entry ->
            scope == BrowserDiagnosticScope.ALL_SESSIONS ||
                (activeSessionId != null && entry.sessionId == activeSessionId)
        }
        .filter { entry -> level == null || entry.level == level }
        .filter { entry -> category == null || entry.category == category }
        .filter { entry ->
            normalizedQuery.isBlank() ||
                entry.event.contains(normalizedQuery, ignoreCase = true) ||
                entry.category.name.contains(normalizedQuery, ignoreCase = true) ||
                entry.host.contains(normalizedQuery, ignoreCase = true) ||
                entry.message.contains(normalizedQuery, ignoreCase = true) ||
                entry.sessionId?.contains(normalizedQuery, ignoreCase = true) == true ||
                entry.details.any { (key, value) ->
                    key.contains(normalizedQuery, ignoreCase = true) ||
                        value.contains(normalizedQuery, ignoreCase = true)
                }
        }
        .sortedWith(
            compareByDescending<BrowserDiagnosticEntry> { entry -> entry.timestamp }
                .thenByDescending { entry -> entry.sequence },
        )
        .toList()
}

internal fun formatBrowserDiagnosticReport(
    entries: List<BrowserDiagnosticEntry>,
): String {
    val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return buildString {
        appendLine("Kiyori Browser Diagnostics")
        appendLine("entries=${entries.size}")
        entries
            .asSequence()
            .map(::sanitizeBrowserDiagnosticEntry)
            .sortedWith(
                compareBy<BrowserDiagnosticEntry> { entry -> entry.timestamp }
                    .thenBy { entry -> entry.sequence },
            )
            .forEach { entry ->
                append('[')
                append(timestampFormat.format(Date(entry.timestamp)))
                append("] [")
                append(entry.level.name)
                append("] [")
                append(entry.category.name)
                append("] ")
                append(entry.event)
                entry.sessionId?.let { sessionId -> append(" session=").append(sessionId) }
                entry.profile?.let { profile -> append(" profile=").append(profile.wireName) }
                entry.documentToken?.let { token -> append(" document=").append(token) }
                entry.host.takeIf(String::isNotBlank)?.let { host -> append(" host=").append(host) }
                entry.message.takeIf(String::isNotBlank)?.let { message -> append(" message=").append(message) }
                entry.details.forEach { (key, value) ->
                    append(' ').append(key).append('=').append(value)
                }
                if (entry.repeatCount > 1) append(" repeatCount=").append(entry.repeatCount)
                appendLine()
            }
    }.trimEnd()
}

internal fun sanitizeBrowserDiagnosticEntry(
    entry: BrowserDiagnosticEntry,
): BrowserDiagnosticEntry {
    val sanitizedMessage = sanitizeBrowserDiagnosticMessage(entry.message)
    val sanitizedDetails =
        entry.details
            .asSequence()
            .filter { (key, _) -> key.isNotBlank() }
            .take(MAX_DETAIL_FIELDS)
            .associate { (key, value) ->
                key.take(MAX_DETAIL_KEY_LENGTH) to sanitizeBrowserDiagnosticValue(key, value)
            }
    return entry.copy(
        repeatCount = entry.repeatCount.coerceIn(1, MAX_REPEAT_COUNT),
        event = sanitizeBrowserDiagnosticMessage(entry.event, MAX_EVENT_LENGTH),
        sessionId = entry.sessionId?.trim()?.take(MAX_ID_LENGTH),
        documentToken = entry.documentToken?.trim()?.take(MAX_ID_LENGTH),
        host = sanitizeBrowserDiagnosticHost(entry.host),
        message = sanitizedMessage,
        details = sanitizedDetails,
    )
}

private fun shouldCoalesceBrowserDiagnostic(
    previous: BrowserDiagnosticEntry,
    current: BrowserDiagnosticEntry,
): Boolean {
    if (!current.event.startsWith("CONSOLE_")) return false
    if (previous.event != current.event || previous.level != current.level || previous.category != current.category) {
        return false
    }
    if (previous.sessionId != current.sessionId || previous.profile != current.profile) return false
    if (previous.documentToken != current.documentToken || previous.host != current.host) return false
    if (previous.message != current.message || previous.details != current.details) return false
    val elapsed = current.timestamp - previous.timestamp
    return elapsed in 0..BROWSER_DIAGNOSTIC_COALESCE_WINDOW_MILLIS
}

internal fun sanitizeBrowserDiagnosticUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    val host = uri?.host?.lowercase(Locale.ROOT)
    if (uri != null && scheme != null && host != null) {
        val query =
            uri.rawQuery
                ?.split('&')
                ?.filter(String::isNotBlank)
                ?.joinToString("&") { part ->
                    val key = part.substringBefore('=').trim().take(MAX_QUERY_KEY_LENGTH)
                    if (key.isBlank()) "<redacted>=<redacted>" else "$key=<redacted>"
                }
                ?.takeIf(String::isNotBlank)
        val displayHost =
            if (host.contains(':') && !host.startsWith('[')) {
                "[$host]"
            } else {
                host
            }
        val port =
            uri.port.takeIf { value ->
                value >= 0 && !(scheme == "http" && value == 80) && !(scheme == "https" && value == 443)
            }
        return buildString {
                append(scheme)
                append("://")
                append(displayHost)
                port?.let { value -> append(':').append(value) }
                append(sanitizeBrowserDiagnosticUrlPath(uri.rawPath.orEmpty().ifBlank { "/" }))
                query?.let { value -> append('?').append(value) }
            }
            .take(MAX_URL_LENGTH)
    }
    if (scheme != null) {
        return when (scheme) {
            "about" -> "about:${uri.rawSchemeSpecificPart.orEmpty().substringBefore('?').take(MAX_URL_LENGTH - 6)}"
            else -> "$scheme:<redacted>"
        }
    }
    return trimmed
        .substringBefore('#')
        .replace(Regex("([?&][^=&#\\s]+)=([^&#\\s]*)"), "$1=<redacted>")
        .replace(SENSITIVE_ASSIGNMENT_REGEX, "$1=<redacted>")
        .take(MAX_URL_LENGTH)
}

private fun sanitizeBrowserDiagnosticUrlPath(rawPath: String): String {
    var redactNextSegment = false
    return rawPath
        .split('/')
        .joinToString("/") { segment ->
            val sanitized =
                when {
                    redactNextSegment -> "<redacted>"
                    segment.contains('=') && segment.substringBefore('=').isSensitiveDiagnosticKey() ->
                        segment.substringBefore('=') + "=<redacted>"
                    else -> segment
                }
            redactNextSegment = !segment.contains('=') && segment.isSensitiveDiagnosticKey()
            sanitized
        }
}

private fun sanitizeBrowserDiagnosticHost(rawHost: String): String =
    rawHost.trim().lowercase(Locale.ROOT).take(MAX_HOST_LENGTH)

private fun sanitizeBrowserDiagnosticValue(key: String, rawValue: String): String {
    val normalizedKey = key.lowercase(Locale.ROOT)
    if (
        normalizedKey.contains("header") ||
            normalizedKey.contains("path") ||
            normalizedKey.isSensitiveDiagnosticKey() ||
            normalizedKey.isUntrustedPageContentKey()
    ) {
        return "<redacted>"
    }
    if (
        normalizedKey.contains("url") ||
            normalizedKey.contains("uri") ||
            normalizedKey.contains("origin")
    ) {
        return sanitizeBrowserDiagnosticUrl(rawValue)
    }
    return sanitizeBrowserDiagnosticMessage(rawValue, MAX_DETAIL_VALUE_LENGTH)
}

private fun sanitizeBrowserDiagnosticMessage(
    rawMessage: String,
    maxLength: Int = MAX_MESSAGE_LENGTH,
): String {
    var value = rawMessage.replace(Regex("https?://[^\\s]+"), "<url>")
    value = value.replace(SENSITIVE_HEADER_REGEX, "$1=<redacted>")
    value = value.replace(SENSITIVE_ASSIGNMENT_REGEX, "$1=<redacted>")
    value = value.replace(SENSITIVE_BEARER_REGEX, "Bearer <redacted>")
    return value.replace(Regex("\\s+"), " ").trim().take(maxLength)
}

private fun String.isUntrustedPageContentKey(): Boolean {
    val normalized = lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
    return normalized in UNTRUSTED_PAGE_CONTENT_KEYS
}

private fun String.isSensitiveDiagnosticKey(): Boolean {
    val normalized = lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")
    return SENSITIVE_KEY_NAMES.any { sensitive ->
        normalized == sensitive || normalized.endsWith(sensitive)
    }
}

private const val MAX_ENTRIES = BrowserDiagnosticLog.MAX_ENTRIES
private const val MAX_EVENT_LENGTH = 64
private const val MAX_ID_LENGTH = 96
private const val MAX_HOST_LENGTH = 128
private const val MAX_MESSAGE_LENGTH = 280
private const val MAX_DETAIL_FIELDS = 12
private const val MAX_DETAIL_KEY_LENGTH = 48
private const val MAX_DETAIL_VALUE_LENGTH = 180
private const val MAX_QUERY_KEY_LENGTH = 48
private const val MAX_URL_LENGTH = 512
private const val BROWSER_DIAGNOSTIC_COALESCE_WINDOW_MILLIS = 1_000L
private const val MAX_REPEAT_COUNT = 1_000_000

private val SENSITIVE_KEY_NAMES =
    setOf(
        "authorization",
        "proxyauthorization",
        "bearer",
        "cookie",
        "setcookie",
        "token",
        "accesstoken",
        "refreshtoken",
        "secret",
        "password",
        "passwd",
        "apikey",
        "signature",
        "sig",
        "credential",
    )
private val UNTRUSTED_PAGE_CONTENT_KEYS =
    setOf(
        "body",
        "content",
        "html",
        "pagetitle",
        "consolemessage",
        "dialogmessage",
        "promptvalue",
        "defaultvalue",
        "text",
    )
private val SENSITIVE_HEADER_REGEX =
    Regex("(?im)\\b(cookie|set-cookie|authorization|proxy[-_ ]?authorization)\\b\\s*:\\s*[^\\r\\n]*")
private val SENSITIVE_BEARER_REGEX =
    Regex("(?i)\\bbearer\\s+[^\\s,;&#]+")
private val SENSITIVE_ASSIGNMENT_REGEX =
    Regex(
        "(?i)\\b(cookie|set-cookie|authorization|proxy[-_ ]?authorization|bearer|token|access[-_ ]?token|refresh[-_ ]?token|secret|password|passwd|api[-_ ]?key|signature|sig|credential)\\b\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;&#]+)",
    )

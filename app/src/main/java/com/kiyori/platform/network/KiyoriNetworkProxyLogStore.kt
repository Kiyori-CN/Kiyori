package com.kiyori.platform.network

import java.net.URI
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KiyoriNetworkProxyLogLevel {
    INFO,
    WARNING,
    ERROR,
}

data class KiyoriNetworkProxyLogEntry(
    val id: Long,
    val timestampEpochMillis: Long,
    val level: KiyoriNetworkProxyLogLevel,
    val source: String,
    val message: String,
)

data class KiyoriNetworkProxyProcessContext(
    val processName: String,
    val processId: Int,
)

/**
 * Process-local diagnostic history for the single Kiyori proxy owner.
 *
 * Core output is useful only when its rejection reason survives, but the same line can contain
 * connection credentials or private paths. Every entry therefore passes one strict redaction
 * boundary before it reaches observable state, clipboard, or SAF export.
 */
object KiyoriNetworkProxyLogStore {
    private const val MAX_ENTRIES = 1_000
    private const val MAX_SOURCE_CHARS = 48
    private const val MAX_MESSAGE_CHARS = 1_000
    private const val MAX_RAW_MESSAGE_CHARS = 4_000

    private val lock = Any()
    private val buffer = ArrayDeque<KiyoriNetworkProxyLogEntry>(MAX_ENTRIES)
    private val mutableEntries = MutableStateFlow<List<KiyoriNetworkProxyLogEntry>>(emptyList())
    val entries: StateFlow<List<KiyoriNetworkProxyLogEntry>> = mutableEntries.asStateFlow()
    private var nextId = 1L
    private var droppedEntryCount = 0L
    private var processContext = KiyoriNetworkProxyProcessContext("unknown", 0)

    internal fun setProcessContext(processName: String, processId: Int) {
        val normalizedName =
            processName
                .replace(CONTROL_CHARACTERS, " ")
                .trim()
                .take(MAX_PROCESS_NAME_CHARS)
                .ifBlank { "unknown" }
        synchronized(lock) {
            processContext =
                KiyoriNetworkProxyProcessContext(
                    processName = normalizedName,
                    processId = processId.coerceAtLeast(0),
                )
        }
    }

    fun info(source: String, message: String) {
        append(KiyoriNetworkProxyLogLevel.INFO, source, message)
    }

    fun warning(source: String, message: String) {
        append(KiyoriNetworkProxyLogLevel.WARNING, source, message)
    }

    fun error(source: String, message: String) {
        append(KiyoriNetworkProxyLogLevel.ERROR, source, message)
    }

    internal fun core(source: String, message: String): String? =
        append(levelForCoreLine(message), source, message)

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            droppedEntryCount = 0L
            mutableEntries.value = emptyList()
        }
    }

    fun exportText(): String =
        synchronized(lock) {
            buildString {
                appendLine("Kiyori network proxy log")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Scope: current application process; sensitive values are redacted")
                appendLine(
                    "Process: ${processContext.processName} pid=${processContext.processId}",
                )
                appendLine("Entries: ${buffer.size} dropped=$droppedEntryCount")
                appendLine()
                buffer.forEach { entry ->
                    append(Instant.ofEpochMilli(entry.timestampEpochMillis))
                    append(" [")
                    append(entry.level.name)
                    append("] [")
                    append(entry.source)
                    append("] ")
                    appendLine(entry.message)
                }
            }
        }

    internal fun redact(rawMessage: String): String =
        redactMessage(rawMessage.take(MAX_RAW_MESSAGE_CHARS)).take(MAX_MESSAGE_CHARS)

    private fun append(
        level: KiyoriNetworkProxyLogLevel,
        rawSource: String,
        rawMessage: String,
    ): String? {
        val source = rawSource.replace(CONTROL_CHARACTERS, " ").trim().take(MAX_SOURCE_CHARS)
        val message = redact(rawMessage)
        if (source.isEmpty() || message.isEmpty()) return null
        synchronized(lock) {
            buffer.addLast(
                KiyoriNetworkProxyLogEntry(
                    id = nextId++,
                    timestampEpochMillis = System.currentTimeMillis(),
                    level = level,
                    source = source,
                    message = message,
                ),
            )
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
                droppedEntryCount += 1
            }
            mutableEntries.value = buffer.toList()
        }
        return message
    }

    private fun redactMessage(rawMessage: String): String {
        var message = rawMessage.replace(CONTROL_CHARACTERS, " ").trim()
        message = PROXY_URI.replace(message, "[proxy-uri-redacted]")
        message = HTTP_URL.replace(message) { match ->
            val scheme = match.groups[1]?.value?.lowercase().orEmpty()
            val authority = match.groups[2]?.value.orEmpty()
            val host =
                runCatching { URI("$scheme://$authority").host }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: "host"
            "$scheme://$host/[redacted]"
        }
        message = BEARER.replace(message, "Bearer [redacted]")
        message = SECRET_ASSIGNMENT.replace(message) { match ->
            "${match.groups[1]?.value}${match.groups[2]?.value}[redacted]"
        }
        message = UUID.replace(message, "[uuid-redacted]")
        message = ANDROID_PRIVATE_PATH.replace(message, "[private-path]")
        message = WINDOWS_PRIVATE_PATH.replace(message, "[private-path]")
        message = LONG_CREDENTIAL.replace(message, "[credential-redacted]")
        return message.replace(REPEATED_WHITESPACE, " ").trim()
    }

    private fun levelForCoreLine(line: String): KiyoriNetworkProxyLogLevel {
        val normalized = line.lowercase()
        return when {
            "error" in normalized || "fatal" in normalized || "failed" in normalized ->
                KiyoriNetworkProxyLogLevel.ERROR
            "warn" in normalized -> KiyoriNetworkProxyLogLevel.WARNING
            else -> KiyoriNetworkProxyLogLevel.INFO
        }
    }

    private const val MAX_PROCESS_NAME_CHARS = 96
    private val CONTROL_CHARACTERS = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\r\\n\\t]+")
    private val REPEATED_WHITESPACE = Regex(" {2,}")
    private val PROXY_URI =
        Regex("(?i)\\b(?:ss|ssr|vmess|vless|trojan|hysteria2?|tuic|wireguard)://\\S+")
    private val HTTP_URL =
        Regex("(?i)\\b(https?)://(?:[^@/\\s]+@)?([^/\\s?#]+)(?:[/?#][^\\s]*)?")
    private val BEARER = Regex("(?i)\\bBearer\\s+[^\\s,;]+")
    private val SECRET_ASSIGNMENT =
        Regex(
            "(?i)\\b(password|passwd|secret|token|authorization|uuid|private-key|public-key|short-id|psk)" +
                "(\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,}\\]]+)",
        )
    private val UUID =
        Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val ANDROID_PRIVATE_PATH = Regex("/(?:data|storage|sdcard)/(?:[^\\s,;]+)")
    private val WINDOWS_PRIVATE_PATH = Regex("(?i)\\b[A-Z]:\\\\[^\\s,;]+")
    private val LONG_CREDENTIAL = Regex("(?i)\\b[A-Za-z0-9_+/-]{40,}={0,2}\\b")
}

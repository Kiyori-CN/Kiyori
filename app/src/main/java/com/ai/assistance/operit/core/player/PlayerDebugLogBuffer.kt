package com.ai.assistance.operit.core.player

import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class PlayerDebugLogLevel(
    val wireValue: Int,
    val label: String,
    val displayName: String,
) {
    DEBUG(1, "D", "调试"),
    INFO(2, "I", "信息"),
    WARN(3, "W", "警告"),
    ERROR(4, "E", "错误"),
    ;

    companion object {
        fun fromWireValue(value: Int): PlayerDebugLogLevel? =
            entries.singleOrNull { level -> level.wireValue == value }
    }
}

internal enum class PlayerDebugLogTopic {
    NETWORK_AND_LOADING,
    PLAYBACK_CONTROL,
    SURFACE_AND_RENDER,
    TRACKS,
    RUNTIME_AND_MPV,
}

internal enum class PlayerDebugLogFilter(
    val displayName: String,
) {
    ALL("全部"),
    ERROR_ONLY("错误"),
    WARN_AND_ERROR("警告及错误"),
    NETWORK_AND_LOADING("网络与加载"),
    PLAYBACK_CONTROL("播放控制"),
    SURFACE_AND_RENDER("画面与 Surface"),
    TRACKS("音轨与字幕"),
    RUNTIME_AND_MPV("运行时与 MPV"),
    ;

    fun accepts(
        level: PlayerDebugLogLevel,
        topics: Set<PlayerDebugLogTopic>,
    ): Boolean =
        when (this) {
            ALL -> true
            WARN_AND_ERROR ->
                level == PlayerDebugLogLevel.WARN || level == PlayerDebugLogLevel.ERROR
            ERROR_ONLY -> level == PlayerDebugLogLevel.ERROR
            NETWORK_AND_LOADING ->
                PlayerDebugLogTopic.NETWORK_AND_LOADING in topics
            PLAYBACK_CONTROL ->
                PlayerDebugLogTopic.PLAYBACK_CONTROL in topics
            SURFACE_AND_RENDER ->
                PlayerDebugLogTopic.SURFACE_AND_RENDER in topics
            TRACKS ->
                PlayerDebugLogTopic.TRACKS in topics
            RUNTIME_AND_MPV ->
                PlayerDebugLogTopic.RUNTIME_AND_MPV in topics
        }
}

internal data class PlayerDebugLogLine(
    val id: Long,
    val timestamp: String,
    val level: PlayerDebugLogLevel,
    val tag: String,
    val message: String,
) {
    val formattedText: String
        get() = "$timestamp ${level.label}/$tag: $message"
}

internal data class PlayerDebugLogSnapshot(
    val text: String,
    val entries: List<PlayerDebugLogLine>,
    val lineCount: Int,
    val totalLineCount: Int,
    val droppedLineCount: Long,
)

private data class PlayerDebugLogEntry(
    val id: Long,
    val timestampEpochMs: Long,
    val level: PlayerDebugLogLevel,
    val tag: String,
    val message: String,
    val topics: Set<PlayerDebugLogTopic>,
)

internal object PlayerDebugLogBuffer {
    private const val MAX_LINES = 2_000
    private val lock = Any()
    private val lines = ArrayDeque<PlayerDebugLogEntry>(MAX_LINES)
    private var droppedLineCount = 0L
    private var nextEntryId = 1L
    private val mutableRevision = MutableStateFlow(0L)

    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLineCount = 0L
        }
        mutableRevision.update { revision -> revision + 1L }
    }

    fun append(tag: String, message: String) {
        append(PlayerDebugLogLevel.INFO, tag, message)
    }

    fun append(
        level: PlayerDebugLogLevel,
        tag: String,
        message: String,
    ) {
        val normalizedTag =
            tag.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
                .take(MAX_TAG_CHARS)
                .ifBlank { "Player" }
        val normalizedMessage = sanitizePlayerDiagnosticMessage(message)
        if (normalizedMessage.isBlank()) return
        synchronized(lock) {
            while (lines.size >= MAX_LINES) {
                lines.removeFirst()
                droppedLineCount += 1L
            }
            lines.addLast(
                PlayerDebugLogEntry(
                    id = nextEntryId++,
                    timestampEpochMs = System.currentTimeMillis(),
                    level = level,
                    tag = normalizedTag,
                    message = normalizedMessage,
                    topics = classifyPlayerDebugLogTopics(normalizedTag, normalizedMessage),
                ),
            )
        }
        mutableRevision.update { revision -> revision + 1L }
    }

    fun snapshot(): String = snapshotState().text

    fun snapshotState(
        filter: PlayerDebugLogFilter = PlayerDebugLogFilter.ALL,
    ): PlayerDebugLogSnapshot =
        synchronized(lock) {
            val matchingEntries =
                lines
                    .filter { entry ->
                        filter.accepts(
                            level = entry.level,
                            topics = entry.topics,
                        )
                    }
                    .map(::toPublicLine)
            PlayerDebugLogSnapshot(
                text = matchingEntries.joinToString("\n") { entry -> entry.formattedText },
                entries = matchingEntries,
                lineCount = matchingEntries.size,
                totalLineCount = lines.size,
                droppedLineCount = droppedLineCount,
            )
        }

    private fun toPublicLine(entry: PlayerDebugLogEntry): PlayerDebugLogLine =
        PlayerDebugLogLine(
            id = entry.id,
            timestamp = LOG_TIME_FORMATTER.format(Instant.ofEpochMilli(entry.timestampEpochMs)),
            level = entry.level,
            tag = entry.tag,
            message = entry.message,
        )

    private const val MAX_TAG_CHARS = 80
    private val LOG_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
}

internal fun classifyPlayerDebugLogTopics(
    tag: String,
    message: String,
): Set<PlayerDebugLogTopic> {
    val normalized = "$tag $message".lowercase(Locale.ROOT)

    // 同一条日志可以同时属于多个诊断主题，避免按单一 tag 切分后丢失跨层调用上下文。
    return buildSet {
        if (PLAYER_NETWORK_LOADING_KEYWORDS.any(normalized::contains)) {
            add(PlayerDebugLogTopic.NETWORK_AND_LOADING)
        }
        if (PLAYER_PLAYBACK_CONTROL_KEYWORDS.any(normalized::contains)) {
            add(PlayerDebugLogTopic.PLAYBACK_CONTROL)
        }
        if (PLAYER_SURFACE_RENDER_KEYWORDS.any(normalized::contains)) {
            add(PlayerDebugLogTopic.SURFACE_AND_RENDER)
        }
        if (PLAYER_TRACK_KEYWORDS.any(normalized::contains)) {
            add(PlayerDebugLogTopic.TRACKS)
        }
        if (PLAYER_RUNTIME_MPV_KEYWORDS.any(normalized::contains)) {
            add(PlayerDebugLogTopic.RUNTIME_AND_MPV)
        }
    }
}

internal fun sanitizePlayerDiagnosticMessage(message: String): String {
    var sanitized =
        message
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    if (sanitized.isBlank()) return ""

    sanitized =
        PLAYER_DIAGNOSTIC_URL_REGEX.replace(sanitized) { match ->
            val rawValue = match.value
            val url = rawValue.trimEnd('.', ',', ';', ')', ']', '}')
            val suffix = rawValue.substring(url.length)
            describePlayerMediaUriForDiagnostics(url) + suffix
        }
    sanitized =
        PLAYER_DIAGNOSTIC_HEADER_BLOCK_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
    sanitized =
        PLAYER_DIAGNOSTIC_SECRET_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
    sanitized =
        PLAYER_DIAGNOSTIC_PRIVATE_PATH_REGEX.replace(sanitized, "<private-path>")
    return sanitized.take(MAX_DIAGNOSTIC_MESSAGE_CHARS)
}

internal fun describePlayerMediaUriForDiagnostics(rawUri: String): String {
    val parsed =
        try {
            URI(rawUri)
        } catch (_: Exception) {
            return "invalid-uri(length=${rawUri.length})"
        }
    val scheme = parsed.scheme?.lowercase().orEmpty()
    return when (scheme) {
        "http",
        "https",
        "rtmp",
        "rtmps",
        "rtsp",
        "ftp",
        "sftp",
        -> {
            val host = parsed.host?.takeIf(String::isNotBlank) ?: "<unknown-host>"
            val authority =
                if (parsed.port >= 0) {
                    "$host:${parsed.port}"
                } else {
                    host
                }
            val path = parsed.rawPath?.takeIf(String::isNotBlank) ?: "/"
            val query = if (parsed.rawQuery.isNullOrBlank()) "" else "?<redacted>"
            "$scheme://$authority$path$query"
        }
        "content" -> {
            val authority = parsed.rawAuthority?.takeIf(String::isNotBlank) ?: "<unknown-authority>"
            "content://$authority/<redacted>"
        }
        "file" -> "file://<redacted>"
        "" -> "relative-uri(length=${rawUri.length})"
        else -> "$scheme:<redacted>"
    }
}

internal fun shortPlayerDiagnosticId(value: String): String {
    if (value.isBlank()) return "none"
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val MAX_DIAGNOSTIC_MESSAGE_CHARS = 1_200
private val PLAYER_NETWORK_LOADING_KEYWORDS =
    listOf(
        "http",
        "uri",
        "url",
        "media",
        "load",
        "buffer",
        "network",
        "request",
        "resolver",
        "加载",
        "缓冲",
        "网络",
        "媒体",
        "请求",
    )
private val PLAYER_PLAYBACK_CONTROL_KEYWORDS =
    listOf(
        "pause",
        "seek",
        "speed",
        "position",
        "playback",
        "end-file",
        "eof",
        "file-loaded",
        "resume",
        "播放",
        "暂停",
        "跳转",
        "倍速",
        "结束",
    )
private val PLAYER_SURFACE_RENDER_KEYWORDS =
    listOf(
        "surface",
        "video",
        " vo ",
        "reconfig",
        "render",
        "gpu",
        "vulkan",
        "anime4k",
        "wid",
        "画面",
        "渲染",
        "超分",
    )
private val PLAYER_TRACK_KEYWORDS =
    listOf(
        "track",
        "audio",
        "subtitle",
        "aid=",
        "sid=",
        "音轨",
        "字幕",
        "声道",
    )
private val PLAYER_RUNTIME_MPV_KEYWORDS =
    listOf(
        "runtime",
        "mpv",
        "command",
        "generation",
        "pid=",
        "binder",
        "connection",
        "connect",
        "disconnect",
        "运行时",
        "命令",
        "绑定",
    )
private val PLAYER_DIAGNOSTIC_URL_REGEX =
    Regex(
        """(?i)\b(?:https?|rtmp|rtmps|rtsp|ftp|sftp|content|file)://[^\s"'<>]+""",
    )
private val PLAYER_DIAGNOSTIC_HEADER_BLOCK_REGEX =
    Regex("""(?i)\b(http-header-fields|request-headers|headers)\s*[:=]\s*[^\r\n]*""")
private val PLAYER_DIAGNOSTIC_SECRET_REGEX =
    Regex(
        """(?i)\b(authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[-_]?key|access[-_]?token|refresh[-_]?token)\s*[:=]\s*[^,;|]+""",
    )
private val PLAYER_DIAGNOSTIC_PRIVATE_PATH_REGEX =
    Regex(
        """(?i)(?:/data/(?:user/\d+|data)/[^/\s]+|/storage/emulated/\d+|/sdcard)/[^\s,;]+""",
    )

package com.ai.assistance.operit.core.ffmpeg.runtime

import java.io.File

internal fun resolveFfmpegRuntimeTerminalState(returnCode: Int): FFmpegRuntimeTerminalState =
    when (returnCode) {
        FFMPEG_RUNTIME_SUCCESS_RETURN_CODE -> FFmpegRuntimeTerminalState.SUCCEEDED
        FFMPEG_RUNTIME_CANCEL_RETURN_CODE -> FFmpegRuntimeTerminalState.CANCELLED
        else -> FFmpegRuntimeTerminalState.FAILED
    }

internal fun boundFfmpegRuntimeDiagnostic(value: String?): String? =
    value
        ?.takeIf(String::isNotBlank)
        ?.let { text ->
            if (text.length <= FFMPEG_RUNTIME_MAX_DIAGNOSTIC_CHARS) {
                text
            } else {
                text.take(FFMPEG_RUNTIME_MAX_DIAGNOSTIC_CHARS) +
                    "\n[diagnostic truncated at $FFMPEG_RUNTIME_MAX_DIAGNOSTIC_CHARS characters]"
            }
        }

internal fun ffmpegRuntimeLogDirectory(cacheDir: File): File =
    File(cacheDir, FFMPEG_RUNTIME_LOG_DIRECTORY_NAME)

internal fun ffmpegRuntimeLogFile(cacheDir: File, requestId: String): File {
    require(FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)) {
        "Invalid FFmpeg runtime request ID"
    }
    return File(ffmpegRuntimeLogDirectory(cacheDir), "$requestId.log")
}

internal fun resolveFfmpegRuntimeLogFile(cacheDir: File, outputLogPath: String): File {
    val root = ffmpegRuntimeLogDirectory(cacheDir).canonicalFile
    val file = File(outputLogPath).canonicalFile
    require(
        file.parentFile == root &&
            FFMPEG_RUNTIME_LOG_FILE_NAME_PATTERN.matches(file.name),
    ) {
        "FFmpeg runtime returned a log outside its private directory"
    }
    return file
}

internal fun readFfmpegRuntimeOutput(cacheDir: File, outputLogPath: String): String {
    val file = resolveFfmpegRuntimeLogFile(cacheDir, outputLogPath)
    return if (file.isFile) file.readText(Charsets.UTF_8) else ""
}

internal class FFmpegRuntimeEventCursor(private val runtimeGeneration: Long) {
    private var lastSequence = 0L

    fun accept(eventGeneration: Long, eventSequence: Long): Boolean {
        if (
            eventGeneration != runtimeGeneration ||
                eventSequence <= 0L ||
                eventSequence <= lastSequence
        ) {
            return false
        }
        lastSequence = eventSequence
        return true
    }
}

internal const val FFMPEG_RUNTIME_SUCCESS_RETURN_CODE = 0
internal const val FFMPEG_RUNTIME_CANCEL_RETURN_CODE = 255
internal const val FFMPEG_RUNTIME_LOG_DIRECTORY_NAME = "ffmpeg-runtime"
internal const val FFMPEG_RUNTIME_MAX_DIAGNOSTIC_CHARS = 16_384
private val FFMPEG_RUNTIME_LOG_FILE_NAME_PATTERN = Regex("[0-9a-f]{32}\\.log")

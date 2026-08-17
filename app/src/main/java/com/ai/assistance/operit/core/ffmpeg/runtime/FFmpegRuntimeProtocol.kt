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

internal data class FFmpegRuntimeLogRetentionResult(
    val examinedFiles: Int,
    val deletedFiles: Int,
    val retainedFiles: Int,
    val retainedBytes: Long,
)

internal fun pruneFfmpegRuntimeLogs(
    cacheDir: File,
    protectedLogPaths: Set<String> = emptySet(),
    nowMillis: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = FFMPEG_RUNTIME_LOG_MAX_AGE_MILLIS,
    maxFiles: Int = FFMPEG_RUNTIME_LOG_MAX_FILES,
    maxTotalBytes: Long = FFMPEG_RUNTIME_LOG_MAX_TOTAL_BYTES,
): FFmpegRuntimeLogRetentionResult {
    require(nowMillis >= 0L) { "FFmpeg log retention clock is invalid" }
    require(maxAgeMillis >= 0L) { "FFmpeg log maximum age is invalid" }
    require(maxFiles >= 0) { "FFmpeg log maximum file count is invalid" }
    require(maxTotalBytes >= 0L) { "FFmpeg log maximum total size is invalid" }

    val directory = ffmpegRuntimeLogDirectory(cacheDir)
    if (!directory.isDirectory) {
        return FFmpegRuntimeLogRetentionResult(0, 0, 0, 0L)
    }
    val protectedCanonicalPaths =
        protectedLogPaths.mapTo(mutableSetOf()) { path ->
            resolveFfmpegRuntimeLogFile(cacheDir, path).canonicalPath
        }
    val discovered =
        directory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && FFMPEG_RUNTIME_LOG_FILE_NAME_PATTERN.matches(file.name)
            }
    var deletedFiles = 0
    discovered
        .filterNot { file -> file.canonicalPath in protectedCanonicalPaths }
        .filter { file -> nowMillis - file.lastModified().coerceAtMost(nowMillis) > maxAgeMillis }
        .forEach { file ->
            if (file.delete()) {
                deletedFiles += 1
            }
        }

    val remaining =
        directory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && FFMPEG_RUNTIME_LOG_FILE_NAME_PATTERN.matches(file.name)
            }
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))
            .toMutableList()
    var retainedBytes = remaining.sumOf(File::length)
    val removable =
        remaining
            .filterNot { file -> file.canonicalPath in protectedCanonicalPaths }
            .toMutableList()
    while (
        (remaining.size > maxFiles || retainedBytes > maxTotalBytes) &&
            removable.isNotEmpty()
    ) {
        val candidate = removable.removeAt(0)
        val candidateBytes = candidate.length()
        if (candidate.delete()) {
            remaining.remove(candidate)
            retainedBytes -= candidateBytes
            deletedFiles += 1
        }
    }
    return FFmpegRuntimeLogRetentionResult(
        examinedFiles = discovered.size,
        deletedFiles = deletedFiles,
        retainedFiles = remaining.size,
        retainedBytes = retainedBytes.coerceAtLeast(0L),
    )
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
internal const val FFMPEG_RUNTIME_MAX_LOG_BYTES = 4 * 1024 * 1024
internal const val FFMPEG_RUNTIME_LOG_MAX_FILES = 32
internal const val FFMPEG_RUNTIME_LOG_MAX_TOTAL_BYTES = 32L * 1024L * 1024L
internal const val FFMPEG_RUNTIME_LOG_MAX_AGE_MILLIS = 48L * 60L * 60L * 1_000L
internal const val FFMPEG_RUNTIME_LOG_TRUNCATION_MARKER =
    "\n[Kiyori] FFmpeg runtime log truncated at 4194304 UTF-8 bytes.\n"
internal val FFMPEG_RUNTIME_LOG_FILE_NAME_PATTERN = Regex("[0-9a-f]{32}\\.log")

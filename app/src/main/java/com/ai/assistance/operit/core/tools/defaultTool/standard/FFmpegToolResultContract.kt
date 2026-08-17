package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeInformation
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeInformationSection
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeMediaInformation
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeProcessDiedException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeRequestException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeResponse
import com.ai.assistance.operit.core.tools.FFmpegResultData
import java.io.File
import java.util.Locale

internal enum class FFmpegConversionPipelineStage(val wireValue: String) {
    VALIDATION("validation"),
    TRANSCODE("transcode"),
    STAGED_OUTPUT("staged_output"),
    PROBE("probe"),
    OUTPUT_CONTRACT("output_contract"),
    ATOMIC_COMMIT("atomic_commit"),
    CLEANUP("cleanup"),
}

internal fun FFmpegRuntimeResponse.toFFmpegResultData(
    command: String,
    outputFile: String? = null,
    mediaInformation: FFmpegRuntimeMediaInformation? = result.mediaInformation,
    pipelineStage: String? = null,
): FFmpegResultData =
    FFmpegResultData(
        command = command,
        returnCode = result.returnCode,
        output = output,
        duration = result.durationMillis,
        outputFile = outputFile,
        mediaInfo = mediaInformation?.toToolMediaInfo(),
        terminalState = result.terminalState.name.lowercase(Locale.ROOT),
        processId = result.processId,
        sessionId = result.sessionId,
        pipelineStage = pipelineStage,
    )

internal fun FFmpegRuntimeRequestException.toFFmpegResultData(
    command: String,
    pipelineStage: String,
): FFmpegResultData =
    FFmpegResultData(
        command = command,
        returnCode = null,
        output = output,
        duration = 0L,
        terminalState = "protocol_failure",
        processId = failure.processId,
        sessionId = failure.sessionId.takeIf { value -> value > 0L },
        pipelineStage = pipelineStage,
        failureCode = failure.failureCode.name.lowercase(Locale.ROOT),
    )

internal fun FFmpegRuntimeProcessDiedException.toFFmpegResultData(
    command: String,
    pipelineStage: String,
): FFmpegResultData =
    FFmpegResultData(
        command = command,
        returnCode = null,
        output = partialOutput,
        duration = 0L,
        terminalState = "process_died",
        processId = processId,
        sessionId = sessionId,
        pipelineStage = pipelineStage,
        failureCode = "runtime_process_died",
        diagnosticLogPath = diagnosticLogPath,
    )

internal fun FFmpegRuntimeMediaInformation.toToolMediaInfo(): FFmpegResultData.MediaInfo {
    val videoStreams =
        streams
            .filter { stream -> stream.type.equals("video", ignoreCase = true) }
            .map { stream ->
                FFmpegResultData.StreamInfo(
                    index = stream.index,
                    codecType = stream.type ?: "unknown",
                    codecName = stream.codec ?: "unknown",
                    profile = stream.profile,
                    pixelFormat = stream.pixelFormat,
                    resolution =
                        if (stream.width != null && stream.height != null) {
                            "${stream.width}x${stream.height}"
                        } else {
                            null
                        },
                    frameRate = stream.realFrameRate,
                )
            }
    val audioStreams =
        streams
            .filter { stream -> stream.type.equals("audio", ignoreCase = true) }
            .map { stream ->
                FFmpegResultData.StreamInfo(
                    index = stream.index,
                    codecType = stream.type ?: "unknown",
                    codecName = stream.codec ?: "unknown",
                    profile = stream.profile,
                    pixelFormat = stream.pixelFormat,
                    sampleRate = stream.sampleRate,
                    channels = stream.channels,
                )
            }
    return FFmpegResultData.MediaInfo(
        format = format ?: "unknown",
        duration = duration ?: "0",
        bitrate = bitrate ?: "0",
        videoStreams = videoStreams,
        audioStreams = audioStreams,
    )
}

internal fun parseFfmpegProbeInput(inputPath: String?): File {
    require(!inputPath.isNullOrBlank()) { "Must provide input_path parameter" }
    require(inputPath.length <= FFMPEG_PROBE_MAX_PATH_CHARS) {
        "FFprobe input path exceeds $FFMPEG_PROBE_MAX_PATH_CHARS characters"
    }
    val input = File(inputPath)
    require(input.isAbsolute) { "FFprobe input path must be absolute" }
    require(input.isFile && input.length() > 0L) {
        "FFprobe input file does not exist or is empty: ${input.path}"
    }
    return input
}

internal fun validateRawFfmpegCommand(command: String?): String {
    require(!command.isNullOrBlank()) { "Must provide command parameter" }
    val scan = scanFfmpegCommandForValidation(command)
    val firstToken = scan.tokens.firstOrNull().orEmpty()
    require(firstToken.isNotEmpty()) {
        "ffmpeg_execute command does not contain an FFmpeg argument"
    }
    require(!firstToken.isFfmpegExecutablePrefix()) {
        "ffmpeg_execute accepts arguments only; remove the leading $firstToken"
    }
    require(scan.unprotectedShellOperator == null) {
        "ffmpeg_execute is not a shell and cannot execute unquoted operator " +
            "${scan.unprotectedShellOperator}; quote or escape literal filter values, " +
            "use ffmpeg_info(section) for Android capability lists, or " +
            "super_admin:terminal for an explicitly requested Ubuntu shell command"
    }
    return command
}

private fun String.isFfmpegExecutablePrefix(): Boolean =
    lowercase(Locale.ROOT)
        .replace('\\', '/')
        .substringAfterLast('/') in FFMPEG_EXECUTABLE_PREFIX_NAMES

private data class FFmpegCommandValidationScan(
    val tokens: List<String>,
    val unprotectedShellOperator: String?,
)

private fun scanFfmpegCommandForValidation(
    command: String,
): FFmpegCommandValidationScan {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var tokenStarted = false
    var unprotectedShellOperator: String? = null

    fun finishToken() {
        if (!tokenStarted) {
            return
        }
        tokens += current.toString()
        current.setLength(0)
        tokenStarted = false
    }

    var index = 0
    while (index < command.length) {
        val character = command[index]
        if (escaped) {
            current.append(character)
            tokenStarted = true
            escaped = false
            index += 1
            continue
        }
        if (character == '\\') {
            escaped = true
            tokenStarted = true
            index += 1
            continue
        }
        if (quote != null) {
            if (character == quote) {
                quote = null
            } else {
                current.append(character)
            }
            tokenStarted = true
            index += 1
            continue
        }
        when {
            character == '\'' || character == '"' -> {
                quote = character
                tokenStarted = true
            }
            character.isWhitespace() -> finishToken()
            character in FFMPEG_SHELL_OPERATOR_CHARACTERS -> {
                if (unprotectedShellOperator == null) {
                    unprotectedShellOperator = command.shellOperatorAt(index)
                }
                current.append(character)
                tokenStarted = true
            }
            else -> {
                current.append(character)
                tokenStarted = true
            }
        }
        index += 1
    }
    require(quote == null) { "ffmpeg_execute command contains an unterminated quote" }
    require(!escaped) { "ffmpeg_execute command ends with an incomplete escape" }
    finishToken()
    return FFmpegCommandValidationScan(
        tokens = tokens,
        unprotectedShellOperator = unprotectedShellOperator,
    )
}

private fun String.shellOperatorAt(index: Int): String {
    val first = this[index]
    val second = getOrNull(index + 1)
    val third = getOrNull(index + 2)
    return when {
        first == '<' && second == '<' && third == '<' -> "<<<"
        first == '|' && second == '|' -> "||"
        first == '&' && second == '&' -> "&&"
        first == '>' && second == '>' -> ">>"
        first == '<' && second == '<' -> "<<"
        else -> first.toString()
    }
}

internal fun formatFfprobeCommandForDisplay(inputPath: String): String =
    "ffprobe " +
        formatFfmpegArgumentsForDisplay(
            listOf(
                "-v",
                "error",
                "-hide_banner",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                "-show_entries",
                FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES,
                "-i",
                inputPath,
            ),
        )

internal fun buildFfmpegRuntimeInformationOutput(
    information: FFmpegRuntimeInformation,
    nativeOutput: String,
): String =
    buildString {
        appendLine("Execution plane: ${information.executionPlane}")
        appendLine("Android process: ${information.processName}")
        appendLine("Section: ${information.section.wireValue}")
        appendLine("FFmpeg version: ${information.ffmpegVersion}")
        appendLine("FFmpegKit wrapper version: ${information.wrapperVersion}")
        appendLine("Build date: ${information.buildDate}")
        appendLine("ABI / Android API: ${information.abi} / ${information.androidApi}")
        appendLine("Qualified conversion profile: ${information.qualifiedProfile}")
        appendLine("Return code semantics: signed_ffmpeg_averror")
        if (information.section == FFmpegRuntimeInformationSection.SUMMARY) {
            appendLine()
            appendLine("Tool routing:")
            appendLine("- ffmpeg_info(section): query this Android FFmpegKit runtime.")
            appendLine("- ffmpeg_probe(input_path): inspect media with Android FFprobe.")
            appendLine("- ffmpeg_convert(...): run the qualified deterministic conversion pipeline.")
            appendLine("- ffmpeg_execute(command): execute raw FFmpeg arguments without rewriting them.")
            appendLine("- This runtime does not invoke Ubuntu /usr/bin/ffmpeg or the :player FFmpeg closure.")
            appendLine("- It does not use a shell, switch execution planes, retry, or change encoders.")
            appendLine(
                "- Android drawtext commands must provide an absolute fontfile, for example " +
                    "/system/fonts/Roboto-Regular.ttf.",
            )
        }
        if (nativeOutput.isNotBlank()) {
            appendLine()
            appendLine("Native FFmpeg output:")
            append(nativeOutput)
        }
    }

internal fun describeFfmpegConversionFailure(
    stage: FFmpegConversionPipelineStage,
    detail: String,
): String {
    val prefix =
        when (stage) {
            FFmpegConversionPipelineStage.VALIDATION ->
                "FFmpeg conversion parameter validation failed"
            FFmpegConversionPipelineStage.TRANSCODE ->
                "Android FFmpeg transcode failed"
            FFmpegConversionPipelineStage.STAGED_OUTPUT ->
                "Android FFmpeg transcode returned success, but the staged output was invalid"
            FFmpegConversionPipelineStage.PROBE ->
                "Android FFmpeg transcode succeeded; output verification failed during Android FFprobe"
            FFmpegConversionPipelineStage.OUTPUT_CONTRACT ->
                "Android FFmpeg transcode and FFprobe succeeded; output contract validation failed"
            FFmpegConversionPipelineStage.ATOMIC_COMMIT ->
                "Android FFmpeg output passed validation, but atomic commit failed"
            FFmpegConversionPipelineStage.CLEANUP ->
                "FFmpeg conversion temporary-output cleanup failed"
        }
    return "$prefix: $detail"
}

private const val FFMPEG_PROBE_MAX_PATH_CHARS = 4_096
private val FFMPEG_EXECUTABLE_PREFIX_NAMES = setOf("ffmpeg", "ffmpeg.exe")
private val FFMPEG_SHELL_OPERATOR_CHARACTERS = setOf('|', '&', ';', '>', '<')

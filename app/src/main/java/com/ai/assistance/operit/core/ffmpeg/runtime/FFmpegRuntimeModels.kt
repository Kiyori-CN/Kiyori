package com.ai.assistance.operit.core.ffmpeg.runtime

import android.os.Parcelable
import java.io.File
import java.util.Locale
import kotlinx.parcelize.Parcelize

internal enum class FFmpegRuntimeOperation(val wireValue: Int) {
    EXECUTE_COMMAND(1),
    EXECUTE_ARGUMENTS(2),
    PROBE_MEDIA(3),
    RUNTIME_INFO(4);

    companion object {
        fun fromWireValue(value: Int): FFmpegRuntimeOperation =
            requireNotNull(entries.singleOrNull { operation -> operation.wireValue == value }) {
                "Unsupported FFmpeg runtime operation: $value"
            }
    }
}

internal enum class FFmpegRuntimeTerminalState(val wireValue: Int) {
    SUCCEEDED(1),
    FAILED(2),
    CANCELLED(3);

    companion object {
        fun fromWireValue(value: Int): FFmpegRuntimeTerminalState =
            requireNotNull(entries.singleOrNull { state -> state.wireValue == value }) {
                "Unsupported FFmpeg runtime terminal state: $value"
            }
    }
}

internal enum class FFmpegRuntimeFailureCode(val wireValue: Int) {
    INVALID_REQUEST(1),
    DUPLICATE_REQUEST(2),
    DISPATCH_FAILURE(3),
    CALLBACK_REPLACED(4),
    CALLBACK_DISCONNECTED(5),
    SERVICE_DESTROYED(6),
    MEDIA_INFORMATION_INVALID(7),
    NATIVE_RETURN_CODE_MISSING(8);

    companion object {
        fun fromWireValue(value: Int): FFmpegRuntimeFailureCode =
            requireNotNull(entries.singleOrNull { code -> code.wireValue == value }) {
                "Unsupported FFmpeg runtime failure code: $value"
            }
    }
}

internal enum class FFmpegRuntimeInformationSection(
    val wireValue: String,
    val arguments: List<String>,
) {
    SUMMARY("summary", listOf("-version")),
    CODECS("codecs", listOf("-codecs")),
    ENCODERS("encoders", listOf("-encoders")),
    DECODERS("decoders", listOf("-decoders")),
    FILTERS("filters", listOf("-filters")),
    FORMATS("formats", listOf("-formats")),
    MUXERS("muxers", listOf("-muxers")),
    DEMUXERS("demuxers", listOf("-demuxers")),
    PROTOCOLS("protocols", listOf("-protocols")),
    HWACCELS("hwaccels", listOf("-hwaccels")),
    BUILDCONF("buildconf", listOf("-buildconf"));

    companion object {
        val default: FFmpegRuntimeInformationSection = SUMMARY

        fun parse(value: String?): FFmpegRuntimeInformationSection {
            if (value == null) {
                return default
            }
            require(value.isNotBlank()) { "FFmpeg information section cannot be blank" }
            return requireNotNull(
                entries.singleOrNull { section ->
                    section.wireValue == value.lowercase(Locale.ROOT)
                },
            ) {
                "Unsupported FFmpeg information section: $value"
            }
        }

        fun fromWireValue(value: String): FFmpegRuntimeInformationSection =
            requireNotNull(entries.singleOrNull { section -> section.wireValue == value }) {
                "Unsupported FFmpeg information section: $value"
            }
    }
}

@Parcelize
internal data class FFmpegRuntimeRequest(
    val requestId: String,
    val operationWireValue: Int,
    val command: String? = null,
    val arguments: List<String> = emptyList(),
    val inputPath: String? = null,
    val informationSectionWireValue: String? = null,
) : Parcelable {
    init {
        require(FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)) {
            "Invalid FFmpeg runtime request ID"
        }
        when (FFmpegRuntimeOperation.fromWireValue(operationWireValue)) {
            FFmpegRuntimeOperation.EXECUTE_COMMAND -> {
                require(!command.isNullOrBlank()) { "FFmpeg command is blank" }
                require(command.length <= FFMPEG_RUNTIME_MAX_COMMAND_CHARS) {
                    "FFmpeg command exceeds $FFMPEG_RUNTIME_MAX_COMMAND_CHARS characters"
                }
                require(arguments.isEmpty() && inputPath == null && informationSectionWireValue == null) {
                    "FFmpeg command request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.EXECUTE_ARGUMENTS -> {
                require(arguments.isNotEmpty() && arguments.none(String::isBlank)) {
                    "FFmpeg argument request is empty or malformed"
                }
                require(arguments.size <= FFMPEG_RUNTIME_MAX_ARGUMENT_COUNT) {
                    "FFmpeg argument request exceeds $FFMPEG_RUNTIME_MAX_ARGUMENT_COUNT entries"
                }
                require(arguments.all { argument -> argument.length <= FFMPEG_RUNTIME_MAX_ARGUMENT_CHARS }) {
                    "FFmpeg argument exceeds $FFMPEG_RUNTIME_MAX_ARGUMENT_CHARS characters"
                }
                require(arguments.sumOf(String::length) <= FFMPEG_RUNTIME_MAX_ARGUMENT_TOTAL_CHARS) {
                    "FFmpeg argument request exceeds $FFMPEG_RUNTIME_MAX_ARGUMENT_TOTAL_CHARS characters"
                }
                require(command == null && inputPath == null && informationSectionWireValue == null) {
                    "FFmpeg argument request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.PROBE_MEDIA -> {
                require(!inputPath.isNullOrBlank() && File(inputPath).isAbsolute) {
                    "FFprobe input path must be absolute"
                }
                require(inputPath.length <= FFMPEG_RUNTIME_MAX_PATH_CHARS) {
                    "FFprobe input path exceeds $FFMPEG_RUNTIME_MAX_PATH_CHARS characters"
                }
                require(
                    command == null &&
                        arguments.isEmpty() &&
                        informationSectionWireValue == null,
                ) {
                    "FFprobe request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.RUNTIME_INFO -> {
                FFmpegRuntimeInformationSection.fromWireValue(
                    requireNotNull(informationSectionWireValue) {
                        "FFmpeg runtime info section is missing"
                    },
                )
                require(command == null && arguments.isEmpty() && inputPath == null) {
                    "FFmpeg runtime info request contains payload"
                }
            }
        }
    }

    val operation: FFmpegRuntimeOperation
        get() = FFmpegRuntimeOperation.fromWireValue(operationWireValue)

    val informationSection: FFmpegRuntimeInformationSection
        get() {
            check(operation == FFmpegRuntimeOperation.RUNTIME_INFO) {
                "FFmpeg request is not a runtime information request"
            }
            return FFmpegRuntimeInformationSection.fromWireValue(
                requireNotNull(informationSectionWireValue),
            )
        }
}

@Parcelize
internal data class FFmpegRuntimeStatistics(
    val videoFrameNumber: Int,
    val videoFps: Float,
    val videoQuality: Float,
    val sizeBytes: Long,
    val timeMillis: Double,
    val bitrateKbitsPerSecond: Double,
    val speed: Double,
) : Parcelable

@Parcelize
internal data class FFmpegRuntimeStreamInformation(
    val index: Int,
    val type: String?,
    val codec: String?,
    val profile: String?,
    val pixelFormat: String?,
    val width: Int?,
    val height: Int?,
    val realFrameRate: String?,
    val sampleRate: String?,
    val channels: Int?,
) : Parcelable

@Parcelize
internal data class FFmpegRuntimeMediaInformation(
    val format: String?,
    val duration: String?,
    val bitrate: String?,
    val streams: List<FFmpegRuntimeStreamInformation>,
) : Parcelable

@Parcelize
internal data class FFmpegRuntimeInformation(
    val sectionWireValue: String,
    val executionPlane: String,
    val processName: String,
    val abi: String,
    val androidApi: Int,
    val qualifiedProfile: String,
    val wrapperVersion: String,
    val ffmpegVersion: String,
    val buildDate: String,
) : Parcelable {
    init {
        FFmpegRuntimeInformationSection.fromWireValue(sectionWireValue)
        require(executionPlane.isNotBlank()) { "FFmpeg execution plane is blank" }
        require(processName.isNotBlank()) { "FFmpeg process name is blank" }
        require(abi.isNotBlank()) { "FFmpeg ABI is blank" }
        require(androidApi > 0) { "FFmpeg Android API is invalid" }
        require(qualifiedProfile.isNotBlank()) { "FFmpeg qualified profile is blank" }
    }

    val section: FFmpegRuntimeInformationSection
        get() = FFmpegRuntimeInformationSection.fromWireValue(sectionWireValue)
}

@Parcelize
internal data class FFmpegRuntimeResult(
    val requestId: String,
    val operationWireValue: Int,
    val processId: Int,
    val sessionId: Long,
    val terminalStateWireValue: Int,
    val returnCode: Int,
    val durationMillis: Long,
    val outputLogPath: String,
    val failStackTrace: String?,
    val statistics: FFmpegRuntimeStatistics?,
    val mediaInformation: FFmpegRuntimeMediaInformation?,
    val runtimeInformation: FFmpegRuntimeInformation?,
) : Parcelable {
    init {
        require(FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)) {
            "Invalid FFmpeg runtime result request ID"
        }
        FFmpegRuntimeOperation.fromWireValue(operationWireValue)
        val terminalState = FFmpegRuntimeTerminalState.fromWireValue(terminalStateWireValue)
        require(processId > 0) { "FFmpeg runtime process ID is invalid" }
        require(terminalState == resolveFfmpegRuntimeTerminalState(returnCode)) {
            "FFmpeg runtime terminal state $terminalState does not match return code $returnCode"
        }
        require(
            sessionId > 0L ||
                (sessionId == 0L && terminalState == FFmpegRuntimeTerminalState.CANCELLED),
        ) {
            "FFmpeg runtime session ID is invalid for terminal state $terminalState"
        }
        require(durationMillis >= 0L) { "FFmpeg runtime duration is invalid" }
        if (sessionId == 0L) {
            require(
                durationMillis == 0L &&
                    failStackTrace == null &&
                    statistics == null &&
                    mediaInformation == null &&
                    runtimeInformation == null,
            ) {
                "Queued FFmpeg cancellation must not contain native-session data"
            }
        }
        require(File(outputLogPath).isAbsolute) { "FFmpeg runtime log path must be absolute" }
    }

    val operation: FFmpegRuntimeOperation
        get() = FFmpegRuntimeOperation.fromWireValue(operationWireValue)

    val terminalState: FFmpegRuntimeTerminalState
        get() = FFmpegRuntimeTerminalState.fromWireValue(terminalStateWireValue)
}

@Parcelize
internal data class FFmpegRuntimeFailure(
    val requestId: String,
    val operationWireValue: Int,
    val processId: Int,
    val sessionId: Long,
    val failureCodeWireValue: Int,
    val message: String,
    val outputLogPath: String,
) : Parcelable {
    init {
        require(FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)) {
            "Invalid FFmpeg runtime failure request ID"
        }
        FFmpegRuntimeOperation.fromWireValue(operationWireValue)
        FFmpegRuntimeFailureCode.fromWireValue(failureCodeWireValue)
        require(processId > 0) { "FFmpeg runtime failure process ID is invalid" }
        require(sessionId >= 0L) { "FFmpeg runtime failure session ID is invalid" }
        require(message.isNotBlank()) { "FFmpeg runtime failure message is blank" }
        require(File(outputLogPath).isAbsolute) { "FFmpeg runtime failure log path must be absolute" }
    }

    val operation: FFmpegRuntimeOperation
        get() = FFmpegRuntimeOperation.fromWireValue(operationWireValue)

    val failureCode: FFmpegRuntimeFailureCode
        get() = FFmpegRuntimeFailureCode.fromWireValue(failureCodeWireValue)
}

internal data class FFmpegRuntimeResponse(
    val result: FFmpegRuntimeResult,
    val output: String,
) {
    val succeeded: Boolean
        get() = result.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED
}

internal open class FFmpegRuntimeException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal class FFmpegRuntimeProcessDiedException(
    val requestId: String,
    val partialOutput: String,
    val diagnosticLogPath: String,
    val processId: Int?,
    val sessionId: Long?,
) : FFmpegRuntimeException(
        buildString {
            append("FFmpeg 运行时进程已终止，当前命令未完成")
            if (partialOutput.isNotBlank()) {
                append("\n已保留的运行时输出：\n")
                append(partialOutput)
            }
        },
    )

internal class FFmpegRuntimeRequestException(
    val failure: FFmpegRuntimeFailure,
    val output: String,
) : FFmpegRuntimeException(
        buildString {
            append(failure.message)
            if (output.isNotBlank()) {
                append("\nFFmpeg 输出：\n")
                append(output)
            }
        },
    )

internal val FFMPEG_RUNTIME_REQUEST_ID_PATTERN = Regex("[0-9a-f]{32}")
internal const val FFMPEG_RUNTIME_MAX_COMMAND_CHARS = 65_536
internal const val FFMPEG_RUNTIME_MAX_ARGUMENT_COUNT = 1_024
internal const val FFMPEG_RUNTIME_MAX_ARGUMENT_CHARS = 16_384
internal const val FFMPEG_RUNTIME_MAX_ARGUMENT_TOTAL_CHARS = 65_536
internal const val FFMPEG_RUNTIME_MAX_PATH_CHARS = 4_096

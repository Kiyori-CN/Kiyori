package com.ai.assistance.operit.core.ffmpeg.runtime

import android.os.Parcelable
import java.io.File
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
    SERVICE_DESTROYED(6);

    companion object {
        fun fromWireValue(value: Int): FFmpegRuntimeFailureCode =
            requireNotNull(entries.singleOrNull { code -> code.wireValue == value }) {
                "Unsupported FFmpeg runtime failure code: $value"
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
) : Parcelable {
    init {
        require(FFMPEG_RUNTIME_REQUEST_ID_PATTERN.matches(requestId)) {
            "Invalid FFmpeg runtime request ID"
        }
        when (FFmpegRuntimeOperation.fromWireValue(operationWireValue)) {
            FFmpegRuntimeOperation.EXECUTE_COMMAND -> {
                require(!command.isNullOrBlank()) { "FFmpeg command is blank" }
                require(arguments.isEmpty() && inputPath == null) {
                    "FFmpeg command request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.EXECUTE_ARGUMENTS -> {
                require(arguments.isNotEmpty() && arguments.none(String::isBlank)) {
                    "FFmpeg argument request is empty or malformed"
                }
                require(command == null && inputPath == null) {
                    "FFmpeg argument request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.PROBE_MEDIA -> {
                require(!inputPath.isNullOrBlank() && File(inputPath).isAbsolute) {
                    "FFprobe input path must be absolute"
                }
                require(command == null && arguments.isEmpty()) {
                    "FFprobe request contains unrelated payload"
                }
            }
            FFmpegRuntimeOperation.RUNTIME_INFO -> {
                require(command == null && arguments.isEmpty() && inputPath == null) {
                    "FFmpeg runtime info request contains payload"
                }
            }
        }
    }

    val operation: FFmpegRuntimeOperation
        get() = FFmpegRuntimeOperation.fromWireValue(operationWireValue)
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
    val wrapperVersion: String,
    val ffmpegVersion: String,
    val buildDate: String,
) : Parcelable

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
        FFmpegRuntimeTerminalState.fromWireValue(terminalStateWireValue)
        require(processId > 0) { "FFmpeg runtime process ID is invalid" }
        require(sessionId > 0L) { "FFmpeg runtime session ID is invalid" }
        require(durationMillis >= 0L) { "FFmpeg runtime duration is invalid" }
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
)

internal open class FFmpegRuntimeException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal class FFmpegRuntimeProcessDiedException(
    val requestId: String,
    val partialOutput: String,
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

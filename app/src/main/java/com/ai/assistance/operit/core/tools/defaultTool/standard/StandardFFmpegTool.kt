package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeTerminalState
import com.ai.assistance.operit.core.tools.FFmpegResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** FFmpeg工具执行器 提供媒体文件处理能力，包括转换、裁剪、合并等功能 */
class StandardFFmpegToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val command = tool.parameters.find { it.name == "command" }?.value ?: ""

        if (command.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Command cannot be empty"
            )
        }

        return try {
            val response =
                FFmpegRuntimeClient.getInstance(context).executeBlocking(command)
            val runtimeResult = response.result
            val output = response.output
            val duration = runtimeResult.durationMillis

            if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                FFmpegResultData(
                                        command = command,
                                        returnCode = runtimeResult.returnCode,
                                        output = output,
                                        duration = duration
                                )
                )
            } else if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.CANCELLED) {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg command was cancelled"
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "FFmpeg execution failed, return code: ${runtimeResult.returnCode}\nOutput:\n$output"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "FFmpeg execution failed", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "FFmpeg execution exception: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value

        if (command.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide command parameter")
        }

        return ToolValidationResult(valid = true)
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> =
        flow {
            emit(withContext(Dispatchers.IO) { invoke(tool) })
        }
}

/** FFmpeg信息工具执行器 获取有关系统FFmpeg配置的信息 */
class StandardFFmpegInfoToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegInfoToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        return try {
            val response =
                FFmpegRuntimeClient.getInstance(context).queryRuntimeInfoBlocking()
            val runtimeResult = response.result
            val runtimeInfo =
                requireNotNull(runtimeResult.runtimeInformation) {
                    "FFmpeg runtime did not return version information"
                }
            val info =
                buildString {
                    appendLine("FFmpeg version: ${runtimeInfo.ffmpegVersion}")
                    appendLine("FFmpegKit wrapper version: ${runtimeInfo.wrapperVersion}")
                    appendLine("Build date: ${runtimeInfo.buildDate}")
                    appendLine()
                    appendLine("Supported codecs:")
                    append(response.output)
                }

            ToolResult(
                    toolName = tool.name,
                    success =
                            runtimeResult.terminalState ==
                                    FFmpegRuntimeTerminalState.SUCCEEDED,
                    result =
                            FFmpegResultData(
                                    command = "-codecs",
                                    returnCode = runtimeResult.returnCode,
                                    output = info,
                                    duration = runtimeResult.durationMillis
                            )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query FFmpeg runtime information", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get FFmpeg info: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 不需要参数
        return ToolValidationResult(valid = true)
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> =
        flow {
            emit(withContext(Dispatchers.IO) { invoke(tool) })
        }
}

/** FFmpeg转换视频工具执行器 提供一个简化的接口用于常见的视频转换操作 */
class StandardFFmpegConvertToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegConvertToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value ?: ""
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value ?: ""
        val resolution = tool.parameters.find { it.name == "resolution" }?.value
        val bitrate = tool.parameters.find { it.name == "bitrate" }?.value
        val audioCodec = tool.parameters.find { it.name == "audio_codec" }?.value
        val videoCodec = tool.parameters.find { it.name == "video_codec" }?.value

        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Input path and output path cannot be empty"
            )
        }

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Input file does not exist: $inputPath"
            )
        }

        // 构建FFmpeg命令
        val commandBuilder = StringBuilder("-i \"$inputPath\"")

        // 添加可选参数
        if (!videoCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:v $videoCodec")
        }

        if (!audioCodec.isNullOrEmpty()) {
            commandBuilder.append(" -c:a $audioCodec")
        }

        if (!resolution.isNullOrEmpty()) {
            commandBuilder.append(" -s $resolution")
        }

        if (!bitrate.isNullOrEmpty()) {
            commandBuilder.append(" -b:v $bitrate")
        }

        // 添加输出文件
        commandBuilder.append(" \"$outputPath\"")

        val command = commandBuilder.toString()

        return try {
            val runtime = FFmpegRuntimeClient.getInstance(context)
            val response = runtime.executeBlocking(command)
            val runtimeResult = response.result
            val output = response.output
            val duration = runtimeResult.durationMillis

            if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                val mediaInfo = runtime.probeMediaBlocking(outputPath).result.mediaInformation

                val ffmpegResult =
                        if (mediaInfo != null) {
                            val videoStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("video", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        resolution =
                                                                if (
                                                                    stream.width != null &&
                                                                        stream.height != null
                                                                ) {
                                                                    "${stream.width}x${stream.height}"
                                                                } else {
                                                                    null
                                                                },
                                                        frameRate = stream.realFrameRate
                                                        )
                                            }
                                            .toList()

                            val audioStreams =
                                    mediaInfo
                                            .streams
                                            .filter { it.type.equals("audio", ignoreCase = true) }
                                            .map { stream ->
                                                FFmpegResultData.StreamInfo(
                                                        index = stream.index,
                                                        codecType = stream.type ?: "unknown",
                                                        codecName = stream.codec ?: "unknown",
                                                        sampleRate = stream.sampleRate,
                                                        channels = stream.channels
                                                        )
                                            }
                                            .toList()

                            FFmpegResultData(
                                    command = command,
                                    returnCode = runtimeResult.returnCode,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath,
                                    mediaInfo =
                                            FFmpegResultData.MediaInfo(
                                                    format = mediaInfo.format ?: "unknown",
                                                    duration = mediaInfo.duration ?: "0",
                                                    bitrate = mediaInfo.bitrate ?: "0",
                                                    videoStreams = videoStreams,
                                                    audioStreams = audioStreams
                                            )
                            )
                        } else {
                            FFmpegResultData(
                                    command = command,
                                    returnCode = runtimeResult.returnCode,
                                    output = output,
                                    duration = duration,
                                    outputFile = outputPath
                            )
                        }

                ToolResult(toolName = tool.name, success = true, result = ffmpegResult)
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Video conversion failed, return code: ${runtimeResult.returnCode}\nCommand: $command\nOutput:\n$output"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "FFmpeg conversion failed", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Video conversion exception: ${e.message}\nCommand: $command"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val inputPath = tool.parameters.find { it.name == "input_path" }?.value
        val outputPath = tool.parameters.find { it.name == "output_path" }?.value

        if (inputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide input_path parameter")
        }

        if (outputPath.isNullOrEmpty()) {
            return ToolValidationResult(valid = false, errorMessage = "Must provide output_path parameter")
        }

        return ToolValidationResult(valid = true)
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> =
        flow {
            emit(withContext(Dispatchers.IO) { invoke(tool) })
        }
}

package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeInformationSection
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeProcessDiedException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeRequestException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeResponse
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeTerminalState
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.commitFileAtomicallyWithoutReplacement
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
        val command =
            try {
                validateRawFfmpegCommand(
                    tool.parameters.find { parameter -> parameter.name == "command" }?.value,
                )
            } catch (error: IllegalArgumentException) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = error.message ?: "Invalid FFmpeg arguments",
                )
            }

        return try {
            val response =
                FFmpegRuntimeClient.getInstance(context).executeBlocking(command)
            val runtimeResult = response.result
            val output = response.output

            if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = response.toFFmpegResultData(command = command)
                )
            } else if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.CANCELLED) {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                response.toFFmpegResultData(
                                        command = command,
                                        pipelineStage = "execution"
                                ),
                        error =
                                "Android FFmpeg command was cancelled; signed return code=" +
                                        runtimeResult.returnCode
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                                response.toFFmpegResultData(
                                        command = command,
                                        pipelineStage = "execution"
                                ),
                        error =
                                "Android FFmpeg execution failed, state=${runtimeResult.terminalState}, " +
                                        "signed return code=${runtimeResult.returnCode}\nOutput:\n$output"
                )
            }
        } catch (e: FFmpegRuntimeRequestException) {
            AppLogger.e(
                    TAG,
                    "Android FFmpeg protocol failure: ${e.failure.failureCode}; ${e.failure.message}"
            )
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = e.toFFmpegResultData(command, "execution"),
                    error = e.message
            )
        } catch (e: FFmpegRuntimeProcessDiedException) {
            AppLogger.e(TAG, "Android FFmpeg runtime process died: ${e.requestId}")
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = e.toFFmpegResultData(command, "execution"),
                    error = e.message
            )
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
        return try {
            validateRawFfmpegCommand(
                tool.parameters.find { parameter -> parameter.name == "command" }?.value,
            )
            ToolValidationResult(valid = true)
        } catch (error: IllegalArgumentException) {
            ToolValidationResult(
                valid = false,
                errorMessage = error.message ?: "Invalid FFmpeg arguments",
            )
        }
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
        val section =
                try {
                    FFmpegRuntimeInformationSection.parse(
                            tool.parameters.find { parameter -> parameter.name == "section" }?.value
                    )
                } catch (error: IllegalArgumentException) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = error.message ?: "Invalid FFmpeg information section"
                    )
                }
        val command = formatFfmpegArgumentsForDisplay(section.arguments)
        return try {
            val response =
                FFmpegRuntimeClient.getInstance(context).queryRuntimeInfoBlocking(section)
            val runtimeResult = response.result
            val runtimeInfo =
                requireNotNull(runtimeResult.runtimeInformation) {
                    "FFmpeg runtime did not return version information"
                }
            require(runtimeInfo.section == section) {
                "FFmpeg runtime returned ${runtimeInfo.section.wireValue} for ${section.wireValue}"
            }
            val info = buildFfmpegRuntimeInformationOutput(runtimeInfo, response.output)
            val succeeded =
                    runtimeResult.terminalState ==
                            FFmpegRuntimeTerminalState.SUCCEEDED

            ToolResult(
                    toolName = tool.name,
                    success = succeeded,
                    result =
                            response
                                    .copy(output = info)
                                    .toFFmpegResultData(command = command),
                    error =
                            if (succeeded) {
                                null
                            } else {
                                "Android FFmpeg information query failed for section=" +
                                        "${section.wireValue}, state=${runtimeResult.terminalState}, " +
                                        "signed return code=${runtimeResult.returnCode}"
                            }
            )
        } catch (e: FFmpegRuntimeRequestException) {
            AppLogger.e(
                    TAG,
                    "Android FFmpeg info protocol failure: ${e.failure.failureCode}; ${e.failure.message}"
            )
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = e.toFFmpegResultData(command, "information"),
                    error = e.message
            )
        } catch (e: FFmpegRuntimeProcessDiedException) {
            AppLogger.e(TAG, "Android FFmpeg info runtime process died: ${e.requestId}")
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = e.toFFmpegResultData(command, "information"),
                    error = e.message
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
        return try {
            FFmpegRuntimeInformationSection.parse(
                    tool.parameters.find { parameter -> parameter.name == "section" }?.value
            )
            ToolValidationResult(valid = true)
        } catch (error: IllegalArgumentException) {
            ToolValidationResult(
                    valid = false,
                    errorMessage = error.message ?: "Invalid FFmpeg information section"
            )
        }
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
        var stage = FFmpegConversionPipelineStage.VALIDATION
        var temporaryOutput: java.io.File? = null
        var command = ""
        var diagnosticResponse: FFmpegRuntimeResponse? = null
        return try {
            val request =
                FFmpegConversionRequest.parse(
                    inputPath = tool.parameters.find { it.name == "input_path" }?.value,
                    outputPath = tool.parameters.find { it.name == "output_path" }?.value,
                    profile = tool.parameters.find { it.name == "profile" }?.value,
                    resolution = tool.parameters.find { it.name == "resolution" }?.value,
                    videoBitrate = tool.parameters.find { it.name == "video_bitrate" }?.value,
                )
            val stagedOutput = request.createTemporaryOutput()
            temporaryOutput = stagedOutput
            val arguments = request.buildArguments(stagedOutput)
            command = formatFfmpegArgumentsForDisplay(arguments)
            val runtime = FFmpegRuntimeClient.getInstance(context)
            stage = FFmpegConversionPipelineStage.TRANSCODE
            val response = runtime.executeArgumentsBlocking(arguments)
            diagnosticResponse = response
            val runtimeResult = response.result

            if (runtimeResult.terminalState != FFmpegRuntimeTerminalState.SUCCEEDED) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                        response.toFFmpegResultData(
                            command = command,
                            pipelineStage = stage.wireValue,
                        ),
                    error =
                        describeFfmpegConversionFailure(
                            stage,
                            "state=${runtimeResult.terminalState}, " +
                                "signed return code=${runtimeResult.returnCode}",
                        ),
                )
            } else {
                stage = FFmpegConversionPipelineStage.STAGED_OUTPUT
                require(stagedOutput.isFile && stagedOutput.length() > 0L) {
                    "conversion completed without a non-empty staged output"
                }
                stage = FFmpegConversionPipelineStage.PROBE
                val probeResponse = runtime.probeMediaBlocking(stagedOutput.absolutePath)
                diagnosticResponse = probeResponse
                val probeResult = probeResponse.result
                if (probeResult.terminalState != FFmpegRuntimeTerminalState.SUCCEEDED) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result =
                            probeResponse.toFFmpegResultData(
                                command = command,
                                pipelineStage = stage.wireValue,
                            ),
                        error =
                            describeFfmpegConversionFailure(
                                stage,
                                "state=${probeResult.terminalState}, " +
                                    "signed return code=${probeResult.returnCode}",
                            ),
                    )
                } else {
                    val mediaInfo =
                        requireNotNull(probeResult.mediaInformation) {
                            "validated Android FFprobe metadata is missing"
                        }
                    stage = FFmpegConversionPipelineStage.OUTPUT_CONTRACT
                    request.validateOutput(mediaInfo)
                    stage = FFmpegConversionPipelineStage.ATOMIC_COMMIT
                    commitFileAtomicallyWithoutReplacement(
                        stagedFile = stagedOutput,
                        targetFile = request.outputFile,
                    )

                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                            response.toFFmpegResultData(
                                command = command,
                                outputFile = request.outputFile.absolutePath,
                                mediaInformation = mediaInfo,
                                pipelineStage = "completed",
                            ),
                    )
                }
            }
        } catch (error: IllegalArgumentException) {
            val detail = error.message ?: "Invalid FFmpeg conversion parameters or output"
            ToolResult(
                toolName = tool.name,
                success = false,
                result =
                    diagnosticResponse?.toFFmpegResultData(
                        command = command,
                        pipelineStage = stage.wireValue,
                    ) ?: StringResultData(""),
                error = describeFfmpegConversionFailure(stage, detail),
            )
        } catch (error: FFmpegRuntimeRequestException) {
            AppLogger.e(
                TAG,
                "FFmpeg conversion ${stage.wireValue} protocol failure: " +
                    "${error.failure.failureCode}; ${error.failure.message}",
            )
            ToolResult(
                toolName = tool.name,
                success = false,
                result = error.toFFmpegResultData(command, stage.wireValue),
                error =
                    describeFfmpegConversionFailure(
                        stage,
                        error.failure.message,
                    ),
            )
        } catch (error: FFmpegRuntimeProcessDiedException) {
            AppLogger.e(
                TAG,
                "FFmpeg conversion ${stage.wireValue} runtime process died: ${error.requestId}",
            )
            ToolResult(
                toolName = tool.name,
                success = false,
                result = error.toFFmpegResultData(command, stage.wireValue),
                error =
                    describeFfmpegConversionFailure(
                        stage,
                        "the Android :ffmpeg process terminated before completion",
                    ),
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "FFmpeg conversion failed at ${stage.wireValue}", error)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error =
                    describeFfmpegConversionFailure(
                        stage,
                        error.message ?: error.javaClass.simpleName,
                    ) + if (command.isBlank()) "" else "\nCommand: $command",
            )
        } finally {
            temporaryOutput?.let { stagedOutput ->
                if (stagedOutput.exists() && !stagedOutput.delete()) {
                    AppLogger.w(
                        TAG,
                        describeFfmpegConversionFailure(
                            FFmpegConversionPipelineStage.CLEANUP,
                            stagedOutput.path,
                        ),
                    )
                }
            }
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        return try {
            FFmpegConversionRequest.parse(
                inputPath = tool.parameters.find { it.name == "input_path" }?.value,
                outputPath = tool.parameters.find { it.name == "output_path" }?.value,
                profile = tool.parameters.find { it.name == "profile" }?.value,
                resolution = tool.parameters.find { it.name == "resolution" }?.value,
                videoBitrate = tool.parameters.find { it.name == "video_bitrate" }?.value,
            )
            ToolValidationResult(valid = true)
        } catch (error: IllegalArgumentException) {
            ToolValidationResult(
                valid = false,
                errorMessage = error.message ?: "Invalid FFmpeg conversion parameters",
            )
        }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> =
        flow {
            emit(withContext(Dispatchers.IO) { invoke(tool) })
        }
}

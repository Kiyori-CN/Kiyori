package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeProcessDiedException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeRequestException
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeTerminalState
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Exposes the same Android :ffmpeg FFprobe execution plane used by ffmpeg_convert. */
class StandardFFmpegProbeToolExecutor(private val context: Context) : ToolExecutor {
    companion object {
        private const val TAG = "FFmpegProbeToolExecutor"
        private const val PIPELINE_STAGE = "probe"
    }

    override fun invoke(tool: AITool): ToolResult {
        val input =
            try {
                parseFfmpegProbeInput(
                    tool.parameters.find { parameter -> parameter.name == "input_path" }?.value,
                )
            } catch (error: IllegalArgumentException) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = error.message ?: "Invalid FFprobe input",
                )
            }
        val command = formatFfprobeCommandForDisplay(input.absolutePath)
        return try {
            val response =
                FFmpegRuntimeClient.getInstance(context)
                    .probeMediaBlocking(input.absolutePath)
            val runtimeResult = response.result
            if (runtimeResult.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                val mediaInformation =
                    requireNotNull(runtimeResult.mediaInformation) {
                        "Android FFprobe succeeded without validated media information"
                    }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                        response.toFFmpegResultData(
                            command = command,
                            mediaInformation = mediaInformation,
                            pipelineStage = PIPELINE_STAGE,
                        ),
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result =
                        response.toFFmpegResultData(
                            command = command,
                            pipelineStage = PIPELINE_STAGE,
                        ),
                    error =
                        "Android FFprobe failed, state=${runtimeResult.terminalState}, " +
                            "signed return code=${runtimeResult.returnCode}",
                )
            }
        } catch (error: FFmpegRuntimeRequestException) {
            AppLogger.e(
                TAG,
                "Android FFprobe protocol failure: ${error.failure.failureCode}; ${error.failure.message}",
            )
            ToolResult(
                toolName = tool.name,
                success = false,
                result = error.toFFmpegResultData(command, PIPELINE_STAGE),
                error = error.message,
            )
        } catch (error: FFmpegRuntimeProcessDiedException) {
            AppLogger.e(TAG, "Android FFprobe runtime process died: ${error.requestId}")
            ToolResult(
                toolName = tool.name,
                success = false,
                result = error.toFFmpegResultData(command, PIPELINE_STAGE),
                error = error.message,
            )
        } catch (error: Exception) {
            AppLogger.e(TAG, "Android FFprobe invocation failed", error)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult =
        try {
            parseFfmpegProbeInput(
                tool.parameters.find { parameter -> parameter.name == "input_path" }?.value,
            )
            ToolValidationResult(valid = true)
        } catch (error: IllegalArgumentException) {
            ToolValidationResult(
                valid = false,
                errorMessage = error.message ?: "Invalid FFprobe input",
            )
        }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> =
        flow {
            emit(withContext(Dispatchers.IO) { invoke(tool) })
        }
}

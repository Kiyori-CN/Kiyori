package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.runBlocking

/**
 * Tool for executing the Android shell surface used by super_admin:shell. The executor must use
 * an explicit Root or Shizuku route; an application-UID Runtime.exec result is not equivalent.
 */
open class StandardShellToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ADBToolExecutor"
        private const val DEFAULT_TIMEOUT = 15000L // 15 seconds
    }

    fun invoke(tool: AITool): ToolResult {
        // Validate parameters
        val validationResult = validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        val command = tool.parameters.find { it.name == "command" }?.value ?: ""
        // Timeout parameter is kept for API compatibility but not used by AdbCommandExecutor

        return try {
            // super_admin:shell is a privileged surface. Keep the route decision in one owner so
            // a live Shizuku grant cannot be accidentally bypassed by this compatibility class.
            val result = runBlocking { AndroidShellExecutor.executePrivilegedShellCommand(command) }

            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                ADBResultData(
                                        command = command,
                                        output = result.stdout,
                                        exitCode = result.exitCode
                                )
                )
            } else {
                // Combine stdout and stderr for error reporting
                val errorOutput =
                        if (result.stderr.isNotEmpty()) {
                            "${result.stderr.trim()}\n${result.stdout.trim()}"
                        } else {
                            result.stdout.trim()
                        }

                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Privileged shell execution failed (exit code: ${result.exitCode}): $errorOutput"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing ADB command", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Privileged shell execution failed: ${e.message}"
            )
        }
    }

    /** Validates the parameters for the ADB tool. */
    fun validateParameters(tool: AITool): ToolValidationResult {
        val command = tool.parameters.find { it.name == "command" }?.value

        return when {
            command.isNullOrBlank() -> {
                ToolValidationResult(valid = false, errorMessage = "Command parameter is required")
            }
            command.contains("rm -rf") || command.contains("format") -> {
                ToolValidationResult(
                        valid = false,
                        errorMessage = "Potentially dangerous command detected"
                )
            }
            else -> {
                ToolValidationResult(valid = true)
            }
        }
    }
}

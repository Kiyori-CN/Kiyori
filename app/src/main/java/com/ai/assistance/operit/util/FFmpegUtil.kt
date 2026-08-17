package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeMediaInformation
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeTerminalState
import com.kiyori.platform.android.ApplicationContextAccess

/**
 * Utility class for FFmpeg operations
 */
internal object FFmpegUtil {
    private const val TAG = "FFmpegUtil"

    /**
     * Build a scale filter string that survives FFmpegKit argument parsing.
     * FFmpeg expressions need an escaped comma when passed without a shell.
     */
    fun scaleFilterMaxWidth(maxWidth: Int): String = "scale=min(${maxWidth}\\,iw):-2"

    /**
     * Execute an FFmpeg command and return if it was successful
     */
    fun executeCommand(command: String): Boolean {
        try {
            AppLogger.d(TAG, "Executing FFmpeg command: $command")
            val response =
                FFmpegRuntimeClient.getInstance(ApplicationContextAccess.current)
                    .executeBlocking(command)
            val result = response.result

            if (result.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                AppLogger.d(TAG, "FFmpeg command executed successfully")
                return true
            } else {
                AppLogger.e(
                    TAG,
                    "FFmpeg failed with return code: ${result.returnCode}, output: ${response.output}"
                )
                return false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing FFmpeg command", e)
            return false
        }
    }

    fun executeArguments(arguments: List<String>): Boolean {
        return try {
            AppLogger.d(TAG, "Executing FFmpeg arguments: ${arguments.joinToString(" ")}")
            val response =
                FFmpegRuntimeClient.getInstance(ApplicationContextAccess.current)
                    .executeArgumentsBlocking(arguments)
            val result = response.result
            if (result.terminalState == FFmpegRuntimeTerminalState.SUCCEEDED) {
                AppLogger.d(TAG, "FFmpeg arguments executed successfully")
                true
            } else {
                AppLogger.e(
                    TAG,
                    "FFmpeg failed with return code: ${result.returnCode}, output: ${response.output}",
                )
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing FFmpeg arguments", e)
            false
        }
    }

    /**
     * Get media information for a file
     */
    fun getMediaInfo(filePath: String): FFmpegRuntimeMediaInformation? {
        return try {
            FFmpegRuntimeClient.getInstance(ApplicationContextAccess.current)
                .probeMediaBlocking(filePath)
                .result
                .mediaInformation
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting media info", e)
            null
        }
    }
} 

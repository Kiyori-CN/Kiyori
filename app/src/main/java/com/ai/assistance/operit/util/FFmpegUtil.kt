package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeClient
import com.ai.assistance.operit.core.ffmpeg.runtime.FFmpegRuntimeResponse
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

    fun executeArguments(arguments: List<String>): FFmpegRuntimeResponse {
        try {
            AppLogger.d(TAG, "Executing FFmpeg arguments: ${arguments.joinToString(" ")}")
            val response =
                FFmpegRuntimeClient.getInstance(ApplicationContextAccess.current)
                    .executeArgumentsBlocking(arguments)
            val result = response.result
            if (response.succeeded) {
                AppLogger.d(TAG, "FFmpeg arguments executed successfully")
            } else {
                AppLogger.e(
                    TAG,
                    "FFmpeg failed with state=${result.terminalState}, " +
                        "returnCode=${result.returnCode}, sessionId=${result.sessionId}, " +
                        "processId=${result.processId}, output=${response.output}",
                )
            }
            return response
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing FFmpeg arguments", e)
            throw e
        }
    }

    fun probeMedia(filePath: String): FFmpegRuntimeResponse {
        try {
            val response =
                FFmpegRuntimeClient.getInstance(ApplicationContextAccess.current)
                .probeMediaBlocking(filePath)
            if (!response.succeeded) {
                val result = response.result
                AppLogger.e(
                    TAG,
                    "FFprobe failed with state=${result.terminalState}, " +
                        "returnCode=${result.returnCode}, sessionId=${result.sessionId}, " +
                        "processId=${result.processId}, output=${response.output}",
                )
            }
            return response
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting media info", e)
            throw e
        }
    }
}

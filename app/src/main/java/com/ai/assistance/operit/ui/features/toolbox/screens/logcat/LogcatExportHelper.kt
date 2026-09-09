package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.kiyori.platform.storage.KiyoriPublicLocation
import com.kiyori.platform.storage.KiyoriStorageService
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Files
import kotlinx.coroutines.withContext

data class LogcatExportResult(
    val message: String,
    val success: Boolean
)

object LogcatExportHelper {

    suspend fun exportLogs(context: Context): LogcatExportResult = withContext(Dispatchers.IO) {
        val logFile = File.createTempFile("kiyori_log_", ".snapshot", context.cacheDir)
        var failure: Throwable? = null
        try {
            if (!AppLogger.copyApplicationLogSnapshot(logFile) || logFile.length() == 0L) {
                return@withContext LogcatExportResult(
                    message = context.getString(R.string.logcat_no_logs_to_save),
                    success = false
                )
            }

            val operationContext = currentCoroutineContext()
            val checkCancelled = { operationContext.ensureActive() }
            val logLineCount = countExportableLogLines(logFile, checkCancelled)
            if (logLineCount == 0L) {
                return@withContext LogcatExportResult(
                    message = context.getString(R.string.logcat_no_logs_to_save),
                    success = false
                )
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "kiyori_log_$timestamp.txt"
            val filePath =
                KiyoriStorageService.getInstance(context)
                    .publicStore
                    .write(
                        location = KiyoriPublicLocation.EXPORT_TOOLBOX,
                        requestedFileName = fileName,
                        mimeType = "text/plain",
                    ) { output ->
                        output.bufferedWriter().use { writer ->
                            writeLogContent(context, writer, logFile, logLineCount, checkCancelled)
                        }
                    }
                    .displayPath

            LogcatExportResult(
                message = context.getString(R.string.logcat_saved_to, filePath),
                success = true
            )
        } catch (cancelled: CancellationException) {
            failure = cancelled
            throw cancelled
        } catch (e: Exception) {
            failure = e
            AppLogger.e("LogcatExportHelper", "Failed to export logcat", e)
            LogcatExportResult(
                message = context.getString(
                    R.string.logcat_save_failed,
                    e.message ?: context.getString(R.string.logcat_unknown_error)
                ),
                success = false
            )
        } finally {
            try { Files.deleteIfExists(logFile.toPath()) }
            catch (cleanup: Exception) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun countExportableLogLines(logFile: File, checkCancelled: () -> Unit): Long {
        var count = 0L
        logFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                checkCancelled()
                if (line.isNotBlank()) {
                    count++
                }
            }
        }
        return count
    }

    private fun writeLogContent(
        context: Context,
        writer: Writer,
        logFile: File,
        logLineCount: Long,
        checkCancelled: () -> Unit
    ) {
        val exportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        writer.appendLine(context.getString(R.string.logcat_header))
        writer.appendLine(context.getString(R.string.logcat_date, exportTime))
        writer.appendLine(context.getString(R.string.logcat_total_count, logLineCount))
        writer.appendLine("===================================")
        writer.appendLine()

        logFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                checkCancelled()
                if (line.isNotBlank()) {
                    writer.appendLine(line)
                }
            }
        }
    }

}

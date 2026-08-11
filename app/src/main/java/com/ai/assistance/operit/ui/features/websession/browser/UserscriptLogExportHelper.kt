package com.ai.assistance.operit.ui.features.websession.browser

import android.content.Context
import com.kiyori.platform.storage.KiyoriPublicLocation
import com.kiyori.platform.storage.KiyoriStorageService
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object UserscriptLogExportHelper {
    suspend fun export(
        context: Context,
        report: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                require(report.isNotBlank()) { "用户脚本日志报告为空" }
                val fileName =
                    "kiyori_userscript_log_${
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    }.txt"
                val result =
                    KiyoriStorageService.getInstance(context)
                        .publicStore
                        .writeBytes(
                            location = KiyoriPublicLocation.EXPORT_BROWSER,
                            requestedFileName = fileName,
                            mimeType = "text/plain",
                            bytes = report.toByteArray(StandardCharsets.UTF_8),
                        )
                Result.success(result.displayPath)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

}

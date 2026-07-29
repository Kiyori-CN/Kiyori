package com.ai.assistance.operit.ui.features.player

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PlayerLogExportHelper {
    suspend fun export(
        context: Context,
        report: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(report.isNotBlank()) { "播放器诊断报告为空" }
                val fileName =
                    "kiyori_player_log_${
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    }.txt"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveWithMediaStore(context, fileName, report)
                } else {
                    saveToPublicExports(fileName, report)
                }
            }
        }

    private fun saveWithMediaStore(
        context: Context,
        fileName: String,
        report: String,
    ): String {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Kiyori/exports",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) {
                "无法创建播放器日志导出文件"
            }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "无法打开播放器日志导出文件" }
                output.write(report.toByteArray(StandardCharsets.UTF_8))
            }
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            ) {
                "无法完成播放器日志导出文件"
            }
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return "Download/Kiyori/exports/$fileName"
    }

    private fun saveToPublicExports(
        fileName: String,
        report: String,
    ): String {
        val file = File(OperitPaths.exportsDir(), fileName)
        file.writeText(report, StandardCharsets.UTF_8)
        require(file.isFile && file.length() > 0L) { "播放器日志导出文件写入失败" }
        return file.absolutePath
    }
}

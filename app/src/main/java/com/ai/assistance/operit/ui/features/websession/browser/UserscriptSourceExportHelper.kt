package com.ai.assistance.operit.ui.features.websession.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.kiyori.platform.storage.KiyoriPaths
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object UserscriptSourceExportHelper {
    suspend fun export(
        context: Context,
        scriptName: String,
        source: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                require(source.isNotBlank()) { "用户脚本源码为空" }
                val fileName =
                    buildUserscriptExportFileName(
                        scriptName = scriptName,
                        timestamp =
                            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()),
                    )
                val path =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveWithMediaStore(context, fileName, source)
                    } else {
                        saveToPublicExports(fileName, source)
                    }
                Result.success(path)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(
        context: Context,
        fileName: String,
        source: String,
    ): String {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/javascript")
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
                "无法创建用户脚本导出文件"
            }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "无法打开用户脚本导出文件" }
                output.write(source.toByteArray(StandardCharsets.UTF_8))
            }
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            ) {
                "无法完成用户脚本导出文件"
            }
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return "Download/Kiyori/exports/$fileName"
    }

    private fun saveToPublicExports(
        fileName: String,
        source: String,
    ): String {
        val file = File(KiyoriPaths.exportsDir(), fileName)
        file.writeText(source, StandardCharsets.UTF_8)
        require(file.isFile && file.length() > 0L) { "用户脚本导出文件写入失败" }
        return file.absolutePath
    }
}

internal fun buildUserscriptExportFileName(
    scriptName: String,
    timestamp: String,
): String {
    require(scriptName.isNotBlank()) { "Userscript name cannot be blank" }
    require(timestamp.matches(Regex("""\d{8}_\d{6}_\d{3}"""))) {
        "Userscript export timestamp must use yyyyMMdd_HHmmss_SSS"
    }
    val sanitizedName =
        scriptName
            .trim()
            .map { character ->
                when {
                    character.code < 32 -> '_'
                    character.isWhitespace() -> '_'
                    character == '.' -> '_'
                    character == '\\' -> '_'
                    character == '/' -> '_'
                    character == ':' -> '_'
                    character == '*' -> '_'
                    character == '?' -> '_'
                    character == '"' -> '_'
                    character == '<' -> '_'
                    character == '>' -> '_'
                    character == '|' -> '_'
                    else -> character
                }
            }
            .joinToString("")
            .take(80)
    require(sanitizedName.isNotBlank()) { "Userscript name cannot produce an empty export file name" }
    return "${sanitizedName}_$timestamp.user.js"
}

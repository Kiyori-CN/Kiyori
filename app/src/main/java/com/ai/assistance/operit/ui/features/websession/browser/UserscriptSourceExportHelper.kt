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
                val result =
                    KiyoriStorageService.getInstance(context)
                        .publicStore
                        .writeBytes(
                            location = KiyoriPublicLocation.EXPORT_USERSCRIPTS,
                            requestedFileName = fileName,
                            mimeType = "application/javascript",
                            bytes = source.toByteArray(StandardCharsets.UTF_8),
                        )
                Result.success(result.displayPath)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
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

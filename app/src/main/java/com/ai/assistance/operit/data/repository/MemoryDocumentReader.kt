package com.ai.assistance.operit.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.MemoryLibraryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext

/** 文件选择器只交付 URI；读取、限额、解析和临时文件清理均在 IO 线程。 */
internal class MemoryDocumentReader(private val context: Context) {
    suspend fun read(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) require(cursor.getLong(sizeIndex) <= MAX_BYTES) { "资料超过 32 MB，请拆分后导入" }
            }
        }
        val mime = context.contentResolver.getType(uri).orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase().ifBlank {
            when (mime) {
                "application/pdf" -> "pdf"
                "application/msword" -> "doc"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                else -> ""
            }
        }
        val textFile = mime.startsWith("text/") || extension in setOf("txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log")
        require(textFile || extension in setOf("pdf", "doc", "docx")) { "不支持该资料类型，请选择文本、PDF 或 Word 文件" }
        val temporary = File.createTempFile("memory_import_", ".${extension.filter(Char::isLetterOrDigit).take(12).ifBlank { "txt" }}", context.cacheDir)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "资料超过 32 MB，请拆分后导入" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val content = if (textFile) decodeText(temporary.readBytes()) else {
                val result = AIToolHandler.getInstance(context).executeTool(AITool("read_file_full", listOf(ToolParameter("path", temporary.absolutePath))))
                check(result.success) { result.error ?: "文件解析失败" }
                // 禁止把 ToolResult 的展示包装、缓存路径或媒体描述当成知识正文保存。
                when (val data = result.result) {
                    is FileContentData -> data.content
                    is StringResultData -> data.value
                    else -> error("解析器未返回文本正文")
                }
            }
            require(content.isNotBlank()) { "文档没有可读取的文字；扫描 PDF 需先识别文字" }
            require(content.length <= MemoryLibraryPolicy.MAX_DOCUMENT_CHARS) { "文档超过 200 万字符，请拆分后导入" }
            name to content
        } finally {
            if (!temporary.delete()) com.ai.assistance.operit.util.AppLogger.w("MemoryDocumentReader", "Temporary document cleanup failed")
        }
    }

    companion object {
        private const val MAX_BYTES = 32L * 1024 * 1024
        private const val BOM = "\uFEFF"
        /**
         * 严格解码后再退回 GB18030：中文用户的 .txt 常见于 GBK/GB18030，宽松解码会把整篇正文
         * 存成替换字符，之后既搜不到也无法还原。都失败时抛出可读原因，而不是保存乱码。
         */
        internal fun decodeText(bytes: ByteArray): String {
            val utf16 = bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
            val candidates = if (utf16) listOf(Charsets.UTF_16) else listOf(Charsets.UTF_8, Charset.forName("GB18030"))
            candidates.forEach { charset ->
                runCatching {
                    charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString().removePrefix(BOM)
                }.onSuccess { return it }
            }
            error("无法识别文本编码，请另存为 UTF-8 后重新导入")
        }
    }
}

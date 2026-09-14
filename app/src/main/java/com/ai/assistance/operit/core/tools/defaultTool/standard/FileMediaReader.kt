package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.util.Base64
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.MediaPoolManager
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 各文件环境只负责取到真实字节，注册和后续请求继续复用附件媒体池。 */
internal object FileMediaReader {
    suspend fun fromFile(tool: AITool, path: String, format: FileMediaReadPolicy.Format): ToolResult {
        val file = File(path)
        require(file.isFile && file.length() > 0) { "Media file is missing or empty" }
        val id = if (format.kind == "image") ImagePoolManager.addImage(path)
            else MediaPoolManager.addMedia(path, format.mimeType)
        return result(tool, path, format, id, file.length(), "android")
    }

    suspend fun fromStream(tool: AITool, path: String, format: FileMediaReadPolicy.Format, environment: String, input: InputStream): ToolResult {
        // 未知 SAF 长度同样有界；不能在检查容量前 readBytes() 耗尽堆。
        val bytes = input.use { it.readBytesBounded() }
        return fromBytes(tool, path, format, environment, bytes)
    }

    suspend fun fromBytes(tool: AITool, path: String, format: FileMediaReadPolicy.Format, environment: String, bytes: ByteArray): ToolResult {
        require(bytes.size in 1..FileMediaReadPolicy.MAX_TRANSFER_BYTES) { "Media transfer must be between 1 byte and 20 MiB; use an explicit smaller clip or image" }
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val id = if (format.kind == "image") ImagePoolManager.addImageFromBase64(base64, format.mimeType)
            else MediaPoolManager.addMediaFromBase64(base64, format.mimeType)
        return result(tool, path, format, id, bytes.size.toLong(), environment)
    }

    private suspend fun InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer, 0, minOf(buffer.size, FileMediaReadPolicy.MAX_TRANSFER_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            require(output.size() <= FileMediaReadPolicy.MAX_TRANSFER_BYTES) { "Media transfer exceeds 20 MiB; use an explicit smaller clip or image" }
        }
        return output.toByteArray()
    }

    private fun result(tool: AITool, path: String, format: FileMediaReadPolicy.Format, id: String, size: Long, environment: String): ToolResult {
        check(id != "error") { "Direct ${format.kind} registration failed; no OCR or metadata substitution was performed" }
        return ToolResult(toolName = tool.name, success = true,
            result = FileContentData(path = path, content = "<link type=\"${format.kind}\" id=\"$id\"></link>", size = size, env = environment))
    }
}

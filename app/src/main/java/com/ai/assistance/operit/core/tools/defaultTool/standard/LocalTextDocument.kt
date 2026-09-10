package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributes

internal const val FILE_MANAGER_TEXT_LIMIT = 1024 * 1024

/** 有界、严格 UTF-8 读取。不能将替换字符解码或截断文本交给可保存的编辑器。 */
internal fun readLocalUtf8Document(path: Path): String {
    val before = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    require(before.isRegularFile && before.size() <= FILE_MANAGER_TEXT_LIMIT) { "仅支持不超过 1 MiB 的普通文本文件" }
    val bytes = Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(FILE_MANAGER_TEXT_LIMIT + 1)
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            count += read
        }
        require(count <= FILE_MANAGER_TEXT_LIMIT) { "文件超过 1 MiB，无法在此编辑" }
        buffer.copyOf(count)
    }
    val after = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    check(before.fileKey() == after.fileKey() && before.size() == after.size() &&
        before.lastModifiedTime() == after.lastModifiedTime()) { "读取时文件发生变化，请重新打开" }
    val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    require('\u0000' !in text) { "包含二进制内容，无法作为文本编辑" }
    return text
}

internal fun executeBoundedTextRead(tool: AITool, backendEnvironment: String = "android"): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val path = parameter("path").orEmpty()
    PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
    return try {
        require(backendEnvironment == "android" && parameter("read_mode") == "bounded_utf8" &&
            (parameter("environment") ?: "android").equals("android", true)) { "此位置尚不支持安全文本编辑" }
        val content = readLocalUtf8Document(java.io.File(path).toPath())
        ToolResult(tool.name, true, FileContentData(path, content, content.toByteArray(Charsets.UTF_8).size.toLong()))
    } catch (error: Exception) {
        ToolResult(tool.name, false, StringResultData(""), "无法读取 UTF-8 文本（${error.javaClass.simpleName}）；请检查编码、权限和大小")
    }
}

/** 另存副本复用原子不覆盖提交。原文件从不写入，失败也不会留下可误认完成的目标。 */
internal fun saveLocalTextCopy(path: Path, content: String, commit: (Path, Path) -> Unit) {
    require(path.isAbsolute)
    require(content.length <= FILE_MANAGER_TEXT_LIMIT) { "文本超过 1 MiB" }
    val encoded = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(content))
    val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
    require(bytes.size <= FILE_MANAGER_TEXT_LIMIT && '\u0000' !in content) { "文本超过 1 MiB 或包含二进制内容" }
    val parent = path.parent.toRealPath()
    val staging = Files.createTempDirectory(parent, ".kiyori-text-")
    val payload = staging.resolve("payload")
    var failure: Throwable? = null
    try {
        FileChannel.open(payload, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        commit(payload, parent.resolve(path.fileName))
    } catch (error: Throwable) {
        failure = error
    }
    try {
        Files.deleteIfExists(payload)
        Files.delete(staging)
    } catch (cleanup: Exception) {
        throw LocalCopyException(FileCopyErrorCode.FAILED,
            if (failure == null) "副本已提交，但暂存清理失败；请检查目标后再操作" else "保存失败且暂存清理失败",
            staging.toString(), failure ?: cleanup)
    }
    failure?.let { throw it }
}

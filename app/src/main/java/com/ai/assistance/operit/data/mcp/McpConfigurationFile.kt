package com.ai.assistance.operit.data.mcp

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal fun writeMcpConfigurationFile(target: File, text: String) {
    publishMcpConfigurationFile(target) { staging ->
        FileOutputStream(staging).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}

internal fun publishMcpConfigurationFile(target: File, prepare: (File) -> Unit) {
    val staging = File.createTempFile(".mcp_config_", ".tmp", requireNotNull(target.absoluteFile.parentFile))
    var failure: Throwable? = null
    try {
        prepare(staging)
        // 同目录完整写入后原子替换；不支持原子发布时直接报错，不改写原文件。
        Files.move(staging.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try { Files.deleteIfExists(staging.toPath()) }
        catch (cleanup: Exception) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
    }
}

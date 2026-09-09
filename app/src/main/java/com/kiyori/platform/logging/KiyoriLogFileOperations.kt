package com.kiyori.platform.logging

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 复用日志写入队列。等待已开始的文件操作结束后才返回，调用者可安全清理快照。 */
internal suspend fun <T> awaitLogFileOperation(executor: ExecutorService, operation: () -> T): T =
    withContext(Dispatchers.IO) {
        try {
            executor.submit<T> { operation() }.get()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

internal fun copyApplicationLogFile(source: File, destination: File): Boolean {
    if (!Files.exists(source.toPath(), NOFOLLOW_LINKS)) return false
    require(Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS)) { "Application log is not a regular file" }
    source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
    return true
}

internal fun clearApplicationLogFile(source: File) {
    if (!Files.exists(source.toPath(), NOFOLLOW_LINKS)) return
    require(Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS)) { "Application log is not a regular file" }
    Files.delete(source.toPath())
}

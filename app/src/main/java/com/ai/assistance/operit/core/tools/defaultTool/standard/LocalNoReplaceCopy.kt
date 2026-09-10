package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** 稳定错误码供工具/UI消费，不能解析本地化错误文案决定是否重试写操作。 */
object FileCopyErrorCode {
    const val CONFLICT = "destination_exists"
    const val UNSUPPORTED = "no_replace_unsupported"
    const val SOURCE_CHANGED = "source_changed"
    const val FAILED = "copy_failed"
}

internal class LocalCopyException(
    val code: String,
    message: String,
    val stagingPath: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

internal data class LocalCopyReceipt(val cleanupWarning: String? = null, val stagingPath: String? = null)

/**
 * 仍属 StandardFileSystemTools 的本地实现：从不修改源，也不合并已有目标。
 * 暂存与最终名称同卷；只有注入的原子不覆盖提交成功，最终名称才指向完整内容。
 * commit 在 Android 必须为 renameat2(RENAME_NOREPLACE)，不能换成 Java renameTo/move 的预检查。
 */
internal fun copyLocalNoReplace(
    source: Path,
    destination: Path,
    recursive: Boolean,
    commit: (Path, Path) -> Unit,
    checkActive: () -> Unit = {},
) : LocalCopyReceipt {
    val sourceAbsolute = source.toAbsolutePath().normalize()
    val destinationAbsolute = destination.toAbsolutePath().normalize()
    if (Files.isSymbolicLink(sourceAbsolute)) throw IOException("不支持复制符号链接")
    val sourceReal = sourceAbsolute.toRealPath()
    val parent = destinationAbsolute.parent?.toRealPath() ?: throw IOException("目标目录不可用")
    val target = parent.resolve(destinationAbsolute.fileName)
    require(sourceReal != target && !target.startsWith(sourceReal)) { "目标不能是源项目或其子目录" }
    // exists 只用于提早报告；最终不覆盖保证完全由原子提交提供。
    if (Files.exists(target, NOFOLLOW_LINKS)) throw LocalCopyException(FileCopyErrorCode.CONFLICT, "目标已存在")
    val rootAttributes = attributes(sourceReal)
    if (rootAttributes.isDirectory && !recursive) throw IOException("复制文件夹需要包含子项目")
    checkActive()
    val staging = Files.createTempDirectory(parent, ".kiyori-copy-")
    var committed = false
    var failure: Throwable? = null
    try {
        val snapshots = linkedMapOf<Path, BasicFileAttributes>()
        copyEntry(sourceReal, staging.resolve("payload"), checkActive, 0, snapshots)
        // 深层项目复制完后仍可能变化；提交前再核对全树已观察到的身份、大小和修改时间。
        snapshots.forEach { (path, before) ->
            checkActive()
            if (!sameSource(before, attributes(path))) {
                throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "复制期间源项目发生变化")
            }
        }
        checkActive()
        commit(staging.resolve("payload"), target)
        // 提交后不再检查取消：真实完成事实不能被协程取消覆盖成未执行。
        committed = true
    } catch (e: Throwable) {
        failure = e
    }
    val cleanupFailure = runCatching { removeStaging(staging) }.exceptionOrNull()
    if (failure != null) {
        val code = when (failure) {
            is LocalCopyException -> failure.code
            is FileAlreadyExistsException -> FileCopyErrorCode.CONFLICT
            else -> FileCopyErrorCode.FAILED
        }
        if (failure is kotlinx.coroutines.CancellationException && cleanupFailure == null) throw failure
        throw LocalCopyException(code,
            (failure.message ?: "复制失败，源项目保留") +
                if (cleanupFailure != null) "；暂存清理失败，请检查暂存位置" else "",
            staging.takeIf { cleanupFailure != null }?.toString(), failure)
    }
    check(committed)
    return LocalCopyReceipt(
        cleanupWarning = cleanupFailure?.let { "复制已完成，但暂存目录清理失败" },
        stagingPath = staging.takeIf { cleanupFailure != null }?.toString(),
    )
}

private fun attributes(path: Path): BasicFileAttributes =
    Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)

private fun sameSource(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.isDirectory == after.isDirectory && before.isRegularFile == after.isRegularFile &&
        before.fileKey() == after.fileKey() && before.size() == after.size() &&
        before.lastModifiedTime() == after.lastModifiedTime()

private fun copyEntry(source: Path, target: Path, checkActive: () -> Unit, depth: Int,
    snapshots: MutableMap<Path, BasicFileAttributes>) {
    checkActive()
    if (depth > 256) throw IOException("目录层级过深，已停止复制")
    val before = attributes(source)
    snapshots[source] = before
    when {
        before.isDirectory -> {
            Files.createDirectory(target)
            Files.newDirectoryStream(source).use { entries ->
                entries.forEach { entry -> copyEntry(entry, target.resolve(entry.fileName), checkActive, depth + 1, snapshots) }
            }
        }
        before.isRegularFile -> {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            FileChannel.open(source, READ, NOFOLLOW_LINKS).use { input ->
                FileChannel.open(target, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { output ->
                    val buffer = ByteBuffer.allocate(128 * 1024)
                    while (true) {
                        checkActive()
                        buffer.clear()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        // 外部持续追加不能使一次复制无限增长。
                        if (copied > before.size()) throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "复制期间源项目发生变化")
                        digest.update(buffer.array(), 0, count)
                        buffer.flip()
                        while (buffer.hasRemaining()) output.write(buffer)
                    }
                    output.force(true)
                }
            }
            if (copied != before.size() || !MessageDigest.isEqual(digest.digest(), hashFile(target, checkActive))) {
                throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "复制校验失败，源项目保留")
            }
        }
        else -> throw IOException("不支持符号链接或特殊文件")
    }
    if (!sameSource(before, attributes(source))) {
        throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "复制期间源项目发生变化")
    }
}

private fun hashFile(path: Path, checkActive: () -> Unit): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(128 * 1024)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest()
}

/** 只清理由本次 createTempDirectory 创建的目录；walkFileTree 默认不跟随链接。 */
private fun removeStaging(staging: Path) {
    Files.walkFileTree(staging, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) throw exc
            Files.delete(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

package com.ai.assistance.operit.core.tools.packTool

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** 文件已发布，调用者须刷新安装事实并展示后续处理原因，不能当作未安装。 */
class StandalonePackageInstalledException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal fun isStandalonePackageFileName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("js", "ts", "hjson")

/** 暂存文件不使用可扫描扩展名，刷新不能把尚未验证的输入作为已安装包。 */
internal fun prepareStandalonePackageFile(source: File, directory: File, checkCancelled: () -> Unit = {}): File {
    require(source.isFile && isStandalonePackageFileName(source.name)) { "Unsupported package source" }
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create package directory" }
    val staging = File.createTempFile(".package_import_", ".pending", directory)
    try {
        source.inputStream().use { input ->
            FileOutputStream(staging).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkCancelled()
                    val size = input.read(buffer)
                    if (size < 0) break
                    output.write(buffer, 0, size)
                }
                output.fd.sync()
            }
        }
        return staging
    } catch (error: Throwable) {
        try { Files.deleteIfExists(staging.toPath()) }
        catch (cleanup: Exception) { error.addSuppressed(cleanup) }
        throw error
    }
}

/** 调用方持有原registry锁。新文件与状态发布失败时恢复原文件，不覆盖旁边的同名文件。 */
internal fun publishStandalonePackageFile(
    staging: File,
    target: File,
    previous: File?,
    publishState: () -> Unit,
    restoreState: () -> Unit
) {
    val directory = requireNotNull(staging.absoluteFile.parentFile).canonicalFile
    fun verifyMember(file: File) {
        require(file.absoluteFile.parentFile?.canonicalFile == directory && !Files.isSymbolicLink(file.toPath())) {
            "Package file is outside its directory or is a symbolic link"
        }
    }
    verifyMember(staging)
    verifyMember(target)
    previous?.let(::verifyMember)
    val sameTarget = previous?.absoluteFile == target.absoluteFile
    require(!Files.exists(target.toPath(), NOFOLLOW_LINKS) || sameTarget) { "A different file already uses this package filename" }
    require(previous == null || Files.isRegularFile(previous.toPath(), NOFOLLOW_LINKS)) { "Installed package source is unavailable" }
    var backup: File? = null
    var backupReady = false
    var newFilePublished = false
    var stateAttempted = false
    try {
        if (previous != null) {
            backup = File.createTempFile(".package_backup_", ".pending", directory)
            previous.inputStream().use { input ->
                FileOutputStream(backup).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            backupReady = true
        }
        // 同名替换没有“原文件已经移走而新文件尚未到位”的窗口。
        Files.move(staging.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        newFilePublished = true
        stateAttempted = true
        publishState()
        // 跨扩展名更新先保留两份完整文件；状态发布后才删除旧文件。
        if (previous != null && !sameTarget) Files.delete(previous.toPath())
    } catch (error: Throwable) {
        try {
            if (newFilePublished && !sameTarget) Files.deleteIfExists(target.toPath())
            backup?.let { saved ->
                if (backupReady && sameTarget && newFilePublished) Files.move(saved.toPath(), requireNotNull(previous).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                else Files.deleteIfExists(saved.toPath())
            }
        } catch (restore: Exception) { error.addSuppressed(restore) }
        if (stateAttempted) {
            try { restoreState() }
            catch (restore: Exception) { error.addSuppressed(restore) }
        }
        throw error
    }
    // 文件与状态已发布；清理失败应报告部分完成，不反向撤销已完成的发布。
    backup?.let { saved ->
        try { Files.deleteIfExists(saved.toPath()) }
        catch (cleanup: Exception) { throw StandalonePackageInstalledException("Package installed, but the previous source backup could not be removed", cleanup) }
    }
}

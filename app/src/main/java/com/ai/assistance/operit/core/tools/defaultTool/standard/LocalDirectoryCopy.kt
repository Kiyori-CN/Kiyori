package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Android 文件工具的递归复制实现。任一子项失败必须向上传播；移动调用方只有在完整成功后才能删源。
 * 不跟随符号链接，避免目录环和复制到源树内部。保留既有工具的同名普通文件替换语义。
 */
internal fun copyLocalDirectory(source: File, destination: File) {
    val sourcePath = source.canonicalFile.toPath()
    val destinationPath = destination.canonicalFile.toPath()
    require(sourcePath != destinationPath && !destinationPath.startsWith(sourcePath)) {
        "Destination cannot be the source directory or one of its descendants"
    }
    copyLocalDirectoryEntries(source, destination)
}

private fun copyLocalDirectoryEntries(source: File, destination: File) {
    if (Files.isSymbolicLink(source.toPath()) || Files.isSymbolicLink(destination.toPath())) {
        throw IOException("Directory copy does not follow symbolic links")
    }
    if (!source.isDirectory) throw IOException("Source directory is unavailable")
    val entries = source.listFiles() ?: throw IOException("Unable to enumerate source directory")
    if (!destination.exists() && !destination.mkdirs()) throw IOException("Unable to create destination directory")
    if (!destination.isDirectory) throw IOException("Destination is not a directory")
    entries.forEach { entry ->
        val target = File(destination, entry.name)
        if (Files.isSymbolicLink(entry.toPath()) || Files.isSymbolicLink(target.toPath())) {
            throw IOException("Directory copy does not follow symbolic links")
        }
        if (entry.isDirectory) {
            copyLocalDirectoryEntries(entry, target)
        } else {
            if (target.exists() && !target.isFile) throw IOException("Destination entry is not a regular file")
            if (!Files.isRegularFile(entry.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("Source entry is not a regular file")
            }
            Files.copy(entry.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (target.length() != entry.length()) throw IOException("Copied file size changed during transfer")
        }
    }
}

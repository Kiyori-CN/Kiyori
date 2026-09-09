package com.ai.assistance.operit.data.mcp

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.zip.ZipFile

internal fun resolveMcpPluginDirectory(root: File, pluginId: String): File {
    // 保留旧的相对多段 ID；禁止绝对路径、父级跳转和链接路径。
    val parts = pluginId.split('/')
    require(parts.first().lowercase(java.util.Locale.ROOT) !in setOf("mcp_config.json", "server_status.json"))
    require(parts.all { it.isNotBlank() && it != "." && it != ".." &&
        !it.startsWith(".mcp_") && it.none { char -> char == '\\' || char == ':' || char.code < 32 } })
    var target = root.canonicalFile
    parts.forEach { part ->
        target = File(target, part)
        require(!Files.isSymbolicLink(target.toPath())) { "Plugin path contains a symbolic link" }
    }
    require(target.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()))
    require(!Files.exists(target.toPath(), NOFOLLOW_LINKS) || Files.isDirectory(target.toPath(), NOFOLLOW_LINKS)) {
        "Plugin target is not a directory"
    }
    return target
}

internal fun deleteMcpPluginTree(root: File) {
    if (!Files.exists(root.toPath(), NOFOLLOW_LINKS)) return
    Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }
        override fun postVisitDirectory(dir: Path, failure: IOException?): FileVisitResult {
            if (failure != null) throw failure
            Files.delete(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

internal fun extractMcpPluginZip(zipFile: File, target: File, checkCancelled: () -> Unit = {}, progress: (Int) -> Unit = {}) {
    Files.createDirectories(target.toPath())
    require(target.listFiles()?.isEmpty() == true) { "Extraction requires an empty staging directory" }
    val root = target.canonicalFile.toPath()
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries().asSequence().filterNot { entry ->
            entry.name.split('/').any { it == "__MACOSX" || it == ".DS_Store" }
        }.toList()
        // 在首次写入之前检查整个目录表，拒绝重复文件与路径歧义。
        val targets = mutableSetOf<Path>()
        val resolved = entries.map { entry ->
            checkCancelled()
            val name = entry.name.removeSuffix("/")
            val parts = name.split('/')
            require(parts.all { it.isNotEmpty() && it != "." && it != ".." &&
                it.none { char -> char == '\\' || char == ':' || char.code < 32 } })
            val destination = File(target, name).canonicalFile.toPath()
            require(destination != root && destination.startsWith(root) && targets.add(destination))
            entry to destination
        }
        resolved.forEachIndexed { index, (entry, destination) ->
            checkCancelled()
            if (entry.isDirectory) Files.createDirectories(destination)
            else {
                Files.createDirectories(destination.parent)
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            checkCancelled()
                            val size = input.read(buffer)
                            if (size < 0) break
                            output.write(buffer, 0, size)
                        }
                    }
                }
            }
            progress((index + 1) * 100 / resolved.size.coerceAtLeast(1))
        }
    }
}

/** 完整 staging 才进入可见目录；发布失败恢复原目录，清理失败仍向调用者报告。 */
internal fun publishMcpPluginDirectory(staging: File, root: File, pluginId: String, onPublished: () -> Unit = {}): File {
    val target = resolveMcpPluginDirectory(root, pluginId)
    require(requireNotNull(staging.parentFile).canonicalFile == root.canonicalFile && staging.name.startsWith(".mcp_"))
    require(staging.isDirectory && !Files.isSymbolicLink(staging.toPath()))
    Files.createDirectories(requireNotNull(target.parentFile).toPath())
    val backup = File(root, ".mcp_backup_${UUID.randomUUID()}")
    val existing = Files.exists(target.toPath(), NOFOLLOW_LINKS)
    if (existing) Files.move(target.toPath(), backup.toPath())
    try { Files.move(staging.toPath(), target.toPath()) }
    catch (failure: Exception) {
        if (existing) {
            try { Files.move(backup.toPath(), target.toPath()) }
            catch (restore: Exception) { failure.addSuppressed(restore) }
        }
        throw failure
    }
    onPublished()
    if (existing) deleteMcpPluginTree(backup)
    return target
}

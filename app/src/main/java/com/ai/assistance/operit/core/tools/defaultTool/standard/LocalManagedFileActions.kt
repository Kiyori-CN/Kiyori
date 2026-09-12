package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.tools.FileInspectionData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ManagedEntry(val relative: String, val attributes: BasicFileAttributes)
internal data class ManagedSnapshot(val entries: List<ManagedEntry>, val fingerprint: String) {
    val bytes get() = entries.filter { it.attributes.isRegularFile }.sumOf { it.attributes.size() }
}

private fun sameEntry(a: BasicFileAttributes, b: BasicFileAttributes): Boolean =
    a.isDirectory == b.isDirectory && a.isRegularFile == b.isRegularFile && a.fileKey() == b.fileKey() &&
        a.creationTime() == b.creationTime() && a.size() == b.size() && a.lastModifiedTime() == b.lastModifiedTime()

internal fun sameManagedDirectoryIdentity(before: BasicFileAttributes, now: BasicFileAttributes): Boolean =
    now.isDirectory && now.fileKey() == before.fileKey() &&
        // Android libcore 在不支持 birthtime 时以 mtime 返回 creationTime。删除子项会改变它，
        // 不能把自己的写入当作目录被替换；真实 birthtime 存在时仍复核，inode 始终复核。
        (before.creationTime() == before.lastModifiedTime() || now.creationTime() == before.creationTime())

/** 只统计真实文件树，不跟随链接。超限失败，不能把不完整扫描作为删除确认依据。 */
internal fun inspectManagedTree(root: Path, checkActive: () -> Unit = {}): ManagedSnapshot {
    val entries = mutableListOf<ManagedEntry>()
    fun add(path: Path, attributes: BasicFileAttributes) {
        checkActive()
        require(entries.size < 100_000) { "项目超过 100000 个，无法一次处理" }
        require(attributes.isDirectory || attributes.isRegularFile) { "包含链接或特殊文件，操作未执行" }
        // 逐段连接，保留 Android 文件名中的反斜杠；不能将合法名称改写为另一条路径。
        entries += ManagedEntry(root.relativize(path).joinToString("/") { it.toString() }, attributes)
    }
    Files.walkFileTree(root, emptySet(), 129, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            add(dir, attrs); return FileVisitResult.CONTINUE
        }
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            require(attrs.isRegularFile) { "目录层级超过 128 或包含链接" }
            add(file, attrs); return FileVisitResult.CONTINUE
        }
        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc
    })
    val ordered = entries.sortedBy { it.relative }
    val digest = MessageDigest.getInstance("SHA-256")
    ordered.forEach { entry ->
        // NIO 允许 fileKey 为 null；缺失也进入指纹，不能因属性读取成功便假设存在 inode 标识。
        listOf(entry.relative, entry.attributes.fileKey()?.toString().orEmpty(), entry.attributes.isDirectory.toString(),
            entry.attributes.creationTime().toString(), entry.attributes.size().toString(), entry.attributes.lastModifiedTime().toString()).forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
        }
    }
    return ManagedSnapshot(ordered, digest.digest().joinToString("") { "%02x".format(it) })
}

internal fun managedSha256(path: Path, checkActive: () -> Unit = {}): String {
    val before = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    require(before.isRegularFile) { "SHA-256 仅适用于普通文件" }
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { checkActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    check(sameEntry(before, Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS))) { "计算期间文件发生变化" }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** 先原子隔离，再核对已确认的清单；删除部分失败时保留隔离目录，绝不回头删除原路径的新项目。 */
internal fun deleteManagedEntry(source: Path, fingerprint: String, commit: (Path, Path) -> Unit) {
    val from = source.toAbsolutePath().normalize()
    require(from.parent != null && fingerprint.isNotBlank()) { "不能删除根目录或未确认项目" }
    check(inspectManagedTree(from).fingerprint == fingerprint) { "项目在确认后发生变化，请重新检查" }
    val staging = Files.createTempDirectory(from.parent, ".kiyori-delete-")
    val payload = staging.resolve("payload")
    try {
        commit(from, payload)
    } catch (failure: Throwable) {
        try { Files.delete(staging) } catch (cleanup: Exception) {
            throw LocalCopyException(FileCopyErrorCode.FAILED, "隔离失败且暂存清理失败", staging.toString(), failure)
        }
        throw failure
    }
    val snapshot = try {
        inspectManagedTree(payload).also { check(it.fingerprint == fingerprint) { "隔离前项目已变化" } }
    } catch (failure: Exception) {
        try { commit(payload, from); Files.delete(staging) } catch (restore: Exception) {
            throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "项目发生变化，未删除；原路径无法恢复，请检查隔离位置", staging.toString(), restore)
        }
        throw IOException("项目发生变化，已恢复原位置，未删除", failure)
    }
    try {
        snapshot.entries.sortedByDescending { it.relative.count { c -> c == '/' } * 2 + if (it.relative.isEmpty()) 0 else 1 }
            .forEach { entry ->
                val path = payload.resolve(entry.relative)
                val now = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                // 子项删除会改变目录 mtime/size；目录只校验身份，且 delete 遇到新增子项必须失败。
                check(if (entry.attributes.isDirectory) sameManagedDirectoryIdentity(entry.attributes, now)
                    else sameEntry(entry.attributes, now)) { "隔离位置中的项目发生变化" }
                Files.delete(path)
            }
        Files.delete(staging)
    } catch (failure: Exception) {
        throw LocalCopyException(FileCopyErrorCode.FAILED, "删除未全部完成；剩余内容位于隔离目录，请先检查", staging.toString(), failure)
    }
}

internal fun zipManagedEntry(source: Path, destination: Path, commit: (Path, Path) -> Unit, checkActive: () -> Unit = {}) {
    zipManagedEntries(listOf(source), destination, commit, checkActive)
}

internal fun zipManagedEntries(sources: List<Path>, destination: Path, commit: (Path, Path) -> Unit, checkActive: () -> Unit = {}) {
    require(sources.isNotEmpty() && sources.size <= 100_000) { "请选择 1 至 100000 个项目" }
    val normalized = sources.map { it.toAbsolutePath().normalize() }
    require(normalized.map { it.fileName }.distinct().size == normalized.size) { "压缩源包含重复名称" }
    val parent = destination.parent.toRealPath()
    val target = parent.resolve(destination.fileName)
    val snapshots = normalized.associateWith { from ->
        require(from.fileName != null && target != from.toRealPath() && !target.startsWith(from.toRealPath())) { "压缩包不能放在源项目内部" }
        inspectManagedTree(from, checkActive)
    }
    require(snapshots.values.sumOf { it.entries.size.toLong() } <= 100_000) { "压缩项目总数超过 100000" }
    val staging = Files.createTempDirectory(parent, ".kiyori-zip-")
    val payload = staging.resolve("payload")
    var failure: Throwable? = null
    try {
        ZipOutputStream(Files.newOutputStream(payload, CREATE_NEW, WRITE)).use { zip ->
            snapshots.forEach { (from, before) ->
            before.entries.forEach { entry ->
                checkActive()
                val path = from.resolve(entry.relative)
                check(sameEntry(entry.attributes, Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS))) { "压缩期间源项目发生变化" }
                val name = from.fileName.toString() + if (entry.relative.isBlank()) "" else "/${entry.relative}"
                require(name.split('/').none { it == ".." || it == "." || '\\' in it }) { "文件名不能安全表示为 ZIP 项目" }
                zip.putNextEntry(ZipEntry(name + if (entry.attributes.isDirectory) "/" else "").apply { time = entry.attributes.lastModifiedTime().toMillis() })
                if (entry.attributes.isRegularFile) Files.newInputStream(path, READ, NOFOLLOW_LINKS).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { checkActive(); val count = input.read(buffer); if (count < 0) break; zip.write(buffer, 0, count) }
                }
                zip.closeEntry()
            }
            }
        }
        snapshots.forEach { (from, before) -> check(inspectManagedTree(from, checkActive).fingerprint == before.fingerprint) { "压缩期间源项目发生变化" } }
        FileChannel.open(payload, WRITE).use { it.force(true) }
        checkActive(); commit(payload, target)
    } catch (error: Throwable) { failure = error }
    try { Files.deleteIfExists(payload); Files.delete(staging) } catch (cleanup: Exception) {
        throw LocalCopyException(FileCopyErrorCode.FAILED, "请检查压缩包与未清理的暂存位置", staging.toString(), failure ?: cleanup)
    }
    failure?.let { throw it }
}

internal suspend fun executeManagedFileTool(tool: AITool, backendEnvironment: String = "android", context: android.content.Context? = null): ToolResult {
    fun parameter(name: String) = tool.parameters.find { it.name == name }?.value
    val path = (parameter("path") ?: parameter("source")).orEmpty()
    val environment = parameter("environment") ?: backendEnvironment
    val destination = parameter("destination")
    fun result(success: Boolean, message: String, code: String? = null, staging: String? = null) = ToolResult(tool.name, success,
        FileOperationData(tool.name, environment, path, success, message, code, destination, staging), message.takeUnless { success })
    if (backendEnvironment != "android" || !environment.equals("android", true)) return result(false, "此位置暂不支持该操作，请使用手机存储", FileCopyErrorCode.UNSUPPORTED)
    PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
    destination?.let { PathValidator.validateAndroidPath(it, tool.name, "destination")?.let { result -> return result } }
    val coroutine = currentCoroutineContext()
    val active = { coroutine.ensureActive() }
    return try {
        val source = Paths.get(path)
        require(source.isAbsolute && source.fileName != null) { "请选择具体项目" }
        when (tool.name) {
            "file_info" -> {
                require(parameter("info_mode") in setOf("manager", "manager_sha256"))
                val tree = inspectManagedTree(source, active)
                val root = tree.entries.first { it.relative.isEmpty() }.attributes
                val sha = if (parameter("info_mode") == "manager_sha256") managedSha256(source, active) else null
                ToolResult(tool.name, true, FileInspectionData(path, root.isDirectory, tree.bytes,
                    tree.entries.count { it.attributes.isRegularFile }, tree.entries.count { it.attributes.isDirectory },
                    root.lastModifiedTime().toMillis(), Files.isReadable(source), Files.isWritable(source), tree.fingerprint, sha))
            }
            "delete_file" -> {
                if (parameter("delete_mode") == "purge_recycled") {
                    val record = LocalFileRecycleBin.recordForPayload(source)
                    check(inspectManagedTree(source).fingerprint == parameter("fingerprint")) { "回收项目已经变化，请重新检查" }
                    LocalFileRecycleBin.delete(record, NativeNoReplaceCommit::commit, parameter("fingerprint"))
                    result(true, "已彻底删除")
                } else if (parameter("delete_mode") == "recycle") {
                    val trashRoot = (parameter("trash_root") ?: fileRecycleRootForSource(requireNotNull(context), source).toString())
                        .also { require(it.isNotBlank()) { "此位置没有可用回收站" } }
                    PathValidator.validateAndroidPath(trashRoot, tool.name)?.let { return it }
                    LocalFileRecycleBin.recycle(source, Paths.get(trashRoot), parameter("fingerprint").orEmpty(), NativeNoReplaceCommit::commit)
                    result(true, "已移至回收站")
                } else {
                    require(parameter("delete_mode") == "checked")
                    deleteManagedEntry(source, parameter("fingerprint").orEmpty(), NativeNoReplaceCommit::commit)
                    result(true, "已永久删除")
                }
            }
            "move_file" -> {
                require(parameter("move_mode") == "restore_recycled")
                val record = LocalFileRecycleBin.recordForPayload(source)
                require(record.originalPath == destination) { "恢复目标与原位置不一致" }
                LocalFileRecycleBin.restore(record, NativeNoReplaceCommit::commit, parameter("fingerprint"))
                result(true, "已恢复到原位置")
            }
            "zip_files" -> {
                val extracting = parameter("zip_mode") == "extract_no_replace"
                require(extracting || parameter("zip_mode") == "no_replace")
                if (extracting) extractManagedZip(source, Paths.get(requireNotNull(destination)), NativeNoReplaceCommit::commit, active)
                else {
                    val sources = parameter("sources")?.let { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it) } ?: listOf(path)
                    sources.forEach { value -> PathValidator.validateAndroidPath(value, tool.name)?.let { return it } }
                    zipManagedEntries(sources.map { Paths.get(it) }, Paths.get(requireNotNull(destination)), NativeNoReplaceCommit::commit, active)
                }
                result(true, if (extracting) "ZIP 解压完成，源压缩包保留" else "ZIP 压缩完成，源项目保留")
            }
            else -> result(false, "不支持的操作", FileCopyErrorCode.UNSUPPORTED)
        }
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (error: LocalCopyException) { result(false, error.message ?: "操作失败", error.code, error.stagingPath)
    } catch (error: FileAlreadyExistsException) { result(false, "目标已存在，请使用其他名称", FileCopyErrorCode.CONFLICT)
    } catch (error: AccessDeniedException) { result(false, "没有访问权限：${error.file}。请检查存储权限及目录访问限制。", FileCopyErrorCode.FAILED)
    } catch (error: NoSuchFileException) { result(false, "项目或父目录不存在：${error.file}。请刷新后重新选择。", FileCopyErrorCode.FAILED)
    } catch (error: LinkageError) { result(false, "原子文件组件不可用", FileCopyErrorCode.UNSUPPORTED)
    } catch (error: Exception) { result(false, error.message ?: "操作失败，请检查权限和文件状态", FileCopyErrorCode.FAILED) }
}

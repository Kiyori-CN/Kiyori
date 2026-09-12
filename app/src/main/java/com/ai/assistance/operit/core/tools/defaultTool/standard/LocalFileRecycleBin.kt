package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Properties
import java.util.UUID

internal data class RecycledFile(val id: String, val originalPath: String, val deletedAt: Long, val root: Path,
    val problem: String? = null)

/** 清单先落盘再移动；恢复与回收都沿用 no-replace，跨文件系统明确失败。 */
internal object LocalFileRecycleBin {
    fun recycle(source: Path, root: Path, fingerprint: String, commit: (Path, Path) -> Unit) {
        require(source.isAbsolute && root.isAbsolute && !root.normalize().startsWith(source.normalize())) { "不能回收根目录或回收站本身" }
        require(!source.normalize().startsWith(root.normalize())) { "回收站内的项目请使用恢复或彻底删除" }
        require(!Files.isSymbolicLink(source)) { "不支持回收符号链接" }
        // /sdcard 等别名不能绕过祖先检查；回收根不得落在被移动的目录树中。
        val actualRoot = root.toFile().canonicalFile.toPath()
        val actualSource = source.toRealPath()
        require(!actualRoot.startsWith(actualSource) && !actualSource.startsWith(actualRoot)) { "不能回收根目录、回收站或包含回收站的目录" }
        Files.createDirectories(root)
        check(root.toRealPath() == actualRoot) { "回收站位置已经变化，请重新检查" }
        check(inspectManagedTree(source).fingerprint == fingerprint) { "项目已经变化，请重新检查" }
        val entry = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
        val metadata = Properties().apply {
            setProperty("path", source.toAbsolutePath().normalize().toString())
            setProperty("deletedAt", System.currentTimeMillis().toString())
        }
        try {
            java.io.FileOutputStream(entry.resolve("record.properties").toFile()).use { metadata.store(it, null); it.fd.sync() }
            commit(source, entry.resolve("payload"))
            // 移动后读取本身失败也必须报告保留位置，不能把已移走的源当作普通未执行失败。
            val verified = try { inspectManagedTree(entry.resolve("payload")).fingerprint == fingerprint }
            catch (failure: Exception) {
                throw LocalCopyException(FileCopyErrorCode.FAILED, "项目已移入回收站，但复核失败，请检查回收内容", entry.toString(), failure)
            }
            if (!verified) {
                try { commit(entry.resolve("payload"), source) }
                catch (restore: Exception) {
                    throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "项目发生变化，内容保留在回收站，请检查", entry.toString(), restore)
                }
                throw IllegalStateException("项目发生变化，已恢复原位置，请重新检查")
            }
        } catch (failure: Exception) {
            // 只有确认 payload 未产生才允许清理空记录，不删除状态不明的用户内容。
            if (!Files.exists(entry.resolve("payload"), NOFOLLOW_LINKS)) {
                try { Files.deleteIfExists(entry.resolve("record.properties")); Files.delete(entry) }
                catch (cleanup: Exception) {
                    throw LocalCopyException(FileCopyErrorCode.FAILED, "${failure.message ?: "回收失败"}；回收记录清理失败", entry.toString(), failure)
                }
            }
            throw failure
        }
    }

    fun list(root: Path): List<RecycledFile> {
        try { Files.readAttributes(root, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS) }
        catch (missing: NoSuchFileException) { return emptyList() }
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "回收站路径不是实际目录" }
        return Files.newDirectoryStream(root).use { paths ->
            paths.map { entry ->
                try {
                    UUID.fromString(entry.fileName.toString())
                    require(Files.isDirectory(entry, NOFOLLOW_LINKS)) { "回收记录不是实际目录" }
                    require(Files.exists(entry.resolve("payload"), NOFOLLOW_LINKS)) { "回收内容不在预期位置，可能有未完成的清理，请检查记录目录" }
                    val metadata = readMetadata(entry)
                    val original = Paths.get(requireNotNull(metadata.getProperty("path")))
                    require(original.isAbsolute && original.parent != null) { "回收记录中的原路径无效" }
                    RecycledFile(entry.fileName.toString(), original.toString(), metadata.getProperty("deletedAt").toLong(), root)
                } catch (failure: Exception) {
                    // 一条损坏记录不能让整个回收站不可用，也不能静默藏起可能仍存在的用户内容。
                    RecycledFile(entry.fileName.toString(), entry.toString(), 0L, root,
                        "${failure.message ?: "回收记录无法读取"}；内容保留在 $entry")
                }
            }.sortedByDescending { it.deletedAt }
        }
    }

    fun restore(record: RecycledFile, commit: (Path, Path) -> Unit, fingerprint: String? = null) {
        val entry = checkedEntry(record)
        val destination = Paths.get(record.originalPath)
        require(destination.isAbsolute && destination.parent != null) { "回收记录中的原路径无效" }
        // 父目录被移动或删掉时保留回收项目，让用户先恢复父目录。
        check(Files.isDirectory(destination.parent, NOFOLLOW_LINKS)) { "原父目录不存在，请先恢复父目录" }
        fingerprint?.let { check(inspectManagedTree(entry.resolve("payload")).fingerprint == it) { "回收内容在确认后发生变化，请刷新并重新检查" } }
        commit(entry.resolve("payload"), destination)
        cleanupRecord(entry, "文件已恢复到 $destination")
    }

    fun delete(record: RecycledFile, commit: (Path, Path) -> Unit, fingerprint: String? = null) {
        val entry = checkedEntry(record)
        val payload = entry.resolve("payload")
        // 确认快照必须传到底层隔离操作，不能重新采样后把已变化内容当作用户已确认。
        deleteManagedEntry(payload, fingerprint ?: inspectManagedTree(payload).fingerprint, commit)
        cleanupRecord(entry, "回收内容已彻底删除")
    }

    private fun cleanupRecord(entry: Path, completed: String) {
        try { Files.delete(entry.resolve("record.properties")); Files.delete(entry) }
        catch (failure: Exception) {
            throw LocalCopyException(FileCopyErrorCode.FAILED, "$completed，但回收记录清理失败", entry.toString(), failure)
        }
    }

    private fun checkedEntry(record: RecycledFile): Path {
        require(record.problem == null) { record.problem.orEmpty() }
        UUID.fromString(record.id)
        return record.root.resolve(record.id).also {
            require(it.parent == record.root && Files.isDirectory(record.root, NOFOLLOW_LINKS) && Files.isDirectory(it, NOFOLLOW_LINKS)) { "回收记录路径无效" }
            val persisted = readMetadata(it)
            check(persisted.getProperty("path") == record.originalPath && persisted.getProperty("deletedAt") == record.deletedAt.toString()) { "回收记录已经变化，请刷新后重试" }
        }
    }

    fun recordForPayload(payload: Path): RecycledFile {
        val entry = requireNotNull(payload.parent)
        require(payload.fileName.toString() == "payload") { "不是回收项目" }
        val root = requireNotNull(entry.parent)
        val metadata = readMetadata(entry)
        // 按精确 UUID 读取，恢复 N 项不再反复扫描整个回收站而退化成 N² 次元数据访问。
        return RecycledFile(entry.fileName.toString(), requireNotNull(metadata.getProperty("path")),
            metadata.getProperty("deletedAt").toLong(), root).also { checkedEntry(it) }
    }

    private fun readMetadata(entry: Path): Properties {
        // 共享回收根里的记录不是可信输入；有界读取，损坏文件不能耗尽应用内存。
        val bytes = ByteArray(64 * 1024 + 1)
        var size = 0
        Files.newInputStream(entry.resolve("record.properties"), NOFOLLOW_LINKS).use { input ->
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
        }
        require(size < bytes.size) { "回收记录超过大小限制" }
        return Properties().apply { java.io.ByteArrayInputStream(bytes, 0, size).use { load(it) } }
    }
}

/** 共享卷不能回收到 Android/data：Android 的 FUSE/绑定挂载可令同一物理卷返回 EXDEV。 */
internal fun sharedFileRecycleRoot(externalFiles: Path): Path? {
    val files = externalFiles.toAbsolutePath().normalize()
    val app = files.parent ?: return null
    val data = app.parent ?: return null
    val android = data.parent ?: return null
    if (files.fileName.toString() != "files" || data.fileName.toString() != "data" || android.fileName.toString() != "Android") return null
    return android.parent?.resolve(".kiyori-recycle-bin")?.resolve(app.fileName)
}

private fun externalFileDirectories(context: android.content.Context): List<Path> =
    (context.getExternalFilesDirs(null).orEmpty().filterNotNull() + listOfNotNull(context.getExternalFilesDir(null)))
        .map { it.toPath() }.distinct()

internal fun fileRecycleRoots(context: android.content.Context): List<Path> = buildList {
    add(context.filesDir.resolve("file-recycle-bin").toPath())
    externalFileDirectories(context).forEach { external ->
        // 保留旧专属目录的读取；已有记录无需迁移或重写。
        add(external.resolve("file-recycle-bin"))
        sharedFileRecycleRoot(external)?.let(::add)
    }
}.distinct()

internal fun fileRecycleRootForSource(context: android.content.Context, source: Path): Path {
    val realSource = source.toRealPath()
    val internal = context.filesDir.toPath()
    if (realSource.startsWith(internal.parent.toRealPath())) return internal.resolve("file-recycle-bin")
    val externalDirectories = externalFileDirectories(context)
    externalDirectories.filter { Files.isDirectory(it) }.firstOrNull { realSource.startsWith(it.toRealPath()) }?.let { return it.resolve("file-recycle-bin") }
    val shared = externalDirectories.mapNotNull(::sharedFileRecycleRoot).distinct()
        .sortedByDescending { it.nameCount }
    return shared.firstOrNull { root -> root.parent.parent.let { volume -> Files.isDirectory(volume) && realSource.startsWith(volume.toRealPath()) } }
        ?: throw IllegalArgumentException("此位置没有同卷回收站；原项目保留。请选择已接入的手机存储。")
}

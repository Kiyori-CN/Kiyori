package com.ai.assistance.operit.core.tools.defaultTool.standard

import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Properties
import java.util.UUID

internal data class RecycledFile(val id: String, val originalPath: String, val deletedAt: Long, val root: Path)

/** 清单先落盘再移动；恢复与回收都沿用 no-replace，跨文件系统明确失败。 */
internal object LocalFileRecycleBin {
    fun recycle(source: Path, root: Path, fingerprint: String, commit: (Path, Path) -> Unit) {
        require(source.isAbsolute && root.isAbsolute && !root.normalize().startsWith(source.normalize())) { "不能回收根目录或回收站本身" }
        require(!source.normalize().startsWith(root.normalize())) { "回收站内的项目请使用恢复或彻底删除" }
        check(inspectManagedTree(source).fingerprint == fingerprint) { "项目已经变化，请重新检查" }
        Files.createDirectories(root)
        val entry = Files.createDirectory(root.resolve(UUID.randomUUID().toString()))
        val metadata = Properties().apply {
            setProperty("path", source.toAbsolutePath().normalize().toString())
            setProperty("deletedAt", System.currentTimeMillis().toString())
        }
        try {
            java.io.FileOutputStream(entry.resolve("record.properties").toFile()).use { metadata.store(it, null); it.fd.sync() }
            commit(source, entry.resolve("payload"))
            if (inspectManagedTree(entry.resolve("payload")).fingerprint != fingerprint) {
                try { commit(entry.resolve("payload"), source) }
                catch (restore: Exception) {
                    throw LocalCopyException(FileCopyErrorCode.SOURCE_CHANGED, "项目发生变化，内容保留在回收站，请检查", entry.toString(), restore)
                }
                throw IllegalStateException("项目发生变化，已恢复原位置，请重新检查")
            }
        } catch (failure: Exception) {
            // 只有确认 payload 未产生才允许清理空记录，不删除状态不明的用户内容。
            if (!Files.exists(entry.resolve("payload"), NOFOLLOW_LINKS)) {
                Files.deleteIfExists(entry.resolve("record.properties")); Files.delete(entry)
            }
            throw failure
        }
    }

    fun list(root: Path): List<RecycledFile> {
        if (!Files.exists(root, NOFOLLOW_LINKS)) return emptyList()
        require(Files.isDirectory(root, NOFOLLOW_LINKS)) { "回收站路径不是实际目录" }
        return Files.newDirectoryStream(root).use { paths ->
            paths.mapNotNull { entry ->
                if (!Files.isDirectory(entry, NOFOLLOW_LINKS) || !Files.exists(entry.resolve("payload"), NOFOLLOW_LINKS)) null
                else {
                    val metadata = Properties().apply { Files.newInputStream(entry.resolve("record.properties"), NOFOLLOW_LINKS).use { load(it) } }
                    RecycledFile(entry.fileName.toString(), requireNotNull(metadata.getProperty("path")), metadata.getProperty("deletedAt").toLong(), root)
                }
            }.sortedByDescending { it.deletedAt }
        }
    }

    fun restore(record: RecycledFile, commit: (Path, Path) -> Unit) {
        val entry = checkedEntry(record)
        val destination = Paths.get(record.originalPath)
        require(destination.isAbsolute && destination.parent != null) { "回收记录中的原路径无效" }
        // 父目录被移动或删掉时保留回收项目，让用户先恢复父目录。
        check(Files.isDirectory(destination.parent, NOFOLLOW_LINKS)) { "原父目录不存在，请先恢复父目录" }
        commit(entry.resolve("payload"), destination)
        Files.delete(entry.resolve("record.properties")); Files.delete(entry)
    }

    fun delete(record: RecycledFile, commit: (Path, Path) -> Unit, fingerprint: String? = null) {
        val entry = checkedEntry(record)
        val payload = entry.resolve("payload")
        // 确认快照必须传到底层隔离操作，不能重新采样后把已变化内容当作用户已确认。
        deleteManagedEntry(payload, fingerprint ?: inspectManagedTree(payload).fingerprint, commit)
        Files.delete(entry.resolve("record.properties")); Files.delete(entry)
    }

    private fun checkedEntry(record: RecycledFile): Path {
        UUID.fromString(record.id)
        return record.root.resolve(record.id).also {
            require(it.parent == record.root && Files.isDirectory(record.root, NOFOLLOW_LINKS) && Files.isDirectory(it, NOFOLLOW_LINKS)) { "回收记录路径无效" }
            val persisted = Properties().apply { Files.newInputStream(it.resolve("record.properties"), NOFOLLOW_LINKS).use { input -> load(input) } }
            check(persisted.getProperty("path") == record.originalPath && persisted.getProperty("deletedAt") == record.deletedAt.toString()) { "回收记录已经变化，请刷新后重试" }
        }
    }

    fun recordForPayload(payload: Path): RecycledFile {
        val entry = requireNotNull(payload.parent)
        require(payload.fileName.toString() == "payload") { "不是回收项目" }
        return list(requireNotNull(entry.parent)).single { it.id == entry.fileName.toString() }.also { checkedEntry(it) }
    }
}

internal fun fileRecycleRoots(context: android.content.Context): List<Path> =
    listOfNotNull(context.filesDir.resolve("file-recycle-bin").toPath(), context.getExternalFilesDir(null)?.resolve("file-recycle-bin")?.toPath())

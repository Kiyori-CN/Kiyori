package com.ai.assistance.operit.core.tools.skill

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** 市场来源身份在 registry 锁内重验，来源标记随完整目录一起发布。 */
internal data class SkillImportPublication(
    val existingDirectory: File?,
    val validateExisting: (File) -> Unit,
    val prepareDirectory: (File) -> Unit,
)

internal fun resolveSkillImportTarget(root: File, name: String): File {
    val normalized = name.trim()
    require(normalized.isNotEmpty() && normalized != "." && normalized != "..")
    require(normalized.none { it == '/' || it == '\\' || it == ':' || it.code < 32 })
    require(!normalized.startsWith(".import_tmp_"))
    val target = File(root, normalized)
    require(target.canonicalFile.parentFile == root.canonicalFile) { "Skill name must identify one child directory" }
    return target
}

internal fun publishSkillImportDirectory(
    source: File,
    root: File,
    name: String,
    publication: SkillImportPublication? = null,
    onPublished: (File) -> Unit = {},
): File {
    val target = resolveSkillImportTarget(root, name)
    val sourcePath = source.canonicalFile.toPath()
    require(sourcePath.startsWith(root.canonicalFile.toPath()) && sourcePath != root.canonicalFile.toPath())
    require(!Files.isSymbolicLink(source.toPath()) && source.isDirectory)
    require(!sourcePath.startsWith(target.canonicalFile.toPath()))
    val expected = publication?.existingDirectory
    val exists = Files.exists(target.toPath(), NOFOLLOW_LINKS)
    if (expected != null) {
        check(expected.canonicalFile == target.canonicalFile) { "Skill update changed its directory name" }
        check(exists && !Files.isSymbolicLink(target.toPath()) && target.isDirectory) { "Installed skill changed before update" }
        publication.validateExisting(target)
    } else if (exists) {
        throw java.nio.file.FileAlreadyExistsException(target.path)
    }
    publication?.prepareDirectory?.invoke(source)
    val backup = File(root, ".import_tmp_backup_${UUID.randomUUID()}")
    if (exists) Files.move(target.toPath(), backup.toPath())
    // 下载、校验、标记准备均已成功才移动旧目录。失败恢复旧版本，不吞掉发布错误。
    try { Files.move(source.toPath(), target.toPath()) }
    catch (failure: Exception) {
        if (exists) {
            try { Files.move(backup.toPath(), target.toPath()) }
            catch (restore: Exception) { failure.addSuppressed(restore) }
        }
        throw failure
    }
    onPublished(target)
    if (exists) deleteSkillTree(backup)
    return target
}

internal fun deleteSkillTree(root: File) {
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

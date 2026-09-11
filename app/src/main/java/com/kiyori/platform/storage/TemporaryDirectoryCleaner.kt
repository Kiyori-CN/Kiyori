package com.kiyori.platform.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 只清理由调用方拥有的临时目录；不跟随目录内链接，也不吞掉枚举或删除失败。 */
internal object TemporaryDirectoryCleaner {
    fun clean(directory: File, preserveRootNoMedia: Boolean): Int {
        val root = directory.toPath()
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return 0
        val attributes = Files.readAttributes(root, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        if (attributes.isSymbolicLink) throw IOException("Temporary directory must not be a symbolic link")
        if (!attributes.isDirectory) return 0

        var deletedFiles = 0
        // walkFileTree 默认不跟随符号链接，使用迭代遍历，避免链接环与深目录造成递归栈溢出。
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val preserveMarker = preserveRootNoMedia && file.parent == root &&
                    file.fileName.toString() == ".nomedia" && attrs.isRegularFile
                if (!preserveMarker) {
                    Files.delete(file)
                    deletedFiles++
                }
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                if (dir != root) Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
        if (preserveRootNoMedia) {
            val marker = root.resolve(".nomedia")
            if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) Files.createFile(marker)
        }
        return deletedFiles
    }
}

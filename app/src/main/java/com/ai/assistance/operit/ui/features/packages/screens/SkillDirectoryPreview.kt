package com.ai.assistance.operit.ui.features.packages.screens

import java.io.File
import java.io.IOException
import java.nio.file.Files

internal data class SkillDirectoryPreview(
    val fileCount: Int,
    val folderCount: Int,
    val text: String,
    val hiddenEntryCount: Int,
)

internal fun buildSkillDirectoryPreview(
    directory: File,
    checkCancelled: () -> Unit = {},
): SkillDirectoryPreview {
    if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory) {
        throw IOException("Skill directory is not readable")
    }
    data class Entry(val file: File, val depth: Int)
    val pending = ArrayDeque<Entry>()
    val lines = mutableListOf<String>()
    var files = 0
    var folders = 0
    fun addChildren(parent: File, depth: Int) {
        checkCancelled()
        val children = parent.listFiles() ?: throw IOException("Skill directory listing failed")
        children.sortedWith(compareBy<File>(
            { Files.isSymbolicLink(it.toPath()) || !it.isDirectory },
            { it.name.lowercase() },
            { it.name },
        )).asReversed().forEach { pending.addLast(Entry(it, depth)) }
    }
    addChildren(directory, 0)
    while (pending.isNotEmpty()) {
        checkCancelled()
        val (file, depth) = pending.removeLast()
        val link = Files.isSymbolicLink(file.toPath())
        val isDirectory = !link && file.isDirectory
        if (isDirectory) folders++ else files++
        if (lines.size < 18) {
            lines += "${"  ".repeat(depth)}${file.name}${if (isDirectory) "/" else if (link) " →" else ""}"
        }
        // 链接只展示入口，不遍历目标，避免循环和枚举技能目录之外的数据。
        if (isDirectory) addChildren(file, depth + 1)
    }
    return SkillDirectoryPreview(files, folders, lines.joinToString("\n"), files + folders - lines.size)
}

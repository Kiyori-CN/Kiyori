package com.ai.assistance.operit.data.mcp

import java.io.File
import java.nio.file.Files

private val mcpProjectMarkers = setOf("mcp.config.json", "readme.md", "package.json", "index.js", "index.py", "main.py", "main.js")

/** 已发布路径优先；旧安装只接受一个有明确入口的候选，不按文件遍历顺序选择。 */
internal fun resolveMcpProjectDirectory(root: File, installedPath: String?): File? {
    if (!root.isDirectory || Files.isSymbolicLink(root.toPath())) return null
    val rootPath = root.canonicalFile.toPath()
    if (!installedPath.isNullOrBlank()) {
        val stored = File(installedPath)
        val storedPath = stored.absoluteFile.toPath().normalize()
        if (!storedPath.startsWith(rootPath) || !stored.isDirectory || !stored.canonicalFile.toPath().startsWith(rootPath)) return null
        var cursor = storedPath
        while (cursor != rootPath) {
            if (Files.isSymbolicLink(cursor)) return null
            cursor = cursor.parent ?: return null
        }
        return stored
    }
    val pending = ArrayDeque<File>().apply { add(root) }
    var candidate: File? = null
    while (pending.isNotEmpty()) {
        val directory = pending.removeFirst()
        val children = directory.listFiles() ?: throw java.io.IOException("Cannot inspect MCP project directory")
        if (children.any { !Files.isSymbolicLink(it.toPath()) && it.isFile && it.name.lowercase(java.util.Locale.ROOT) in mcpProjectMarkers }) {
            if (directory == root) return root
            if (candidate != null) return null
            candidate = directory
            // 已找到项目根后不扫描依赖目录，避免把依赖误认为另一个项目。
        } else children.filterTo(pending) { !Files.isSymbolicLink(it.toPath()) && it.isDirectory }
    }
    return candidate
}

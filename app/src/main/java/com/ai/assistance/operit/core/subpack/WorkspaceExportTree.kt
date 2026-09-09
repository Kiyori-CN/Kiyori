package com.ai.assistance.operit.core.subpack

import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal data class WorkspaceExportEntry(val file: File, val relativePath: String, val isDirectory: Boolean)

/** Android 与 Windows 共用导出边界；不跟随越界链接，不把读取失败当作空目录。 */
internal fun collectWorkspaceExportEntries(root: File, checkCancellation: () -> Unit = {}): List<WorkspaceExportEntry> {
    val rootPath = root.toPath().toRealPath()
    check(root.isDirectory) { "Export source is not a directory" }
    val entries = mutableListOf<WorkspaceExportEntry>()
    data class Frame(val real: java.nio.file.Path, val relative: String, val children: Iterator<File>)
    val ancestors = mutableSetOf<java.nio.file.Path>()
    val stack = java.util.ArrayDeque<Frame>()
    fun enter(directory: File, relative: String) {
        checkCancellation()
        val real = directory.toPath().toRealPath()
        check(real.startsWith(rootPath)) { "Export link leaves the workspace" }
        check(ancestors.add(real)) { "Export source contains a directory cycle" }
        val children = directory.listFiles() ?: error("Unable to read export source directory")
        stack.addLast(Frame(real, relative, children.sortedBy { it.name }.iterator()))
    }
    // 使用显式栈，深层目录不依赖 JVM 调用栈大小。
    enter(root, "")
    while (stack.isNotEmpty()) {
        checkCancellation()
        val frame = stack.last()
        if (!frame.children.hasNext()) {
            ancestors.remove(stack.removeLast().real)
            continue
        }
        val child = frame.children.next()
        check(child.name.none { it == '\\' || it.isISOControl() }) { "Export source contains an unsupported file name" }
        val target = child.toPath().toRealPath()
        check(target.startsWith(rootPath)) { "Export link leaves the workspace" }
        val path = if (frame.relative.isEmpty()) child.name else "${frame.relative}/${child.name}"
        val isDirectory = child.isDirectory
        check(isDirectory || child.isFile) { "Export source contains an unsupported file type" }
        entries += WorkspaceExportEntry(target.toFile(), path, isDirectory)
        if (isDirectory) enter(child, path)
    }
    return entries
}

internal fun copyWorkspaceExportBytes(input: InputStream, output: OutputStream, checkCancellation: () -> Unit) {
    val buffer = ByteArray(32 * 1024)
    while (true) {
        checkCancellation()
        val count = input.read(buffer)
        if (count < 0) break
        if (count > 0) output.write(buffer, 0, count)
    }
}

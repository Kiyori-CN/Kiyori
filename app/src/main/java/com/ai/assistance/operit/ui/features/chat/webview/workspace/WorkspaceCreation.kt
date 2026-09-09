package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** 模板仅写入本次 staging；已有工作区必须由用户明确复用，不能因重新绑定而重置。 */
internal fun createWorkspaceDirectorySafely(root: File, name: String, populate: (File) -> Unit): File {
    require(isWorkspaceEntryNameValid(name)) { "Invalid workspace directory name" }
    if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create workspace root")
    val destination = File(root, name)
    if (destination.exists()) throw IOException("Workspace already exists; select the existing workspace")
    val staging = Files.createTempDirectory(root.toPath(), ".workspace-")
    try {
        populate(staging.toFile())
        // 无 REPLACE_EXISTING：并发出现的目标同样必须保留。
        Files.move(staging, destination.toPath())
        return destination
    } finally {
        if (Files.exists(staging)) {
            // Files.walk 默认不跟随符号链接，只清理由本次调用创建的临时树。
            Files.walk(staging).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}

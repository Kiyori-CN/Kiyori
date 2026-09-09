package com.ai.assistance.operit.ui.features.chat.components

import java.io.File
import java.nio.file.Files
import java.util.UUID

/** 发布前仅写临时产物，成功后才给出最终文件；不替换已有导出。 */
internal class WorkspaceExportFile(directory: File, prefix: String, extension: String) {
    val temporary: File
    val destination: File

    init {
        check(directory.isDirectory || directory.mkdirs()) { "Unable to create export directory" }
        temporary = File.createTempFile(".workspace-export-", ".$extension", directory)
        destination = File(directory, "${prefix}_${UUID.randomUUID()}.$extension")
    }

    fun publish(): File {
        check(temporary.isFile && temporary.length() > 0) { "Export did not produce a complete file" }
        Files.move(temporary.toPath(), destination.toPath())
        return destination
    }
}

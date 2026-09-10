package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

enum class FileManagerOpenKind { TEXT, MEDIA, IMAGE, SYSTEM }

/** 扩展名只决定呈现入口；文本读取仍需校验大小、UTF-8 和二进制内容。ts 优先作为源码。 */
fun fileManagerOpenKind(file: FileItem): FileManagerOpenKind {
    val extension = file.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    if (extension in setOf("ts", "csv", "tsv", "svg", "markdown", "sql", "properties", "gradle", "go", "dart", "h", "hpp", "mjs", "cjs") ||
        file.name.lowercase(java.util.Locale.ROOT) in setOf("readme", "license", "makefile", "dockerfile", ".gitignore", ".env")) return FileManagerOpenKind.TEXT
    return when (fileManagerFileKind(file)) {
        FileManagerFileKind.TEXT, FileManagerFileKind.CODE -> FileManagerOpenKind.TEXT
        FileManagerFileKind.VIDEO, FileManagerFileKind.AUDIO -> FileManagerOpenKind.MEDIA
        FileManagerFileKind.IMAGE -> FileManagerOpenKind.IMAGE
        else -> FileManagerOpenKind.SYSTEM
    }
}

data class FileManagerOpenRequest(val id: Long, val path: String, val name: String, val kind: FileManagerOpenKind)

data class FileManagerTextState(
    val id: Long,
    val path: String,
    val environment: String?,
    val name: String,
    val content: String = "",
    val savedContent: String = "",
    val loading: Boolean = true,
    val readable: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val unknown: Boolean = false,
    val savedPath: String? = null,
) {
    val dirty: Boolean get() = content != savedContent
}

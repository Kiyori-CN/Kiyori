package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 仅用于展示，不凭扩展名判断文件真实内容或决定执行权限。 */
enum class FileManagerFileKind { PARENT, FOLDER, IMAGE, AUDIO, VIDEO, PDF, DOCUMENT, SHEET, PRESENTATION, ARCHIVE, CODE, TEXT, PACKAGE, FILE }

fun fileManagerFileKind(file: FileItem): FileManagerFileKind {
    if (file.name == "..") return FileManagerFileKind.PARENT
    if (file.isDirectory) return FileManagerFileKind.FOLDER
    return when (file.displayName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif", "svg" -> FileManagerFileKind.IMAGE
        "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus" -> FileManagerFileKind.AUDIO
        "mp4", "avi", "mkv", "mov", "webm", "m4v", "ts", "3gp" -> FileManagerFileKind.VIDEO
        "pdf" -> FileManagerFileKind.PDF
        "doc", "docx", "odt", "rtf" -> FileManagerFileKind.DOCUMENT
        "xls", "xlsx", "ods", "csv", "tsv" -> FileManagerFileKind.SHEET
        "ppt", "pptx", "odp" -> FileManagerFileKind.PRESENTATION
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz" -> FileManagerFileKind.ARCHIVE
        "js", "ts", "jsx", "tsx", "py", "kt", "kts", "java", "json", "xml", "html", "css", "sh", "yml", "yaml", "toml", "c", "cpp", "rs" -> FileManagerFileKind.CODE
        "txt", "md", "log", "ini", "conf" -> FileManagerFileKind.TEXT
        "apk", "apks", "xapk", "aab" -> FileManagerFileKind.PACKAGE
        else -> FileManagerFileKind.FILE
    }
}

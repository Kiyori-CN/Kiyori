package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 标签项数据类 */
data class TabItem(
    val path: String,
    val title: String,
    val environment: String? = null
)

/** 文件项数据类 */
data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val fullPath: String? = null
)

enum class FileManagerPane {
    LEFT,
    RIGHT,
}

enum class FileManagerSortMode {
    NAME,
    SIZE,
    MODIFIED,
}

enum class FileManagerBackAction {
    HISTORY,
    PARENT,
    EXIT,
}

data class FileManagerLocation(
    val path: String,
    val environment: String?,
)

data class FileManagerPaneState(
    val path: String,
    val environment: String?,
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val backStack: List<FileManagerLocation> = emptyList(),
    val forwardStack: List<FileManagerLocation> = emptyList(),
)

internal fun fileManagerBackAction(
    state: FileManagerPaneState,
    initialStoragePath: String,
): FileManagerBackAction = when {
    state.backStack.isNotEmpty() -> FileManagerBackAction.HISTORY
    state.path != initialStoragePath || state.environment != null ->
        if (state.path == "/") FileManagerBackAction.EXIT else FileManagerBackAction.PARENT
    else -> FileManagerBackAction.EXIT
}

internal fun fileManagerParentPath(path: String): String? {
    if (path == "/") return null
    return path.trimEnd('/').substringBeforeLast('/').ifBlank { "/" }
}

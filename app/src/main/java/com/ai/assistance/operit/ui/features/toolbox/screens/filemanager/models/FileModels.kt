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
    val fullPath: String? = null,
    /** 原始后端时间标签；当后端不能提供 epoch millis 时仍可显示真实信息。 */
    val lastModifiedLabel: String = "",
    /** 回收记录以 UUID 作稳定身份，名称与原路径仅用于展示，防止同名项目互相选中。 */
    val displayName: String = name,
    val recycledOriginalPath: String? = null,
    val recycleProblem: String? = null,
)

enum class FileManagerPane {
    LEFT,
    RIGHT,
}

typealias FileManagerSortMode = com.ai.assistance.operit.data.preferences.FileManagerSortMode

enum class FileManagerBackAction {
    HISTORY,
    PARENT,
    INITIAL_STORAGE,
    EXIT,
}

data class FileManagerLocation(
    val path: String,
    val environment: String?,
)

data class FileManagerScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)

data class FileManagerPaneState(
    val path: String,
    val environment: String?,
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val backStack: List<FileManagerLocation> = emptyList(),
    val forwardStack: List<FileManagerLocation> = emptyList(),
    /** 当前目录完整快照；files 是同一快照的可见投影，不重新读取存储来筛选。 */
    val entries: List<FileItem> = emptyList(),
    val filterQuery: String = "",
)

internal fun fileManagerBackAction(
    state: FileManagerPaneState,
    initialStoragePath: String,
): FileManagerBackAction = when {
    state.backStack.isNotEmpty() -> FileManagerBackAction.HISTORY
    state.environment == "recycle" -> FileManagerBackAction.INITIAL_STORAGE
    state.path == initialStoragePath && state.environment == null -> FileManagerBackAction.EXIT
    fileManagerParentPath(state.path) != null -> FileManagerBackAction.PARENT
    else -> FileManagerBackAction.INITIAL_STORAGE
}

internal fun fileManagerParentPath(path: String): String? {
    if (path == "/") return null
    return path.trimEnd('/').substringBeforeLast('/').ifBlank { "/" }
}

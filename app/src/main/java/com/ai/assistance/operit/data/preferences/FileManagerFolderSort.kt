package com.ai.assistance.operit.data.preferences

import kotlinx.serialization.Serializable

@Serializable
data class FileManagerFolderSort(
    val path: String,
    val environment: String? = null,
    val mode: FileManagerSortMode = FileManagerSortMode.NAME,
    val descending: Boolean = false,
) {
    fun matches(path: String, environment: String?): Boolean =
        normalizeSortPath(this.path) == normalizeSortPath(path) &&
            normalizeSortEnvironment(this.environment) == normalizeSortEnvironment(environment)
}

private fun normalizeSortPath(path: String) = "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/")
private fun normalizeSortEnvironment(environment: String?) = environment?.takeUnless { it.isBlank() || it == "android" }

internal fun FileManagerSettings.folderSort(path: String, environment: String?): FileManagerFolderSort? =
    folderSorts.lastOrNull { it.matches(path, environment) }

/** 覆盖按写入顺序排列，读取取最后一条；超出上限时丢弃最久未调整的路径，偏好文件不会无限增长。 */
internal const val MAX_FOLDER_SORT_OVERRIDES = 200

internal fun FileManagerSettings.withSort(path: String, environment: String?, mode: FileManagerSortMode,
    descending: Boolean, folderOnly: Boolean): FileManagerSettings {
    val remaining = folderSorts.filterNot { it.matches(path, environment) }
    return if (folderOnly) copy(folderSorts = (remaining + FileManagerFolderSort(normalizeSortPath(path),
        normalizeSortEnvironment(environment), mode, descending)).takeLast(MAX_FOLDER_SORT_OVERRIDES))
    else copy(sortMode = mode, sortDescending = descending, folderSorts = remaining)
}

/** 仅在底层已确认重命名成功后迁移本路径与子目录；完整路径段防止误迁移同名前缀。 */
internal fun FileManagerSettings.moveFolderSorts(source: String, target: String, environment: String?): FileManagerSettings {
    val from = normalizeSortPath(source)
    val to = normalizeSortPath(target)
    val moved = folderSorts.filter { normalizeSortEnvironment(it.environment) == normalizeSortEnvironment(environment) &&
        (it.path == from || it.path.startsWith("$from/")) }
        .map { it.copy(path = to + it.path.removePrefix(from)) }
    return copy(folderSorts = folderSorts.filterNot { entry ->
        normalizeSortEnvironment(entry.environment) == normalizeSortEnvironment(environment) &&
            (entry.path == from || entry.path.startsWith("$from/") || moved.any { it.matches(entry.path, entry.environment) })
    } + moved)
}

package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models

/** 数字段按位数比较而非转 Long，超长数字文件名也不会溢出。 */
internal fun compareFileManagerNames(left: String, right: String): Int {
    var a = 0
    var b = 0
    while (a < left.length && b < right.length) {
        if (left[a] in '0'..'9' && right[b] in '0'..'9') {
            val startA = a
            val startB = b
            while (a < left.length && left[a] in '0'..'9') a++
            while (b < right.length && right[b] in '0'..'9') b++
            var significantA = startA
            var significantB = startB
            while (significantA < a - 1 && left[significantA] == '0') significantA++
            while (significantB < b - 1 && right[significantB] == '0') significantB++
            val lengthComparison = (a - significantA).compareTo(b - significantB)
            if (lengthComparison != 0) return lengthComparison
            for (offset in 0 until a - significantA) {
                val comparison = left[significantA + offset].compareTo(right[significantB + offset])
                if (comparison != 0) return comparison
            }
            val zeroComparison = (a - startA).compareTo(b - startB)
            if (zeroComparison != 0) return zeroComparison
        } else {
            val comparison = left[a].lowercaseChar().compareTo(right[b].lowercaseChar())
            if (comparison != 0) return comparison
            a++
            b++
        }
    }
    return (left.length - a).compareTo(right.length - b).takeIf { it != 0 } ?: left.compareTo(right)
}
internal fun fileManagerComparator(mode: FileManagerSortMode, descending: Boolean): Comparator<FileItem> =
    Comparator { left, right ->
        // 目录优先与排序方向无关，避免降序时把目录推到文件后面。
        val directoryOrder = right.isDirectory.compareTo(left.isDirectory)
        if (directoryOrder != 0) directoryOrder else {
            val fieldOrder = when (mode) {
                FileManagerSortMode.NAME -> compareFileManagerNames(left.displayName, right.displayName)
                FileManagerSortMode.SIZE -> left.size.compareTo(right.size)
                FileManagerSortMode.MODIFIED -> left.lastModified.compareTo(right.lastModified)
                FileManagerSortMode.FORMAT -> compareFileManagerNames(fileManagerExtension(left.displayName), fileManagerExtension(right.displayName))
            }
            if (fieldOrder != 0) {
                if (descending) -fieldOrder else fieldOrder
            } else compareFileManagerNames(left.name, right.name)
        }
    }

/** 到达内部存储初始目录后仍可继续向上，直到系统文件树的真实根目录 "/"。 */
internal fun fileManagerCanNavigateUp(location: FileManagerLocation): Boolean =
    location.environment != "recycle" && fileManagerParentPath(location.path) != null

internal fun fileManagerVisibleEntries(
    entries: List<FileItem>, query: String, showHidden: Boolean, canNavigateUp: Boolean,
    filter: FileManagerFilter = FileManagerFilter(),
): List<FileItem> = buildList {
    if (canNavigateUp) add(FileItem("..", true))
    addAll(entries.filter { it.name != "." && it.name != ".." &&
        (showHidden || it.recycledOriginalPath != null || !it.name.startsWith('.')) && it.displayName.contains(query, ignoreCase = true) && filter.matches(it) })
}

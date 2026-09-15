package com.ai.assistance.operit.ui.features.memory.viewmodel

import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.normalizeFolderPath

/**
 * 文件夹模式的纯逻辑：当前目录只展示直接下级，与检索使用的整棵子树范围分开。
 * 根用空路径表示，不存在名为“未分类”的目录；没有归属的条目直接躺在根目录里。
 */
internal object MemoryFolderBrowsePolicy {

    /** 当前目录的上一级；已经在根目录时仍是根目录。 */
    fun parentOf(path: String): String {
        val normalized = normalizeFolderPath(path) ?: return ""
        return normalized.substringBeforeLast('/', "")
    }

    /** 面包屑分段：名称与可直接跳转的完整路径，根目录由调用方提供本地化名称。 */
    fun breadcrumb(path: String): List<FolderCrumb> {
        val normalized = normalizeFolderPath(path) ?: return emptyList()
        var current = ""
        return normalized.split('/').map { name ->
            current = if (current.isEmpty()) name else "$current/$name"
            FolderCrumb(name = name, path = current)
        }
    }

    /**
     * 当前目录的直接下级目录。[folderPaths] 已包含中间层级，这里只按前缀取下一段，
     * 不再从叶子路径推断，避免同名前缀（“项目”与“项目备份”）混入。
     */
    fun childFolders(folderPaths: List<String>, current: String): List<String> {
        val parent = normalizeFolderPath(current)
        val prefix = parent?.let { "$it/" }
        return folderPaths.asSequence()
            .mapNotNull(::normalizeFolderPath)
            .filter { path ->
                if (prefix == null) !path.contains('/')
                else path.startsWith(prefix) && !path.removePrefix(prefix).contains('/')
            }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .toList()
    }

    /** 直接躺在当前目录里的条目；子目录内容归子目录展示。 */
    fun directEntries(memories: List<Memory>, current: String): List<Memory> {
        val folder = normalizeFolderPath(current)
        return memories.filter { normalizeFolderPath(it.folderPath) == folder }
    }

    /** 目录行的数量提示：包含子目录内容，与进入后逐级看到的总量一致。 */
    fun subtreeCount(memories: List<Memory>, folderPath: String): Int {
        val folder = normalizeFolderPath(folderPath) ?: return memories.size
        return memories.count { memory ->
            val path = normalizeFolderPath(memory.folderPath)
            path == folder || path?.startsWith("$folder/") == true
        }
    }
}

internal data class FolderCrumb(val name: String, val path: String)

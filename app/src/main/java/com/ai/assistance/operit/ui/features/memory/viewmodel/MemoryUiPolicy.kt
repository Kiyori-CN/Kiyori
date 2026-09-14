package com.ai.assistance.operit.ui.features.memory.viewmodel

import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.normalizeFolderPath

/** 目录身份按完整路径段比较，不能把「项目」误匹配成「项目备份」。 */
internal object MemoryUiPolicy {
    fun isWithinFolder(path: String, parent: String): Boolean {
        val normalized = normalizeFolderPath(path) ?: return false
        val root = normalizeFolderPath(parent) ?: return false
        return normalized == root || normalized.startsWith("$root/")
    }

    fun renamedSelection(selected: String, oldPath: String, newPath: String): String {
        if (!isWithinFolder(selected, oldPath)) return selected
        val old = requireNotNull(normalizeFolderPath(oldPath))
        val new = requireNotNull(normalizeFolderPath(newPath))
        return new + requireNotNull(normalizeFolderPath(selected)).removePrefix(old)
    }
}

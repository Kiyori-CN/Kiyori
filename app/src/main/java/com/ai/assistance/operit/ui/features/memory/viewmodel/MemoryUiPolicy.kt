package com.ai.assistance.operit.ui.features.memory.viewmodel

import com.ai.assistance.operit.data.repository.MemoryRepository.Companion.normalizeFolderPath

/** 目录身份按完整路径段比较，不能把「项目」误匹配成「项目备份」。 */
internal object MemoryUiPolicy {
    /** 查询改变后，旧图谱选择不能继续作为关联或批量删除的目标。 */
    fun beginSearch(state: MemoryUiState): MemoryUiState = state.copy(
        isLoading = true,
        error = null,
        selectedNodeId = null,
        selectedEdge = null,
        isLinkingMode = false,
        linkingNodeIds = emptyList(),
        isBoxSelectionMode = false,
        boxSelectedNodeIds = emptySet(),
        showBatchDeleteConfirm = false,
    )

    /** 整理面板只能重置自己控制的条件，不能把用户带离当前目录或清除查询草稿。 */
    fun resetOrganizationFilters(state: MemoryUiState): MemoryUiState = state.copy(
        categoryFilter = null,
        tagFilter = null,
        showArchived = false,
        sortByTitle = false,
    )

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

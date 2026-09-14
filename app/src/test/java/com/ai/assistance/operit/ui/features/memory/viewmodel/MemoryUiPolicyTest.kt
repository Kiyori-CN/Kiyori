package com.ai.assistance.operit.ui.features.memory.viewmodel

import org.junit.Assert.*
import org.junit.Test

class MemoryUiPolicyTest {
    @Test fun newQueryInvalidatesOldGraphDeleteAndLinkTargets() {
        val state = MemoryUiState(
            selectedFolderPath = "新目录",
            showGraph = true,
            selectedNodeId = "旧节点",
            isBoxSelectionMode = true,
            boxSelectedNodeIds = setOf("旧节点", "已被筛选隐藏的节点"),
            showBatchDeleteConfirm = true,
            isLinkingMode = true,
            linkingNodeIds = listOf("旧节点"),
        )
        val loading = MemoryUiPolicy.beginSearch(state)
        assertTrue(loading.isLoading)
        assertTrue(loading.showGraph)
        assertEquals("新目录", loading.selectedFolderPath)
        assertNull(loading.selectedNodeId)
        assertFalse(loading.isBoxSelectionMode)
        assertTrue(loading.boxSelectedNodeIds.isEmpty())
        assertFalse(loading.showBatchDeleteConfirm)
        assertFalse(loading.isLinkingMode)
        assertTrue(loading.linkingNodeIds.isEmpty())
    }

    @Test fun organizationResetPreservesLocationKindAndUnsubmittedQuery() {
        val state = MemoryUiState(
            selectedFolderPath = "项目/参考资料",
            libraryKind = "knowledge",
            searchQuery = "尚未提交的查询",
            appliedSearchQuery = "已有查询",
            showGraph = true,
            categoryFilter = "fact",
            tagFilter = "important",
            showArchived = true,
            sortByTitle = true,
        )
        val reset = MemoryUiPolicy.resetOrganizationFilters(state)
        assertEquals(state.selectedFolderPath, reset.selectedFolderPath)
        assertEquals(state.libraryKind, reset.libraryKind)
        assertEquals(state.searchQuery, reset.searchQuery)
        assertEquals(state.appliedSearchQuery, reset.appliedSearchQuery)
        assertTrue(reset.showGraph)
        assertNull(reset.categoryFilter)
        assertNull(reset.tagFilter)
        assertFalse(reset.showArchived)
        assertFalse(reset.sortByTitle)
    }

    @Test fun renameMovesTheSelectedDescendantAndNormalizesSeparators() {
        assertEquals("新项目/资料/证据", MemoryUiPolicy.renamedSelection("项目/资料/证据", "项目", " 新项目/ "))
        assertEquals("新项目/资料", MemoryUiPolicy.renamedSelection("项目\\资料", "项目", "新项目"))
    }
    @Test fun similarSiblingNamesAndRootAreNotDescendants() {
        assertFalse(MemoryUiPolicy.isWithinFolder("项目备份/资料", "项目"))
        assertFalse(MemoryUiPolicy.isWithinFolder("", "项目"))
        assertFalse(MemoryUiPolicy.isWithinFolder("项目", ""))
        assertEquals("项目备份", MemoryUiPolicy.renamedSelection("项目备份", "项目", "新项目"))
    }
    @Test fun sameFolderAndDescendantsAreMatched() {
        assertTrue(MemoryUiPolicy.isWithinFolder("项目", "项目"))
        assertTrue(MemoryUiPolicy.isWithinFolder("项目/资料", "项目"))
        assertEquals("新项目", MemoryUiPolicy.renamedSelection("项目", "项目", "新项目"))
    }
}

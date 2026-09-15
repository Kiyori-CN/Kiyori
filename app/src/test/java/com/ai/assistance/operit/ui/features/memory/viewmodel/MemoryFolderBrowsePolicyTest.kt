package com.ai.assistance.operit.ui.features.memory.viewmodel

import com.ai.assistance.operit.data.model.Memory
import org.junit.Assert.*
import org.junit.Test

class MemoryFolderBrowsePolicyTest {

    private fun memory(id: Long, folderPath: String?) = Memory(id = id, title = "t$id", content = "c", folderPath = folderPath)

    @Test fun parentGoesUpOneLevelAndStopsAtRoot() {
        assertEquals("项目/资料", MemoryFolderBrowsePolicy.parentOf("项目/资料/证据"))
        assertEquals("", MemoryFolderBrowsePolicy.parentOf("项目"))
        assertEquals("", MemoryFolderBrowsePolicy.parentOf(""))
        assertEquals("项目", MemoryFolderBrowsePolicy.parentOf("项目\\资料"))
    }

    @Test fun breadcrumbCarriesJumpTargetForEveryLevel() {
        val crumbs = MemoryFolderBrowsePolicy.breadcrumb("项目/资料/证据")
        assertEquals(listOf("项目", "资料", "证据"), crumbs.map { it.name })
        assertEquals(listOf("项目", "项目/资料", "项目/资料/证据"), crumbs.map { it.path })
        assertTrue(MemoryFolderBrowsePolicy.breadcrumb("").isEmpty())
    }

    @Test fun onlyDirectChildrenAreListedAndSiblingPrefixesAreExcluded() {
        val paths = listOf("项目", "项目/资料", "项目/资料/证据", "项目备份", "项目备份/资料", "生活")
        // 顶层按名称排序（码点序），同前缀的兄弟目录彼此独立。
        assertEquals(listOf("生活", "项目", "项目备份"), MemoryFolderBrowsePolicy.childFolders(paths, ""))
        assertEquals(listOf("项目/资料"), MemoryFolderBrowsePolicy.childFolders(paths, "项目"))
        assertEquals(listOf("项目/资料/证据"), MemoryFolderBrowsePolicy.childFolders(paths, "项目/资料"))
        assertTrue(MemoryFolderBrowsePolicy.childFolders(paths, "项目/资料/证据").isEmpty())
    }

    @Test fun directEntriesExcludeSubfolderContent() {
        val memories = listOf(memory(1, null), memory(2, "项目"), memory(3, "项目/资料"), memory(4, " 项目 "))
        assertEquals(listOf(1L), MemoryFolderBrowsePolicy.directEntries(memories, "").map { it.id })
        assertEquals(listOf(2L, 4L), MemoryFolderBrowsePolicy.directEntries(memories, "项目").map { it.id })
        assertEquals(listOf(3L), MemoryFolderBrowsePolicy.directEntries(memories, "项目/资料").map { it.id })
    }

    @Test fun folderCountIncludesDescendantsButNotSimilarSiblings() {
        val memories = listOf(memory(1, "项目"), memory(2, "项目/资料"), memory(3, "项目备份"), memory(4, null))
        assertEquals(2, MemoryFolderBrowsePolicy.subtreeCount(memories, "项目"))
        assertEquals(1, MemoryFolderBrowsePolicy.subtreeCount(memories, "项目备份"))
        assertEquals(memories.size, MemoryFolderBrowsePolicy.subtreeCount(memories, ""))
    }
}

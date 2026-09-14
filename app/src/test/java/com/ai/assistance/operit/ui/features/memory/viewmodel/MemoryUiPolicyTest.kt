package com.ai.assistance.operit.ui.features.memory.viewmodel

import org.junit.Assert.*
import org.junit.Test

class MemoryUiPolicyTest {
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

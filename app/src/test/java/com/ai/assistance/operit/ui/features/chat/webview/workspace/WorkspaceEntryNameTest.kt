package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEntryNameTest {
    @Test fun `creation stays inside selected directory`() {
        listOf("", " ", ".", "..", "../file", "/file", "dir/file", "dir\\file", "file\nname", "file\u0000")
            .forEach { assertFalse(it, isWorkspaceEntryNameValid(it)) }
        listOf(".gitignore", "中文文档.md", "a b.txt", "file.kt")
            .forEach { assertTrue(it, isWorkspaceEntryNameValid(it)) }
    }
}

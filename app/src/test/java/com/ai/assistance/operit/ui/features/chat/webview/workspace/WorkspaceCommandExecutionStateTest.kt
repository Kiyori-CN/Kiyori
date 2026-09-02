package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceCommandExecutionStateTest {
    @Test
    fun incrementalTrailingDelimiterDoesNotCreateSyntheticBlankRow() {
        assertEquals(listOf("line"), "line\n".toWorkspaceCommandOutputEntries())
    }

    @Test
    fun blankLineBeforeDelimiterRemainsVisible() {
        assertEquals(listOf("line", ""), "line\n\n".toWorkspaceCommandOutputEntries())
    }
}

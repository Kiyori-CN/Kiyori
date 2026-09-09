package com.ai.assistance.operit.ui.features.chat.webview.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceCommandExecutionStateTest {
    @Test fun outputLimitPreservesRecentEntriesAndReportsOmission() {
        val initial = WorkspaceCommandExecutionState("/tmp", "Run", "echo", "session", usesDedicatedSession = false)
        val result = initial.appendOutput((0..2_004).map(Int::toString)).appendOutput(listOf("last"))
        assertEquals(2_000, result.outputEntries.size)
        assertEquals(6, result.omittedOutputEntries)
        assertEquals("6", result.outputEntries.first())
        assertEquals("last", result.outputEntries.last())
    }

    @Test
    fun incrementalTrailingDelimiterDoesNotCreateSyntheticBlankRow() {
        assertEquals(listOf("line"), "line\n".toWorkspaceCommandOutputEntries())
    }

    @Test
    fun blankLineBeforeDelimiterRemainsVisible() {
        assertEquals(listOf("line", ""), "line\n\n".toWorkspaceCommandOutputEntries())
    }
}

package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerLocation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPaneState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerParentPath
import org.junit.Assert.assertEquals
import org.junit.Test

class FileManagerNavigationPolicyTest {
    private val initialPath = "/storage/emulated/0"

    @Test
    fun `system back consumes pane history before leaving file manager`() {
        val state = FileManagerPaneState(
            path = "$initialPath/Download",
            environment = null,
            backStack = listOf(FileManagerLocation(initialPath, null)),
        )

        assertEquals(FileManagerBackAction.HISTORY, fileManagerBackAction(state, initialPath))
    }

    @Test
    fun `system back navigates to parent when a direct child has no history`() {
        val state = FileManagerPaneState(
            path = "$initialPath/Download",
            environment = null,
        )

        assertEquals(FileManagerBackAction.PARENT, fileManagerBackAction(state, initialPath))
        assertEquals(initialPath, fileManagerParentPath(state.path))
    }

    @Test
    fun `system back exits only at the initial storage directory`() {
        val state = FileManagerPaneState(path = initialPath, environment = null)

        assertEquals(FileManagerBackAction.EXIT, fileManagerBackAction(state, initialPath))
    }

    @Test
    fun `rooted repository pane remains inside the pane history boundary`() {
        val state = FileManagerPaneState(
            path = "/",
            environment = "repo:workspace",
            backStack = listOf(FileManagerLocation(initialPath, null)),
        )

        assertEquals(FileManagerBackAction.HISTORY, fileManagerBackAction(state, initialPath))
    }
}

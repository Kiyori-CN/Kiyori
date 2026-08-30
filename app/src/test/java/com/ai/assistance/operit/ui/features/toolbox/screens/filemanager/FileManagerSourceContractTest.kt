package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerSourceContractTest {
    private fun source(relativePath: String): String =
        File(System.getProperty("user.dir"), "src/main/$relativePath").readText()

    @Test
    fun `screen owns the exit and back contracts`() {
        val screen = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/FileManagerScreen.kt",
        )

        assertTrue(screen.contains("BackHandler"))
        assertTrue(screen.contains("viewModel.navigateBack()"))
        assertTrue(screen.contains("onExitFileManager = onBack"))
        assertTrue(screen.contains("WindowInsets.safeDrawing"))
        assertTrue(screen.contains("FileManagerDualPane"))
        assertTrue(screen.contains("FileManagerBottomBar"))
        assertTrue(screen.contains("val viewModel = remember { FileManagerViewModel(context) }"))
        assertFalse(screen.contains("BackHandler(onBack = onBack)"))
    }

    @Test
    fun `view model owns both pane states and directory loading`() {
        val viewModel = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/viewmodel/FileManagerViewModel.kt",
        )

        assertTrue(viewModel.contains("private var leftPane by mutableStateOf(FileManagerPaneState"))
        assertTrue(viewModel.contains("private var rightPane by mutableStateOf(FileManagerPaneState"))
        assertTrue(viewModel.contains("fun loadPaneDirectory("))
        assertTrue(viewModel.contains("backStack = if (recordHistory)"))
        assertTrue(viewModel.contains("forwardStack = if (recordHistory)"))
    }

    @Test
    fun `dual pane keeps equal columns and compact file rows`() {
        val dualPane = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerDualPane.kt",
        )

        assertTrue(dualPane.contains("Row("))
        assertTrue(dualPane.contains("modifier = Modifier.weight(1f)"))
        assertTrue(dualPane.contains("displayMode = DisplayMode.TWO_COLUMNS"))
        assertTrue(dualPane.contains("compact = true"))
    }
}

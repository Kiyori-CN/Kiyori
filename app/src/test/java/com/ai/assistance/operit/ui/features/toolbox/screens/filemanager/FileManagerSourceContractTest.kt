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
        assertTrue(screen.contains("onPathClick"))
        assertTrue(screen.contains("onItemSwipeRight"))
        assertTrue(screen.contains("showPathDialog"))
        assertTrue(screen.contains("viewModel.selectedFile == file"))
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
        assertTrue(dualPane.contains(".shadow("))
        assertTrue(dualPane.contains(".zIndex("))
        assertTrue(dualPane.contains("PaddingValues(0.dp)"))
        assertFalse(dualPane.contains("Modifier.border"))
    }

    @Test
    fun `file rows match mt continuous selection and metadata contract`() {
        val item = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileListItem.kt",
        )

        assertTrue(item.contains("detectHorizontalDragGestures"))
        assertTrue(item.contains("horizontalDistance >= 48.dp.toPx()"))
        assertTrue(item.contains("onSwipeRight"))
        assertTrue(item.contains("selectedFileRowColor = Color(0xFFE0E0E0)"))
        assertTrue(item.contains("unselectedFileRowColor = Color.White"))
        assertTrue(item.contains("getFileIconColor(file)"))
        assertTrue(item.contains("file.name != \"..\""))
        assertFalse(item.contains("file_list_folder"))
        assertFalse(item.contains("tonalElevation = if (isSelected)"))
    }

    @Test
    fun `bottom action mirrors active path without swapping panes`() {
        val chrome = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerChrome.kt",
        )
        val viewModel = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/viewmodel/FileManagerViewModel.kt",
        )

        assertTrue(chrome.contains("onMirrorPath"))
        assertTrue(chrome.contains("同步活动路径到另一栏"))
        assertFalse(chrome.contains("onSwap"))
        assertTrue(viewModel.contains("fun mirrorActivePaneToOther()"))
        assertTrue(viewModel.contains("navigatePaneTo(targetPane, source.path, source.environment"))
        assertTrue(viewModel.contains("if (pane == activePane) clearSelection()"))
        assertFalse(viewModel.contains("fun swapPanes()"))
    }
}

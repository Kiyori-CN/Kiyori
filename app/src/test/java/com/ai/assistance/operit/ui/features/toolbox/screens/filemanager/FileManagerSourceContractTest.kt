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
        assertTrue(screen.contains("KiyoriStatusBarAppearanceOverride(darkIcons = false)"))
        assertTrue(screen.contains("Color(0xFFFAFAFA)"))
        assertTrue(screen.contains("FileManagerDualPane"))
        assertTrue(screen.contains("FileManagerBottomBar"))
        assertTrue(screen.contains("background(androidx.compose.ui.graphics.Color(0xFFFAFAFA))"))
        assertTrue(screen.contains("onPathClick"))
        assertTrue(screen.contains("onItemSwipeRight"))
        assertTrue(screen.contains("showPathDialog"))
        assertTrue(screen.contains("viewModel.toggleSelection(file)"))
        assertTrue(screen.contains("viewModel.navigateBackDirectory()"))
        assertTrue(screen.contains("gesturesEnabled = false"))
        assertTrue(screen.contains("储存："))
        assertTrue(screen.contains("storage.totalBytes - storage.availableBytes"))
        assertFalse(screen.contains("LoadingOverlay("))
        assertFalse(screen.contains("viewModel.selectedFile ="))
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
        assertTrue(dualPane.contains("awaitFirstDown(requireUnconsumed = false)"))
        assertTrue(dualPane.contains("elevation = if (isActive) 8.dp"))
        assertTrue(dualPane.contains("PaddingValues(0.dp)"))
        assertTrue(dualPane.contains("pane == activePane && if (isMultiSelectMode)"))
        assertFalse(dualPane.contains(".clickable("))
        assertFalse(dualPane.contains("Modifier.border"))
    }

    @Test
    fun `file rows match mt continuous selection and metadata contract`() {
        val item = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileListItem.kt",
        )

        assertTrue(item.contains("detectHorizontalDragGestures"))
        assertTrue(item.contains("val selectionThreshold = if (isCompactTwoColumn)"))
        assertTrue(item.contains("val maxOffset = if (isCompactTwoColumn)"))
        assertTrue(item.contains("(baseIconSize * itemSize).toPx()"))
        assertTrue(item.contains("if (dragOffset >= selectionThreshold) onSwipeRight()"))
        assertTrue(item.contains("graphicsLayer { translationX = dragOffset }"))
        assertTrue(item.contains("onSwipeRight"))
        assertTrue(item.contains("selectedFileRowColor = Color(0xFF7DBEDC)"))
        assertTrue(item.contains("unselectedFileRowColor = Color(0xFFFAFAFA)"))
        assertTrue(item.contains("baseHeight = if (isCompactTwoColumn) 40.dp"))
        assertTrue(item.contains("baseIconSize = if (isCompactTwoColumn) 28.dp"))
        assertTrue(item.contains("titleLineHeight = if (isCompactTwoColumn) 17.sp"))
        assertTrue(item.contains("metadataLineHeight = if (isCompactTwoColumn) 12.sp"))
        assertTrue(item.contains("metadataTextSize = if (isCompactTwoColumn) 10.sp"))
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
        assertTrue(chrome.contains("fileManagerChromeColor = Color(0xFF303030)"))
        assertTrue(chrome.contains("fileManagerContentColor = Color(0xFFFAFAFA)"))
        assertTrue(chrome.contains("padding(horizontal = 0.dp, vertical = 2.dp)"))
        assertTrue(chrome.contains("activeArrowColor = Color(0xFF2B2B2B)"))
        assertTrue(chrome.contains("inactiveArrowColor = Color(0xFF9E9E9E)"))
        assertTrue(chrome.contains("size(16.dp).offset(x = (-10).dp"))
        assertTrue(chrome.contains("size(16.dp).offset(x = 10.dp"))
        assertFalse(chrome.contains("onSwap"))
        assertTrue(viewModel.contains("fun mirrorActivePaneToOther()"))
        assertTrue(viewModel.contains("navigatePaneTo(targetPane, source.path, source.environment"))
        assertTrue(viewModel.contains("private var leftSelectedFiles by mutableStateOf<List<FileItem>>(emptyList())"))
        assertTrue(viewModel.contains("private var rightSelectedFiles by mutableStateOf<List<FileItem>>(emptyList())"))
        assertTrue(viewModel.contains("paneFiles.indexOfFirst { candidate -> candidate.name == anchorName }"))
        assertTrue(viewModel.contains("paneFiles.subList(rangeStart, rangeEnd + 1)"))
        assertTrue(viewModel.contains("current + range.filterNot"))
        assertTrue(viewModel.contains("clearSelection()\n            return true"))
        assertTrue(viewModel.contains("fun navigateBackDirectory(): Boolean"))
        assertFalse(viewModel.contains("fun swapPanes()"))
    }
}

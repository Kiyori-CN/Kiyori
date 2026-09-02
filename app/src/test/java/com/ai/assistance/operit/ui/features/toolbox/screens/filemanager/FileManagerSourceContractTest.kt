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
        assertTrue(dualPane.contains("postClickFeedbackDurationMillis = 80L"))
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
        assertTrue(item.contains("if (abs(dragOffset) >= selectionThreshold) onSwipeRight()"))
        assertTrue(item.contains("((basePadding + baseIconSize) * itemSize).toPx()"))
        assertTrue(item.contains("coerceIn(-maxOffset, maxOffset)"))
        assertTrue(item.contains("graphicsLayer { translationX = dragOffset }"))
        assertTrue(item.contains("onSwipeRight"))
        assertTrue(item.contains("selectedFileRowColor = Color(0xFF7DBEDC)"))
        assertTrue(item.contains("unselectedFileRowColor = Color(0xFFFAFAFA)"))
        assertTrue(item.contains("pressedFileRowColor = Color(0xFFE0E0E0)"))
        assertTrue(item.contains("collectIsPressedAsState()"))
        assertTrue(item.contains("isPressed || clickFeedback -> pressedFileRowColor"))
        assertTrue(item.contains("postClickFeedbackDurationMillis: Long = 0L"))
        assertTrue(item.contains("delay(postClickFeedbackDurationMillis)"))
        assertTrue(item.contains("clickFeedback = true"))
        assertTrue(item.contains("shadowElevation = if (isPressed || clickFeedback) 2.dp else 0.dp"))
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
        assertTrue(chrome.contains("activeArrowColor = Color(0xFFBEBEBE)"))
        assertTrue(chrome.contains("inactiveArrowColor = Color(0xFF646464)"))
        assertTrue(chrome.contains("size(16.dp).offset(x = (-9).dp, y = 4.dp)"))
        assertTrue(chrome.contains("size(16.dp).offset(x = 9.dp, y = (-4).dp)"))
        assertTrue(chrome.contains("KeyboardArrowLeft"))
        assertTrue(chrome.contains("KeyboardArrowRight"))
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

    @Test
    fun `top bar keeps path row and centered statistics row independent`() {
        val chrome = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileManagerChrome.kt",
        )

        assertTrue(chrome.contains("modifier = Modifier.fillMaxWidth().height(40.dp).offset(y = 2.dp)"))
        assertTrue(chrome.contains(".fillMaxWidth()\n                    .height(16.dp)\n                    .offset(y = (-8).dp)"))
        assertTrue(chrome.contains("horizontalArrangement = Arrangement.Center"))
        assertTrue(chrome.contains("verticalAlignment = Alignment.CenterVertically"))
        assertTrue(chrome.contains("fontSize = 10.sp, lineHeight = 12.sp"))
        assertTrue(chrome.contains("textAlign = TextAlign.Center"))
        assertTrue(chrome.contains("softWrap = false"))
        assertTrue(chrome.contains("append(\"文件夹：${'$'}folderCount  文件：${'$'}fileCount  ${'$'}storageLabel\")"))
    }

    @Test
    fun `new entry dialog separates file and folder creation`() {
        val screen = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/FileManagerScreen.kt",
        )
        val dialog = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/NewFolderDialog.kt",
        )
        val viewModel = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/viewmodel/FileManagerViewModel.kt",
        )

        assertTrue(screen.contains("FileManagerNewEntryDialog("))
        assertTrue(screen.contains("onCreateFile ="))
        assertTrue(screen.contains("onCreateFolder ="))
        assertTrue(dialog.contains("text = \"新建\""))
        assertTrue(dialog.contains("Text(\"文件\""))
        assertTrue(dialog.contains("Text(\"文件夹\""))
        assertTrue(dialog.contains("Modifier.width(310.dp).height(151.dp)"))
        assertTrue(dialog.contains("BasicTextField("))
        assertTrue(dialog.contains("height(32.dp)"))
        assertTrue(dialog.contains("background(Color(0xFF42A5F5))"))
        assertTrue(dialog.contains("padding(start = 24.dp, top = 21.dp, end = 24.dp)"))
        assertTrue(dialog.contains("height(48.dp).offset(x = (-12.5).dp)"))
        assertTrue(dialog.contains("height(48.dp).offset(x = 33.dp)"))
        assertTrue(dialog.contains("height(48.dp).offset(x = 8.dp)"))
        assertTrue(dialog.contains("fontSize = 14.sp"))
        assertTrue(viewModel.contains("name = \"create_file\""))
        assertTrue(viewModel.contains("ToolParameter(\"new\", \"\")"))
        assertTrue(viewModel.contains("fun createNewFile(fileName: String)"))
    }

    @Test
    fun `long press menu is centered and projects folder disabled actions`() {
        val menu = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/components/FileContextMenu.kt",
        )
        val screen = source(
            "java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/FileManagerScreen.kt",
        )

        assertTrue(menu.contains("DialogProperties(usePlatformDefaultWidth = false)"))
        assertTrue(menu.contains("FLAG_DIM_BEHIND"))
        assertTrue(menu.contains("setDimAmount(0f)"))
        assertTrue(menu.contains("setGravity(Gravity.CENTER)"))
        assertTrue(menu.contains("(-18.5).dp.roundToPx()"))
        assertTrue(menu.contains("shadowElevation = 8.dp"))
        assertTrue(menu.contains("color = Color(0xFFFAFAFA)"))
        assertTrue(menu.contains("modifier = Modifier.padding(start = 10.dp)"))
        assertTrue(menu.contains("TextStyle(fontSize = 12.sp, lineHeight = 16.sp)"))
        assertFalse(menu.contains("KiyoriModalBottomDrawer"))
        assertTrue(menu.contains("val isFolder = contextMenuFile.isDirectory"))
        assertTrue(menu.contains("enabled = !isFolder"))
        assertTrue(menu.contains("val moveEnabled = !isFolder || sourcePath != targetPath || sourceEnvironment != targetEnvironment"))
        assertTrue(menu.contains("Modifier.width(320.dp).height(269.dp)"))
        assertTrue(menu.contains("height(28.dp)"))
        assertTrue(menu.contains("height(48.dp)"))
        assertTrue(menu.contains("Modifier.size(24.dp)"))
        assertTrue(menu.contains("val copyMoveArrow = if (sourcePane == FileManagerPane.LEFT) \"->\" else \"<-\""))
        assertTrue(menu.contains("val copyMoveArrowBeforeLabel = sourcePane == FileManagerPane.RIGHT"))
        assertTrue(menu.contains("arrowBeforeLabel = copyMoveArrowBeforeLabel"))
        assertTrue(menu.contains("Icons.Default.FileDownload"))
        assertTrue(menu.contains("Icons.Default.CollectionsBookmark"))
        assertTrue(menu.contains("sourcePane: FileManagerPane"))
        assertTrue(screen.contains("sourcePane = viewModel.contextMenuPane"))
        assertTrue(screen.contains("viewModel.contextMenuPane = pane"))
    }
}

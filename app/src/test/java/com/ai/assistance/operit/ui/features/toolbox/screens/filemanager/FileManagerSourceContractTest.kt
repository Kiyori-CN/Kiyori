package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** 架构接线合同；异步正确性由 DirectoryLifecycle/WorkLifecycle 行为测试验证。 */
class FileManagerSourceContractTest {
    private fun source(name: String): String = File(System.getProperty("user.dir"),
        "src/main/java/com/ai/assistance/operit/ui/features/toolbox/screens/filemanager/$name").readText()

    @Test fun `screen retains shell back and a single lifecycle owned view model`() {
        val screen = source("FileManagerScreen.kt")
        assertTrue(screen.contains("viewModel.navigateBack()"))
        assertTrue(screen.contains("onExitFileManager = exitFileManager"))
        assertTrue(screen.contains("val store = remember { ViewModelStore() }"))
        assertTrue(screen.contains("FileManagerViewModel(applicationContext, settingsStore ="))
        assertTrue(screen.contains("onDispose { store.clear() }"))
        assertTrue(screen.contains("gesturesEnabled = drawerState.isOpen"))
        assertTrue(screen.contains("Modifier.width(storageDrawerWidth)"))
        assertTrue(screen.contains("BackHandler(enabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open)"))
        assertTrue(screen.contains("viewModel.clickEntry(file)"))
        val swipe = screen.substringAfter("onItemSwipeRight = { pane, file ->").substringBefore("},")
        assertTrue(swipe.contains("viewModel.selectFile(file)"))
        assertFalse(swipe.contains("toggleSelection"))
        assertTrue(screen.contains("leftSelectionMode = viewModel.selectionModeForPane(FileManagerPane.LEFT)"))
        assertTrue(screen.contains("rightSelectionMode = viewModel.selectionModeForPane(FileManagerPane.RIGHT)"))
        assertTrue(source("components/FileManagerDualPane.kt").contains("selectionMode = selectionMode"))
        assertFalse(screen.contains("BackHandler(onBack = onBack)"))
        assertTrue(screen.contains("onDispose"))
        assertTrue(screen.contains("KiyoriSettingsTheme { FileManagerContent(onBack, onOpenSettings, modifier, sessionViewModel, onOpenAiDialogue, onOpenBrowser) }"))
        assertTrue(screen.contains("viewModel.clearActiveSelection()"))
        assertTrue(screen.contains("viewModel.addSingleSelection(it)"))
    }

    @Test fun `scroll effects restore loaded locations before collecting positions`() {
        val screen = source("FileManagerScreen.kt")
        assertTrue(screen.contains("FileManagerLocation(state.path, state.environment)"))
        assertTrue(screen.contains("state.isLoading, state.error"))
        assertTrue(screen.contains("if (state.isLoading || state.error != null) return@LaunchedEffect"))
        assertTrue(screen.contains("listState.layoutInfo.totalItemsCount }.first { it == latestState.files.size }"))
        assertTrue(screen.contains("listState.scrollToItem(position.index.coerceAtMost"))
        assertTrue(screen.contains("viewModel.saveScrollPosition(pane, location, current, state.scrollKey, state.presentationVersion)"))
    }

    @Test fun `storage statistics are read on IO only while started`() {
        val screen = source("FileManagerScreen.kt")
        assertTrue(screen.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(screen.contains("withContext(Dispatchers.IO) { readStorageLabel(path) }"))
        assertFalse(screen.contains("val storageLabel = readStorageLabel()"))
    }

    @Test fun `original surfaces use theme and accessible content instead of screenshot window hacks`() {
        listOf("components/FileManagerChrome.kt", "components/NewFolderDialog.kt", "components/FileContextMenu.kt").forEach {
            val code = source(it)
            assertFalse(it, code.contains("Color(0x"))
            assertFalse(it, code.contains("FLAG_DIM_BEHIND"))
            assertFalse(it, code.contains("setGravity("))
            assertFalse(it, code.contains("onClick = {}"))
        }
        assertTrue(source("FileManagerScreen.kt").contains("MaterialTheme.colorScheme.surface.luminance()"))
        assertTrue(source("components/FileListItem.kt").contains("selected = isSelected"))
        assertTrue(source("components/FileListItem.kt").contains(".heightIn(min = (baseHeight"))
        assertTrue(source("components/FileManagerDualPane.kt").contains("onRetry(pane)"))
        assertTrue(source("components/FileManagerVisuals.kt").contains("tone.resolveSettingsIconColors()"))
        assertFalse(source("utils/FileUtils.kt").contains("Color(0x"))
        assertTrue(source("components/FileListItem.kt").contains("rememberUpdatedState(onSwipeRight)"))
        assertTrue(source("components/FileManagerBrowseDrawers.kt").contains("KiyoriModalBottomDrawer(onDismissRequest"))
        assertTrue(source("components/FileContextMenu.kt").contains("actions.chunked(5)"))
        assertFalse(source("components/FileManagerDualPane.kt").contains("BorderStroke"))
    }

    @Test fun `whole row movement and equal navigation geometry retain their owners`() {
        val item = source("components/FileListItem.kt")
        assertTrue(item.contains("Box(Modifier.fillMaxWidth().clipToBounds())"))
        assertTrue(item.contains(".graphicsLayer { translationX = rowOffset }"))
        assertFalse(item.contains("dragDistance * 0.15f"))
        assertTrue(item.contains("onDragCancel = { dragDistance = 0f; dragging = false }"))
        val screen = source("FileManagerScreen.kt")
        assertFalse(screen.contains("FileManagerLocationBar"))
        assertFalse(screen.contains("FileManagerCopyBar"))
        assertFalse(screen.contains("layoutMode"))
        assertTrue(screen.contains("onRefresh = { pane -> viewModel.refreshPane(pane) }"))
        assertFalse(source("components/FileManagerChrome.kt").contains("showOptions"))
        assertTrue(item.contains("maxLines = 4"))
        assertTrue(item.contains("coerceIn(-maxDrag.toPx(), maxDrag.toPx())"))
        assertTrue(source("components/FileManagerDualPane.kt").contains(".drawWithContent"))
        assertFalse(source("components/FileManagerVisuals.kt").contains("Icons.Rounded.Check else icon"))
        val bottom = source("components/FileManagerChrome.kt").substringAfter("fun FileManagerBottomBar(").substringBefore("data class FileManagerStorageEntry")
        assertEquals(3, "BrowserBottomBarAction(".toRegex(RegexOption.LITERAL).findAll(bottom).count())
        assertEquals(2, "BrowserBottomBarSlot(".toRegex(RegexOption.LITERAL).findAll(bottom).count())
        assertFalse(bottom.contains("Text("))
        assertFalse(bottom.contains("FilledTonalButton"))
        assertFalse(bottom.contains("horizontalScroll"))
        assertTrue(source("components/SearchDialogs.kt").contains("KiyoriModalBottomDrawer"))
    }

    @Test fun `new entry and search UI delegate to their state owners`() {
        val screen = source("FileManagerScreen.kt")
        assertTrue(screen.contains("onNew = viewModel::beginCreateEntry"))
        assertTrue(screen.contains("isCreating = viewModel.isCreating"))
        assertTrue(screen.contains("error = viewModel.creationError"))
        assertTrue(screen.contains("onDismiss = viewModel::cancelSearch"))
        assertTrue(screen.contains("error = viewModel.searchError"))
        assertTrue(source("components/NewFolderDialog.kt").contains("fileManagerNameError(entryName)"))
        assertTrue(source("components/NewFolderDialog.kt").contains("enabled = !isCreating && !unknown && validation == null"))
    }
}

package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.viewmodel

import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.FileSearchData
import com.ai.assistance.operit.core.tools.FileSearchNameMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerLocation
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPane
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerPaneState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerSortMode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerScrollPosition
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.TabItem
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerBackAction
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerParentPath
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerTransferState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerTransferItemResult
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerTransferOutcome
import java.io.File
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.defaultTool.standard.FileCopyErrorCode
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerCopyRequest
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerCopyConflict
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerRenameState
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.FileManagerRenameRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerNameError
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.fileManagerJoinPath

import com.ai.assistance.operit.data.preferences.FileManagerPreferences
import com.ai.assistance.operit.data.preferences.FileManagerSettings
import com.ai.assistance.operit.data.preferences.FileManagerHiddenEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class FileManagerViewModel(
    private val context: Context,
    private val initialStoragePath: String = Environment.getExternalStorageDirectory().absolutePath,
    private val directoryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val settingsStore: FileManagerPreferences? = null,
    private val historyStore: FileManagerHistoryStore? = null,
    private val directorySizeClock: () -> Long = System::nanoTime,
    private val executeDirectoryTool: suspend (AITool) -> ToolResult = { tool ->
        AIToolHandler.getInstance(context).executeTool(tool)
    },
) : ViewModel() {

    private fun initialPane(left: Boolean) = FileManagerPaneState(
        (if (left) settingsStore?.current?.leftStartPath else settingsStore?.current?.rightStartPath)
            ?.takeIf { it.isNotBlank() } ?: initialStoragePath, null,
        sortMode = settingsStore?.current?.sortMode ?: FileManagerSortMode.NAME,
        sortDescending = settingsStore?.current?.sortDescending ?: false)
    private var leftPane by mutableStateOf(initialPane(true))
    private var rightPane by mutableStateOf(initialPane(false))

    var activePane by mutableStateOf(FileManagerPane.LEFT)
        private set

    val leftPaneState: FileManagerPaneState
        get() = leftPane
    val rightPaneState: FileManagerPaneState
        get() = rightPane
    val initialPath: String
        get() = initialStoragePath

    val currentPath: String
        get() = paneState(activePane).path
    val currentEnvironment: String?
        get() = paneState(activePane).environment
    val isRecycleBin: Boolean get() = currentEnvironment == "recycle"
    val canCreateHere: Boolean get() = !isWriting && !isLoading && error == null && !isRecycleBin
    val canPasteHere: Boolean get() = canCreateHere && fileManagerIsLocal(currentEnvironment) &&
        fileManagerIsLocal(clipboardSourceEnvironment) && clipboardFiles.isNotEmpty()

    fun openRecycleBin() = navigateToPath("/回收站", "recycle")

    /** 存储入口撤销后同时清理两栏历史与剪贴板，避免另一栏继续使用已经释放的授权。 */
    fun detachStorageEnvironment(environment: String?) {
        if (environment == null || isWriting) return
        FileManagerPane.entries.forEach { pane ->
            if (paneState(pane).environment == environment) navigatePaneTo(pane, initialStoragePath, null, recordHistory = false)
            updatePane(pane) { it.copy(backStack = it.backStack.filterNot { location -> location.environment == environment },
                forwardStack = it.forwardStack.filterNot { location -> location.environment == environment }) }
        }
        if (clipboardSourceEnvironment == environment) clearClipboard()
        tabs.removeAll { it.environment == environment }
        if (tabs.isEmpty()) tabs.add(TabItem(initialStoragePath, context.getString(R.string.file_manager_home), null))
        activeTabIndex = activeTabIndex.coerceIn(tabs.indices)
        contextMenuFile = null; showBottomActionMenu = false
    }

    /** 各存储卷及旧记录共用一个列表投影，记录身份包含根位置，避免跨卷 UUID 冲突。 */
    private fun readRecycleEntries(): List<FileItem> =
        com.ai.assistance.operit.core.tools.defaultTool.standard.fileRecycleRoots(context).flatMap { root ->
            try {
            com.ai.assistance.operit.core.tools.defaultTool.standard.LocalFileRecycleBin.list(root).map { record ->
                val payload = root.resolve(record.id).resolve("payload")
                try {
                    check(record.problem == null) { record.problem.orEmpty() }
                    val attributes = java.nio.file.Files.readAttributes(payload, java.nio.file.attribute.BasicFileAttributes::class.java, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    FileItem(payload.toString(), attributes.isDirectory, attributes.size(), record.deletedAt,
                        fullPath = payload.toString(), displayName = File(record.originalPath).name, recycledOriginalPath = record.originalPath)
                } catch (failure: Exception) {
                    FileItem(payload.toString(), false, fullPath = root.resolve(record.id).toString(),
                        displayName = "需检查的回收项目", recycledOriginalPath = record.originalPath,
                        recycleProblem = failure.message ?: "无法读取回收内容，请检查记录目录")
                }
            }
            } catch (failure: Exception) {
                listOf(FileItem(root.toString(), false, fullPath = root.toString(), displayName = "回收位置无法读取",
                    recycledOriginalPath = root.toString(), recycleProblem = "${failure.message ?: "存储不可用"}；请检查权限或存储连接后刷新。"))
            }
        }

    fun contextItems(): List<FileItem> {
        val file = contextMenuFile ?: return emptyList()
        val pane = paneState(contextMenuPane)
        if (pane.isLoading || pane.error != null) return emptyList()
        val selected = selectedFilesFor(contextMenuPane)
        val targets = if (selected.any { it.name == file.name }) selected else listOf(file)
        return targets.mapNotNull { target -> pane.files.firstOrNull { it.name == target.name && it.name != ".." } }
    }
    val files: List<FileItem>
        get() = paneState(activePane).files
    val isLoading: Boolean
        get() = paneState(activePane).isLoading
    val error: String?
        get() = paneState(activePane).error

    // 选择状态按窗格隔离；否则两栏中同名 FileItem 会因 data class 相等而同时高亮。
    private var leftSelectedFiles by mutableStateOf<List<FileItem>>(emptyList())
    private var rightSelectedFiles by mutableStateOf<List<FileItem>>(emptyList())
    private var leftSelectedFile by mutableStateOf<FileItem?>(null)
    private var rightSelectedFile by mutableStateOf<FileItem?>(null)
    val isMultiSelectMode: Boolean
        get() = selectionModeForPane(activePane)
    // 每个区间由两次滑动确定；完成后清除锚点，下次滑动开始新区间。
    // 记录名称而不是列表索引，刷新或排序后仍能定位同一项，不会产生越界范围。
    private var leftSelectionAnchorName by mutableStateOf<String?>(null)
    private var rightSelectionAnchorName by mutableStateOf<String?>(null)

    val selectedFiles: List<FileItem>
        get() = selectedFilesFor(activePane)
    val selectedFile: FileItem?
        get() = selectedFileFor(activePane)

    fun selectionForPane(pane: FileManagerPane): List<FileItem> = selectedFilesFor(pane)

    fun selectionModeForPane(pane: FileManagerPane): Boolean =
        selectedFilesFor(pane).isNotEmpty()

    var transferState by mutableStateOf(FileManagerTransferState())
        private set
    var pendingCopy by mutableStateOf<FileManagerCopyRequest?>(null)
        private set
    var copyConflict by mutableStateOf<FileManagerCopyConflict?>(null)
        private set
    var showTransferDetails by mutableStateOf(false)
    private var conflictDecision: CompletableDeferred<String?>? = null
    private var closed = false
    var renameState by mutableStateOf(FileManagerRenameState())
        private set
    val isWriting: Boolean get() = transferState.running || renameState.running || isCreating || textDocument?.saving == true || actionState?.running == true

    var actionState by mutableStateOf<FileManagerActionState?>(null)
        private set
    var transferDraft by mutableStateOf<FileManagerTransferDraft?>(null)
        private set
    var pendingShare by mutableStateOf<FileManagerShareRequest?>(null)
        private set
    private var actionGeneration = 0L
    private var actionReadJob: Job? = null

    fun finishShare(error: String? = null) { pendingShare = null; openError = error }

    fun beginContextTransfer(move: Boolean) {
        val file = contextMenuFile ?: return
        activatePane(contextMenuPane)
        // 长按已选项目时，复制/移动整个当前栏选择；长按未选项目只操作该项目。
        beginTransfer(contextItems(), move)
    }

    fun beginSelectionTransfer(move: Boolean) = beginTransfer(selectedFiles.toList(), move)

    private fun beginTransfer(items: List<FileItem>, move: Boolean) {
        if (isWriting || isLoading || error != null || !fileManagerIsLocal(currentEnvironment)) return
        val current = items.mapNotNull { item -> files.firstOrNull { it.name == item.name && it.name != ".." } }
        if (current.isEmpty()) return
        val other = paneState(if (activePane == FileManagerPane.LEFT) FileManagerPane.RIGHT else FileManagerPane.LEFT)
        transferDraft = FileManagerTransferDraft(current, FileManagerLocation(currentPath, currentEnvironment),
            FileManagerLocation(other.path, other.environment), move, activePane)
        showBottomActionMenu = false
    }

    fun dismissTransferDraft() { transferDraft = null }
    fun browseTransferDestination() {
        val draft = transferDraft ?: return
        clipboardVersion++
        clipboardFiles.clear(); clipboardFiles.addAll(draft.files)
        clipboardSourcePath = draft.source.path; clipboardSourceEnvironment = draft.source.environment; isCutOperation = draft.move
        transferDraft = null
        val sourcePane = paneState(draft.sourcePane)
        if (sourcePane.path == draft.source.path && sourcePane.environment == draft.source.environment)
            clearPaneSelection(draft.sourcePane)
    }
    fun useOtherTransferDestination() {
        val draft = transferDraft ?: return
        transferDraft = null
        pendingCopy = FileManagerCopyRequest(draft.files, draft.source, draft.other, draft.move)
    }

    fun beginContextAction(kind: FileManagerActionKind, shareAfter: Boolean = false) {
        if (isWriting) return
        val targets = contextItems()
        val file = targets.firstOrNull() ?: return
        val pane = paneState(contextMenuPane)
        if (pane.isLoading || pane.error != null || pane.files.none { it.name == file.name }) return
        if (pane.environment == "recycle" && kind !in setOf(FileManagerActionKind.RESTORE, FileManagerActionKind.PURGE, FileManagerActionKind.PROPERTIES)) return
        if (pane.environment != "recycle" && (!fileManagerIsLocal(pane.environment) || kind in setOf(FileManagerActionKind.RESTORE, FileManagerActionKind.PURGE))) return
        actionReadJob?.cancel()
        actionState = FileManagerActionState(++actionGeneration, kind, file, FileManagerLocation(pane.path, pane.environment),
            outputName = when (kind) {
                FileManagerActionKind.RENAME -> "{name} ({n}){ext}"
                FileManagerActionKind.EXTRACT -> file.displayName.substringBeforeLast('.', file.displayName) + "_extracted"
                else -> file.displayName + ".zip"
            }, shareAfter = shareAfter,
            files = targets)
        showBottomActionMenu = false
        readActionInspection(false)
    }

    fun readActionInspection(hash: Boolean) {
        val state = actionState ?: return
        if (state.running || state.unknown) return
        actionReadJob?.cancel()
        if (state.results.isNotEmpty()) return
        actionState = state.copy(loading = true, inspection = null, inspections = emptyMap(), error = null)
        actionReadJob = viewModelScope.launch {
            try {
                val inspections = withContext(directoryDispatcher) { state.files.associate { file ->
                    check(file.recycleProblem == null) { file.recycleProblem.orEmpty() }
                    val result = executeDirectoryTool(AITool("file_info", withEnvParams(listOf(
                        ToolParameter("path", actionPath(state, file)),
                        ToolParameter("info_mode", if (hash && !file.isDirectory) "manager_sha256" else "manager"),
                    ), if (state.location.environment == "recycle") "android" else state.location.environment)))
                    val inspection = result.result as? com.ai.assistance.operit.core.tools.FileInspectionData
                    check(result.success && inspection != null) { "${file.displayName}：${result.error ?: "读取属性失败"}" }
                    file.name to inspection
                } }
                if (closed || actionState?.id != state.id) return@launch
                actionState = actionState?.copy(loading = false, inspection = inspections.values.first(), inspections = inspections)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (!closed && actionState?.id == state.id) actionState = actionState?.copy(loading = false, error = error.message ?: "读取失败")
            }
        }
    }

    fun updateActionName(name: String) {
        if (actionState?.running == false && actionState?.unknown == false) actionState = actionState?.copy(outputName = name, error = null)
    }
    private fun actionPath(state: FileManagerActionState, file: FileItem): String =
        if (state.location.environment == "recycle") requireNotNull(file.fullPath) else fileManagerJoinPath(state.location.path, file.name)
    fun dismissAction() {
        if (actionState?.running == true) return
        actionReadJob?.cancel(); actionState = null; actionGeneration++
    }

    fun stopActionAfterCurrent() {
        actionState?.takeIf { it.running }?.let { actionState = it.copy(stopRequested = true) }
    }

    fun openActionRecycleBin() {
        val state = actionState ?: return
        if (state.running || state.kind != FileManagerActionKind.DELETE || state.results.isEmpty()) return
        dismissAction()
        openRecycleBin()
    }
    fun shareContextItem() {
        val file = contextMenuFile ?: return
        val pane = paneState(contextMenuPane)
        if (!pane.environment.isNullOrBlank() && pane.environment != "android") { openError = "此位置暂不支持系统分享"; return }
        if (file.isDirectory || contextItems().size > 1) { beginContextAction(FileManagerActionKind.ZIP, shareAfter = true); return }
        pendingShare = FileManagerShareRequest(fileManagerJoinPath(pane.path, file.name), file.name)
        showBottomActionMenu = false
    }
    fun confirmContextAction() {
        val state = actionState ?: return
        if (state.inspection == null || state.files.any { it.name !in state.inspections }) return
        if (state.loading || state.completed || state.unknown || state.error != null || state.results.isNotEmpty() || isWriting) return
        if (state.kind !in setOf(FileManagerActionKind.DELETE, FileManagerActionKind.RESTORE, FileManagerActionKind.PURGE, FileManagerActionKind.RENAME, FileManagerActionKind.ZIP, FileManagerActionKind.EXTRACT)) return
        if (state.kind == FileManagerActionKind.RENAME) {
            fileManagerBatchRenameError(state.files, state.outputName)?.let { actionState = state.copy(error = it); return }
        }
        if (state.kind == FileManagerActionKind.ZIP || state.kind == FileManagerActionKind.EXTRACT) {
            val error = fileManagerNameError(state.outputName)
            if (error != null) { actionState = state.copy(error = error); return }
        }
        val destination = fileManagerJoinPath(state.location.path, state.outputName)
        val batch = if (state.kind == FileManagerActionKind.ZIP) listOf(state.file) else state.files
        actionState = state.copy(running = true, error = null)
        viewModelScope.launch {
            withContext(NonCancellable) {
                val taskRecord = beginTaskRecord(when (state.kind) {
                    FileManagerActionKind.DELETE -> "删除"; FileManagerActionKind.RESTORE -> "恢复"
                    FileManagerActionKind.PURGE -> "永久删除"; FileManagerActionKind.RENAME -> "重命名"
                    FileManagerActionKind.ZIP -> "压缩"; FileManagerActionKind.EXTRACT -> "解压"
                    else -> state.kind.name
                }, state.location, if (state.kind in setOf(FileManagerActionKind.ZIP, FileManagerActionKind.EXTRACT)) state.location else null, state.files.size)
                val results = mutableListOf<FileManagerTransferItemResult>()
                try {
                    for ((index, file) in batch.withIndex()) {
                        if (closed || actionState?.stopRequested == true) break
                        actionState = actionState?.copy(currentName = file.displayName)
                        val source = actionPath(state, file)
                        val target = if (state.kind == FileManagerActionKind.RESTORE) file.recycledOriginalPath.orEmpty()
                            else if (state.kind == FileManagerActionKind.RENAME) fileManagerJoinPath(state.location.path, fileManagerBatchRenameName(file, index, state.outputName))
                            else if (state.kind == FileManagerActionKind.DELETE || state.kind == FileManagerActionKind.PURGE) ""
                            else if (state.kind == FileManagerActionKind.EXTRACT && batch.size > 1)
                                fileManagerJoinPath(state.location.path, file.name + "_extracted") else destination
                        val parameters = when (state.kind) {
                            FileManagerActionKind.DELETE, FileManagerActionKind.PURGE -> listOf(
                                ToolParameter("path", source), ToolParameter("recursive", file.isDirectory.toString()),
                                ToolParameter("delete_mode", if (state.kind == FileManagerActionKind.PURGE) "purge_recycled" else "recycle"),
                                ToolParameter("fingerprint", requireNotNull(state.inspections[file.name]).fingerprint),
                            )
                            FileManagerActionKind.RESTORE -> listOf(ToolParameter("source", source), ToolParameter("destination", target), ToolParameter("move_mode", "restore_recycled"),
                                ToolParameter("fingerprint", requireNotNull(state.inspections[file.name]).fingerprint))
                            FileManagerActionKind.RENAME -> listOf(ToolParameter("source", source), ToolParameter("destination", target), ToolParameter("move_mode", "rename_no_replace"))
                            else -> listOf(ToolParameter("source", source), ToolParameter("destination", target),
                                ToolParameter("sources", Json.encodeToString(state.files.map { actionPath(state, it) })),
                                ToolParameter("zip_mode", if (state.kind == FileManagerActionKind.EXTRACT) "extract_no_replace" else "no_replace"))
                        }
                        val toolName = when (state.kind) {
                            FileManagerActionKind.DELETE, FileManagerActionKind.PURGE -> "delete_file"
                            FileManagerActionKind.RESTORE, FileManagerActionKind.RENAME -> "move_file"
                            else -> "zip_files"
                        }
                        val outcome = try {
                            val result = withContext(directoryDispatcher) { executeDirectoryTool(AITool(toolName, withEnvParams(parameters,
                                if (state.location.environment == "recycle") "android" else state.location.environment))) }
                            val operation = result.result as? FileOperationData
                            val status = when {
                                operation == null || operation.stagingPath != null || result.success != operation.successful -> FileManagerTransferOutcome.UNKNOWN
                                result.success && operation.successful -> FileManagerTransferOutcome.COMPLETED
                                else -> FileManagerTransferOutcome.FAILED
                            }
                            FileManagerTransferItemResult(file.displayName, status, result.error ?: operation?.details,
                                target.takeIf { it.isNotEmpty() }, operation?.stagingPath)
                        } catch (failure: Exception) {
                            FileManagerTransferItemResult(file.displayName, FileManagerTransferOutcome.UNKNOWN, "未取得可靠结果，请检查文件；不会自动重试", target)
                        }
                        results += outcome
                        if (!closed && actionState?.id == state.id) actionState = actionState?.copy(results = results.toList())
                        // 未知副作用立即停止批次；失败项不自动重试，已完成项不重复提交。
                        if (outcome.outcome == FileManagerTransferOutcome.UNKNOWN) break
                    }
                } finally {
                    val submitted = results.size
                    results += batch.drop(submitted).map { FileManagerTransferItemResult(it.displayName, FileManagerTransferOutcome.NOT_STARTED,
                        if (results.any { result -> result.outcome == FileManagerTransferOutcome.UNKNOWN }) "前项结果未确认，未提交" else "已停止，未提交") }
                    finishTaskRecord(taskRecord, results)
                    if (!closed && actionState?.id == state.id) {
                        val completed = results.all { it.outcome == FileManagerTransferOutcome.COMPLETED }
                        actionState = state.copy(running = false, completed = completed, results = results.toList(),
                            stopRequested = actionState?.stopRequested == true,
                            unknown = results.any { it.outcome == FileManagerTransferOutcome.UNKNOWN },
                            error = if (completed) null else results.firstOrNull { it.outcome == FileManagerTransferOutcome.FAILED || it.outcome == FileManagerTransferOutcome.UNKNOWN }?.let {
                                "${it.name}：${it.message ?: "结果未确认，请检查来源与回收站"}"
                            })
                        if (completed && state.shareAfter) pendingShare = FileManagerShareRequest(destination, state.outputName)
                        refreshLocation(state.location)
                        if (state.kind == FileManagerActionKind.DELETE) refreshLocation(FileManagerLocation("/回收站", "recycle"))
                        if (state.kind == FileManagerActionKind.RESTORE) state.files.mapNotNull { it.recycledOriginalPath?.substringBeforeLast('/') }.distinct().forEach { parent ->
                            refreshLocation(FileManagerLocation(parent, null))
                        }
                    }
                }
            }
        }
    }

    var pendingOpen by mutableStateOf<FileManagerOpenRequest?>(null)
        private set
    var openError by mutableStateOf<String?>(null)
        private set
    var textDocument by mutableStateOf<FileManagerTextState?>(null)
        private set
    private var contentGeneration = 0L
    private var contentJob: Job? = null

    fun dismissOpenError() { openError = null }

    fun finishOpen(id: Long, error: String? = null) {
        if (pendingOpen?.id != id) return
        pendingOpen = null
        openError = error
    }

    /** 行点击按当前窗格模式分流；菜单中的显式打开仍直接使用 openEntry。 */
    fun clickEntry(file: FileItem) {
        if (file.name != ".." && isMultiSelectMode) toggleSelection(file) else openEntry(file)
    }

    fun openEntry(file: FileItem, preferredKind: FileManagerOpenKind? = null) {
        if (isRecycleBin) {
            contextMenuPane = activePane; contextMenuFile = file
            beginContextAction(FileManagerActionKind.RESTORE)
            return
        }
        if (isLoading || error != null || textDocument != null || pendingOpen != null) return
        val current = files.firstOrNull { it.name == file.name } ?: return
        if (current.isDirectory) { navigateToDirectory(current); return }
        val path = fileManagerJoinPath(currentPath, current.name)
        val environment = currentEnvironment
        if (!environment.isNullOrBlank() && !environment.equals("android", true)) {
            openError = if (environment.startsWith("network:")) "网络存储当前支持目录浏览，远程内容打开尚未接入。" else "此位置暂未接入内容打开，请先复制到手机存储。"
            return
        }
        val kind = preferredKind ?: fileManagerOpenKind(current)
        val id = ++contentGeneration
        openError = null
        if (kind != FileManagerOpenKind.TEXT) {
            pendingOpen = FileManagerOpenRequest(id, path, current.name, kind)
            return
        }
        textDocument = FileManagerTextState(id, path, environment, current.name)
        contentJob = viewModelScope.launch {
            try {
                val result = withContext(directoryDispatcher) {
                    executeDirectoryTool(AITool("read_file_full", listOf(
                        ToolParameter("path", path), ToolParameter("read_mode", "bounded_utf8"),
                        ToolParameter("environment", environment ?: "android"),
                    )))
                }
                if (textDocument?.id != id || closed) return@launch
                val data = result.result as? com.ai.assistance.operit.core.tools.FileContentData
                check(result.success && data != null) { result.error ?: "未能读取文本" }
                textDocument = textDocument?.copy(content = data.content, savedContent = data.content, loading = false, readable = true)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                if (textDocument?.id == id && !closed) textDocument = textDocument?.copy(loading = false, error = failure.message ?: "读取失败")
            }
        }
    }

    fun updateTextDocument(content: String) {
        val document = textDocument ?: return
        if (!document.readable || document.loading || document.saving) return
        textDocument = document.copy(content = content)
    }

    fun closeTextDocument() {
        if (textDocument?.saving == true) return
        contentJob?.cancel()
        textDocument = null
        contentGeneration++
    }

    fun saveTextCopy(name: String) {
        val document = textDocument ?: return
        if (!document.readable || document.loading || isWriting || document.unknown) return
        val nameError = fileManagerNameError(name)
        if (nameError != null) { textDocument = document.copy(error = nameError); return }
        val parent = fileManagerParentPath(document.path) ?: "/"
        val destination = fileManagerJoinPath(parent, name)
        if (destination == document.path) {
            textDocument = document.copy(error = "另存副本需要使用其他名称，原文件保持不变")
            return
        }
        textDocument = document.copy(saving = true, error = null)
        viewModelScope.launch {
            // 写入提交后即使界面被清理也保留工具终态；未知结果不能自动重放。
            withContext(NonCancellable) {
                try {
                    val result = withContext(directoryDispatcher) {
                        executeDirectoryTool(AITool("create_file", listOf(
                            ToolParameter("path", destination), ToolParameter("new", document.content),
                            ToolParameter("create_mode", "no_replace_text"),
                            ToolParameter("environment", document.environment ?: "android"),
                        )))
                    }
                    if (closed || textDocument?.id != document.id) return@withContext
                    val operation = result.result as? FileOperationData
                    val uncertain = operation == null || operation.stagingPath != null
                    textDocument = if (result.success && operation?.successful == true) document.copy(
                        saving = false, savedContent = document.content, savedPath = destination,
                    ) else document.copy(saving = false, error = result.error ?: "保存失败", unknown = uncertain)
                    if (result.success) {
                        refreshLocation(FileManagerLocation(parent, document.environment))
                    }
                } catch (failure: Exception) {
                    if (!closed && textDocument?.id == document.id) textDocument = document.copy(
                        saving = false, unknown = true, error = "保存结果未知，请检查目标目录；不会自动再次保存",)
                }
            }
        }
    }


    // 剪贴板状态
    var clipboardFiles = mutableStateListOf<FileItem>()
    var isCutOperation by mutableStateOf(false)
    var clipboardSourcePath by mutableStateOf<String?>(null)
    var clipboardSourceEnvironment by mutableStateOf<String?>(null)

    // 显示状态
    var itemSize by mutableStateOf(settingsStore?.current?.itemSize ?: 1f)
        private set
    val minItemSize = 0.5f
    val maxItemSize = 1.3f
    val itemSizeStep = 0.1f
    val sortDescending: Boolean get() = paneState(activePane).sortDescending
    val filterQuery: String get() = paneState(activePane).filterQuery
    val canNavigateUp: Boolean get() = canNavigateUp(activePane)
    var showHiddenFiles by mutableStateOf(settingsStore?.current?.showHiddenFiles ?: true)
    var showManuallyHiddenFiles by mutableStateOf(settingsStore?.current?.showManuallyHiddenFiles ?: false)
        private set
    var manuallyHiddenFiles by mutableStateOf(settingsStore?.current?.manuallyHiddenFiles ?: emptySet())
        private set
    val sortMode: FileManagerSortMode get() = paneState(activePane).sortMode
    val activePaneState: FileManagerPaneState get() = paneState(activePane)

    // 相同路径可能来自手机、Ubuntu 或不同 SAF 书签，位置必须包含环境和窗格。
    private val scrollPositions = mutableMapOf<Triple<FileManagerPane, FileManagerLocation, String>, FileManagerScrollPosition>()
    private val directoryJobs = mutableMapOf<FileManagerPane, Job>()
    private val directoryRequestVersions = mutableMapOf<FileManagerPane, Long>()
    private data class DirectorySizeSnapshot(val recordedAt: Long, val bytes: Long?, val needsValidation: Boolean = false)
    private val directorySizes = linkedMapOf<FileManagerLocation, DirectorySizeSnapshot>()
    private val directorySizeMutex = Mutex()
    private var directorySizeInvalidationVersion = 0L

    /** 滚动只复用结果；前台目录刷新才定期核验内容，父目录时间不能代表深层子文件大小变化。 */
    suspend fun loadVisibleDirectorySizes(pane: FileManagerPane, names: List<String>, revalidate: Boolean = false) {
        val initial = paneState(pane)
        if (closed || initial.isLoading || initial.refreshing || isWriting) return
        val version = directoryRequestVersions[pane]
        for (file in initial.files.filter { it.isDirectory && it.name != ".." && it.name in names }) {
            val location = FileManagerLocation(file.fullPath ?: "${initial.path.trimEnd('/')}/${file.name}", initial.environment)
            val snapshot = directorySizeMutex.withLock {
                if (closed || isWriting || directoryRequestVersions[pane] != version) return@withLock null
                val cached = directorySizes[location]
                if (cached != null && !cached.needsValidation &&
                    (!revalidate || directorySizeClock() - cached.recordedAt < 30_000_000_000L)) cached
                else {
                    val invalidationVersion = directorySizeInvalidationVersion
                    val bytes = if (!fileManagerIsLocal(initial.environment) && initial.environment != "recycle") null else try {
                        // 比自动目录刷新周期短，避免每次自动刷新都在超时前取消同一大目录的统计。
                        val result = withTimeoutOrNull(2_000L) { withContext(directoryDispatcher) {
                            executeDirectoryTool(AITool("file_info", withEnvParams(listOf(ToolParameter("path", location.path),
                                ToolParameter("info_mode", "manager")), if (initial.environment == "recycle") "android" else initial.environment)))
                        } }
                        (result?.result as? com.ai.assistance.operit.core.tools.FileInspectionData)
                            ?.takeIf { result.success && it.directory && it.path == location.path && it.bytes >= 0 }?.bytes
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { AppLogger.e("FileManagerViewModel", "目录大小统计失败", failure); null }
                    // 手动刷新或本地写入可以在扫描挂起期间使结果失效，旧扫描不得重新填回缓存。
                    if (closed || directorySizeInvalidationVersion != invalidationVersion) return@withLock null
                    DirectorySizeSnapshot(directorySizeClock(), bytes).also {
                        directorySizes[location] = it
                        while (directorySizes.size > 256) directorySizes.remove(directorySizes.keys.first())
                    }
                }
            } ?: return
            val current = paneState(pane)
            if (closed || directoryRequestVersions[pane] != version || current.path != initial.path || current.environment != initial.environment) return
            updatePane(pane) { state -> state.copy(entries = state.entries.map {
                if (it.name == file.name) it.copy(directoryContentSize = snapshot.bytes, directorySizeUnavailable = snapshot.bytes == null) else it
            }) }
            projectPane(pane)
        }
    }

    private fun withDirectorySizes(entries: List<FileItem>, path: String, environment: String?): List<FileItem> =
        entries.map { file ->
            if (!file.isDirectory) file else {
                val location = FileManagerLocation(file.fullPath ?: "${path.trimEnd('/')}/${file.name}", environment)
                directorySizes[location]?.let { snapshot ->
                    file.copy(directoryContentSize = snapshot.bytes, directorySizeUnavailable = snapshot.bytes == null)
                } ?: file
            }
        }

    private fun invalidateDirectorySizes(location: FileManagerLocation) {
        directorySizeInvalidationVersion++
        directorySizes.replaceAll { key, snapshot ->
            val sameEnvironment = key.environment == location.environment ||
                fileManagerIsLocal(key.environment) && fileManagerIsLocal(location.environment)
            val path = location.path.trimEnd('/')
            val cachedPath = key.path.trimEnd('/')
            if (sameEnvironment && (cachedPath == path || cachedPath.startsWith("$path/") || path.startsWith("$cachedPath/")))
                snapshot.copy(needsValidation = true) else snapshot
        }
    }

    // 标签页状态保留既有外部行为，当前活动标签跟随活动窗格路径。
    var tabs = mutableStateListOf(TabItem(initialStoragePath, context.getString(R.string.file_manager_home), null))
    var activeTabIndex by mutableStateOf(0)

    // 上下文菜单状态
    var showBottomActionMenu by mutableStateOf(false)
    var contextMenuFile by mutableStateOf<FileItem?>(null)

    /** 底栏只打开动作菜单，不改变两栏选择，也不沿用过期的长按对象。 */
    fun openActionMenu() {
        contextMenuPane = activePane
        contextMenuFile = if (isLoading || error != null) null else selectedFiles.firstOrNull()?.let { selected ->
            files.firstOrNull { it.name == selected.name && it.name != ".." }
        }
        showBottomActionMenu = true
    }
    var contextMenuPane by mutableStateOf(FileManagerPane.LEFT)

    // 对话框状态
    var showNewEntryDialog by mutableStateOf(false)
    var newEntryName by mutableStateOf("")
    var isCreating by mutableStateOf(false)
        private set
    var creationUnknown by mutableStateOf(false)
        private set
    var creationError by mutableStateOf<String?>(null)
    private var creationLocation: FileManagerLocation? = null
    var showCompressDialog by mutableStateOf(false)
    var compressFileName by mutableStateOf("")

    // 搜索状态
    var history by mutableStateOf(FileManagerHistory())
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    private var searchDialogLocation: FileManagerLocation? = null
    private var searchDialogPane = FileManagerPane.LEFT
    val searchDialogLocationLabel: String get() = fileManagerLocationLabel(searchDialogLocation ?: FileManagerLocation(currentPath, currentEnvironment))
    val searchDialogPaneLabel: String get() = if (searchDialogPane == FileManagerPane.LEFT) "左列" else "右列"

    private suspend fun saveSearchRecord(record: FileManagerSearchRecord) {
        try { historyStore?.putSearch(record) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            historyError = "搜索历史保存失败，请检查应用存储空间"
            AppLogger.e("FileManagerHistory", "Search history write failed: ${error.javaClass.simpleName}")
        }
    }
    private suspend fun beginTaskRecord(title: String, source: FileManagerLocation, destination: FileManagerLocation?, total: Int): FileManagerTaskRecord {
        val record = FileManagerTaskRecord(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), title, source, destination, total)
        saveTaskRecord(record)
        return record
    }
    private suspend fun saveTaskRecord(record: FileManagerTaskRecord) {
        try { historyStore?.putTask(record) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            historyError = "任务历史保存失败，请检查应用存储空间；文件操作结果请查看当前任务"
            AppLogger.e("FileManagerHistory", "Task history write failed: ${error.javaClass.simpleName}")
        }
    }
    private suspend fun finishTaskRecord(record: FileManagerTaskRecord, results: List<FileManagerTransferItemResult>) =
        saveTaskRecord(record.copy(status = fileManagerTaskStatus(results), results = results, finishedAt = System.currentTimeMillis()))

    fun removeSearchRecord(id: String? = null) = changeHistory { removeSearch(id) }
    fun removeTaskRecord(id: String? = null) = changeHistory { removeTask(id) }
    private fun changeHistory(action: suspend FileManagerHistoryStore.() -> Unit) {
        viewModelScope.launch {
            try { historyStore?.action(); historyError = null }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                historyError = "历史记录更新失败，请检查应用存储空间"
                AppLogger.e("FileManagerHistory", "History update failed: ${error.javaClass.simpleName}")
            }
        }
    }
    fun openSearchRecord(record: FileManagerSearchRecord) {
        cancelSearch()
        showingSavedSearch = true
        searchOriginPane = activePane
        searchLocation = record.location
        searchDialogLocation = record.location
        searchDialogPane = activePane
        searchForm = record.form
        searchQuery = record.query; searchDialogQuery = record.query
        searchResults.addAll(record.results)
        searchSummary = "保存的结果 · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.time))} · ${record.status} · ${fileManagerLocationLabel(record.location)}"
        searchLimitations = record.limitations
        searchError = record.error
        showSearchResultsDialog = true
    }
    fun repeatSavedSearch() { searchFiles(searchDialogQuery, searchDialogLocation, searchDialogPane) }
    fun submitSearchDialog() { searchFiles(searchDialogQuery, searchDialogLocation, searchDialogPane) }
    private var searchJob: Job? = null
    private var searchVersion = 0L
    private var searchLocation: FileManagerLocation? = null
    private var searchOriginPane = FileManagerPane.LEFT
    private var showingSavedSearch = false
    var searchError by mutableStateOf<String?>(null)
        private set
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults = mutableStateListOf<FileItem>()
    var showSearchDialog by mutableStateOf(false)
    var searchDialogQuery by mutableStateOf("")
    var showSearchResultsDialog by mutableStateOf(false)
    var searchForm by mutableStateOf(FileManagerSearchForm())
    var searchSummary by mutableStateOf("")
        private set
    var searchLimitations by mutableStateOf<List<String>>(emptyList())
        private set
    var isCaseSensitive: Boolean
        get() = searchForm.caseSensitive
        set(value) { searchForm = searchForm.copy(caseSensitive = value) }
    var useWildcard: Boolean
        get() = searchForm.nameMode == FileSearchNameMode.GLOB
        set(value) { searchForm = searchForm.copy(nameMode = if (value) FileSearchNameMode.GLOB else FileSearchNameMode.CONTAINS) }

    fun beginSearchDialog() {
        searchDialogLocation = FileManagerLocation(currentPath, currentEnvironment)
        searchDialogPane = activePane
        searchDialogQuery = searchQuery
        showSearchDialog = true
    }
    fun editSearch() { showSearchResultsDialog = false; showSearchDialog = true }


    private fun paneState(pane: FileManagerPane): FileManagerPaneState =
        if (pane == FileManagerPane.LEFT) leftPane else rightPane

    private fun selectedFilesFor(pane: FileManagerPane): List<FileItem> =
        if (pane == FileManagerPane.LEFT) leftSelectedFiles else rightSelectedFiles

    private fun setSelectedFiles(pane: FileManagerPane, files: List<FileItem>) {
        if (pane == FileManagerPane.LEFT) {
            leftSelectedFiles = files
        } else {
            rightSelectedFiles = files
        }
    }

    private fun selectedFileFor(pane: FileManagerPane): FileItem? =
        if (pane == FileManagerPane.LEFT) leftSelectedFile else rightSelectedFile

    private fun setSelectedFile(pane: FileManagerPane, file: FileItem?) {
        if (pane == FileManagerPane.LEFT) {
            leftSelectedFile = file
        } else {
            rightSelectedFile = file
        }
    }

    private fun selectionAnchorFor(pane: FileManagerPane): String? =
        if (pane == FileManagerPane.LEFT) leftSelectionAnchorName else rightSelectionAnchorName

    private fun setSelectionAnchor(pane: FileManagerPane, name: String?) {
        if (pane == FileManagerPane.LEFT) {
            leftSelectionAnchorName = name
        } else {
            rightSelectionAnchorName = name
        }
    }

    private fun updatePane(
        pane: FileManagerPane,
        transform: (FileManagerPaneState) -> FileManagerPaneState,
    ) {
        if (pane == FileManagerPane.LEFT) {
            leftPane = transform(leftPane)
        } else {
            rightPane = transform(rightPane)
        }
    }

    private fun withEnvParams(
        base: List<ToolParameter>,
        environment: String? = currentEnvironment,
    ): List<ToolParameter> {
        if (environment.isNullOrBlank()) return base
        return base + ToolParameter("environment", environment)
    }

    fun saveScrollPosition(
        pane: FileManagerPane,
        location: FileManagerLocation,
        position: FileManagerScrollPosition,
        filter: String = paneState(pane).scrollKey,
        presentationVersion: Long = paneState(pane).presentationVersion,
    ) {
        val current = paneState(pane)
        if (current.path != location.path || current.environment != location.environment ||
            current.isLoading || current.error != null || current.scrollKey != filter || current.presentationVersion != presentationVersion
        ) return
        scrollPositions[Triple(pane, location, filter)] = position
    }

    fun scrollPosition(pane: FileManagerPane, location: FileManagerLocation, filter: String = paneState(pane).scrollKey): FileManagerScrollPosition =
        scrollPositions[Triple(pane, location, filter)] ?: FileManagerScrollPosition()

    fun paneCanGoBack(pane: FileManagerPane = activePane): Boolean =
        paneState(pane).backStack.isNotEmpty()

    fun paneCanGoForward(pane: FileManagerPane = activePane): Boolean =
        paneState(pane).forwardStack.isNotEmpty()

    fun activatePane(pane: FileManagerPane) {
        if (activePane != pane) {
            activePane = pane
            // 切换焦点只改变投影，两栏分别保留选择和滑动锚点。
        }
    }

    fun clearActiveSelection() {
        clearPaneSelection(activePane)
    }

    /** 长按菜单的单项选择不继承滑动范围锚点，也不取消已经选择的项目。 */
    fun addSingleSelection(file: FileItem) {
        if (isLoading || error != null || file.name == "..") return
        val currentItem = files.firstOrNull { it.name == file.name } ?: return
        setSelectionAnchor(activePane, null)
        val selected = selectedFilesFor(activePane)
        if (selected.none { it.name == currentItem.name }) setSelectedFiles(activePane, selected + currentItem)
        setSelectedFile(activePane, null)
    }

    fun clearSelection() {
        clearPaneSelection(FileManagerPane.LEFT)
        clearPaneSelection(FileManagerPane.RIGHT)
    }

    private fun clearPaneSelection(pane: FileManagerPane) {
        setSelectedFile(pane, null)
        setSelectedFiles(pane, emptyList())
        setSelectionAnchor(pane, null)
    }

    fun toggleSelection(file: FileItem) {
        if (isLoading || error != null || file.name == ".." || files.none { it.name == file.name }) return
        val pane = activePane
        val currentItem = files.first { it.name == file.name }
        val current = selectedFilesFor(pane)
        val updated = if (current.any { selected -> selected.name == file.name }) {
            current.filterNot { selected -> selected.name == file.name }
        } else {
            current + currentItem
        }
        setSelectedFiles(pane, updated)
        setSelectedFile(pane, null)
        // 最后一项被点击取消时结束本次选择，下一次滑动不能继承旧区间起点。
        if (updated.isEmpty()) setSelectionAnchor(pane, null)
    }

    /** 首次滑动建立起点；滑动另一项完成区间，之后重新等待下一对起止点。 */
    fun selectFile(file: FileItem) {
        if (isLoading || error != null || file.name == "..") return
        val pane = activePane
        val paneFiles = paneState(pane).files
        val targetIndex = paneFiles.indexOfFirst { candidate -> candidate.name == file.name }
        if (targetIndex < 0) return
        val anchorIndex = selectionAnchorFor(pane)?.let { anchorName ->
            paneFiles.indexOfFirst { candidate -> candidate.name == anchorName }
        }
        if (anchorIndex == null || anchorIndex < 0) {
            setSelectionAnchor(pane, file.name)
            val current = selectedFilesFor(pane)
            if (current.none { selected -> selected.name == file.name }) {
                setSelectedFiles(pane, current + paneFiles[targetIndex])
            }
        } else {
            val rangeStart = minOf(anchorIndex, targetIndex)
            val rangeEnd = maxOf(anchorIndex, targetIndex)
            val range = paneFiles.subList(rangeStart, rangeEnd + 1)
                .filter { candidate -> candidate.name != ".." }
            val current = selectedFilesFor(pane)
            setSelectedFiles(
                pane,
                current + range.filterNot { candidate -> current.any { selected -> selected.name == candidate.name } },
            )
            // 重复滑动起点只保持选中；到达另一项才完成区间并释放起点。
            if (targetIndex != anchorIndex) setSelectionAnchor(pane, null)
        }
        setSelectedFile(pane, null)
    }

    fun invertSelection() {
        if (isLoading || error != null) return
        val names = selectedFiles.map { it.name }.toSet()
        setSelectedFiles(activePane, files.filter { it.name != ".." && it.name !in names })
        setSelectedFile(activePane, null)
        setSelectionAnchor(activePane, null)
    }

    fun selectAll() {
        if (isLoading || error != null) return
        val pane = activePane
        setSelectionAnchor(pane, null)
        setSelectedFiles(pane, paneState(pane).files.filter { file -> file.name != ".." })
        setSelectedFile(pane, null)
    }

    fun canNavigateUp(pane: FileManagerPane): Boolean =
        paneState(pane).let { fileManagerCanNavigateUp(FileManagerLocation(it.path, it.environment), initialStoragePath) }

    private fun reconcileSelection(pane: FileManagerPane) {
        val available = paneState(pane).files.filter { it.name != ".." }.associateBy { it.name }
        // 不保留旧 FileItem 元数据，也不能让已隐藏/删除的项目参与下一次批量操作。
        setSelectedFiles(pane, selectedFilesFor(pane).mapNotNull { available[it.name] })
        setSelectedFile(pane, selectedFileFor(pane)?.let { available[it.name] })
        if (selectionAnchorFor(pane) !in available) setSelectionAnchor(pane, null)
    }

    private fun projectPane(pane: FileManagerPane) {
        updatePane(pane) { state -> state.copy(files = fileManagerVisibleEntries(
            manuallyVisibleEntries(state.entries, state.path, state.environment), "", showHiddenFiles, canNavigateUp(pane), state.filter,
        )) }
        reconcileSelection(pane)
    }

    fun setDirectoryFilter(query: String) {
        updatePane(activePane) { it.copy(filter = FileManagerFilter(name = query),
            filterDraft = FileManagerFilterDraft(name = query), presentationVersion = it.presentationVersion + 1) }
        projectPane(activePane)
    }

    fun applyDirectoryFilter(pane: FileManagerPane, location: FileManagerLocation, draft: FileManagerFilterDraft) {
        val state = paneState(pane)
        if (state.path != location.path || state.environment != location.environment) return
        val filter = draft.compile()
        updatePane(pane) { it.copy(filter = filter, filterDraft = draft, presentationVersion = it.presentationVersion + 1) }
        projectPane(pane)
    }

    private fun changeSettings(transform: (FileManagerSettings) -> FileManagerSettings) {
        val updated = if (settingsStore != null) {
            settingsStore.update(transform)
            settingsStore.current
        } else transform(FileManagerSettings(showHiddenFiles, sortMode, sortDescending, itemSize, showManuallyHiddenFiles, manuallyHiddenFiles))
        applySettings(updated)
    }

    private fun applySettings(settings: FileManagerSettings) {
        val hiddenChanged = showHiddenFiles != settings.showHiddenFiles ||
            showManuallyHiddenFiles != settings.showManuallyHiddenFiles || manuallyHiddenFiles != settings.manuallyHiddenFiles
        showHiddenFiles = settings.showHiddenFiles
        showManuallyHiddenFiles = settings.showManuallyHiddenFiles
        manuallyHiddenFiles = settings.manuallyHiddenFiles
        itemSize = settings.itemSize
        // 设置中的排序仅是新会话默认值，当前两栏保持各自已确认的顺序。
        if (hiddenChanged) FileManagerPane.entries.forEach(::projectPane)
    }

    fun toggleHiddenFiles() = changeSettings { it.copy(showHiddenFiles = !it.showHiddenFiles) }

    fun setShowSystemHidden(show: Boolean) = changeSettings { it.copy(showHiddenFiles = show) }
    fun setShowManuallyHidden(show: Boolean) = changeSettings { it.copy(showManuallyHiddenFiles = show) }

    fun hideSelectedFiles() {
        val state = paneState(activePane)
        if (isWriting || state.environment == "recycle") return
        val additions = selectedFilesFor(activePane).filter { it.name != ".." }.map {
            FileManagerHiddenEntry(it.fullPath ?: "${state.path.trimEnd('/')}/${it.name}", state.environment)
        }
        changeSettings { it.copy(manuallyHiddenFiles = it.manuallyHiddenFiles + additions, showManuallyHiddenFiles = false) }
    }

    fun editHiddenEntry(original: FileManagerHiddenEntry, path: String) {
        require(path.startsWith('/') && path.split('/').none { it == "." || it == ".." || it.contains('\u0000') }) {
            "请输入具体项目的绝对路径，不使用 . 或 .."
        }
        val normalized = "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/")
        require(normalized.length > 1) { "请选择具体文件或文件夹，不能隐藏整个根目录" }
        if (original !in manuallyHiddenFiles) return
        changeSettings { it.copy(manuallyHiddenFiles = it.manuallyHiddenFiles - original + original.copy(path = normalized)) }
    }

    fun removeHiddenEntry(entry: FileManagerHiddenEntry) = changeSettings { it.copy(manuallyHiddenFiles = it.manuallyHiddenFiles - entry) }

    private fun manuallyVisibleEntries(entries: List<FileItem>, path: String, environment: String?): List<FileItem> =
        if (showManuallyHiddenFiles || environment == "recycle" || manuallyHiddenFiles.isEmpty()) entries
        else entries.filter { file -> manuallyHiddenFiles.none { it.contains(file.fullPath ?: "${path.trimEnd('/')}/${file.name}", environment) } }

    fun cycleSortMode() = selectSortMode(FileManagerSortMode.entries[(sortMode.ordinal + 1) % FileManagerSortMode.entries.size])

    fun selectSortMode(mode: FileManagerSortMode) {
        if (sortMode == mode) return
        applySort(activePane, mode, mode != FileManagerSortMode.NAME)
    }

    fun toggleSortDirection() {
        applySort(activePane, sortMode, !sortDescending)
    }

    fun applySort(pane: FileManagerPane, mode: FileManagerSortMode, descending: Boolean) {
        updatePane(pane) { it.copy(sortMode = mode, sortDescending = descending) }
        // 取消旧排序请求并使用同一目录代际，避免在途读取以旧顺序回写。
        loadPaneDirectory(pane, background = true)
    }

    fun refreshPane(pane: FileManagerPane = activePane) {
        val state = paneState(pane)
        if (closed || state.isLoading || state.refreshing) return
        invalidateDirectorySizes(FileManagerLocation(state.path, state.environment))
        loadPaneDirectory(pane, background = true, manualRefresh = true)
    }

    /** 对调完整会话投影；旧请求仍属于物理栏，必须先作废，迟到结果不能串栏。 */
    fun swapPanes() {
        if (isWriting) return
        val loading = FileManagerPane.entries.associateWith { paneState(it).isLoading || directoryJobs[it]?.isActive == true }
        FileManagerPane.entries.forEach { pane ->
            directoryRequestVersions[pane] = (directoryRequestVersions[pane] ?: 0) + 1
            directoryJobs.remove(pane)?.cancel()
        }
        val oldLeft = leftPane
        leftPane = rightPane.copy(refreshing = false, presentationVersion = oldLeft.presentationVersion + 1)
        rightPane = oldLeft.copy(refreshing = false, presentationVersion = rightPane.presentationVersion + 1)
        val selected = leftSelectedFiles; leftSelectedFiles = rightSelectedFiles; rightSelectedFiles = selected
        val single = leftSelectedFile; leftSelectedFile = rightSelectedFile; rightSelectedFile = single
        val anchor = leftSelectionAnchorName; leftSelectionAnchorName = rightSelectionAnchorName; rightSelectionAnchorName = anchor
        val positions = scrollPositions.toMap(); scrollPositions.clear()
        positions.forEach { (key, value) -> scrollPositions[Triple(otherPane(key.first), key.second, key.third)] = value }
        activePane = otherPane(activePane)
        contextMenuPane = otherPane(contextMenuPane)
        searchOriginPane = otherPane(searchOriginPane)
        searchDialogPane = otherPane(searchDialogPane)
        networkRefreshTimes.clear()
        FileManagerPane.entries.forEach { pane -> if (loading[otherPane(pane)] == true) loadPaneDirectory(pane, background = !paneState(pane).isLoading) }
        syncActiveTab(activePane)
    }

    private fun otherPane(pane: FileManagerPane) = if (pane == FileManagerPane.LEFT) FileManagerPane.RIGHT else FileManagerPane.LEFT

    /** 将活动窗格的位置复制给另一栏；焦点和两栏既有内容不交换。 */
    fun mirrorActivePaneToOther() {
        val sourcePane = activePane
        val targetPane = if (sourcePane == FileManagerPane.LEFT) FileManagerPane.RIGHT else FileManagerPane.LEFT
        val source = paneState(sourcePane)
        navigatePaneTo(targetPane, source.path, source.environment, recordHistory = true)
    }

    // 取消负责及时停止工作，代际校验负责拒绝同路径刷新或 A-B-A 导航中晚到的结果。
    /** 前台自动刷新复用原请求代际；不取消正在加载的目录、不在写入中重复扫描。 */
    private val networkRefreshTimes = mutableMapOf<FileManagerPane, Long>()
    fun refreshVisibleDirectories() {
        if (closed || isWriting || showBottomActionMenu) return
        FileManagerPane.entries.forEach { pane ->
            if (paneState(pane).environment?.startsWith("network:") == true) {
                val now = System.nanoTime()
                if (now - (networkRefreshTimes[pane] ?: 0L) < 30_000_000_000L) return@forEach
                networkRefreshTimes[pane] = now
            }
            if (directoryJobs[pane]?.isActive != true) loadPaneDirectory(pane, background = true)
        }
    }

    fun loadPaneDirectory(
        pane: FileManagerPane,
        path: String = paneState(pane).path,
        environment: String? = paneState(pane).environment,
        postLoadError: String? = null,
        background: Boolean = false,
        manualRefresh: Boolean = false,
    ) {
        directoryJobs.remove(pane)?.cancel()
        val requestVersion = (directoryRequestVersions[pane] ?: 0L) + 1L
        directoryRequestVersions[pane] = requestVersion
        val requestedSortMode = paneState(pane).sortMode
        val requestedDescending = paneState(pane).sortDescending
        updatePane(pane) { it.copy(refreshing = manualRefresh) }
        if (!background) updatePane(pane) { state -> state.copy(isLoading = true, error = null) }
        directoryJobs[pane] = viewModelScope.launch {
            withContext(directoryDispatcher) {
                try {
                    val listFilesTool =
                        AITool(
                            name = "list_files",
                            parameters = withEnvParams(
                                listOf(ToolParameter("path", path)),
                                environment,
                            ),
                        )
                    AppLogger.d("ToolboxFileManager", "Loading directory pane=$pane request=$requestVersion")
                    val result = if (environment == "recycle") null else executeDirectoryTool(listFilesTool)
                    AppLogger.d(
                        "ToolboxFileManager",
                        "Directory result pane=$pane request=$requestVersion success=${result?.success}",
                    )
                    // 文件 stat、条目转换及排序全部留在后台，Main 只接收完成后的列表快照。
                    val visibleFiles =
                        if (environment == "recycle") {
                            readRecycleEntries().sortedWith(fileManagerComparator(requestedSortMode, requestedDescending))
                        } else if (result?.success == true) {
                            val directoryListing = result.result as DirectoryListingData
                            val fileList = directoryListing.entries.map { entry ->
                                ensureActive()
                                val localTimestamp = if (environment == null) {
                                    File(path, entry.name).lastModified()
                                } else {
                                    0L
                                }
                                val rawTimestamp = entry.lastModified.toLongOrNull()?.let { raw ->
                                    if (raw in 1L..10_000_000_000L) raw * 1000L else raw
                                } ?: 0L
                                FileItem(
                                    name = entry.name,
                                    isDirectory = entry.isDirectory,
                                    size = entry.size,
                                    lastModified = localTimestamp.takeIf { it > 0L } ?: rawTimestamp,
                                    lastModifiedLabel = entry.lastModified,
                                )
                            }
                            fileList.sortedWith(fileManagerComparator(requestedSortMode, requestedDescending))
                        } else {
                            null
                        }
                    ensureActive()
                    withContext(Dispatchers.Main) publish@{
                        val state = paneState(pane)
                        if (directoryRequestVersions[pane] != requestVersion ||
                            state.path != path || state.environment != environment
                        ) return@publish
                        if (visibleFiles != null) {
                            // 新目录快照必须带回已知大小，否则每次自动刷新都会短暂显示“大小 —”。
                            val sizedFiles = withDirectorySizes(visibleFiles, path, environment)
                            updatePane(pane) { current ->
                                current.copy(
                                    entries = sizedFiles,
                                    directoryRevision = current.directoryRevision + 1,
                                    files = fileManagerVisibleEntries(manuallyVisibleEntries(sizedFiles, path, environment), "", showHiddenFiles, canNavigateUp(pane), current.filter),
                                    isLoading = false,
                                    refreshing = false,
                                    error = postLoadError,
                                )
                            }
                            reconcileSelection(pane)
                        } else {
                            clearPaneSelection(pane)
                            updatePane(pane) { current ->
                                current.copy(
                                    isLoading = false,
                                    refreshing = false,
                                    error = result?.error ?: context.getString(R.string.file_manager_operation_failed),
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Error loading directory", e)
                    withContext(Dispatchers.Main) {
                        if (directoryRequestVersions[pane] == requestVersion && paneState(pane).path == path && paneState(pane).environment == environment) {
                            clearPaneSelection(pane)
                        }
                        updatePane(pane) { current ->
                            if (directoryRequestVersions[pane] != requestVersion ||
                                current.path != path || current.environment != environment
                            ) {
                                current
                            } else {
                                current.copy(
                                    isLoading = false,
                                    refreshing = false,
                                    error = "Error: ${e.message}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadCurrentDirectory(
        path: String = currentPath,
        environment: String? = currentEnvironment,
    ) {
        loadPaneDirectory(activePane, path, environment)
    }

    private fun navigatePaneTo(
        pane: FileManagerPane,
        path: String,
        environment: String?,
        recordHistory: Boolean,
    ) {
        val current = paneState(pane)
        if (current.path == path && current.environment == environment) {
            loadPaneDirectory(pane, path, environment)
            return
        }
        clearPaneSelection(pane)
        val location = FileManagerLocation(current.path, current.environment)
        updatePane(pane) {
            it.copy(
                path = path,
                environment = environment,
                files = emptyList(),
                entries = emptyList(),
                filter = FileManagerFilter(), filterDraft = FileManagerFilterDraft(),
                backStack = if (recordHistory) it.backStack + location else it.backStack,
                forwardStack = if (recordHistory) emptyList() else it.forwardStack,
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, path, environment)
    }

    fun navigateToDirectory(dir: FileItem) {
        if (!dir.isDirectory) return
        if (dir.name == "..") {
            navigateUp()
            return
        }
        navigatePaneTo(
            pane = activePane,
            path = fileManagerJoinPath(currentPath, dir.name),
            environment = currentEnvironment,
            recordHistory = true,
        )
    }

    // 活动窗格到达手机存储初始目录后，向上按钮保持在边界，不越出文件管理器的初始状态。
    fun navigateUp(): Boolean {
        if (isRecycleBin) return navigateBackDirectory()
        val state = paneState(activePane)
        val parentPath = fileManagerParentPath(state.path) ?: return false
        if (state.path == initialStoragePath && state.environment == null) return false
        navigatePaneTo(activePane, parentPath, state.environment, recordHistory = true)
        return true
    }

    fun navigateBack(): Boolean {
        if (leftSelectedFiles.isNotEmpty() || rightSelectedFiles.isNotEmpty() ||
            leftSelectedFile != null || rightSelectedFile != null
        ) {
            // 有选择时首次 Back 只清空选择，不改变目录或筛选。
            clearSelection()
            return true
        }
        if (activePaneState.hasFilter) {
            setDirectoryFilter("")
            return true
        }
        if (isSearching || showSearchResultsDialog) {
            cancelSearch()
            return true
        }
        return navigateBackDirectory()
    }

    /** 底栏方向键只负责目录历史，不改变系统 Back 的选择取消语义。 */
    fun navigateBackDirectory(): Boolean {
        val pane = activePane
        val state = paneState(pane)
        when (fileManagerBackAction(state, initialStoragePath)) {
            FileManagerBackAction.EXIT -> return false
            FileManagerBackAction.INITIAL_STORAGE -> {
                navigatePaneTo(pane, initialStoragePath, null, recordHistory = false)
                return true
            }
            else -> Unit
        }
        val previous = state.backStack.lastOrNull()
        if (previous == null) {
            return navigateUp()
        }
        val current = FileManagerLocation(state.path, state.environment)
        clearPaneSelection(pane)
        updatePane(pane) {
            it.copy(
                path = previous.path,
                environment = previous.environment,
                files = emptyList(),
                entries = emptyList(),
                filter = FileManagerFilter(), filterDraft = FileManagerFilterDraft(),
                backStack = it.backStack.dropLast(1),
                forwardStack = it.forwardStack + current,
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, previous.path, previous.environment)
        return true
    }

    fun navigateForward(): Boolean {
        val pane = activePane
        val state = paneState(pane)
        val next = state.forwardStack.lastOrNull() ?: return false
        val current = FileManagerLocation(state.path, state.environment)
        clearPaneSelection(pane)
        updatePane(pane) {
            it.copy(
                path = next.path,
                environment = next.environment,
                files = emptyList(),
                entries = emptyList(),
                filter = FileManagerFilter(), filterDraft = FileManagerFilterDraft(),
                backStack = it.backStack + current,
                forwardStack = it.forwardStack.dropLast(1),
                error = null,
            )
        }
        syncActiveTab(pane)
        loadPaneDirectory(pane, next.path, next.environment)
        return true
    }

    fun navigateToPath(path: String, environment: String? = currentEnvironment) {
        if (path.isEmpty()) return
        if (environment == "recycle" && path != "/回收站") {
            openError = "回收站只提供恢复与彻底删除。浏览普通目录请先从存储抽屉切换位置。"
            return
        }
        val normalizedPath = when {
            path == "/" -> "/"
            path.endsWith("/") -> path.dropLast(1)
            else -> path
        }
        navigatePaneTo(activePane, normalizedPath, environment, recordHistory = true)
    }

    private fun syncActiveTab(pane: FileManagerPane) {
        if (pane != activePane || activeTabIndex !in tabs.indices) return
        val state = paneState(pane)
        val updatedTabs = tabs.toMutableList()
        updatedTabs[activeTabIndex] = updatedTabs[activeTabIndex].copy(
            path = state.path,
            environment = state.environment,
        )
        tabs.clear()
        tabs.addAll(updatedTabs)
    }

    fun addTab(
        path: String = initialStoragePath,
        title: String = context.getString(R.string.file_manager_new_tab),
    ) {
        tabs.add(TabItem(path, title, null))
        activeTabIndex = tabs.size - 1
        navigatePaneTo(activePane, path, null, recordHistory = true)
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1 || index !in tabs.indices) return
        tabs.removeAt(index)
        activeTabIndex = activeTabIndex.coerceAtMost(tabs.lastIndex)
        val tab = tabs[activeTabIndex]
        navigatePaneTo(activePane, tab.path, tab.environment, recordHistory = true)
    }

    fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        val tab = tabs[index]
        navigatePaneTo(activePane, tab.path, tab.environment, recordHistory = true)
    }

    fun createNewFolder(folderName: String) = createEntry(folderName, directory = true)

    fun createNewFile(fileName: String) = createEntry(fileName, directory = false)

    fun beginCreateEntry() {
        if (isWriting || isRecycleBin) return
        creationLocation = FileManagerLocation(currentPath, currentEnvironment)
        newEntryName = ""
        creationError = null
        creationUnknown = false
        showNewEntryDialog = true
    }

    fun dismissCreateEntry() {
        if (isCreating) return
        if (creationUnknown) creationLocation?.let(::refreshLocation)
        showNewEntryDialog = false
        creationLocation = null
    }

    private fun createEntry(name: String, directory: Boolean) {
        if (isWriting || creationUnknown || closed) return
        creationError = fileManagerNameError(name)
        if (creationError != null) return
        val location = creationLocation ?: FileManagerLocation(currentPath, currentEnvironment)
        if (location.environment == "recycle") { creationError = "回收站不能新建项目"; return }
        creationLocation = location
        isCreating = true
        creationError = null
        viewModelScope.launch {
            // 已经提交的创建不能因页面清理丢失终态，更不能把未知结果展示为可安全重试。
            withContext(NonCancellable) {
                val taskRecord = beginTaskRecord(if (directory) "新建文件夹" else "新建文件", location, location, 1)
                var outcome = FileManagerTransferOutcome.UNKNOWN
                try {
                    val parameters = listOf(
                        ToolParameter("path", fileManagerJoinPath(location.path, name)),
                        ToolParameter("create_mode", "no_replace"),
                    )
                    val result = withContext(directoryDispatcher) {
                        executeDirectoryTool(AITool(
                            name = if (directory) "make_directory" else "create_file",
                            parameters = withEnvParams(parameters, location.environment),
                        ))
                    }
                    outcome = if (result.success) FileManagerTransferOutcome.COMPLETED else FileManagerTransferOutcome.FAILED
                    if (result.success) {
                        showNewEntryDialog = false
                        creationLocation = null
                    } else {
                        creationError = result.error?.takeIf { it.isNotBlank() } ?: "创建失败，请检查名称和访问权限"
                    }
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Create result unavailable: ${e.javaClass.simpleName}")
                    creationUnknown = true
                    creationError = "创建结果未确认，请关闭并检查目录；不会自动重试"
                } finally {
                    finishTaskRecord(taskRecord, listOf(FileManagerTransferItemResult(name, outcome, creationError, fileManagerJoinPath(location.path, name))))
                    isCreating = false
                    if (!closed) refreshLocation(location)
                }
            }
        }
    }

    fun cancelSearch() {
        searchVersion++
        searchJob?.cancel()
        searchJob = null
        isSearching = false
        searchError = null
        searchResults.clear()
        showSearchResultsDialog = false
        searchLocation = null
        searchSummary = ""
        searchLimitations = emptyList()
        showingSavedSearch = false
    }

    fun searchFiles(query: String, requestedLocation: FileManagerLocation? = null, requestedPane: FileManagerPane = activePane) {
        val options = try { searchForm.options(query) } catch (failure: IllegalArgumentException) {
            searchError = failure.message; return
        }
        cancelSearch()
        val version = searchVersion
        val location = requestedLocation ?: FileManagerLocation(currentPath, currentEnvironment)
        val originPane = requestedPane
        val submittedForm = searchForm
        searchDialogLocation = location; searchDialogPane = originPane
        val local = location.environment.isNullOrBlank() || location.environment == "android"
        searchQuery = query
        searchDialogQuery = query
        searchLocation = location
        searchOriginPane = originPane
        searchSummary = "${if (options.recursive) "包含子目录" else "仅当前目录"} · ${location.path}"
        isSearching = true
        showSearchResultsDialog = true
        searchJob = viewModelScope.launch {
            var record = FileManagerSearchRecord(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), query, submittedForm, location)
            try {
                saveSearchRecord(record)
                val result = withContext(directoryDispatcher) {
                    // 远端/SAF 保留既有名称搜索；未实现的高级条件明确拒绝，不忽略条件返回伪匹配。
                    if (!local) require(options.content.isEmpty() && options.minimumBytes == null && options.maximumBytes == null &&
                        options.modifiedAfter == null && options.modifiedBefore == null && options.nameMode != FileSearchNameMode.REGEX) {
                        "此位置暂仅支持名称搜索；大小、时间、内容和正则请使用手机存储"
                    }
                    val parameters = if (local) listOf(ToolParameter("path", location.path),
                        ToolParameter("search_mode", "manager"), ToolParameter("search_options", Json.encodeToString(options)))
                    else listOf(ToolParameter("path", location.path),
                        ToolParameter("pattern", if (options.nameMode == FileSearchNameMode.GLOB) query else "*$query*"),
                        ToolParameter("case_insensitive", (!options.caseSensitive).toString()),
                        ToolParameter("max_depth", if (options.recursive) "-1" else "0"))
                    executeDirectoryTool(AITool("find_files", withEnvParams(parameters, location.environment)))
                }
                ensureActive()
                if (version != searchVersion || closed) return@launch
                check(result.success) { result.error ?: "搜索失败" }
                val data = result.result
                val files = when (data) {
                    is FileSearchData -> {
                        searchSummary += " · 扫描 ${data.scanned} 项 · 跳过 ${data.skipped} 项"
                        searchLimitations = data.limitations
                        data.entries.distinctBy { it.path }.map {
                            FileItem(it.path.substringAfterLast('/'), it.directory, size = it.size, lastModified = it.modified, fullPath = it.path)
                        }
                    }
                    is FindFilesResultData -> data.files.distinct().filter { path ->
                        options.includeHidden || path.removePrefix(location.path).split('/').none { it.startsWith('.') && it.isNotEmpty() }
                    }.map { FileItem(it.substringAfterLast('/'), isDirectory = false, fullPath = it) }
                    else -> error("搜索结果格式无效")
                }
                val displayedFiles = manuallyVisibleEntries(files, location.path, location.environment)
                searchResults.addAll(displayedFiles)
                record = record.copy(results = displayedFiles, total = displayedFiles.size, status = if (searchLimitations.isEmpty()) "完成" else "部分结果",
                    summary = searchSummary, limitations = searchLimitations)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (version == searchVersion && !closed) {
                    AppLogger.e("FileManagerViewModel", "Search failed: ${failure.javaClass.simpleName}")
                    searchError = failure.message ?: "搜索失败，请检查位置和权限"
                    record = record.copy(status = "失败", error = searchError)
                }
            } finally {
                if (record.status == "搜索中") record = record.copy(status = "已取消")
                withContext(NonCancellable) { saveSearchRecord(record) }
                if (version == searchVersion) isSearching = false
            }
        }
    }

    fun navigateToFileDirectory(filePath: String) {
        val location = searchLocation ?: return
        val result = searchResults.firstOrNull { it.fullPath == filePath } ?: return
        if (showingSavedSearch) {
            val version = searchVersion
            val origin = searchOriginPane
            val parent = filePath.substringBeforeLast('/').ifBlank { "/" }
            if (isSearching) return
            isSearching = true
            searchJob = viewModelScope.launch {
                try {
                    val response = withContext(directoryDispatcher) { executeDirectoryTool(AITool("list_files",
                        withEnvParams(listOf(ToolParameter("path", parent)), location.environment))) }
                    if (version != searchVersion || closed) return@launch
                    check(response.success) { response.error ?: "无法核对保存结果，请检查存储与权限" }
                    val current = (response.result as? DirectoryListingData)?.entries?.firstOrNull { it.name == result.name }
                    check(current != null && current.isDirectory == result.isDirectory) { "此搜索结果已失效或类型改变，请重新搜索" }
                    showingSavedSearch = false
                    isSearching = false
                    searchOriginPane = origin
                    navigateToFileDirectory(filePath)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (version == searchVersion) searchError = failure.message ?: "无法核对保存结果"
                } finally { if (version == searchVersion) isSearching = false }
            }
            return
        }
        // 结果属于原搜索环境；切换活动栏后也不能把 SAF/Linux 路径当作手机路径。
        val directoryPath = if (result.isDirectory) filePath else filePath.substringBeforeLast('/').ifBlank { "/" }
        activatePane(searchOriginPane)
        navigatePaneTo(searchOriginPane, directoryPath, location.environment, recordHistory = true)
        // 用户明确选择了隐藏搜索结果，定位时需同步可见性，否则名称筛选后会得到空列表。
        if (!result.isDirectory && result.name.startsWith('.') && !showHiddenFiles) toggleHiddenFiles()
        if (!showManuallyHiddenFiles && manuallyHiddenFiles.any { it.contains(filePath, location.environment) }) setShowManuallyHidden(true)
        if (!result.isDirectory) setDirectoryFilter(result.name)
        cancelSearch()
    }

    private var clipboardVersion = 0L

    fun setClipboard(files: List<FileItem>, isCut: Boolean) {
        clipboardVersion++
        clipboardFiles.clear()
        clipboardFiles.addAll(files.filter { it.name != ".." })
        clipboardSourcePath = currentPath
        clipboardSourceEnvironment = currentEnvironment
        isCutOperation = isCut
    }

    fun clearClipboard() {
        clipboardVersion++
        clipboardFiles.clear()
        clipboardSourcePath = null
        clipboardSourceEnvironment = null
        isCutOperation = false
    }

    fun beginCopySelection() {
        if (isLoading || error != null || selectedFiles.isEmpty()) return
        setClipboard(selectedFiles.toList(), isCut = false)
        clearPaneSelection(activePane)
    }

    fun beginCopyContextItem() {
        val file = contextMenuFile ?: return
        activatePane(contextMenuPane)
        setClipboard(listOf(file), isCut = false)
        showBottomActionMenu = false
    }

    fun requestPaste() {
        if (!canCreateHere || clipboardFiles.isEmpty()) return
        val source = clipboardSourcePath ?: return
        pendingCopy = FileManagerCopyRequest(clipboardFiles.toList(),
            FileManagerLocation(source, clipboardSourceEnvironment), FileManagerLocation(currentPath, currentEnvironment), isCutOperation, clipboardVersion)
    }

    fun dismissPaste() { pendingCopy = null }

    fun confirmPaste() {
        val request = pendingCopy ?: return
        if (isWriting || fileManagerTransferError(request) != null) return
        pendingCopy = null
        startCopy(request)
    }

    /** 兼容内部调用；UI 必须先 requestPaste 展示固定来源与目标。 */
    fun pasteFiles() {
        if (transferState.running || clipboardFiles.isEmpty()) return
        val source = clipboardSourcePath ?: return
        startCopy(FileManagerCopyRequest(clipboardFiles.toList(),
            FileManagerLocation(source, clipboardSourceEnvironment), FileManagerLocation(currentPath, currentEnvironment), isCutOperation, clipboardVersion))
    }

    fun stopTransferAfterCurrent() {
        if (!transferState.running) return
        transferState = transferState.copy(stopRequested = true)
        // 等待冲突时没有正在写的项目，可立即停止；已提交工具必须等待真实结果。
        conflictDecision?.complete(null)
    }

    fun openTransferDestination() {
        if (transferState.running) return
        val destination = transferState.destination ?: return
        showTransferDetails = false
        navigateToPath(destination.path, destination.environment)
    }

    fun resolveCopyConflict(newName: String?) {
        if (newName != null && fileManagerNameError(newName) != null) return
        conflictDecision?.complete(newName)
    }

    private fun startCopy(request: FileManagerCopyRequest) {
        if (isWriting || closed) return
        val batch = request.files.filter { it.name != ".." }.distinctBy { it.name }
        if (batch.isEmpty()) return
        fileManagerTransferError(request)?.let { openError = it; return }
        val clipboardAtStart = request.clipboardVersion
        transferState = FileManagerTransferState(total = batch.size, running = true,
            source = request.source, destination = request.destination, move = request.moveRequested)
        showTransferDetails = true
        viewModelScope.launch {
            val taskRecord = withContext(NonCancellable) { beginTaskRecord(if (request.moveRequested) "移动" else "复制", request.source, request.destination, batch.size) }
            try {
                for (file in batch) {
                    ensureActive()
                    if (transferState.stopRequested || closed) break
                    transferState = transferState.copy(currentName = file.name)
                    var targetName = file.name
                    var itemFinished = false
                    while (!itemFinished && !transferState.stopRequested && !closed) {
                        val target = fileManagerJoinPath(request.destination.path, targetName)
                        var copied: ToolResult? = null
                        var rejected: String? = null
                        var submitted = false
                        // 当前工具调用不可被 UI stop 或 ViewModel 清理截断结果发布，否则可能把已写入误报为未执行。
                        withContext(NonCancellable) {
                            try {
                                copied = withContext(directoryDispatcher) {
                                    require(fileManagerNameError(file.name) == null && fileManagerNameError(targetName) == null) { "项目名称无效" }
                                    val source = fileManagerJoinPath(request.source.path, file.name)
                                    val sameEnvironment = (request.source.environment ?: "android") == (request.destination.environment ?: "android")
                                    val local = request.source.environment == null || request.source.environment == "android"
                                    val sourceNormalized = if (local) File(source).canonicalPath.replace(File.separatorChar, '/') else source
                                    val targetNormalized = if (local && sameEnvironment) File(target).canonicalPath.replace(File.separatorChar, '/') else target
                                    require(!sameEnvironment || !file.isDirectory ||
                                        !targetNormalized.startsWith(sourceNormalized.trimEnd('/') + "/")) {
                                        "目标不能是源项目或其子目录"
                                    }
                                    require(!request.moveRequested || !sameEnvironment || sourceNormalized != targetNormalized) { "来源与目标相同，无需移动" }
                                    if (sameEnvironment && sourceNormalized == targetNormalized) {
                                        return@withContext ToolResult("copy_file", false,
                                            FileOperationData("copy", path = source, successful = false, details = "目标已存在",
                                                errorCode = FileCopyErrorCode.CONFLICT), "目标已存在")
                                    }
                                    val parameters = listOf(
                                        ToolParameter("source", source), ToolParameter("destination", target),
                                        ToolParameter("recursive", file.isDirectory.toString()),
                                        ToolParameter(if (request.moveRequested) "move_mode" else "copy_mode", if (request.moveRequested) "move_no_replace" else "no_replace"),
                                        ToolParameter("source_environment", request.source.environment ?: "android"),
                                        ToolParameter("dest_environment", request.destination.environment ?: "android"),
                                    )
                                    submitted = true
                                    executeDirectoryTool(AITool(if (request.moveRequested) "move_file" else "copy_file", parameters))
                                }
                            } catch (e: IllegalArgumentException) {
                                if (submitted) {
                                    appendTransferResult(FileManagerTransferItemResult(file.name,
                                        FileManagerTransferOutcome.UNKNOWN, "结果未确认，请检查目标后再操作", target))
                                    itemFinished = true
                                } else rejected = e.message ?: "项目参数无效"
                            } catch (e: Exception) {
                                AppLogger.e("FileManagerViewModel", "Copy result unavailable: ${e.javaClass.simpleName}")
                                // 工具可能已经写入但返回丢失；不能标为普通失败或自动重新提交。
                                appendTransferResult(FileManagerTransferItemResult(file.name,
                                    FileManagerTransferOutcome.UNKNOWN, "结果未确认，请检查目标后再操作", target))
                                itemFinished = true
                            }
                            val response = copied
                            val operation = response?.result as? FileOperationData
                            // 写入必须返回一致的结构化终态，不能把字符串成功或矛盾标记当成已完成。
                            if (!itemFinished && submitted &&
                                (operation == null || response.success != operation.successful)) {
                                appendTransferResult(FileManagerTransferItemResult(file.name,
                                    FileManagerTransferOutcome.UNKNOWN, response?.error ?: operation?.details ?: "结果未确认，请检查来源与目标", target, operation?.stagingPath))
                                itemFinished = true
                            }
                            if (!itemFinished && (response?.success == true || rejected != null ||
                                    operation?.errorCode != FileCopyErrorCode.CONFLICT || operation.stagingPath != null)) {
                                appendTransferResult(FileManagerTransferItemResult(file.name,
                                    if (response?.success == true) FileManagerTransferOutcome.COMPLETED else FileManagerTransferOutcome.FAILED,
                                    rejected ?: if (response?.success == true) operation?.details else response?.error ?: "操作失败，请检查来源与目标",
                                    target, operation?.stagingPath))
                                itemFinished = true
                            }
                        }
                        if (itemFinished) {
                            if (transferState.results.lastOrNull()?.let { it.outcome == FileManagerTransferOutcome.UNKNOWN || it.stagingPath != null } == true) stopTransferAfterCurrent()
                            continue
                        }
                        // 只有明确且无残留的冲突才能再次提交；用户选择保留两份，仍走同一原子不覆盖入口。
                        if (transferState.stopRequested || closed) break
                        val decision = CompletableDeferred<String?>()
                        conflictDecision = decision
                        copyConflict = FileManagerCopyConflict(file.name, targetName, file.isDirectory)
                        val newName = try { decision.await() } finally {
                            copyConflict = null
                            conflictDecision = null
                        }
                        if (transferState.stopRequested || closed) break
                        if (newName == null) {
                            appendTransferResult(FileManagerTransferItemResult(file.name, FileManagerTransferOutcome.SKIPPED,
                                "已跳过同名项目", target))
                            itemFinished = true
                        } else targetName = newName
                    }
                }
            } finally {
                copyConflict = null
                conflictDecision = null
                val recorded = transferState.results.map { it.name }.toSet()
                val notStarted = batch.filterNot { it.name in recorded }.map {
                    FileManagerTransferItemResult(it.name, FileManagerTransferOutcome.NOT_STARTED, "已停止，未提交操作")
                }
                val finishedTransfer = transferState.copy(running = false, currentName = null,
                    results = transferState.results + notStarted)
                // 终态落盘前保持写入锁，避免新任务覆盖后续剪贴板清理使用的批次结果。
                withContext(NonCancellable) { finishTaskRecord(taskRecord, finishedTransfer.results) }
                transferState = finishedTransfer
                if (request.moveRequested && clipboardVersion == clipboardAtStart && isCutOperation &&
                    clipboardSourcePath == request.source.path && clipboardSourceEnvironment == request.source.environment) {
                    val consumed = transferState.results.filter { it.outcome in setOf(FileManagerTransferOutcome.COMPLETED, FileManagerTransferOutcome.UNKNOWN) }.map { it.name }.toSet()
                    clipboardFiles.removeAll { it.name in consumed }
                    if (clipboardFiles.isEmpty()) clearClipboard()
                }
                if (!closed) {
                    refreshLocation(request.destination)
                    if (request.moveRequested) refreshLocation(request.source)
                }
            }
        }
    }

    private fun appendTransferResult(result: FileManagerTransferItemResult) {
        transferState = transferState.copy(results = transferState.results + result)
    }

    fun beginRenameContextItem() {
        if (isWriting) return
        if (contextItems().size > 1) { beginContextAction(FileManagerActionKind.RENAME); return }
        val file = contextMenuFile ?: return
        if (file.name == "..") return
        val pane = paneState(contextMenuPane)
        renameState = FileManagerRenameState(
            request = FileManagerRenameRequest(file.name, FileManagerLocation(pane.path, pane.environment)),
            newName = file.name,
        )
        showBottomActionMenu = false
    }

    fun updateRenameName(name: String) {
        if (!renameState.running && !renameState.unknown) renameState = renameState.copy(newName = name, error = null)
    }

    fun dismissRename() {
        if (renameState.running) return
        renameState.request?.let { if (renameState.unknown) refreshLocation(it.location) }
        renameState = FileManagerRenameState()
    }

    fun confirmRename() {
        if (isWriting || renameState.unknown || closed) return
        val request = renameState.request ?: return
        val name = renameState.newName
        val error = fileManagerNameError(name) ?: if (name == request.name) "请输入不同的名称" else null
        if (error != null) {
            renameState = renameState.copy(error = error)
            return
        }
        renameState = renameState.copy(running = true, error = null)
        viewModelScope.launch {
            withContext(NonCancellable) {
                val taskRecord = beginTaskRecord("重命名", request.location, request.location, 1)
                var outcome = FileManagerTransferOutcome.UNKNOWN
                var finalRename = renameState
                try {
                    val result = withContext(directoryDispatcher) {
                        executeDirectoryTool(AITool("move_file", withEnvParams(listOf(
                            ToolParameter("source", fileManagerJoinPath(request.location.path, request.name)),
                            ToolParameter("destination", fileManagerJoinPath(request.location.path, name)),
                            ToolParameter("move_mode", "rename_no_replace"),
                        ), request.location.environment)))
                    }
                    outcome = if (result.success) FileManagerTransferOutcome.COMPLETED else FileManagerTransferOutcome.FAILED
                    if (result.success) {
                        finalRename = FileManagerRenameState()
                        // 旧名称选择不能残留，否则下一次批量操作会指向已经不存在的项目。
                        FileManagerPane.entries.forEach { pane ->
                            val state = paneState(pane)
                            if (state.path == request.location.path && state.environment == request.location.environment) {
                                setSelectedFiles(pane, selectedFilesFor(pane).filterNot { it.name == request.name })
                                if (selectedFileFor(pane)?.name == request.name) setSelectedFile(pane, null)
                                setSelectionAnchor(pane, null)
                            }
                        }
                    } else finalRename = renameState.copy(running = false, error = result.error ?: "重命名失败，请检查名称和权限")
                } catch (e: Exception) {
                    AppLogger.e("FileManagerViewModel", "Rename result unavailable: ${e.javaClass.simpleName}")
                    finalRename = renameState.copy(running = false, unknown = true,
                        error = "重命名结果未确认，请关闭并检查目录；不会自动重试")
                } finally {
                    finishTaskRecord(taskRecord, listOf(FileManagerTransferItemResult(request.name, outcome, finalRename.error, fileManagerJoinPath(request.location.path, name))))
                    renameState = finalRename
                    if (!closed) refreshLocation(request.location)
                }
            }
        }
    }

    private fun refreshLocation(location: FileManagerLocation) {
        invalidateDirectorySizes(location)
        FileManagerPane.entries.forEach { pane ->
            val state = paneState(pane)
            if (state.path == location.path && (state.environment == location.environment ||
                    fileManagerIsLocal(state.environment) && fileManagerIsLocal(location.environment))) loadPaneDirectory(pane)
        }
    }

    override fun onCleared() {
        closed = true
        stopTransferAfterCurrent()
        super.onCleared()
    }

    init {
        historyStore?.let { store ->
            viewModelScope.launch {
                try { store.load() }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    historyError = "历史记录读取失败，原记录未覆盖"
                    AppLogger.e("FileManagerHistory", "History read failed: ${error.javaClass.simpleName}")
                }
                store.state.collect { history = it }
            }
        }
        settingsStore?.let { preferences ->
            viewModelScope.launch { preferences.state.collect { if (!closed) applySettings(it) } }
        }
        loadPaneDirectory(FileManagerPane.LEFT)
        loadPaneDirectory(FileManagerPane.RIGHT)
    }
}

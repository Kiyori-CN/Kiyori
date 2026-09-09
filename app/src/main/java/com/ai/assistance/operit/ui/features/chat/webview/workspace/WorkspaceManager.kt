package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.ui.common.copyPlainTextToClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.workspace.CommandConfig
import com.ai.assistance.operit.core.tools.defaultTool.websession.browser.BrowserWorkspaceDownloadDispatcher
import com.ai.assistance.operit.core.workspace.WorkspaceConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.features.chat.components.rememberCompactDialogMetrics
import com.ai.assistance.operit.ui.features.chat.components.attachments.AudioAttachmentPlayer
import com.ai.assistance.operit.ui.features.chat.components.attachments.VideoAttachmentPlayer
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.WebViewHandler
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeEditor
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.CodeFormatter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.LanguageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.Serializable

/** 可序列化的位置数据类，用于持久化FAB位置 */
@Serializable
data class FabPosition(val x: Float = 0f, val y: Float = 0f)

private fun WebView.releaseWorkspaceWebView() {
    stopLoading()
    removeAllViews()
    destroy()
}

private fun isLocalPreviewUrl(url: String): Boolean {
    if (url.isBlank()) return false
    return runCatching {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase()
        (uri.scheme == "http" || uri.scheme == "https") &&
            (host == "localhost" || host == "127.0.0.1")
    }.getOrDefault(false)
}

private fun previewWebViewOptions(url: String): WebViewHandler.WebViewOptions {
    if (!isLocalPreviewUrl(url)) {
        return WebViewHandler.WebViewOptions()
    }
    return WebViewHandler.WebViewOptions(
        preferDesktopSite = false,
        enableWorkspaceCorsProxy = false,
        supportZoom = false,
        useWideViewPort = false,
        loadWithOverviewMode = false
    )
}

@Composable
private fun WorkspaceMarkdownPreview(
    content: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 960.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                StreamMarkdownRenderer(
                    content = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    onLinkClick = { url -> uriHandler.openUri(url) }
                )
            }
        }
    }
}

/** VSCode风格的工作区管理器组件 集成了WebView预览和文件管理功能 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkspaceManager(
        actualViewModel: ChatViewModel,
        currentChat: ChatHistory,
        workspacePath: String,
        workspaceEnv: String? = null,
        isVisible: Boolean,
        onExportClick: (workDir: File) -> Unit
) {
    val context = LocalContext.current
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()
    val workspaceCommandExecutionState by actualViewModel.workspaceCommandExecutionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    
    val isSafEnv = remember(workspaceEnv) { workspaceEnv?.startsWith("repo:", ignoreCase = true) == true }

    var loadedConfiguration by remember(workspacePath, workspaceEnv) { mutableStateOf<LoadedWorkspaceConfiguration?>(null) }
    var isConfigLoading by remember { mutableStateOf(true) }
    var configError by remember { mutableStateOf<String?>(null) }
    var configRevision by remember { mutableIntStateOf(0) }
    val workspaceConfig = loadedConfiguration?.config ?: WorkspaceConfig()

    LaunchedEffect(isVisible, workspacePath, workspaceEnv, configRevision) {
        if (!isVisible) return@LaunchedEffect
        isConfigLoading = true
        configError = null
        try {
            loadedConfiguration = actualViewModel.loadWorkspaceConfiguration(workspacePath, workspaceEnv)
            actualViewModel.updateWebServerForCurrentChat(currentChat.id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadedConfiguration = null
            configError = context.getString(R.string.workspace_config_load_failed)
            actualViewModel.updateWebServerForCurrentChat(currentChat.id)
        } finally { isConfigLoading = false }
    }

    fun withWorkspaceEnvParams(base: List<ToolParameter>): List<ToolParameter> {
        if (workspaceEnv.isNullOrBlank()) return base
        return base + ToolParameter("environment", workspaceEnv)
    }

    // 将 webViewHandler 和 webView 实例提升到 remember 中，使其在重组中保持稳定
    val webViewHandler =
            remember(context) {
                WebViewHandler(context, BrowserWorkspaceDownloadDispatcher(context)).apply {
                    onFileChooserRequest = { intent, callback ->
                        actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                            callback(resultCode, data)
                        }
                    }
                }
            }
    var workspaceWebView by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedWorkspacePreviewUrl by remember(workspacePath, workspaceEnv) {
        mutableStateOf<String?>(null)
    }
    val workspacePreviewUrl = workspaceConfig.preview.url.ifBlank {
        if (workspaceConfig.server.enabled) "http://127.0.0.1:${com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer.WORKSPACE_PORT}" else ""
    }
    val workspacePreviewOptions = remember(workspacePreviewUrl) { previewWebViewOptions(workspacePreviewUrl) }

    var canWebViewGoBack by remember { mutableStateOf(false) }
    var canWebViewGoForward by remember { mutableStateOf(false) }
    var showCommandBrowserPreview by remember(workspacePath, workspaceEnv, workspaceConfig.preview.url) {
        mutableStateOf(false)
    }
    var commandPreviewWebView by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedCommandPreviewUrl by remember(workspacePath, workspaceEnv, workspaceConfig.preview.url) {
        mutableStateOf<String?>(null)
    }
    var lastHandledWebViewRefreshCounter by remember(workspacePath, workspaceEnv) {
        mutableIntStateOf(webViewRefreshCounter)
    }
    var canCommandPreviewGoBack by remember { mutableStateOf(false) }
    var canCommandPreviewGoForward by remember { mutableStateOf(false) }
    val commandPreviewUrl = workspaceConfig.preview.url
    val commandPreviewOptions = remember(commandPreviewUrl) { previewWebViewOptions(commandPreviewUrl) }

    LaunchedEffect(webViewHandler) {
        webViewHandler.onCanGoBackChanged = { canGoBack ->
            canWebViewGoBack = canGoBack
        }
        webViewHandler.onCanGoForwardChanged = { canGoForward ->
            canWebViewGoForward = canGoForward
        }
    }

    val commandPreviewHandler =
        remember(context) {
            WebViewHandler(context, BrowserWorkspaceDownloadDispatcher(context)).apply {
                onFileChooserRequest = { intent, callback ->
                    actualViewModel.startFileChooserForResult(intent) { resultCode, data ->
                        callback(resultCode, data)
                    }
                }
            }
        }

    LaunchedEffect(commandPreviewHandler) {
        commandPreviewHandler.onCanGoBackChanged = { canGoBack ->
            canCommandPreviewGoBack = canGoBack
        }
        commandPreviewHandler.onCanGoForwardChanged = { canGoForward ->
            canCommandPreviewGoForward = canGoForward
        }
    }

    // 文件管理和标签状态 - 使用内存态，避免编辑大文件时频繁持久化整份内容
    var showFileManager by remember { mutableStateOf(false) }
    val editorState = remember(actualViewModel, workspacePath, workspaceEnv) {
        actualViewModel.workspaceEditorState(workspacePath, workspaceEnv)
    }
    var filePreviewStates by remember(workspacePath, workspaceEnv) { mutableStateOf(mapOf<String, Boolean>()) }
    val isBrowserPreviewVisible =
        isVisible && !isConfigLoading && configError == null && loadedConfiguration != null && editorState.currentFileIndex == -1 && workspaceConfig.preview.type == "browser"
    val isCommandPreviewVisible =
        isVisible && !isConfigLoading && configError == null && loadedConfiguration != null &&
            editorState.currentFileIndex == -1 &&
            workspaceConfig.preview.type != "browser" &&
            showCommandBrowserPreview &&
            commandPreviewUrl.isNotEmpty()
    val activePreviewWebView =
        when {
            isBrowserPreviewVisible -> workspaceWebView
            isCommandPreviewVisible -> commandPreviewWebView
            else -> null
        }
    val activePreviewCanGoBack =
        when {
            isBrowserPreviewVisible -> canWebViewGoBack
            isCommandPreviewVisible -> canCommandPreviewGoBack
            else -> false
        }
    val activePreviewCanGoForward =
        when {
            isBrowserPreviewVisible -> canWebViewGoForward
            isCommandPreviewVisible -> canCommandPreviewGoForward
            else -> false
        }
    
    // 控制可展开FAB的菜单状态
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    var showRenameWorkspaceDialog by remember { mutableStateOf(false) }
    var renameWorkspaceInput by remember(workspacePath) { mutableStateOf(File(workspacePath).name) }
    var renameWorkspaceError by remember { mutableStateOf<String?>(null) }
    var isRenamingWorkspace by remember { mutableStateOf(false) }
    val canRenameWorkspace by remember(workspacePath, workspaceEnv) {
        mutableStateOf(
            !isSafEnv &&
                runCatching {
                    val workspaceRoot = File(context.filesDir, "workspace").canonicalFile
                    File(workspacePath).canonicalFile.parentFile?.canonicalFile == workspaceRoot
                }.getOrDefault(false)
        )
    }
    
    // 解绑确认对话框状态
    var showUnbindConfirmDialog by remember { mutableStateOf(false) }
    var isUnbinding by remember { mutableStateOf(false) }
    var unbindError by remember { mutableStateOf<String?>(null) }
    
    // 关闭文件确认对话框状态
    var fileToClosePath by remember(workspacePath, workspaceEnv) { mutableStateOf<String?>(null) }
    
    // 当前活动的编辑器引用
    var activeEditor by remember(workspacePath, workspaceEnv) { mutableStateOf<com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.NativeCodeEditor?>(null) }
    var editorInteraction by remember { mutableStateOf(com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.EditorInteractionState()) }
    var activeEditorPath by remember(workspacePath, workspaceEnv) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            isFabMenuExpanded = false
        }
    }

    // 监听WebView刷新计数器变化并触发刷新
    LaunchedEffect(
        webViewRefreshCounter,
        isBrowserPreviewVisible,
        isCommandPreviewVisible,
        workspaceWebView,
        commandPreviewWebView
    ) {
        if (webViewRefreshCounter <= lastHandledWebViewRefreshCounter) {
            return@LaunchedEffect
        }

        lastHandledWebViewRefreshCounter = webViewRefreshCounter
        AppLogger.d("WorkspaceManager", "WebView refresh triggered, counter: $webViewRefreshCounter")

        // 仅处理新的刷新事件，避免重新进入页面时把旧计数误判成一次刷新。
        kotlinx.coroutines.delay(100)
        when {
            isBrowserPreviewVisible -> workspaceWebView?.reload()
            isCommandPreviewVisible -> commandPreviewWebView?.reload()
        }
    }

    // 外部读取挂起期间可能继续编辑、关标签或打开新文件，只发布仍匹配的单文件快照。
    LaunchedEffect(isVisible, workspacePath, workspaceEnv, editorState.refreshGeneration, editorState.isRestoring) {
        if (isVisible && !editorState.isRestoring) {
            editorState.openFiles.toList().forEach { fileInfo ->
                if (fileInfo.path in editorState.unsavedFiles || fileInfo.path in editorState.savingFiles) return@forEach
                val currentFile = File(fileInfo.path)
                val requiresProviderRead = isSafEnv || workspaceEnv?.equals("linux", ignoreCase = true) == true
                if (!requiresProviderRead && fileInfo.lastModified != Long.MIN_VALUE &&
                    (!currentFile.exists() || currentFile.lastModified() <= fileInfo.lastModified)) return@forEach
                if (fileInfo.isReadOnlyPreview) {
                    editorState.acceptExternalUpdate(fileInfo, fileInfo.copy(lastModified = if (requiresProviderRead) System.currentTimeMillis() else currentFile.lastModified()))
                } else {
                    try {
                        val tool = AITool("read_file_full", withWorkspaceEnvParams(listOf(ToolParameter("path", fileInfo.path))))
                        val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                        if (result.success && result.result is FileContentData) {
                            editorState.acceptExternalUpdate(fileInfo, fileInfo.copy(
                                content = result.result.content,
                                lastModified = currentFile.lastModified(),
                            ))
                        }
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e("WorkspaceManager", "Failed to refresh open file", error)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        isVisible,
        isSafEnv,
        workspacePath,
        workspaceEnv,
        workspaceConfig.preview.type,
        editorState.currentFileIndex,
        workspaceWebView
    ) {
        if (!isVisible || isSafEnv) {
            return@LaunchedEffect
        }

        WorkspacePreviewRefreshBus.events.collect { event ->
            val isSameWorkspace = event.workspacePath == workspacePath
            val isSameEnvironment =
                event.workspaceEnv?.trim().orEmpty().equals(
                    workspaceEnv?.trim().orEmpty(),
                    ignoreCase = true
                )
            val shouldRefreshBrowserPreview =
                editorState.currentFileIndex == -1 && workspaceConfig.preview.type == "browser"

            if (!isSameWorkspace || !isSameEnvironment || !shouldRefreshBrowserPreview) {
                return@collect
            }

            AppLogger.d(
                "WorkspaceManager",
                "Workspace preview refresh requested by ${event.source}: ${event.affectedPaths.joinToString()}"
            )
            workspaceWebView?.reload()
        }
    }
    
    fun saveFile(fileInfo: OpenFileInfo, closeAfterSave: Boolean = false) {
        if (fileInfo.isReadOnlyPreview || fileInfo.path in editorState.savingFiles) return
        coroutineScope.launch {
            try {
                val saved = editorState.save(fileInfo.path) { snapshot ->
                    val tool = AITool("write_file", withWorkspaceEnvParams(listOf(
                        ToolParameter("path", snapshot.path), ToolParameter("content", snapshot.content),
                    )))
                    val result = withContext(Dispatchers.IO) { toolHandler.executeTool(tool) }
                    check(result.success) { result.error ?: context.getString(R.string.file_manager_operation_failed) }
                    true
                }
                if (saved) {
                    if (fileInfo.path == File(workspacePath, ".operit/config.json").path) configRevision++
                    if (fileInfo.isHtml && filePreviewStates[fileInfo.path] == true) actualViewModel.refreshWebView()
                    if (closeAfterSave) {
                        editorState.close(fileInfo.path)
                        fileToClosePath = null
                    }
                } else if (closeAfterSave) {
                    actualViewModel.showToast(context.getString(R.string.workspace_save_draft_changed))
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("WorkspaceManager", "Failed to save workspace file", error)
                actualViewModel.showToast(context.getString(R.string.workspace_save_failed, fileInfo.name))
            }
        }
    }

    fun closeFile(path: String) {
        if (path in editorState.savingFiles) return
        if (path in editorState.unsavedFiles) fileToClosePath = path else editorState.close(path)
    }

    // 切换文件预览状态
    fun togglePreview(path: String) {
        filePreviewStates =
                filePreviewStates.toMutableMap().apply { this[path] = !(this[path] ?: false) }
    }

    // 打开文件
    fun openFile(fileInfo: OpenFileInfo) {
        // 检查文件是否已经打开
        val existingIndex = editorState.openFiles.indexOfFirst { it.path == fileInfo.path }

        if (existingIndex != -1) {
            // 如果文件已经打开，切换到该标签
            editorState.currentFileIndex = existingIndex
        } else {
            // 否则添加到打开的文件列表
            editorState.openFiles = editorState.openFiles + fileInfo
            editorState.currentFileIndex = editorState.openFiles.size - 1

            // 初始化预览状态
            filePreviewStates =
                    filePreviewStates.toMutableMap().apply {
                        // HTML文件默认预览，Markdown保持默认编辑态，其他文件也默认编辑态
                        this[fileInfo.path] = fileInfo.isHtml
                    }
        }
    }

    // 新的布局根节点，使用Box来支持FAB和底部面板的覆盖
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
    ) {
        BackHandler(enabled = activePreviewCanGoBack) {
            if (activePreviewCanGoBack) {
                try {
                    activePreviewWebView?.goBack()
                } catch (e: Exception) {
                    AppLogger.e("WorkspaceManager", "Failed to navigate WebView back", e)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 整合后的顶部栏：标签 + 动态操作
            Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.zIndex(1f) // 强制将标签栏置于顶层，防止被WebView覆盖
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 文件标签栏
                    Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        // 预览标签
                        VSCodeTab(
                                title = stringResource(R.string.workspace_preview),
                                icon = Icons.Default.Visibility,
                                isActive = editorState.currentFileIndex == -1,
                                isUnsaved = false,
                                onClose = null,
                                onClick = { editorState.currentFileIndex = -1 }
                        )

                        // 打开的文件标签
                        editorState.openFiles.forEachIndexed { index, fileInfo ->
                            VSCodeTab(
                                    title = fileInfo.name,
                                    icon = getFileIcon(fileInfo.name), // 使用统一的 getFileIcon
                                    isActive = editorState.currentFileIndex == index,
                                    isUnsaved = editorState.unsavedFiles.contains(fileInfo.path),
                                    onClose = { closeFile(fileInfo.path) },
                                    onClick = { editorState.currentFileIndex = index }
                            )
                        }
                    }

                    // 动态操作区域
                    val currentFile = editorState.openFiles.getOrNull(editorState.currentFileIndex)

                    // 保存按钮
                    if (currentFile != null && editorState.unsavedFiles.contains(currentFile.path)) {
                        IconButton(
                            onClick = {
                                saveFile(currentFile)
                            },
                            enabled = currentFile.path !in editorState.savingFiles,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = context.getString(R.string.save)
                            )
                        }
                    }

                    if (currentFile != null && (currentFile.isHtml || currentFile.isMarkdown)) {
                        val isPreview = filePreviewStates[currentFile.path] ?: false
                        IconButton(
                                onClick = { togglePreview(currentFile.path) },
                                // 限制按钮大小，使其与标签高度(40.dp)保持一致，防止撑开父布局
                                modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                    if (isPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                    contentDescription = "Toggle Preview"
                            )
                        }
                    } else if (isBrowserPreviewVisible || isCommandPreviewVisible) {
                        IconButton(
                            onClick = { activePreviewWebView?.goBack() },
                            enabled = activePreviewCanGoBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.web_session_back),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { activePreviewWebView?.goForward() },
                            enabled = activePreviewCanGoForward,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = stringResource(R.string.web_session_forward),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { activePreviewWebView?.reload() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.web_session_refresh),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (isCommandPreviewVisible) {
                            IconButton(
                                onClick = { showCommandBrowserPreview = false },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.workspace_close_preview),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 主内容区域
            Box(
                    modifier =
                            Modifier.weight(1f)
                                    .background(MaterialTheme.colorScheme.surface) // 添加背景色防止闪烁
            ) {
                when {
                    editorState.currentFileIndex == -1 && (isConfigLoading || configError != null) -> {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            if (isConfigLoading) CircularProgressIndicator()
                            else {
                                Text(configError.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                TextButton(onClick = { configRevision++ }) { Text(stringResource(R.string.workspace_config_reload)) }
                                TextButton(onClick = { showFileManager = true }) { Text(stringResource(R.string.workspace_open_files)) }
                            }
                        }
                    }
                    editorState.currentFileIndex == -1 && workspaceConfig.preview.type == "browser" && workspacePreviewUrl.isBlank() -> {
                        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.workspace_preview_not_configured), textAlign = TextAlign.Center)
                            TextButton(onClick = { showFileManager = true }) { Text(stringResource(R.string.workspace_open_files)) }
                        }
                    }
                    // 显示WebView预览（仅当preview类型为browser时）
                    editorState.currentFileIndex == -1 && workspaceConfig.preview.type == "browser" -> {
                        key(workspacePath, workspaceEnv) {
                            AndroidView(
                                    factory = { androidContext ->
                                        ParentInterceptingWebView(androidContext).apply {
                                            webViewHandler.configureWebView(
                                                this,
                                                WebViewHandler.WebViewMode.WORKSPACE,
                                                "workspace_preview_webview",
                                                workspacePreviewOptions
                                            )
                                            loadUrl(workspacePreviewUrl)
                                            workspaceWebView = this
                                            webViewHandler.currentWebView = this
                                            lastLoadedWorkspacePreviewUrl = workspacePreviewUrl
                                        }
                                    },
                                    update = { view ->
                                        workspaceWebView = view
                                        webViewHandler.currentWebView = view
                                        if (lastLoadedWorkspacePreviewUrl != workspacePreviewUrl) {
                                            view.loadUrl(workspacePreviewUrl)
                                            lastLoadedWorkspacePreviewUrl = workspacePreviewUrl
                                        }
                                    },
                                    onRelease = { view ->
                                        if (workspaceWebView === view) {
                                            workspaceWebView = null
                                        }
                                        if (webViewHandler.currentWebView === view) {
                                            webViewHandler.currentWebView = null
                                        }
                                        canWebViewGoBack = false
                                        canWebViewGoForward = false
                                        lastLoadedWorkspacePreviewUrl = null
                                        view.releaseWorkspaceWebView()
                                    },
                                    modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // 显示命令按钮界面（当preview类型不是browser时）
                    editorState.currentFileIndex == -1 && workspaceConfig.preview.type != "browser" -> {
                        if (isCommandPreviewVisible) {
                            key(workspacePath, workspaceEnv, commandPreviewUrl) {
                                AndroidView(
                                    factory = { androidContext ->
                                        ParentInterceptingWebView(androidContext).apply {
                                            commandPreviewHandler.configureWebView(
                                                this,
                                                WebViewHandler.WebViewMode.WORKSPACE,
                                                "workspace_preview_${workspacePath.hashCode()}",
                                                commandPreviewOptions
                                            )
                                            loadUrl(commandPreviewUrl)
                                            commandPreviewWebView = this
                                            commandPreviewHandler.currentWebView = this
                                            lastLoadedCommandPreviewUrl = commandPreviewUrl
                                        }
                                    },
                                    update = { webView ->
                                        commandPreviewWebView = webView
                                        commandPreviewHandler.currentWebView = webView
                                        if (lastLoadedCommandPreviewUrl != commandPreviewUrl) {
                                            webView.loadUrl(commandPreviewUrl)
                                            lastLoadedCommandPreviewUrl = commandPreviewUrl
                                        }
                                        webView.requestFocus()
                                    },
                                    onRelease = { webView ->
                                        if (commandPreviewWebView === webView) {
                                            commandPreviewWebView = null
                                        }
                                        if (commandPreviewHandler.currentWebView === webView) {
                                            commandPreviewHandler.currentWebView = null
                                        }
                                        canCommandPreviewGoBack = false
                                        canCommandPreviewGoForward = false
                                        lastLoadedCommandPreviewUrl = null
                                        webView.releaseWorkspaceWebView()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            CommandButtonsView(
                                config = workspaceConfig,
                                workspacePath = workspacePath,
                                onCommandExecute = { command ->
                                    // 在专属会话中执行命令
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        loadedConfiguration?.let { actualViewModel.executeCommandInWorkspace(command, workspacePath, it) }
                                    } else {
                                        // 对于旧版本Android，显示不支持提示
                                        AppLogger.w("WorkspaceManager", "Terminal features require Android 8.0+")
                                    }
                                },
                                onOpenBrowserPreview = {
                                    showCommandBrowserPreview = true
                                }
                            )
                        }
                    }
                    // 显示打开的文件
                    editorState.currentFileIndex in editorState.openFiles.indices -> {
                        val fileInfo = editorState.openFiles[editorState.currentFileIndex]
                        val isPreviewMode = filePreviewStates[fileInfo.path] ?: false

                        when {
                            // 图片文件：显示图片预览
                            fileInfo.isImage -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = workspaceEnv,
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                WorkspaceImagePreview(
                                    fileName = fileInfo.name,
                                    previewUri = previewUri,
                                    isSourceLoading = previewFileState.loading,
                                    errorMessage = previewFileState.errorMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isAudio -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = workspaceEnv,
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator()
                                    } else if (previewUri != null) {
                                        AudioAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier.fillMaxWidth(),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isVideo -> {
                                val previewFileState by rememberWorkspacePreviewFileState(
                                    fileInfo = fileInfo,
                                    workspaceEnv = workspaceEnv,
                                    toolHandler = toolHandler
                                )
                                val previewUri = remember(previewFileState.file?.absolutePath) {
                                    workspacePreviewUriFromFile(previewFileState.file)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previewFileState.loading) {
                                        CircularProgressIndicator(color = Color.White)
                                    } else if (previewUri != null) {
                                        VideoAttachmentPlayer(
                                            uri = previewUri,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 180.dp, max = 420.dp),
                                            autoPlay = false
                                        )
                                    } else {
                                        Text(
                                            text = previewFileState.errorMessage
                                                ?: context.getString(R.string.cannot_open_file, fileInfo.name),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            fileInfo.isReadOnlyDocumentPreviewable -> {
                                WorkspaceReadOnlyDocumentPreview(
                                    fileInfo = fileInfo,
                                    workspaceEnv = workspaceEnv,
                                    toolHandler = toolHandler,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            fileInfo.isMarkdown && isPreviewMode -> {
                                WorkspaceMarkdownPreview(
                                    content = fileInfo.content,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            // HTML文件的预览模式：使用WebView
                            fileInfo.isHtml && isPreviewMode -> {
                                AndroidView(
                                        factory = { context ->
                                            ParentInterceptingWebView(context).apply {
                                                webViewHandler.configureWebView(this, WebViewHandler.WebViewMode.WORKSPACE, "workspace_file_preview_${fileInfo.path}")
                                            }
                                         },
                                        update = { webView ->
                                            val baseUrl = "file://${File(fileInfo.path).parent}/"
                                            webView.loadDataWithBaseURL(
                                                    baseUrl,
                                                    fileInfo.content, // 使用最新的文件内容
                                                    "text/html",
                                                    "UTF-8",
                                                    null
                                            )
                                        },
                                        onRelease = { webView ->
                                            webView.releaseWorkspaceWebView()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                )
                            }
                            // 其他所有情况：使用CodeEditor
                            else -> {
                                key(fileInfo.path) {
                                    val fileLanguage = LanguageDetector.detectLanguage(fileInfo.name)
                                    CodeEditor(
                                            code = fileInfo.content,
                                            language = fileLanguage,
                                            onCodeChange = { newContent ->
                                                editorState.edit(fileInfo.path, newContent)
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            onInteractionStateChanged = { interaction ->
                                                if (activeEditorPath == fileInfo.path) editorInteraction = interaction
                                            },
                                            editorRef = { editor ->
                                                if (editor != null) {
                                                    activeEditor = editor
                                                    activeEditorPath = fileInfo.path
                                                } else if (activeEditorPath == fileInfo.path) {
                                                    activeEditor = null
                                                    activeEditorPath = null
                                                    editorInteraction = com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.EditorInteractionState()
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 从底部弹出的文件管理器面板
        BackHandler(enabled = isVisible && showFileManager) { showFileManager = false }
        if (isVisible && showFileManager) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showFileManager = false }
            )
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    // 文件管理器标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(context.getString(R.string.file_browser), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showFileManager = false }) {
                            Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                        }
                    }

                    HorizontalDivider()

                    // 嵌入文件浏览器组件
                    FileBrowser(
                        initialPath = workspacePath,
                        environment = workspaceEnv,
                        onCancel = { showFileManager = false },
                        isManageMode = true,
                        onFileOpen = { fileInfo ->
                            openFile(fileInfo)
                            showFileManager = false
                        }
                    )
                }
            }
        }
        
        // 键盘弹起时隐藏工作区悬浮菜单，避免遮挡编辑区与输入区域
        if (!isImeVisible) {
            ExpandableFabMenu(
                isExpanded = isVisible && isFabMenuExpanded,
                onToggle = { isFabMenuExpanded = !isFabMenuExpanded },
                exportEnabled = workspaceConfig.export.enabled && workspaceEnv.isNullOrBlank(),
                canUndo = activeEditor != null && editorInteraction.canUndo,
                canRedo = activeEditor != null && editorInteraction.canRedo,
                onExportClick = {
                    if (!isSafEnv) {
                        onExportClick(File(workspacePath))
                    }
                },
                onFileManagerClick = { showFileManager = true },
                onUndoClick = { activeEditor?.takeIf { activeEditorPath == editorState.openFiles.getOrNull(editorState.currentFileIndex)?.path }?.undo() },
                onRedoClick = { activeEditor?.takeIf { activeEditorPath == editorState.openFiles.getOrNull(editorState.currentFileIndex)?.path }?.redo() },
                onFormatClick = {
                    // 格式化当前文件
                    val currentFile = editorState.openFiles.getOrNull(editorState.currentFileIndex)
                    if (currentFile != null) {
                        val language = LanguageDetector.detectLanguage(currentFile.name)
                        val formattedCode = CodeFormatter.format(currentFile.content, language)
                        
                        editorState.edit(currentFile.path, formattedCode)
                        activeEditor?.takeIf { activeEditorPath == editorState.openFiles.getOrNull(editorState.currentFileIndex)?.path }?.replaceAllText(formattedCode)
                    }
                    isFabMenuExpanded = false
                },
                onUnbindClick = { 
                    if (editorState.unsavedFiles.isNotEmpty()) {
                        actualViewModel.showToast(context.getString(R.string.workspace_unbind_save_first))
                    } else showUnbindConfirmDialog = true
                    isFabMenuExpanded = false
                },
                renameEnabled = canRenameWorkspace,
                onRenameWorkspaceClick = {
                    if (editorState.unsavedFiles.isNotEmpty()) {
                        actualViewModel.showToast(context.getString(R.string.workspace_rename_save_first))
                    } else {
                        renameWorkspaceInput = File(workspacePath).name
                        renameWorkspaceError = null
                        showRenameWorkspaceDialog = true
                    }
                    isFabMenuExpanded = false
                },
                canFormat = editorState.openFiles.getOrNull(editorState.currentFileIndex)?.let { file ->
                    val language = LanguageDetector.detectLanguage(file.name).lowercase()
                    language in listOf("javascript", "js", "css", "html", "htm")
                } ?: false
            )
        }
        
        // 解绑确认对话框
        if (isVisible && showUnbindConfirmDialog) {
            AlertDialog(
                onDismissRequest = { if (!isUnbinding) showUnbindConfirmDialog = false },
                title = { Text(context.getString(R.string.unbind_workspace_title)) },
                text = { Column {
                    Text(context.getString(R.string.unbind_workspace_confirm))
                    unbindError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = {
                    TextButton(
                        enabled = !isUnbinding,
                        onClick = {
                            isUnbinding = true
                            unbindError = null
                            coroutineScope.launch {
                                try {
                                    check(editorState.unsavedFiles.isEmpty()) { context.getString(R.string.workspace_unbind_save_first) }
                                    actualViewModel.unbindChatFromWorkspace(currentChat.id, workspacePath, workspaceEnv)
                                    showUnbindConfirmDialog = false
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (error: Exception) { unbindError = error.message ?: context.getString(R.string.workspace_binding_failed) }
                                finally { isUnbinding = false }
                            }
                        }
                    ) {
                        Text(context.getString(if (isUnbinding) R.string.workspace_binding_saving else R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isUnbinding, onClick = { showUnbindConfirmDialog = false }) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }

        if (isVisible && showRenameWorkspaceDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isRenamingWorkspace) {
                        showRenameWorkspaceDialog = false
                        renameWorkspaceError = null
                    }
                },
                title = { Text(context.getString(R.string.workspace_rename_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = renameWorkspaceInput,
                            enabled = !isRenamingWorkspace,
                            onValueChange = {
                                renameWorkspaceInput = it
                                renameWorkspaceError = null
                            },
                            label = { Text(context.getString(R.string.file_dialog_new_name)) },
                            singleLine = true,
                            isError = renameWorkspaceError != null,
                            supportingText = {
                                renameWorkspaceError?.let { Text(it) }
                            }
                        )
                        Text(
                            text = context.getString(R.string.workspace_rename_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            if (editorState.unsavedFiles.isNotEmpty() || editorState.savingFiles.isNotEmpty() || editorState.isRestoring) {
                                renameWorkspaceError =
                                    context.getString(R.string.workspace_rename_save_first)
                                return@TextButton
                            }
                            val requestedName = renameWorkspaceInput
                            isRenamingWorkspace = true
                            coroutineScope.launch {
                                try {
                                    val result = actualViewModel.renameWorkspace(
                                        chatId = currentChat.id,
                                        newWorkspaceName = requestedName
                                    )
                                    actualViewModel.showToast(
                                        context.getString(
                                            R.string.workspace_rename_success,
                                            result.workspaceName
                                        )
                                    )
                                    showRenameWorkspaceDialog = false
                                    renameWorkspaceError = null
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    renameWorkspaceError =
                                        error.message
                                            ?: context.getString(R.string.workspace_rename_failed)
                                } finally { isRenamingWorkspace = false }
                            }
                        }
                    ) {
                        Text(context.getString(R.string.confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isRenamingWorkspace,
                        onClick = {
                            showRenameWorkspaceDialog = false
                            renameWorkspaceError = null
                        }
                    ) {
                        Text(context.getString(R.string.cancel))
                    }
                }
            )
        }
        
        // 关闭文件确认对话框
        if (isVisible && fileToClosePath != null) {
            val file = editorState.openFiles.find { it.path == fileToClosePath }
            if (file != null) {
                AlertDialog(
                    onDismissRequest = { if (file.path !in editorState.savingFiles) fileToClosePath = null },
                    title = { Text(context.getString(R.string.save_changes_question)) },
                    text = { Text(context.getString(R.string.file_modified_save_prompt, file.name)) },
                    confirmButton = {
                        TextButton(
                            enabled = file.path !in editorState.savingFiles,
                            onClick = {
                                saveFile(file, closeAfterSave = true)
                            }
                        ) {
                            Text(context.getString(R.string.save))
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(enabled = file.path !in editorState.savingFiles, onClick = { fileToClosePath = null }) {
                                Text(context.getString(R.string.cancel))
                            }
                            TextButton(
                                enabled = file.path !in editorState.savingFiles,
                                onClick = {
                                    editorState.close(file.path)
                                    fileToClosePath = null
                                }
                            ) {
                                Text(context.getString(R.string.dont_save))
                            }
                        }
                    }
                )
            }
        }

        val commandDialogState = workspaceCommandExecutionState
        if (isVisible && commandDialogState != null &&
            commandDialogState.workspacePath == workspacePath &&
            commandDialogState.workspaceEnvironment == workspaceEnv &&
            commandDialogState.isVisible
        ) {
            WorkspaceCommandExecutionDialog(
                state = commandDialogState,
                onClose = { actualViewModel.dismissWorkspaceCommandExecutionDialog(workspacePath) },
                onCancel = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        actualViewModel.cancelWorkspaceCommandExecution()
                    }
                },
                onCopyOutput = { output ->
                    context.copyPlainTextToClipboard("Kiyori terminal output", output)
                    actualViewModel.showToast(context.getString(R.string.copied_to_clipboard))
                }
            )
        }
        if (isVisible && editorState.isRestoring) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(context.getString(R.string.workspace_restoring)) },
                text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
                confirmButton = {},
            )
        }
    }
}

@Composable
private fun WorkspaceCommandExecutionDialog(
    state: WorkspaceCommandExecutionState,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onCopyOutput: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val outputText = remember(state.outputEntries) {
        state.outputEntries.joinToString(separator = "\n")
    }

    var previousOutputSize by remember(state.commandId) { mutableIntStateOf(0) }
    LaunchedEffect(state.outputEntries.size, state.omittedOutputEntries) {
        val wasAtBottom = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= previousOutputSize - 2
        previousOutputSize = state.outputEntries.size
        if (state.outputEntries.isNotEmpty() && wasAtBottom) {
            listState.animateScrollToItem(state.outputEntries.lastIndex)
        }
    }

    val dialogMetrics = rememberCompactDialogMetrics()
    val outerPadding = if (dialogMetrics.isCompact) 8.dp else 20.dp
    val contentPadding = if (dialogMetrics.isCompact) 16.dp else 20.dp
    val outputMinHeight = if (dialogMetrics.isCompact) 96.dp else 180.dp
    val outputMaxHeight = if (dialogMetrics.isCompact) 220.dp else 360.dp
    val surfaceModifier =
        Modifier
            .fillMaxWidth()
            .padding(outerPadding)
            .let { base ->
                if (dialogMetrics.isCompact) {
                    base.heightIn(max = dialogMetrics.maxHeight - outerPadding * 2)
                } else {
                    base
                }
            }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = surfaceModifier,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = state.commandLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.commandText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        !state.isRunning && state.exitCode != null -> stringResource(R.string.workspace_command_exit_code, state.exitCode)
                        !state.isRunning -> stringResource(R.string.workspace_command_result_unconfirmed)
                        state.isCancelling -> stringResource(R.string.workspace_command_cancelling)
                        else -> stringResource(R.string.workspace_command_running)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!state.isRunning && state.exitCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                if (state.omittedOutputEntries > 0) {
                    Text(stringResource(R.string.workspace_command_output_limited, state.omittedOutputEntries),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.isRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Surface(
                    modifier =
                        if (dialogMetrics.isCompact) {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = outputMinHeight, max = outputMaxHeight)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = outputMinHeight, max = outputMaxHeight)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    if (state.outputEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.isRunning) {
                                        R.string.workspace_command_waiting_output
                                    } else {
                                        R.string.workspace_command_no_output
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(
                                    items = state.outputEntries,
                                    key = { index, _ -> index }
                                ) { _, line ->
                                    Text(
                                        text = line.ifEmpty { " " },
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (state.isRunning) {
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.workspace_command_hide))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = onCancel,
                            enabled = !state.isCancelling
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    } else {
                        TextButton(
                            onClick = { onCopyOutput(outputText) },
                            enabled = state.outputEntries.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.copy_result))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onClose) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    exportEnabled: Boolean = true,
    onExportClick: () -> Unit,
    onFileManagerClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onFormatClick: () -> Unit,
    onUnbindClick: () -> Unit,
    renameEnabled: Boolean = false,
    onRenameWorkspaceClick: () -> Unit,
    canFormat: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val density = LocalDensity.current
        val minX = -with(density) { (maxWidth - 56.dp).coerceAtLeast(0.dp).toPx() }
        val minY = -with(density) { (maxHeight - 56.dp).coerceAtLeast(0.dp).toPx() }
        val menuMaxHeight = maxHeight.coerceAtLeast(48.dp)
        var savedPosition by rememberLocal<FabPosition?>("fab_menu_offset", null)
        var draggingPosition by remember { mutableStateOf<FabPosition?>(null) }
        fun bounded(position: FabPosition?) = FabPosition(
            (position?.x ?: 0f).takeIf { it.isFinite() }?.coerceIn(minX, 0f) ?: 0f,
            (position?.y ?: 0f).takeIf { it.isFinite() }?.coerceIn(minY, 0f) ?: 0f,
        )
        val position = bounded(draggingPosition ?: savedPosition)
        Box(Modifier.align(Alignment.BottomEnd).offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }) {
            FloatingActionButton(
                onClick = onToggle,
                modifier = Modifier.pointerInput(minX, minY) {
                    detectDragGestures(
                        onDragStart = { draggingPosition = bounded(savedPosition) },
                        onDragEnd = { savedPosition = bounded(draggingPosition); draggingPosition = null },
                        onDragCancel = { draggingPosition = null },
                    ) { change, amount ->
                        change.consume()
                        val current = bounded(draggingPosition ?: savedPosition)
                        draggingPosition = bounded(FabPosition(current.x + amount.x, current.y + amount.y))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(if (isExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                    contentDescription = stringResource(if (isExpanded) R.string.workspace_close_menu else R.string.workspace_open_menu))
            }
            DropdownMenu(expanded = isExpanded, onDismissRequest = onToggle,
                modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).heightIn(max = menuMaxHeight)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.undo)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) }, enabled = canUndo, onClick = { onToggle(); onUndoClick() })
                DropdownMenuItem(text = { Text(stringResource(R.string.redo)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, null) }, enabled = canRedo, onClick = { onToggle(); onRedoClick() })
                if (canFormat) DropdownMenuItem(text = { Text(stringResource(R.string.format_code)) },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) }, onClick = { onToggle(); onFormatClick() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.files)) },
                    leadingIcon = { Icon(Icons.Default.Folder, null) }, onClick = { onToggle(); onFileManagerClick() })
                if (exportEnabled) DropdownMenuItem(text = { Text(stringResource(R.string.export)) },
                    leadingIcon = { Icon(Icons.Default.Upload, null) }, onClick = { onToggle(); onExportClick() })
                if (renameEnabled) DropdownMenuItem(text = { Text(stringResource(R.string.workspace_rename_action)) },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onToggle(); onRenameWorkspaceClick() })
                DropdownMenuItem(text = { Text(stringResource(R.string.unbind)) },
                    leadingIcon = { Icon(Icons.Default.LinkOff, null) }, onClick = { onToggle(); onUnbindClick() })
            }
        }
    }
}

/** VSCode风格的标签组件 */
@Composable
fun VSCodeTab(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isActive: Boolean,
        isUnsaved: Boolean,
        onClose: (() -> Unit)? = null,
        onClick: () -> Unit
) {
    val backgroundColor =
            if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent // 非活动标签背景透明

    val contentColor =
            if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant

    val bottomBorderColor = if (isActive) contentColor else Color.Transparent

    Box(
            modifier =
                    Modifier.height(48.dp).widthIn(max = 240.dp)
                            .background(
                                    backgroundColor,
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                            .selectable(selected = isActive, role = Role.Tab, onClick = onClick)
    ) {
        Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                        text = title,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                )

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isUnsaved) {
                            Icon(
                                Icons.Filled.FiberManualRecord,
                                contentDescription = stringResource(R.string.workspace_close_unsaved_tab, title),
                                modifier = Modifier.size(8.dp),
                                tint = contentColor.copy(alpha = 0.9f)
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.workspace_close_tab, title),
                                modifier = Modifier.size(14.dp),
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp)) // 保持对齐
                }
            }
            // 活动标签下划线
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(bottomBorderColor))
        }
    }
}

/**
 * 命令按钮视图组件
 * 用于非 browser 类型的预览界面，显示 config.json 中定义的命令按钮
 */
@Composable
fun CommandButtonsView(
    config: WorkspaceConfig,
    workspacePath: String,
    onCommandExecute: (CommandConfig) -> Unit,
    onOpenBrowserPreview: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = config.title ?: "${config.projectType.uppercase()} ${stringResource(R.string.workspace_project_suffix)}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (config.description != null) {
            Text(
                text = config.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = stringResource(R.string.workspace_click_buttons_below),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 浏览器预览按钮（可选）
        if (config.preview.showPreviewButton && config.preview.url.isNotEmpty()) {
            Button(
                onClick = onOpenBrowserPreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = config.preview.previewButtonLabel,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 显示命令按钮
        if (config.commands.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = stringResource(R.string.workspace_no_commands_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            config.commands.forEach { command ->
                Button(
                    onClick = { onCommandExecute(command) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = command.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 项目信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.workspace_path_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = workspacePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

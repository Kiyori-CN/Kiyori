package com.ai.assistance.operit.ui.features.chat.webview.workspace

import com.ai.assistance.operit.core.workspace.WorkspaceConfig
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider

/** 页面借用读取配置时的实际 provider 身份，不创建或持有另一连接生命周期。 */
internal data class LoadedWorkspaceConfiguration(
    val config: WorkspaceConfig,
    val fileSystem: FileSystemProvider? = null,
    val environment: String? = null,
)

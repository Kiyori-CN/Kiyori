package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mcp.InstallProgress
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.net.Uri
import android.content.Context
import com.ai.assistance.operit.R

/** ViewModel for MCP 服务器管理，包括安装、卸载等功能 */
class MCPViewModel(
    val repository: MCPRepository,
    private val context: Context
) : ViewModel() {

    // 当前安装进度
    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    // 安装结果
    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    // 当前正在操作的服务器
    private val _currentServer = MutableStateFlow<MCPLocalServer.PluginMetadata?>(null)
    val currentServer: StateFlow<MCPLocalServer.PluginMetadata?> = _currentServer.asStateFlow()

    
    // 存储选中的ZIP文件URI
    private var selectedZipUri: Uri? = null

    init {
        repository.observeConfiguration(viewModelScope)
        // 同步已安装状态
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    private val _isOperating = MutableStateFlow(false)
    val isOperating = _isOperating.asStateFlow()
    private val _isUninstallOperation = MutableStateFlow(false)
    val isUninstallOperation = _isUninstallOperation.asStateFlow()
    private var retryOperation: (() -> Unit)? = null

    private fun runOperation(server: MCPLocalServer.PluginMetadata, uninstall: Boolean,
        operation: suspend ((InstallProgress) -> Unit) -> InstallResult) {
        if (_isOperating.value) return
        _isOperating.value = true
        _isUninstallOperation.value = uninstall
        _currentServer.value = server
        _installProgress.value = InstallProgress.Preparing
        _installResult.value = null
        retryOperation = { runOperation(server, uninstall, operation) }
        viewModelScope.launch {
            try {
                _installResult.value = operation { progress ->
                    // Finished 只能跟随最终结果，不能在元数据持久化前开放关闭入口。
                    if (progress !is InstallProgress.Finished) _installProgress.value = progress
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _installResult.value = InstallResult.Error(context.getString(if (uninstall) R.string.mcp_uninstall_failed else R.string.mcp_install_incomplete))
            } finally {
                _installProgress.value = InstallProgress.Finished
                _isOperating.value = false
            }
        }
    }

    fun retryLastOperation() { if (!_isOperating.value) retryOperation?.invoke() }

    fun installServer(server: MCPLocalServer.PluginMetadata) = runOperation(server, false) { progress ->
        repository.installMCPServer(server.id, progress)
    }

    fun installServerWithObject(server: MCPLocalServer.PluginMetadata) = runOperation(server, false) { progress ->
        repository.installMCPServerWithObject(server, progress)
    }

    fun installServerFromZip(server: MCPLocalServer.PluginMetadata, zipFilePath: String) {
        val uri = selectedZipUri
        runOperation(server, false) { progress ->
            if (uri == null) InstallResult.Error(context.getString(R.string.mcp_error_no_zip_selected))
            else repository.installMCPServerFromZip(server.id, uri, server.name, server.description, server.author, progress)
        }
    }

    fun setSelectedZipUri(uri: Uri) { selectedZipUri = uri }

    fun uninstallServer(server: MCPLocalServer.PluginMetadata) = runOperation(server, true) {
        val success = if (server.type == "remote") repository.removeRemoteServer(server.id) else repository.uninstallMCPServer(server.id)
        if (success) InstallResult.Success("") else InstallResult.Error(context.getString(R.string.mcp_uninstall_failed))
    }

    /** Await persistence so the editor can preserve its draft on failure. */
    suspend fun addRemoteServer(server: MCPLocalServer.PluginMetadata) = repository.addRemoteServer(server)

    suspend fun updateRemoteServer(server: MCPLocalServer.PluginMetadata, original: MCPLocalServer.PluginMetadata) =
        repository.updateRemoteServer(server, original)

    suspend fun generatePluginDescription(
        server: MCPLocalServer.PluginMetadata,
        pluginName: String
    ): Result<String> {
        return repository.generatePluginDescription(
            pluginId = server.id,
            pluginName = pluginName
        )
    }

    /** 重置安装状态 */
    fun resetInstallState() {
        if (_isOperating.value) return
        retryOperation = null
        _installProgress.value = null
        _installResult.value = null
        _currentServer.value = null
    }

    /** 获取已安装插件的路径 */
    fun getInstalledPath(serverId: String): String? = repository.getInstalledPluginPath(serverId)

    /** 获取本地插件信息，无需网络请求 */
    fun getLocalPluginDetails(serverId: String): MCPLocalServer.PluginMetadata? {
        // 如果插件没有安装，返回null
        if (!repository.isPluginInstalled(serverId)) {
            return null
        }

        // 从当前列表中查找，这里的信息已经通过updateInstalledStatus更新过，
        // 包含了本地元数据
        return repository.mcpServers.value.find { it.id == serverId }
    }

    /** 刷新本地插件列表 */
    fun refreshLocalPlugins() {
        viewModelScope.launch {
            repository.syncInstalledStatus()
            // 清除路径缓存，强制重新读取
        }
    }

    /** 刷新插件列表 */
    fun refreshPluginList() {
        viewModelScope.launch {
            repository.syncInstalledStatus()
        }
    }

    /** 检查插件是否已安装 */
    fun isPluginInstalled(serverId: String): Boolean {
        return repository.isPluginInstalled(serverId)
    }

    /** 同步所有插件的安装状态 */
    fun syncInstalledStatus() {
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    /** ViewModel Factory */
    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPViewModel::class.java)) {
                return MCPViewModel(MCPRepository(context.applicationContext), context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

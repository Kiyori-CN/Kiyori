package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 命令草稿的来源与加载结果一起发布，空命令不能再表示另一个插件的缓存。 */
data class McpDeployCommandState(
    val pluginId: String? = null,
    val loading: Boolean = false,
    val commands: List<String> = emptyList(),
    val error: String? = null
)

class MCPDeployViewModel(
    private val context: Context,
    private val mcpRepository: MCPRepository,
    private val mcpDeployer: MCPDeployer = MCPDeployer(context),
    private val commandLoader: suspend (String, String) -> List<String> = mcpDeployer::getDeployCommands
) : ViewModel() {
    private val _deploymentStatus = MutableStateFlow<MCPDeployer.DeploymentStatus>(MCPDeployer.DeploymentStatus.NotStarted)
    val deploymentStatus = _deploymentStatus.asStateFlow()
    private val _currentDeployingPlugin = MutableStateFlow<String?>(null)
    val currentDeployingPlugin = _currentDeployingPlugin.asStateFlow()
    private val _outputMessages = MutableStateFlow<List<String>>(emptyList())
    val outputMessages = _outputMessages.asStateFlow()
    private val _omittedOutputLines = MutableStateFlow(0)
    val omittedOutputLines = _omittedOutputLines.asStateFlow()
    private val _commandState = MutableStateFlow(McpDeployCommandState())
    val commandState = _commandState.asStateFlow()
    private var commandRequest = 0L
    private var deploymentJob: Job? = null
    private val _isDeploying = MutableStateFlow(false)
    val isDeploying = _isDeploying.asStateFlow()
    private val _environmentVariables = MutableStateFlow<Map<String, String>>(emptyMap())
    val environmentVariables = _environmentVariables.asStateFlow()

    suspend fun getDeployCommands(pluginId: String) {
        val request = ++commandRequest
        _commandState.value = McpDeployCommandState(pluginId, loading = true)
        try {
            val path = checkNotNull(mcpRepository.getInstalledPluginPath(pluginId))
            val commands = if (path.startsWith("virtual://")) emptyList() else commandLoader(pluginId, path)
            currentCoroutineContext().ensureActive()
            if (request == commandRequest) {
                _commandState.value = McpDeployCommandState(pluginId, commands = commands,
                    error = if (commands.isEmpty() && !path.startsWith("virtual://")) context.getString(R.string.mcp_deploy_error_cannot_determine) else null)
            }
        } catch (cancelled: CancellationException) {
            if (request == commandRequest) _commandState.value = McpDeployCommandState()
            throw cancelled
        } catch (_: Exception) {
            if (request == commandRequest) _commandState.value = McpDeployCommandState(pluginId,
                error = context.getString(R.string.mcp_deploy_error_cannot_determine))
        }
    }

    fun setEnvironmentVariables(envVars: Map<String, String>) {
        if (!_isDeploying.value) _environmentVariables.value = envVars.toMap()
    }

    fun deployPlugin(pluginId: String) = startDeployment(pluginId, null)

    fun deployPluginWithCommands(pluginId: String, customCommands: List<String>) = startDeployment(pluginId, customCommands.toList())

    private fun startDeployment(pluginId: String, customCommands: List<String>?) {
        if (_isDeploying.value) return
        if (_currentDeployingPlugin.value != pluginId) _environmentVariables.value = emptyMap()
        val environment = _environmentVariables.value.toMap()
        _isDeploying.value = true
        _currentDeployingPlugin.value = pluginId
        _outputMessages.value = emptyList()
        _omittedOutputLines.value = 0
        _deploymentStatus.value = MCPDeployer.DeploymentStatus.InProgress(context.getString(R.string.mcp_deploy_status_preparing))
        deploymentJob = viewModelScope.launch {
            try {
                val path = mcpRepository.getInstalledPluginPath(pluginId)
                    ?: error(context.getString(R.string.mcp_deploy_error_cannot_get_path, pluginId))
                // 默认部署重新分析当前目标；自定义命令只来自当前目标已加载的编辑器。
                val commands = customCommands ?: if (path.startsWith("virtual://")) emptyList() else commandLoader(pluginId, path)
                if (commands.isEmpty() && !path.startsWith("virtual://")) {
                    _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_error_cannot_determine))
                    return@launch
                }
                mcpDeployer.deployPluginWithCommands(pluginId, path, commands, environment) { status ->
                    if (status is MCPDeployer.DeploymentStatus.InProgress) {
                        val prefixes = listOf(context.getString(R.string.mcp_deployment_output_prefix).trimEnd(), context.getString(R.string.mcp_deploy_output_prefix).trimEnd())
                        val prefix = prefixes.firstOrNull { status.message.startsWith(it) }
                        if (prefix != null) {
                            val lines = status.message.removePrefix(prefix).trimStart().replace("\r\n", "\n").split('\n').filter { it.isNotBlank() }
                            if (lines.isNotEmpty()) {
                                val combined = _outputMessages.value + lines
                                _omittedOutputLines.update { it + (combined.size - 2000).coerceAtLeast(0) }
                                _outputMessages.value = combined.takeLast(2000)
                            }
                        } else _deploymentStatus.value = status
                    } else _deploymentStatus.value = status
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error(context.getString(R.string.mcp_deploy_failed))
            } finally { _isDeploying.value = false }
        }
    }

    fun resetDeploymentState() {
        if (_isDeploying.value) return
        _deploymentStatus.value = MCPDeployer.DeploymentStatus.NotStarted
        _currentDeployingPlugin.value = null
        _outputMessages.value = emptyList()
        _omittedOutputLines.value = 0
        _environmentVariables.value = emptyMap()
    }

    /** 工厂类 */
    class Factory(private val context: Context, private val mcpRepository: MCPRepository) :
            ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPDeployViewModel::class.java)) {
                return MCPDeployViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

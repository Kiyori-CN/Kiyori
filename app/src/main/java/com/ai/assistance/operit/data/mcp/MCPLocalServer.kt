package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.OperitPaths
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * 统一的MCP配置管理中心
 * 
 * 负责管理所有MCP相关的配置，包括：
 * - 官方MCP配置格式的读写
 * - 插件配置管理
 * - 服务器状态管理
 * - 统一存储在下载/Kiyori/mcp_plugins目录
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PATH = "server_path"
        
        // 配置文件名称
        private const val MCP_CONFIG_FILE = "mcp_config.json"
        private const val SERVER_STATUS_FILE = "server_status.json"

        @Volatile private var INSTANCE: MCPLocalServer? = null

        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 配置目录路径
    private val configBaseDir by lazy {
        OperitPaths.mcpPluginsDir()
    }

    // 配置文件路径
    private val mcpConfigFile get() = File(configBaseDir, MCP_CONFIG_FILE)
    private val serverStatusFile get() = File(configBaseDir, SERVER_STATUS_FILE)

    // 服务路径
    private val _serverPath = MutableStateFlow(configBaseDir.absolutePath)
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    private val pluginFilesMutex = kotlinx.coroutines.sync.Mutex()
    internal suspend fun <T> withPluginFilesMutation(action: suspend () -> T): T {
        pluginFilesMutex.lock()
        try { return action() } finally { pluginFilesMutex.unlock() }
    }

    private val configurationLock = Any()
    private val registrationGeneration = McpRegistrationGeneration()
    private val _configurationReadError = MutableStateFlow(false)
    val configurationReadError: StateFlow<Boolean> = _configurationReadError.asStateFlow()

    // 配置状态
    private val _mcpConfig = MutableStateFlow(MCPConfig())
    val mcpConfig: StateFlow<MCPConfig> = _mcpConfig.asStateFlow()

    // 插件元数据 - 现在从MCPConfig派生
    val pluginMetadata: StateFlow<Map<String, PluginMetadata>> = _mcpConfig
        .map { it.pluginMetadata.toMap() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyMap())

    // 服务器状态
    private val _serverStatus = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val serverStatus: StateFlow<Map<String, ServerStatus>> = _serverStatus.asStateFlow()

    // Gson实例 - 使用格式化输出
    private val gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        // 初始化时加载所有配置
        loadAllConfigurations()
    }

    // ==================== 官方MCP配置格式支持 ====================

    /**
     * 官方MCP配置格式数据结构
     */
    @Serializable
    data class MCPConfig(
        @SerializedName("mcpServers")
        val mcpServers: MutableMap<String, ServerConfig> = mutableMapOf(),
        @SerializedName("pluginMetadata")
        val pluginMetadata: MutableMap<String, PluginMetadata> = mutableMapOf()
    ) {
        @Serializable
        data class ServerConfig(
            @SerializedName("command")
            val command: String,
            @SerializedName("args")
            val args: List<String>? = emptyList(),
            @SerializedName("disabled")
            val disabled: Boolean = false,
            @SerializedName("autoApprove")
            val autoApprove: List<String>? = emptyList(),
            @SerializedName("env")
            val env: Map<String, String>? = emptyMap()
        )
    }

    /**
     * 插件元数据
     */
    @Serializable
    data class PluginMetadata(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String,
        @SerializedName("logoUrl")
        val logoUrl: String? = null,
        @SerializedName("author")
        val author: String = "Unknown",
        @SerializedName("isInstalled")
        val isInstalled: Boolean = false,
        @SerializedName("version")
        val version: String = "",
        @SerializedName("updatedAt")
        val updatedAt: String = "",
        @SerializedName("longDescription")
        val longDescription: String = "",
        @SerializedName("repoUrl")
        val repoUrl: String = "",
        // 新增字段以支持远程服务
        @SerializedName("type")
        val type: String = "local", // "local" or "remote"
        @SerializedName("endpoint")
        val endpoint: String? = null,
        @SerializedName("connectionType")
        val connectionType: String? = "httpStream",
        @SerializedName("disabled")
        val disabled: Boolean = false,
        // 认证相关字段（用于远程服务）
        @SerializedName("bearerToken")
        val bearerToken: String? = null,
        @SerializedName("headers")
        val headers: Map<String, String>? = null,
        // 本地安装相关字段
        @SerializedName("installedPath")
        val installedPath: String? = null,
        @SerializedName("installedTime")
        val installedTime: Long = System.currentTimeMillis(),
        // 市场配置（来自 GitHub Issue）
        @SerializedName("marketConfig")
        val marketConfig: String? = null
    )

    /**
     * 服务器运行状态
     * 注意：启用/禁用状态已移至ServerConfig.disabled字段
     */
    @Serializable
    data class ServerStatus(
        @SerializedName("serverId")
        val serverId: String,
        @SerializedName("lastStartTime")
        val lastStartTime: Long = 0L,
        @SerializedName("lastStopTime")
        val lastStopTime: Long = 0L,
        @SerializedName("errorMessage")
        val errorMessage: String? = null,
        @SerializedName("cachedTools")
        val cachedTools: List<CachedToolInfo>? = null,
        @SerializedName("toolsCachedTime")
        val toolsCachedTime: Long = 0L,
        val toolCacheConfiguration: String? = null
    )

    /**
     * 缓存的工具信息
     */
    @Serializable
    data class CachedToolInfo(
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String = "",
        @SerializedName("inputSchema")
        val inputSchema: String = "{}", // JSON字符串形式的schema
        @SerializedName("cachedAt")
        val cachedAt: Long = System.currentTimeMillis()
    )

    // ==================== 配置文件操作 ====================
    
    /**
     * 重新加载配置文件（用于用户手动编辑配置后刷新）
     */
    suspend fun reloadConfigurations() {
        withContext(Dispatchers.IO) {
            loadAllConfigurations(throwOnFailure = true)
            AppLogger.d(TAG, "配置已重新加载")
        }
    }

    /**
     * 加载所有配置文件
     */
    private fun loadAllConfigurations(throwOnFailure: Boolean = false) {
        synchronized(configurationLock) {
            try {
                val rawConfig = if (mcpConfigFile.exists()) {
                    checkNotNull(gson.fromJson(mcpConfigFile.readText(), MCPConfig::class.java))
                } else MCPConfig()
                val sanitized = sanitizeMCPConfig(rawConfig, "loadAllConfigurations")
                check(sanitized.removedServerIds.isEmpty()) { "Invalid MCP server configuration" }
                val updated = autoFillMissingMetadata(sanitized.config)
                if (updated != rawConfig) writeMcpConfigurationFile(mcpConfigFile, gson.toJson(updated))
                publishMcpConfiguration(updated)
                _configurationReadError.value = false
            } catch (_: Exception) {
                _configurationReadError.value = true
                AppLogger.e(TAG, "MCP configuration could not be loaded")
                if (throwOnFailure) throw java.io.IOException("MCP configuration could not be loaded")
                return
            }
            try {
                if (serverStatusFile.exists()) {
                    val statusJson = serverStatusFile.readText()
                    val type = object : TypeToken<Map<String, ServerStatus>>() {}.type
                    _serverStatus.value = gson.fromJson<Map<String, ServerStatus>>(statusJson, type) ?: emptyMap()
                    if (statusJson.contains("\"active\"")) coroutineScope.launch { saveServerStatus() }
                }
                initializeMissingServerStatus()
            } catch (_: Exception) {
                AppLogger.e(TAG, "MCP server status could not be loaded")
                if (throwOnFailure) throw java.io.IOException("MCP server status could not be loaded")
            }
        }
    }

    /**
     * 自动为缺失的服务器创建默认元数据
     */
    private fun autoFillMissingMetadata(config: MCPConfig): MCPConfig {
        val newMetadata = config.pluginMetadata.toMutableMap()
        var hasNewMetadata = false
        
        config.mcpServers.forEach { (serverId, serverConfig) ->
            if (!newMetadata.containsKey(serverId)) {
                // 从 serverId 生成友好的显示名称
                val displayName = serverId
                    .replace("_", " ")
                    .replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                
                // 创建默认元数据
                val metadata = PluginMetadata(
                    id = serverId,
                    name = displayName,
                    description = "",
                    logoUrl = null,
                    author = "Unknown",
                    isInstalled = true,
                    version = "1.0.0",
                    updatedAt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    longDescription = context.getString(R.string.mcp_local_auto_detected_server),
                    repoUrl = "",
                    type = "local",
                    endpoint = null,
                    connectionType = "httpStream"
                )
                
                newMetadata[serverId] = metadata
                hasNewMetadata = true
                AppLogger.d(TAG, "自动创建元数据: $serverId -> $displayName")
            }
        }
        
        return if (hasNewMetadata) {
            config.copy(pluginMetadata = newMetadata)
        } else {
            config
        }
    }

    private data class SanitizedConfigResult(
        val config: MCPConfig,
        val removedServerIds: List<String>,
        val removedMetadataIds: List<String>
    )

    private fun sanitizeServerConfig(
        serverId: String,
        serverConfig: MCPConfig.ServerConfig,
        source: String
    ): MCPConfig.ServerConfig? {
        val command = serverConfig.command.trim()
        if (command.isNullOrEmpty()) {
            AppLogger.w(TAG, "忽略无效MCP服务器配置: $serverId, source=$source, command为空")
            return null
        }

        val args = serverConfig.args?.mapNotNull { it } ?: emptyList()
        val autoApprove = serverConfig.autoApprove?.mapNotNull { it } ?: emptyList()
        val env = serverConfig.env?.entries?.mapNotNull { entry ->
            val key = entry.key.takeIf { it.isNotBlank() }
            if (key == null) null else key to entry.value
        }?.toMap() ?: emptyMap()

        return MCPConfig.ServerConfig(
            command = command,
            args = args,
            disabled = serverConfig.disabled,
            autoApprove = autoApprove,
            env = env
        )
    }

    private fun sanitizeMCPConfig(config: MCPConfig, source: String): SanitizedConfigResult {
        val sanitizedServers = mutableMapOf<String, MCPConfig.ServerConfig>()
        val removedServerIds = mutableListOf<String>()

        config.mcpServers.forEach { (serverId, serverConfig) ->
            val sanitizedServer = sanitizeServerConfig(serverId, serverConfig, source)
            if (sanitizedServer != null) {
                sanitizedServers[serverId] = sanitizedServer
            } else {
                removedServerIds.add(serverId)
            }
        }

        val sanitizedMetadata = config.pluginMetadata.toMutableMap()
        val removedMetadataIds = removedServerIds.filter { serverId ->
            sanitizedMetadata[serverId]?.type != "remote"
        }
        removedMetadataIds.forEach { serverId ->
            sanitizedMetadata.remove(serverId)
        }

        if (removedServerIds.isNotEmpty()) {
            AppLogger.w(
                TAG,
                "已清理无效MCP服务器配置: ${removedServerIds.joinToString()}" +
                    if (removedMetadataIds.isNotEmpty()) {
                        ", 同步移除本地元数据: ${removedMetadataIds.joinToString()}"
                    } else {
                        ""
                    }
            )
        }

        return SanitizedConfigResult(
            config = config.copy(
                mcpServers = sanitizedServers,
                pluginMetadata = sanitizedMetadata
            ),
            removedServerIds = removedServerIds,
            removedMetadataIds = removedMetadataIds
        )
    }
    
    /**
     * 为新配置的服务器初始化状态
     */
    private fun initializeMissingServerStatus() {
        val currentStatus = _serverStatus.value.toMutableMap()
        var hasNewStatus = false
        
        _mcpConfig.value.mcpServers.forEach { (serverId, _) ->
            if (!currentStatus.containsKey(serverId)) {
                currentStatus[serverId] = ServerStatus(
                    serverId = serverId,
                    lastStartTime = 0L,
                    lastStopTime = 0L,
                    errorMessage = null
                )
                hasNewStatus = true
                AppLogger.d(TAG, "初始化服务器状态: $serverId")
            }
        }
        
        if (hasNewStatus) {
            publishServerStatuses(currentStatus)
        }
    }

    /**
     * 保存MCP配置
     */
    suspend fun saveMCPConfig() = updateMcpConfiguration { it }

    /** 所有配置读改写与 reload 共用临界区；磁盘完整发布成功后才更新内存事实。 */
    private suspend fun updateMcpConfiguration(transform: (MCPConfig) -> MCPConfig) {
        withContext(Dispatchers.IO) {
            synchronized(configurationLock) {
                check(!_configurationReadError.value) { "Reload the MCP configuration before editing" }
                val updated = transform(_mcpConfig.value)
                writeMcpConfigurationFile(mcpConfigFile, gson.toJson(updated))
                publishMcpConfiguration(updated)
            }
        }
    }

    /** 调用者持有 configurationLock；配置落盘后先使旧运行时失效，再发布内存配置。 */
    private fun publishMcpConfiguration(updated: MCPConfig) {
        val previous = _mcpConfig.value
        registrationGeneration.advance(previous, updated).forEach { pluginId ->
            unregisterMcpRuntimeTools(context, pluginId)
        }
        _mcpConfig.value = updated
    }

    internal fun capturePluginRegistration(pluginId: String): McpPluginRuntimeIdentity? =
        synchronized(configurationLock) {
            if (_configurationReadError.value) null
            else registrationGeneration.capture(_mcpConfig.value, pluginId)
        }

    /** 网络发现不持锁；最终发布与禁用、删除、编辑配置互斥，旧发现结果不能复活工具。 */
    internal fun publishPluginRegistration(pluginId: String, expected: McpPluginRuntimeIdentity, publish: () -> Unit): Boolean =
        synchronized(configurationLock) {
            if (_configurationReadError.value || !expected.enabled ||
                registrationGeneration.capture(_mcpConfig.value, pluginId) != expected) false
            else { publish(); true }
        }

    /**
     * 保存服务器状态
     */
    suspend fun saveServerStatus() = updateServerStatuses { it }

    // 配置、发现缓存和启停状态使用同一锁；先原子落盘再发布，失败不伪装成已保存。
    private fun publishServerStatuses(updated: Map<String, ServerStatus>) {
        writeMcpConfigurationFile(serverStatusFile, gson.toJson(updated))
        _serverStatus.value = updated
    }

    private suspend fun updateServerStatuses(
        transform: (Map<String, ServerStatus>) -> Map<String, ServerStatus>
    ) = withContext(Dispatchers.IO) {
        synchronized(configurationLock) {
            publishServerStatuses(transform(_serverStatus.value))
        }
    }

    // ==================== MCP服务器管理 ====================

    /**
     * 添加或更新MCP服务器配置
     */
    suspend fun addOrUpdateMCPServer(
        serverId: String,
        command: String,
        args: List<String>? = emptyList(),
        env: Map<String, String>? = emptyMap(),
        disabled: Boolean = false,
        autoApprove: List<String>? = emptyList()
    ) {
        val normalizedCommand = command.trim()
        require(!normalizedCommand.isNullOrEmpty()) { "MCP服务器 $serverId 的 command 不能为空" }

        updateMcpConfiguration { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            newServers[serverId] = MCPConfig.ServerConfig(
                command = normalizedCommand,
                args = args?.mapNotNull { it } ?: emptyList(),
                disabled = disabled,
                autoApprove = autoApprove?.mapNotNull { it } ?: emptyList(),
                env = env?.entries?.mapNotNull { entry ->
                    val key = entry.key.takeIf { it.isNotBlank() }
                    if (key == null) null else key to entry.value
                }?.toMap() ?: emptyMap()
            )
            currentConfig.copy(mcpServers = newServers)
        }

        AppLogger.d(TAG, "MCP服务器配置已更新: $serverId")
    }

    /**
     * 删除MCP服务器配置
     */
    suspend fun removeMCPServer(serverId: String) {
        updateMcpConfiguration { currentConfig ->
            val newServers = currentConfig.mcpServers.toMutableMap()
            newServers.remove(serverId)
            val metadata = currentConfig.pluginMetadata.toMutableMap().apply { remove(serverId) }
            currentConfig.copy(mcpServers = newServers, pluginMetadata = metadata)
        }


        // 同时清理相关的元数据和状态
        removeServerStatus(serverId)
        
        AppLogger.d(TAG, "MCP服务器配置已删除: $serverId")
    }

    /**
     * 合并JSON配置到现有配置
     */
    suspend fun mergeConfigFromJson(jsonConfig: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val servers = parseMcpServerImport(jsonConfig)
            updateMcpConfiguration { current ->
                autoFillMissingMetadata(current.copy(mcpServers = current.mcpServers.toMutableMap().apply { putAll(servers) }))
            }
            initializeMissingServerStatus()
            Result.success(servers.size)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            AppLogger.e(TAG, "MCP配置合并未完成")
            Result.failure(java.io.IOException(context.getString(R.string.mcp_merge_incomplete)))
        }
    }

    /**
     * 获取配置文件路径
     */
    fun getConfigFilePath(): String = mcpConfigFile.absolutePath

    /**
     * 获取MCP服务器配置
     */
    fun getMCPServer(serverId: String): MCPConfig.ServerConfig? {
        return _mcpConfig.value.mcpServers[serverId]
    }

    /**
     * 获取所有MCP服务器配置
     */
    fun getAllMCPServers(): Map<String, MCPConfig.ServerConfig> {
        return _mcpConfig.value.mcpServers.toMap()
    }

    // ==================== 插件元数据管理 ====================

    /**
     * 添加或更新插件元数据
     */
    suspend fun addOrUpdatePluginMetadata(metadata: PluginMetadata) {
        updateMcpConfiguration { currentConfig ->
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            newMetadata[metadata.id] = metadata
            currentConfig.copy(pluginMetadata = newMetadata)
        }

        AppLogger.d(TAG, "插件元数据已更新: ${metadata.id} - ${metadata.name}")
    }

    suspend fun addRemotePlugin(metadata: PluginMetadata) {
        updateMcpConfiguration { current ->
            check(metadata.id !in current.pluginMetadata && metadata.id !in current.mcpServers) {
                "A plugin with this ID already exists"
            }
            current.copy(pluginMetadata = current.pluginMetadata.toMutableMap().apply { put(metadata.id, metadata) })
        }
    }

    internal suspend fun recordPluginInstallation(metadata: PluginMetadata) {
        updateMcpConfiguration { current ->
            val latest = current.pluginMetadata[metadata.id]
            check(latest?.type != "remote")
            val installed = latest?.copy(installedPath = metadata.installedPath,
                installedTime = metadata.installedTime, version = metadata.version,
                repoUrl = metadata.repoUrl, marketConfig = metadata.marketConfig, isInstalled = true)
                ?: metadata.copy(isInstalled = true)
            current.copy(pluginMetadata = current.pluginMetadata.toMutableMap().apply { put(metadata.id, installed) })
        }
    }

    internal suspend fun recordMarketConfigMetadata(metadata: List<PluginMetadata>) {
        updateMcpConfiguration { current ->
            val updated = current.pluginMetadata.toMutableMap()
            metadata.forEach { incoming ->
                check(incoming.id in current.mcpServers) { "Imported MCP configuration no longer exists" }
                updated[incoming.id] = mergeMcpMarketMetadata(updated[incoming.id], incoming)
            }
            current.copy(pluginMetadata = updated)
        }
    }

    suspend fun editPluginMetadata(original: PluginMetadata, edited: PluginMetadata) {
        updateMcpConfiguration { current ->
            val latest = checkNotNull(current.pluginMetadata[original.id]) { "Plugin no longer exists" }
            val merged = mergeMcpMetadataEdit(original, edited, latest)
            current.copy(pluginMetadata = current.pluginMetadata.toMutableMap().apply { put(original.id, merged) })
        }
    }

    /**
     * 删除插件元数据
     */
    suspend fun removePluginMetadata(pluginId: String) {
        updateMcpConfiguration { currentConfig ->
            val newMetadata = currentConfig.pluginMetadata.toMutableMap()
            newMetadata.remove(pluginId)
            currentConfig.copy(pluginMetadata = newMetadata)
        }

        AppLogger.d(TAG, "插件元数据已删除: $pluginId")
    }

    /**
     * 获取插件元数据
     */
    fun getPluginMetadata(pluginId: String): PluginMetadata? {
        return _mcpConfig.value.pluginMetadata[pluginId]
    }

    /**
     * 获取所有插件元数据
     */
    fun getAllPluginMetadata(): Map<String, PluginMetadata> {
        return _mcpConfig.value.pluginMetadata.toMap()
    }

    // ==================== 服务器状态管理 ====================

    /**
     * 更新服务器状态
     * 注意：启用/禁用状态请使用 setServerEnabled() 方法
     */
    suspend fun updateServerStatus(
        serverId: String,
        errorMessage: String? = null,
        cachedTools: List<CachedToolInfo>? = null,
        lastStartTime: Long? = null,
        lastStopTime: Long? = null
    ) {
        updateServerStatuses { current ->
            val currentStatus = current.toMutableMap()
            val existingStatus = currentStatus[serverId] ?: ServerStatus(serverId)
            currentStatus[serverId] = existingStatus.copy(
                errorMessage = errorMessage ?: existingStatus.errorMessage,
                cachedTools = cachedTools ?: existingStatus.cachedTools,
                toolsCachedTime = if (cachedTools != null) System.currentTimeMillis() else existingStatus.toolsCachedTime,
                // 未携带发现凭据的旧状态入口不能给工具缓存背书。
                toolCacheConfiguration = if (cachedTools != null) null else existingStatus.toolCacheConfiguration,
                lastStartTime = lastStartTime ?: existingStatus.lastStartTime,
                lastStopTime = lastStopTime ?: existingStatus.lastStopTime
            )
            currentStatus
        }
        AppLogger.d(TAG, "服务器状态已更新: $serverId")
    }

    /**
     * 缓存服务器的工具列表
     */
    internal suspend fun cacheServerTools(serverId: String, tools: List<CachedToolInfo>, expected: McpPluginRuntimeIdentity) {
        withContext(Dispatchers.IO) {
            publishPluginRegistration(serverId, expected) {
                val status = _serverStatus.value[serverId] ?: ServerStatus(serverId)
                publishServerStatuses(_serverStatus.value.toMutableMap().apply {
                    this[serverId] = status.copy(cachedTools = tools,
                        toolsCachedTime = System.currentTimeMillis(), toolCacheConfiguration = expected.fingerprint())
                })
            }
        }
    }

    /**
     * 获取缓存的工具列表
     */
    fun getCachedTools(serverId: String): List<CachedToolInfo>? {
        return synchronized(configurationLock) {
            val identity = capturePluginRegistration(serverId) ?: return@synchronized null
            val status = _serverStatus.value[serverId] ?: return@synchronized null
            // 老缓存没有配置指纹时重新发现；绝不把另一个端点或凭据下的工具当作当前事实。
            status.cachedTools.takeIf { status.toolCacheConfiguration == identity.fingerprint() }
        }
    }

    /**
     * 检查工具缓存是否有效 (有效期1天)
     */
    fun hasValidToolCache(serverId: String): Boolean {
        val status = _serverStatus.value[serverId] ?: return false
        
        val cachedTools = getCachedTools(serverId)
        val cacheTime = status.toolsCachedTime
        
        if (cachedTools.isNullOrEmpty() || cacheTime <= 0) {
            return false
        }
        
        // 缓存有效期为1天
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - cacheTime) in 0 until oneDayInMillis
    }

    /**
     * 删除服务器状态
     */
    suspend fun removeServerStatus(serverId: String) {
        updateServerStatuses { current -> current.toMutableMap().apply { remove(serverId) } }
        AppLogger.d(TAG, "服务器状态已删除: $serverId")
    }

    /**
     * 获取服务器状态
     */
    fun getServerStatus(serverId: String): ServerStatus? {
        return _serverStatus.value[serverId]
    }

    /**
     * 获取所有服务器状态
     */
    fun getAllServerStatus(): Map<String, ServerStatus> {
        return _serverStatus.value.toMap()
    }

    /**
     * 基于时间戳推断服务是否处于运行态（近似状态，不是实时状态）
     */
    fun isServerLikelyRunning(serverId: String): Boolean {
        val status = _serverStatus.value[serverId] ?: return false
        return status.lastStartTime > 0L && status.lastStartTime >= status.lastStopTime
    }

    /**
     * 检查服务器是否启用
     * 本地插件从 mcpServers.disabled 读取；远程插件从 pluginMetadata.disabled 读取
     */
    fun isServerEnabled(serverId: String): Boolean {
        val serverConfig = getMCPServer(serverId)
        if (serverConfig != null) {
            return serverConfig.disabled != true // disabled=true 表示禁用
        }

        val metadata = getPluginMetadata(serverId)
        if (metadata?.type == "remote") {
            return metadata.disabled != true // disabled=true 表示禁用
        }

        return true
    }

    /**
     * 设置服务器启用状态
     * 本地插件写入 mcpServers.disabled；远程插件写入 pluginMetadata.disabled
     */
    suspend fun setServerEnabled(serverId: String, enabled: Boolean) {
        updateMcpConfiguration { current ->
            val server = current.mcpServers[serverId]
            val metadata = current.pluginMetadata[serverId]
            when {
                server != null -> current.copy(mcpServers = current.mcpServers.toMutableMap().apply {
                    this[serverId] = server.copy(disabled = !enabled)
                })
                metadata?.type == "remote" -> current.copy(pluginMetadata = current.pluginMetadata.toMutableMap().apply {
                    this[serverId] = metadata.copy(disabled = !enabled)
                })
                else -> throw IllegalStateException("MCP server does not exist")
            }
        }
    }

    fun getPluginRuntimeDirectory(pluginId: String): String {
        val pluginHomeDir = "~/mcp_plugins"
        return "$pluginHomeDir/${pluginId.split("/").last()}"
    }

    private fun getPluginCommandName(pluginId: String): String? {
        return getMCPServer(pluginId)?.command
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.lowercase(Locale.ROOT)
    }

    private fun pluginRuntimeRequiresFiles(pluginId: String): Boolean {
        return when (getPluginCommandName(pluginId)) {
            "npx", "uvx", "uv" -> false
            else -> true
        }
    }

    /**
     * 检查插件运行目录是否已就绪
     * 对于 npx/uvx/uv 类型：目录存在即可，允许为空目录
     * 对于普通本地插件：目录存在且至少包含一个文件
     */
    suspend fun isPluginRuntimeReady(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = getPluginMetadata(pluginId)

            if (metadata?.type == "remote") {
                AppLogger.d(TAG, "插件 $pluginId 是远程服务，运行目录视为已就绪")
                return@withContext true
            }

            val pluginDir = getPluginRuntimeDirectory(pluginId)
            val toolHandler = AIToolHandler.getInstance(context)
            val checkExistsTool = AITool(
                name = "file_exists",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val existsResult = toolHandler.executeTool(checkExistsTool)
            val dirExists = existsResult.success && existsResult.result is FileExistsData && 
                            existsResult.result.exists
            
            if (!dirExists) {
                AppLogger.d(TAG, "插件 $pluginId 运行目录不存在: $pluginDir")
                return@withContext false
            }

            if (!pluginRuntimeRequiresFiles(pluginId)) {
                AppLogger.d(TAG, "插件 $pluginId 运行目录已就绪: $pluginDir (允许空目录)")
                return@withContext true
            }

            val listFilesTool = AITool(
                name = "list_files",
                parameters = listOf(
                    ToolParameter("path", pluginDir),
                    ToolParameter("environment", "linux")
                )
            )
            
            val listResult = toolHandler.executeTool(listFilesTool)
            val hasFiles = if (listResult.success && listResult.result is DirectoryListingData) {
                val listing = listResult.result
                listing.entries.isNotEmpty()
            } else {
                false
            }

            AppLogger.d(TAG, "插件 $pluginId 运行目录检查: $hasFiles (路径: $pluginDir, 包含${if (hasFiles) "有" else "无"}文件)")
            return@withContext hasFiles
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "检查插件运行目录状态时出错: $pluginId", e)
            return@withContext false
        }
    }

    // ==================== 兼容性方法 ====================

    /**
     * 获取插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空对象
     */
    fun getPluginConfig(pluginId: String): String {
        val serverConfig = getMCPServer(pluginId)
        return if (serverConfig != null) {
            val configForOnePlugin = MCPConfig(
                mcpServers = mutableMapOf(pluginId to serverConfig)
            )
            gson.toJson(configForOnePlugin)
        } else {
            gson.toJson(MCPConfig())
        }
    }

    /**
     * 保存插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串，可以是完整的MCPConfig或单个ServerConfig
     * @return 是否保存成功
     */
    suspend fun savePluginConfig(pluginId: String, config: String, expectedConfig: String? = null): Boolean {
        return try {
            val parsedServerConfig = parseMcpPluginConfig(pluginId, config)
            val serverConfig = sanitizeServerConfig(pluginId, parsedServerConfig, "savePluginConfig")
                ?: return false
            
            val expected = expectedConfig?.let { parseMcpPluginConfigSnapshot(pluginId, it) }
            updateMcpConfiguration { currentConfig ->
                if (expectedConfig != null) {
                    val latest = currentConfig.mcpServers[pluginId]
                    check(latest == expected || latest == serverConfig) { "Plugin configuration changed elsewhere" }
                }
                val newServers = currentConfig.mcpServers.toMutableMap()
                newServers[pluginId] = serverConfig
                currentConfig.copy(mcpServers = newServers)
            }

            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存插件配置失败: $pluginId", e)
            false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 导出配置为JSON字符串
     */
    fun exportConfigAsJson(): String {
        val exportData = mapOf(
            "mcpConfig" to _mcpConfig.value,
            "serverStatus" to _serverStatus.value,
            "exportTime" to System.currentTimeMillis(),
            "version" to "1.0"
        )
        return gson.toJson(exportData)
    }

    /**
     * 从JSON字符串导入配置
     */
    suspend fun importConfigFromJson(json: String): Boolean {
        return try {
            val typeToken = object : TypeToken<Map<String, Any>>() {}.type
            val importData = gson.fromJson<Map<String, Any>>(json, typeToken)
            
            importData["mcpConfig"]?.let { config ->
                val configJson = gson.toJson(config)
                val rawMcpConfig = gson.fromJson(configJson, MCPConfig::class.java) ?: MCPConfig()
                val sanitizedConfig = sanitizeMCPConfig(rawMcpConfig, "importConfigFromJson")
                updateMcpConfiguration { autoFillMissingMetadata(sanitizedConfig.config) }

            }
            
            importData["serverStatus"]?.let { status ->
                val statusJson = gson.toJson(status)
                val typeToken3 = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val serverStatus = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken3)
                updateServerStatuses { serverStatus }
            }
            
            AppLogger.d(TAG, "配置导入成功")
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "导入配置失败", e)
            false
        }
    }

    /**
     * 获取配置目录路径
     */
    fun getConfigDirectory(): String = configBaseDir.absolutePath

    /**
     * 清理无效配置
     */
    suspend fun cleanupInvalidConfigurations() {
        try {
            updateMcpConfiguration { current ->
                current.copy(mcpServers = current.mcpServers.filterKeys { it in current.pluginMetadata }.toMutableMap())
            }
            updateServerStatuses { current ->
                val validPluginIds = _mcpConfig.value.pluginMetadata.keys
                current.filterKeys { it in validPluginIds }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理配置时出错", e)
        }
    }
}

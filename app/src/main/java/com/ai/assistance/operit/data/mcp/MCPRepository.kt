package com.ai.assistance.operit.data.mcp

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.data.mcp.plugins.MCPConfigGenerator
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.kiyori.platform.network.KiyoriNetworkModule
import com.kiyori.platform.network.KiyoriNetworkProxyManager

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * 统一的MCP仓库管理类
 * 
 * 职责：
 * - 管理MCP服务器的UI状态和数据
 * - 处理插件的安装、卸载
 * - 管理已安装插件的状态跟踪
 * - 处理远程服务器的添加和管理
 * 
 * 配置管理由MCPLocalServer单独处理
 */
class MCPRepository(private val context: Context) {
    private val mcpLocalServer = MCPLocalServer.getInstance(context)

    companion object {
        private const val TAG = "MCPRepository"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 15000
    }

    // UI状态管理
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<MCPLocalServer.PluginMetadata>>(emptyList())
    val mcpServers: StateFlow<List<MCPLocalServer.PluginMetadata>> = _mcpServers.asStateFlow()

    // 已安装插件ID管理
    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    // 插件安装目录
    private val pluginsBaseDir by lazy {
        OperitPaths.mcpPluginsDir()
    }

    init {
        loadPluginsFromMCPLocalServer()
        
    }

    /** 只有实际需要实时列表的界面订阅；观察协程随调用者生命周期结束。 */
    fun observeConfiguration(scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        mcpLocalServer.mcpConfig.collect { loadPluginsFromMCPLocalServer() }
    }

    fun refreshInstalledPlugins() {
        loadPluginsFromMCPLocalServer()
    }

    // ==================== 插件状态管理 ====================

    /**
     * 从MCPLocalServer加载插件信息（主要数据源）
     */
    @Synchronized
    private fun loadPluginsFromMCPLocalServer() {
        try {
            val pluginMetadata = mcpLocalServer.getAllPluginMetadata()

            // 构建插件列表
            val servers = mutableListOf<MCPLocalServer.PluginMetadata>()
            val installedIds = mutableSetOf<String>()

            pluginMetadata.values.forEach { metadata ->
                // 统一检查：根据 command 判断是否需要物理安装
                val isInstalled = if (metadata.type == "remote") {
                    true // 远程服务器
                } else {
                    isPluginPhysicallyInstalled(metadata.id) // 自动处理 npx/uvx/uv
                }

                if (isInstalled) {
                    installedIds.add(metadata.id)
                }

                // 创建更新的metadata，确保isInstalled字段正确
                val updatedMetadata = metadata.copy(isInstalled = isInstalled)
                servers.add(updatedMetadata)
            }

            // 补充扫描 mcp_plugins 目录中的本地插件（即使它们尚未写入 JSON 配置）
            val physicallyInstalledIds = scanPhysicallyInstalledPlugins()
            installedIds.addAll(physicallyInstalledIds)

            val missingMetadataPluginIds = physicallyInstalledIds - pluginMetadata.keys
            if (missingMetadataPluginIds.isNotEmpty()) {
                AppLogger.d(
                    TAG,
                    "发现 ${missingMetadataPluginIds.size} 个仅存在于 mcp_plugins 的插件: ${missingMetadataPluginIds.joinToString()}"
                )
            }

            missingMetadataPluginIds.forEach { pluginId ->
                servers.add(
                    MCPLocalServer.PluginMetadata(
                        id = pluginId,
                        name = pluginId,
                        description = context.getString(R.string.local_installed_plugin),
                        author = context.getString(R.string.local_installation),
                        isInstalled = true,
                        version = context.getString(R.string.local_version),
                        longDescription = context.getString(R.string.local_installed_plugin),
                        type = "local"
                    )
                )
            }

            _mcpServers.value = servers.sortedBy { it.name }
            _installedPluginIds.value = installedIds

        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "从MCPLocalServer加载插件失败", e)
        }
    }

    /**
     * 扫描文件系统中实际安装的插件（辅助验证）
     */
    private fun scanPhysicallyInstalledPlugins(): Set<String> {
        val installedIds = mutableSetOf<String>()
        try {
            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (!pluginDir.name.startsWith(".mcp_") && pluginDir.isDirectory && isPluginPhysicallyInstalled(pluginDir.name)) {
                        installedIds.add(pluginDir.name)
                    }
                }
            }
            AppLogger.d(TAG, "文件系统扫描到已安装插件: ${installedIds.size}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "扫描文件系统插件失败", e)
        }
        return installedIds
    }

    /**
     * 判断插件是否需要物理安装（npx/uvx/uv/remote 类型不需要）
     */
    private fun needsPhysicalInstallation(serverId: String): Boolean {
        val serverConfig = mcpLocalServer.getMCPServer(serverId)
        val command = serverConfig?.command?.lowercase() ?: return true
        
        return commandNeedsPhysicalInstallation(command)
    }
    
    /**
     * 判断命令类型是否需要物理安装
     * @param command 命令字符串（小写）
     * @return true 如果需要物理安装，false 如果是 npx/uvx/uv 等不需要物理安装的命令
     */
    private fun commandNeedsPhysicalInstallation(command: String): Boolean {
        // npx、uvx、uv、remote 类型的插件不需要物理安装
        return when (command) {
            "npx" -> false
            "uvx" -> false
            "uv" -> false
            else -> true
        }
    }
    
    /**
     * 检查 JSON 配置中的所有服务器是否需要物理安装
     * @param jsonConfig JSON 配置字符串
     * @return true 如果至少有一个服务器需要物理安装，false 如果所有服务器都不需要物理安装
     */
    fun checkConfigNeedsPhysicalInstallation(jsonConfig: String): Boolean {
        try {
            val jsonElement = Json.parseToJsonElement(jsonConfig)
            val mcpServersObject = jsonElement.jsonObject["mcpServers"]?.jsonObject
            
            if (mcpServersObject == null) {
                AppLogger.w(TAG, "No mcpServers found in config, assuming needs installation")
                return true
            }
            
            // 检查每个服务器的 command
            for ((serverId, serverConfigElement) in mcpServersObject) {
                val serverConfig = serverConfigElement.jsonObject
                val command = serverConfig["command"]?.toString()?.trim('"')?.lowercase() ?: return true
                
                if (commandNeedsPhysicalInstallation(command)) {
                    AppLogger.d(TAG, "Server $serverId with command '$command' requires physical installation")
                    return true
                }
            }
            
            // 所有命令都是 npx/uvx/uv，不需要物理安装
            AppLogger.d(TAG, "All commands in config are npx/uvx/uv, no physical installation needed")
            return false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking if config needs physical installation", e)
            return true
        }
    }

    /**
     * 检查插件是否在文件系统中物理存在
     */
    private fun isPluginPhysicallyInstalled(serverId: String): Boolean {
        // 如果不需要物理安装，直接返回 true
        if (!needsPhysicalInstallation(serverId)) {
            return true
        }
        
        return getInstalledPluginPath(serverId) != null
    }

    /**
     * 检查插件是否已安装（优先从MCPLocalServer检查）
     */
    fun isPluginInstalled(serverId: String): Boolean {
        val metadata = mcpLocalServer.getPluginMetadata(serverId)
        return if (metadata == null) {
            false // 没有元数据记录
        } else if (metadata.type == "remote") {
            true // 远程服务器配置后即为已安装
        } else {
            isPluginPhysicallyInstalled(serverId) // 自动处理 npx/uvx/uv
        }
    }

    /**
     * 获取已安装插件的路径
     */
    fun getInstalledPluginPath(serverId: String): String? {
        // 对于 npx/uvx/uv 类型的插件，返回一个虚拟路径标记
        if (!needsPhysicalInstallation(serverId)) {
            return "virtual://$serverId"
        }
        
        val pluginDir = try { resolveMcpPluginDirectory(pluginsBaseDir, serverId) } catch (_: IllegalArgumentException) { return null }
        if (!pluginDir.exists() || !pluginDir.isDirectory) return null

        return try {
            resolveMcpProjectDirectory(pluginDir, mcpLocalServer.getPluginMetadata(serverId)?.installedPath)?.path
        } catch (_: Exception) {
            AppLogger.e(TAG, "MCP项目目录读取失败")
            null
        }
    }

    // ==================== 插件安装功能 ====================

    /**
     * 安装MCP插件
     */
    suspend fun installMCPServer(pluginId: String, progressCallback: (InstallProgress) -> Unit = {}): InstallResult {
        val metadata = mcpLocalServer.getPluginMetadata(pluginId)
            ?: return InstallResult.Error(context.getString(R.string.mcp_repository_server_not_found))
        return installMCPServerWithObject(metadata, progressCallback)
    }

    suspend fun installMCPServerWithObject(server: MCPLocalServer.PluginMetadata, progressCallback: (InstallProgress) -> Unit = {}): InstallResult =
        installPreparedArchive(server, progressCallback) {
            val (owner, repo) = requireNotNull(extractOwnerAndRepo(server.repoUrl))
            checkNotNull(downloadRepositoryZip(owner, repo, server.id, progressCallback))
        }

    suspend fun installMCPServerFromZip(
        serverId: String, zipUri: Uri, name: String, description: String, author: String,
        progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult = installPreparedArchive(
        MCPLocalServer.PluginMetadata(serverId, name, description, author = author), progressCallback
    ) {
        val archive = File.createTempFile("mcp_local_", ".zip", context.cacheDir)
        try {
            val coroutine = currentCoroutineContext()
            checkNotNull(context.contentResolver.openInputStream(zipUri)).use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutine.ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                    }
                }
            }
            archive
        } catch (failure: Exception) {
            archive.delete()
            throw failure
        }
    }

    private suspend fun installPreparedArchive(
        server: MCPLocalServer.PluginMetadata,
        progressCallback: (InstallProgress) -> Unit,
        download: suspend () -> File
    ): InstallResult = withContext(Dispatchers.IO) {
        mcpLocalServer.withPluginFilesMutation {
            var archive: File? = null
            var staging: File? = null
            var published = false
            try {
                resolveMcpPluginDirectory(pluginsBaseDir, server.id)
                check(mcpLocalServer.getPluginMetadata(server.id)?.type != "remote")
                progressCallback(InstallProgress.Preparing)
                archive = download()
                staging = java.nio.file.Files.createTempDirectory(pluginsBaseDir.toPath(), ".mcp_install_").toFile()
                val prepared = staging
                val coroutine = currentCoroutineContext()
                extractMcpPluginZip(archive, prepared, { coroutine.ensureActive() }) { progressCallback(InstallProgress.Extracting(it)) }
                val entries = checkNotNull(prepared.listFiles())
                check(entries.isNotEmpty())
                // 单目录包装的仓库使用该目录；根目录有文件则根目录本身就是项目。
                val relativeMain = if (entries.size == 1 && entries.single().isDirectory) entries.single().name else ""
                coroutine.ensureActive()
                withContext(NonCancellable) {
                    check(mcpLocalServer.getPluginMetadata(server.id)?.type != "remote")
                    val target = publishMcpPluginDirectory(prepared, pluginsBaseDir, server.id) { published = true }
                    val mainDir = if (relativeMain.isEmpty()) target else File(target, relativeMain)
                    savePluginMetadata(server, mainDir.path)
                    loadPluginsFromMCPLocalServer()
                    progressCallback(InstallProgress.Finished)
                    InstallResult.Success(mainDir.path)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                InstallResult.Error(context.getString(if (published) R.string.mcp_install_metadata_failed else R.string.mcp_install_incomplete))
            } finally {
                archive?.let { if (it.exists() && !it.delete()) AppLogger.w(TAG, "MCP临时下载文件清理失败") }
                staging?.let { directory ->
                    try { deleteMcpPluginTree(directory) }
                    catch (_: Exception) { AppLogger.w(TAG, "MCP临时解压目录清理失败") }
                }
            }
        }
    }

    suspend fun uninstallMCPServer(pluginId: String): Boolean = withContext(Dispatchers.IO) {
        mcpLocalServer.withPluginFilesMutation {
            try {
                val directory = resolveMcpPluginDirectory(pluginsBaseDir, pluginId)
                deleteMcpPluginTree(directory)
                mcpLocalServer.removeMCPServer(pluginId)
                loadPluginsFromMCPLocalServer()
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                AppLogger.e(TAG, "MCP卸载未完成")
                false
            }
        }
    }

    // ==================== 下载和解压工具方法 ====================

    /**
     * 下载仓库ZIP文件
     */
    private suspend fun downloadRepositoryZip(
        owner: String,
        repoName: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val defaultBranch = getGithubDefaultBranch(owner, repoName)

        if (defaultBranch == null) {
            AppLogger.e(TAG, "无法确定 $owner/$repoName 的默认分支，下载失败")
            return@withContext null
        }
        
        val zipUrl = "https://github.com/$owner/$repoName/archive/refs/heads/$defaultBranch.zip"
        AppLogger.d(TAG, "从确定的默认分支 '$defaultBranch' 下载: $zipUrl")
            
            val file = downloadFromUrl(zipUrl, serverId, progressCallback)
            if (file != null && file.exists() && file.length() > 0) {
            AppLogger.d(TAG, "从默认分支 '$defaultBranch' 下载成功")
                return@withContext file
            }
        
        file?.let { if (it.exists() && !it.delete()) AppLogger.w(TAG, "MCP空下载文件清理失败") }
        AppLogger.e(TAG, "从默认分支 '$defaultBranch' 下载失败")
        null
    }

    /**
     * 使用 GitHub API 获取仓库的默认分支
     */
    private suspend fun getGithubDefaultBranch(owner: String, repoName: String): String? = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        AppLogger.d(TAG, "从 GitHub API 获取仓库信息: $apiUrl")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            val active =
                KiyoriNetworkProxyManager.getInstance(context)
                    .openConnectionBlocking(url, KiyoriNetworkModule.APP_SERVICES) as HttpURLConnection
            connection = active
            active.requestMethod = "GET"
            active.setRequestProperty("Accept", "application/vnd.github.v3+json")
            active.connectTimeout = CONNECT_TIMEOUT
            active.readTimeout = READ_TIMEOUT

            if (active.responseCode == HttpURLConnection.HTTP_OK) {
                val response = active.inputStream.bufferedReader().use { it.readText() }

                val jsonObject = JsonParser.parseString(response).asJsonObject
                val defaultBranch = jsonObject.get("default_branch")?.asString

                if (!defaultBranch.isNullOrBlank()) {
                    AppLogger.d(TAG, "找到 $owner/$repoName 的默认分支: $defaultBranch")
                    return@withContext defaultBranch
                } else {
                    AppLogger.e(TAG, "在 $owner/$repoName 的 API 响应中找不到 'default_branch'")
                }
            } else {
                AppLogger.e(TAG, "GitHub API 请求失败，响应码: ${active.responseCode}，URL: $apiUrl")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取 $owner/$repoName 的默认分支时出错", e)
        } finally { connection?.disconnect() }
        null
    }

    /**
     * 从URL下载文件
     */
    private suspend fun downloadFromUrl(
        zipUrl: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("mcp_repo_", ".zip", context.cacheDir)
        var connection: HttpURLConnection? = null
        var completed = false
        try {
            val active = KiyoriNetworkProxyManager.getInstance(context)
                .openConnectionBlocking(URL(zipUrl), KiyoriNetworkModule.DOWNLOADS) as HttpURLConnection
            connection = active
            active.connectTimeout = CONNECT_TIMEOUT
            active.readTimeout = READ_TIMEOUT
            active.connect()
            check(active.responseCode == HttpURLConnection.HTTP_OK)
            val contentLength = active.contentLengthLong
            val coroutine = currentCoroutineContext()
            active.inputStream.buffered().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    var reported = -2
                    while (true) {
                        coroutine.ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                        total += size
                        val progress = if (contentLength > 0) ((total * 100 / contentLength).coerceIn(0, 100)).toInt() else -1
                        if (progress != reported) { progressCallback(InstallProgress.Downloading(progress)); reported = progress }
                    }
                    check(contentLength < 0 || total == contentLength)
                }
            }
            coroutine.ensureActive()
            completed = true
            tempFile
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            AppLogger.e(TAG, "MCP ZIP下载未完成")
            null
        } finally {
            connection?.disconnect()
            if (!completed && tempFile.exists() && !tempFile.delete()) AppLogger.w(TAG, "MCP临时下载文件清理失败")
        }
    }

    private fun extractOwnerAndRepo(repoUrl: String): Pair<String, String>? {
        val regex = "(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)(?:\\.git)?/?.*".toRegex()
        val matchResult = regex.find(repoUrl)
        
        if (matchResult != null && matchResult.groupValues.size >= 3) {
            val owner = matchResult.groupValues[1]
            val repo = matchResult.groupValues[2]
        
            if (owner.isNotBlank() && repo.isNotBlank()) {
                return owner to repo
            }
        }
        
        return null
    }

    /**
     * 保存插件元数据到MCPLocalServer
     */
    private suspend fun savePluginMetadata(server: MCPLocalServer.PluginMetadata, pluginPath: String) {
        val metadata = server.copy(
            type = "local",
            installedPath = pluginPath,
            installedTime = System.currentTimeMillis()
        )
        
        mcpLocalServer.recordPluginInstallation(metadata)
    }
    // ==================== 远程服务器管理 ====================

    /**
     * 添加远程服务器
     */
    suspend fun addRemoteServer(server: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            require(server.type == "remote")
            validateMcpRemoteEndpoint(server.endpoint, server.connectionType)
            validateMcpHeaders(server.headers.orEmpty().toList())

            // For remote servers, we no longer create a local process.
            // We just store the metadata. The bridge will handle the connection.

            // 保存远程服务器元数据
            val metadata = server.copy(
                type = "remote",
                installedTime = System.currentTimeMillis()
            )
            
            AppLogger.d(TAG, "添加远程服务器: ${server.id}")
            
            mcpLocalServer.addRemotePlugin(metadata)

            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 更新远程服务器
     */
    suspend fun updateRemoteServer(server: MCPLocalServer.PluginMetadata, original: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            if (server.type == "remote") {
                validateMcpRemoteEndpoint(server.endpoint, server.connectionType)
                validateMcpHeaders(server.headers.orEmpty().toList())
            }
            mcpLocalServer.editPluginMetadata(original, server)
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 删除远程服务器
     */
    suspend fun removeRemoteServer(serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpLocalServer.removeMCPServer(serverId)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
                AppLogger.d(TAG, "远程服务器 $serverId 删除成功")
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "删除远程服务器 $serverId 时出错", e)
                false
            }
        }
    }

    // ==================== 状态同步和管理 ====================

    /**
     * 同步桥接器中服务的实时运行状态
     */
    suspend fun syncBridgeStatus() {
        withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "开始从桥接器同步服务状态...")
            try {
                val bridge = com.ai.assistance.operit.data.mcp.plugins.MCPBridge.getInstance(context)
                val listResponse = bridge.listMcpServices()

                if (listResponse?.optBoolean("success", false) == true) {
                    val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                    val activeServices = mutableSetOf<String>()
                    
                    if (services != null) {
                        for (i in 0 until services.length()) {
                            val service = services.optJSONObject(i)
                            val serviceName = service?.optString("name")
                            val isActive = service?.optBoolean("active", false) ?: false

                            if (!serviceName.isNullOrEmpty()) {
                                val now = System.currentTimeMillis()
                                val wasRunning = mcpLocalServer.isServerLikelyRunning(serviceName)
                                if (isActive) {
                                    activeServices.add(serviceName)
                                }
                                if (isActive && !wasRunning) {
                                    mcpLocalServer.updateServerStatus(
                                        serverId = serviceName,
                                        lastStartTime = now,
                                        errorMessage = ""
                                    )
                                } else if (!isActive && wasRunning) {
                                    mcpLocalServer.updateServerStatus(
                                        serverId = serviceName,
                                        lastStopTime = now
                                    )
                                }
                            }
                        }
                    }
                    
                    // 对于已安装但不在活跃列表中的插件，更新停止时间
                    _installedPluginIds.value.forEach { pluginId ->
                        if (!activeServices.contains(pluginId) && mcpLocalServer.isServerLikelyRunning(pluginId)) {
                            mcpLocalServer.updateServerStatus(
                                serverId = pluginId,
                                lastStopTime = System.currentTimeMillis()
                            )
                        }
                    }
                    AppLogger.d(TAG, "桥接器状态同步完成。活跃服务: ${activeServices.joinToString()}")
                } else {
                    AppLogger.w(TAG, "从桥接器获取服务列表失败")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "同步桥接器状态时出错", e)
            }
        }
    }

    /**
     * 同步已安装状态
     */
    suspend fun syncInstalledStatus() {
        withContext(Dispatchers.IO) {
            try {
                // 重新从MCPLocalServer加载插件信息
                loadPluginsFromMCPLocalServer()
                AppLogger.d(TAG, "同步插件安装状态完成，${_installedPluginIds.value.size} 个已安装插件")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "同步安装状态失败", e)
            }
        }
    }
    /**
     * 初始化仓库
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 获取已安装插件的信息
     */
    fun getInstalledPluginInfo(pluginId: String): MCPLocalServer.PluginMetadata? {
        return mcpLocalServer.getPluginMetadata(pluginId)
    }

    suspend fun generatePluginDescription(
        pluginId: String,
        pluginName: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val metadata =
                    mcpLocalServer.getPluginMetadata(pluginId)
                        ?: return@withContext Result.failure(
                            IllegalStateException(context.getString(R.string.mcp_repository_server_not_found))
                        )

                val toolDescriptions = collectToolDescriptionsForDescriptionGeneration(metadata)
                if (toolDescriptions.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.mcp_regenerate_description_no_tools))
                    )
                }

                val targetPluginName = pluginName.trim().ifBlank { metadata.name }
                val generatedDescription =
                    EnhancedAIService.generatePackageDescription(
                        context = context,
                        pluginName = targetPluginName,
                        toolDescriptions = toolDescriptions
                    ).trim()

                if (generatedDescription.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.mcp_regenerate_description_empty))
                    )
                }

                Result.success(generatedDescription)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "重新生成插件描述失败: $pluginId", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun collectToolDescriptionsForDescriptionGeneration(
        metadata: MCPLocalServer.PluginMetadata
    ): List<String> {
        val cachedToolDescriptions =
            mcpLocalServer.getCachedTools(metadata.id)
                .orEmpty()
                .mapNotNull { cachedTool ->
                    val toolName = cachedTool.name.trim()
                    if (toolName.isEmpty()) {
                        null
                    } else {
                        cachedTool.description.trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { "$toolName: $it" }
                            ?: toolName
                    }
                }
        if (cachedToolDescriptions.isNotEmpty()) {
            return cachedToolDescriptions
        }

        val serviceName =
            when (metadata.type) {
                "remote" -> metadata.name.toServiceName(metadata.id)
                else -> {
                    val pluginConfig = mcpLocalServer.getPluginConfig(metadata.id)
                    MCPConfigGenerator().extractServerNameFromConfig(pluginConfig)
                        ?: metadata.id.substringAfterLast('/').lowercase()
                }
            }

        return MCPBridgeClient(context, serviceName).getToolDescriptions()
    }

    private fun String.toServiceName(pluginId: String): String {
        return trim()
            .replace(" ", "_")
            .lowercase()
            .ifBlank { pluginId.substringAfterLast('/').lowercase() }
    }

    /**
     * 手动刷新插件列表
     * 会重新加载配置文件，自动识别新添加的 mcpServers 配置
     */
    suspend fun refreshPluginList() {
        withContext(Dispatchers.IO) {
            // 重新加载配置文件（会自动识别新的 mcpServers 配置并创建元数据）
            mcpLocalServer.reloadConfigurations()
            // 重新加载插件列表
            loadPluginsFromMCPLocalServer()
            AppLogger.d(TAG, "插件列表已刷新")
        }
    }

    /**
     * 为加载成功的插件注册工具
     * 优先使用本地缓存的工具信息，避免重复连接服务
     *
     * @param successfulPluginIds 加载成功的插件ID列表
     */
    internal fun registerToolsForLoadedPlugins(successfulPluginIds: List<String>, expectedRegistrations: Map<String, McpPluginRuntimeIdentity>) {
        if (successfulPluginIds.isEmpty()) {
            AppLogger.d(TAG, "没有成功加载的插件，无需注册工具")
            return
        }

        AppLogger.d(TAG, "开始为 ${successfulPluginIds.size} 个插件注册工具: ${successfulPluginIds.joinToString()}")

        val mcpManager = MCPManager.getInstance(context)
        val toolHandler = AIToolHandler.getInstance(context)
        val mcpToolExecutor = MCPToolExecutor(context, mcpManager)

        successfulPluginIds.forEach { pluginId ->
            try {
                AppLogger.d(TAG, "正在为插件 $pluginId 注册工具...")

                val registration = expectedRegistrations[pluginId] ?: return@forEach
                val pluginMetadata = mcpLocalServer.getPluginMetadata(pluginId)
                if (pluginMetadata == null) {
                    AppLogger.w(TAG, "在MCPLocalServer中找不到插件 $pluginId 的元数据")
                    return@forEach
                }

                // 统一注册服务器，无论是缓存还是动态
                val serverConfig = MCPServerConfig(
                    name = pluginId,
                    endpoint = if (pluginMetadata.type == "remote") pluginMetadata.endpoint ?: "" else "mcp://plugin/$pluginId",
                    description = pluginMetadata.description,
                    capabilities = listOf("tools"),
                    extraData = emptyMap()
                )
                // 发现阶段不向运行时发布；禁用或编辑可以在网络请求期间立即使结果失效。
                val toolsToRegister = getToolsForPlugin(pluginId, serverConfig)

                if (toolsToRegister.isEmpty()) {
                    AppLogger.w(TAG, "插件 $pluginId 没有可注册的工具")
                    return@forEach
                }

                val published = mcpLocalServer.publishPluginRegistration(pluginId, registration) {
                    // 覆盖旧工具集，移除服务端已撤回的工具与描述。
                    unregisterMcpRuntimeTools(context, pluginId)
                    mcpManager.registerServer(pluginId, serverConfig)
                    toolsToRegister.forEach { toolInfo ->
                        toolHandler.registerTool(
                            name = "$pluginId:${toolInfo.name}",
                            executor = mcpToolExecutor,
                            descriptionGenerator = { tool ->
                                val parameters = tool.parameters.joinToString(", ") { "${it.name}='${it.value}'" }
                                toolInfo.description + if (parameters.isEmpty()) "" else "\nParameters: $parameters"
                            }
                        )
                    }
                }
                if (!published) {
                    AppLogger.d(TAG, "Discarded MCP discovery after configuration changed: $pluginId")
                    return@forEach
                }
                AppLogger.d(TAG, "插件 $pluginId 的工具注册完成，共 ${toolsToRegister.size} 个")

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "为插件 $pluginId 注册工具时发生异常", e)
            }
        }
        AppLogger.d(TAG, "所有插件的工具注册流程完成")
    }

    /**
     * 反注册插件对应的运行时服务器与工具，避免禁用后仍出现在系统提示词。
     */
    fun unregisterToolsForPlugins(pluginIds: List<String>) {
        if (pluginIds.isEmpty()) return

        val mcpManager = MCPManager.getInstance(context)
        val toolHandler = AIToolHandler.getInstance(context)

        pluginIds.forEach { pluginId ->
            try {
                val toolPrefix = "$pluginId:"
                val toolNamesToRemove = toolHandler.getAllToolNames().filter { it.startsWith(toolPrefix) }
                toolNamesToRemove.forEach { toolName ->
                    toolHandler.unregisterTool(toolName)
                }

                val serverNamesToRemove = mutableSetOf(pluginId)
                val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                if (pluginConfig.isNotBlank()) {
                    runCatching {
                        val root = JsonParser.parseString(pluginConfig).asJsonObject
                        root.getAsJsonObject("mcpServers")?.keySet()?.forEach { serverName ->
                            serverNamesToRemove.add(serverName)
                        }
                    }.onFailure { e ->
                        AppLogger.w(TAG, "Failed to parse plugin config for $pluginId: ${e.message}")
                    }
                }

                serverNamesToRemove.forEach { serverName ->
                    mcpManager.unregisterServer(serverName)
                }

                AppLogger.d(
                    TAG,
                    "Runtime MCP entries removed for $pluginId, tools=${toolNamesToRemove.size}, servers=${serverNamesToRemove.size}"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to unregister runtime MCP entries for $pluginId", e)
            }
        }
    }

    private fun getToolsForPlugin(pluginId: String, serverConfig: MCPServerConfig): List<UnifiedToolInfo> {
        // 1. 检查缓存
        val cachedTools = mcpLocalServer.getCachedTools(pluginId)
        if (cachedTools != null && cachedTools.isNotEmpty()) {
            AppLogger.d(TAG, "从缓存为插件 $pluginId 获取了 ${cachedTools.size} 个工具")
            return cachedTools.map {
                UnifiedToolInfo(it.name, it.description, it.inputSchema)
            }
        }

        // 2. 如果没有缓存，动态获取
        AppLogger.d(TAG, "插件 $pluginId 无工具缓存，使用动态连接方式获取")
        val mcpLoadResult = MCPPackage.loadFromServer(context, serverConfig)
        val mcpPackage = mcpLoadResult.mcpPackage
        if (mcpPackage == null) {
            AppLogger.w(
                TAG,
                "无法从服务器 $pluginId 获取MCP包: ${mcpLoadResult.errorMessage ?: "unknown reason"}"
            )
            return emptyList()
        }

        val toolPackage = mcpPackage.toToolPackage()
        return toolPackage.tools.map {
            val schemaParams = it.parameters.map { param ->
                mapOf(
                    "name" to param.name,
                    "description" to param.description.resolve(context),
                    "type" to param.type,
                    "required" to param.required
                )
            }
            UnifiedToolInfo(
                name = it.name,
                description = it.description.resolve(context),
                inputSchema = Gson().toJson(schemaParams) // 假设 MCPToolExecutor 可以处理
            )
        }
    }
}

// ==================== 数据类定义 ====================

/** 统一的工具信息数据类 */
private data class UnifiedToolInfo(
    val name: String,
    val description: String,
    val inputSchema: String
)

/** 安装进度状态 */
sealed class InstallProgress {
    object Preparing : InstallProgress()
    data class Downloading(val progress: Int) : InstallProgress() // -1 表示未知进度
    data class Extracting(val progress: Int) : InstallProgress() // -1 表示未知进度
    object Finished : InstallProgress()
}

/** 安装结果 */
sealed class InstallResult {
    data class Success(val pluginPath: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}

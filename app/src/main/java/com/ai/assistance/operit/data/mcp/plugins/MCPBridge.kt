package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import com.ai.assistance.operit.util.PortProcessKiller
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MCPBridge - 用于与TCP桥接器通信的插件类 支持以下命令:
 * - spawn: 启动新的MCP服务
 * - shutdown: 关闭当前MCP服务
 * - listtools: 列出所有可用工具
 * - toolcall: 调用特定工具
 * - list: 列出已注册的MCP服务或查询单个服务状态
 * - register: 注册新的MCP服务
 * - unregister: 取消注册MCP服务
 * - reset: 重置桥接器
 */
class MCPBridge private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPBridge"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val BRIDGE_PORT = 8752  // 远程bridge监听的端口
        private const val CLIENT_PORT = 8751  // Android客户端连接的端口（SSH转发）
        private const val TERMUX_BRIDGE_PATH = "~/bridge"
        private const val START_COMMAND_THROTTLE_MS = 4000L
        private const val COMMAND_CONNECTION_KEEP_MS = 3500L
        private const val DETECT_PORT_CACHE_MS = 3500L
        private var appContext: Context? = null

        private val startBridgeMutex = Mutex()

        private val commandConnectionMutex = Mutex()
        private val commandConnectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var commandConnection: MCPBridgeConnection? = null

        @Volatile
        private var commandLastUsedAtMs: Long = 0L

        @Volatile
        private var commandCloseJob: Job? = null

        private val detectPortMutex = Mutex()

        @Volatile
        private var cachedDetectedPort: Int? = null

        @Volatile
        private var cachedDetectedPortAtMs: Long = 0L

        @Volatile
        private var startBridgeDeferred: CompletableDeferred<Boolean>? = null

        @Volatile
        private var lastStartCommandAtMs: Long = 0L
        
        @Volatile
        private var INSTANCE: MCPBridge? = null
        
        fun getInstance(context: Context): MCPBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MCPBridge(context.applicationContext).also { 
                    INSTANCE = it
                    appContext = context.applicationContext
                }
            }
        }
        
        /**
         * 智能检测可用端口
         * 优先尝试 8752（本地直连），失败后尝试 8751（SSH转发）
         */
        private suspend fun detectPort(): Int = withContext(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val cached = cachedDetectedPort
            if (cached != null && nowMs - cachedDetectedPortAtMs <= DETECT_PORT_CACHE_MS) {
                return@withContext cached
            }

            return@withContext detectPortMutex.withLock {
                val lockNowMs = System.currentTimeMillis()
                val lockCached = cachedDetectedPort
                if (lockCached != null && lockNowMs - cachedDetectedPortAtMs <= DETECT_PORT_CACHE_MS) {
                    return@withLock lockCached
                }

                val detectedPort =
                    if (isPortAvailable(DEFAULT_HOST, BRIDGE_PORT)) {
                        AppLogger.d(TAG, "检测到本地环境，使用端口 $BRIDGE_PORT")
                        BRIDGE_PORT
                    } else {
                        AppLogger.d(TAG, "本地连接失败，尝试SSH转发端口 $CLIENT_PORT")
                        CLIENT_PORT
                    }

                cachedDetectedPort = detectedPort
                cachedDetectedPortAtMs = lockNowMs
                return@withLock detectedPort
            }
        }
        
        /**
         * 检查端口是否可用（快速检测，无日志污染）
         */
        private fun isPortAvailable(host: String, port: Int): Boolean {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.reuseAddress = true
                socket.connect(java.net.InetSocketAddress(host, port), 500) // 500ms快速检测
                socket.isConnected
            } catch (e: Exception) {
                false // 静默失败，不记录日志
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // 静默关闭
                }
            }
        }

        private fun closeCommandConnectionLocked() {
            commandConnection?.close()
            commandConnection = null
            commandLastUsedAtMs = 0L
            commandCloseJob?.cancel()
            commandCloseJob = null
        }

        internal suspend fun closeCommandConnection() {
            commandConnectionMutex.withLock { closeCommandConnectionLocked() }
        }

        private fun scheduleCommandConnectionCloseLocked() {
            commandCloseJob?.cancel()
            val expectedLastUsed = commandLastUsedAtMs
            commandCloseJob = commandConnectionScope.launch {
                delay(COMMAND_CONNECTION_KEEP_MS)
                commandConnectionMutex.withLock {
                    if (commandLastUsedAtMs == expectedLastUsed &&
                        System.currentTimeMillis() - commandLastUsedAtMs >= COMMAND_CONNECTION_KEEP_MS
                    ) {
                        closeCommandConnectionLocked()
                    }
                }
            }
        }

        // 部署桥接器到终端
        suspend fun deployBridge(context: Context, sessionId: String? = null): Boolean {
            appContext = context.applicationContext
            return withContext(Dispatchers.IO) {
                try {
                    // 1. 首先将桥接器从 assets 复制到 Kiyori 公共 bridge 目录。
                    val publicBridgeDir = OperitPaths.bridgeDir()

                    // 复制打包后的 index.js 到公共目录（已包含所有依赖）
                    val inputStream = context.assets.open("bridge/index.js")
                    val indexJsContent = inputStream.bufferedReader().use { it.readText() }
                    val outputFile = File(publicBridgeDir, "index.js")
                    outputFile.writeText(indexJsContent)
                    inputStream.close()

                    // 复制打包后的 spawn-helper.js 到公共目录（已包含所有依赖）
                    val spawnHelperInputStream = context.assets.open("bridge/spawn-helper.js")
                    val spawnHelperJsContent = spawnHelperInputStream.bufferedReader().use { it.readText() }
                    val spawnHelperOutputFile = File(publicBridgeDir, "spawn-helper.js")
                    spawnHelperOutputFile.writeText(spawnHelperJsContent)
                    spawnHelperInputStream.close()

                    AppLogger.d(TAG, "桥接器文件已复制到公共目录: ${publicBridgeDir.absolutePath}")

                    // 2. 确保终端目录存在并复制文件
                    // 获取终端管理器
                    val terminal = Terminal.getInstance(context)
                    
                    // 确保已连接到终端服务
                    if (!terminal.isConnected()) {
                        val connected = terminal.initialize()
                        if (!connected) {
                            AppLogger.e(TAG, "无法连接到终端服务")
                            return@withContext false
                        }
                    }

                    // 使用传入的sessionId或创建新的会话
                    val actualSessionId = sessionId ?: run {
                        val newSessionId = terminal.createSession("mcp-bridge-deploy")
                        newSessionId
                    }

                    // 使用sdcard路径而不是Android storage路径
                    val sdcardBridgePath = OperitPaths.bridgePathSdcard()
                    
                    // 获取 AIToolHandler 实例
                    val toolHandler = AIToolHandler.getInstance(context)
                    
                    // 先创建目标目录
                    val mkdirCommand = "mkdir -p $TERMUX_BRIDGE_PATH"
                    terminal.executeCommand(actualSessionId, mkdirCommand)
                    delay(100) // 等待目录创建
                    
                    // 使用 AIToolHandler 复制打包后的文件（跨环境复制：Android -> Linux）
                    // 打包后的文件已包含所有依赖，不需要 package.json 和 node_modules
                    val filesToCopy = listOf("index.js", "spawn-helper.js")
                    
                    for (fileName in filesToCopy) {
                        val copyTool = AITool(
                            name = "copy_file",
                            parameters = listOf(
                                ToolParameter("source", "$sdcardBridgePath/$fileName"),
                                ToolParameter("destination", "$TERMUX_BRIDGE_PATH/$fileName"),
                                ToolParameter("source_environment", "android"),
                                ToolParameter("dest_environment", "linux"),
                                ToolParameter("recursive", "false")
                            )
                        )
                        
                        val result = toolHandler.executeTool(copyTool)
                        if (!result.success) {
                            AppLogger.e(TAG, "复制文件 $fileName 失败: ${result.error}")
                            return@withContext false
                        }
                        AppLogger.d(TAG, "成功复制文件: $fileName")
                    }
                    
                    // 打包后的文件已包含所有依赖，无需安装 node_modules

                    AppLogger.d(TAG, "桥接器成功部署到终端")
                    return@withContext true
                } catch (e: Exception) {
                    AppLogger.e(TAG, "部署桥接器异常", e)
                    return@withContext false
                }
            }
        }

        // 在终端中启动桥接器
        suspend fun startBridge(
                context: Context? = null,
                port: Int = BRIDGE_PORT,
                mcpCommand: String? = null,
                mcpArgs: List<String>? = null,
                sessionId: String? = null
        ): Boolean =
                withContext(Dispatchers.IO) {
                    // 使用传入的context或保存的appContext
                    val ctx = context ?: appContext
                    if (ctx == null) {
                        AppLogger.e(TAG, "没有可用的上下文，无法执行命令")
                        return@withContext false
                    }

                    var isLeader = false
                    val deferred = startBridgeMutex.withLock {
                        val inFlight = startBridgeDeferred
                        if (inFlight != null && !inFlight.isCompleted) {
                            inFlight
                        } else {
                            val newDeferred = CompletableDeferred<Boolean>()
                            startBridgeDeferred = newDeferred
                            isLeader = true
                            newDeferred
                        }
                    }

                    if (!isLeader) {
                        return@withContext deferred.await()
                    }

                    try {
                        closeCommandConnection()
                        PortProcessKiller.killListeners(port)
                        cachedDetectedPort = null
                        cachedDetectedPortAtMs = 0L

                        // 获取终端管理器
                        val terminal = Terminal.getInstance(ctx)
                        
                        // 确保已连接到终端服务
                        if (!terminal.isConnected()) {
                            val connected = terminal.initialize()
                            if (!connected) {
                                AppLogger.e(TAG, "无法连接到终端服务")
                                deferred.complete(false)
                                return@withContext false
                            }
                        }

                        // 使用传入的sessionId或创建新的会话
                        val actualSessionId = sessionId ?: run {
                            val newSessionId = terminal.createSession("mcp-bridge-daemon")
                            newSessionId
                        }

                        // 构建启动命令 - 使用后台方式运行
                        val command = StringBuilder("cd $TERMUX_BRIDGE_PATH && node index.js $port")
                        if (mcpCommand != null) {
                            command.append(" $mcpCommand")
                            if (mcpArgs != null && mcpArgs.isNotEmpty()) {
                                command.append(" ${mcpArgs.joinToString(" ")}")
                            }
                        }
                        command.append(" &")

                        val now = System.currentTimeMillis()
                        val shouldSendStartCommand = startBridgeMutex.withLock {
                            val last = lastStartCommandAtMs
                            if (now - last < START_COMMAND_THROTTLE_MS) {
                                false
                            } else {
                                lastStartCommandAtMs = now
                                true
                            }
                        }

                        if (shouldSendStartCommand) {
                            AppLogger.d(TAG, "进行桥接器启动...")
                            terminal.executeCommand(actualSessionId, command.toString())
                        } else {
                            AppLogger.w(TAG, "桥接器启动命令发送过于频繁，跳过本次发送")
                        }

                        // 等待一段时间让桥接器启动
                        AppLogger.d(TAG, "等待桥接器启动...")
                        delay(2000)

                        // 验证桥接器是否成功启动 - 尝试三次
                        var isRunning = false
                        for (i in 1..3) {
                            val checkResult = getInstance(ctx).listMcpServices()
                            if (checkResult != null && checkResult.optBoolean("success", false)) {
                                AppLogger.d(TAG, "桥接器成功启动，服务列表查询成功")
                                isRunning = true
                                break
                            }
                            AppLogger.d(TAG, "第${i}次尝试连接桥接器失败，等待1秒后重试")
                            delay(1000)
                        }

                        // 如果三次尝试后仍然无法ping通，检查日志
                        if (!isRunning) {
                            AppLogger.e(TAG, "桥接器可能未成功启动。请检查终端会话 'mcp-bridge-daemon' 的输出。")
                        }

                        deferred.complete(isRunning)
                        return@withContext isRunning
                    } catch (cancelled: CancellationException) {
                        deferred.completeExceptionally(cancelled)
                        throw cancelled
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "启动桥接器异常", e)
                        deferred.complete(false)
                        return@withContext false
                    } finally {
                        startBridgeMutex.withLock {
                            if (startBridgeDeferred === deferred) {
                                startBridgeDeferred = null
                            }
                        }
                    }
                }

        // 重置桥接器（静态方法）
        suspend fun reset(context: Context? = null): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        AppLogger.d(TAG, "重置桥接器 - 关闭所有服务并清空注册表...")
                        val ctx = context ?: appContext
                        if (ctx == null) {
                            AppLogger.e(TAG, "Cannot reset bridge: context is null")
                            return@withContext null
                        }
                        val response = sendCommand(ctx, MCPBridgeClient.buildResetCommand())
                        if (response?.optBoolean("success", false) == true) {
                            AppLogger.i(TAG, "桥接器重置成功")
                        } else {
                            AppLogger.w(TAG, "桥接器重置失败")
                        }
                        return@withContext response
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "重置桥接器异常", e)
                        return@withContext null
                    }
                }

        suspend fun sendCommand(
            command: JSONObject,
            host: String = DEFAULT_HOST,
            port: Int? = null
        ): JSONObject? {
            val ctx = appContext
            if (ctx == null) {
                AppLogger.e(TAG, "Cannot send command: appContext is null")
                return null
            }
            return sendCommand(ctx, command, host, port)
        }

        // 发送命令到桥接器
        @Suppress("UNUSED_PARAMETER") // 保持公开 Context 重载；传输不依赖 Android 文案或 UI。
        suspend fun sendCommand(
                context: Context,
                command: JSONObject,
                host: String = DEFAULT_HOST,
                port: Int? = null
        ): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        // 自动检测端口（如果未指定）
                        val actualPort = port ?: detectPort()
                        sendCommandAtPort(command, host, actualPort)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        val cmdType = command.optString("command", "unknown")
                        AppLogger.e(TAG, "发送命令失败[$cmdType]: ${e.javaClass.simpleName}")
                        return@withContext null
                    }
                }

        // 调用方在 IO 线程执行；显式端口入口与自动探测共用同一连接和同步边界。
        internal suspend fun sendCommandAtPort(command: JSONObject, host: String, port: Int): JSONObject? {
            val cmdType = command.optString("command", "unknown")
            val cmdId = command.optString("id", "no-id")
            AppLogger.d(TAG, "发送命令[$cmdId: $cmdType]")
            return try {
                val response = if (cmdType == "spawn") {
                    MCPBridgeConnection(host, port).use { it.exchange(command) }
                } else {
                    commandConnectionMutex.withLock {
                        commandCloseJob?.cancel()
                        commandCloseJob = null
                        val cached = commandConnection
                        val connection = if (
                            cached != null && cached.canReuse(host, port) &&
                            System.currentTimeMillis() - commandLastUsedAtMs <= COMMAND_CONNECTION_KEEP_MS
                        ) {
                            cached
                        } else {
                            closeCommandConnectionLocked()
                            // 在 connect 之前取得资源所有权，建立连接或创建流失败也必须关闭。
                            MCPBridgeConnection(host, port).also { commandConnection = it }
                        }
                        try {
                            val result = connection.exchange(command)
                            commandLastUsedAtMs = System.currentTimeMillis()
                            scheduleCommandConnectionCloseLocked()
                            result
                        } catch (error: Exception) {
                            // 清理发生在锁内；新请求不能消费取消或失败连接的迟到响应。
                            closeCommandConnectionLocked()
                            throw error
                        }
                    }
                }
                AppLogger.d(TAG, "命令[$cmdId: $cmdType]响应 success=${response.optBoolean("success", false)}")
                response
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLogger.e(TAG, "命令[$cmdId: $cmdType]通信或解析失败: ${error.javaClass.simpleName}")
                null
            }
        }
    }

    // 注册新的MCP服务
    suspend fun registerMcpService(
        name: String,
        command: String,
        args: List<String> = emptyList(),
        description: String? = null,
        env: Map<String, String> = emptyMap(),
        cwd: String? = null
    ): JSONObject? {
        return sendCommand(
            MCPBridgeClient.buildRegisterLocalCommand(
                name = name,
                command = command,
                args = args,
                description = description,
                env = env,
                cwd = cwd
            )
        )
    }

    // Overload for remote services
    suspend fun registerMcpService(
        name: String,
        type: String,
        endpoint: String,
        connectionType: String? = null,
        description: String? = null,
        bearerToken: String? = null,
        headers: Map<String, String>? = null
    ): JSONObject? {
        return sendCommand(
            MCPBridgeClient.buildRegisterRemoteCommand(
                name = name,
                type = type,
                endpoint = endpoint,
                connectionType = connectionType,
                description = description,
                bearerToken = bearerToken,
                headers = headers
            )
        )
    }

    // 取消注册MCP服务
    suspend fun unregisterMcpService(name: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildUnregisterCommand(name))
    }

    // 列出所有注册的MCP服务或查询单个服务
    suspend fun listMcpServices(serviceName: String? = null): JSONObject? {
        return sendCommand(MCPBridgeClient.buildListServicesCommand(serviceName))
    }

    // 启动MCP服务
    suspend fun spawnMcpService(
        name: String? = null,
        command: String? = null,
        args: List<String>? = null,
        env: Map<String, String>? = null,
        cwd: String? = null,
        timeoutMs: Long? = null
    ): JSONObject? {
        return sendCommand(
            MCPBridgeClient.buildSpawnCommand(
                name = name,
                command = command,
                args = args,
                env = env,
                cwd = cwd,
                timeoutMs = timeoutMs
            )
        )
    }

    // 停止MCP服务（不注销）
    suspend fun unspawnMcpService(name: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildUnspawnCommand(name))
    }

    // 获取工具列表
    suspend fun listTools(serviceName: String? = null): JSONObject? {
        AppLogger.d(TAG, "获取工具列表${if (serviceName != null) " 服务: $serviceName" else " (默认服务)"}")
        return sendCommand(MCPBridgeClient.buildListToolsCommand(serviceName))
    }

    // 缓存工具列表到bridge（用于已有缓存的插件）
    suspend fun cacheTools(serviceName: String, tools: List<JSONObject>): JSONObject? {
        AppLogger.d(TAG, "缓存工具列表到bridge 服务: $serviceName 工具数: ${tools.size}")
        return sendCommand(MCPBridgeClient.buildCacheToolsCommand(serviceName, tools))
    }

    // 调用工具
    suspend fun callTool(method: String, params: JSONObject): JSONObject? {
        return sendCommand(MCPBridgeClient.buildToolCallCommand(name = null, method = method, params = params))
    }

    // 简化调用工具的方法
    suspend fun toolcall(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callTool(method, paramsJson)
    }

    /**
     * 查询特定MCP服务的状态
     *
     * @param serviceName 要查询的服务名称
     * @return 服务信息响应，如果失败则返回null
     */
    suspend fun getServiceStatus(serviceName: String): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "查询服务 $serviceName 的状态")
                return@withContext listMcpServices(serviceName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "查询服务状态时出错: ${e.message}")
                return@withContext null
            }
        }

    suspend fun getServiceLogs(serviceName: String): JSONObject? {
        return sendCommand(MCPBridgeClient.buildLogsCommand(serviceName))
    }

    /**
     * 重置桥接器 - 关闭所有服务、清空注册表和池子
     *
     * @return 重置是否成功
     */
    suspend fun resetBridge(): JSONObject? =
        withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "开始重置桥接器，关闭所有服务并清空注册表...")
                val response = sendCommand(MCPBridgeClient.buildResetCommand())

                if (response?.optBoolean("success", false) == true) {
                    AppLogger.i(TAG, "桥接器重置成功")
                    return@withContext response
                } else {
                    AppLogger.w(TAG, "桥接器重置失败")
                    return@withContext null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e(TAG, "重置桥接器时出错: ${e.message}")
                return@withContext null
            }
        }
}

/** 单条 JSON 行连接的资源；共享调用顺序仍由 MCPBridge 的连接锁管理。 */
internal class MCPBridgeConnection(
    private val host: String,
    private val port: Int,
    private val socket: Socket = Socket(),
    private val readTimeoutMs: Int = 180000,
) : Closeable {
    private class Streams(val writer: BufferedWriter, val reader: BufferedReader)
    private var streams: Streams? = null

    fun canReuse(host: String, port: Int): Boolean =
        this.host == host && this.port == port && streams != null && socket.isConnected &&
            !socket.isClosed && !socket.isInputShutdown && !socket.isOutputShutdown

    suspend fun exchange(command: JSONObject): JSONObject = suspendCancellableCoroutine { continuation ->
        // readLine 持有 reader 锁；取消只关闭 Socket，不能在回调中等待 reader.close()。
        continuation.invokeOnCancellation { close() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try {
            val io = streams ?: run {
                socket.reuseAddress = true
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), 5000)
                Streams(
                    OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8).buffered(),
                    InputStreamReader(socket.getInputStream(), Charsets.UTF_8).buffered(),
                ).also { streams = it }
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            // BufferedWriter 直接传播写入异常，不能使用隐藏错误的 PrintWriter。
            io.writer.write(command.toString())
            io.writer.newLine()
            io.writer.flush()
            val response = io.reader.readLine()
            if (response.isNullOrBlank()) throw EOFException("Bridge returned no response")
            continuation.resume(JSONObject(response))
        } catch (error: Exception) {
            close()
            continuation.resumeWithException(error)
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (error: IOException) {
            AppLogger.w("MCPBridge", "关闭命令 Socket 失败: ${error.javaClass.simpleName}")
        }
    }
}

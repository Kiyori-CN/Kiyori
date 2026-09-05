package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutionLimits
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * MCP工具执行器
 *
 * 处理MCP工具的调用，类似于已有的PackageToolExecutor
 */
class MCPToolExecutor(private val context: Context, private val mcpManager: MCPManager) :
        ToolExecutor {
    companion object {
        private const val TAG = "MCPToolExecutor"
    }

    /** 保存过长的 MCP 结果，并返回适合内联展示的内容 */
    private fun persistLongResultIfNeeded(
            result: String,
            serverName: String,
            toolName: String
    ): String {
        val maxResultLength = ToolExecutionLimits.MAX_TEXT_RESULT_LENGTH

        if (result.length <= maxResultLength) {
            return result
        }

        val outputFile = writeMcpResultToFile(result, serverName, toolName)
        val truncated = result.substring(0, maxResultLength).trimEnd()
        val remainingLength = result.length - maxResultLength
        return "$truncated\n\n[Result too long. Full MCP result saved to file: ${outputFile.absolutePath}]\n[Original result length: ${result.length} chars, inline preview omitted $remainingLength chars]\nUse read_file_part or grep_code to inspect the saved file."
    }

    private fun writeMcpResultToFile(result: String, serverName: String, toolName: String): File {
        val outputDir = OperitPaths.cleanOnExitInternalDir(context)
        val safeServerName = sanitizeFileNamePart(serverName)
        val safeToolName = sanitizeFileNamePart(toolName)
        val file =
                File(
                        outputDir,
                        "mcp_${safeServerName}_${safeToolName}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.txt"
                )
        file.writeText(result, Charsets.UTF_8)
        return file
    }

    private fun sanitizeFileNamePart(value: String): String =
            value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80)

    /**
     * 从 MCP 结果中提取内容
     * 
     * 解析 content 数组，智能识别并提取不同类型的内容：
     * - text: 直接提取文本，如果是 JSON 字符串则尝试格式化
     * - image: 显示图像信息
     * - resource: 提取资源内容或显示资源信息
     * 
     * @param resultData MCP 返回的 result 对象
     * @return 提取后的文本内容
     */
    private fun extractContentFromResult(resultData: JSONObject?): String {
        if (resultData == null) {
            return "{}"
        }

        // 提取 content 数组中的内容
        val contentArray = resultData.optJSONArray("content")
        val contentText =
                if (contentArray != null && contentArray.length() > 0) {
                    val extractedText = StringBuilder()
                    for (i in 0 until contentArray.length()) {
                        val contentItem = contentArray.optJSONObject(i) ?: continue
                        val contentType = contentItem.optString("type", "text")

                        when (contentType) {
                            "text" -> {
                                val text = contentItem.optString("text", "")
                                val processedText =
                                        if (isJsonString(text)) {
                                            try {
                                                formatJson(text)
                                            } catch (e: Exception) {
                                                text
                                            }
                                        } else {
                                            text
                                        }
                                extractedText.append(processedText)
                            }
                            "image" -> {
                                val mimeType = contentItem.optString("mimeType", "image/png")
                                val data = contentItem.optString("data", "")
                                if (data.isNotEmpty()) {
                                    val imageId = ImagePoolManager.addImageFromBase64(data, mimeType)
                                    if (imageId != "error") {
                                        extractedText.append("<link type=\"image\" id=\"$imageId\"></link>")
                                    } else {
                                        val dataSize = data.length
                                        extractedText.append("[Image: $mimeType, Size: $dataSize bytes]")
                                    }
                                } else {
                                    extractedText.append("[Image: $mimeType, Size: 0 bytes]")
                                }
                            }
                            "resource" -> {
                                val resource = contentItem.optJSONObject("resource")
                                if (resource != null) {
                                    val uri = resource.optString("uri", "")
                                    val text = resource.optString("text")
                                    val mimeType = resource.optString("mimeType", "")
                                    val blob = resource.optString("blob", "")
                                    val data = if (blob.isNotEmpty()) blob else resource.optString("data", "")
                                    val isImage = mimeType.startsWith("image/") && data.isNotEmpty()
                                    if (isImage) {
                                        val finalMimeType = if (mimeType.isNotEmpty()) mimeType else "image/png"
                                        val imageId = ImagePoolManager.addImageFromBase64(data, finalMimeType)
                                        if (imageId != "error") {
                                            extractedText.append("<link type=\"image\" id=\"$imageId\"></link>")
                                        } else if (text != null && text.isNotEmpty()) {
                                            extractedText.append(text)
                                        } else {
                                            extractedText.append("[Resource: $uri]")
                                        }
                                    } else if (text != null && text.isNotEmpty()) {
                                        extractedText.append(text)
                                    } else {
                                        extractedText.append("[Resource: $uri]")
                                    }
                                }
                            }
                            else -> {
                                extractedText.append("[Unknown content type '$contentType': ${contentItem}]")
                            }
                        }

                        if (i < contentArray.length() - 1) {
                            extractedText.append("\n")
                        }
                    }
                    extractedText.toString()
                } else {
                    ""
                }

        // 提取元数据 (resultData 中除了 "content" 之外的所有字段)
        val metadata = JSONObject()
        val keys = resultData.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "content") {
                metadata.put(key, resultData.get(key))
            }
        }

        val metadataText = if (metadata.length() > 0) metadata.toString() else ""

        // 组合元数据和内容
        return when {
            metadataText.isNotEmpty() && contentText.isNotEmpty() -> {
                "$metadataText\n\n$contentText"
            }
            metadataText.isNotEmpty() -> metadataText
            contentText.isNotEmpty() -> contentText
            else -> resultData.toString() // fallback to original data if both are empty
        }
    }

    /**
     * 判断字符串是否为 JSON 格式
     * 
     * @param text 待判断的字符串
     * @return 如果是 JSON 返回 true
     */
    private fun isJsonString(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        
        // 检查是否以 JSON 对象或数组的标志开头和结尾
        val isJsonObject = trimmed.startsWith("{") && trimmed.endsWith("}")
        val isJsonArray = trimmed.startsWith("[") && trimmed.endsWith("]")
        
        if (!isJsonObject && !isJsonArray) return false
        
        // 尝试解析以确认
        return try {
            if (isJsonObject) {
                JSONObject(trimmed)
            } else {
                org.json.JSONArray(trimmed)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 格式化 JSON 字符串为单行紧凑格式
     * 
     * @param jsonString JSON 字符串
     * @return 紧凑格式的 JSON 字符串
     */
    private fun formatJson(jsonString: String): String {
        val trimmed = jsonString.trim()
        
        return try {
            if (trimmed.startsWith("{")) {
                // JSON 对象
                val jsonObject = JSONObject(trimmed)
                jsonObject.toString()
            } else if (trimmed.startsWith("[")) {
                // JSON 数组
                val jsonArray = org.json.JSONArray(trimmed)
                jsonArray.toString()
            } else {
                jsonString
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "JSON 格式化失败: ${e.javaClass.simpleName}")
            jsonString
        }
    }

    override fun invoke(tool: AITool): ToolResult {
        // 从工具名称中提取服务器名称和工具名称
        // 格式：服务器名称:工具名称
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid MCP tool name format, should be 'server_name:tool_name'"
            )
        }

        val serverName = toolNameParts[0]
        val actualToolName = toolNameParts.subList(1, toolNameParts.size).joinToString(":")

        // 获取MCP桥接客户端
        val mcpClient = mcpManager.getOrCreateClient(serverName)
        if (mcpClient == null) {
            val detailedReason = mcpManager.getLastConnectionFailureReason(serverName)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            detailedReason?.let {
                                "Cannot connect to MCP server '$serverName': $it"
                            }
                                    ?: "Cannot connect to MCP server: $serverName"
            )
        }

        // 在调用工具前，检查服务是否处于激活状态
        val isActive = try {
            runBlocking { mcpClient.isActive() }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
        if (!isActive) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "MCP service '$serverName' is not activated. Please use the 'use_package' tool with the package name '$serverName' to activate it first."
            )
        }

        AppLogger.d(TAG, "准备调用MCP工具: $serverName:$actualToolName")

        // 将AITool参数转换为Map
        val parameters = tool.parameters.associate { it.name to it.value }

        // 获取工具参数类型信息 (如果可用)
        val toolInfo = getToolInfo(serverName, actualToolName)

        // 自动类型转换处理
        val convertedParameters = convertParameterTypes(parameters, toolInfo)

        // 调用MCP工具 - 使用同步版本
        val result =
                try {
                    // 直接调用工具，返回完整的响应（包括 success, result, error）
                    val response = mcpClient.callToolSync(actualToolName, convertedParameters)

                    if (response == null) {
                        // 如果响应为空（不应该发生，但做个保护）
                        AppLogger.e(TAG, "MCP工具调用返回空响应: $serverName:$actualToolName")
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Tool call returned empty response"
                        )
                    } else {
                        val success = response.optBoolean("success", false)
                        
                        if (success) {
                            // 成功：提取 result 字段并解析 content 数组
                            val resultData = response.optJSONObject("result")
                            val extractedContent = extractContentFromResult(resultData)
                            val displayResult =
                                    persistLongResultIfNeeded(
                                            result = extractedContent,
                                            serverName = serverName,
                                            toolName = actualToolName
                                    )
                            AppLogger.d(TAG, "MCP工具调用成功: $serverName:$actualToolName")
                            ToolResult(
                                    toolName = tool.name,
                                    success = true,
                                    result = StringResultData(displayResult),
                                    error = null
                            )
                        } else {
                            // 失败：提取 error 字段
                            val errorObj = response.optJSONObject("error")
                            val errorMessage = if (errorObj != null) {
                                val code = errorObj.optInt("code", -1)
                                val message = errorObj.optString("message", "Unknown error")
                                "[$code] $message"
                            } else {
                                "Tool call failed but no error message returned"
                            }
                            
                            AppLogger.w(TAG, "MCP工具调用失败: $serverName:$actualToolName")
                            ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = errorMessage
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                } catch (e: Exception) {
                    val errorMessage = "Exception occurred while calling tool: ${e.message}"
                    AppLogger.e(TAG, "调用MCP工具时发生异常: ${e.javaClass.simpleName}")
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage
                    )
                }

        return result
    }

    /** 尝试获取工具的参数类型信息 */
    private fun getToolInfo(serverName: String, toolName: String): JSONObject? {
        try {
            val client = mcpManager.getOrCreateClient(serverName) ?: return null
            val tools = kotlinx.coroutines.runBlocking { client.getTools() }

            return tools.find { it.optString("name") == toolName }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取工具信息失败: ${e.javaClass.simpleName}")
            return null
        }
    }

    /**
     * 自动转换参数类型
     *
     * 将字符串参数转换为适当的类型（包括 number、boolean、array 等）
     * 支持递归处理数组内的元素
     */
    private fun convertParameterTypes(
            parameters: Map<String, Any>,
            toolInfo: JSONObject?
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        parameters.forEach { (name, value) ->
            // 尝试从工具定义中获取参数类型（从 inputSchema.properties 中获取）
            val expectedType =
                    toolInfo?.optJSONObject("inputSchema")?.optJSONObject("properties")?.let {
                                properties ->
                        properties.optJSONObject(name)?.optString("type")
                    }

            // 使用 MCPToolParameter.smartConvert 进行智能类型转换
            val convertedValue = MCPToolParameter.smartConvert(value, expectedType)

            if (convertedValue != value) {
                AppLogger.d(
                        TAG,
                        "参数 $name 从 ${value::class.java.simpleName} 转换为 ${convertedValue::class.java.simpleName}"
                )
            }

            result[name] = convertedValue
        }

        return result
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 验证工具名称格式
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "Invalid MCP tool name format, should be 'server_name:tool_name'"
            )
        }

        // 这里可以添加更多验证逻辑，但目前简单返回成功
        return ToolValidationResult(valid = true)
    }
}

/**
 * MCP管理器
 *
 * 管理注册代际、连接和失败状态；共享桥接器的网络通信仍由 MCPBridgeClient 执行。
 */
class MCPManager internal constructor(private val clientFactory: (String) -> MCPBridgeClient) {
    constructor(context: Context) : this(
        clientFactory = context.applicationContext.let { appContext ->
            { name: String -> MCPBridgeClient(appContext, name) }
        }
    )

    companion object {
        private const val TAG = "MCPManager"

        @Volatile private var INSTANCE: MCPManager? = null

        fun getInstance(context: Context): MCPManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: MCPManager(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private class ServerEntry(var config: MCPServerConfig?) {
        val connectionLock = ReentrantLock()
        var generation = 0L
        var activeCalls = 0
        var client: MCPBridgeClient? = null
        var failureReason: String? = null
    }

    // 配置、客户端和错误必须原子发布；连接等待不能占用此锁，否则卸载无法使旧请求失效。
    private val registrationLock = Any()
    private val servers = mutableMapOf<String, ServerEntry>()

    /**
     * 检查服务器是否已注册
     *
     * @param serverName 服务器名称
     * @return 如果服务器已注册则返回true
     */
    fun isServerRegistered(serverName: String): Boolean {
        return synchronized(registrationLock) { servers[serverName]?.config != null }
    }

    /**
     * 获取所有已注册的服务器配置
     *
     * @return 服务器名称到服务器配置的映射
     */
    fun getRegisteredServers(): Map<String, MCPServerConfig> {
        return synchronized(registrationLock) {
            servers.mapNotNull { (name, entry) -> entry.config?.let { name to it } }.toMap()
        }
    }

    fun getLastConnectionFailureReason(serverName: String): String? {
        return synchronized(registrationLock) {
            val entry = servers[serverName]
            if (entry?.config == null) {
                "Server is not registered in MCPManager. The runtime registration was not completed or has been removed."
            } else {
                entry.failureReason
            }
        }
    }

    /**
     * 获取或创建MCP桥接客户端
     *
     * @param serverName 服务器名称
     * @return MCP桥接客户端，如果服务器不存在或无法连接则返回null
     */
    fun getOrCreateClient(serverName: String): MCPBridgeClient? {
        val (entry, generation) = synchronized(registrationLock) {
            val current = servers[serverName] ?: return null
            if (current.config == null) return null
            current.activeCalls++
            current to current.generation
        }
        try {
            entry.connectionLock.lockInterruptibly()
            try {
                return connectCurrentRegistration(serverName, entry, generation)
            } finally {
                entry.connectionLock.unlock()
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } finally {
            synchronized(registrationLock) {
                entry.activeCalls--
                // 在途请求归零前保留同服务锁，防止卸载后立即重装产生两条并行连接。
                if (entry.config == null && entry.activeCalls == 0) {
                    servers.remove(serverName, entry)
                }
            }
        }
    }

    private fun connectCurrentRegistration(
        serverName: String,
        entry: ServerEntry,
        generation: Long,
    ): MCPBridgeClient? {
        var client = synchronized(registrationLock) {
            if (!isCurrentRegistration(serverName, entry, generation)) return null
            entry.client
        }
        var published = false
        try {
            val currentClient = client ?: clientFactory(serverName).also { client = it }
            synchronized(registrationLock) {
                if (!isCurrentRegistration(serverName, entry, generation)) return null
            }
            val connected = currentClient.isConnected() || runBlocking { currentClient.connect() }
            val failure = if (connected) {
                null
            } else {
                currentClient.getLastConnectionFailureDetail()
                    ?: "Connection attempt failed without a detailed bridge reason."
            }
            return synchronized(registrationLock) {
                if (!isCurrentRegistration(serverName, entry, generation)) return@synchronized null
                entry.failureReason = failure
                entry.client = if (connected) currentClient else null
                published = connected
                entry.client
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            throw interrupted
        } catch (error: Exception) {
            synchronized(registrationLock) {
                if (isCurrentRegistration(serverName, entry, generation)) {
                    entry.client = null
                    entry.failureReason = "Bridge client connection failed: ${error.javaClass.simpleName}"
                }
            }
            AppLogger.e(TAG, "MCP client connection failed: ${error.javaClass.simpleName}")
            return null
        } finally {
            if (!published) {
                synchronized(registrationLock) {
                    if (isCurrentRegistration(serverName, entry, generation) && entry.client === client) {
                        entry.client = null
                    }
                }
                client?.disconnect()
            }
        }
    }

    // 调用方持有 registrationLock；generation 同时隔离迟到成功和迟到错误。
    private fun isCurrentRegistration(serverName: String, entry: ServerEntry, generation: Long): Boolean {
        return servers[serverName] === entry && entry.config != null && entry.generation == generation
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param serverConfig 服务器配置
     */
    fun registerServer(serverName: String, serverConfig: MCPServerConfig) {
        val oldClient = synchronized(registrationLock) {
            val entry = servers.getOrPut(serverName) { ServerEntry(null) }
            entry.config = serverConfig.copy(
                capabilities = serverConfig.capabilities.toList(),
                extraData = serverConfig.extraData.toMap(),
            )
            entry.generation++
            entry.failureReason = null
            entry.client.also { entry.client = null }
        }
        oldClient?.disconnect()
    }

    /**
     * 注销MCP服务器配置
     *
     * @param serverName 服务器名称
     */
    fun unregisterServer(serverName: String) {
        val oldClient = synchronized(registrationLock) {
            val entry = servers[serverName] ?: return
            entry.config = null
            entry.generation++
            entry.failureReason = null
            if (entry.activeCalls == 0) servers.remove(serverName)
            entry.client.also { entry.client = null }
        }
        oldClient?.disconnect()
    }

    /**
     * 注册MCP服务器（简化版）
     *
     * @param serverName 服务器名称
     * @param endpoint 服务器端点URL
     * @param description 服务器描述
     */
    fun registerServer(serverName: String, endpoint: String, description: String = "") {
        val serverConfig =
                MCPServerConfig(
                        name = serverName,
                        endpoint = endpoint,
                        description = description,
                        capabilities = listOf("tools"),
                        extraData = emptyMap()
                )
        registerServer(serverName, serverConfig)
    }

    /** 清理当前连接并使在途请求失效；注册配置保留，后续调用可建立新的连接。 */
    fun shutdown() {
        val oldClients = synchronized(registrationLock) {
            servers.values.mapNotNull { entry ->
                entry.generation++
                entry.failureReason = null
                entry.client.also { entry.client = null }
            }
        }
        oldClients.forEach { it.disconnect() }
    }
}

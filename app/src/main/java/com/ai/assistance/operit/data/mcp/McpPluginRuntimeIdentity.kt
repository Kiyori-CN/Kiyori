package com.ai.assistance.operit.data.mcp

/** 仅连接相关配置决定工具发现的有效性；更新显示文案不应断开正在使用的服务。 */
internal data class McpPluginRuntimeIdentity(
    val server: MCPLocalServer.MCPConfig.ServerConfig?,
    val type: String,
    val endpoint: String?,
    val connectionType: String?,
    val disabled: Boolean,
    private val bearerToken: String?,
    private val headers: Map<String, String>?,
    val installedPath: String?,
    private val marketConfig: String?,
    val generation: Long = 0,
) {
    val enabled: Boolean get() = !disabled && server?.disabled != true &&
        (if (type == "remote") !endpoint.isNullOrBlank() else server != null)

    // 配置身份只用于相等性校验，禁止通过日志泄露认证头或环境变量。
    override fun toString(): String = "McpPluginRuntimeIdentity(type=$type, enabled=$enabled)"

    fun fingerprint(): String {
        val payload = com.google.gson.Gson().toJson(listOf(
            server?.copy(env = server.env?.toSortedMap()), type, endpoint, connectionType,
            disabled, bearerToken, headers?.toSortedMap(), installedPath, marketConfig
        ))
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun from(config: MCPLocalServer.MCPConfig, pluginId: String): McpPluginRuntimeIdentity? {
            val metadata = config.pluginMetadata[pluginId] ?: return null
            return McpPluginRuntimeIdentity(config.mcpServers[pluginId], metadata.type,
                metadata.endpoint, metadata.connectionType, metadata.disabled,
                metadata.bearerToken, metadata.headers?.toMap(), metadata.installedPath, metadata.marketConfig)
        }
    }
}

/** 由 MCPLocalServer 的配置锁保护；删除后保留代际，防止同名配置恢复导致旧任务复活。 */
internal class McpRegistrationGeneration {
    private val generations = mutableMapOf<String, Long>()

    fun advance(previous: MCPLocalServer.MCPConfig, updated: MCPLocalServer.MCPConfig): Set<String> {
        val changed = (previous.pluginMetadata.keys + updated.pluginMetadata.keys).filterTo(linkedSetOf()) {
            McpPluginRuntimeIdentity.from(previous, it) != McpPluginRuntimeIdentity.from(updated, it)
        }
        changed.forEach { generations[it] = (generations[it] ?: 0L) + 1L }
        return changed
    }

    fun capture(config: MCPLocalServer.MCPConfig, pluginId: String): McpPluginRuntimeIdentity? =
        McpPluginRuntimeIdentity.from(config, pluginId)?.takeIf { it.enabled }
            ?.copy(generation = generations[pluginId] ?: 0L)
}

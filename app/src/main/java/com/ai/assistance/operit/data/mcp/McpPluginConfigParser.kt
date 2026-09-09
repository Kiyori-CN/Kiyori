package com.ai.assistance.operit.data.mcp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException

internal fun parseMcpServerImport(json: String): Map<String, MCPLocalServer.MCPConfig.ServerConfig> {
    try {
        val root = JsonParser.parseString(json).asJsonObject
        require(!root.has("command"))
        val servers = root.getAsJsonObject("mcpServers")
        require(servers.size() > 0)
        return servers.entrySet().associate { (id, value) ->
            require(id.isNotBlank())
            id to parseMcpPluginConfig(id, value.toString())
        }
    } catch (_: Exception) {
        throw IOException("Invalid MCP server configuration")
    }
}

internal fun parseMcpPluginConfig(pluginId: String, json: String): MCPLocalServer.MCPConfig.ServerConfig {
    try {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject)
        val objectRoot = root.asJsonObject
        val server = if (objectRoot.has("mcpServers")) {
            // 完整配置必须含目标服务，不能把缺目标的文档重新解释为单服务配置。
            require(!objectRoot.has("command"))
            val servers = objectRoot.get("mcpServers")
            require(servers.isJsonObject)
            checkNotNull(servers.asJsonObject.get(pluginId))
        } else objectRoot
        require(server.isJsonObject)
        val fields = server.asJsonObject
        fun JsonElement.isStringValue() = isJsonPrimitive && asJsonPrimitive.isString
        val command = fields.get("command")
        require(command != null && command.isStringValue() && command.asString.isNotBlank())
        for (name in listOf("args", "autoApprove")) {
            fields.get(name)?.takeUnless { it.isJsonNull }?.let { values ->
                require(values.isJsonArray && values.asJsonArray.all { it.isStringValue() })
            }
        }
        fields.get("disabled")?.let { require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }
        fields.get("env")?.takeUnless { it.isJsonNull }?.let { environment ->
            require(environment.isJsonObject)
            require(environment.asJsonObject.entrySet().all { (key, value) -> key.isNotBlank() && value.isStringValue() })
        }
        val parsed = Gson().fromJson(fields, MCPLocalServer.MCPConfig.ServerConfig::class.java)
        return parsed.copy(command = parsed.command.trim(), args = parsed.args.orEmpty(),
            autoApprove = parsed.autoApprove.orEmpty(), env = parsed.env.orEmpty())
    } catch (_: Exception) {
        // 解析器异常可能包含配置正文（令牌、环境变量）；不向上游传播原文或 cause。
        throw IOException("Invalid MCP server configuration")
    }
}

/** 打开详情时尚未部署的插件没有配置，这也是需要比较的原始状态。 */
internal fun parseMcpPluginConfigSnapshot(pluginId: String, json: String): MCPLocalServer.MCPConfig.ServerConfig? {
    try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.has("mcpServers") && !root.has("command")) {
            val servers = root.getAsJsonObject("mcpServers")
            if (!servers.has(pluginId)) return null
        }
        return parseMcpPluginConfig(pluginId, json)
    } catch (_: Exception) {
        throw IOException("Invalid MCP server configuration")
    }
}

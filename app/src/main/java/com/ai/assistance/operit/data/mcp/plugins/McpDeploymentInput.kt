package com.ai.assistance.operit.data.mcp.plugins

import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.parseMcpPluginConfig
import com.google.gson.JsonParser
import java.io.IOException

internal fun quoteMcpRuntimePath(path: String): String {
    require(path.startsWith("~/mcp_plugins/"))
    val name = path.removePrefix("~/mcp_plugins/")
    require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.code < 32 })
    return "\"\$HOME\"/'" + path.removePrefix("~/").replace("'", "'\"'\"'") + "'"
}

internal fun buildMcpDeploymentCommand(path: String, command: String): String =
    "cd ${quoteMcpRuntimePath(path)} && {\n$command\n}"

internal fun parseMcpDeploymentConfig(pluginId: String, json: String): MCPLocalServer.MCPConfig.ServerConfig {
    try {
        val servers = JsonParser.parseString(json).asJsonObject.getAsJsonObject("mcpServers")
        val selected = servers.get(pluginId) ?: servers.entrySet().singleOrNull()?.value
        requireNotNull(selected)
        return parseMcpPluginConfig(pluginId, selected.toString())
    } catch (_: Exception) {
        throw IOException("Deployment needs one unambiguous valid server configuration")
    }
}

package com.ai.assistance.operit.data.mcp

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.mcp.MCPManager

/** 使用既有注册表删除工具和连接，配置 owner 与 Repository 共用这一条失效路径。 */
internal fun unregisterMcpRuntimeTools(context: Context, pluginId: String) {
    val handler = AIToolHandler.getInstance(context)
    handler.getAllToolNames().filter { it.startsWith("$pluginId:") }.forEach(handler::unregisterTool)
    val manager = MCPManager.getInstance(context)
    val names = manager.getRegisteredServers().filter { (name, config) ->
        name == pluginId || config.endpoint == "mcp://plugin/$pluginId"
    }.keys
    names.forEach(manager::unregisterServer)
}

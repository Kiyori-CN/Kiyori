package com.ai.assistance.operit.data.mcp

/** 市场只更新来源资料，连接、启停与安装目录取锁内的最新事实。 */
internal fun mergeMcpMarketMetadata(
    current: MCPLocalServer.PluginMetadata?,
    incoming: MCPLocalServer.PluginMetadata,
): MCPLocalServer.PluginMetadata {
    if (current == null) return incoming.copy(isInstalled = true)
    require(current.id == incoming.id)
    return current.copy(
        name = current.name.ifBlank { incoming.name },
        description = current.description.ifBlank { incoming.description },
        logoUrl = incoming.logoUrl ?: current.logoUrl,
        author = incoming.author.ifBlank { current.author },
        isInstalled = true,
        version = incoming.version.ifBlank { current.version },
        updatedAt = incoming.updatedAt.ifBlank { current.updatedAt },
        longDescription = incoming.longDescription,
        repoUrl = incoming.repoUrl,
        marketConfig = incoming.marketConfig,
    )
}

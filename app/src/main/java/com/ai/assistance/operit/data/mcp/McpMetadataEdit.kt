package com.ai.assistance.operit.data.mcp

import java.net.URI

internal fun validateMcpRemoteEndpoint(endpoint: String?, connectionType: String?) {
    val uri = try { URI(endpoint.orEmpty()) } catch (_: Exception) { null }
    require(uri != null && uri.scheme?.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null) {
        "Enter an HTTP(S) endpoint without embedded credentials or a fragment"
    }
    require(connectionType in setOf("httpStream", "sse")) { "Unsupported connection type" }
}

internal fun validateMcpHeaders(headers: List<Pair<String, String>>) {
    val names = mutableSetOf<String>()
    headers.forEach { (name, value) ->
        require(name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) &&
            names.add(name.lowercase(java.util.Locale.ROOT)) &&
            value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
            "Headers need unique valid names and single-line values"
        }
    }
}

/** 只合并编辑字段，保留同时发生的启停与安装状态；第三个值表示真正的编辑冲突。 */
internal fun mergeMcpMetadataEdit(
    original: MCPLocalServer.PluginMetadata,
    edited: MCPLocalServer.PluginMetadata,
    latest: MCPLocalServer.PluginMetadata
): MCPLocalServer.PluginMetadata {
    require(original.id == edited.id && original.id == latest.id && original.type == latest.type)
    fun <T> field(before: T, after: T, current: T): T {
        if (before == after) return current
        check(current == before || current == after) { "Plugin changed elsewhere; reopen the editor" }
        return after
    }
    val remote = latest.type == "remote"
    return latest.copy(
        name = field(original.name, edited.name, latest.name),
        description = field(original.description, edited.description, latest.description),
        longDescription = field(original.longDescription, edited.longDescription, latest.longDescription),
        author = field(original.author, edited.author, latest.author),
        endpoint = if (remote) field(original.endpoint, edited.endpoint, latest.endpoint) else latest.endpoint,
        connectionType = if (remote) field(original.connectionType, edited.connectionType, latest.connectionType) else latest.connectionType,
        bearerToken = if (remote) field(original.bearerToken, edited.bearerToken, latest.bearerToken) else latest.bearerToken,
        headers = if (remote) field(original.headers, edited.headers, latest.headers) else latest.headers
    )
}

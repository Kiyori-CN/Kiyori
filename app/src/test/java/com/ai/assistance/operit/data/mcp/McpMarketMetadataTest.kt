package com.ai.assistance.operit.data.mcp

import org.junit.Assert.*
import org.junit.Test

class McpMarketMetadataTest {
    @Test fun `market refresh preserves connection and lifecycle state`() {
        val current = MCPLocalServer.PluginMetadata("id", "User name", "User description",
            type = "remote", endpoint = "https://example.test/mcp", disabled = true,
            bearerToken = "private-value", headers = mapOf("X-Key" to "private-value"),
            installedPath = "/latest", installedTime = 1L)
        val incoming = MCPLocalServer.PluginMetadata("id", "Market name", "Market description",
            version = "2", installedTime = 2L, repoUrl = "https://example.test/repo")
        val merged = mergeMcpMarketMetadata(current, incoming)
        assertEquals(current.name, merged.name)
        assertEquals(current.description, merged.description)
        assertEquals(current.endpoint, merged.endpoint)
        assertEquals(current.bearerToken, merged.bearerToken)
        assertEquals(current.headers, merged.headers)
        assertEquals(current.installedPath, merged.installedPath)
        assertEquals(current.installedTime, merged.installedTime)
        assertTrue(merged.disabled)
        assertEquals("remote", merged.type)
        assertEquals("2", merged.version)
        assertTrue(merged.isInstalled)
    }

    @Test fun `missing metadata is created and blank labels are filled`() {
        val incoming = MCPLocalServer.PluginMetadata("id", "Name", "Description")
        assertTrue(mergeMcpMarketMetadata(null, incoming).isInstalled)
        val merged = mergeMcpMarketMetadata(incoming.copy(name = "", description = ""), incoming)
        assertEquals("Name", merged.name)
        assertEquals("Description", merged.description)
    }
}

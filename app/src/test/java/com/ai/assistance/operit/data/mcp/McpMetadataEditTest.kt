package com.ai.assistance.operit.data.mcp

import org.junit.Assert.*
import org.junit.Test

class McpMetadataEditTest {
    private val original = MCPLocalServer.PluginMetadata("sample", "Name", "Description", type = "remote", endpoint = "https://example.test/mcp")

    @Test fun `edit preserves concurrent lifecycle and untouched fields`() {
        val latest = original.copy(disabled = true, installedPath = "/changed", description = "Elsewhere")
        val result = mergeMcpMetadataEdit(original, original.copy(name = "Edited"), latest)
        assertEquals("Edited", result.name)
        assertEquals("Elsewhere", result.description)
        assertTrue(result.disabled)
        assertEquals("/changed", result.installedPath)
    }

    @Test fun `conflicting edit fails but same target retry succeeds`() {
        val edited = original.copy(name = "Edited")
        try { mergeMcpMetadataEdit(original, edited, original.copy(name = "Other")); fail("Conflict ignored") }
        catch (_: IllegalStateException) { }
        assertEquals(edited, mergeMcpMetadataEdit(original, edited, edited))
    }

    @Test fun `local metadata edit retains hidden credentials`() {
        val local = original.copy(type = "local", bearerToken = "private-value")
        assertEquals(local.bearerToken, mergeMcpMetadataEdit(local, local.copy(name = "Edited", bearerToken = null), local).bearerToken)
    }

    @Test fun `endpoint and header validation rejects ambiguous input`() {
        validateMcpRemoteEndpoint("http://localhost:8080/mcp?mode=1", "httpStream")
        for (endpoint in listOf("file:///tmp/mcp", "https://example.test/#fragment", "https://user:password@example.test/", "invalid")) {
            try { validateMcpRemoteEndpoint(endpoint, "sse"); fail("Invalid endpoint accepted") }
            catch (_: IllegalArgumentException) { }
        }
        for (headers in listOf(listOf("X-Key" to "1", "x-key" to "2"), listOf("" to "value"), listOf("X-Key" to "a\r\nb"))) {
            try { validateMcpHeaders(headers); fail("Invalid headers accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}

package com.ai.assistance.operit.data.mcp

import org.junit.Assert.*
import org.junit.Test

class McpPluginRuntimeIdentityTest {
    private val metadata = MCPLocalServer.PluginMetadata(id = "remote", name = "Remote", description = "Tools",
        type = "remote", endpoint = "https://example.test/mcp", bearerToken = "test-only-value")
    private fun identity(value: MCPLocalServer.PluginMetadata) = McpPluginRuntimeIdentity.from(
        MCPLocalServer.MCPConfig(pluginMetadata = mutableMapOf("remote" to value)), "remote")!!

    @Test fun `display edits preserve registration while credentials and disable invalidate it`() {
        val initial = identity(metadata)
        assertTrue(initial.enabled)
        assertEquals(initial, identity(metadata.copy(description = "New text", name = "New title")))
        assertNotEquals(initial, identity(metadata.copy(endpoint = "https://other.test/mcp")))
        assertNotEquals(initial.fingerprint(), identity(metadata.copy(bearerToken = "different")).fingerprint())
        assertFalse(identity(metadata.copy(disabled = true)).enabled)
        assertFalse(initial.toString().contains("test-only-value"))
    }

    @Test fun `missing metadata and local server config are never enabled`() {
        assertNull(McpPluginRuntimeIdentity.from(MCPLocalServer.MCPConfig(), "gone"))
        assertFalse(identity(metadata.copy(type = "local")).enabled)
    }

    @Test fun `cache fingerprints are insensitive to header map iteration order`() {
        val first = identity(metadata.copy(headers = linkedMapOf("A" to "1", "B" to "2")))
        val second = identity(metadata.copy(headers = linkedMapOf("B" to "2", "A" to "1")))
        assertEquals(first.fingerprint(), second.fingerprint())
    }

    @Test fun `disable enable and delete recreate invalidate earlier discovery receipts`() {
        val gate = McpRegistrationGeneration()
        val original = MCPLocalServer.MCPConfig(pluginMetadata = mutableMapOf("remote" to metadata))
        val disabled = original.copy(pluginMetadata = mutableMapOf("remote" to metadata.copy(disabled = true)))
        val first = gate.capture(original, "remote")!!
        gate.advance(original, disabled)
        assertNull(gate.capture(disabled, "remote"))
        gate.advance(disabled, original)
        val second = gate.capture(original, "remote")!!
        assertNotEquals(first, second)
        assertEquals(first.fingerprint(), second.fingerprint())
        val deleted = MCPLocalServer.MCPConfig()
        gate.advance(original, deleted)
        gate.advance(deleted, original)
        assertNotEquals(second, gate.capture(original, "remote"))
    }

    @Test fun `display edits do not invalidate concurrent discovery`() {
        val gate = McpRegistrationGeneration()
        val original = MCPLocalServer.MCPConfig(pluginMetadata = mutableMapOf("remote" to metadata))
        val renamed = original.copy(pluginMetadata = mutableMapOf("remote" to metadata.copy(name = "Renamed")))
        val receipt = gate.capture(original, "remote")
        assertTrue(gate.advance(original, renamed).isEmpty())
        assertEquals(receipt, gate.capture(renamed, "remote"))
    }
}

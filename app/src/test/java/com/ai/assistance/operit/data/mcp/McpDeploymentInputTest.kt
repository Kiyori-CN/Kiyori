package com.ai.assistance.operit.data.mcp

import com.ai.assistance.operit.data.mcp.plugins.parseMcpDeploymentConfig
import com.ai.assistance.operit.data.mcp.plugins.quoteMcpRuntimePath
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class McpDeploymentInputTest {
    @Test fun `deployment uses matching key or a single external server alias`() {
        assertEquals("node", parseMcpDeploymentConfig("installed", """{"mcpServers":{"external":{"command":"node"}}}""").command)
        assertEquals("uvx", parseMcpDeploymentConfig("installed", """{"mcpServers":{"external":{"command":"node"},"installed":{"command":"uvx"}}}""").command)
    }

    @Test fun `ambiguous or missing commands do not pick a default`() {
        for (json in listOf("""{"mcpServers":{"a":{"command":"node"},"b":{"command":"uvx"}}}""", """{"mcpServers":{"a":{}}}""")) {
            try { parseMcpDeploymentConfig("installed", json); fail("Invalid deployment accepted") }
            catch (error: IOException) { assertNull(error.cause) }
        }
    }

    @Test fun `runtime path keeps home expansion but quotes the plugin suffix`() {
        assertEquals("\"\$HOME\"/'mcp_plugins/a'\"'\"'b \$(cmd)'", quoteMcpRuntimePath("~/mcp_plugins/a'b \$(cmd)"))
    }
}

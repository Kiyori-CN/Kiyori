package com.ai.assistance.operit.data.mcp

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class McpPluginConfigParserTest {
    @Test fun `import validates all servers and rejects a malformed member`() {
        assertEquals(setOf("one", "two"), parseMcpServerImport("""{"mcpServers":{"one":{"command":"node"},"two":{"command":"uvx"}}}""").keys)
        try {
            parseMcpServerImport("""{"mcpServers":{"one":{"command":"node"},"two":{"command":12}}}""")
            fail("Partial import accepted")
        } catch (error: IOException) { assertNull(error.cause) }
    }
    @Test fun `absent original and normalized retry snapshots are explicit`() {
        assertNull(parseMcpPluginConfigSnapshot("target", """{"mcpServers":{}}"""))
        val config = parseMcpPluginConfig("target", """{"command":" node ","args":null}""")
        assertEquals("node", config.command)
        assertEquals(emptyList<String>(), config.args)
        assertEquals(config, parseMcpPluginConfigSnapshot("target", """{"command":"node"}"""))
    }
    @Test fun `direct and complete documents select the exact target`() {
        assertEquals("node", parseMcpPluginConfig("target", """{"command":"node","args":["server.js"]}""").command)
        val config = parseMcpPluginConfig("target", """{"mcpServers":{"other":{"command":"other"},"target":{"command":"uvx","env":{"MODE":"local"}}}}""")
        assertEquals("uvx", config.command)
        assertEquals("local", config.env?.get("MODE"))
    }

    @Test fun `missing target or mixed formats cannot be reinterpreted`() {
        listOf("""{"mcpServers":{"other":{"command":"node"}}}""", """{"mcpServers":{},"command":"node"}""").forEach(::assertInvalid)
    }

    @Test fun `wrong field types and blank command are rejected without coercion`() {
        listOf("[]", "null", "{}", """{"command":" "}""", """{"command":12}""", """{"command":"node","args":[12]}""", """{"command":"node","env":{"key":false}}""", """{"command":"node","disabled":"false"}""").forEach(::assertInvalid)
    }

    @Test fun `failure never exposes the source document`() {
        assertInvalid("""{"command":"private-value", malformed}""")
    }

    private fun assertInvalid(json: String) {
        try { parseMcpPluginConfig("target", json); fail("Invalid configuration accepted") }
        catch (error: IOException) {
            assertEquals("Invalid MCP server configuration", error.message)
            assertNull(error.cause)
        }
    }
}

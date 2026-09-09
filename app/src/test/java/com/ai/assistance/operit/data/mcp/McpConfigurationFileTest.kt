package com.ai.assistance.operit.data.mcp

import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpConfigurationFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `successful publication replaces the complete configuration`() {
        val target = temporary.newFile("mcp_config.json").apply { writeText("original") }
        writeMcpConfigurationFile(target, "中文 configuration")
        assertEquals("中文 configuration", target.readText())
        assertEquals(listOf("mcp_config.json"), temporary.root.list()!!.toList())
    }

    @Test fun `partial write failure preserves original and removes staging`() {
        val target = temporary.newFile("mcp_config.json").apply { writeText("original") }
        try {
            publishMcpConfigurationFile(target) { it.writeText("partial"); throw IOException("write failed") }
            fail("Failure was hidden")
        } catch (_: IOException) { }
        assertEquals("original", target.readText())
        assertEquals(listOf("mcp_config.json"), temporary.root.list()!!.toList())
    }

    @Test fun `failed publication does not remove an existing directory`() {
        val target = temporary.newFolder("mcp_config.json")
        target.resolve("keep").writeText("existing")
        try { writeMcpConfigurationFile(target, "new"); fail("Directory replaced") }
        catch (_: IOException) { }
        assertEquals("existing", target.resolve("keep").readText())
        assertEquals(listOf("mcp_config.json"), temporary.root.list()!!.toList())
    }
}

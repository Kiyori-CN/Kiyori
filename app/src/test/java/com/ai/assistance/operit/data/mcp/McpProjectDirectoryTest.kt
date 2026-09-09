package com.ai.assistance.operit.data.mcp

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpProjectDirectoryTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `project root takes precedence over dependency directories`() {
        val root = temporary.newFolder()
        root.resolve("package.json").writeText("{}")
        root.resolve("node_modules/dependency").apply { mkdirs(); resolve("package.json").writeText("{}") }
        assertEquals(root, resolveMcpProjectDirectory(root, null))
    }
    @Test fun `ambiguous legacy projects are not selected by iteration order`() {
        val root = temporary.newFolder()
        for (name in listOf("a", "b")) root.resolve(name).apply { mkdir(); resolve("main.py").writeText("") }
        assertNull(resolveMcpProjectDirectory(root, null))
        assertEquals(File(root, "b"), resolveMcpProjectDirectory(root, File(root, "b").path))
    }
    @Test fun `stored path cannot escape into a neighboring installation`() {
        val root = temporary.newFolder("plugin")
        val outside = temporary.newFolder("plugin-other")
        assertNull(resolveMcpProjectDirectory(root, outside.path))
        assertNull(resolveMcpProjectDirectory(root, root.resolve("missing").path))
    }
}

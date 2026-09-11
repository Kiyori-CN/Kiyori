package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.ToolPackage
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ToolPkgLoadOrderTest {
    private fun container(name: String, dependencies: List<String> = emptyList(), version: String = "1.2.0",
                          children: List<ToolPkgSubpackageRuntime> = emptyList()): ToolPkgContainerRuntime = mock {
        on { packageName } doReturn name
        on { this.version } doReturn version
        on { requires } doReturn dependencies.map { ToolPkgManifestRequirement(it, "required") }
        on { subpackages } doReturn children
    }

    private fun resolve(containers: List<ToolPkgContainerRuntime>, preferred: List<String> = emptyList(),
                        enabled: List<String> = containers.flatMap { listOf(it.packageName) + it.subpackages.map { child -> child.packageName } }): ToolPkgLoadOrderResult =
        ToolPkgLoadOrderResolver.resolve(containers, emptyMap(), enabled, preferred)

    @Test fun `dependencies precede consumers regardless of preference and input order`() {
        val a = container("a", listOf("b"))
        val b = container("b", listOf("c"))
        val c = container("c")
        listOf(listOf(a,b,c), listOf(c,a,b), listOf(b,c,a)).forEach { input ->
            val result = resolve(input, listOf("a", "b", "c"))
            assertEquals(listOf("c", "b", "a"), result.orderedContainers.map { it.packageName })
            assertTrue(result.failures.isEmpty())
        }
    }

    @Test fun `independent packages retain user preference`() {
        assertEquals(listOf("b", "a", "c"), resolve(listOf(container("a"),container("b"),container("c")), listOf("b","a")).orderedContainers.map { it.packageName })
    }

    @Test fun `missing disabled and failed transitive dependencies reject their consumers`() {
        val result = resolve(listOf(container("a", listOf("b")), container("b",listOf("missing")), container("ok")))
        assertEquals(setOf("a","b"), result.failures.keys)
        assertEquals(listOf("ok"), result.orderedContainers.map { it.packageName })
        assertTrue(resolve(listOf(container("a",listOf("b")),container("b")),enabled=listOf("a")).failures.containsKey("a"))
    }

    @Test fun `cycles and cycle dependents do not prevent independent packages loading`() {
        val result=resolve(listOf(container("a",listOf("b")),container("b",listOf("a")),container("c",listOf("b")),container("ok")))
        assertEquals(setOf("a","b","c"),result.failures.keys)
        assertEquals(listOf("ok"),result.orderedContainers.map { it.packageName })
    }

    @Test fun `ambiguous short subpackage IDs cannot depend on scan order`() {
        fun child(parent:String)=ToolPkgSubpackageRuntime("$parent/shared",parent,"shared","main.js",LocalizedText.of("shared"),LocalizedText.of(""),true,1)
        val a=container("a",children=listOf(child("a")))
        val b=container("b",children=listOf(child("b")))
        for (input in listOf(listOf(a,b),listOf(b,a))) {
            assertTrue(resolve(input+container("consumer",listOf("shared"))).failures.getValue("consumer").contains("ambiguous"))
            assertTrue(resolve(input+container("consumer",listOf("a/shared"))).failures.isEmpty())
        }
    }
}

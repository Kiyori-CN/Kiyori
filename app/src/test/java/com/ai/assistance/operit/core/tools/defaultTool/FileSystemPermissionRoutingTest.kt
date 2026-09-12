package com.ai.assistance.operit.core.tools.defaultTool

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileSystemPermissionRoutingTest {
    @Test fun `similarly named private package paths never use the host app identity`() {
        org.mockito.Mockito.mockStatic(android.os.Process::class.java).use {
            val packageName = com.ai.assistance.operit.BuildConfig.APPLICATION_ID
            val debugger = com.ai.assistance.operit.core.tools.defaultTool.debugger.DebuggerFileSystemTools
            assertTrue(debugger.isOperitInternalPath("/data/data/$packageName/files"))
            assertTrue(debugger.isOperitInternalPath("/data/user/0/$packageName/files"))
            assertFalse(debugger.isOperitInternalPath("/data/data/$packageName.sibling/files"))
            assertFalse(debugger.isOperitInternalPath("/data/user/0/$packageName.sibling/files"))
        }
    }
    @Test fun `new Shizuku grant is honored without overriding explicit permission choice`() {
        assertEquals(AndroidPermissionLevel.STANDARD, ToolGetter.fileSystemPermissionLevel(null, false))
        assertEquals(AndroidPermissionLevel.DEBUGGER, ToolGetter.fileSystemPermissionLevel(null, true))
        AndroidPermissionLevel.entries.forEach { configured ->
            assertEquals(configured, ToolGetter.fileSystemPermissionLevel(configured, true))
            assertEquals(configured, ToolGetter.fileSystemPermissionLevel(configured, false))
        }
    }

    @Test fun `registered file executors resolve permission at execution not first registration`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val registration = File(root, "app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt").readText()
        assertTrue(registration.contains("fun fileSystemTools() = ToolGetter.getFileSystemTools(context)"))
        assertTrue(registration.contains("fileSystemTools().listFiles(tool)"))
        assertFalse(registration.contains("val fileSystemTools = ToolGetter.getFileSystemTools(context)"))
    }
}

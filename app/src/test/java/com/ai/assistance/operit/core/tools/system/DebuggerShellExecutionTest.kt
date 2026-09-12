package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.core.tools.system.shell.DebuggerShellExecutor
import kotlinx.coroutines.runBlocking
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File

class DebuggerShellExecutionTest {
    @Test fun `directory locale assignment and quoted paths reach Shizuku as one shell script`() = runBlocking {
        val service: IShizukuService = mock()
        val process: IRemoteProcess = mock()
        whenever(service.newProcess(any(), isNull(), isNull())).thenReturn(process)
        whenever(process.waitFor()).thenReturn(0)
        val executor = DebuggerShellExecutor(mock<Context>())
        val commands = listOf(
            "LC_ALL=C ls -lan -- '/storage/emulated/0/'",
            "LC_ALL=C ls -lan -- '/storage/emulated/0/a b'\"'\"'c\\d/'",
            "LC_ALL=C ls -lan -- '/storage/emulated/0/a | grep > b/'",
            "printf '%s' ''",
        )
        commands.forEach { command ->
            val result = executor.executeWithShell(command, service)
            assertTrue(result.success)
        }
        val arguments = argumentCaptor<Array<String>>()
        verify(service, times(commands.size)).newProcess(arguments.capture(), isNull(), isNull())
        arguments.allValues.zip(commands).forEach { (argv, command) ->
            assertArrayEquals(arrayOf("/system/bin/sh", "-c", command), argv)
        }
        verify(process, times(commands.size)).waitFor()
        verify(process, never()).destroy()
    }

    @Test fun `nonzero process exit is retained as failure`() = runBlocking {
        val service: IShizukuService = mock()
        val process: IRemoteProcess = mock()
        whenever(service.newProcess(any(), isNull(), isNull())).thenReturn(process)
        whenever(process.waitFor()).thenReturn(13)
        val result = DebuggerShellExecutor(mock()).executeWithShell("ls '/restricted'", service)
        assertFalse(result.success)
        assertEquals(13, result.exitCode)
    }

    @Test fun `public command entry cannot bypass the shell for assignment commands`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val source = File(root, "app/src/main/java/com/ai/assistance/operit/core/tools/system/shell/DebuggerShellExecutor.kt").readText()
        assertFalse(source.contains("executeCommandDirect"))
        assertFalse(source.contains("containsShellOperators"))
        assertTrue(source.substringBefore("internal suspend fun executeWithShell").contains("executeWithShell(command)"))
    }
}

package com.ai.assistance.operit.ui.features.packages

import android.content.Context
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPDeployViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class McpDeploymentCommandLoadingTest {
    private val context = mock<Context>().apply { whenever(getString(any())).thenReturn("Could not load commands") }
    private val repository = mock<MCPRepository>().apply { whenever(getInstalledPluginPath(any())).thenReturn("/plugins/current") }

    @Test fun `late plugin analysis cannot replace the next plugin commands`() = runTest {
        val release = CompletableDeferred<Unit>()
        val vm = MCPDeployViewModel(context, repository, commandLoader = { id, _ ->
            if (id == "old") release.await()
            listOf("install-$id")
        })
        val old = launch { vm.getDeployCommands("old") }
        runCurrent()
        vm.getDeployCommands("new")
        release.complete(Unit)
        old.join()
        assertEquals("new", vm.commandState.value.pluginId)
        assertEquals(listOf("install-new"), vm.commandState.value.commands)
    }

    @Test fun `cancelled noncooperative analysis cannot erase current results`() = runTest {
        val release = CompletableDeferred<Unit>()
        val vm = MCPDeployViewModel(context, repository, commandLoader = { id, _ ->
            if (id == "old") withContext(NonCancellable) { release.await() }
            listOf("install-$id")
        })
        val old = launch { vm.getDeployCommands("old") }
        runCurrent()
        old.cancel()
        vm.getDeployCommands("new")
        release.complete(Unit)
        old.join()
        assertEquals("new", vm.commandState.value.pluginId)
        assertFalse(vm.commandState.value.loading)
        assertNull(vm.commandState.value.error)
    }

    @Test fun `failed new target exposes error and clears old commands`() = runTest {
        val vm = MCPDeployViewModel(context, repository, commandLoader = { id, _ ->
            if (id == "broken") error("private diagnostic")
            listOf("install-$id")
        })
        vm.getDeployCommands("old")
        vm.getDeployCommands("broken")
        assertEquals("broken", vm.commandState.value.pluginId)
        assertTrue(vm.commandState.value.commands.isEmpty())
        assertFalse(vm.commandState.value.loading)
        assertEquals("Could not load commands", vm.commandState.value.error)
    }
}

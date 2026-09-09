package com.ai.assistance.operit.ui.features.packages

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.data.mcp.*
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class McpInstallOperationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = mock<MCPRepository>()
    private val context = mock<Context>().apply { whenever(getString(any())).thenReturn("Operation failed") }
    private val server = MCPLocalServer.PluginMetadata("target", "Target", "Description")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model() = MCPViewModel(repository, context).also { store.put("mcp", it) }

    @Test fun `rapid duplicate actions submit once and cannot reset an active operation`() = runTest {
        whenever(repository.installMCPServerWithObject(eq(server), any())).thenReturn(InstallResult.Success("/target"))
        val vm = model()
        vm.installServerWithObject(server)
        vm.installServerWithObject(server.copy(id = "other"))
        vm.resetInstallState()
        assertTrue(vm.isOperating.value)
        assertEquals(server, vm.currentServer.value)
        runCurrent()
        verify(repository, times(1)).installMCPServerWithObject(eq(server), any())
        assertFalse(vm.isOperating.value)
        assertTrue(vm.installResult.value is InstallResult.Success)
    }

    @Test fun `uninstall exceptions retain operation identity and support explicit retry`() = runTest {
        whenever(repository.uninstallMCPServer(server.id)).thenThrow(IllegalStateException("private diagnostic"))
        val vm = model()
        vm.uninstallServer(server)
        runCurrent()
        assertTrue(vm.isUninstallOperation.value)
        assertEquals("Operation failed", (vm.installResult.value as InstallResult.Error).message)
        vm.retryLastOperation()
        runCurrent()
        verify(repository, times(2)).uninstallMCPServer(server.id)
    }

    @Test fun `ZIP operation uses the URI captured at the click`() = runTest {
        val first = mock<Uri>()
        val later = mock<Uri>()
        whenever(repository.installMCPServerFromZip(any(), any(), any(), any(), any(), any())).thenReturn(InstallResult.Success("/target"))
        val vm = model()
        vm.setSelectedZipUri(first)
        vm.installServerFromZip(server, "first.zip")
        vm.setSelectedZipUri(later)
        runCurrent()
        verify(repository).installMCPServerFromZip(eq(server.id), eq(first), eq(server.name), eq(server.description), eq(server.author), any())
    }
}

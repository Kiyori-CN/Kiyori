package com.ai.assistance.operit.ui.features.toolbox.screens.filemanager

import com.ai.assistance.operit.ui.features.toolbox.screens.filemanager.models.*
import org.junit.Assert.*
import org.junit.Test

class FileManagerTransferPolicyTest {
    private fun request(target: String, environment: String? = null, move: Boolean = false) =
        FileManagerCopyRequest(listOf(FileItem("folder", true)), FileManagerLocation("/storage", null), FileManagerLocation(target, environment), move)

    @Test fun `copy within source directory can ask for another name but same directory move is rejected`() {
        assertNull(fileManagerTransferError(request("/storage/./")))
        assertNotNull(fileManagerTransferError(request("/storage/./", move = true)))
    }
    @Test fun `descendants are rejected using path boundaries and normalized dot segments`() {
        assertNotNull(fileManagerTransferError(request("/storage/folder")))
        assertNotNull(fileManagerTransferError(request("/storage/other/../folder/child")))
        assertNull(fileManagerTransferError(request("/storage/folder-other")))
        assertNull(fileManagerTransferError(request("/storage/another")))
    }
    @Test fun `browse only environments never become transfer targets`() {
        listOf("linux", "recycle", "network:id", "repo:uri").forEach {
            assertNotNull(fileManagerTransferError(request("/storage", it)))
        }
        assertNull(fileManagerTransferError(request("/storage", "android")))
        assertEquals("回收站 · /回收站", fileManagerLocationLabel(FileManagerLocation("/回收站", "recycle")))
    }
}

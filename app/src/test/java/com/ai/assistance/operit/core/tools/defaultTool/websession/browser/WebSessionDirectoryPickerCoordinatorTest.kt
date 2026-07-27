package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSessionDirectoryPickerCoordinatorTest {
    @Test
    fun `directory result is delivered exactly once`() {
        val results = mutableListOf<String?>()
        val requestId =
            WebSessionDirectoryPickerCoordinator.registerRequest { treeUriString ->
                results += treeUriString
            }

        WebSessionDirectoryPickerCoordinator.completeRequest(
            requestId,
            "content://com.android.externalstorage.documents/tree/primary%3ADownload",
        )
        WebSessionDirectoryPickerCoordinator.completeRequest(requestId, null)

        assertEquals(
            listOf("content://com.android.externalstorage.documents/tree/primary%3ADownload"),
            results,
        )
    }

    @Test
    fun `directory picker cancellation is delivered exactly once`() {
        val results = mutableListOf<String?>()
        val requestId =
            WebSessionDirectoryPickerCoordinator.registerRequest { treeUriString ->
                results += treeUriString
            }

        WebSessionDirectoryPickerCoordinator.completeRequest(requestId, null)
        WebSessionDirectoryPickerCoordinator.completeRequest(requestId, "content://ignored")

        assertEquals(listOf<String?>(null), results)
    }
}

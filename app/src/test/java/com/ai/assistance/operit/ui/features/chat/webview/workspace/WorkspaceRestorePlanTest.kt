package com.ai.assistance.operit.ui.features.chat.webview.workspace

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WorkspaceRestorePlanTest {
    private val hash = "a".repeat(64)

    @Test fun `missing object fails before writes or deletes`() = runTest {
        val events = mutableListOf<String>()
        try {
            restoreWorkspaceFiles(mapOf("old" to hash), mapOf("new" to hash), { null },
                { path, _, _ -> events += path }, { events += it })
            fail("Missing object must fail")
        } catch (_: IllegalStateException) { }
        assertTrue(events.isEmpty())
    }

    @Test fun `failed write leaves files scheduled for deletion intact`() = runTest {
        val deleted = mutableListOf<String>()
        try {
            restoreWorkspaceFiles(mapOf("old" to hash), mapOf("new" to hash), { "object" },
                { _, _, _ -> error("write failed") }, { deleted += it })
            fail("Write failure must propagate")
        } catch (_: IllegalStateException) { }
        assertTrue(deleted.isEmpty())
    }

    @Test fun `successful restore writes changed files then deletes removed files`() = runTest {
        val events = mutableListOf<String>()
        restoreWorkspaceFiles(mapOf("old" to hash, "same" to hash), mapOf("new" to hash, "same" to hash), { "object" },
            { path, _, _ -> events += "write $path" }, { events += "delete $it" })
        assertEquals(listOf("write new", "delete old"), events)
    }

    @Test fun `invalid manifest paths cannot escape workspace`() = runTest {
        for (path in listOf("../outside", "/absolute", "a/../../b", "a\\b", "a//b")) {
            try {
                restoreWorkspaceFiles(emptyMap(), mapOf(path to hash), { fail("Must validate before I/O"); null },
                    { _, _, _ -> fail("Must not write") }, { fail("Must not delete") })
                fail("Invalid path must fail")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

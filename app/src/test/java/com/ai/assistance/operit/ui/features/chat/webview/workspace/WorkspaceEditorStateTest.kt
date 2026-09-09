package com.ai.assistance.operit.ui.features.chat.webview.workspace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class WorkspaceEditorStateTest {
    private fun state() = WorkspaceEditorState().apply {
        openFiles = listOf(OpenFileInfo("a", "original", 0), OpenFileInfo("b", "second", 0))
        currentFileIndex = 1
    }

    @Test fun `save failure keeps dirty file and current content`() = runTest {
        val state = state()
        state.edit("a", "edited")
        assertFalse(state.save("a") { false })
        assertTrue("a" in state.unsavedFiles)
        assertEquals("edited", state.openFiles.first().content)
        assertTrue(state.savingFiles.isEmpty())
    }

    @Test fun `edit during save remains dirty and cannot be closed as saved`() = runTest {
        val state = state()
        state.edit("a", "first edit")
        val finish = CompletableDeferred<Boolean>()
        val save = async { state.save("a") { finish.await() } }
        yield()
        state.close("a")
        assertEquals(2, state.openFiles.size)
        state.edit("a", "later edit")
        finish.complete(true)
        assertFalse(save.await())
        assertEquals("later edit", state.openFiles.first().content)
        assertTrue("a" in state.unsavedFiles)
        assertTrue(state.save("a") { true })
        state.close("a")
        assertEquals("b", state.openFiles[state.currentFileIndex].path)
    }

    @Test fun `closing inactive tab preserves active file and preview selection`() {
        val state = state()
        state.close("a")
        assertEquals("b", state.openFiles[state.currentFileIndex].path)
        state.currentFileIndex = -1
        state.close("b")
        assertEquals(-1, state.currentFileIndex)
    }

    @Test fun `external refresh never overwrites dirty or reopens closed file`() {
        val state = state()
        val snapshot = state.openFiles.first()
        state.edit("a", "local work")
        state.acceptExternalUpdate(snapshot, snapshot.copy(content = "external"))
        assertEquals("local work", state.openFiles.first().content)
        state.close("a")
        state.acceptExternalUpdate(snapshot, snapshot.copy(content = "external"))
        assertEquals(listOf("b"), state.openFiles.map { it.path })
    }

    @Test fun `rewind rejects unsaved content before touching disk`() = runTest {
        val state = state()
        state.edit("a", "keep me")
        try {
            state.withRewind { fail("Dirty editor must block restore") }
            fail("Expected rejection")
        } catch (_: IllegalStateException) { }
        assertEquals("keep me", state.openFiles.first().content)
        assertFalse(state.isRestoring)
    }

    @Test fun `rewind prevents writes and invalidates snapshots after failure`() = runTest {
        val state = state()
        val snapshot = state.openFiles.first()
        try {
            state.withRewind {
                assertTrue(state.isRestoring)
                state.edit("a", "late edit")
                assertFalse(state.save("a") { fail("Must not save while restoring"); true })
                state.acceptExternalUpdate(snapshot, snapshot.copy(content = "late read"))
                assertEquals(snapshot.content, state.openFiles.first().content)
                error("partial I/O failure")
            }
        } catch (_: IllegalStateException) { }
        assertFalse(state.isRestoring)
        assertEquals(1, state.refreshGeneration)
        assertTrue(state.openFiles.isEmpty())
        assertEquals(-1, state.currentFileIndex)
    }

    @Test fun `rewind cannot overlap an in flight save`() = runTest {
        val state = state()
        val finish = CompletableDeferred<Boolean>()
        val save = async { state.save("a") { finish.await() } }
        yield()
        try {
            state.withRewind { fail("Save in progress must block restore") }
            fail("Expected rejection")
        } catch (_: IllegalStateException) { }
        finish.complete(true)
        assertTrue(save.await())
    }
}

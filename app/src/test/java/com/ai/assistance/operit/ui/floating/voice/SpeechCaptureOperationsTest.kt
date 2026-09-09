package com.ai.assistance.operit.ui.floating.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechCaptureOperationsTest {
    @Test fun `new owner releases a completed start before taking microphone`() = runTest {
        val operations = SpeechCaptureOperations()
        val oldOwner = Any()
        val events = mutableListOf<String>()
        operations.start(oldOwner, this) { events += "old start" }.join()
        operations.start(Any(), this, releasePrevious = { events += "release"; true }) {
            events += "new start"
        }.join()
        assertEquals(listOf("old start", "release", "new start"), events)
        assertNull(operations.stop(oldOwner, this) { events += "stale release" })
    }

    @Test fun `failed release does not start another capture`() = runTest {
        val operations = SpeechCaptureOperations()
        operations.start(Any(), this) {}.join()
        var started = false
        operations.start(Any(), this, releasePrevious = { false }) { started = true }.join()
        assertFalse(started)
    }

    @Test fun `cancel during handoff prevents delayed microphone start`() = runTest {
        val operations = SpeechCaptureOperations()
        val owner = Any()
        val handoff = CompletableDeferred<Unit>()
        var starts = 0
        var releases = 0
        operations.start(owner, this) { handoff.await(); starts++ }
        operations.stop(owner, this) { releases++ }?.join()
        handoff.complete(Unit)
        assertEquals(0, starts)
        assertEquals(1, releases)
        assertFalse(operations.owns(owner))
    }

    @Test fun `replacement waits for cleanup and stale owner cannot cancel it`() = runTest {
        val operations = SpeechCaptureOperations()
        val oldOwner = Any()
        val newOwner = Any()
        val finishRelease = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        operations.start(oldOwner, this) { awaitCancellation() }
        operations.stop(oldOwner, this) { finishRelease.await(); events += "released" }
        val next = operations.start(newOwner, this) { events += "started" }
        assertEquals(emptyList<String>(), events)
        finishRelease.complete(Unit)
        next.join()
        assertEquals(listOf("released", "started"), events)
        assertNull(operations.stop(oldOwner, this) { events += "stale release" })
    }

    @Test fun `dispose releases resources even when UI scope was cancelled`() = runTest {
        val operations = SpeechCaptureOperations()
        val uiScope = CoroutineScope(coroutineContext + Job())
        val owner = Any()
        operations.start(owner, uiScope) { awaitCancellation() }
        uiScope.cancel()
        var released = false
        operations.stop(owner, uiScope) { released = true }?.join()
        assertEquals(true, released)
    }
}

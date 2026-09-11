package com.ai.assistance.operit.ui.common.composedsl

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComposeDslTextInputSyncTest {
    @Test fun blurDuringFlushDoesNotRestoreAnOlderValue() {
        val guard = ComposeDslTextFieldEchoGuard("before")
        guard.onLocalEditDispatched("after")
        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO, guard.reconcile("before", "after", false))
        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.CONVERGED, guard.reconcile("after", "after", false))
        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.EXTERNAL_CHANGE, guard.reconcile("external", "after", false))
    }
    @Test fun staleTypingAndRepeatedDeletionEchoKeepTheEditorValue() {
        val guard = ComposeDslTextFieldEchoGuard("ab")
        guard.onLocalEditDispatched("abc")
        guard.onLocalEditDispatched("a")
        repeat(2) {
            assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.OWN_ECHO, guard.reconcile("abc", "a", true))
        }
        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.CONVERGED, guard.reconcile("a", "a", true))
        assertEquals(ComposeDslTextFieldEchoGuard.ExternalUpdate.EXTERNAL_CHANGE, guard.reconcile("template", "a", true))
    }

    @Test fun queueSerializesEditsAndOnlyRendersWhenDrained() {
        val started = mutableListOf<String>()
        val callbacks = mutableListOf<() -> Unit>()
        var renders = 0
        val queue = ComposeDslTextInputDispatchQueue { renders++ }
        val dispatch: (ComposeDslTextInputDispatchQueue.Entry, () -> Unit) -> Unit = { entry, done ->
            started += entry.text
            callbacks += done
        }
        queue.enqueue("input", "a", dispatch)
        queue.enqueue("input", "ab", dispatch)
        assertEquals(listOf("a"), started)
        callbacks[0]()
        assertEquals(listOf("a", "ab"), started)
        assertEquals(0, renders)
        callbacks[1]()
        assertFalse(queue.hasPending())
        assertEquals(1, renders)
        callbacks[1]()
        assertEquals(1, renders)
    }

    @Test fun staleCallbackCannotReleaseNewGenerationDispatch() {
        val started = mutableListOf<String>()
        val callbacks = mutableListOf<() -> Unit>()
        val queue = ComposeDslTextInputDispatchQueue {}
        val dispatch: (ComposeDslTextInputDispatchQueue.Entry, () -> Unit) -> Unit = { entry, done ->
            started += entry.text
            callbacks += done
        }
        queue.enqueue("input", "old", dispatch)
        queue.completeAll()
        queue.enqueue("input", "new", dispatch)
        callbacks[0]()
        queue.enqueue("input", "newer", dispatch)
        assertEquals(listOf("old", "new"), started)
        callbacks[1]()
        assertEquals(listOf("old", "new", "newer"), started)
        callbacks[2]()
    }

    @Test fun disposalAndCallerCancellationNeverResumeDependentSave() = runBlocking {
        val queue = ComposeDslTextInputDispatchQueue {}
        queue.enqueue("input", "pending") { _, _ -> }
        var saved = false
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { queue.awaitAll(); saved = true }
        queue.completeAll()
        waiting.join()
        assertTrue(waiting.isCancelled)
        assertFalse(saved)

        queue.enqueue("input", "pending") { _, _ -> }
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { queue.awaitAll(); saved = true }
        cancelled.cancel()
        cancelled.join()
        assertFalse(saved)
        assertTrue(queue.hasPending())
        queue.completeAll()
    }
}

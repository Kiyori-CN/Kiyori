package com.ai.assistance.operit.ui.features.toolbox.screens.tooltester

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ToolTesterViewModelTest {
    private fun test(caseId: String) = ToolTest("write_file", caseId, "", emptyList(), caseId)
    private fun success(test: ToolTest) = ToolResult(test.id, true, StringResultData(test.caseId))

    @Test fun `single and batch share synchronous reservation and cases retain distinct results`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val calls = mutableListOf<String>()
            val pending = CompletableDeferred<Unit>()
            val state = ToolTesterViewModel { calls += it.caseId; pending.await(); success(it) }
            store.put("test", state)
            state.run(listOf(test("small"), test("large")))
            state.run(listOf(test("duplicate")))
            assertTrue(state.isRunning)
            runCurrent()
            assertEquals(listOf("small"), calls)
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("small", "large"), calls)
            assertEquals(setOf("small", "large"), state.results.keys)
            assertTrue(state.results.values.all { it.status == TestStatus.SUCCESS })
            assertFalse(state.isRunning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `stop preserves in flight result and never submits remaining cases`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val pending = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val state = ToolTesterViewModel { calls += it.caseId; pending.await(); success(it) }
            store.put("test", state)
            state.run(listOf(test("first"), test("second")))
            runCurrent()
            state.requestStop()
            assertTrue(state.isRunning)
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("first"), calls)
            assertEquals(TestStatus.SUCCESS, state.results["first"]?.status)
            assertEquals(TestStatus.SKIPPED, state.results["second"]?.status)
            assertFalse(state.isRunning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `exceptions release reservation and rejected preparation does not execute`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val state = ToolTesterViewModel { calls++; error("denied") }
            store.put("test", state)
            state.run(listOf(test("first")))
            advanceUntilIdle()
            assertEquals("denied", state.results["first"]?.result?.error)
            assertEquals(TestStatus.FAILED, state.results["first"]?.status)
            state.run(listOf(test("hidden")), prepare = { false })
            advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals(TestStatus.SKIPPED, state.results["hidden"]?.status)
            assertFalse(state.isRunning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `lifecycle cancellation marks unknown current result and skips unsent work`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val state = ToolTesterViewModel { CompletableDeferred<ToolResult>().await() }
            store.put("test", state)
            state.run(listOf(test("first"), test("second")))
            runCurrent()
            store.clear()
            advanceUntilIdle()
            assertEquals(TestStatus.INTERRUPTED, state.results["first"]?.status)
            assertEquals(TestStatus.SKIPPED, state.results["second"]?.status)
            assertFalse(state.isRunning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `catalog has unique cases and isolated cleanup with archive outside source`() {
        val context = mock<Context>()
        whenever(context.getString(any())).thenReturn("label")
        val groups = getFinalToolTestGroups(context, "/test/session-one")
        val tests = groups.flatMap { it.tests }
        assertEquals(tests.size, tests.map { it.caseId }.distinct().size)
        val zip = tests.single { it.id == "zip_files" }
        val source = zip.parameters.single { it.name == "source" }.value
        val destination = zip.parameters.single { it.name == "destination" }.value
        assertFalse(destination.startsWith("$source/"))
        assertEquals("/test/session-one", tests.single { it.id == "delete_file" }.parameters.first().value)
        val batch = groups.filterNot { it.isManual }.flatMap { it.tests }.map { it.id }
        assertFalse(batch.any { it in setOf("tap", "swipe", "press_key", "set_input_text", "modify_system_setting") })
    }

    @Test fun `cancellation before launch releases reservation without claiming submitted tool`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val state = ToolTesterViewModel { fail("Must not execute"); success(it) }
            store.put("test", state)
            state.run(listOf(test("first")))
            store.clear()
            advanceUntilIdle()
            assertEquals(TestStatus.SKIPPED, state.results["first"]?.status)
            assertFalse(state.isRunning)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

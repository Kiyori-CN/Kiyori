package com.ai.assistance.operit.ui.features.toolbox.screens.shellexecutor

import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellExecutorViewModelTest {
    @Test fun `all send entries share immediate exclusion and completion preserves a new draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val result = CompletableDeferred<CommandRecord>()
            val calls = mutableListOf<String>()
            val state = ShellExecutorViewModel { command -> calls += command; result.await() }
            store.put("shell", state)
            state.commandInput = " first "
            state.executeCommand(state.commandInput)
            state.executeCommand("second")
            assertTrue(state.isExecuting)
            runCurrent()
            assertEquals(listOf("first"), calls)
            state.commandInput = "new draft"
            result.complete(record("first"))
            runCurrent()
            assertEquals("new draft", state.commandInput)
            assertEquals("first", state.commandHistory.single().command)
            assertFalse(state.isExecuting)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `failure preserves input and explicit retry replaces only matching history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var fail = true
            val state = ShellExecutorViewModel { command ->
                if (fail) throw IllegalStateException("unavailable")
                record(command)
            }
            store.put("shell", state)
            state.commandInput = "command"
            state.executeCommand(state.commandInput)
            runCurrent()
            assertEquals("command", state.commandInput)
            assertEquals("unavailable", state.errorMessage)
            assertTrue(state.commandHistory.isEmpty())
            fail = false
            state.executeCommand(state.commandInput)
            runCurrent()
            assertEquals("", state.commandInput)
            assertNull(state.errorMessage)
            state.commandInput = "unrelated draft"
            state.executeCommand("command")
            state.clearHistory()
            assertEquals(1, state.commandHistory.size)
            runCurrent()
            assertEquals(1, state.commandHistory.size)
            assertEquals("unrelated draft", state.commandInput)
            state.clearHistory()
            assertTrue(state.commandHistory.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `clearing owner before dispatch does not execute or leave busy state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            var calls = 0
            val state = ShellExecutorViewModel { command -> calls++; record(command) }
            store.put("shell", state)
            state.executeCommand("command")
            store.clear()
            runCurrent()
            assertEquals(0, calls)
            assertFalse(state.isExecuting)
            assertNull(state.errorMessage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    private fun record(command: String) = CommandRecord(command, AndroidShellExecutor.CommandResult(true, "output", exitCode = 0))
}

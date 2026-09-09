package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class LogcatOperationTest {
    @Test fun `clear and export exclude each other and result remains until dismissed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val pending = CompletableDeferred<LogcatExportResult>()
            var exports = 0
            var clears = 0
            val state = LogcatViewModel(mock(), { exports++; pending.await() }, { clears++ })
            store.put("logcat", state)
            state.saveLogsToFile()
            state.saveLogsToFile()
            state.clearLogs { fail("Clear must not run during export") }
            runCurrent()
            assertEquals(1, exports)
            assertEquals(0, clears)
            pending.complete(LogcatExportResult("saved path", true))
            advanceUntilIdle()
            assertFalse(state.isSaving.value)
            assertEquals("saved path", state.saveResult.value)
            state.dismissResult()
            assertNull(state.saveResult.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `failed clear keeps confirmation and explicit retry reports real success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val context = mock<Context>()
            whenever(context.getString(R.string.logcat_cleared)).thenReturn("cleared")
            whenever(context.getString(R.string.logcat_clear_failed, "denied")).thenReturn("failed")
            var fail = true
            var confirmed = 0
            val state = LogcatViewModel(context, { error("Must not export") }, {
                if (fail) throw IllegalStateException("denied")
            })
            store.put("logcat", state)
            state.clearLogs { confirmed++ }
            state.clearLogs { confirmed++ }
            runCurrent()
            assertFalse(state.isClearing.value)
            assertEquals(0, confirmed)
            assertEquals("failed", state.saveResult.value)
            fail = false
            state.clearLogs { confirmed++ }
            runCurrent()
            assertEquals(1, confirmed)
            assertEquals("cleared", state.saveResult.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

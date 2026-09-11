package com.ai.assistance.operit.ui.features.workflow.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkflowReadStateTest {
    @Test fun silentRefreshTakesOverVisibleLoadingAndOldFinishCannotClearIt() {
        val state = WorkflowReadState()
        val old = state.begin(true)
        val fresh = state.begin(false)
        state.finish(old)
        assertTrue(state.isLoading)
        assertFalse(state.isCurrent(old))
        state.finish(fresh)
        assertFalse(state.isLoading)
    }

    @Test fun backgroundRefreshStaysSilentAndOtherOwnerRemainsIndependent() {
        val list = WorkflowReadState()
        val detail = WorkflowReadState()
        val request = list.begin(false)
        detail.begin(true)
        assertFalse(list.isLoading)
        list.finish(request)
        assertTrue(detail.isLoading)
    }

    @Test fun cancelledAndFailedReadsReleaseCurrentLoadingInFinally() = runBlocking {
        for (error in listOf(IllegalStateException("read failed"), CancellationException("left page"))) {
            val state = WorkflowReadState()
            val request = state.begin(true)
            try {
                try { throw error } finally { state.finish(request) }
            } catch (actual: Exception) {
                assertSame(error, actual)
            }
            assertFalse(state.isLoading)
        }
    }
}

package com.ai.assistance.operit.ui.features.packages.market

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.RepoMarketPublishViewModel
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.RepoPublishDraft
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.RepoPublishSuccessAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class RepoMarketPublishOperationTest {
    @Test fun `duplicate save is rejected before dispatch and failed save can explicitly retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = mock<MarketStatsApiService>()
            val auth = mock<GitHubAuthPreferences>()
            val context = mock<Context>()
            whenever(context.getSharedPreferences(any(), any())).thenReturn(mock<SharedPreferences>())
            whenever(auth.isLoggedIn()).thenReturn(true)
            whenever(auth.getCurrentUserInfo()).thenReturn(GitHubUser(1, "author", avatarUrl = ""))
            val entry = mock<MarketV2Entry> { on { id } doReturn "entry" }
            whenever(api.updateEntry(eq("entry"), any())).thenReturn(Result.failure(IllegalStateException("offline")))
            val vm = RepoMarketPublishViewModel(context, MarketStatsType.SKILL, api, auth)
            store.put("vm", vm)
            val draft = RepoPublishDraft(title = "Original", description = "Description", category = "tools")
            vm.submitDraft(draft, "", entry, false, true)
            vm.submitDraft(draft.copy(title = "Duplicate"), "", entry, false, true)
            assertTrue(vm.isLoading.value)
            runCurrent()
            assertFalse(vm.isLoading.value)
            assertEquals("offline", vm.errorMessage.value)
            assertNull(vm.successAction.value)
            verify(api, times(1)).updateEntry(eq("entry"), argThat { title == "Original" })
            whenever(api.updateEntry(eq("entry"), any())).thenReturn(Result.success(entry))
            vm.submitDraft(draft, "", entry, false, true)
            runCurrent()
            assertEquals(RepoPublishSuccessAction.METADATA, vm.successAction.value)
            assertNull(vm.errorMessage.value)
            vm.submitDraft(draft, "", entry, false, true)
            runCurrent()
            verify(api, times(2)).updateEntry(eq("entry"), any())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun `cancelled result clears busy state without reporting a publish failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val api = mock<MarketStatsApiService>()
            val auth = mock<GitHubAuthPreferences>()
            val context = mock<Context>()
            whenever(context.getSharedPreferences(any(), any())).thenReturn(mock<SharedPreferences>())
            whenever(auth.isLoggedIn()).thenReturn(true)
            whenever(auth.getCurrentUserInfo()).thenReturn(GitHubUser(1, "author", avatarUrl = ""))
            val entry = mock<MarketV2Entry> { on { id } doReturn "entry" }
            whenever(api.updateEntry(eq("entry"), any())).thenReturn(Result.failure(CancellationException()))
            val vm = RepoMarketPublishViewModel(context, MarketStatsType.MCP, api, auth)
            store.put("vm", vm)
            vm.submitDraft(RepoPublishDraft(), "", entry, false, true)
            runCurrent()
            assertFalse(vm.isLoading.value)
            assertNull(vm.errorMessage.value)
            assertNull(vm.successAction.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}

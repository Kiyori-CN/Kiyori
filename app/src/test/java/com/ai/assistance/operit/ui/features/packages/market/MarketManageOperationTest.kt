package com.ai.assistance.operit.ui.features.packages.market

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageKind
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketManageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarketManageOperationTest {
    private val firstUser = GitHubUser(1L, "first", avatarUrl = "")
    private val secondUser = GitHubUser(2L, "second", avatarUrl = "")
    private val entry = MarketV2PublisherEntrySummary(id = "entry", relation = "owner", type = "script", stateCode = "approved")

    @Test fun `withdrawal failure keeps confirmation and current entry while allowing explicit retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                val auth = mock<GitHubAuthPreferences>()
                whenever(auth.isLoggedInFlow).thenReturn(MutableStateFlow(true))
                whenever(auth.userInfoFlow).thenReturn(MutableStateFlow(firstUser))
                whenever(auth.isLoggedIn()).thenReturn(true)
                whenever(auth.getCurrentUserInfo()).thenReturn(firstUser)
                whenever(api.getUserPublishedEntries("script")).thenReturn(Result.success(listOf(entry)))
                whenever(api.withdrawEntry("entry")).thenReturn(Result.failure(IllegalStateException("offline")))
                val vm = UnifiedMarketManageViewModel(mock(), UnifiedMarketManageKind.SCRIPT, api, auth)
                store.put("vm", vm)
                runCurrent()
                vm.loadEntries()
                runCurrent()
                assertTrue(vm.hasLoaded.value)
                var closes = 0
                vm.withdrawEntry(entry) { closes++ }
                vm.withdrawEntry(entry) { closes++ }
                runCurrent()
                assertEquals(0, closes)
                assertEquals("approved", vm.entries.value.single().stateCode)
                assertFalse(vm.isMutating.value)
                assertFalse(vm.isLoading.value)
                assertEquals("offline", vm.errorMessage.value)
                vm.withdrawEntry(entry) { closes++ }
                runCurrent()
                verify(api, times(2)).withdrawEntry("entry")
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun `account switch clears old entries and refuses a queued old-account action`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                val auth = mock<GitHubAuthPreferences>()
                val users = MutableStateFlow<GitHubUser?>(firstUser)
                whenever(auth.isLoggedInFlow).thenReturn(MutableStateFlow(true))
                whenever(auth.userInfoFlow).thenReturn(users)
                whenever(auth.isLoggedIn()).thenReturn(true)
                whenever(auth.getCurrentUserInfo()).thenAnswer { users.value }
                whenever(api.getUserPublishedEntries("script")).thenReturn(Result.success(listOf(entry)), Result.success(emptyList()))
                val vm = UnifiedMarketManageViewModel(mock(), UnifiedMarketManageKind.SCRIPT, api, auth)
                store.put("vm", vm)
                runCurrent()
                vm.loadEntries()
                runCurrent()
                assertEquals("entry", vm.entries.value.single().id)
                vm.withdrawEntry(entry) { fail("Old account action was applied") }
                users.value = secondUser
                runCurrent()
                assertEquals(2L, vm.accountId.value)
                assertTrue(vm.entries.value.isEmpty())
                assertFalse(vm.isLoading.value)
                verify(api, never()).withdrawEntry("entry")
                verify(api, times(1)).getUserPublishedEntries("script")
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }
}

package com.ai.assistance.operit.ui.features.packages.market

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.data.api.*
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketBrowseScope
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketBrowseViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarketBrowseLoadingTest {
    private fun page(id: String, number: Int = 1, total: Int = 1) = MarketRankPageResponse(
        page = number, totalPages = total,
        items = listOf(MarketRankEntryResponse(id, entry = MarketV2Entry(id = id))),
    )

    @Test fun `late local projection cannot replace a newly selected sort`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getAllRankPage("updated", 1)).thenReturn(Result.success(page("old")))
                whenever(api.getAllRankPage("likes", 1)).thenReturn(Result.success(page("new")))
                val oldRead = CompletableDeferred<Unit>()
                val vm = UnifiedMarketBrowseViewModel(mock(), UnifiedMarketBrowseScope.All, api) { entries ->
                    if (entries.single().id == "old") withContext(NonCancellable) { oldRead.await() }
                    entries.associate { it.id to MarketLocalInstallState(it.id, MarketLocalInstallStateKind.NOT_INSTALLED) }
                }
                store.put("vm", vm)
                vm.loadEntries()
                runCurrent()
                assertTrue(vm.isLoading.value)
                vm.onSortOptionChanged(MarketSortOption.LIKES)
                runCurrent()
                assertEquals(setOf("new"), vm.localInstallStates.value.keys)
                oldRead.complete(Unit)
                runCurrent()
                assertEquals(setOf("new"), vm.localInstallStates.value.keys)
                assertFalse(vm.isLoading.value)
                assertNull(vm.errorMessage.value)
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun `pagination is synchronous single flight and failed page remains retryable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getAllRankPage("updated", 1)).thenReturn(Result.success(page("first", total = 2)))
                whenever(api.getAllRankPage("updated", 2)).thenReturn(Result.failure(IllegalStateException("offline")))
                val vm = UnifiedMarketBrowseViewModel(mock(), UnifiedMarketBrowseScope.All, api) { emptyMap() }
                store.put("vm", vm)
                vm.loadEntries()
                runCurrent()
                vm.loadMoreEntries()
                vm.loadMoreEntries()
                runCurrent()
                verify(api, times(1)).getAllRankPage("updated", 2)
                assertTrue(vm.hasMore.value)
                assertFalse(vm.isLoadingMore.value)
                vm.loadMoreEntries()
                runCurrent()
                verify(api, times(2)).getAllRankPage("updated", 2)
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun `manifest failure ends loading and can be retried`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getManifest()).thenReturn(Result.failure(IllegalStateException("offline")))
                val vm = UnifiedMarketBrowseViewModel(mock(), UnifiedMarketBrowseScope.All, api) { emptyMap() }
                store.put("vm", vm)
                vm.loadManifest()
                vm.loadManifest()
                assertTrue(vm.isLoadingManifest.value)
                runCurrent()
                assertFalse(vm.isLoadingManifest.value)
                assertEquals("offline", vm.errorMessage.value)
                vm.loadManifest()
                runCurrent()
                verify(api, times(2)).getManifest()
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun `notification refresh failure preserves prior messages and cancelled entry never navigates`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getNotifications(50, 0)).thenReturn(
                    Result.success(listOf(MarketV2Notification(id = "notice", entryId = "entry"))),
                    Result.failure(IllegalStateException("offline")),
                )
                val vm = UnifiedMarketBrowseViewModel(mock(), UnifiedMarketBrowseScope.All, api) { emptyMap() }
                store.put("vm", vm)
                vm.loadNotifications()
                vm.loadNotifications()
                runCurrent()
                verify(api, times(1)).getNotifications(50, 0)
                vm.loadNotifications()
                runCurrent()
                assertFalse(vm.isLoading.value)
                assertEquals("notice", vm.notifications.value.single().id)
                assertEquals("offline", vm.errorMessage.value)
                var opened = false
                vm.openNotificationEntry("entry") { opened = true }
                vm.cancelOpeningNotificationEntry()
                runCurrent()
                assertFalse(opened)
                assertNull(vm.openingEntryId.value)
                verify(api, never()).getEntry("entry")
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }
}

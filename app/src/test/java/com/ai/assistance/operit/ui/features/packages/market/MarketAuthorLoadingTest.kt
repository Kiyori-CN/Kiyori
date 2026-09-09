package com.ai.assistance.operit.ui.features.packages.market

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel.UnifiedMarketAuthorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class MarketAuthorLoadingTest {
    @Test fun `failed author list remains reloadable instead of becoming an empty success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getPublisherEntries("author")).thenReturn(Result.failure(IllegalStateException("offline")))
                val vm = UnifiedMarketAuthorViewModel(mock(), "author", api)
                store.put("vm", vm)
                vm.loadEntries()
                vm.loadEntries()
                runCurrent()
                assertFalse(vm.isLoading.value)
                assertFalse(vm.hasLoaded.value)
                assertEquals("offline", vm.errorMessage.value)
                vm.loadEntries()
                runCurrent()
                verify(api, times(2)).getPublisherEntries("author")
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }

    @Test fun `opening detail is single flight and cancellation prevents late navigation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        mockStatic(Log::class.java).use {
            try {
                val api = mock<MarketStatsApiService>()
                whenever(api.getEntry("entry")).thenReturn(Result.success(MarketV2Entry(id = "entry")))
                val vm = UnifiedMarketAuthorViewModel(mock(), "author", api)
                store.put("vm", vm)
                var opened = 0
                val summary = MarketV2PublisherEntrySummary(id = "entry", relation = "owner")
                vm.openEntryDetail(summary) { opened++ }
                vm.openEntryDetail(summary) { opened++ }
                assertFalse(vm.isLoading.value)
                assertEquals("entry", vm.openingEntryId.value)
                vm.cancelOpeningEntry()
                runCurrent()
                assertEquals(0, opened)
                assertNull(vm.openingEntryId.value)
                vm.openEntryDetail(summary) { opened++ }
                vm.openEntryDetail(summary) { opened++ }
                runCurrent()
                assertEquals(1, opened)
                verify(api, times(1)).getEntry("entry")
            } finally { store.clear(); Dispatchers.resetMain() }
        }
    }
}

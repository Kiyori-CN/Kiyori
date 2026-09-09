package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UnifiedMarketAuthorViewModel(
    private val context: Context,
    private val authorId: String,
    private val marketStatsApiService: MarketStatsApiService = MarketStatsApiService(),
) : ViewModel() {
    private var openingJob: Job? = null
    private val _openingEntryId = MutableStateFlow<String?>(null)
    val openingEntryId: StateFlow<String?> = _openingEntryId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2PublisherEntrySummary>>(emptyList())
    val entries: StateFlow<List<MarketV2PublisherEntrySummary>> = _entries.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    fun loadEntries(refresh: Boolean = false) {
        if (_isLoading.value || (!refresh && _hasLoaded.value)) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val loaded = marketStatsApiService.getPublisherEntries(authorId).getOrThrow()
                currentCoroutineContext().ensureActive()
                _entries.value = loaded.distinctBy { it.id to it.relation }
                _hasLoaded.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _errorMessage.value = error.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load market author entries", error)
            }
        }.invokeOnCompletion { _isLoading.value = false }
    }

    fun openEntryDetail(entry: MarketV2PublisherEntrySummary, onLoaded: (MarketV2Entry) -> Unit) {
        if (_openingEntryId.value != null) return
        if (entry.id.isBlank()) {
            _errorMessage.value = context.getString(R.string.market_error_load_failed)
            return
        }
        _openingEntryId.value = entry.id
        _errorMessage.value = null
        openingJob = viewModelScope.launch {
            try {
                val fullEntry = marketStatsApiService.getEntry(entry.id).getOrThrow()
                    ?: error(context.getString(R.string.market_error_load_failed))
                currentCoroutineContext().ensureActive()
                onLoaded(fullEntry)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _errorMessage.value = error.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load author market entry", error)
            }
        }.also { job -> job.invokeOnCompletion { _openingEntryId.value = null } }
    }

    fun cancelOpeningEntry() { openingJob?.cancel() }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val authorId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketAuthorViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketAuthorViewModel(context, authorId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketAuthorViewModel"
    }
}

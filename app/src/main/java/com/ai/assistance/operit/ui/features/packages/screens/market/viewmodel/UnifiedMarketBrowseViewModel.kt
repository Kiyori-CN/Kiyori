package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2ManifestCategory
import com.ai.assistance.operit.data.api.MarketV2Notification
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.features.packages.market.MarketEntryInstallController
import com.ai.assistance.operit.ui.features.packages.market.MarketInstallProgress
import com.ai.assistance.operit.ui.features.packages.market.MarketInstallStateStore
import com.ai.assistance.operit.ui.features.packages.market.MarketLocalInstallState
import com.ai.assistance.operit.ui.features.packages.market.MarketSortOption
import com.ai.assistance.operit.ui.features.packages.market.resolveMarketLocalInstallStates
import com.ai.assistance.operit.ui.features.packages.market.toRankMetric
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UnifiedMarketListScope {
    ALL,
    TYPE,
    CATEGORY,
    TYPE_CATEGORY
}

data class UnifiedMarketBrowseScope(
    val kind: UnifiedMarketListScope,
    val categoryId: String = "",
    val type: String = ""
) {
    companion object {
        val All = UnifiedMarketBrowseScope(kind = UnifiedMarketListScope.ALL)

        fun type(type: String): UnifiedMarketBrowseScope {
            return UnifiedMarketBrowseScope(
                kind = UnifiedMarketListScope.TYPE,
                type = type
            )
        }

        fun category(categoryId: String): UnifiedMarketBrowseScope {
            return UnifiedMarketBrowseScope(
                kind = UnifiedMarketListScope.CATEGORY,
                categoryId = categoryId
            )
        }

        fun typeCategory(type: String, categoryId: String): UnifiedMarketBrowseScope {
            return UnifiedMarketBrowseScope(
                kind = UnifiedMarketListScope.TYPE_CATEGORY,
                categoryId = categoryId,
                type = type
            )
        }
    }
}

class UnifiedMarketBrowseViewModel(
    private val context: Context,
    private val browseScope: UnifiedMarketBrowseScope,
    private val marketApiService: MarketStatsApiService = MarketStatsApiService(),
    private val localStateReader: suspend (List<MarketV2Entry>) -> Map<String, MarketLocalInstallState> = { entries ->
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            resolveMarketLocalInstallStates(appContext,
                PackageManager.getInstance(appContext, AIToolHandler.getInstance(appContext)), entries)
        }
    },
) : ViewModel() {
    private val installController by lazy { MarketEntryInstallController(context, marketApiService) }
    private var listRequestGeneration = 0L
    private var localProjectionGeneration = 0L
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var openingEntryJob: Job? = null
    private var notificationJob: Job? = null
    private var notificationGeneration = 0L
    private val _openingEntryId = MutableStateFlow<String?>(null)
    val openingEntryId: StateFlow<String?> = _openingEntryId.asStateFlow()
    private val _isLoadingManifest = MutableStateFlow(false)
    val isLoadingManifest: StateFlow<Boolean> = _isLoadingManifest.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val installStates: StateFlow<Map<String, MarketInstallProgress>> = MarketInstallStateStore.installStates
    private val _localInstallStates = MutableStateFlow<Map<String, MarketLocalInstallState>>(emptyMap())
    val localInstallStates: StateFlow<Map<String, MarketLocalInstallState>> = _localInstallStates.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(MarketSortOption.UPDATED)
    val sortOption: StateFlow<MarketSortOption> = _sortOption.asStateFlow()

    private val _featuredOnly = MutableStateFlow(true)
    val featuredOnly: StateFlow<Boolean> = _featuredOnly.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2Entry>>(emptyList())
    private val _categories = MutableStateFlow<List<MarketV2ManifestCategory>>(emptyList())
    val categories: StateFlow<List<MarketV2ManifestCategory>> = _categories.asStateFlow()

    private val _notifications = MutableStateFlow<List<MarketV2Notification>>(emptyList())
    val notifications: StateFlow<List<MarketV2Notification>> = _notifications.asStateFlow()

    private val _listScrollIndex = MutableStateFlow(0)
    val listScrollIndex: StateFlow<Int> = _listScrollIndex.asStateFlow()

    private val _listScrollOffset = MutableStateFlow(0)
    val listScrollOffset: StateFlow<Int> = _listScrollOffset.asStateFlow()

    val entries: StateFlow<List<MarketV2Entry>> =
        combine(_entries, _searchQuery, _featuredOnly) { entries, query, featuredOnly ->
            val trimmedQuery = query.trim()
            var filtered = entries
            if (!trimmedQuery.isBlank()) {
                filtered = filtered.filter { entry ->
                    entry.title.contains(trimmedQuery, ignoreCase = true) ||
                        entry.description.contains(trimmedQuery, ignoreCase = true) ||
                        entry.detail.contains(trimmedQuery, ignoreCase = true) ||
                        entry.categoryId.contains(trimmedQuery, ignoreCase = true) ||
                        entry.type.contains(trimmedQuery, ignoreCase = true) ||
                        entry.publisherLogin().contains(trimmedQuery, ignoreCase = true)
                }
            }
            if (featuredOnly) {
                filtered = filtered.filter { it.featured }
            }
            filtered
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var currentPage = 1
    private var totalPages = 1
    private var hasLoadedEntries = false
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChanged(option: MarketSortOption) {
        if (_sortOption.value == option) return
        _sortOption.value = option
        _entries.value = emptyList()
        _localInstallStates.value = emptyMap()
        updateListScrollPosition(0, 0)
        loadEntries()
    }

    fun onFeaturedOnlyChanged(enabled: Boolean) {
        if (_featuredOnly.value == enabled) return
        _featuredOnly.value = enabled
    }

    fun loadManifest() {
        if (_isLoadingManifest.value) return
        _isLoadingManifest.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val manifest = marketApiService.getManifest().getOrThrow()
                _categories.value = manifest.categories.filter { it.id.isNotBlank() }.distinctBy { it.id }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _errorMessage.value = error.message ?: "Failed to load market manifest"
                AppLogger.e(TAG, "Failed to load market manifest", error)
            }
        }.also { job -> job.invokeOnCompletion { _isLoadingManifest.value = false } }
    }

    fun loadEntries() {
        val generation = ++listRequestGeneration
        listJob?.cancel()
        moreJob?.cancel()
        _isLoading.value = true
        _isLoadingMore.value = false
        _hasMore.value = false
        _errorMessage.value = null
        listJob = viewModelScope.launch {
            try {
                val page = loadPage(page = 1).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation != listRequestGeneration) return@launch
                currentPage = page.page
                totalPages = page.totalPages.coerceAtLeast(1)
                val loadedEntries = page.items.map { it.entry }.distinctBy { it.id }.orderedForCurrentSort()
                _entries.value = loadedEntries
                _hasMore.value = currentPage < totalPages
                refreshLocalInstallStates(loadedEntries)
                hasLoadedEntries = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != listRequestGeneration) return@launch
                _errorMessage.value = error.message ?: "Failed to load market list"
                AppLogger.e(TAG, "Failed to load market list", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == listRequestGeneration) _isLoading.value = false
        } }
    }

    fun loadEntriesIfNeeded() {
        if (hasLoadedEntries || _isLoading.value) return
        loadEntries()
    }

    fun updateListScrollPosition(index: Int, offset: Int) {
        _listScrollIndex.value = index
        _listScrollOffset.value = offset
    }

    fun loadMoreEntries() {
        if (_isLoading.value || _isLoadingMore.value || !_hasMore.value) return
        val generation = listRequestGeneration
        _isLoadingMore.value = true
        _errorMessage.value = null
        moreJob = viewModelScope.launch {
            try {
                val page = loadPage(page = currentPage + 1).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation != listRequestGeneration) return@launch
                currentPage = page.page
                totalPages = page.totalPages.coerceAtLeast(1)
                val loadedEntries = (_entries.value + page.items.map { it.entry }).distinctBy { it.id }.orderedForCurrentSort()
                _entries.value = loadedEntries
                _hasMore.value = currentPage < totalPages
                refreshLocalInstallStates(loadedEntries)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != listRequestGeneration) return@launch
                _errorMessage.value = error.message ?: "Failed to load more market entries"
                AppLogger.e(TAG, "Failed to load more market entries", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == listRequestGeneration) _isLoadingMore.value = false
        } }
    }

    fun resetNotifications() {
        notificationGeneration++
        notificationJob?.cancel()
        cancelOpeningNotificationEntry()
        _notifications.value = emptyList()
        _isLoading.value = false
        _errorMessage.value = null
    }

    fun loadNotifications() {
        if (_isLoading.value) return
        val generation = notificationGeneration
        _isLoading.value = true
        _errorMessage.value = null
        notificationJob = viewModelScope.launch {
            try {
                val loaded = marketApiService.getNotifications(limit = 50, offset = 0).getOrThrow()
                currentCoroutineContext().ensureActive()
                if (generation == notificationGeneration) _notifications.value = loaded.distinctBy { it.id }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != notificationGeneration) return@launch
                _errorMessage.value = error.message ?: "Failed to load market notifications"
                AppLogger.e(TAG, "Failed to load market notifications", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == notificationGeneration) _isLoading.value = false
        } }
    }

    fun openNotificationEntry(entryId: String, onLoaded: (MarketV2Entry) -> Unit) {
        if (entryId.isBlank() || _openingEntryId.value != null) return
        _openingEntryId.value = entryId
        _errorMessage.value = null
        openingEntryJob = viewModelScope.launch {
            try {
                val entry = marketApiService.getEntry(entryId).getOrThrow()
                    ?: error(context.getString(com.ai.assistance.operit.R.string.market_error_load_failed))
                currentCoroutineContext().ensureActive()
                onLoaded(entry)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _errorMessage.value = error.message ?: context.getString(com.ai.assistance.operit.R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to open notification entry", error)
            }
        }.also { job -> job.invokeOnCompletion { _openingEntryId.value = null } }
    }

    fun cancelOpeningNotificationEntry() { openingEntryJob?.cancel() }

    fun installEntry(entry: MarketV2Entry) {
        val entryId = entry.id.trim()
        if (!MarketInstallStateStore.start(entryId)) return
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                installController.install(entry) { stage, progress ->
                    MarketInstallStateStore.update(entryId, stage, progress)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to install market entry"
                AppLogger.e(TAG, "Failed to install market entry ${entry.id}", e)
            } finally {
                try {
                    refreshLocalInstallStates(_entries.value)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _errorMessage.value = context.getString(com.ai.assistance.operit.R.string.market_install_state_refresh_failed)
                    AppLogger.e(TAG, "Failed to refresh local installation state", error)
                }
            }
        }.invokeOnCompletion { MarketInstallStateStore.finish(entryId) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private suspend fun loadPage(page: Int) =
        when (browseScope.kind) {
            UnifiedMarketListScope.ALL ->
                marketApiService.getAllRankPage(
                    metric = _sortOption.value.toRankMetric(),
                    page = page
                )

            UnifiedMarketListScope.TYPE ->
                marketApiService.getTypeRankPage(
                    type = browseScope.type,
                    metric = _sortOption.value.toRankMetric(),
                    page = page
                )

            UnifiedMarketListScope.CATEGORY ->
                marketApiService.getCategoryRankPage(
                    categoryId = browseScope.categoryId,
                    metric = _sortOption.value.toRankMetric(),
                    page = page
                )

            UnifiedMarketListScope.TYPE_CATEGORY ->
                marketApiService.getTypedCategoryRankPage(
                    type = browseScope.type,
                    categoryId = browseScope.categoryId,
                    metric = _sortOption.value.toRankMetric(),
                    page = page
                )
        }

    private suspend fun refreshLocalInstallStates(entries: List<MarketV2Entry>) {
        val generation = ++localProjectionGeneration
        val states = if (entries.isEmpty()) emptyMap() else localStateReader(entries)
        currentCoroutineContext().ensureActive()
        if (generation == localProjectionGeneration && entries === _entries.value) _localInstallStates.value = states
    }

    class Factory(
        private val context: Context,
        private val browseScope: UnifiedMarketBrowseScope
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketBrowseViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketBrowseViewModel(context, browseScope) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketBrowseVM"
    }

    private fun List<MarketV2Entry>.orderedForCurrentSort(): List<MarketV2Entry> {
        return when (_sortOption.value) {
            MarketSortOption.UPDATED -> sortedByDescending { it.updatedTimestamp() }
            MarketSortOption.DOWNLOADS -> sortedByDescending { it.stats?.downloads ?: it.downloadCount.takeIf { count -> count > 0 } ?: it.downloads }
            MarketSortOption.LIKES -> sortedByDescending { it.likeCount() }
        }
    }
}

private fun MarketV2Entry.publisherLogin(): String {
    return publisher?.login?.ifBlank { author?.login.orEmpty() }
        ?.ifBlank { publisherId.removePrefix("gh_").ifBlank { authorId.removePrefix("gh_") } }
        .orEmpty()
}

private fun MarketV2Entry.updatedTimestamp(): String {
    return updatedAt ?: publishedAt ?: createdAt.orEmpty()
}

private fun MarketV2Entry.likeCount(): Int {
    return stats?.likes ?: reactions.sumOf { reaction ->
        val key = reaction.reaction.ifBlank { reaction.content }
        if (key == "+1" || key.equals("like", ignoreCase = true)) reaction.total.coerceAtLeast(1) else 0
    }
}

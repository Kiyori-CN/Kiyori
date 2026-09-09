package com.ai.assistance.operit.ui.features.packages.screens.market.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.MarketStatsApiService
import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.data.api.MarketV2PublisherEntrySummary
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.features.packages.market.MarketStatsType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class UnifiedMarketManageKind(val types: Set<String>) {
    SCRIPT(setOf(MarketStatsType.SCRIPT.wireValue)),
    PACKAGE(setOf(MarketStatsType.PACKAGE.wireValue)),
    ARTIFACT(setOf(MarketStatsType.SCRIPT.wireValue, MarketStatsType.PACKAGE.wireValue)),
    SKILL(setOf(MarketStatsType.SKILL.wireValue)),
    MCP(setOf(MarketStatsType.MCP.wireValue))
}

class UnifiedMarketManageViewModel(
    private val context: Context,
    private val kind: UnifiedMarketManageKind,
    private val marketStatsApiService: MarketStatsApiService = MarketStatsApiService(),
    private val githubAuth: GitHubAuthPreferences = GitHubAuthPreferences.getInstance(context),
) : ViewModel() {
    private var accountGeneration = 0L
    private var loadedAccountId: Long? = null
    private var loadJob: Job? = null
    private var requestJob: Job? = null
    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _entries = MutableStateFlow<List<MarketV2PublisherEntrySummary>>(emptyList())
    val entries: StateFlow<List<MarketV2PublisherEntrySummary>> = _entries.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _accountId = MutableStateFlow<Long?>(null)
    val accountId: StateFlow<Long?> = _accountId.asStateFlow()
    val isLoggedIn: StateFlow<Boolean> = accountId.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            githubAuth.isLoggedInFlow.combine(githubAuth.userInfoFlow) { loggedIn, user ->
                if (loggedIn) user?.id else null
            }.distinctUntilChanged().collect { id ->
                reset()
                // 先清除旧账号事实，再通知可见页面加载；隐藏分类不自动联网。
                _accountId.value = id
            }
        }
    }

    private suspend fun verifyAccount(expected: Long?) {
        currentCoroutineContext().ensureActive()
        check(expected != null && githubAuth.isLoggedIn() && githubAuth.getCurrentUserInfo()?.id == expected) {
            context.getString(R.string.market_account_changed)
        }
    }

    fun loadEntries(refresh: Boolean = false) {
        if (_isLoading.value || _isRefreshing.value || (!refresh && _hasLoaded.value)) return
        val expected = accountId.value ?: return
        val generation = accountGeneration
        if (refresh) _isRefreshing.value = true else _isLoading.value = true
        _errorMessage.value = null
        loadJob = viewModelScope.launch {
            try {
                verifyAccount(expected)
                val loaded = kind.types.flatMap { marketStatsApiService.getUserPublishedEntries(it).getOrThrow() }
                    .filter { it.type.lowercase() in kind.types }.distinctBy { it.id }.sortedByDescending { it.updatedAt }
                verifyAccount(expected)
                if (generation != accountGeneration) return@launch
                _entries.value = loaded
                loadedAccountId = expected
                _hasLoaded.value = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != accountGeneration) return@launch
                _errorMessage.value = error.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load managed market entries", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == accountGeneration) {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        } }
    }

    fun reset() {
        accountGeneration++
        loadJob?.cancel()
        requestJob?.cancel()
        loadedAccountId = null
        _entries.value = emptyList()
        _hasLoaded.value = false
        _errorMessage.value = null
        _isLoading.value = false
        _isRefreshing.value = false
        _isMutating.value = false
    }

    fun cancelPendingRead() { if (!_isMutating.value) requestJob?.cancel() }

    fun clearError() {
        _errorMessage.value = null
    }

    fun openEntryDetail(entry: MarketV2PublisherEntrySummary, onLoaded: (MarketV2Entry) -> Unit) {
        openEntry(entry, { marketStatsApiService.getEntry(entry.id).getOrThrow()
            ?: error(context.getString(R.string.market_error_load_failed)) }, onLoaded)
    }

    fun withdrawEntry(entry: MarketV2PublisherEntrySummary, onSuccess: () -> Unit = {}) {
        if (_isLoading.value || _isRefreshing.value || entry.id.isBlank()) return
        val expected = loadedAccountId
        val generation = accountGeneration
        _isLoading.value = true
        _isMutating.value = true
        _errorMessage.value = null
        requestJob = viewModelScope.launch {
            try {
                verifyAccount(expected)
                marketStatsApiService.withdrawEntry(entry.id).getOrThrow()
                verifyAccount(expected)
                if (generation != accountGeneration) return@launch
                _entries.value = _entries.value.map { if (it.id == entry.id) it.copy(stateCode = "withdrawn") else it }
                onSuccess()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != accountGeneration) return@launch
                _errorMessage.value = error.message ?: context.getString(R.string.market_error_action_failed)
                AppLogger.e(TAG, "Failed to withdraw managed market entry", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == accountGeneration) { _isLoading.value = false; _isMutating.value = false }
        } }
    }

    private fun openEntry(entry: MarketV2PublisherEntrySummary, read: suspend () -> MarketV2Entry, onLoaded: (MarketV2Entry) -> Unit) {
        if (_isLoading.value || _isRefreshing.value || entry.id.isBlank()) return
        val expected = loadedAccountId
        val generation = accountGeneration
        _isLoading.value = true
        _errorMessage.value = null
        requestJob = viewModelScope.launch {
            try {
                verifyAccount(expected)
                val fullEntry = read()
                verifyAccount(expected)
                if (generation == accountGeneration) onLoaded(fullEntry)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (generation != accountGeneration) return@launch
                _errorMessage.value = error.message ?: context.getString(R.string.market_error_load_failed)
                AppLogger.e(TAG, "Failed to load managed market entry detail", error)
            }
        }.also { job -> job.invokeOnCompletion {
            if (generation == accountGeneration) _isLoading.value = false
        } }
    }

    fun openEntryForRevision(
        entry: MarketV2PublisherEntrySummary,
        onLoaded: (MarketV2Entry) -> Unit
    ) {
        AppLogger.i(
            TAG,
            "revision_gate click entryId=${entry.id} stateCode=${entry.stateCode} " +
                "revisionAvailableAt=${entry.revisionAvailableAt} " +
                "updatedAt=${entry.updatedAt} compatVersion=${BuildConfig.OPERIT_MARKET_COMPAT_VERSION}"
        )
        revisionGateMessage(entry)?.let { message ->
            AppLogger.i(
                TAG,
                "revision_gate decision=blocked entryId=${entry.id} message=$message"
            )
            _errorMessage.value = message
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return
        }

        openOwnedEntryDetail(entry, onLoaded)
    }

    fun openOwnedEntryDetail(entry: MarketV2PublisherEntrySummary, onLoaded: (MarketV2Entry) -> Unit) {
        openEntry(entry, { marketStatsApiService.getMyEntryDetail(entry.id).getOrThrow() }, onLoaded)
    }

    private fun revisionGateMessage(entry: MarketV2PublisherEntrySummary): String? {
        if (!entry.stateCode.equals("changes_requested", ignoreCase = true)) {
            return context.getString(R.string.market_manage_revision_state_invalid)
        }
        val revisionAvailableAt = entry.revisionAvailableAt?.trim().orEmpty()
        if (revisionAvailableAt.isBlank()) {
            return context.getString(R.string.market_manage_revision_time_invalid)
        }
        val availableAt =
            runCatching { java.time.Instant.parse(revisionAvailableAt).toEpochMilli() }
                .getOrElse { error ->
                    AppLogger.e(
                        TAG,
                        "Invalid revisionAvailableAt for entry ${entry.id}: $revisionAvailableAt",
                        error
                    )
                    return context.getString(R.string.market_manage_revision_time_invalid)
                }
        val remainingMillis = availableAt - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            return null
        }

        val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        val remainingText =
            when {
                hours > 0L && minutes > 0L ->
                    context.getString(R.string.market_duration_hours_minutes, hours, minutes)
                hours > 0L -> context.getString(R.string.market_duration_hours, hours)
                else -> context.getString(R.string.market_duration_minutes, minutes)
            }
        return context.getString(R.string.market_manage_revision_cooldown, remainingText)
    }


    class Factory(
        private val context: Context,
        private val kind: UnifiedMarketManageKind
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UnifiedMarketManageViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UnifiedMarketManageViewModel(context, kind) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "UnifiedMarketManageViewModel"
    }
}

